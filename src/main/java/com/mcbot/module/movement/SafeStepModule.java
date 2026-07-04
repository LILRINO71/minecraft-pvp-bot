package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SafeStep (Safe Walk) — stops you from walking off the edge of blocks.
 *
 * <p>Each tick, if you're on the ground and about to move past a ledge (no block would be under any
 * corner of your feet at the projected position), your horizontal velocity is cancelled so you stop
 * right at the edge. Sneak to override and step off deliberately.
 */
public class SafeStepModule extends Module {

    public SafeStepModule() {
        super("SafeStep", "Won't let you walk off block edges (sneak to override).",
                ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onTick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null) return;
        if (!player.onGround()) return;
        if (player.isShiftKeyDown()) return; // holding sneak = intentional step-off

        Vec3 v = player.getDeltaMovement();
        double vx = v.x, vz = v.z;
        if (vx * vx + vz * vz < 1.0e-6) return;

        AABB box = player.getBoundingBox();
        double y = box.minY - 0.05; // just below the feet
        boolean supported =
                ground(client, box.minX + vx, y, box.minZ + vz) ||
                ground(client, box.maxX + vx, y, box.minZ + vz) ||
                ground(client, box.minX + vx, y, box.maxZ + vz) ||
                ground(client, box.maxX + vx, y, box.maxZ + vz);

        if (!supported) {
            // Freeze horizontal motion; keep vertical (so gravity/jumps still work).
            player.setDeltaMovement(0.0, v.y, 0.0);
        }
    }

    private boolean ground(Minecraft client, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        return !client.level.getBlockState(p).isAir();
    }
}
