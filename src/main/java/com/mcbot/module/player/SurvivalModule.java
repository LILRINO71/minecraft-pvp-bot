package com.mcbot.module.player;

import com.mcbot.MCBotClient;
import com.mcbot.module.Module;
import com.mcbot.module.ModuleManager;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.targeting.TargetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Survival (Autopilot) — one toggle that keeps you alive: it composes the existing survival modules
 * so the bot eats when hungry, keeps a totem in your off-hand, water-clutches fall damage, and
 * fights off hostile mobs — all with the bot's timing. This is the first step of the "play the game
 * for me" brain: a supervisor that turns high-level intent ("stay alive") into the right modules.
 *
 * <p>It only disables what it itself turned on, so it never clobbers modules you enabled manually.
 */
public class SurvivalModule extends Module {

    private final BoolSetting autoEat   = addSetting(new BoolSetting("eat",   "Eat when hungry.", true));
    private final BoolSetting autoTotem = addSetting(new BoolSetting("totem", "Keep a totem in the off-hand.", true));
    private final BoolSetting clutch    = addSetting(new BoolSetting("clutch","Water-clutch fall damage.", true));
    private final BoolSetting fightMobs = addSetting(new BoolSetting("fightMobs", "Fight off nearby hostile mobs.", true));

    private final Set<String> enabledByUs = new HashSet<>();

    public SurvivalModule() {
        super("Survival", "Autopilot: eat, totem, fall-clutch and mob-defense in one toggle.",
                ModuleCategory.PLAYER);
    }

    @Override
    protected void onEnable() {
        ModuleManager mm = manager();
        if (mm == null) return;
        applyWants(mm);
        if (fightMobs.get()) TargetConfig.get().attackHostile = true;
        Minecraft c = Minecraft.getInstance();
        if (c.player != null) c.player.sendSystemMessage(Component.literal(
                "§a[MC BOT] Survival autopilot ON — eating, totem, fall-clutch and mob-defense active."));
    }

    @Override
    protected void onTick(Minecraft client) {
        ModuleManager mm = manager();
        if (mm != null) applyWants(mm);   // keep the survival suite asserted
    }

    @Override
    protected void onDisable() {
        ModuleManager mm = manager();
        if (mm != null) {
            for (String name : enabledByUs) {
                Module m = mm.getModule(name);
                if (m != null) m.disable();
            }
        }
        enabledByUs.clear();
    }

    private void applyWants(ModuleManager mm) {
        want(mm, "AutoEat",    autoEat.get());
        want(mm, "AutoTotem",  autoTotem.get());
        want(mm, "AutoClutch", clutch.get());
        want(mm, "KillAura",   fightMobs.get());
    }

    /** Enables a sub-module if wanted and not already on, remembering that we did. */
    private void want(ModuleManager mm, String name, boolean on) {
        if (!on) return;
        Module m = mm.getModule(name);
        if (m != null && !m.isEnabled()) {
            m.enable();
            enabledByUs.add(name);
        }
    }

    private ModuleManager manager() {
        MCBotClient bot = MCBotClient.get();
        return bot != null ? bot.getModuleManager() : null;
    }
}
