package com.mcbot.module.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Exploration — systematically explores the world in a spiral pattern.
 *
 * Uses Baritone's GoalXZ to navigate to each point in an expanding spiral.
 * Records visited chunks and avoids revisiting.
 * Also searches for specific structures if requested.
 */
public class ExplorationModule extends Module {

    private static final int CHUNK_STEP = 128; // blocks per spiral step

    private final Set<Long> visitedChunks = new HashSet<>();
    private final Deque<BlockPos> spiral = new ArrayDeque<>();
    private int spiralRadius = 0;
    private int spiralLeg = 0;
    private int spiralSteps = 1;
    private int stepCount = 0;
    private boolean generatingSpiral = false;

    private String searchTarget = null; // e.g. "village", "stronghold", null = pure explore

    public ExplorationModule() {
        super("Exploration", "Systematically explores the world using Baritone navigation.", ModuleCategory.WORLD);
    }

    @Override
    protected void onEnable() {
        visitedChunks.clear();
        spiral.clear();
        spiralRadius = 0; spiralLeg = 0; spiralSteps = 1; stepCount = 0;
        generateNextSpiralPoints(20);
    }

    @Override
    protected void onDisable() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        if (baritone.getPathingBehavior().isPathing()) return; // still moving

        // Mark current chunk visited
        BlockPos pos = client.player.blockPosition();
        visitedChunks.add(chunkKey(pos));

        // Get next destination
        if (spiral.isEmpty()) generateNextSpiralPoints(10);
        if (spiral.isEmpty()) { disable(); return; }

        BlockPos next = spiral.poll();

        // Skip already-visited
        while (next != null && visitedChunks.contains(chunkKey(next))) {
            if (spiral.isEmpty()) generateNextSpiralPoints(10);
            next = spiral.poll();
        }
        if (next == null) { disable(); return; }

        // Navigate
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(next.getX(), next.getZ()));

        client.player.sendSystemMessage(Component.literal(
                "[MC BOT] Exploring → " + next.getX() + ", " + next.getZ()));
    }

    // ── Spiral generation (outward square spiral) ─────────────────────────

    private void generateNextSpiralPoints(int count) {
        // Classic outward spiral: right, up, left, down, repeat with increasing step
        int[] dx = {1, 0, -1, 0};
        int[] dz = {0, 1, 0, -1};
        int x = (spiral.isEmpty() && visitedChunks.isEmpty()) ? 0 :
                (spiral.isEmpty() ? 0 : spiral.peekLast().getX());
        int z = spiral.isEmpty() ? 0 : spiral.peekLast().getZ();

        for (int i = 0; i < count; i++) {
            x += dx[spiralLeg] * CHUNK_STEP;
            z += dz[spiralLeg] * CHUNK_STEP;
            spiral.add(new BlockPos(x, 64, z));
            stepCount++;
            if (stepCount >= spiralSteps) {
                stepCount = 0;
                spiralLeg = (spiralLeg + 1) % 4;
                if (spiralLeg % 2 == 0) spiralSteps++;
            }
        }
    }

    private long chunkKey(BlockPos pos) {
        // Chunk coordinates
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return ((long)cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public void setSearchTarget(String target) { this.searchTarget = target; }
    public int getVisitedChunkCount() { return visitedChunks.size(); }
}
