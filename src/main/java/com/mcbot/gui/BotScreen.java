package com.mcbot.gui;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.module.ModuleManager;
import com.mcbot.settings.Setting;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BotScreen — ClickGUI panel (Right-Shift).
 *
 * <p>Left-click a module row to toggle it. <b>Right-click a module row to expand its settings
 * submenu</b>; setting rows adjust on click (left half = decrease/previous, right half =
 * increase/next; booleans toggle either way).
 *
 * <p>Layout is computed once per interaction into {@link Row} records used by BOTH drawing and
 * click hit-testing, so the two can never disagree.
 *
 * <p>Uses the 26.1 GUI pipeline: {@code extractRenderState(GuiGraphicsExtractor,...)} replaces the
 * old {@code render(GuiGraphics,...)}; input arrives as {@code MouseButtonEvent}.
 */
public class BotScreen extends Screen {

    // ---- layout constants ----
    private static final int COLUMN_WIDTH  = 128;
    private static final int COLUMN_GAP    = 6;
    private static final int ROW_HEIGHT    = 13;
    private static final int HEADER_HEIGHT = 15;
    private static final int PANEL_TOP     = 34;
    private static final int PANEL_LEFT    = 8;
    private static final int PAD           = 4;

    // ---- colors (ARGB) ----
    private static final int C_DIM            = 0xC0000000;
    private static final int C_PANEL_BG       = 0xE0161616;
    private static final int C_PANEL_BORDER   = 0xFF303030;
    private static final int C_HEADER_BG      = 0xFF232323;
    private static final int C_HEADER_TEXT    = 0xFFDDDDDD;
    private static final int C_ROW_HOVER      = 0x40FFFFFF;
    private static final int C_ENABLED_TEXT   = 0xFF55FF55;
    private static final int C_ENABLED_BAR    = 0xFF2E7D32;
    private static final int C_DISABLED_TEXT  = 0xFF9A9A9A;
    private static final int C_SETTING_BG     = 0xE0101010;
    private static final int C_SETTING_TEXT   = 0xFFCCCCCC;
    private static final int C_SETTING_VALUE  = 0xFF55FFFF;
    private static final int C_TITLE          = 0xFFFFFFFF;
    private static final int C_HINT           = 0xFFAAAAAA;

    private final ModuleManager moduleManager;
    /** Modules whose settings submenu is expanded (persists while the screen is open). */
    private final Set<Module> expanded = new HashSet<>();

    /** One clickable rectangle: either a module row or a setting row. */
    private record Row(int x, int y, int w, int h, Module module, Setting<?> setting) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my < y + h;
        }
        boolean isSetting() { return setting != null; }
    }

    /** Background panel rectangle for one category. */
    private record Panel(int x, int y, int w, int h, ModuleCategory category) {}

    public BotScreen(ModuleManager moduleManager) {
        super(Component.literal("MC BOT"));
        this.moduleManager = moduleManager;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------------
    //  Unified layout: one pass produces every panel + row rectangle
    // ---------------------------------------------------------------------

    private List<Panel> panels;
    private List<Row> rows;

    private void computeLayout() {
        panels = new ArrayList<>();
        rows = new ArrayList<>();
        int x = PANEL_LEFT;
        int y = PANEL_TOP;

        for (ModuleCategory cat : ModuleCategory.values()) {
            List<Module> modules = moduleManager.getByCategory(cat);
            if (modules == null) modules = List.of();

            // Panel height depends on which modules are expanded.
            int panelH = HEADER_HEIGHT + PAD;
            for (Module m : modules) {
                panelH += ROW_HEIGHT;
                if (expanded.contains(m)) panelH += m.getSettings().size() * ROW_HEIGHT;
            }

            // Wrap to a new column if this category would overflow the bottom.
            if (y + panelH > this.height - 4 && y > PANEL_TOP) {
                y = PANEL_TOP;
                x += COLUMN_WIDTH + COLUMN_GAP;
            }

            panels.add(new Panel(x, y, COLUMN_WIDTH, panelH, cat));

            int rowY = y + HEADER_HEIGHT;
            for (Module m : modules) {
                rows.add(new Row(x, rowY, COLUMN_WIDTH, ROW_HEIGHT, m, null));
                rowY += ROW_HEIGHT;
                if (expanded.contains(m)) {
                    for (Setting<?> s : m.getSettings()) {
                        rows.add(new Row(x, rowY, COLUMN_WIDTH, ROW_HEIGHT, m, s));
                        rowY += ROW_HEIGHT;
                    }
                }
            }

            y += panelH + COLUMN_GAP;
        }
    }

    // ---------------------------------------------------------------------
    //  Rendering
    // ---------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, C_DIM);

        Font f = this.font;
        g.text(f, "MC BOT - ClickGUI", PANEL_LEFT, 8, C_TITLE);
        g.text(f, "L-click: toggle | R-click: settings | Esc: close", PANEL_LEFT, 8 + f.lineHeight + 2, C_HINT);

        if (moduleManager == null) {
            g.text(f, "Module manager not ready.", PANEL_LEFT, PANEL_TOP, C_DISABLED_TEXT);
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            return;
        }

        computeLayout();

        // Panels + headers.
        for (Panel p : panels) {
            g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), C_PANEL_BG);
            drawBorder(g, p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), C_PANEL_BORDER);
            g.fill(p.x(), p.y(), p.x() + p.w(), p.y() + HEADER_HEIGHT, C_HEADER_BG);
            int textY = p.y() + (HEADER_HEIGHT - f.lineHeight) / 2 + 1;
            g.text(f, p.category().displayName, p.x() + PAD, textY, C_HEADER_TEXT);
        }

        // Rows.
        Row hovered = null;
        for (Row r : rows) {
            boolean hover = r.contains(mouseX, mouseY);
            if (hover) hovered = r;
            int textY = r.y() + (ROW_HEIGHT - f.lineHeight) / 2 + 1;

            if (r.isSetting()) {
                // Setting row: darker bg, "name  value" with the value right-aligned.
                g.fill(r.x() + 2, r.y(), r.x() + r.w(), r.y() + r.h(), C_SETTING_BG);
                if (hover) g.fill(r.x() + 2, r.y(), r.x() + r.w(), r.y() + r.h(), C_ROW_HOVER);
                g.text(f, r.setting().getName(), r.x() + PAD + 6, textY, C_SETTING_TEXT);
                String val = r.setting().display();
                int valW = f.width(stripColor(val));
                g.text(f, val, r.x() + r.w() - PAD - valW, textY, C_SETTING_VALUE);
            } else {
                Module m = r.module();
                boolean enabled = m.isEnabled();
                if (enabled) g.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), C_ENABLED_BAR);
                if (hover) g.fill(r.x() + 2, r.y(), r.x() + r.w(), r.y() + r.h(), C_ROW_HOVER);
                g.text(f, m.getName(), r.x() + PAD + 2, textY,
                        enabled ? C_ENABLED_TEXT : C_DISABLED_TEXT);
                // Expansion marker for modules that have settings.
                if (!m.getSettings().isEmpty()) {
                    String marker = expanded.contains(m) ? "-" : "+";
                    g.text(f, marker, r.x() + r.w() - PAD - f.width(marker), textY, C_HINT);
                }
            }
        }

        // Tooltip: module description, or setting description.
        if (hovered != null) {
            String desc = hovered.isSetting()
                    ? hovered.setting().getDescription()
                    : hovered.module().getDescription();
            if (desc != null && !desc.isEmpty()) {
                g.setTooltipForNextFrame(f, Component.literal(desc), mouseX, mouseY);
            }
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private static String stripColor(String s) {
        return s.replaceAll("§.", "");
    }

    private void drawBorder(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    // ---------------------------------------------------------------------
    //  Input
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (moduleManager != null) {
            computeLayout();
            for (Row r : rows) {
                if (!r.contains(event.x(), event.y())) continue;

                if (r.isSetting()) {
                    // Left half = decrease/previous, right half = increase/next.
                    int dir = (event.x() < r.x() + r.w() / 2.0) ? -1 : 1;
                    r.setting().adjust(dir);
                    return true;
                }

                if (event.button() == 1) {
                    // Right-click: expand/collapse the settings submenu.
                    if (!r.module().getSettings().isEmpty()) {
                        if (!expanded.remove(r.module())) expanded.add(r.module());
                    }
                    return true;
                }
                if (event.button() == 0) {
                    r.module().toggle();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }
}
