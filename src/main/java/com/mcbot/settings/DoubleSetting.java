package com.mcbot.settings;

import java.util.Locale;

/** Decimal setting with min/max bounds and a GUI step. */
public class DoubleSetting extends Setting<Double> {

    private final double min, max, step;

    public DoubleSetting(String name, String description, double defaultValue,
                         double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }

    @Override
    public void set(Double v) { super.set(Math.max(min, Math.min(max, v))); }

    @Override
    public boolean parse(String input) {
        try { set(Double.parseDouble(input.trim())); return true; }
        catch (NumberFormatException e) { return false; }
    }

    @Override
    public String display() { return String.format(Locale.ROOT, "%.1f", value); }

    @Override
    public void adjust(int direction) {
        // Round to the step grid to avoid 0.30000000000004-style drift.
        double v = value + direction * step;
        set(Math.round(v / step) * step);
    }
}
