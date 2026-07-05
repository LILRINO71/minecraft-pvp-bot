package com.mcbot.command;

import com.mcbot.MCBotClient;
import com.mcbot.ai.BotBrainModule;
import com.mcbot.ai.Task;
import com.mcbot.macro.MacroLibrary;
import com.mcbot.module.Module;
import com.mcbot.module.ModuleManager;
import com.mcbot.module.world.AutoMineModule;
import com.mcbot.module.world.BuilderModule;
import com.mcbot.targeting.FriendList;
import com.mcbot.targeting.TargetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * BotCommandHandler — processes #bot chat commands before they're sent to the server.
 *
 * Commands:
 *   #bot help                        — list all commands
 *   #bot on / #bot off               — enable / disable all modules
 *   #bot <module>                    — toggle a module by name
 *   #bot mine <ore>                  — start mining specific ore
 *   #bot build <schematic>           — build a schematic
 *   #bot goto <x> <z>               — navigate to coordinates
 *   #bot explore                     — start world exploration
 *   #bot brain <goal>                — AI brain: natural language goal
 *   #bot stop                        — stop all Baritone processes
 *   #bot status                      — show enabled modules
 */
public class BotCommandHandler {

    private final ModuleManager moduleManager;

    public BotCommandHandler(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /**
     * Processes a chat message. Returns true if it was a bot command (suppress send).
     */
    public boolean handle(String message, Minecraft client) {
        if (!message.startsWith("#bot")) return false;

        String[] parts = message.trim().split("\\s+", 3);
        String sub = parts.length > 1 ? parts[1].toLowerCase() : "help";
        String arg = parts.length > 2 ? parts[2] : "";

        switch (sub) {
            case "help" -> showHelp(client);
            case "on"   -> { moduleManager.getAll().forEach(Module::enable);  say(client, "All modules ON"); }
            case "off"  -> { moduleManager.disableAll(); say(client, "All modules OFF"); }
            case "stop" -> {
                moduleManager.disableAll();
                baritone.api.BaritoneAPI.getProvider()
                        .getPrimaryBaritone().getPathingBehavior().cancelEverything();
                say(client, "Stopped everything.");
            }
            case "status" -> showStatus(client);
            case "mine"   -> handleMine(client, arg);
            case "gather" -> handleGather(client, arg);
            case "travel" -> handleTravel(client, arg);
            case "build"  -> handleBuild(client, arg);
            case "goto"   -> handleGoto(client, arg);
            case "explore"-> moduleManager.getModule("Exploration").enable();
            case "farm"   -> moduleManager.getModule("AutoFarm").toggle();
            case "killaura"  -> moduleManager.getModule("KillAura").toggle();
            case "crystal"   -> moduleManager.getModule("AutoCrystal").toggle();
            case "mace"      -> moduleManager.getModule("MaceCombat").toggle();
            case "pearl"     -> moduleManager.getModule("AutoPearl").toggle();
            case "grapple"   -> moduleManager.getModule("Grapple").toggle();
            case "silentaim", "silent" -> moduleManager.getModule("SilentAim").toggle();
            case "trigger", "triggerbot" -> moduleManager.getModule("TriggerBot").toggle();
            case "totem", "autototem"    -> moduleManager.getModule("AutoTotem").toggle();
            case "velocity", "antikb"    -> moduleManager.getModule("Velocity").toggle();
            case "elytra"    -> moduleManager.getModule("ElytraFlight").toggle();
            case "shield"    -> moduleManager.getModule("AutoShield").toggle();
            case "autoeat"   -> moduleManager.getModule("AutoEat").toggle();
            case "armor"     -> moduleManager.getModule("AutoArmor").toggle();
            case "bridge"    -> moduleManager.getModule("SpeedBridge").toggle();
            case "esp"       -> moduleManager.getModule("EntityESP").toggle();
            case "brain"     -> handleBrain(client, arg);
            // ── Targeting ─────────────────────────────────────────────────
            case "target"    -> handleTarget(client, arg);
            case "friend"    -> handleFriend(client, arg);
            case "foe"       -> handleFoe(client, arg);
            case "unfriend"  -> { FriendList.get().remove(arg); say(client, "Removed: " + arg); }
            // ── Settings ──────────────────────────────────────────────────
            case "set"       -> handleSet(client, arg);
            case "settings"  -> handleSettings(client, arg);
            // ── Macros (no AI needed) ─────────────────────────────────────
            case "macro"     -> handleMacro(client, arg);
            default          -> toggleByName(client, sub);
        }
        return true; // suppress the chat message
    }

    // ── Sub-commands ──────────────────────────────────────────────────────

    private void handleMine(Minecraft client, String ore) {
        if (ore.isBlank()) { say(client, "Usage: #bot mine <diamond|iron|gold|emerald|ancient|coal|obsidian>"); return; }
        AutoMineModule mine = (AutoMineModule) moduleManager.getModule("AutoMine");
        switch (ore.toLowerCase()) {
            case "iron"     -> mine.mineIron();
            case "gold"     -> mine.mineGold();
            case "diamond"  -> mine.mineDiamonds();
            case "emerald"  -> mine.mineEmeralds();
            case "ancient"  -> mine.mineAncient();
            case "coal"     -> mine.mineCoal();
            case "obsidian" -> mine.mineObsidian();
            default -> { say(client, "Unknown ore: " + ore); return; }
        }
        if (!mine.isEnabled()) mine.enable();
        say(client, "Mining: " + ore);
    }

    /** #bot gather <item> [count] — collect N of a resource via Baritone. */
    private void handleGather(Minecraft client, String arg) {
        String[] parts = arg.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            say(client, "Usage: #bot gather <item> [count]   e.g. §e#bot gather iron 64");
            return;
        }
        int count = 1;
        if (parts.length > 1) {
            try { count = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { say(client, "Bad count: §c" + parts[1]); return; }
        }
        runTask(client, new Task(Task.Type.GATHER, "gather " + count + " " + parts[0], parts[0], String.valueOf(count)));
        say(client, "§aGathering " + count + " " + parts[0] + " — Baritone is on it.");
    }

    /** #bot travel <x> <z>  (or <x> <y> <z>) — route there and stop on arrival. */
    private void handleTravel(Minecraft client, String arg) {
        String[] parts = arg.trim().split("\\s+");
        if (parts.length < 2) { say(client, "Usage: #bot travel <x> <z>   (or <x> <y> <z>)"); return; }
        runTask(client, new Task(Task.Type.TRAVEL, "travel to " + arg.trim(), parts));
        say(client, "§aTravelling to " + arg.trim() + ".");
    }

    /** Queues a single task on the BotBrain executor and starts it (no AI/API key needed). */
    private void runTask(Minecraft client, Task task) {
        BotBrainModule brain = (BotBrainModule) moduleManager.getModule("BotBrain");
        brain.getTaskQueue().add(task);
        if (!brain.isEnabled()) brain.enable();
    }

    private void handleBuild(Minecraft client, String schematic) {
        if (schematic.isBlank()) { say(client, "Usage: #bot build <schematic name>"); return; }
        BuilderModule builder = (BuilderModule) moduleManager.getModule("Builder");
        builder.startBuild(schematic);
        if (!builder.isEnabled()) builder.enable();
    }

    private void handleGoto(Minecraft client, String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) { say(client, "Usage: #bot goto <x> <z>"); return; }
        try {
            int x = Integer.parseInt(parts[0]);
            int z = Integer.parseInt(parts[1]);
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getCustomGoalProcess()
                    .setGoalAndPath(new baritone.api.pathing.goals.GoalXZ(x, z));
            say(client, "Navigating to " + x + ", " + z);
        } catch (NumberFormatException e) {
            say(client, "Invalid coordinates: " + args);
        }
    }

    private void handleBrain(Minecraft client, String goal) {
        if (goal.isBlank()) { say(client, "Usage: #bot brain <your goal>"); return; }
        BotBrainModule brain = (BotBrainModule) moduleManager.getModule("BotBrain");
        brain.submitGoal(goal, client);
    }

    private void handleTarget(Minecraft client, String arg) {
        TargetConfig tc = TargetConfig.get();
        // #bot target hostile/neutral/passive/players on/off  OR  #bot target status
        String[] parts = arg.trim().split("\\s+", 2);
        String type = parts.length > 0 ? parts[0].toLowerCase() : "";
        boolean turnOn = parts.length < 2 || parts[1].equalsIgnoreCase("on");

        switch (type) {
            case "hostile"  -> { tc.attackHostile = turnOn;  say(client, "Hostile targeting: " + onOff(turnOn)); }
            case "neutral"  -> { tc.attackNeutral = turnOn;  say(client, "Neutral targeting: " + onOff(turnOn)); }
            case "passive"  -> { tc.attackPassive = turnOn;  say(client, "Passive targeting: " + onOff(turnOn)); }
            case "players"  -> { tc.attackPlayers = turnOn;  say(client, "Player targeting:  " + onOff(turnOn)); }
            case "all"      -> {
                tc.attackHostile = tc.attackNeutral = tc.attackPassive = tc.attackPlayers = true;
                say(client, "Targeting: ALL");
            }
            case "none"     -> {
                tc.attackHostile = tc.attackNeutral = tc.attackPassive = tc.attackPlayers = false;
                say(client, "Targeting: NOTHING");
            }
            case "default"  -> {
                tc.attackHostile = true;
                tc.attackNeutral = tc.attackPassive = tc.attackPlayers = false;
                say(client, "Targeting reset to default (hostile only)");
            }
            default -> {
                say(client, "Targeting: §f" + tc.getSummary());
                say(client, "§7Usage: #bot target <hostile|neutral|passive|players|all|none|default> [on|off]");
            }
        }
    }

    private void handleFriend(Minecraft client, String name) {
        if (name.isBlank()) {
            String friends = String.join(", ", FriendList.get().getFriends());
            say(client, "§aFriends: " + (friends.isBlank() ? "none" : friends));
            return;
        }
        FriendList.get().addFriend(name);
        say(client, "§a" + name + " is now a friend — will never be attacked.");
    }

    private void handleFoe(Minecraft client, String name) {
        if (name.isBlank()) {
            String foes = String.join(", ", FriendList.get().getFoes());
            say(client, "§cFoes: " + (foes.isBlank() ? "none" : foes));
            return;
        }
        FriendList.get().addFoe(name);
        say(client, "§c" + name + " is now a foe — will always be attacked.");
    }

    private void handleMacro(Minecraft client, String arg) {
        if (arg.isBlank() || arg.equalsIgnoreCase("list")) {
            say(client, "§6=== Macros (no AI, 100% offline) ===");
            for (String line : MacroLibrary.listString().split("\n")) say(client, line);
            say(client, "§7Usage: #bot macro <name>");
            return;
        }
        BotBrainModule brain = (BotBrainModule) moduleManager.getModule("BotBrain");
        boolean found = MacroLibrary.run(arg, brain.getTaskQueue(), client);
        if (!found) {
            say(client, "Unknown macro: §c" + arg + "§f. Try #bot macro list");
        } else {
            if (!brain.isEnabled()) brain.enable();
        }
    }

    /** #bot set <module> <setting> <value> */
    private void handleSet(Minecraft client, String arg) {
        String[] parts = arg.trim().split("\\s+", 3);
        if (parts.length < 3) { say(client, "Usage: #bot set <module> <setting> <value>"); return; }
        Module m = findModule(parts[0]);
        if (m == null) { say(client, "Unknown module: §c" + parts[0]); return; }
        com.mcbot.settings.Setting<?> s = m.getSetting(parts[1]);
        if (s == null) {
            say(client, "Unknown setting §c" + parts[1] + "§f on " + m.getName() + ". Try #bot settings " + m.getName());
            return;
        }
        if (s.parse(parts[2])) {
            say(client, m.getName() + "." + s.getName() + " = §a" + s.display());
        } else {
            say(client, "Invalid value §c" + parts[2] + "§f for " + s.getName());
        }
    }

    /** #bot settings <module> — list a module's tunables. */
    private void handleSettings(Minecraft client, String arg) {
        if (arg.isBlank()) { say(client, "Usage: #bot settings <module>"); return; }
        Module m = findModule(arg.trim());
        if (m == null) { say(client, "Unknown module: §c" + arg); return; }
        var settings = m.getSettings();
        if (settings.isEmpty()) { say(client, m.getName() + " has no settings."); return; }
        say(client, "§6=== " + m.getName() + " settings ===");
        for (com.mcbot.settings.Setting<?> s : settings) {
            say(client, "§e" + s.getName() + " §f= " + s.display() + " §7— " + s.getDescription());
        }
        say(client, "§7Set with: #bot set " + m.getName() + " <setting> <value>");
    }

    private Module findModule(String name) {
        for (Module m : moduleManager.getAll()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private String onOff(boolean b) { return b ? "§aON" : "§cOFF"; }

    private void toggleByName(Minecraft client, String name) {
        // Case-insensitive match
        for (Module m : moduleManager.getAll()) {
            if (m.getName().equalsIgnoreCase(name)) {
                m.toggle();
                say(client, m.getName() + (m.isEnabled() ? " ON" : " OFF"));
                return;
            }
        }
        say(client, "Unknown module or command: " + name);
    }

    // ── Display helpers ───────────────────────────────────────────────────

    private void showHelp(Minecraft client) {
        say(client, "§6=== MC BOT Commands ===");
        say(client, "§6── Autonomy (no AI key needed) ──");
        say(client, "§e#bot gather <item> [count]     §7— collect N of a resource (e.g. gather iron 64)");
        say(client, "§e#bot travel <x> <z>            §7— route there and stop on arrival");
        say(client, "§e#bot mine <ore>                §7— mine an ore forever (no count)");
        say(client, "§e#bot goto <x> <z>              §7— navigate to coords");
        say(client, "§e#bot build <schematic>         §7— build from .nbt file");
        say(client, "§e#bot farm / explore            §7— toggle auto farm / exploration");
        say(client, "§e#bot killaura / crystal / mace §7— toggle combat modules");
        say(client, "§e#bot pearl / grapple           §7— pearl-catch / fishing-rod yank");
        say(client, "§e#bot silentaim                 §7— aim server-side, camera stays still");
        say(client, "§e#bot trigger                   §7— auto-attack what you look at");
        say(client, "§e#bot totem                     §7— keep a totem in your off-hand");
        say(client, "§e#bot velocity                  §7— reduce knockback taken");
        say(client, "§e#bot elytra / shield / bridge  §7— toggle other modules");
        say(client, "§e#bot esp                       §7— toggle entity hitbox ESP");
        say(client, "§6── Targeting (no AI needed) ──");
        say(client, "§e#bot target <hostile|neutral|passive|players> [on|off]");
        say(client, "§e#bot target all / none / default");
        say(client, "§e#bot friend [name]             §7— add friend / list friends");
        say(client, "§e#bot foe [name]                §7— add foe / list foes");
        say(client, "§e#bot unfriend <name>           §7— remove from friend/foe list");
        say(client, "§6── Macros (no AI, 100% free) ──");
        say(client, "§e#bot macro list                §7— show all macros");
        say(client, "§e#bot macro <name>              §7— run a macro");
        say(client, "§6── AI Brain (needs API key) ──");
        say(client, "§e#bot brain <goal>              §7— natural language goal");
        say(client, "§6── Tuning (everything is adjustable) ──");
        say(client, "§e#bot settings <module>         §7— list a module's tunable settings");
        say(client, "§e#bot set <module> <name> <val> §7— change a setting (or use the ClickGUI)");
        say(client, "§7  Right-click a module in the GUI to open its settings submenu.");
        say(client, "§6── Control ──");
        say(client, "§e#bot stop / status             §7— stop / show active modules");
        say(client, "§7Right-Shift=GUI | G=Friend/Foe toggle | END=panic");
    }

    private void showStatus(Minecraft client) {
        var enabled = moduleManager.getEnabled();
        if (enabled.isEmpty()) {
            say(client, "No modules active.");
        } else {
            say(client, "§aActive: " + enabled.stream().map(Module::getName)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    private void say(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[MC BOT] " + msg));
        }
    }
}
