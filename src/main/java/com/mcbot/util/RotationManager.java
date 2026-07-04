package com.mcbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * RotationManager — central aiming for combat modules, with tunable aim modes.
 *
 * <p>Modes (set by the SilentAim module's settings):
 * <ul>
 *   <li><b>Snap</b> — instantly face the target (camera turns).</li>
 *   <li><b>Smooth</b> — turn the camera toward the target a few degrees per tick (human-like).</li>
 *   <li><b>Silent</b> — never turn the camera; send a rotation packet to the server so hits land.
 *       Silent also smooths: it tugs the <i>server</i> rotation toward the target at {@link #speed}
 *       degrees/tick instead of snapping, so it looks legit server-side.</li>
 * </ul>
 *
 * <p>Because the client's own movement packet is sent during LocalPlayer.tick and our packet+attack
 * are sent afterwards (END_CLIENT_TICK), the server sees our aim for the attack — no mixin needed.
 */
public final class RotationManager {

    private RotationManager() {}

    public static final String SNAP = "Snap";
    public static final String SMOOTH = "Smooth";
    public static final String SILENT = "Silent";

    private static volatile String mode = SNAP;
    /** Max degrees to turn per tick in Smooth/Silent mode. */
    private static volatile double speed = 30.0;

    // Working rotation carried between ticks (so smoothing is continuous).
    private static float curYaw = 0f, curPitch = 0f;
    private static boolean initialized = false;

    public static void setMode(String m) { mode = m; }
    public static void setSpeed(double s) { speed = s; }
    public static String getMode() { return mode; }
    public static boolean isSilent() { return SILENT.equalsIgnoreCase(mode); }

    /** Back-compat: SilentAim toggling on/off maps to Silent / Snap. */
    public static void setSilent(boolean silent) { mode = silent ? SILENT : SNAP; }

    /** Reset the smoothing origin to the player's current facing (call when acquiring a target). */
    public static void reset(Minecraft client) {
        if (client.player != null) {
            curYaw = client.player.getYRot();
            curPitch = client.player.getXRot();
            initialized = true;
        }
    }

    public static float[] rotationTo(Minecraft client, Vec3 point) {
        Vec3 eyes = client.player.getEyePosition();
        double dx = point.x - eyes.x, dy = point.y - eyes.y, dz = point.z - eyes.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, hDist)));
        return new float[] { yaw, pitch };
    }

    public static void aimAt(Minecraft client, Entity target) {
        if (client.player == null || target == null) return;
        aimAt(client, target.getEyePosition());
    }

    public static void aimAt(Minecraft client, Vec3 point) {
        if (client.player == null) return;
        float[] rot = rotationTo(client, point);
        float targetYaw = rot[0], targetPitch = rot[1];

        if (SNAP.equalsIgnoreCase(mode)) {
            client.player.setYRot(targetYaw);
            client.player.setXRot(targetPitch);
            curYaw = targetYaw; curPitch = targetPitch;
            return;
        }

        // Smooth + Silent: step the working rotation toward the target.
        if (!initialized) reset(client);
        curYaw   = stepToward(curYaw, targetYaw, (float) speed, true);
        curPitch = stepToward(curPitch, targetPitch, (float) speed, false);

        if (SILENT.equalsIgnoreCase(mode)) {
            sendServerRotation(client, curYaw, curPitch);         // camera untouched
        } else { // SMOOTH
            client.player.setYRot(curYaw);
            client.player.setXRot(curPitch);
        }
    }

    /** Steps {@code current} toward {@code target} by at most {@code maxStep} degrees. */
    private static float stepToward(float current, float target, float maxStep, boolean wrap) {
        float diff = target - current;
        if (wrap) { // shortest angular path for yaw
            diff = ((diff % 360f) + 540f) % 360f - 180f;
        }
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    /** Sends a rotation-only movement packet to the server without moving the camera. */
    public static void sendServerRotation(Minecraft client, float yaw, float pitch) {
        if (client.player == null || client.player.connection == null) return;
        client.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, client.player.onGround(), client.player.horizontalCollision));
    }
}
