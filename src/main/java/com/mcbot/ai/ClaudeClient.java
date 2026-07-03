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
                Each task has: {"type":"MINE|NAVIGATE|FARM|BUILD|FIGHT|EXPLORE|WAIT|CHAT","description":"...","args":["..."]}

                Types:
                - MINE: args[0] = ore name (diamond, iron, gold, emerald, ancient, coal, obsidian)
                - NAVIGATE: args[0] = x, args[1] = z
                - FARM: no args needed
                - BUILD: args[0] = schematic filename (without extension)
                - FIGHT: no args
                - EXPLORE: no args
                - WAIT: args[0] = ticks
                - CHAT: args[0] = message to send

                Keep task lists SHORT (3-6 tasks max). Prioritise the most direct path to the goal.
                Output ONLY the JSON array. No explanation, no markdown.

                Example for "get me diamonds and smelt them":
                [
                  {"type":"MINE","description":"Mine diamonds","args":["diamond"]},
                  {"type":"NAVIGATE","description":"Return to base","args":["0","64"]},
                  {"type":"CHAT","description":"Done","args":["[MC BOT] Got diamonds!"]}
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
