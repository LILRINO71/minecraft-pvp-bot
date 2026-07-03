package com.mcbot.module.world;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Builder - schematic building via Baritone. Stubbed during 26.1 port (Baritone schematic API TBD). */
public class BuilderModule extends Module {
    private boolean building = false;
    private String currentSchematic = null;
    public BuilderModule() {
        super("Builder", "Builds .nbt/.schem schematics using Baritone (WIP on 26.1).", ModuleCategory.WORLD);
    }
    @Override protected void onTick(Minecraft client) {}
    public boolean startBuild(String schematicName) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                "[MC BOT] Schematic building isn't wired up on 26.1 yet: " + schematicName));
        }
        return false;
    }
    public boolean isBuilding() { return building; }
    public String getCurrentSchematic() { return currentSchematic; }
}
