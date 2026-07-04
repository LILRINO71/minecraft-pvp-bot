package com.mcbot.settings;

/** On/off setting. */
public class BoolSetting extends Setting<Boolean> {

    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    @Override
    public boolean parse(String input) {
        switch (input.toLowerCase()) {
            case "on", "true", "yes", "1"  -> { set(true);  return true; }
            case "off", "false", "no", "0" -> { set(false); return true; }
            default -> { return false; }
        }
    }

    @Override
    public String display() { return value ? "§aON" : "§cOFF"; }

    @Override
    public void adjust(int direction) { set(!value); }
}
