package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.RotationManager;
import net.minecraft.client.Minecraft;

/**
 * SilentAim — makes the aiming combat modules (KillAura, etc.) aim server-side only, so your
 * hits land on the target while your camera never turns.
 *
 * <p>This is a modifier: it flips a global flag on {@link RotationManager}. Combat modules that
 * aim via {@code RotationManager.aimAt(...)} will send a rotation packet to the server instead of
 * rotating the camera. Enable it together with KillAura for classic "silent aura".
 */
public class SilentAimModule extends Module {

    public SilentAimModule() {
        super("SilentAim", "Aim at targets server-side without moving your camera (use with KillAura).",
                ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        RotationManager.setSilent(true);
    }

    @Override
    protected void onDisable() {
        RotationManager.setSilent(false);
    }

    @Override
    protected void onTick(Minecraft client) {
        // Pure modifier — the actual silent rotations are sent by the combat modules that aim.
        // Keep the flag in sync in case anything else touched it.
        RotationManager.setSilent(true);
    }
}
