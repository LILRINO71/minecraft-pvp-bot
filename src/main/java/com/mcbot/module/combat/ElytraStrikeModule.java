package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.CombatUtil;
import com.mcbot.util.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * ElytraStrike — aerial dive-bomb attack using elytra momentum.
 *
 * While gliding, the bot targets enemies and performs high-speed
 * strafing attacks. Combines elytra maneuverability with sword hits
 * for difficult-to-dodge hit-and-run patterns.
 */
public class ElytraStrikeModule extends Module {

    private static final double ATTACK_RANGE  = 3.8;
    private static final double TARGET_RANGE  = 20.0;
    private static final float  MIN_SPEED     = 0.6f;  // min velocity to trigger strike

    private LivingEntity currentTarget = null;
    private int strikeDelay = 0;

    public ElytraStrikeModule() {
        super("ElytraStrike", "Aerial sword strikes while gliding — hit-and-run PvP.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        currentTarget = null;
        strikeDelay = 0;
    }

    @Override
    protected void onDisable() {
        currentTarget = null;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;

        // Only active while gliding
        if (!client.player.isFallFlying()) return;

        // Check speed
        Vec3 vel = client.player.getDeltaMovement();
        double speed = vel.length();
        if (speed < MIN_SPEED) return;

        // Find target
        Optional<LivingEntity> targetOpt = EntityUtil.getNearestTarget(client, TARGET_RANGE, true, true);
        if (targetOpt.isEmpty()) return;

        currentTarget = targetOpt.get();

        // Steer toward target
        EntityUtil.lookAt(client, currentTarget);

        // Strike delay
        if (strikeDelay > 0) { strikeDelay--; return; }

        // Check if close enough to strike
        double dist = EntityUtil.distance(client, currentTarget);
        if (dist <= ATTACK_RANGE && CombatUtil.isCooldownReady(client, 0.95f)) {
            CombatUtil.switchToSword(client);
            CombatUtil.attack(client, currentTarget);
            strikeDelay = 3; // brief cooldown between aerial strikes
        }
    }
}
