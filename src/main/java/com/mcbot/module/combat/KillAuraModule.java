package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.settings.DoubleSetting;
import com.mcbot.settings.IntSetting;
import com.mcbot.targeting.TargetConfig;
import com.mcbot.util.CombatUtil;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.MovementUtil;
import com.mcbot.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * KillAura — real melee combat: circle-strafe, jump-crits, and W-tap/S-tap sprint-reset combos.
 *
 * <p>Each tick it sets the full movement key-state via {@link MovementUtil#setKeys} (so nothing
 * sticks), orbits the target on a diagonal (W + A/D) so sprint actually engages, jumps when charged
 * and strikes ONLY on the descent for a critical, then W-taps (or S-taps) for one to two ticks after
 * the hit so the next swing lands as a fresh sprint-hit with full knockback.
 *
 * <p>This fixes the previous version, which held the jump key forever (bunny-hopping), attacked on
 * the way UP (never critting), and strafed sideways-only (so sprint never engaged → sweep hits).
 */
public class KillAuraModule extends Module {

    private static final int DIR_FLIP_TICKS = 30;

    private final DoubleSetting reach = addSetting(new DoubleSetting(
            "reach", "Max distance to hit (vanilla ~3.0; keep <= 3.0 for servers).", 3.0, 2.5, 6.0, 0.1));
    private final DoubleSetting charge = addSetting(new DoubleSetting(
            "charge", "Cooldown charge required to swing (1.0 = full damage).", 0.95, 0.1, 1.0, 0.01));
    private final BoolSetting strafe = addSetting(new BoolSetting(
            "strafe", "Circle-strafe around the target.", true));
    private final DoubleSetting strafeRange = addSetting(new DoubleSetting(
            "strafeRange", "Distance to orbit the target at.", 2.6, 1.5, 4.0, 0.1));
    private final BoolSetting criticals = addSetting(new BoolSetting(
            "criticals", "Jump and strike on the way down for critical hits.", true));
    private final BoolSetting wtap = addSetting(new BoolSetting(
            "wtap", "Sprint-reset after each hit for full knockback (anti-sweep).", true));
    private final BoolSetting sTap = addSetting(new BoolSetting(
            "sTap", "Use S-tap (pull back) instead of W-tap for spacing.", false));

    private int strafeDir = 1;
    private int dirTimer = 0;
    private boolean jumpedForCrit = false;
    private int comboTicks = 0;   // >0 = mid W-tap/S-tap after a hit

    public KillAuraModule() {
        super("KillAura", "Circle-strafe, jump-crits, and W-tap/S-tap knockback combos.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        dirTimer = 0;
        jumpedForCrit = false;
        comboTicks = 0;
        // Common gotcha: players aren't targeted by default. Tell the user so KillAura isn't "dead".
        TargetConfig tc = TargetConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !tc.attackPlayers) {
            mc.player.sendSystemMessage(Component.literal(
                    "§e[MC BOT] KillAura on. Targeting: §f" + tc.getSummary()
                    + "§e. For PvP run §f#bot target players on"));
        }
    }

    @Override
    protected void onDisable() {
        MovementUtil.releaseAll(Minecraft.getInstance());
    }

    @Override
    protected void onTick(Minecraft client) {
        var player = client.player;
        Optional<LivingEntity> targetOpt = EntityUtil.getNearestTarget(client, reach.get() + strafeRange.get() + 4.0);
        if (targetOpt.isEmpty()) {
            MovementUtil.releaseAll(client);   // hand control back to the player
            jumpedForCrit = false;
            comboTicks = 0;
            return;
        }
        LivingEntity target = targetOpt.get();

        double dist = EntityUtil.distance(client, target);
        boolean charged = CombatUtil.isCooldownReady(client, (float) (double) charge.get());
        boolean inRange = dist <= reach.get();
        boolean grounded = player.onGround();
        boolean falling = MovementUtil.inCritWindow(client);

        // ── Aim + weapon ──────────────────────────────────────────────────
        RotationManager.aimAt(client, target);
        if (AttributeSwapModule.isActive()) AttributeSwapModule.holdCarry(client);
        else CombatUtil.switchToBestWeapon(client);

        // ── Decide the full movement key-state for this tick ──────────────
        boolean wFwd = false, wBack = false, wLeft = false, wRight = false, wJump = false, wSprint = false;

        if (strafe.get()) {
            double orbit = strafeRange.get();
            if (dist > orbit + 0.3) wFwd = true;        // move in
            else if (dist < orbit - 0.6) wBack = true;   // too close, ease back

            // Flip orbit direction on a timer, or when we stall against a wall while pushing in.
            if (--dirTimer <= 0) { strafeDir = (Math.random() < 0.5) ? -1 : 1; dirTimer = DIR_FLIP_TICKS; }
            if (wFwd && player.getDeltaMovement().horizontalDistance() < 0.02) {
                strafeDir = -strafeDir; dirTimer = DIR_FLIP_TICKS;
            }
            if (strafeDir < 0) wLeft = true; else wRight = true;
            wSprint = wFwd;   // sprint only counts when moving forward
        }

        // ── W-tap / S-tap: break momentum for a tick or two after a hit ───
        if (comboTicks > 0) {
            comboTicks--;
            wSprint = false;
            if (sTap.get()) { wFwd = false; wBack = true; }  // pull back for spacing
            else            { wFwd = false; }                // just drop forward (sprint reset)
        }

        // ── Jump for a crit: only when grounded, charged, in range, not mid-combo ──
        if (criticals.get() && charged && inRange && grounded && !jumpedForCrit && comboTicks == 0) {
            wJump = true;
            jumpedForCrit = true;
        }

        MovementUtil.setKeys(client, wFwd, wBack, wLeft, wRight, wJump, wSprint);

        // ── Attack timing ─────────────────────────────────────────────────
        if (!charged || !inRange) return;

        boolean doHit;
        if (criticals.get()) {
            if (jumpedForCrit) {
                if (falling)       doHit = true;                 // descending → critical hit
                else if (grounded) { jumpedForCrit = false; doHit = false; } // landed w/o hitting, retry
                else               doHit = false;                // still rising, wait
            } else {
                // No jump queued: hit if we're already descending, or if a crit-jump isn't possible.
                doHit = falling || !MovementUtil.canCritJump(client);
            }
        } else {
            doHit = true;
        }

        if (doHit) {
            if (AttributeSwapModule.isActive()) AttributeSwapModule.strike(client, target);
            else CombatUtil.attack(client, target);
            jumpedForCrit = false;
            comboTicks = wtap.get() ? 2 : 0;   // start the W-tap/S-tap window
        }
    }
}
