package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.EntityUtil;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Grapple — fishing-rod "grappling hook" combat tool.
 *
 *   CAST  : a target is in range and we hold a rod → aim at it and cast.
 *   REEL  : once the bobber hooks the target (or the cast lands close), reel it
 *           back in — the rod's retract pulls the hooked enemy toward the bot,
 *           dragging runners back into melee range and breaking their spacing.
 *
 * Pairs well with KillAura: grapple to yank, then strafe-combo them down.
 */
public class GrappleModule extends Module {

    private static final double GRAPPLE_RANGE = 12.0;
    private static final int    CAST_TIMEOUT  = 20;  // give up waiting for a hook
    private static final int    REEL_CD       = 14;  // ticks before re-casting

    private enum State { IDLE, CAST }
    private State state = State.IDLE;
    private int stateTimer = 0;
    private int cooldown = 0;

    public GrappleModule() {
        super("Grapple", "Fishing-rod grappling hook — yank targets back into reach.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() { state = State.IDLE; cooldown = 0; }

    @Override
    protected void onTick(Minecraft client) {
        assert client.player != null;
        assert client.level != null;
        if (cooldown > 0) cooldown--;
        stateTimer++;

        if (InventoryUtil.findInHotbar(client, Items.FISHING_ROD) < 0) return;

        FishingHook hook = findOwnHook(client);

        switch (state) {
            case IDLE -> {
                if (cooldown > 0) return;
                Optional<LivingEntity> t = EntityUtil.getNearestTarget(client, GRAPPLE_RANGE);
                if (t.isEmpty()) return;
                LivingEntity target = t.get();
                if (hook != null) return; // a bobber is already out

                InventoryUtil.switchToItem(client, Items.FISHING_ROD);
                EntityUtil.lookAt(client, target);
                useRod(client);
                state = State.CAST;
                stateTimer = 0;
            }
            case CAST -> {
                boolean hooked = hook != null && hook.getHookedIn() instanceof LivingEntity;
                if (hooked || stateTimer > CAST_TIMEOUT) {
                    // Reel in: this is the "yank" that drags the target toward us.
                    InventoryUtil.switchToItem(client, Items.FISHING_ROD);
                    useRod(client);
                    if (hooked) {
                        client.player.sendSystemMessage(Component.literal(
                                "§a[MC BOT] Grapple hit — reeling target in!"));
                    }
                    state = State.IDLE;
                    cooldown = REEL_CD;
                }
            }
        }
    }

    private void useRod(Minecraft client) {
        if (client.gameMode == null) return;
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Finds the fishing bobber belonging to the local player, if one is out. */
    private FishingHook findOwnHook(Minecraft client) {
        Vec3 p = client.player.position();
        double r = 40.0;
        AABB box = new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r);
        List<FishingHook> hooks = client.level.getEntitiesOfClass(
                FishingHook.class, box, h -> h.getPlayerOwner() == client.player);
        return hooks.isEmpty() ? null : hooks.get(0);
    }

    @Override
    protected void onDisable() { state = State.IDLE; cooldown = 0; }
}
