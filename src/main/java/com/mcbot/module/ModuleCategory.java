package com.mcbot.module;

public enum ModuleCategory {
    COMBAT("⚔ Combat"),
    WORLD("⛏ Level"),
    PLAYER("🧍 Player"),
    MOVEMENT("🚀 Movement"),
    RENDER("👁 Render"),
    AI("🧠 AI Brain");

    public final String displayName;
    ModuleCategory(String displayName) { this.displayName = displayName; }
}
