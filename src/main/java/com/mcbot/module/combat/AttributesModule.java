package com.mcbot.module.combat;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Attributes — spawns modified player attribute values for a PvP edge: longer reach, faster attack
 * speed, knockback resistance, higher jumps, bigger/smaller scale. Each setting defaults to the
 * vanilla value, so anything you leave alone is unchanged. Originals are captured on enable and
 * restored on disable.
 *
 * <p>Client-side: reach/block-reach/jump/scale take effect immediately in singleplayer. On servers
 * the authoritative checks may reject values beyond their tolerance (reach) or ignore them
 * entirely (attack speed / knockback resistance) — that's inherent to client attribute edits.
 */
public class AttributesModule extends Module {

    private final DoubleSetting reach      = addSetting(new DoubleSetting("reach",
            "Attack (entity) reach in blocks. Vanilla 3.0.", 3.0, 3.0, 6.0, 0.1));
    private final DoubleSetting blockReach = addSetting(new DoubleSetting("blockReach",
            "Block interaction reach. Vanilla 4.5.", 4.5, 4.5, 6.0, 0.1));
    private final DoubleSetting attackSpeed = addSetting(new DoubleSetting("attackSpeed",
            "Attack-cooldown recharge rate. Vanilla 4.0 (higher = faster).", 4.0, 4.0, 20.0, 0.5));
    private final DoubleSetting knockbackRes = addSetting(new DoubleSetting("kbResist",
            "Knockback resistance 0-1. Vanilla 0.", 0.0, 0.0, 1.0, 0.05));
    private final DoubleSetting jump = addSetting(new DoubleSetting("jump",
            "Jump strength. Vanilla 0.42.", 0.42, 0.42, 2.0, 0.01));
    private final DoubleSetting scale = addSetting(new DoubleSetting("scale",
            "Body scale/size. Vanilla 1.0.", 1.0, 0.5, 3.0, 0.1));

    private final Map<Holder<Attribute>, Double> original = new HashMap<>();
    private boolean captured = false;

    public AttributesModule() {
        super("Attributes", "Client-side attribute buffs: reach, attack speed, knockback resist, jump, scale.",
                ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() { captured = false; }

    @Override
    protected void onTick(Minecraft client) {
        Player p = client.player;
        if (p == null) return;
        if (!captured) { capture(p); captured = true; }

        apply(p, Attributes.ENTITY_INTERACTION_RANGE, reach.get());
        apply(p, Attributes.BLOCK_INTERACTION_RANGE, blockReach.get());
        apply(p, Attributes.ATTACK_SPEED, attackSpeed.get());
        apply(p, Attributes.KNOCKBACK_RESISTANCE, knockbackRes.get());
        apply(p, Attributes.JUMP_STRENGTH, jump.get());
        apply(p, Attributes.SCALE, scale.get());
    }

    @Override
    protected void onDisable() {
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            for (Map.Entry<Holder<Attribute>, Double> e : original.entrySet()) {
                AttributeInstance inst = p.getAttribute(e.getKey());
                if (inst != null) inst.setBaseValue(e.getValue());
            }
        }
        original.clear();
        captured = false;
    }

    private void capture(Player p) {
        original.clear();
        for (Holder<Attribute> h : new Holder[]{
                Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE,
                Attributes.ATTACK_SPEED, Attributes.KNOCKBACK_RESISTANCE,
                Attributes.JUMP_STRENGTH, Attributes.SCALE}) {
            AttributeInstance inst = p.getAttribute(h);
            if (inst != null) original.put(h, inst.getBaseValue());
        }
    }

    private void apply(Player p, Holder<Attribute> attr, double value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null && inst.getBaseValue() != value) inst.setBaseValue(value);
    }
}
