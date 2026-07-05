package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.settings.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * AttributeSwap — the 1.21 "attribute / weapon / breach swap" technique (vanilla bug MC-28289).
 *
 * <p>You hold a fast, high-damage <b>carry</b> weapon (e.g. a Sharpness netherite sword) so the
 * attack cooldown charges to 100% on it. At the instant of the hit, the module swaps to a
 * <b>strike</b> weapon (e.g. a Breach mace) and attacks in the SAME tick, then swaps back. Because
 * the server processes the slot-change packet before the attack packet, the hit lands with the
 * strike weapon's <i>enchantments + mace-smash property</i> while damage/attack-speed/charge stay
 * from the carry weapon — sword damage that also ignores armor (Breach) or smashes (Mace).
 *
 * <p>Verified packet order (one tick): SetCarriedItem(strike) → attack → swing → SetCarriedItem(carry).
 * KillAura/TriggerBot route their hits through {@link #strike} when this is on, and hold the carry
 * weapon between hits so the charge builds on it.
 *
 * <p><b>Server caveat:</b> this is an exploit. It works fully in singleplayer; on servers running
 * Grim/Vulcan or Paper's update-equipment-on-player-actions it may be flagged or only land ~50% of
 * the time, and Mojang began patching it in 26.2 snapshots. Gated behind this toggle.
 */
public class AttributeSwapModule extends Module {

    private final IntSetting carry = addSetting(new IntSetting(
            "carrySlot", "Hotbar slot (1-9) of the fast/high-damage weapon you charge on (e.g. sword).", 1, 1, 9, 1));
    private final IntSetting strikeSlot = addSetting(new IntSetting(
            "strikeSlot", "Hotbar slot (1-9) of the weapon you HIT with (e.g. Breach mace).", 2, 1, 9, 1));
    private final BoolSetting swapBackSetting = addSetting(new BoolSetting(
            "swapBack", "Swap back to the carry weapon after each hit (to rebuild charge).", true));

    // Static mirror so KillAura/TriggerBot can consult it without a hard reference.
    private static volatile boolean active = false;
    private static volatile int carrySlot0 = 0;   // 0-based
    private static volatile int strikeSlot0 = 1;
    private static volatile boolean holdCarry = true;
    private static volatile boolean swapBack = true;

    public AttributeSwapModule() {
        super("AttributeSwap", "Weapon/breach swap: sword damage + charge with the strike weapon's enchants.",
                ModuleCategory.COMBAT);
    }

    @Override protected void onEnable()  { active = true;  sync(); }
    @Override protected void onDisable() { active = false; }

    @Override
    protected void onTick(Minecraft client) {
        sync();
        // Between hits, keep the carry weapon selected so the cooldown charges on it.
        if (holdCarry) holdCarry(client);
    }

    private void sync() {
        carrySlot0  = carry.get() - 1;
        strikeSlot0 = strikeSlot.get() - 1;
        swapBack    = swapBackSetting.get();
    }

    public static boolean isActive() { return active; }

    /** Ensures the carry weapon is selected (client + server) so charge builds on it. */
    public static void holdCarry(Minecraft client) {
        LocalPlayer p = client.player;
        if (p == null || p.connection == null) return;
        if (p.getInventory().getSelectedSlot() != carrySlot0) {
            p.connection.send(new ServerboundSetCarriedItemPacket(carrySlot0));
            p.getInventory().setSelectedSlot(carrySlot0);
        }
    }

    /**
     * Atomic swap-hit-return: swap to the strike slot, attack the target, swing, swap back.
     * Must run in a single tick so the server sees the strike weapon for exactly this hit.
     */
    public static void strike(Minecraft client, Entity target) {
        LocalPlayer p = client.player;
        if (p == null || p.connection == null || client.gameMode == null) return;

        // a. tell the server we now hold the strike weapon
        p.connection.send(new ServerboundSetCarriedItemPacket(strikeSlot0));
        p.getInventory().setSelectedSlot(strikeSlot0);

        // b+c. attack (server reads the strike weapon live) + swing
        client.gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);

        // d. swap back to the carry weapon to rebuild charge
        if (swapBack) {
            p.connection.send(new ServerboundSetCarriedItemPacket(carrySlot0));
            p.getInventory().setSelectedSlot(carrySlot0);
        }
    }
}
