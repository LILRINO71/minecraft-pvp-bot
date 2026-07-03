package com.mcbot.util;

import com.mcbot.targeting.TargetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EntityUtil {

    private EntityUtil() {}

    /** All living entities within [range] blocks, excluding the local player. */
    public static List<LivingEntity> getLivingInRange(Minecraft client, double range) {
        assert client.player != null;
        assert client.level != null;
        Vec3 pos = client.player.position();
        AABB box = new AABB(
                pos.x - range, pos.y - range, pos.z - range,
                pos.x + range, pos.y + range, pos.z + range
        );
        return client.level.getEntitiesOfClass(LivingEntity.class, box, e ->
                e != client.player && e.isAlive()
        );
    }

    /**
     * Nearest valid target according to the global TargetConfig.
     * This is the primary targeting method used by all combat modules.
     */
    public static Optional<LivingEntity> getNearestTarget(Minecraft client, double range) {
        TargetConfig tc = TargetConfig.get();
        return getLivingInRange(client, range).stream()
                .filter(tc::shouldTarget)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(client.player)));
    }

    /**
     * Legacy overload — still works, but prefer getNearestTarget(client, range).
     * @deprecated use getNearestTarget(client, range) with TargetConfig instead
     */
    @Deprecated
    public static Optional<LivingEntity> getNearestTarget(Minecraft client,
                                                           double range,
                                                           boolean targetPlayers,
                                                           boolean targetMobs) {
        return getLivingInRange(client, range).stream()
                .filter(e -> {
                    if (targetPlayers && e instanceof Player) return true;
                    if (targetMobs && (e instanceof Monster || e instanceof Monster)) return true;
                    return false;
                })
                .min(Comparator.comparingDouble(e ->
                        e.distanceToSqr(client.player)
                ));
    }

    /** All targetable entities in range (filtered by TargetConfig). */
    public static List<LivingEntity> getTargetsInRange(Minecraft client, double range) {
        TargetConfig tc = TargetConfig.get();
        return getLivingInRange(client, range).stream()
                .filter(tc::shouldTarget)
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(client.player)))
                .toList();
    }

    /** All players except local player within range. */
    public static List<Player> getPlayersInRange(Minecraft client, double range) {
        assert client.player != null;
        assert client.level != null;
        Vec3 pos = client.player.position();
        AABB box = new AABB(
                pos.x - range, pos.y - range, pos.z - range,
                pos.x + range, pos.y + range, pos.z + range
        );
        return client.level.getEntitiesOfClass(Player.class, box,
                e -> e != client.player && e.isAlive()
        );
    }

    /** True if the entity can be seen (raytrace check). */
    public static boolean canSee(Minecraft client, Entity target) {
        assert client.player != null;
        assert client.level != null;
        return client.player.hasLineOfSight(target);
    }

    /** Distance squared from local player to entity. */
    public static double distanceSq(Minecraft client, Entity e) {
        assert client.player != null;
        return client.player.distanceToSqr(e);
    }

    public static double distance(Minecraft client, Entity e) {
        return Math.sqrt(distanceSq(client, e));
    }

    /** Look at an entity (yaw + pitch). */
    public static void lookAt(Minecraft client, Entity target) {
        assert client.player != null;
        Vec3 eyes = client.player.getEyePosition();
        Vec3 tPos = target.getEyePosition();
        double dx = tPos.x - eyes.x;
        double dy = tPos.y - eyes.y;
        double dz = tPos.z - eyes.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, hDist)));
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
    }

    /** Look at a position in 3D space. */
    public static void lookAt(Minecraft client, Vec3 pos) {
        assert client.player != null;
        Vec3 eyes = client.player.getEyePosition();
        double dx = pos.x - eyes.x;
        double dy = pos.y - eyes.y;
        double dz = pos.z - eyes.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, hDist)));
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
    }
}
