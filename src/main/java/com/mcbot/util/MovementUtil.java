package com.mcbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * MovementUtil — drives the player through the vanilla key-binding system
 * ({@code client.options.keyX.setDown(...)}) so movement looks and behaves
 * exactly like a real player pressing W/A/S/D/space/ctrl.
 *
 * Used by combat modules for circle-strafing, sprint-reset (W-tap) knockback,
 * and jump-crit timing. Every module that calls these MUST call
 * {@link #releaseAll(Minecraft)} in onDisable() so it doesn't leave keys stuck.
 */
public final class MovementUtil {

    private MovementUtil() {}

    // ── Raw key control ──────────────────────────────────────────────────

    public static void forward(Minecraft client, boolean down) { client.options.keyUp.setDown(down); }
    public static void back(Minecraft client, boolean down)    { client.options.keyDown.setDown(down); }
    public static void left(Minecraft client, boolean down)    { client.options.keyLeft.setDown(down); }
    public static void right(Minecraft client, boolean down)   { client.options.keyRight.setDown(down); }
    public static void jump(Minecraft client, boolean down)    { client.options.keyJump.setDown(down); }
    public static void sprint(Minecraft client, boolean down)  { client.options.keySprint.setDown(down); }
    public static void sneak(Minecraft client, boolean down)   { client.options.keyShift.setDown(down); }

    /** Release every movement key this util can press. Call on module disable. */
    public static void releaseAll(Minecraft client) {
        forward(client, false);
        back(client, false);
        left(client, false);
        right(client, false);
        jump(client, false);
        sneak(client, false);
        // intentionally do NOT force-disable sprint here — leave the player's own state
    }

    // ── Strafe ───────────────────────────────────────────────────────────

    /**
     * Circle-strafe around a target. The caller is responsible for aiming the
     * crosshair at the target (yaw); this method only presses the movement keys.
     *
     * @param dir +1 to strafe right (clockwise), -1 to strafe left
     * @param desiredRange ideal distance to hold from the target
     */
    public static void circleStrafe(Minecraft client, Entity target, int dir, double desiredRange) {
        if (client.player == null) return;
        double dist = EntityUtil.distance(client, target);

        // Hold the orbit distance: push in when too far, back off when too close.
        if (dist > desiredRange + 0.4) {
            forward(client, true);
            back(client, false);
        } else if (dist < desiredRange - 0.4) {
            forward(client, false);
            back(client, true);
        } else {
            forward(client, false);
            back(client, false);
        }

        // Strafe sideways to orbit.
        if (dir >= 0) { right(client, true);  left(client, false); }
        else          { left(client, true);   right(client, false); }
    }

    /** Stop all strafing input (keeps sprint untouched). */
    public static void stopStrafe(Minecraft client) {
        forward(client, false);
        back(client, false);
        left(client, false);
        right(client, false);
    }

    // ── Knockback / crit helpers ─────────────────────────────────────────

    /**
     * Sprint-reset ("W-tap"): drop sprint for a single tick so the next attack
     * re-triggers a fresh sprint-hit and lands maximum knockback. The caller
     * re-enables sprint on the following tick.
     */
    public static void sprintReset(Minecraft client) {
        if (client.player == null) return;
        sprint(client, false);
        client.player.setSprinting(false);
    }

    /**
     * Returns true when a jump-crit is currently possible (on ground, charged,
     * not in water/lava, not gliding, not riding). The caller should jump THIS
     * tick and attack on the way down for the crit + knockback combo.
     */
    public static boolean canCritJump(Minecraft client) {
        if (client.player == null) return false;
        return client.player.onGround()
                && !client.player.isInWater()
                && !client.player.isInLava()
                && !client.player.isFallFlying()
                && !client.player.isPassenger()
                && !client.player.onClimbable();
    }

    /** Begin a crit jump (presses the jump key for this tick). */
    public static void critJump(Minecraft client) {
        jump(client, true);
    }

    /**
     * True on the descending part of a jump — the window where an attack lands
     * as a critical hit (player airborne, moving downward, not gliding).
     */
    public static boolean inCritWindow(Minecraft client) {
        if (client.player == null) return false;
        Vec3 v = client.player.getDeltaMovement();
        return !client.player.onGround()
                && !client.player.isFallFlying()
                && v.y < 0.0
                && client.player.fallDistance > 0.0f;
    }
}
