package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Step — walk straight up full blocks without jumping (like going up a slab). Raises the player's
 * step-height attribute while enabled and restores vanilla (0.6) on disable. Meteor-equivalent.
 */
public class StepModule extends Module {

    private static final double VANILLA_STEP = 0.6;

    private final DoubleSetting height = addSetting(new DoubleSetting(
            "height", "How many blocks tall a ledge you can step straight up.", 1.0, 0.6, 3.0, 0.5));

    public StepModule() {
        super("Step", "Auto-step up full blocks without jumping.", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onTick(Minecraft client) {
        AttributeInstance attr = client.player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null && attr.getBaseValue() != height.get()) {
            attr.setBaseValue(height.get());
        }
    }

    @Override
    protected void onDisable() {
        var p = Minecraft.getInstance().player;
        if (p != null) {
            AttributeInstance attr = p.getAttribute(Attributes.STEP_HEIGHT);
            if (attr != null) attr.setBaseValue(VANILLA_STEP);
        }
    }
}
