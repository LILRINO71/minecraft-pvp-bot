package com.mcbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class CombatUtil {

    private CombatUtil() {}

    // ── Attack cooldown ──────────────────────────────────────────────────

    /** Returns true when the attack cooldown is >= threshold (0-1). */
    public static boolean isCooldownReady(Minecraft client, float threshold) {
        assert client.player != null;
        return client.player.getAttackStrengthScale(0f) >= threshold;
    }

    /** True when fully charged (1.0). */
    public static boolean isFullyCharged(Minecraft client) {
        return isCooldownReady(client, 1.0f);
    }

    // ── Combo mechanics ──────────────────────────────────────────────────

    /**
     * Jump-crit: only possible when on ground, not in water, cooldown full.
     * Call this before attacking to attempt a jump for a crit hit.
     */
    public static boolean tryCritJump(Minecraft client) {
        assert client.player != null;
        if (client.player.onGround() && isFullyCharged(client)
                && !client.player.isInWater()
                && !client.player.isShiftKeyDown()) {
            client.player.setJumping(true);
            return true;
        }
        return false;
    }

    /**
     * W-tap: briefly stops forward movement so the next hit applies full knockback.
     * Toggles off for one tick; caller should restore movement after.
     */
    public static void wTap(Minecraft client) {
        assert client.player != null;
        // Briefly stop sprinting & forward — handled in KillAuraModule per-tick
        client.player.setSprinting(false);
    }

    // ── Damage estimation ────────────────────────────────────────────────

    /** Estimated damage from an End Crystal explosion at crystalPos on target. */
    public static float estimateCrystalDamage(Vec3 crystalPos, LivingEntity target) {
        // MC explosion damage formula approximation
        double dist = crystalPos.distanceTo(target.position());
        if (dist > 12.0) return 0f;
        float exposure = 1.0f; // simplified (real: raytrace-based)
        float impact = (float)((1.0 - dist / 12.0) * exposure);
        float base = (impact * impact + impact) / 2.0f * 7.0f * 12.0f + 1.0f;
        float armourReduction = target.getArmorValue() * 0.04f;
        return base * Math.max(0, 1.0f - armourReduction);
    }

    /** Estimated self-damage from crystal at crystalPos. */
    public static float estimateSelfDamage(Minecraft client, Vec3 crystalPos) {
        assert client.player != null;
        return estimateCrystalDamage(crystalPos, client.player);
    }

    // ── Crystal helpers ──────────────────────────────────────────────────

    /** End crystals in the world within range blocks. */
    public static List<EndCrystal> getCrystalsInRange(Minecraft client, double range) {
        assert client.player != null;
        assert client.level != null;
        Vec3 pos = client.player.position();
        AABB box = new AABB(pos.x-range, pos.y-range, pos.z-range,
                          pos.x+range, pos.y+range, pos.z+range);
        return client.level.getEntitiesOfClass(EndCrystal.class, box, e -> true);
    }

    /** Finds the End Crystal dealing maximum damage to target with acceptable self-damage. */
    public static EndCrystal bestCrystalTarget(Minecraft client,
                                                      LivingEntity target,
                                                      float maxSelfDamage,
                                                      double range) {
        EndCrystal best = null;
        float bestDmg = -1;
        for (EndCrystal crystal : getCrystalsInRange(client, range)) {
            Vec3 cp = crystal.position();
            float dmg = estimateCrystalDamage(cp, target);
            float self = estimateSelfDamage(client, cp);
            if (self <= maxSelfDamage && dmg > bestDmg) {
                bestDmg = dmg;
                best = crystal;
            }
        }
        return best;
    }

    // ── Mace helpers ─────────────────────────────────────────────────────

    /**
     * Mace smash damage formula:
     * base_damage + fallDistance * 3 (approximate from MC sources)
     * Returns estimated total damage.
     */
    public static float estimateMaceDamage(float fallDistance) {
        return 6.0f + fallDistance * 3.0f; // base 6 + 3 per block fallen
    }

    /** Optimal height to drop from to guarantee a one-hit kill on a target. */
    public static float heightForInstakill(float targetMaxHealth) {
        // Solve: 6 + h*3 >= targetMaxHealth => h >= (targetMaxHealth - 6) / 3
        return Math.max(0, (targetMaxHealth - 6.0f) / 3.0f);
    }

    // ── Inventory helpers ────────────────────────────────────────────────

    /** Switches to the mace if it's in the hotbar. Returns true if found. */
    public static boolean switchToMace(Minecraft client) {
        return InventoryUtil.switchToItem(client, Items.MACE);
    }

    /** Switches to a sword if it's in the hotbar. Returns true if found. */
    public static boolean switchToSword(Minecraft client) {
        if (InventoryUtil.switchToItem(client, Items.NETHERITE_SWORD)) return true;
        if (InventoryUtil.switchToItem(client, Items.DIAMOND_SWORD)) return true;
        if (InventoryUtil.switchToItem(client, Items.IRON_SWORD)) return true;
        return false;
    }

    /** Switches to an axe. Returns true if found. */
    public static boolean switchToAxe(Minecraft client) {
        if (InventoryUtil.switchToItem(client, Items.NETHERITE_AXE)) return true;
        if (InventoryUtil.switchToItem(client, Items.DIAMOND_AXE)) return true;
        return InventoryUtil.switchToItem(client, Items.IRON_AXE);
    }

    /**
     * Reads the hotbar and switches to the single best melee weapon available
     * (axe/sword by material tier). Returns true if a weapon was selected.
     */
    public static boolean switchToBestWeapon(Minecraft client) {
        int slot = InventoryUtil.bestMeleeSlot(client);
        if (slot < 0) return false;
        return InventoryUtil.switchToSlot(client, slot);
    }

    // ── Attack ───────────────────────────────────────────────────────────

    /** Performs an attack interaction on the entity. */
    public static void attack(Minecraft client, LivingEntity target) {
        assert client.gameMode != null;
        assert client.player != null;
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Attacks any entity (e.g. End Crystals). */
    public static void attack(Minecraft client, Entity target) {
        if (client.gameMode == null || client.player == null) return;
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
    }
}
