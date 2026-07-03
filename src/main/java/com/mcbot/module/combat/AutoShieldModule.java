package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * AutoShield — automatically blocks with shield when taking damage or
 * when a projectile is incoming. Releases to allow attacking.
 *
 * Logic:
 *  - If an arrow / projectile is heading toward the player → raise shield
 *  - If an enemy is in melee range and attacking → block
 *  - Release to attack, re-raise immediately after
 */
public class AutoShieldModule extends Module {

    private static final double PROJECTILE_SCAN_RANGE = 16.0;
    private static final double MELEE_RANGE           = 4.5;
    private static final int    BLOCK_HOLD_TICKS      = 3;

    private boolean shielding = false;
    private int holdTicks = 0;
    private int offhandShieldSlot = -1;

    public AutoShieldModule() {
        super("AutoShield", "Raises shield when projectiles or attacks are incoming.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onDisable() {
        // Release shield
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.stopUsingItem();
        }
        shielding = false;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;

        // Check if shield is in offhand
        boolean hasShield = client.player.getOffhandItem().getItem() == Items.SHIELD;
        if (!hasShield) return;

        boolean shouldBlock = false;

        // ── Check for incoming projectiles ────────────────────────────────
        Vec3 playerPos = client.player.position();
        AABB scanBox = new AABB(
                playerPos.x - PROJECTILE_SCAN_RANGE, playerPos.y - PROJECTILE_SCAN_RANGE,
                playerPos.z - PROJECTILE_SCAN_RANGE,
                playerPos.x + PROJECTILE_SCAN_RANGE, playerPos.y + PROJECTILE_SCAN_RANGE,
                playerPos.z + PROJECTILE_SCAN_RANGE
        );

        List<Projectile> projectiles = client.level.getEntitiesOfClass(
                Projectile.class, scanBox, p -> true
        );

        for (Projectile proj : projectiles) {
            Vec3 vel = proj.getDeltaMovement();
            Vec3 projPos = proj.position();
            // Check if moving toward player
            Vec3 toPlayer = playerPos.subtract(projPos).normalize();
            double dot = vel.normalize().dot(toPlayer);
            if (dot > 0.7) { // heading toward us
                shouldBlock = true;
                break;
            }
        }

        // ── Check for nearby enemies ──────────────────────────────────────
        if (!shouldBlock) {
            Optional<LivingEntity> nearestEnemy = EntityUtil.getNearestTarget(
                    client, MELEE_RANGE, true, true);
            if (nearestEnemy.isPresent()) {
                // Block preemptively in melee range
                shouldBlock = true;
            }
        }

        // ── Apply block state ─────────────────────────────────────────────
        if (shouldBlock && !shielding) {
            // Raise shield via offhand use
            client.options.keyUse.setDown(true);
            shielding = true;
            holdTicks = BLOCK_HOLD_TICKS;
        } else if (shielding) {
            if (holdTicks > 0) {
                holdTicks--;
            } else {
                // Release shield briefly to allow attacking
                client.options.keyUse.setDown(false);
                shielding = false;
            }
        }
    }
}
