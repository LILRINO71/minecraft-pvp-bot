package com.mcbot.settings;

import java.util.List;

/** One-of-N choice setting (e.g. aim mode: Snap / Smooth / Silent). Cycles in the GUI. */
public class ModeSetting extends Setting<String> {

    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) {
            throw new IllegalArgumentException("default mode not in modes: " + defaultValue);
        }
    }

    public List<String> getModes() { return modes; }

    public boolean is(String mode) { return value.equalsIgnoreCase(mode); }

    @Override
    public boolean parse(String input) {
        for (String m : modes) {
            if (m.equalsIgnoreCase(input.trim())) { set(m); return true; }
        }
        return false;
    }

    @Override
    public String display() { return value; }

    @Override
    public void adjust(int direction) {
        int i = modes.indexOf(value);
        int next = ((i + direction) % modes.size() + modes.size()) % modes.size();
        set(modes.get(next));
    }
}
