package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * ElytraFlight — smart elytra controller.
 *
 * Features:
 *  - Auto-deploys elytra when falling (no more crash landings)
 *  - Maintains altitude via pitch control
 *  - Firework boost management (fires when speed < threshold)
 *  - Smooth landing detection
 */
public class ElytraFlightModule extends Module {

    private static final double MIN_SPEED       = 0.4;   // m/tick; below this → use firework
    private static final double SAFE_LAND_SPEED = 0.5;   // close to ground, slow down
    private static final double AUTO_DEPLOY_VEL = -0.3;  // y-velocity to auto-open
    private static final int    BOOST_COOLDOWN  = 40;    // ticks between firework boosts

    private int boostTimer = 0;
    private boolean wasGliding = false;

    public ElytraFlightModule() {
        super("ElytraFlight", "Smart elytra controller with auto-deploy, altitude hold, and firework boosts.", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        boostTimer = 0;
        wasGliding = false;
    }

    @Override
    protected void onDisable() {
        wasGliding = false;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;

        boolean hasElytra = client.player.getItemBySlot(EquipmentSlot.CHEST)
                .getItem() == Items.ELYTRA;
        if (!hasElytra) return;

        Vec3 vel = client.player.getDeltaMovement();
        boolean isGliding = client.player.isFallFlying();

        // ── Auto-deploy ───────────────────────────────────────────────────
        if (!isGliding && !client.player.onGround()
                && vel.y < AUTO_DEPLOY_VEL
                && client.player.getY() > 10) {
            // Press jump to open elytra
            client.player.setJumping(true);
            return;
        }

        if (!isGliding) return;

        double speed = new Vec3(vel.x, 0, vel.z).length(); // horizontal speed

        // ── Firework boost when too slow ─────────────────────────────────
        if (boostTimer > 0) boostTimer--;
        if (speed < MIN_SPEED && boostTimer == 0) {
            useFireworkBoost(client);
            boostTimer = BOOST_COOLDOWN;
        }

        // ── Altitude maintenance ──────────────────────────────────────────
        // Check terrain below; if within SAFE altitude, pitch up to gain height
        double groundDist = estimateGroundDistance(client);
        if (groundDist < 15.0 && vel.y < 0) {
            // Pitch up to slow descent
            float currentPitch = client.player.getXRot();
            client.player.setXRot(Math.max(currentPitch - 2f, -30f));
        } else if (groundDist > 50.0) {
            // Safe to descend slightly
            float currentPitch = client.player.getXRot();
            if (currentPitch < 15f) {
                client.player.setXRot(currentPitch + 0.5f);
            }
        }

        wasGliding = true;
    }

    private void useFireworkBoost(Minecraft client) {
        assert client.player != null;
        // Switch to firework rocket in hotbar
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).getItem() == Items.FIREWORK_ROCKET) {
                client.player.getInventory().setSelectedSlot(i);
                // Use the firework
                assert client.gameMode != null;
                client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                break;
            }
        }
    }

    private double estimateGroundDistance(Minecraft client) {
        assert client.player != null;
        assert client.level != null;
        double py = client.player.getY();
        // Raycast downward (simplified: check blocks below)
        for (int dy = 1; dy <= 100; dy++) {
            net.minecraft.core.BlockPos check = net.minecraft.core.BlockPos.containing(
                    client.player.getX(), py - dy, client.player.getZ()
            );
            if (!client.level.getBlockState(check).isAir()) return dy;
        }
        return 100.0;
    }
}
