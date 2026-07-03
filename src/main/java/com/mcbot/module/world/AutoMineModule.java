package com.mcbot.module.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IMineProcess;
import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * AutoMine — uses Baritone's mine process to automatically mine specified ores.
 *
 * Default target: all diamond ores (normal + deepslate).
 * Command override: ".bot mine <ore>" sets a custom target.
 *
 * Baritone handles:
 *   - Pathfinding to the ore
 *   - Digging through walls
 *   - Best Y-level selection
 *   - Tool switching
 */
public class AutoMineModule extends Module {

    private List<Block> targetBlocks;
    private String targetName = "diamonds";

    public AutoMineModule() {
        super("AutoMine", "Baritone-powered: mines any ore automatically at superhuman speed.", ModuleCategory.WORLD);
        // Default target: diamonds
        targetBlocks = List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
    }

    @Override
    protected void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        startMining(client);
    }

    @Override
    protected void onDisable() {
        stopBaritone();
    }

    @Override
    protected void onTick(Minecraft client) {
        // Baritone manages itself — just ensure it's still running
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (!baritone.getMineProcess().isActive() && isEnabled()) {
            // Restart if stopped (e.g., ore depleted in local area — Baritone will explore)
            startMining(client);
        }
    }

    private void startMining(Minecraft client) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        IMineProcess miner = baritone.getMineProcess();
        Block[] blocks = targetBlocks.toArray(new Block[0]);
        miner.mine(blocks);
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "[MC BOT] AutoMine: hunting " + targetName + "..."));
        }
    }

    private void stopBaritone() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getPathingBehavior().cancelEverything();
    }

    // ── Public API for command handler ────────────────────────────────────

    public void setTarget(String name, Block... blocks) {
        this.targetName = name;
        this.targetBlocks = Arrays.asList(blocks);
        if (isEnabled()) {
            stopBaritone();
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) startMining(client);
        }
    }

    /** Convenience presets. */
    public void mineIron()     { setTarget("iron",     Blocks.IRON_ORE,     Blocks.DEEPSLATE_IRON_ORE); }
    public void mineGold()     { setTarget("gold",     Blocks.GOLD_ORE,     Blocks.DEEPSLATE_GOLD_ORE); }
    public void mineDiamonds() { setTarget("diamonds", Blocks.DIAMOND_ORE,  Blocks.DEEPSLATE_DIAMOND_ORE); }
    public void mineEmeralds() { setTarget("emeralds", Blocks.EMERALD_ORE,  Blocks.DEEPSLATE_EMERALD_ORE); }
    public void mineAncient()  { setTarget("ancient debris", Blocks.ANCIENT_DEBRIS); }
    public void mineCoal()     { setTarget("coal",     Blocks.COAL_ORE,     Blocks.DEEPSLATE_COAL_ORE); }
    public void mineObsidian() { setTarget("obsidian", Blocks.OBSIDIAN); }
}
