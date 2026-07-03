package com.mcbot;

import com.mcbot.command.BotCommandHandler;
import com.mcbot.gui.BotScreen;
import com.mcbot.gui.HudOverlay;
import com.mcbot.module.ModuleManager;
import com.mcbot.targeting.FriendList;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCBotClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("mcbot");
    public static MCBotClient INSTANCE;

    public static KeyMapping.Category CATEGORY;
    public static KeyMapping OPEN_GUI_KEY;
    public static KeyMapping TOGGLE_KILLAURA_KEY;
    public static KeyMapping TOGGLE_AUTOCRYSTAL_KEY;
    public static KeyMapping TOGGLE_ELYTRA_KEY;
    public static KeyMapping TOGGLE_MACE_KEY;
    public static KeyMapping TOGGLE_PEARL_KEY;
    public static KeyMapping TOGGLE_GRAPPLE_KEY;
    public static KeyMapping PANIC_KEY;
    public static KeyMapping FRIEND_FOE_KEY;

    private ModuleManager moduleManager;
    private BotCommandHandler commandHandler;
    private HudOverlay hudOverlay;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("[MC BOT] Initialising");

        CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mcbot", "main"));

        OPEN_GUI_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        TOGGLE_KILLAURA_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.killaura", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        TOGGLE_AUTOCRYSTAL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.autocrystal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        TOGGLE_ELYTRA_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.elytra", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));
        TOGGLE_MACE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.mace", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        TOGGLE_PEARL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.pearl", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY));
        TOGGLE_GRAPPLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.grapple", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        PANIC_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.panic", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, CATEGORY));
        FRIEND_FOE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mcbot.friendfoe", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

        moduleManager = new ModuleManager();
        commandHandler = new BotCommandHandler(moduleManager);
        hudOverlay = new HudOverlay(moduleManager);

        // Intercept #bot chat commands locally (handled, not sent to the server)
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("#bot")) {
                boolean handled = commandHandler.handle(message, Minecraft.getInstance());
                if (handled) return false;
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            while (OPEN_GUI_KEY.consumeClick()) {
                client.setScreen(new BotScreen(moduleManager));
            }
            while (TOGGLE_KILLAURA_KEY.consumeClick()) {
                moduleManager.getModule("KillAura").toggle();
            }
            while (TOGGLE_AUTOCRYSTAL_KEY.consumeClick()) {
                moduleManager.getModule("AutoCrystal").toggle();
            }
            while (TOGGLE_ELYTRA_KEY.consumeClick()) {
                moduleManager.getModule("ElytraFlight").toggle();
            }
            while (TOGGLE_MACE_KEY.consumeClick()) {
                moduleManager.getModule("MaceCombat").toggle();
            }
            while (TOGGLE_PEARL_KEY.consumeClick()) {
                moduleManager.getModule("AutoPearl").toggle();
            }
            while (TOGGLE_GRAPPLE_KEY.consumeClick()) {
                moduleManager.getModule("Grapple").toggle();
            }
            while (PANIC_KEY.consumeClick()) {
                moduleManager.disableAll();
                try {
                    baritone.api.BaritoneAPI.getProvider()
                            .getPrimaryBaritone().getPathingBehavior().cancelEverything();
                } catch (Throwable ignored) {}
                client.player.sendSystemMessage(
                        Component.literal("§c[MC BOT] PANIC - everything stopped."));
                LOGGER.info("[MC BOT] PANIC - all modules disabled");
            }
            while (FRIEND_FOE_KEY.consumeClick()) {
                onFriendFoeKey(client);
            }

            moduleManager.tick(client);
        });

        // NOTE: HUD overlay + entity ESP world rendering are disabled pending the
        // 26.1 HudElementRegistry / world-render API wiring (non-essential to the bot).

        LOGGER.info("[MC BOT] Ready. RShift=GUI | K=KillAura C=Crystal M=Mace P=Pearl H=Grapple V=Elytra | G=FriendFoe | END=panic");
    }

    private void onFriendFoeKey(Minecraft client) {
        if (client.crosshairPickEntity == null) {
            client.player.sendSystemMessage(
                    Component.literal("§7[MC BOT] Look at an entity first."));
            return;
        }
        Entity target = client.crosshairPickEntity;
        String key;
        String displayName;

        if (target instanceof Player player) {
            key = player.getGameProfile().name();
            displayName = key;
        } else if (target instanceof LivingEntity) {
            key = target.getStringUUID();
            displayName = target.getName().getString();
        } else {
            return;
        }

        String status = FriendList.get().toggle(key);
        String color = switch (status) {
            case "friend" -> "§a";
            case "foe"    -> "§c";
            default       -> "§7";
        };
        client.player.sendSystemMessage(
                Component.literal("[MC BOT] " + color + displayName + " §fis now " + color + status));
    }

    public ModuleManager getModuleManager() { return moduleManager; }
    public BotCommandHandler getCommandHandler() { return commandHandler; }
    public static MCBotClient get() { return INSTANCE; }
}
