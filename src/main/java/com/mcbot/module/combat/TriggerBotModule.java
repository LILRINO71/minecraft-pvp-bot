package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.targeting.TargetConfig;
import com.mcbot.util.CombatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * TriggerBot — automatically attacks whatever entity your crosshair is on, as soon as the attack
 * cooldown is ready. Respects {@link TargetConfig} (won't hit friends/filtered entities).
 *
 * <p>Unlike KillAura it never moves you or aims for you — it only pulls the trigger when you're
 * already looking at a valid target, which makes it far less detectable.
 */
public class TriggerBotModule extends Module {

    /** Only swing when the cooldown is at least this charged (1.0 = fully charged). */
    private static final float CHARGE_THRESHOLD = 0.92f;

    public TriggerBotModule() {
        super("TriggerBot", "Auto-attacks the entity under your crosshair when charged.",
                ModuleCategory.COMBAT);
    }

    @Override
    protected void onTick(Minecraft client) {
        Entity looked = client.crosshairPickEntity;
        if (!(looked instanceof LivingEntity target)) return;
        if (!target.isAlive()) return;
        if (!TargetConfig.get().shouldTarget(target)) return;
        if (!CombatUtil.isCooldownReady(client, CHARGE_THRESHOLD)) return;

        CombatUtil.attack(client, target);
    }
}
