package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * AutoPearl — "pearl catching": close the gap on a running target and bail out
 * when things go bad.
 *
 *   CHASE   : target is out of melee range but reachable → lob a pearl at them
 *             so the bot teleports right onto them (pearl-catching the runner).
 *   ESCAPE  : own health is low and an enemy is close → throw a pearl up-and-away
 *             to disengage.
 *   TRACK   : detects enemy-thrown pearls in flight and reports where they'll
 *             land so the bot (and you) can react.
 *
 * Respects the natural ender-pearl cooldown via an internal timer.
 */
public class AutoPearlModule extends Module {

    private static final double MIN_CHASE   = 6.0;   // don't pearl if already in melee
    private static final double MAX_CHASE   = 34.0;  // out of practical pearl range
    private static final float  LOW_HEALTH  = 7.0f;  // escape below this
    private static final double DANGER_RANGE = 5.0;
    private static final int    THROW_CD    = 22;    // ~1.1s, matches pearl cooldown

    private int throwTimer = 0;

    public AutoPearlModule() {
        super("AutoPearl", "Pearl-catch runners and escape-pearl when low.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() { throwTimer = 0; }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;
        if (throwTimer > 0) throwTimer--;

        trackEnemyPearls(client);

        if (throwTimer > 0) return;
        if (InventoryUtil.findInHotbar(client, Items.ENDER_PEARL) < 0) return;

        Optional<LivingEntity> t = EntityUtil.getNearestTarget(client, MAX_CHASE);
        if (t.isEmpty()) return;
        LivingEntity target = t.get();
        double dist = EntityUtil.distance(client, target);

        float health = client.player.getHealth();

        // ── ESCAPE ────────────────────────────────────────────────────────
        if (health <= LOW_HEALTH && dist <= DANGER_RANGE) {
            // Aim up and directly away from the threat, then throw.
            Vec3 away = client.player.position().subtract(target.position()).normalize();
            float yaw = (float) (Math.toDegrees(Math.atan2(away.z, away.x)) - 90.0);
            client.player.setYRot(yaw);
            client.player.setXRot(-35f); // arc up and back
            throwPearl(client, "§e[MC BOT] Escape pearl!");
            return;
        }

        // ── CHASE ─────────────────────────────────────────────────────────
        if (dist >= MIN_CHASE && dist <= MAX_CHASE) {
            // Aim at the target with an upward arc that grows with distance.
            EntityUtil.lookAt(client, target);
            float arc = (float) Math.min(35.0, dist * 1.2);
            client.player.setXRot(client.player.getXRot() - arc);
            throwPearl(client, "§b[MC BOT] Pearl-catching target (" + (int) dist + "m)");
        }
    }

    private void throwPearl(Minecraft client, String msg) {
        if (!InventoryUtil.switchToItem(client, Items.ENDER_PEARL)) return;
        if (client.gameMode == null) return;
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
        throwTimer = THROW_CD;
        if (client.player != null) client.player.sendSystemMessage(Component.literal(msg));
    }

    /** Reports the predicted landing of any nearby enemy-thrown pearls. */
    private void trackEnemyPearls(Minecraft client) {
        Vec3 p = client.player.position();
        double r = 40.0;
        AABB box = new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r);
        List<ThrownEnderpearl> pearls = client.level.getEntitiesOfClass(
                ThrownEnderpearl.class, box,
                e -> e.getOwner() != client.player);
        for (ThrownEnderpearl pearl : pearls) {
            // Only report fast-moving (in-flight) pearls, once, roughly.
            if (pearl.getDeltaMovement().lengthSqr() < 0.05) continue;
            if (pearl.tickCount % 10 != 0) continue;
            Vec3 v = pearl.getDeltaMovement();
            Vec3 landing = pearl.position().add(v.scale(8));
            client.player.sendSystemMessage(Component.literal(String.format(
                    "§7[MC BOT] Enemy pearl heading to ~(%.0f, %.0f, %.0f)",
                    landing.x, landing.y, landing.z)));
        }
    }

    @Override
    protected void onDisable() { throwTimer = 0; }
}
