package com.mcbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

public final class InventoryUtil {

    private InventoryUtil() {}

    public static boolean switchToItem(Minecraft client, Item item) {
        if (client.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).getItem() == item) {
                client.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    public static boolean switchToItem(Minecraft client, Predicate<ItemStack> pred) {
        if (client.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty() && pred.test(stack)) {
                client.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    public static int findItem(Minecraft client, Item item) {
        if (client.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    public static int countItem(Minecraft client, Item item) {
        if (client.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getItem(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    public static boolean hasItem(Minecraft client, Item item) {
        return findItem(client, item) >= 0;
    }

    /** Best armour piece for the slot (first equippable match). */
    public static ItemStack findBestArmour(Minecraft client, EquipmentSlot slot) {
        if (client.player == null) return ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            var eq = s.get(DataComponents.EQUIPPABLE);
            if (eq != null && eq.slot() == slot) return s;
        }
        return ItemStack.EMPTY;
    }

    public static int findFoodInHotbar(Minecraft client) {
        if (client.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).has(DataComponents.FOOD)) return i;
        }
        return -1;
    }

    public static int findBestFood(Minecraft client) {
        if (client.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getItem(i);
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food != null) {
                float score = food.nutrition() + food.saturation() * 2;
                if (score > bestScore) { bestScore = score; bestSlot = i; }
            }
        }
        return bestSlot;
    }

    public static boolean switchToEndCrystal(Minecraft client) {
        return switchToItem(client, Items.END_CRYSTAL);
    }

    public static int countEndCrystals(Minecraft client) {
        return countItem(client, Items.END_CRYSTAL);
    }

    // ── Hotbar reading (slots 0-8 only) ───────────────────────────────────

    /** Currently selected hotbar slot (0-8). */
    public static int getSelectedSlot(Minecraft client) {
        if (client.player == null) return 0;
        return client.player.getInventory().getSelectedSlot();
    }

    /** The item currently held in the main hand. */
    public static Item getHeldItem(Minecraft client) {
        if (client.player == null) return Items.AIR;
        return client.player.getMainHandItem().getItem();
    }

    /** Hotbar slot (0-8) holding the given item, or -1 if not on the hotbar. */
    public static int findInHotbar(Minecraft client, Item item) {
        if (client.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    public static boolean hasInHotbar(Minecraft client, Item item) {
        return findInHotbar(client, item) >= 0;
    }

    /** Weapon priority — higher beats lower when picking the best melee tool. */
    private static int weaponScore(Item item) {
        if (item == Items.NETHERITE_AXE)    return 100;
        if (item == Items.NETHERITE_SWORD)  return 95;
        if (item == Items.DIAMOND_AXE)      return 90;
        if (item == Items.DIAMOND_SWORD)    return 85;
        if (item == Items.IRON_AXE)         return 70;
        if (item == Items.IRON_SWORD)       return 65;
        if (item == Items.STONE_SWORD)      return 50;
        if (item == Items.GOLDEN_SWORD)     return 45;
        if (item == Items.WOODEN_SWORD)     return 40;
        if (item == Items.STONE_AXE)        return 35;
        if (item == Items.WOODEN_AXE || item == Items.GOLDEN_AXE) return 30;
        return 0;
    }

    /** Best melee weapon slot on the hotbar (0-8), or -1 if none. */
    public static int bestMeleeSlot(Minecraft client) {
        if (client.player == null) return -1;
        int bestSlot = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            int s = weaponScore(client.player.getInventory().getItem(i).getItem());
            if (s > bestScore) { bestScore = s; bestSlot = i; }
        }
        return bestSlot;
    }

    public static boolean switchToSlot(Minecraft client, int slot) {
        if (client.player == null || slot < 0 || slot > 8) return false;
        client.player.getInventory().setSelectedSlot(slot);
        return true;
    }

}
