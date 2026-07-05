package com.mcbot.ai;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * ClaudeClient — sends the player's goal to the Claude API and returns
 * a list of tasks as JSON for the TaskQueue to execute.
 *
 * ─── SETUP ───────────────────────────────────────────────────────────────
 * 1. Get your API key from https://console.anthropic.com/
 * 2. Paste it into API_KEY below (or set MC_BOT_CLAUDE_KEY env variable)
 * ─────────────────────────────────────────────────────────────────────────
 */
public class ClaudeClient {

    // ← PASTE YOUR API KEY HERE (or leave blank and set env var MC_BOT_CLAUDE_KEY)
    private static final String API_KEY = "";

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-haiku-4-5-20251001"; // cheapest, fast
    private static final int    MAX_TOKENS = 512;

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Sends the player's natural-language goal to Claude.
     * Returns a CompletableFuture<String> with the raw JSON task list.
     * Call this off the main thread.
     */
    public static CompletableFuture<String> planTasks(String playerGoal, String inventorySummary) {
        return CompletableFuture.supplyAsync(() -> {
            String key = resolveKey();
            if (key.isBlank()) {
                return errorJson("No API key set. Paste your key into ClaudeClient.java or set MC_BOT_CLAUDE_KEY env var.");
            }

            String systemPrompt = buildSystemPrompt();
            String userMessage  = "Player goal: " + playerGoal + "\n\nInventory: " + inventorySummary;

            try {
                String requestBody = buildRequestBody(systemPrompt, userMessage);
                String response = sendRequest(key, requestBody);
                return extractContent(response);
            } catch (Exception e) {
                return errorJson("API error: " + e.getMessage());
            }
        });
    }

    // ── Request building ──────────────────────────────────────────────────

    private static String buildSystemPrompt() {
        return """
                You are the AI brain of a Minecraft bot. Given a player's goal, output ONLY a JSON array of tasks.
                Each task has: {"type":"GATHER|TRAVEL|MINE|NAVIGATE|FARM|BUILD|FIGHT|EXPLORE|WAIT|CHAT","description":"...","args":["..."]}

                Types (PREFER GATHER and TRAVEL — they know when they're done):
                - GATHER: collect N of a resource. args[0] = resource, args[1] = count.
                  resources: diamond, iron, gold, copper, coal, redstone, lapis, emerald, ancient, obsidian, stone, dirt, sand, wood
                - TRAVEL: route to coordinates and stop on arrival. args = ["x","z"] or ["x","y","z"]
                - CRAFT: craft an item at an open crafting table. args[0] = item, args[1] = count.
                  items: planks, stick, crafting_table, chest, furnace, iron/diamond + _pickaxe/_sword/_axe/_helmet/_chestplate/_leggings/_boots
                - MINE: keep mining an ore forever (no count). args[0] = ore name
                - NAVIGATE: like TRAVEL. args[0]=x, args[1]=z
                - FARM / FIGHT / EXPLORE: no args
                - BUILD: args[0] = schematic filename (without extension)
                - WAIT: args[0] = ticks
                - CHAT: args[0] = message to send

                Keep task lists SHORT (3-6 tasks max). Prioritise the most direct path to the goal.
                Output ONLY the JSON array. No explanation, no markdown.

                Example for "get me 3 diamonds then come home":
                [
                  {"type":"GATHER","description":"Mine 3 diamonds","args":["diamond","3"]},
                  {"type":"TRAVEL","description":"Return to base","args":["0","64"]},
                  {"type":"CHAT","description":"Done","args":["[MC BOT] Got the diamonds!"]}
                ]
                """;
    }

    private static String buildRequestBody(String system, String user) {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", MAX_TOKENS);
        body.addProperty("system", system);

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", user);
        messages.add(userMsg);
        body.add("messages", messages);

        return GSON.toJson(body);
    }

    private static String sendRequest(String apiKey, String body) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (status >= 400) throw new IOException("HTTP " + status + ": " + response);
        return response;
    }

    private static String extractContent(String response) {
        JsonObject obj = GSON.fromJson(response, JsonObject.class);
        JsonArray content = obj.getAsJsonArray("content");
        if (content != null && !content.isEmpty()) {
            return content.get(0).getAsJsonObject().get("text").getAsString();
        }
        return errorJson("Empty response from Claude");
    }

    private static String resolveKey() {
        if (!API_KEY.isBlank()) return API_KEY;
        String env = System.getenv("MC_BOT_CLAUDE_KEY");
        return env != null ? env : "";
    }

    private static String errorJson(String msg) {
        return "[{\"type\":\"CHAT\",\"description\":\"Error\",\"args\":[\"[MC BOT Brain Error] " + msg + "\"]}]";
    }
}
