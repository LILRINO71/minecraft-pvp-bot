package com.mcbot.targeting;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

/**
 * TargetConfig — central targeting settings.
 *
 * Controls what the bot will and won't attack across ALL combat modules
 * (KillAura, AutoCrystal, MaceCombat, ElytraStrike).
 *
 * This is a singleton — access via TargetConfig.get().
 */
public class TargetConfig {

    private static final TargetConfig INSTANCE = new TargetConfig();
    public static TargetConfig get() { return INSTANCE; }

    // ── Target toggles ────────────────────────────────────────────────────

    /** Attack hostile mobs (zombies, skeletons, creepers, etc.) */
    public boolean attackHostile  = true;

    /** Attack neutral mobs (endermen, wolves, etc.) when not provoked */
    public boolean attackNeutral  = false;

    /** Attack passive mobs (cows, pigs, villagers, etc.) */
    public boolean attackPassive  = false;

    /** Attack players (only useful on a server) */
    public boolean attackPlayers  = false;

    /** If true, never attack friends even if attackPlayers = true */
    public boolean protectFriends = true;

    /** If true, always attack foes even if their mob type is disabled */
    public boolean alwaysAttackFoes = true;

    // ── Max range ─────────────────────────────────────────────────────────
    public double attackRange = 4.5;

    // ── Mob-type classification ───────────────────────────────────────────

    /**
     * Returns true if this entity should be targeted, given current config
     * and the friend/foe lists.
     */
    public boolean shouldTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;

        FriendList fl = FriendList.get();

        // Player handling
        if (entity instanceof Player player) {
            String name = player.getGameProfile().name();
            if (fl.isFriend(name)) return false;              // never hit friends
            if (fl.isFoe(name) && alwaysAttackFoes) return true;
            return attackPlayers;
        }

        // Explicit foe override
        if (alwaysAttackFoes && fl.isFoe(entity.getStringUUID())) return true;

        // Mob classification
        if (entity instanceof Monster) return attackHostile;
        if (entity instanceof Animal)  return attackPassive;

        // Everything else (golems, bats, etc.) treated as neutral
        return attackNeutral;
    }

    /**
     * Returns a short summary string for the GUI.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        if (attackHostile)  sb.append("Hostile ");
        if (attackNeutral)  sb.append("Neutral ");
        if (attackPassive)  sb.append("Passive ");
        if (attackPlayers)  sb.append("Players ");
        if (sb.isEmpty())   sb.append("Nothing");
        return sb.toString().trim();
    }
}
