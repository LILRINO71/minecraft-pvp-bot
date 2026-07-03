package com.mcbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class BlockUtil {

    private BlockUtil() {}

    public static boolean isAir(Level world, BlockPos pos) {
        return world.getBlockState(pos).isAir();
    }

    public static boolean isSolid(Level world, BlockPos pos) {
        return world.getBlockState(pos).blocksMotion();
    }

    public static boolean isWalkable(Level world, BlockPos pos) {
        return isSolid(world, pos.below()) && isAir(world, pos) && isAir(world, pos.above());
    }

    /** True if the block is safe to stand on (not lava/fire/cactus/magma). */
    public static boolean isSafe(Level world, BlockPos pos) {
        Block b = world.getBlockState(pos).getBlock();
        return b != Blocks.LAVA && b != Blocks.FIRE && b != Blocks.CACTUS && b != Blocks.MAGMA_BLOCK;
    }

    /** Returns all blocks matching any of the given blocks within radius. */
    public static List<BlockPos> findBlocks(Level world, BlockPos centre, int radius, Block... targets) {
        Set<Block> targetSet = new HashSet<>(Arrays.asList(targets));
        List<BlockPos> results = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    if (targetSet.contains(world.getBlockState(p).getBlock())) results.add(p);
                }
            }
        }
        results.sort(Comparator.comparingInt(p -> (int) p.distSqr(centre)));
        return results;
    }

    /** True if an End Crystal can be placed on this block (obsidian or bedrock). */
    public static boolean canPlaceCrystal(Level world, BlockPos pos) {
        Block b = world.getBlockState(pos).getBlock();
        if (b != Blocks.OBSIDIAN && b != Blocks.BEDROCK && b != Blocks.CRYING_OBSIDIAN) return false;
        // Since 1.16 a crystal only needs the single block directly above to be free.
        return isAir(world, pos.above());
    }

    public static List<BlockPos> getCrystalPlacementPositions(Level world, Vec3 targetPos, int range) {
        BlockPos centre = BlockPos.containing(targetPos);
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos p = centre.offset(dx, -1, dz);
                if (canPlaceCrystal(world, p)) candidates.add(p);
                BlockPos p2 = centre.offset(dx, 0, dz);
                if (canPlaceCrystal(world, p2)) candidates.add(p2);
            }
        }
        return candidates;
    }

    public static boolean isCropMature(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        return false;
    }

    public static boolean isFarmland(Level world, BlockPos pos) {
        return world.getBlockState(pos).is(Blocks.FARMLAND);
    }

    public static Direction getPlaceFace(BlockPos pos, Vec3 playerPos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        double dy = playerPos.y - centre.y, dx = playerPos.x - centre.x, dz = playerPos.z - centre.z;
        if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > Math.abs(dz)) return dy > 0 ? Direction.UP : Direction.DOWN;
        else if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        else return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static double distanceTo(Minecraft client, BlockPos pos) {
        if (client.player == null) return Double.MAX_VALUE;
        return client.player.position().distanceTo(Vec3.atCenterOf(pos));
    }
}
