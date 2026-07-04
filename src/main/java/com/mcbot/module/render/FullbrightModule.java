package com.mcbot.module.render;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;

/**
 * Fullbright — maxes out brightness so caves/nights are fully lit. Sets the vanilla gamma option
 * to its brightest and restores your previous value on disable. Meteor-equivalent (gamma method).
 */
public class FullbrightModule extends Module {

    private double previousGamma = 1.0;
    private boolean saved = false;

    public FullbrightModule() {
        super("Fullbright", "Maximum brightness — see in the dark.", ModuleCategory.RENDER);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (!saved) { previousGamma = mc.options.gamma().get(); saved = true; }
        mc.options.gamma().set(1.0); // vanilla-max brightness
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (saved) { mc.options.gamma().set(previousGamma); saved = false; }
    }

    @Override
    protected void onTick(Minecraft client) {
        // Keep it pinned in case something else lowered it.
        if (client.options.gamma().get() < 1.0) client.options.gamma().set(1.0);
    }
}
