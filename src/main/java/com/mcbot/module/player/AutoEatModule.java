package com.mcbot.module.player;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

/**
 * AutoEat — automatically eats when hunger drops below threshold.
 *
 * - Picks the best food available (highest hunger + saturation score)
 * - Switches it to the selected hotbar slot
 * - Holds the use key to eat
 * - Switches back to previous item when done
 */
public class AutoEatModule extends Module {

    private static final int    HUNGER_THRESHOLD = 16;  // out of 20 (eat when ≤ 16)
    private static final int    FULL_THRESHOLD   = 20;

    private boolean eating = false;
    private int previousSlot = 0;
    private int eatSlot = -1;

    public AutoEatModule() {
        super("AutoEat", "Eats the best available food when hunger drops.", ModuleCategory.PLAYER);
    }

    @Override
    protected void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.stopUsingItem();
        }
        eating = false;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;

        int hunger = client.player.getFoodData().getFoodLevel();

        if (eating) {
            // Check if done eating
            if (!client.player.isUsingItem()) {
                eating = false;
                // Restore previous slot if we moved to eat
                if (eatSlot >= 0) {
                    client.player.getInventory().setSelectedSlot(previousSlot);
                    eatSlot = -1;
                }
            }
            return;
        }

        // Should we eat?
        if (hunger > HUNGER_THRESHOLD) return;

        // Find best food in hotbar or inventory
        int foodSlot = InventoryUtil.findBestFood(client);
        if (foodSlot < 0) return;

        // Move food to hotbar if needed
        if (foodSlot >= 9) {
            // Food is not in hotbar — try to swap it into current slot via inventory click
            // For simplicity: just eat from inventory is not possible; skip
            // (User should keep food in hotbar)
            return;
        }

        previousSlot = client.player.getInventory().getSelectedSlot();
        eatSlot = foodSlot;
        client.player.getInventory().setSelectedSlot(foodSlot);

        // Start eating
        client.options.keyUse.setDown(true);
        eating = true;
    }
}
