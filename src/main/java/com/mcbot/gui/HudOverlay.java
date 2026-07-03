package com.mcbot.gui;

import com.mcbot.module.ModuleManager;

/** HudOverlay - active-module list. Disabled during the 26.1 port (HUD API reworked). */
public class HudOverlay {
    private final ModuleManager moduleManager;
    public HudOverlay(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }
}
