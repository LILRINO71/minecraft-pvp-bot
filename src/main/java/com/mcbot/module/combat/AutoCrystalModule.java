package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
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
    private static final float  MIN_DAMAGE   = 4.0f;   // min enemy damage to bother
    private static final float  MAX_SELF_DMG = 9.0f;   // abort if self-damage too high
    private static final int    PLACE_RADIUS = 4;      // search radius around enemy

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

        // ── Step 1: pop the best existing crystal ─────────────────────────
        EndCrystal bestPop = CombatUtil.bestCrystalTarget(client, target, MAX_SELF_DMG, POP_RANGE);
        if (bestPop != null) {
            float dmg = CombatUtil.estimateCrystalDamage(bestPop.position(), target);
            if (dmg >= MIN_DAMAGE) {
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
            if (noBaseWarnCd-- <= 0) {
                client.player.sendSystemMessage(Component.literal(
                        "§e[MC BOT] Crystal: no obsidian/bedrock base near target."));
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
                    if (self > MAX_SELF_DMG) return -999.0;
                    if (dmg  < MIN_DAMAGE)   return -999.0;
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

    @Override
    protected void onDisable() {
        placeCooldown = 0;
    }
}
