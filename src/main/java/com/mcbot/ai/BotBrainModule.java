package com.mcbot.ai;

import com.google.gson.*;
import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * BotBrain — the AI module that interprets natural-language goals and
 * generates a sequence of tasks for the bot to execute.
 *
 * Usage in-game: .bot brain <your goal>
 * Example: .bot brain get me ready for the End fight
 */
public class BotBrainModule extends Module {

    private final TaskQueue taskQueue = new TaskQueue();
    private CompletableFuture<String> pendingPlan = null;
    private static final Gson GSON = new GsonBuilder().create();

    public BotBrainModule() {
        super("BotBrain", "AI brain — understands natural-language goals and executes them.", ModuleCategory.AI);
    }

    @Override
    protected void onDisable() {
        taskQueue.clear();
        pendingPlan = null;
    }

    @Override
    protected void onTick(Minecraft client) {
        // Check if a plan just arrived from the API
        if (pendingPlan != null && pendingPlan.isDone()) {
            try {
                String json = pendingPlan.get();
                parsePlanIntoQueue(json, client);
            } catch (Exception e) {
                if (client.player != null)
                    client.player.sendSystemMessage(Component.literal("[MC BOT Brain] Error: " + e.getMessage()));
            }
            pendingPlan = null;
        }

        // Tick the task queue
        taskQueue.tick(client);

        // Auto-disable when queue is empty
        if (pendingPlan == null && taskQueue.isEmpty()) {
            if (isEnabled()) disable();
        }
    }

    /**
     * Submits a new natural-language goal to the AI.
     * Called by BotCommandHandler when user types ".bot brain <goal>"
     */
    public void submitGoal(String goal, Minecraft client) {
        taskQueue.clear();
        if (pendingPlan != null && !pendingPlan.isDone()) {
            pendingPlan.cancel(true);
        }

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[MC BOT Brain] Thinking about: " + goal + "..."));
        }

        String inventory = buildInventorySummary(client);
        pendingPlan = ClaudeClient.planTasks(goal, inventory);
        enable();
    }

    private void parsePlanIntoQueue(String json, Minecraft client) {
        try {
            JsonArray tasks = GSON.fromJson(json, JsonArray.class);
            for (JsonElement el : tasks) {
                JsonObject obj = el.getAsJsonObject();
                String typeStr = obj.get("type").getAsString();
                String desc    = obj.has("description") ? obj.get("description").getAsString() : typeStr;

                String[] args = new String[0];
                if (obj.has("args")) {
                    JsonArray argsArr = obj.getAsJsonArray("args");
                    args = new String[argsArr.size()];
                    for (int i = 0; i < argsArr.size(); i++) {
                        args[i] = argsArr.get(i).getAsString();
                    }
                }

                Task.Type type;
                try { type = Task.Type.valueOf(typeStr); }
                catch (IllegalArgumentException e) { continue; }

                taskQueue.add(new Task(type, desc, args));
            }

            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(
                        "[MC BOT Brain] Plan ready — " + taskQueue.size() + " tasks."));
            }
        } catch (JsonSyntaxException e) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("[MC BOT Brain] Couldn't parse plan: " + json));
            }
        }
    }

    private String buildInventorySummary(Minecraft client) {
        if (client.player == null) return "unknown";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                sb.append(stack.getCount()).append("x ")
                  .append(stack.getItem().toString()).append(", ");
            }
        }
        return sb.length() > 0 ? sb.substring(0, Math.min(sb.length(), 500)) : "empty";
    }

    public TaskQueue getTaskQueue() { return taskQueue; }
}
