package com.mcbot.settings;

/** Whole-number setting with min/max bounds and a GUI step. */
public class IntSetting extends Setting<Integer> {

    private final int min, max, step;

    public IntSetting(String name, String description, int defaultValue, int min, int max, int step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }

    @Override
    public void set(Integer v) { super.set(Math.max(min, Math.min(max, v))); }

    @Override
    public boolean parse(String input) {
        try { set(Integer.parseInt(input.trim())); return true; }
        catch (NumberFormatException e) { return false; }
    }

    @Override
    public String display() { return String.valueOf(value); }

    @Override
    public void adjust(int direction) { set(value + direction * step); }
}
