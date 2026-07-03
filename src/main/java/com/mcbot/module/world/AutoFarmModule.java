package com.mcbot.module.world;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.BlockUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.world.level.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * AutoFarm — harvests mature crops and replants them.
 *
 * Supported crops: wheat, carrots, potatoes, beetroot, nether wart.
 * Scans in radius around player, harvests mature crops by interacting,
 * replants by switching to seeds and right-clicking farmland.
 */
public class AutoFarmModule extends Module {

    private static final int    SCAN_RADIUS    = 10;
    private static final int    HARVEST_TICKS  = 3;  // ticks between harvest actions

    private int cooldown = 0;

    public AutoFarmModule() {
        super("AutoFarm", "Harvests mature crops and replants — fully autonomous.", ModuleCategory.WORLD);
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;
        assert client.gameMode != null;

        if (cooldown > 0) { cooldown--; return; }

        BlockPos playerPos = client.player.blockPosition();

        // Find all mature crops in range
        List<BlockPos> matureCrops = BlockUtil.findBlocks(
                client.level, playerPos, SCAN_RADIUS,
                Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES,
                Blocks.BEETROOTS, Blocks.NETHER_WART
        ).stream()
         .filter(p -> BlockUtil.isCropMature(client.level, p))
         .sorted(Comparator.comparingDouble(p -> p.distSqr(playerPos)))
         .toList();

        if (matureCrops.isEmpty()) return;

        BlockPos crop = matureCrops.get(0);

        // Move toward crop if too far
        if (BlockUtil.distanceTo(client, crop) > 4.5) {
            // Let navigation handle it externally — just skip this tick
            return;
        }

        // Look at the crop
        Vec3 hitVec = Vec3.atCenterOf(crop);
        Vec3 eyePos = client.player.getEyePosition();
        double dx = hitVec.x - eyePos.x;
        double dy = hitVec.y - eyePos.y;
        double dz = hitVec.z - eyePos.z;
        double hDist = Math.sqrt(dx*dx + dz*dz);
        client.player.setYRot((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90));
        client.player.setXRot((float)(-Math.toDegrees(Math.atan2(dy, hDist))));

        // Harvest: attack the crop block (left-click)
        client.gameMode.startDestroyBlock(crop, Direction.UP);

        // After harvesting, replant on next tick
        cooldown = HARVEST_TICKS;

        // Check what was there and replant
        BlockPos farmlandPos = crop.below();
        if (BlockUtil.isFarmland(client.level, farmlandPos)
                && client.level.getBlockState(crop).isAir()) {
            replant(client, crop, farmlandPos);
        }
    }

    private void replant(Minecraft client, BlockPos cropPos, BlockPos farmlandPos) {
        assert client.gameMode != null;
        assert client.player != null;

        // Switch to appropriate seed
        boolean hasSeed = InventoryUtil.switchToItem(client, Items.WHEAT_SEEDS)
                || InventoryUtil.switchToItem(client, Items.CARROT)
                || InventoryUtil.switchToItem(client, Items.POTATO)
                || InventoryUtil.switchToItem(client, Items.BEETROOT_SEEDS);

        if (!hasSeed) return;

        BlockHitResult hitResult = new BlockHitResult(
                Vec3.atCenterOf(farmlandPos).add(0, 0.5, 0),
                Direction.UP, farmlandPos, false
        );

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
    }
}
