package com.mcbot.gui;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.module.ModuleManager;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BotScreen - ClickGUI panel opened via Right-Shift.
 *
 * Ported to Minecraft 26.1.2 (Mojang official mappings). In 26.1 the GUI render
 * pipeline was reworked: the old {@code render(GuiGraphics, int, int, float)} is
 * gone. Screens now override:
 *   {@code void extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick)}
 * and draw via {@link GuiGraphicsExtractor} methods:
 *   {@code fill(x1,y1,x2,y2,argb)}, {@code text(Font,String,x,y,argb)},
 *   {@code centeredText(Font,String,x,y,argb)}, {@code setTooltipForNextFrame(Font,Component,x,y)}.
 * Mouse input is {@code mouseClicked(MouseButtonEvent, boolean)}; the event
 * exposes {@code x()}, {@code y()}, {@code button()}.
 *
 * All Minecraft/Fabric signatures used here were verified against
 * minecraft-merged.jar (26.1.2) with javap.
 */
public class BotScreen extends Screen {

    // ---- layout constants ----
    private static final int COLUMN_WIDTH  = 118;
    private static final int COLUMN_GAP    = 6;
    private static final int ROW_HEIGHT    = 13;
    private static final int HEADER_HEIGHT = 15;
    private static final int PANEL_TOP     = 34;
    private static final int PANEL_LEFT    = 8;
    private static final int PAD           = 4;

    // ---- colors (ARGB) ----
    private static final int C_DIM          = 0xC0000000; // full-screen dim
    private static final int C_PANEL_BG      = 0xE0161616; // category panel background
    private static final int C_PANEL_BORDER  = 0xFF303030;
    private static final int C_HEADER_BG      = 0xFF232323;
    private static final int C_HEADER_TEXT    = 0xFFDDDDDD;
    private static final int C_ROW_HOVER      = 0x40FFFFFF;
    private static final int C_ENABLED_TEXT   = 0xFF55FF55; // green
    private static final int C_ENABLED_BAR    = 0xFF2E7D32; // left accent bar when enabled
    private static final int C_DISABLED_TEXT  = 0xFF9A9A9A; // gray
    private static final int C_TITLE          = 0xFFFFFFFF;
    private static final int C_HINT           = 0xFFAAAAAA;

    private final ModuleManager moduleManager;

    /**
     * Constructor signature is fixed by the caller
     * (MCBotClient: client.setScreen(new BotScreen(moduleManager))).
     */
    public BotScreen(ModuleManager moduleManager) {
        super(Component.literal("MC BOT"));
        this.moduleManager = moduleManager;
    }

    @Override
    protected void init() {
        super.init();
        // No child widgets: rows are drawn and hit-tested manually.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------
    //  Rendering (26.1 pipeline): extractRenderState replaces render()
    // ---------------------------------------------------------------------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Dim the whole screen behind the panel.
        g.fill(0, 0, this.width, this.height, C_DIM);

        // Title + close hint.
        Font f = this.font;
        g.text(f, "MC BOT - ClickGUI", PANEL_LEFT, 8, C_TITLE);
        g.text(f, "Esc to close  |  click a module to toggle", PANEL_LEFT, 8 + f.lineHeight + 2, C_HINT);

        if (moduleManager != null) {
            drawCategories(g, f, mouseX, mouseY);
        } else {
            g.text(f, "Module manager not ready.", PANEL_LEFT, PANEL_TOP, C_DISABLED_TEXT);
        }

        // Let the base class render any child widgets (none currently) and
        // keep the standard extraction contract intact.
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawCategories(GuiGraphicsExtractor g, Font f, int mouseX, int mouseY) {
        int x = PANEL_LEFT;
        int y = PANEL_TOP;
        Module hovered = null;

        for (ModuleCategory cat : ModuleCategory.values()) {
            List<Module> modules = safeModules(cat);

            int panelH = HEADER_HEIGHT + modules.size() * ROW_HEIGHT + PAD;

            // Wrap to a new column if this category would overflow the bottom.
            if (y + panelH > this.height - 4 && y > PANEL_TOP) {
                y = PANEL_TOP;
                x += COLUMN_WIDTH + COLUMN_GAP;
            }

            int left  = x;
            int right = x + COLUMN_WIDTH;
            int top   = y;
            int bottom = y + panelH;

            // Panel background + border.
            g.fill(left, top, right, bottom, C_PANEL_BG);
            drawBorder(g, left, top, right, bottom, C_PANEL_BORDER);

            // Category header.
            g.fill(left, top, right, top + HEADER_HEIGHT, C_HEADER_BG);
            String header = cat.displayName;
            int textY = top + (HEADER_HEIGHT - f.lineHeight) / 2 + 1;
            g.text(f, header, left + PAD, textY, C_HEADER_TEXT);

            // Rows.
            int rowY = top + HEADER_HEIGHT;
            for (Module m : modules) {
                boolean enabled = m.isEnabled();
                boolean rowHover = mouseX >= left && mouseX <= right
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

                if (enabled) {
                    // Green left accent bar for enabled modules.
                    g.fill(left, rowY, left + 2, rowY + ROW_HEIGHT, C_ENABLED_BAR);
                }
                if (rowHover) {
                    g.fill(left + 2, rowY, right, rowY + ROW_HEIGHT, C_ROW_HOVER);
                    hovered = m;
                }

                String label = m.getName();
                int rowTextY = rowY + (ROW_HEIGHT - f.lineHeight) / 2 + 1;
                g.text(f, label, left + PAD + 2, rowTextY,
                        enabled ? C_ENABLED_TEXT : C_DISABLED_TEXT);

                rowY += ROW_HEIGHT;
            }

            y = bottom + COLUMN_GAP;
        }

        // Tooltip for the hovered module's description (drawn last, on top).
        if (hovered != null) {
            String desc = hovered.getDescription();
            if (desc != null && !desc.isEmpty()) {
                g.setTooltipForNextFrame(f, Component.literal(desc), mouseX, mouseY);
            }
        }
    }

    private void drawBorder(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);            // top
        g.fill(left, bottom - 1, right, bottom, color);      // bottom
        g.fill(left, top, left + 1, bottom, color);          // left
        g.fill(right - 1, top, right, bottom, color);        // right
    }

    // ---------------------------------------------------------------------
    //  Input (26.1): mouseClicked takes a MouseButtonEvent record
    // ---------------------------------------------------------------------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        // button() == 0 is left click.
        if (moduleManager != null && event.button() == 0) {
            Module clicked = moduleAt((int) event.x(), (int) event.y());
            if (clicked != null) {
                clicked.toggle();
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    /**
     * Reproduces the exact column/row layout from drawCategories to hit-test a
     * click position against module rows.
     */
    private Module moduleAt(int mx, int my) {
        int x = PANEL_LEFT;
        int y = PANEL_TOP;

        for (ModuleCategory cat : ModuleCategory.values()) {
            List<Module> modules = safeModules(cat);
            int panelH = HEADER_HEIGHT + modules.size() * ROW_HEIGHT + PAD;

            if (y + panelH > this.height - 4 && y > PANEL_TOP) {
                y = PANEL_TOP;
                x += COLUMN_WIDTH + COLUMN_GAP;
            }

            int left  = x;
            int right = x + COLUMN_WIDTH;
            int top   = y;
            int rowY  = top + HEADER_HEIGHT;

            for (Module m : modules) {
                if (mx >= left && mx <= right && my >= rowY && my < rowY + ROW_HEIGHT) {
                    return m;
                }
                rowY += ROW_HEIGHT;
            }

            y = top + panelH + COLUMN_GAP;
        }
        return null;
    }

    private List<Module> safeModules(ModuleCategory cat) {
        List<Module> list = moduleManager.getByCategory(cat);
        return list != null ? list : new ArrayList<>();
    }
}
