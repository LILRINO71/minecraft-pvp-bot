package com.mcbot.module;

import com.mcbot.module.combat.*;
import com.mcbot.module.movement.ElytraFlightModule;
import com.mcbot.module.player.AutoArmorModule;
import com.mcbot.module.player.AutoEatModule;
import com.mcbot.module.player.SpeedBridgeModule;
import com.mcbot.module.render.EntityESPModule;
import com.mcbot.module.world.*;
import com.mcbot.ai.BotBrainModule;
import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {

    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        register(
            // Combat
            new KillAuraModule(),
            new AutoCrystalModule(),
            new MaceCombatModule(),
            new ElytraStrikeModule(),
            new AutoShieldModule(),
            new AutoPearlModule(),
            new GrappleModule(),

            // Level
            new AutoMineModule(),
            new AutoFarmModule(),
            new BuilderModule(),
            new ExplorationModule(),

            // Player
            new AutoEatModule(),
            new AutoArmorModule(),
            new SpeedBridgeModule(),

            // Movement
            new ElytraFlightModule(),

            // Render
            new EntityESPModule(),

            // AI
            new BotBrainModule()
        );
    }

    private void register(Module... mods) {
        for (Module m : mods) modules.put(m.getName(), m);
    }

    public void tick(Minecraft client) {
        for (Module m : modules.values()) m.tick(client);
    }

    public void disableAll() {
        for (Module m : modules.values()) m.disable();
    }

    public Module getModule(String name) {
        return modules.get(name);
    }

    public Collection<Module> getAll() {
        return modules.values();
    }

    public List<Module> getByCategory(ModuleCategory cat) {
        return modules.values().stream()
                .filter(m -> m.getCategory() == cat)
                .collect(Collectors.toList());
    }

    public List<Module> getEnabled() {
        return modules.values().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }
}
