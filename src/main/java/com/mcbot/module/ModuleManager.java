package com.mcbot.module;

import com.mcbot.module.combat.*;
import com.mcbot.module.movement.AutoClutchModule;
import com.mcbot.module.movement.AutoSprintModule;
import com.mcbot.module.movement.ElytraFlightModule;
import com.mcbot.module.movement.NoFallModule;
import com.mcbot.module.movement.SafeStepModule;
import com.mcbot.module.movement.StepModule;
import com.mcbot.module.movement.VelocityModule;
import com.mcbot.module.player.AutoArmorModule;
import com.mcbot.module.player.AutoEatModule;
import com.mcbot.module.player.AutoTotemModule;
import com.mcbot.module.player.SpeedBridgeModule;
import com.mcbot.module.player.SurvivalModule;
import com.mcbot.module.render.EntityESPModule;
import com.mcbot.module.render.FullbrightModule;
import com.mcbot.module.render.TracersModule;
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
            new SilentAimModule(),
            new TriggerBotModule(),
            new SurroundModule(),

            // Level
            new AutoMineModule(),
            new AutoFarmModule(),
            new BuilderModule(),
            new ExplorationModule(),

            // Player
            new AutoEatModule(),
            new AutoArmorModule(),
            new SpeedBridgeModule(),
            new AutoTotemModule(),
            new SurvivalModule(),

            // Movement
            new ElytraFlightModule(),
            new VelocityModule(),
            new SafeStepModule(),
            new AutoSprintModule(),
            new StepModule(),
            new NoFallModule(),
            new AutoClutchModule(),

            // Render
            new EntityESPModule(),
            new TracersModule(),
            new FullbrightModule(),

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
