package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.CombatUtil;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.MovementUtil;
import com.mcbot.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * KillAura — full "god PvP" melee combat, not just a swing timer.
 *
 * Per-hit cycle:
 *   1. Aim at the target and pick the best weapon on the hotbar.
 *   2. Sprint + circle-strafe around the target (direction flips on a timer or
 *      when movement stalls against a wall) so the bot is a moving target.
 *   3. When the attack cooldown is full, jump and attack on the way down for a
 *      critical hit (extra damage).
 *   4. Sprint-reset (W-tap) on the tick after each hit so every swing lands as
 *      a fresh sprint-hit with maximum knockback.
 *
 * This replaces the old version that only W-tapped on flat ground, which
 * produced low-damage sweep hits with no crits, no knockback and no movement.
 */
public class KillAuraModule extends Module {

    private static final double REACH            = 4.0;  // max hit distance
    private static final double STRAFE_RANGE      = 2.9; // orbit distance to hold
    private static final float  CHARGE_THRESHOLD  = 0.92f;
    private static final int    DIR_FLIP_TICKS    = 26;  // re-roll strafe direction

    private int strafeDir = 1;        // +1 right, -1 left
    private int dirTimer = 0;
    private boolean pendingSprintReset = false;
    private boolean jumpedForCrit = false;

    public KillAuraModule() {
        super("KillAura", "God-tier melee: circle-strafe, jump-crits, knockback combos.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        dirTimer = 0;
        pendingSprintReset = false;
        jumpedForCrit = false;
    }

    @Override
    protected void onDisable() {
        Minecraft client = Minecraft.getInstance();
        MovementUtil.releaseAll(client);
        if (client.player != null) client.player.setSprinting(false);
    }

    @Override
    protected void onTick(Minecraft client) {
        Optional<LivingEntity> targetOpt = EntityUtil.getNearestTarget(client, REACH + STRAFE_RANGE + 4.0);
        if (targetOpt.isEmpty()) {
            // No target → stop driving the player so manual control returns.
            MovementUtil.stopStrafe(client);
            return;
        }

        LivingEntity target = targetOpt.get();

        // ── Aim + weapon ──────────────────────────────────────────────────
        // Route through RotationManager so SilentAim (if on) aims server-side without
        // turning the camera; otherwise this rotates the camera like before.
        RotationManager.aimAt(client, target);
        CombatUtil.switchToBestWeapon(client);

        // ── Sprint-reset bookkeeping (runs the tick after a hit) ──────────
        if (pendingSprintReset) {
            MovementUtil.sprintReset(client);   // drop sprint for this one tick
            pendingSprintReset = false;
        } else {
            // Keep sprint engaged so strafing/attacks count as sprint hits.
            client.player.setSprinting(true);
            MovementUtil.sprint(client, true);
        }

        // ── Movement: circle-strafe around the target ─────────────────────
        if (--dirTimer <= 0) {
            strafeDir = (Math.random() < 0.5) ? -1 : 1;
            dirTimer = DIR_FLIP_TICKS;
        }
        // Flip away from walls: if barely moving horizontally, reverse.
        double hSpeed = client.player.getDeltaMovement().horizontalDistance();
        if (hSpeed < 0.03) { strafeDir = -strafeDir; dirTimer = DIR_FLIP_TICKS; }
        MovementUtil.circleStrafe(client, target, strafeDir, STRAFE_RANGE);

        // ── Attack timing ─────────────────────────────────────────────────
        boolean charged = CombatUtil.isCooldownReady(client, CHARGE_THRESHOLD);
        double dist = EntityUtil.distance(client, target);
        if (!charged || dist > REACH) return;

        // Try to land the hit as a critical: jump when grounded, strike on descent.
        if (MovementUtil.canCritJump(client) && !jumpedForCrit) {
            MovementUtil.critJump(client);
            jumpedForCrit = true;
            return; // wait until we're falling to swing
        }

        boolean inCrit = MovementUtil.inCritWindow(client);
        // Strike if we're in the crit window, or if a crit isn't available
        // (e.g. mid-air from terrain) so we never stall and let the enemy hit us.
        if (inCrit || !MovementUtil.canCritJump(client)) {
            CombatUtil.attack(client, target);
            jumpedForCrit = false;
            pendingSprintReset = true; // reset sprint next tick for max knockback
        }
    }
}
