package com.mcbot.module;

import net.minecraft.client.Minecraft;

/**
 * Base class for every MC BOT module.
 * Subclasses override onEnable(), onDisable(), onTick().
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final ModuleCategory category;
    private boolean enabled = false;

    protected Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    public final void toggle() {
        if (enabled) disable(); else enable();
    }

    public final void enable() {
        if (enabled) return;
        enabled = true;
        onEnable();
    }

    public final void disable() {
        if (!enabled) return;
        enabled = false;
        onDisable();
    }

    /** Called once per game tick while the module is enabled. */
    public final void tick(Minecraft client) {
        if (!enabled) return;
        if (client.player == null || client.level == null) return;
        onTick(client);
    }

    // ── Override in subclass ─────────────────────────────────────────────

    protected void onEnable() {}
    protected void onDisable() {}
    protected abstract void onTick(Minecraft client);

    // ── Getters ──────────────────────────────────────────────────────────

    public String getName() { return name; }
    public String getDescription() { return description; }
    public ModuleCategory getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
}
