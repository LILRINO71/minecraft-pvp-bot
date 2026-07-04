package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.settings.DoubleSetting;
import com.mcbot.util.BlockUtil;
import com.mcbot.util.CombatUtil;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AutoCrystal — proper End Crystal PvP (place AND pop), not just swinging.
 *
 * Each tick:
 *   1. POP any existing crystal that hits the target hard enough while keeping
 *      self-damage acceptable (this pops both our own and the enemy's crystals).
 *   2. PLACE a fresh crystal on the best obsidian/bedrock base near the target,
 *      remembering to actually right-click (useItemOn) the block face — the old
 *      version frequently fell through to a left-click swing, which is why it
 *      "just hit with the crystal" instead of placing one.
 *
 * If no valid base block exists near the target it says so (instead of silently
 * doing nothing), so you know to bring obsidian / enable an obsidian placer.
 */
public class AutoCrystalModule extends Module {

    private static final double PLACE_RANGE  = 5.0;
    private static final double POP_RANGE    = 6.0;
    private static final int    PLACE_RADIUS = 4;      // search radius around enemy

    private final DoubleSetting minDamage = addSetting(new DoubleSetting(
            "minDamage", "Minimum damage to the target before placing/popping.", 4.0, 1.0, 20.0, 0.5));
    private final DoubleSetting maxSelfDamage = addSetting(new DoubleSetting(
            "maxSelfDamage", "Never place/pop if it would deal more than this to you.", 9.0, 1.0, 20.0, 0.5));
    private final BoolSetting placeObsidian = addSetting(new BoolSetting(
            "placeObsidian", "If no obsidian base is near the target, place one from your inventory.", true));

    private int placeCooldown = 0;
    private int noBaseWarnCd = 0;

    public AutoCrystalModule() {
        super("AutoCrystal", "Real crystal PvP — places obsidian-based crystals then pops them.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        placeCooldown = 0;
        noBaseWarnCd = 0;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;
        assert client.gameMode != null;

        Optional<LivingEntity> targetOpt = EntityUtil.getNearestTarget(client, POP_RANGE + PLACE_RADIUS);
        if (targetOpt.isEmpty()) return;
        LivingEntity target = targetOpt.get();

        float maxSelf = (float) (double) maxSelfDamage.get();
        float minDmg  = (float) (double) minDamage.get();

        // ── Step 1: pop the best existing crystal ─────────────────────────
        EndCrystal bestPop = CombatUtil.bestCrystalTarget(client, target, maxSelf, POP_RANGE);
        if (bestPop != null) {
            float dmg = CombatUtil.estimateCrystalDamage(bestPop.position(), target);
            if (dmg >= minDmg) {
                EntityUtil.lookAt(client, bestPop);
                CombatUtil.attack(client, bestPop);   // left-click = detonate
            }
        }

        // ── Step 2: place a new crystal ───────────────────────────────────
        if (placeCooldown > 0) { placeCooldown--; return; }
        if (!InventoryUtil.hasItem(client, Items.END_CRYSTAL)) return;

        List<BlockPos> bases = BlockUtil.getCrystalPlacementPositions(
                client.level, target.position(), PLACE_RADIUS);
        if (bases.isEmpty()) {
            // No base near the target: place obsidian ourselves if we have it.
            if (placeObsidian.get() && InventoryUtil.hasItem(client, Items.OBSIDIAN)) {
                if (tryPlaceObsidian(client, target)) { placeCooldown = 2; return; }
            }
            if (noBaseWarnCd-- <= 0) {
                client.player.sendSystemMessage(Component.literal(
                        "§e[MC BOT] Crystal: no obsidian/bedrock base near target"
                        + (InventoryUtil.hasItem(client, Items.OBSIDIAN) ? "." : " (and no obsidian to place).")));
                noBaseWarnCd = 60;
            }
            return;
        }

        // Pick the base that maximises (enemyDamage - 0.5*selfDamage) within reach.
        BlockPos bestBase = bases.stream()
                .filter(p -> BlockUtil.distanceTo(client, p) <= PLACE_RANGE)
                .max(Comparator.comparingDouble(p -> {
                    Vec3 crystal = Vec3.atCenterOf(p).add(0, 1, 0);
                    float dmg  = CombatUtil.estimateCrystalDamage(crystal, target);
                    float self = CombatUtil.estimateSelfDamage(client, crystal);
                    if (self > maxSelf) return -999.0;
                    if (dmg  < minDmg)  return -999.0;
                    return (double) dmg - self * 0.5;
                }))
                .orElse(null);
        if (bestBase == null) return;

        // Need the crystal selected on the hotbar before we can place it.
        if (!InventoryUtil.switchToEndCrystal(client)) return;

        // Right-click the TOP face of the base block to spawn the crystal on it.
        Vec3 topFace = new Vec3(bestBase.getX() + 0.5, bestBase.getY() + 1.0, bestBase.getZ() + 0.5);
        EntityUtil.lookAt(client, topFace);

        BlockHitResult hit = new BlockHitResult(topFace, Direction.UP, bestBase, false);
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);

        if (result.consumesAction()) {
            client.player.swing(InteractionHand.MAIN_HAND);
            placeCooldown = 1; // brief delay; crystal entity appears next tick to pop
        }
    }

    /**
     * Places an obsidian block near the target so a crystal can sit on it. Finds an air spot at the
     * target's feet level with solid ground beneath and air above, then right-clicks the block below
     * it (top face) with obsidian selected. Returns true if a placement was attempted.
     */
    private boolean tryPlaceObsidian(Minecraft client, LivingEntity target) {
        if (!InventoryUtil.switchToItem(client, Items.OBSIDIAN)) return false;
        BlockPos feet = BlockPos.containing(target.position());

        for (int dy = 0; dy >= -1; dy--) {
            for (int[] off : new int[][] { {0,0}, {1,0}, {-1,0}, {0,1}, {0,-1} }) {
                BlockPos p = feet.offset(off[0], dy, off[1]);         // where obsidian should go
                BlockPos support = p.below();                        // block we click against
                if (!BlockUtil.isAir(client.level, p)) continue;
                if (!BlockUtil.isAir(client.level, p.above())) continue; // room for the crystal
                if (BlockUtil.isAir(client.level, support)) continue;    // need something to click
                if (BlockUtil.distanceTo(client, p) > PLACE_RANGE) continue;

                Vec3 topFace = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
                EntityUtil.lookAt(client, topFace);
                BlockHitResult hit = new BlockHitResult(topFace, Direction.UP, support, false);
                InteractionResult r = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                if (r.consumesAction()) {
                    client.player.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onDisable() {
        placeCooldown = 0;
    }
}
