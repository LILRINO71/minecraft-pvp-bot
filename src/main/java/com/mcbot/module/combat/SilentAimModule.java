package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.DoubleSetting;
import com.mcbot.settings.ModeSetting;
import com.mcbot.util.RotationManager;
import net.minecraft.client.Minecraft;

/**
 * SilentAim — controls HOW the combat modules aim. A modifier: it pushes its settings into
 * {@link RotationManager}, which KillAura (and others) aim through.
 *
 * <p>With mode = Silent, hits land on the target while your camera never turns, and the aim
 * <b>tugs</b> toward the target at {@code speed} degrees/tick rather than snapping/locking — set
 * a low speed (e.g. 8-15) for a legit, human-looking pull.
 */
public class SilentAimModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting(
            "mode", "Snap = instant camera, Smooth = camera tug, Silent = server-only tug (no camera).",
            RotationManager.SILENT, RotationManager.SNAP, RotationManager.SMOOTH, RotationManager.SILENT));

    private final DoubleSetting speed = addSetting(new DoubleSetting(
            "speed", "Degrees turned toward the target per tick (lower = smoother/legit).",
            12.0, 1.0, 180.0, 1.0));

    public SilentAimModule() {
        super("SilentAim", "Aim at targets without snapping — smooth tug, optionally server-side only.",
                ModuleCategory.COMBAT);
    }

    private void apply() {
        RotationManager.setMode(mode.get());
        RotationManager.setSpeed(speed.get());
    }

    @Override
    protected void onEnable() {
        apply();
        RotationManager.reset(Minecraft.getInstance());
    }

    @Override
    protected void onDisable() {
        RotationManager.setMode(RotationManager.SNAP);
    }

    @Override
    protected void onTick(Minecraft client) {
        apply(); // keep RotationManager in sync with live setting changes
    }
}
