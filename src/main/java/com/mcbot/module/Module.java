package com.mcbot.module;

import com.mcbot.settings.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every MC BOT module.
 * Subclasses override onEnable(), onDisable(), onTick() and declare tunable
 * values with addSetting(...) — those show up in the ClickGUI submenu and
 * are settable via "#bot set <module> <setting> <value>".
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled = false;

    protected Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    // ── Settings ─────────────────────────────────────────────────────────

    /** Register a tunable setting (call from the subclass constructor). */
    protected <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }

    /** Case-insensitive setting lookup, or null. */
    public Setting<?> getSetting(String settingName) {
        for (Setting<?> s : settings) {
            if (s.getName().equalsIgnoreCase(settingName)) return s;
        }
        return null;
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
