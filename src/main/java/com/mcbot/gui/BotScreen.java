package com.mcbot.gui;

import com.mcbot.module.ModuleManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * BotScreen - Right-Shift panel. Minimal during the 26.1 port (GUI render API reworked).
 * Toggle modules via keybinds (K/C/V) or chat commands (.bot &lt;module&gt;).
 */
public class BotScreen extends Screen {
    private final ModuleManager moduleManager;
    public BotScreen(ModuleManager moduleManager) {
        super(Component.literal("MC BOT"));
        this.moduleManager = moduleManager;
    }
    @Override
    public boolean isPauseScreen() { return false; }
}
