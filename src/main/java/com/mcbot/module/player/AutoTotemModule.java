package com.mcbot.module.player;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/**
 * AutoTotem — keeps a Totem of Undying in your off-hand at all times.
 *
 * <p>When the off-hand isn't already a totem, it swaps one in from your inventory using an
 * off-hand container swap (button 40 = off-hand slot), exactly like pressing F would. Works
 * without opening the inventory. Pops-and-replaces automatically after a totem saves you.
 */
public class AutoTotemModule extends Module {

    /** Off-hand is index 40 in the SWAP button convention (0-8 = hotbar slots, 40 = off-hand). */
    private static final int OFFHAND_BUTTON = 40;

    private int cooldown = 0;
    private boolean warnedEmpty = false;

    public AutoTotemModule() {
        super("AutoTotem", "Keeps a Totem of Undying in your off-hand automatically.",
                ModuleCategory.PLAYER);
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
        warnedEmpty = false;
    }

    @Override
    protected void onTick(Minecraft client) {
        if (client.gameMode == null) return;
        // Don't fiddle with slots while a real container (chest, etc.) is open.
        if (client.screen != null) return;

        if (cooldown > 0) { cooldown--; return; }

        // Already holding a totem in the off-hand? Nothing to do.
        if (client.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
            warnedEmpty = false;
            return;
        }

        int invIndex = InventoryUtil.findItem(client, Items.TOTEM_OF_UNDYING); // 0-35, -1 if none
        if (invIndex < 0) {
            if (!warnedEmpty) {
                client.player.sendSystemMessage(
                        Component.literal("§c[MC BOT] AutoTotem: no totems left!"));
                warnedEmpty = true;
            }
            return;
        }

        // Inventory index -> container-menu slot: hotbar (0-8) maps to menu 36-44, main stays 9-35.
        int menuSlot = invIndex <= 8 ? invIndex + 36 : invIndex;

        client.gameMode.handleContainerInput(
                client.player.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON,
                ContainerInput.SWAP, client.player);

        cooldown = 3; // let the swap round-trip before trying again
    }
}
