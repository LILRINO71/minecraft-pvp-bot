package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.settings.IntSetting;
import com.mcbot.util.BlockUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * AutoClutch — perfect-timing water-bucket clutch to negate fall damage.
 *
 * <p>While falling, it watches the ground below. As soon as you're within {@code clutchHeight}
 * blocks of the ground (and have fallen far enough to actually get hurt), it looks straight down and
 * places a water bucket so you land safely — then re-collects the water once you're on the ground.
 * This is exactly the "MLG water bucket" a top player does, with frame-perfect timing.
 */
public class AutoClutchModule extends Module {

    private final IntSetting minFall = addSetting(new IntSetting(
            "minFall", "Only clutch once you've fallen at least this many blocks.", 4, 2, 20, 1));
    private final IntSetting clutchHeight = addSetting(new IntSetting(
            "height", "Place the water when this many blocks above the ground.", 3, 1, 6, 1));
    private final BoolSetting pickup = addSetting(new BoolSetting(
            "pickup", "Re-collect the water bucket after landing.", true));

    private boolean placedWater = false;
    private int prevSlot = -1;

    public AutoClutchModule() {
        super("AutoClutch", "MLG water-bucket clutch — negates fall damage with perfect timing.",
                ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() { placedWater = false; prevSlot = -1; }

    @Override
    protected void onTick(Minecraft client) {
        var p = client.player;
        if (p == null || client.level == null || client.gameMode == null) return;

        // Landed: pick the water back up, then reset.
        if (p.onGround()) {
            if (placedWater && pickup.get()) tryPickupWater(client);
            placedWater = false;
            return;
        }
        if (p.isFallFlying() || p.getAbilities().flying) return;
        if (p.getDeltaMovement().y >= 0) return;         // still going up
        if (p.fallDistance < minFall.get()) return;      // not far enough to hurt
        if (placedWater) return;                          // already clutched this fall

        int ground = distanceToGround(client, 8);
        if (ground < 0 || ground > clutchHeight.get()) return;

        // Look straight down and place the water on the block below us.
        if (!InventoryUtil.switchToItem(client, Items.WATER_BUCKET)) return;
        prevSlot = p.getInventory().getSelectedSlot();
        p.setXRot(90f);
        client.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        placedWater = true;
    }

    /** Number of air blocks between the player's feet and the first solid block below (-1 = none). */
    private int distanceToGround(Minecraft client, int max) {
        BlockPos feet = client.player.blockPosition();
        for (int i = 1; i <= max; i++) {
            if (!BlockUtil.isAir(client.level, feet.below(i))) return i - 1;
        }
        return -1;
    }

    private void tryPickupWater(Minecraft client) {
        var p = client.player;
        // The water bucket became an empty bucket in the same slot; scoop the water back up.
        if (InventoryUtil.switchToItem(client, Items.BUCKET)) {
            p.setXRot(90f);
            client.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        }
        if (prevSlot >= 0 && prevSlot <= 8) {
            p.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }
}
