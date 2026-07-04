package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.BlockUtil;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Surround — instantly boxes obsidian around your feet so enemy End Crystals can't be placed next
 * to you (a core crystal-PvP defense). Places the 4 horizontal blocks around your lower body.
 * Meteor-equivalent.
 */
public class SurroundModule extends Module {

    public SurroundModule() {
        super("Surround", "Places obsidian around your feet to block crystal damage.",
                ModuleCategory.COMBAT);
    }

    @Override
    protected void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (!InventoryUtil.switchToItem(client, Items.OBSIDIAN)) return;

        // Ring of 4 blocks around the block the player is standing in.
        BlockPos feet = client.player.blockPosition();
        Direction[] sides = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
        for (Direction d : sides) {
            BlockPos p = feet.relative(d);
            if (!BlockUtil.isAir(client.level, p)) continue;   // already filled
            BlockPos support = p.below();                       // click the ground beneath it
            if (BlockUtil.isAir(client.level, support)) continue;

            Vec3 topFace = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
            EntityUtil.lookAt(client, topFace);
            BlockHitResult hit = new BlockHitResult(topFace, Direction.UP, support, false);
            if (client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
                client.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }
}
