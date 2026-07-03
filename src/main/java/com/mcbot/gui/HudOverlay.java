package com.mcbot.gui;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * HudOverlay - a Meteor/Impact-style "arraylist": a right-aligned vertical list of the
 * currently ENABLED modules, drawn in the top-right corner of the screen every frame,
 * with a small "MC BOT" watermark at the top.
 *
 * <p>26.1 note: the old {@code HudRenderCallback} was removed from fabric-rendering-v1.
 * The current API is {@link HudElementRegistry}, which registers {@link HudElement}s that
 * are called each frame via {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}.
 * {@link GuiGraphicsExtractor} exposes the immediate draw primitives we need
 * ({@code text(...)}, {@code fill(...)}, {@code guiWidth()}, {@code guiHeight()}), all
 * javap-verified against minecraft-merged.jar for 26.1.2.
 */
public class HudOverlay {

    /** Namespaced id under which this HUD element is registered. */
    private static final Identifier ELEMENT_ID =
            Identifier.fromNamespaceAndPath("mcbot", "arraylist");

    // Layout constants.
    private static final int MARGIN = 2;          // distance from the right/top screen edge
    private static final int PADDING_X = 3;        // horizontal text padding inside a row
    private static final int ROW_EXTRA = 2;        // extra vertical space per row on top of lineHeight

    // Colors (ARGB, 0xAARRGGBB).
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_WATERMARK = 0xFF55FFFF;         // aqua
    private static final int COLOR_BG = 0x90000000;                // translucent black backing
    private static final int COLOR_ACCENT = 0xFF00AAFF;            // side accent bar

    private final ModuleManager moduleManager;

    public HudOverlay(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /**
     * Registers the arraylist HUD element with Fabric. Call once from
     * {@code onInitializeClient()} after the {@link ModuleManager} exists.
     */
    public void register() {
        // addLast => our element is drawn on top of the vanilla HUD stack.
        HudElementRegistry.addLast(ELEMENT_ID, new HudElement() {
            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
                render(graphics);
            }
        });
    }

    /** Draws the watermark and the enabled-module list. Safe to call every frame. */
    private void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        // Do not draw over any open GUI screen (inventory, chat, our BotScreen, menus, ...).
        if (mc.screen != null) {
            return;
        }

        // Do not draw while the F3 debug screen is showing.
        DebugScreenOverlay debug = mc.getDebugOverlay();
        if (debug != null && debug.showDebugScreen()) {
            return;
        }

        Font font = mc.font;
        if (font == null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int lineHeight = font.lineHeight;
        int rowHeight = lineHeight + ROW_EXTRA;

        int y = MARGIN;

        // --- Watermark ---------------------------------------------------------
        String watermark = "MC BOT";
        int wmWidth = font.width(watermark);
        int wmX = screenWidth - wmWidth - MARGIN - PADDING_X;
        // Backing behind the watermark for legibility over bright terrain.
        graphics.fill(wmX - PADDING_X, y, screenWidth - MARGIN, y + rowHeight, COLOR_BG);
        graphics.text(font, watermark, wmX, y + (ROW_EXTRA / 2), COLOR_WATERMARK, true);
        y += rowHeight + 1;

        // --- Enabled module list ----------------------------------------------
        if (moduleManager == null) {
            return;
        }

        List<Module> enabled = new ArrayList<>(moduleManager.getEnabled());
        if (enabled.isEmpty()) {
            // Watermark only â€” keep it tasteful when nothing is active.
            return;
        }

        // Classic staircase: longest names on top. Sort by rendered width descending,
        // tie-broken alphabetically for stability.
        enabled.sort((a, b) -> {
            int wa = font.width(a.getName());
            int wb = font.width(b.getName());
            if (wa != wb) {
                return Integer.compare(wb, wa);
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (Module module : enabled) {
            String name = module.getName();
            int textWidth = font.width(name);
            // Right edge is fixed at the screen edge; x flows leftward from there.
            int rowRight = screenWidth - MARGIN;
            int textX = rowRight - PADDING_X - textWidth;
            int bgLeft = textX - PADDING_X;

            // Row background.
            graphics.fill(bgLeft, y, rowRight, y + rowHeight, COLOR_BG);
            // Accent bar on the right edge of the row (the "attached" look).
            graphics.fill(rowRight, y, rowRight + 1, y + rowHeight, COLOR_ACCENT);
            // Module name.
            graphics.text(font, name, textX, y + (ROW_EXTRA / 2), COLOR_TEXT, true);

            y += rowHeight;
        }
    }
}
