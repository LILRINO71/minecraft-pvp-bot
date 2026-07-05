package com.mcbot.ai.exec;

import baritone.api.BaritoneAPI;
import baritone.api.process.IMineProcess;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * GatherExecutor — collect at least {@code target} of a resource by letting Baritone mine/branch-mine
 * the matching blocks and pick up the drops, finishing when the inventory count hits the target.
 *
 * <p>Supports the common ores plus a few basics (obsidian, stone, dirt, sand, wood). "Have N" is the
 * total of the drop item(s) in the inventory, matching Baritone's own quantity semantics.
 */
public class GatherExecutor implements TaskExecutor {

    private final String name;
    private final int target;

    private Block[] blocks;      // what Baritone mines
    private Item[] countItems;   // what we count toward the target (the drops)
    private boolean resolved;
    private String fail = "";

    private int inactiveTicks = 0;

    public GatherExecutor(String name, int target) {
        this.name = name == null ? "" : name.toLowerCase().trim();
        this.target = Math.max(1, target);
    }

    @Override
    public void start(Minecraft client) {
        resolved = resolve();
        if (!resolved) { fail = "don't know how to gather '" + name + "'"; return; }
        if (have(client) >= target) return; // already satisfied; tick() will report DONE
        IMineProcess mine = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess();
        mine.mine(target, blocks);          // Baritone collects until we have `target`
    }

    @Override
    public Status tick(Minecraft client) {
        if (!resolved) return Status.FAILED;
        if (client.player == null) return Status.RUNNING;

        if (have(client) >= target) return Status.DONE;

        IMineProcess mine = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess();
        if (!mine.isActive()) {
            // Baritone stopped without reaching the target. Give it a short grace period in case
            // it's re-pathing, then fail (nothing reachable / can't dig here).
            if (++inactiveTicks > 100) { fail = "couldn't find enough " + name + " nearby"; return Status.FAILED; }
        } else {
            inactiveTicks = 0;
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(Minecraft client) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
    }

    @Override
    public String progress() {
        Minecraft mc = Minecraft.getInstance();
        return have(mc) + "/" + target + " " + name;
    }

    @Override
    public String failReason() { return fail; }

    private int have(Minecraft client) {
        int total = 0;
        for (Item it : countItems) total += InventoryUtil.countItem(client, it);
        return total;
    }

    /** Maps a resource name to the blocks to mine and the item(s) to count. */
    private boolean resolve() {
        switch (name) {
            case "diamond", "diamonds" -> set(new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE}, Items.DIAMOND);
            case "iron"                -> set(new Block[]{Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE}, Items.RAW_IRON);
            case "gold"                -> set(new Block[]{Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE}, Items.RAW_GOLD);
            case "copper"              -> set(new Block[]{Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE}, Items.RAW_COPPER);
            case "coal"                -> set(new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE}, Items.COAL);
            case "redstone"            -> set(new Block[]{Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE}, Items.REDSTONE);
            case "lapis"               -> set(new Block[]{Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE}, Items.LAPIS_LAZULI);
            case "emerald", "emeralds" -> set(new Block[]{Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE}, Items.EMERALD);
            case "ancient", "netherite", "debris" -> set(new Block[]{Blocks.ANCIENT_DEBRIS}, Items.ANCIENT_DEBRIS);
            case "obsidian"            -> set(new Block[]{Blocks.OBSIDIAN}, Items.OBSIDIAN);
            case "stone", "cobblestone", "cobble" -> set(new Block[]{Blocks.STONE}, Items.COBBLESTONE);
            case "dirt"                -> set(new Block[]{Blocks.DIRT}, Items.DIRT);
            case "sand"                -> set(new Block[]{Blocks.SAND}, Items.SAND);
            case "wood", "log", "logs" -> {
                blocks = new Block[]{Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
                        Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG};
                countItems = new Item[]{Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                        Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG};
            }
            default -> { return false; }
        }
        return true;
    }

    private void set(Block[] b, Item countItem) {
        this.blocks = b;
        this.countItems = new Item[]{countItem};
    }
}
