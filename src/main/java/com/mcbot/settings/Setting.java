package com.mcbot.settings;

/**
 * Setting — one tunable value on a module (Meteor-style).
 *
 * <p>Subclasses: {@link BoolSetting}, {@link IntSetting}, {@link DoubleSetting},
 * {@link ModeSetting}. Modules declare them via {@code addSetting(...)}; the ClickGUI renders
 * them in an expandable submenu, chat sets them via {@code #bot set <module> <setting> <value>},
 * and {@link SettingsStore} persists them across restarts.
 */
public abstract class Setting<T> {

    private final String name;
    private final String description;
    protected final T defaultValue;
    protected T value;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    public T get() { return value; }

    public void set(T v) {
        this.value = v;
        SettingsStore.markDirty();
    }

    public void reset() { set(defaultValue); }

    /** Parse a chat-supplied value. Returns false if the input is invalid. */
    public abstract boolean parse(String input);

    /** Short value text shown in the GUI row (e.g. "ON", "4.5", "Smooth"). */
    public abstract String display();

    /**
     * GUI nudge: -1 = decrease/previous/toggle, +1 = increase/next/toggle.
     * Bool toggles either way; numbers step; modes cycle.
     */
    public abstract void adjust(int direction);

    /** Serialized form for the settings file. */
    public String serialize() { return String.valueOf(value); }
}
