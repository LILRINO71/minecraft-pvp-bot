package com.mcbot.module.player;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * SpeedBridge — places blocks below the player while walking backward,
 * allowing superhuman-speed bridge building without falling.
 *
 * The bot:
 *  1. Detects when the player is at the edge of a block (about to fall)
 *  2. Sneaks automatically to stay on edge
 *  3. Places a block under the air where the player is about to step
 *
 * Mimics pro speedbridge technique but at every single tick.
 */
public class SpeedBridgeModule extends Module {

    private static final int PLACE_COOLDOWN = 1; // 1 tick between placements
    private int cooldown = 0;

    public SpeedBridgeModule() {
        super("SpeedBridge", "Automatically places blocks while walking — superhuman bridging.", ModuleCategory.PLAYER);
    }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;
        assert client.gameMode != null;

        if (cooldown > 0) { cooldown--; return; }

        // Only activate when player is moving and airborne at edge
        if (client.player.onGround()) {
            // Check if next step would be air
            Vec3 vel = client.player.getDeltaMovement();
            if (Math.abs(vel.x) < 0.01 && Math.abs(vel.z) < 0.01) return;

            // Position in front of player (in movement direction)
            Vec3 pos = client.player.position();
            Vec3 lookDir = client.player.getViewVector(1.0f).scale(0.8);
            BlockPos placePos = BlockPos.containing(pos.add(lookDir)).below();

            // Check if block needed
            if (!client.level.getBlockState(placePos).isAir()) return;
            BlockPos placeBase = placePos.below();
            if (client.level.getBlockState(placeBase).isAir()) return;

            // Auto-sneak at edge to prevent falling
            client.player.setShiftKeyDown(true);

            // Place block
            Vec3 hitVec = Vec3.atCenterOf(placeBase).add(0, 0.5, 0);
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, placeBase, false);
            InteractionResult result = client.gameMode.useItemOn(
                    client.player, InteractionHand.MAIN_HAND, hitResult
            );

            if (result.consumesAction()) {
                cooldown = PLACE_COOLDOWN;
            }
        }
    }
}
