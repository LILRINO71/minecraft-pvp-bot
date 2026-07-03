package com.mcbot.module.render;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import net.minecraft.client.Minecraft;

/** Entity ESP - temporarily disabled pending the 26.1 world-render API wiring. */
public class EntityESPModule extends Module {
    private static EntityESPModule INSTANCE;
    public static EntityESPModule get() { return INSTANCE; }
    public EntityESPModule() {
        super("EntityESP", "Colored entity hitboxes (disabled in 26.1 port).", ModuleCategory.RENDER);
        INSTANCE = this;
    }
    @Override protected void onTick(Minecraft client) { /* render wiring TBD */ }
}
