package com.mcbot.macro;

import com.mcbot.ai.Task;
import com.mcbot.ai.TaskQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * MacroLibrary — predefined hardcoded bot sequences.
 * Zero API calls, zero cost, runs 100% offline.
 *
 * Usage in-game:
 *   .bot macro list                — show all macros
 *   .bot macro <name>             — run a macro
 *
 * Available macros:
 *   diamonds        — mine diamonds until inventory is getting full
 *   iron            — mass iron mining (fuel for furnaces etc.)
 *   end-prep        — mine obsidian, explore for fortress, stock ender eyes
 *   farm-run        — harvest all crops in area
 *   explore         — explore new chunks in a spiral
 *   gear-up         — mine iron + diamonds + craft (semi-hardcoded)
 *   server-test     — combat patrol: find and fight all hostile mobs nearby
 */
public class MacroLibrary {

    private static final Map<String, BotMacro> MACROS = new LinkedHashMap<>();

    static {
        register(new BotMacro("diamonds",
                "Mine diamonds until full, then return to 0,0",
                List.of(
                        new Task(Task.Type.MINE,     "Mine diamonds",            "diamond"),
                        new Task(Task.Type.NAVIGATE, "Return to spawn",          "0", "0"),
                        new Task(Task.Type.CHAT,     "Announce done",
                                "[MC BOT] Diamond run complete!")
                )));

        register(new BotMacro("iron",
                "Mass iron mining run",
                List.of(
                        new Task(Task.Type.MINE,     "Mine iron ore",            "iron"),
                        new Task(Task.Type.NAVIGATE, "Return to spawn",          "0", "0"),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Iron run complete!")
                )));

        register(new BotMacro("ancient",
                "Mine ancient debris in the Nether (make sure you're in the Nether first)",
                List.of(
                        new Task(Task.Type.MINE,     "Mine ancient debris",      "ancient"),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Ancient debris run complete!")
                )));

        register(new BotMacro("end-prep",
                "Mine obsidian for End portal frame, then explore",
                List.of(
                        new Task(Task.Type.MINE,     "Mine obsidian (×10)",      "obsidian"),
                        new Task(Task.Type.MINE,     "Mine diamonds for gear",   "diamond"),
                        new Task(Task.Type.EXPLORE,  "Scout for stronghold",     ""),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] End prep complete — check inventory!")
                )));

        register(new BotMacro("farm-run",
                "Harvest all mature crops in a 10-block radius",
                List.of(
                        new Task(Task.Type.FARM,     "Harvest nearby crops",     ""),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Farm run complete!")
                )));

        register(new BotMacro("explore",
                "Spiral exploration run — maps out new chunks",
                List.of(
                        new Task(Task.Type.EXPLORE,  "Explore new chunks",       ""),
                        new Task(Task.Type.NAVIGATE, "Return to spawn",          "0", "0"),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Exploration complete!")
                )));

        register(new BotMacro("gear-up",
                "Full gear-up sequence: iron → diamonds → return",
                List.of(
                        new Task(Task.Type.MINE,     "Mine iron",                "iron"),
                        new Task(Task.Type.NAVIGATE, "Surface at origin",        "0", "0"),
                        new Task(Task.Type.MINE,     "Mine diamonds",            "diamond"),
                        new Task(Task.Type.NAVIGATE, "Return to base",           "0", "0"),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Gear-up complete! Check inventory for iron + diamonds.")
                )));

        register(new BotMacro("server-test",
                "Combat patrol: fight all hostiles in 16-block radius, then return",
                List.of(
                        new Task(Task.Type.FIGHT,    "Fight nearby hostiles",    ""),
                        new Task(Task.Type.NAVIGATE, "Return to origin",         "0", "0"),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Combat patrol complete!")
                )));

        register(new BotMacro("coal",
                "Mine coal for fuel",
                List.of(
                        new Task(Task.Type.MINE,     "Mine coal",                "coal"),
                        new Task(Task.Type.CHAT,     "Done",
                                "[MC BOT] Coal run complete!")
                )));
    }

    private static void register(BotMacro macro) {
        MACROS.put(macro.name.toLowerCase(), macro);
    }

    // ── Public API ────────────────────────────────────────────────────────

    public static Optional<BotMacro> get(String name) {
        return Optional.ofNullable(MACROS.get(name.toLowerCase()));
    }

    public static Collection<BotMacro> all() {
        return Collections.unmodifiableCollection(MACROS.values());
    }

    /**
     * Loads a macro into the TaskQueue and announces it.
     * @return true if macro found, false if not
     */
    public static boolean run(String name, TaskQueue queue, Minecraft client) {
        Optional<BotMacro> opt = get(name);
        if (opt.isEmpty()) return false;

        BotMacro macro = opt.get();
        queue.clear();

        // Clone tasks into the queue (so the macro can be re-run)
        for (Task t : macro.tasks) {
            queue.add(new Task(t.type, t.description, t.args));
        }

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "§6[MC BOT] §fRunning macro §e'" + macro.name + "'§f: " + macro.description));
        }
        return true;
    }

    public static String listString() {
        StringBuilder sb = new StringBuilder();
        for (BotMacro m : MACROS.values()) {
            sb.append("§e").append(m.name).append("§7 — ").append(m.description).append("\n");
        }
        return sb.toString().trim();
    }
}
