package com.mcbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * RotationManager — central aiming for combat modules, with an optional "silent" mode.
 *
 * <p>Normal mode: {@link #aimAt} rotates the actual player (the camera turns), exactly like
 * the old {@code EntityUtil.lookAt}.
 *
 * <p>Silent mode (toggled by the SilentAim module): the camera is NOT moved. Instead we send a
 * rotation-only movement packet ({@link ServerboundMovePlayerPacket.Rot}) to the server aimed at
 * the target. Because the client's own per-tick movement packet is sent first (during
 * LocalPlayer.tick) and our packet + the attack are sent afterwards (in END_CLIENT_TICK), the
 * server sees the player facing the target for exactly the attack — server-side aim without any
 * on-screen head movement. This is the standard packet-based "silent aim" and needs no mixin.
 */
public final class RotationManager {

    private RotationManager() {}

    private static volatile boolean silent = false;

    public static void setSilent(boolean s) { silent = s; }
    public static boolean isSilent() { return silent; }

    /** Computes {yaw, pitch} in degrees from the player's eyes to a world point. */
    public static float[] rotationTo(Minecraft client, Vec3 point) {
        Vec3 eyes = client.player.getEyePosition();
        double dx = point.x - eyes.x;
        double dy = point.y - eyes.y;
        double dz = point.z - eyes.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, hDist)));
        return new float[] { yaw, pitch };
    }

    /** Aim at an entity's eye position (silent or camera, depending on mode). */
    public static void aimAt(Minecraft client, Entity target) {
        if (client.player == null || target == null) return;
        aimAt(client, target.getEyePosition());
    }

    /** Aim at a world point (silent or camera, depending on mode). */
    public static void aimAt(Minecraft client, Vec3 point) {
        if (client.player == null) return;
        float[] rot = rotationTo(client, point);
        if (silent) {
            sendServerRotation(client, rot[0], rot[1]);
        } else {
            client.player.setYRot(rot[0]);
            client.player.setXRot(rot[1]);
        }
    }

    /** Sends a rotation-only movement packet to the server without moving the camera. */
    public static void sendServerRotation(Minecraft client, float yaw, float pitch) {
        if (client.player == null || client.player.connection == null) return;
        client.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, client.player.onGround(), client.player.horizontalCollision));
    }
}
