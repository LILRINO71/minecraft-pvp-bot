package com.mcbot.module.movement;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * NoFall — cancels fall damage. While you're falling, it tells the server you're on the ground by
 * sending a status-only movement packet with onGround=true, so the server never applies fall
 * damage. Packet-based (no mixin). Meteor-equivalent.
 */
public class NoFallModule extends Module {

    public NoFallModule() {
        super("NoFall", "Prevents fall damage (sends on-ground packets while falling).",
                ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onTick(Minecraft client) {
        var p = client.player;
        if (p == null || p.connection == null) return;
        if (p.onGround() || p.isFallFlying() || p.getAbilities().flying) return;
        // Only bother once we've fallen far enough to actually take damage.
        if (p.fallDistance > 2.5 && p.getDeltaMovement().y < 0) {
            p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
        }
    }
}
