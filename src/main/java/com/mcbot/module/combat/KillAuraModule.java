package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.settings.DoubleSetting;
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

    private static final double STRAFE_RANGE      = 2.9; // orbit distance to hold
    private static final int    DIR_FLIP_TICKS    = 26;  // re-roll strafe direction

    private final DoubleSetting reach = addSetting(new DoubleSetting(
            "reach", "Max distance to hit a target (vanilla ~3.0, server-safe <= 3.0).", 3.0, 2.5, 6.0, 0.1));
    private final DoubleSetting charge = addSetting(new DoubleSetting(
            "charge", "How charged the cooldown must be to swing (1.0 = fully, max damage).", 0.92, 0.1, 1.0, 0.01));
    private final BoolSetting strafe = addSetting(new BoolSetting(
            "strafe", "Circle-strafe around the target (off = stand still, more legit).", true));
    private final BoolSetting criticals = addSetting(new BoolSetting(
            "criticals", "Jump before hits to land criticals.", true));
    private final BoolSetting sprintReset = addSetting(new BoolSetting(
            "sprintReset", "Drop sprint for one tick after each hit for full knockback (anti-sweep).", true));

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
        Optional<LivingEntity> targetOpt = EntityUtil.getNearestTarget(client, reach.get() + STRAFE_RANGE + 4.0);
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
        if (sprintReset.get() && pendingSprintReset) {
            MovementUtil.sprintReset(client);   // drop sprint for this one tick
            pendingSprintReset = false;
        } else {
            // Keep sprint engaged so strafing/attacks count as sprint hits.
            client.player.setSprinting(true);
            MovementUtil.sprint(client, true);
            pendingSprintReset = false;
        }

        // ── Movement: circle-strafe around the target (optional) ──────────
        if (strafe.get()) {
            if (--dirTimer <= 0) {
                strafeDir = (Math.random() < 0.5) ? -1 : 1;
                dirTimer = DIR_FLIP_TICKS;
            }
            // Flip away from walls: if barely moving horizontally, reverse.
            double hSpeed = client.player.getDeltaMovement().horizontalDistance();
            if (hSpeed < 0.03) { strafeDir = -strafeDir; dirTimer = DIR_FLIP_TICKS; }
            MovementUtil.circleStrafe(client, target, strafeDir, STRAFE_RANGE);
        } else {
            MovementUtil.stopStrafe(client);
        }

        // ── Attack timing ─────────────────────────────────────────────────
        boolean charged = CombatUtil.isCooldownReady(client, (float) (double) charge.get());
        double dist = EntityUtil.distance(client, target);
        if (!charged || dist > reach.get()) return;

        // Try to land the hit as a critical: jump when grounded, strike on descent.
        if (criticals.get() && MovementUtil.canCritJump(client) && !jumpedForCrit) {
            MovementUtil.critJump(client);
            jumpedForCrit = true;
            return; // wait until we're falling to swing
        }

        boolean inCrit = !criticals.get() || MovementUtil.inCritWindow(client);
        // Strike if we're in the crit window, or if a crit isn't available
        // (e.g. mid-air from terrain) so we never stall and let the enemy hit us.
        if (inCrit || !MovementUtil.canCritJump(client)) {
            CombatUtil.attack(client, target);
            jumpedForCrit = false;
            pendingSprintReset = true; // reset sprint next tick for max knockback
        }
    }
}
