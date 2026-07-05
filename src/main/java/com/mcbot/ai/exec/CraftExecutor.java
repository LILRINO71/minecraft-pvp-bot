package com.mcbot.ai.exec;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * CraftExecutor — crafts an item at an OPEN crafting table by hand-filling the 3x3 grid via
 * container clicks, then shift-clicking the result, repeating until the inventory holds {@code
 * target} of the output.
 *
 * <p>Uses a small hardcoded recipe book (the progression + iron/diamond gear path) with flexible
 * ingredients (any planks/logs, exact ingots). It expects a crafting table to already be open —
 * autonomous table placement + pathing is the next iteration; v1 keeps the risky pieces small and
 * testable.
 *
 * <p>Grid cells are indexed row-major 0-8. In a CraftingMenu the result is slot 0, the grid is
 * slots 1-9, and the player inventory is slots 10+.
 */
public class CraftExecutor implements TaskExecutor {

    private static final int RESULT_SLOT = 0;
    private static final int GRID_START  = 1;   // grid cell c -> menu slot 1 + c
    private static final int INV_START   = 10;  // player inventory begins here in a CraftingMenu

    private final String name;
    private final int target;
    private Recipe recipe;
    private String fail = "";
    private int cooldown = 0;

    public CraftExecutor(String name, int target) {
        this.name = name == null ? "" : name.toLowerCase().trim().replace(' ', '_');
        this.target = Math.max(1, target);
    }

    @Override
    public void start(Minecraft client) {
        recipe = RECIPES.get(name);
        if (recipe == null) fail = "no recipe for '" + name + "'";
    }

    @Override
    public Status tick(Minecraft client) {
        if (recipe == null) return Status.FAILED;
        if (client.player == null || client.gameMode == null) return Status.RUNNING;

        if (countHave(client) >= target) return Status.DONE;

        if (!(client.player.containerMenu instanceof CraftingMenu menu)) {
            fail = "open a crafting table first (then re-run)";
            return Status.FAILED;
        }

        if (cooldown > 0) { cooldown--; return Status.RUNNING; }

        int cid = menu.containerId;

        // Fill each required cell with one matching item from the inventory.
        for (int cell = 0; cell < 9; cell++) {
            Predicate<ItemStack> need = recipe.grid[cell];
            if (need == null) continue;
            int invSlot = findInInventory(menu, need);
            if (invSlot < 0) { fail = "missing ingredients — needs " + recipe.requirement; return Status.FAILED; }
            click(client, cid, invSlot, 0, ContainerInput.PICKUP);          // grab the stack
            click(client, cid, GRID_START + cell, 1, ContainerInput.PICKUP); // drop ONE into the grid cell
            click(client, cid, invSlot, 0, ContainerInput.PICKUP);          // return the remainder
        }

        // Craft: shift-click the result out to the inventory (one craft, grid had one of each).
        click(client, cid, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
        cooldown = 3; // pace crafts a little
        return Status.RUNNING;
    }

    @Override public String progress()   { return countHave(Minecraft.getInstance()) + "/" + target + " " + name; }
    @Override public String failReason() { return fail; }

    // ── helpers ──────────────────────────────────────────────────────────

    private void click(Minecraft c, int cid, int slot, int button, ContainerInput input) {
        c.gameMode.handleContainerInput(cid, slot, button, input, c.player);
    }

    /** First inventory slot (>= INV_START) whose item matches, or -1. */
    private int findInInventory(CraftingMenu menu, Predicate<ItemStack> match) {
        for (int i = INV_START; i < menu.slots.size(); i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (!s.isEmpty() && match.test(s)) return i;
        }
        return -1;
    }

    private int countHave(Minecraft client) {
        if (client.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getItem(i);
            if (!s.isEmpty() && recipe.output.test(s)) total += s.getCount();
        }
        return total;
    }

    // ── recipe book ──────────────────────────────────────────────────────

    private record Recipe(Predicate<ItemStack> output, String requirement, Predicate<ItemStack>[] grid) {}

    private static Predicate<ItemStack> item(Item it) { return s -> s.getItem() == it; }
    private static final Predicate<ItemStack> PLANK = s -> s.is(ItemTags.PLANKS);
    private static final Predicate<ItemStack> LOG   = s -> s.is(ItemTags.LOGS);
    private static final Predicate<ItemStack> COBBLE = s -> s.getItem() == Items.COBBLESTONE;
    private static final Predicate<ItemStack> STICK  = item(Items.STICK);

    /** Builds a 9-cell grid from a compact pattern: chars map via the legend (space = empty). */
    @SuppressWarnings("unchecked")
    private static Predicate<ItemStack>[] grid(String pattern, Map<Character, Predicate<ItemStack>> legend) {
        Predicate<ItemStack>[] g = new Predicate[9];
        for (int i = 0; i < 9 && i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch != ' ') g[i] = legend.get(ch);
        }
        return g;
    }

    private static final Map<String, Recipe> RECIPES = new HashMap<>();

    private static void reg(String name, Predicate<ItemStack> output, String requirement, String pattern, Object... legendPairs) {
        Map<Character, Predicate<ItemStack>> legend = new HashMap<>();
        for (int i = 0; i + 1 < legendPairs.length; i += 2) {
            legend.put((Character) legendPairs[i], (Predicate<ItemStack>) legendPairs[i + 1]);
        }
        RECIPES.put(name, new Recipe(output, requirement, grid(pattern, legend)));
    }

    static {
        // Basics
        reg("planks", PLANK, "1 log", "L        ", 'L', LOG);
        reg("stick",  STICK, "2 planks", "P  P     ", 'P', PLANK);
        RECIPES.put("sticks", RECIPES.get("stick"));
        reg("crafting_table", item(Items.CRAFTING_TABLE), "4 planks", "PP PP    ", 'P', PLANK);
        RECIPES.put("table", RECIPES.get("crafting_table"));
        reg("chest",   item(Items.CHEST),   "8 planks",     "PPPP PPPP", 'P', PLANK);
        reg("furnace", item(Items.FURNACE), "8 cobblestone","CCCC CCCC", 'C', COBBLE);

        // Iron tools + armor
        ironOrDiamond("iron",    Items.IRON_INGOT, item(Items.IRON_PICKAXE), item(Items.IRON_SWORD), item(Items.IRON_AXE),
                item(Items.IRON_HELMET), item(Items.IRON_CHESTPLATE), item(Items.IRON_LEGGINGS), item(Items.IRON_BOOTS), "iron ingot");
        ironOrDiamond("diamond", Items.DIAMOND,    item(Items.DIAMOND_PICKAXE), item(Items.DIAMOND_SWORD), item(Items.DIAMOND_AXE),
                item(Items.DIAMOND_HELMET), item(Items.DIAMOND_CHESTPLATE), item(Items.DIAMOND_LEGGINGS), item(Items.DIAMOND_BOOTS), "diamond");
    }

    private static void ironOrDiamond(String mat, Item ingot,
                                      Predicate<ItemStack> pick, Predicate<ItemStack> sword, Predicate<ItemStack> axe,
                                      Predicate<ItemStack> helm, Predicate<ItemStack> chest, Predicate<ItemStack> legs, Predicate<ItemStack> boots,
                                      String matName) {
        Predicate<ItemStack> M = item(ingot);
        reg(mat + "_pickaxe", pick,  "3 " + matName + " + 2 sticks", "MMM S  S ", 'M', M, 'S', STICK);
        reg(mat + "_sword",   sword, "2 " + matName + " + 1 stick",  " M  M  S ", 'M', M, 'S', STICK);
        reg(mat + "_axe",     axe,   "3 " + matName + " + 2 sticks", "MM MS  S ", 'M', M, 'S', STICK);
        reg(mat + "_helmet",     helm,  "5 " + matName, "MMMM M   ", 'M', M);
        reg(mat + "_chestplate", chest, "8 " + matName, "M MMMMMMM", 'M', M);
        reg(mat + "_leggings",   legs,  "7 " + matName, "MMMM MM M", 'M', M);
        reg(mat + "_boots",      boots, "4 " + matName, "   M MM M", 'M', M);
    }
}
