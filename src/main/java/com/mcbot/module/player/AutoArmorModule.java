package com.mcbot.module.player;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** AutoArmor - equips equippable armour found in the hotbar for any empty slot. */
public class AutoArmorModule extends Module {
    private static final int CHECK_INTERVAL = 20;
    private int tickCount = 0;
    public AutoArmorModule() {
        super("AutoArmor", "Equips available armour automatically.", ModuleCategory.PLAYER);
    }
    @Override protected void onTick(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;
        if (++tickCount < CHECK_INTERVAL) return;
        tickCount = 0;
        equipForSlot(client, EquipmentSlot.HEAD);
        equipForSlot(client, EquipmentSlot.CHEST);
        equipForSlot(client, EquipmentSlot.LEGS);
        equipForSlot(client, EquipmentSlot.FEET);
    }
    private void equipForSlot(Minecraft client, EquipmentSlot slot) {
        if (!client.player.getItemBySlot(slot).isEmpty()) return;
        int prev = client.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            var eq = s.get(DataComponents.EQUIPPABLE);
            if (eq != null && eq.slot() == slot) {
                client.player.getInventory().setSelectedSlot(i);
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                client.player.getInventory().setSelectedSlot(prev);
                return;
            }
        }
    }
}
