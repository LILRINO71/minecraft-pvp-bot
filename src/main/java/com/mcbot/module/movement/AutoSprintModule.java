package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;

/**
 * AutoSprint — keeps you sprinting whenever you're moving forward, so you never have to hold it
 * (and every melee hit counts as a sprint-hit). Meteor-equivalent.
 */
public class AutoSprintModule extends Module {

    public AutoSprintModule() {
        super("AutoSprint", "Always sprint while moving forward.", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onTick(Minecraft client) {
        var p = client.player;
        if (p == null) return;
        // Only sprint when actually pressing forward and not sneaking/blind.
        if (client.options.keyUp.isDown() && !p.isShiftKeyDown() && p.getFoodData().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }
}
