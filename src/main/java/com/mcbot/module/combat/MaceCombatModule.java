package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.CombatUtil;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.InventoryUtil;
import com.mcbot.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * MaceCombat — Elytra + Mace dive-bomb.
 *
 *   IDLE     → target found, has mace + elytra → TAKEOFF
 *   TAKEOFF  → hop off the ground and start gliding → ASCENDING
 *   ASCENDING→ rocket-boost straight up until DROP_HEIGHT above target → DIVING
 *   DIVING   → aim down at target, steep dive, build fall distance → SMASHING
 *   SMASHING → mace hit lands → IDLE
 *
 * The old version got stuck in ASCENDING staring at the sky because it never
 * actually gained altitude (no elytra deploy, no firework boost) — so it also
 * never reached SMASHING and never printed anything. This version really flies.
 */
public class MaceCombatModule extends Module {

    private static final double TARGET_RANGE = 28.0;
    private static final float  DROP_HEIGHT  = 18.0f;  // blocks above target to climb
    private static final double DIVE_SPEED   = 1.6;    // extra downward m/tick
    private static final double SMASH_DIST    = 3.0;
    private static final int    BOOST_CD      = 12;    // ticks between rocket boosts

    private enum State { IDLE, TAKEOFF, ASCENDING, DIVING, SMASHING }
    private State state = State.IDLE;
    private LivingEntity target = null;
    private double targetY = 0;
    private int stateTimer = 0;
    private int boostTimer = 0;

    public MaceCombatModule() {
        super("MaceCombat", "Elytra climb → mace dive-bomb. One-shots most targets from height.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        target = null;
        boostTimer = 0;
    }

    @Override
    protected void onDisable() {
        MovementUtil.releaseAll(Minecraft.getInstance());
        state = State.IDLE;
        target = null;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        stateTimer++;
        if (boostTimer > 0) boostTimer--;

        switch (state) {
            case IDLE      -> tickIdle(client);
            case TAKEOFF   -> tickTakeoff(client);
            case ASCENDING -> tickAscending(client);
            case DIVING    -> tickDiving(client);
            case SMASHING  -> tickSmashing(client);
        }
    }

    // ── IDLE ──────────────────────────────────────────────────────────────
    private void tickIdle(Minecraft client) {
        Optional<LivingEntity> t = EntityUtil.getNearestTarget(client, TARGET_RANGE);
        if (t.isEmpty()) return;

        if (!InventoryUtil.hasInHotbar(client, Items.MACE)) {
            announce(client, "§cMace: no mace on hotbar.");
            return;
        }
        boolean hasElytra = client.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        if (!hasElytra) { announce(client, "§cMace: no elytra equipped."); return; }
        if (InventoryUtil.findInHotbar(client, Items.FIREWORK_ROCKET) < 0) {
            announce(client, "§cMace: no firework rockets on hotbar.");
            return;
        }

        target = t.get();
        targetY = target.getY();
        transition(State.TAKEOFF);
        announce(client, "§b[MC BOT] Mace: taking off…");
    }

    // ── TAKEOFF ─────────────────────────────────────────────────────────
    private void tickTakeoff(Minecraft client) {
        if (lostTarget(client) || stateTimer > 60) { abort(client); return; }

        if (client.player.isFallFlying()) {
            transition(State.ASCENDING);
            announce(client, "§b[MC BOT] Mace: climbing " + (int) DROP_HEIGHT + " blocks…");
            return;
        }

        // Hop off the ground, then attempt to deploy the elytra mid-air.
        if (client.player.onGround()) {
            MovementUtil.jump(client, true);
        } else {
            MovementUtil.jump(client, false);
            client.player.tryToStartFallFlying(); // sends start-glide to the server
            boostRocket(client);                  // kick in a rocket to get moving
        }
    }

    // ── ASCENDING ─────────────────────────────────────────────────────────
    private void tickAscending(Minecraft client) {
        if (lostTarget(client) || stateTimer > 140) { abort(client); return; }
        if (!client.player.isFallFlying()) { transition(State.TAKEOFF); return; }

        // Aim steeply upward and keep boosting with rockets until high enough.
        client.player.setXRot(-75f);
        boostRocket(client);

        if (client.player.getY() >= targetY + DROP_HEIGHT) {
            transition(State.DIVING);
            announce(client, "§b[MC BOT] Mace: diving!");
        }
    }

    // ── DIVING ──────────────────────────────────────────────────────────
    private void tickDiving(Minecraft client) {
        if (lostTarget(client) || stateTimer > 80) { abort(client); return; }

        // Track the target and dive steeply, adding downward velocity to build
        // fall distance (the mace's damage scales with how far you've fallen).
        EntityUtil.lookAt(client, target);
        client.player.setXRot(82f);
        Vec3 v = client.player.getDeltaMovement();
        client.player.setDeltaMovement(v.x, Math.min(v.y, -DIVE_SPEED), v.z);

        if (EntityUtil.distance(client, target) <= SMASH_DIST) {
            transition(State.SMASHING);
        }
    }

    // ── SMASHING ────────────────────────────────────────────────────────
    private void tickSmashing(Minecraft client) {
        if (lostTarget(client)) { abort(client); return; }

        CombatUtil.switchToMace(client);
        EntityUtil.lookAt(client, target);

        if (CombatUtil.isCooldownReady(client, 0.85f)) {
            float fall = client.player.fallDistance;
            CombatUtil.attack(client, target);
            float dmg = CombatUtil.estimateMaceDamage(fall);
            announce(client, String.format("§a[MC BOT] Mace SMASH! ~%.0f dmg from %.1f blocks", dmg, fall));
            abort(client);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void boostRocket(Minecraft client) {
        if (boostTimer > 0) return;
        if (InventoryUtil.switchToItem(client, Items.FIREWORK_ROCKET) && client.gameMode != null) {
            client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            boostTimer = BOOST_CD;
        }
    }

    private boolean lostTarget(Minecraft client) {
        return target == null || !target.isAlive()
                || EntityUtil.distance(client, target) > TARGET_RANGE + 16;
    }

    private void transition(State s) { state = s; stateTimer = 0; }

    private void abort(Minecraft client) {
        MovementUtil.jump(client, false);
        transition(State.IDLE);
        target = null;
    }

    private void announce(Minecraft client, String msg) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(msg));
    }
}
