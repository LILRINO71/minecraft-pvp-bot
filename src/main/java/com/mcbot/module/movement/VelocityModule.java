package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Velocity (AntiKnockback) — reduces the knockback you take when hit.
 *
 * <p>On the tick a hit registers ({@code hurtTime} rising to its max), the horizontal component of
 * your velocity is scaled down (default: cancelled) so you barely get pushed, while vertical motion
 * is preserved. This is a client-side reduction of the residual knockback impulse; it is not a
 * full packet cancel, but it noticeably steadies you in melee.
 */
public class VelocityModule extends Module {

    /** 0.0 = cancel horizontal knockback entirely, 1.0 = normal. */
    private static final double HORIZONTAL_MULT = 0.0;
    /** 1.0 = keep vertical knockback (so you can still be bumped up), 0.0 = cancel. */
    private static final double VERTICAL_MULT = 1.0;

    private int prevHurtTime = 0;

    public VelocityModule() {
        super("Velocity", "Reduces the knockback you take when hit (anti-knockback).",
                ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        prevHurtTime = 0;
    }

    @Override
    protected void onTick(Minecraft client) {
        int hurt = client.player.hurtTime;
        // Rising edge to max hurtTime = a fresh hit this tick.
        boolean freshHit = hurt > prevHurtTime;
        prevHurtTime = hurt;
        if (!freshHit) return;

        Vec3 v = client.player.getDeltaMovement();
        client.player.setDeltaMovement(v.x * HORIZONTAL_MULT, v.y * VERTICAL_MULT, v.z * HORIZONTAL_MULT);
    }
}
