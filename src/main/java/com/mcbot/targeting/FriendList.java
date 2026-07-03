package com.mcbot.targeting;

import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * FriendList — persistent friend and foe registry.
 *
 * Stored in .minecraft/mcbot_friends.txt and mcbot_foes.txt
 * Survives game restarts.
 *
 * Friends: never attacked, shown in green on ESP.
 * Foes: always attacked (if alwaysAttackFoes = true), shown in red on ESP.
 */
public class FriendList {

    private static final FriendList INSTANCE = new FriendList();
    public static FriendList get() { return INSTANCE; }

    private final Set<String> friends = new HashSet<>();
    private final Set<String> foes    = new HashSet<>();
    private boolean loaded = false;

    // ── Public API ────────────────────────────────────────────────────────

    public void addFriend(String name) { ensureLoaded(); friends.add(name.toLowerCase()); foes.remove(name.toLowerCase()); save(); }
    public void addFoe(String name)    { ensureLoaded(); foes.add(name.toLowerCase()); friends.remove(name.toLowerCase()); save(); }
    public void remove(String name)    { ensureLoaded(); friends.remove(name.toLowerCase()); foes.remove(name.toLowerCase()); save(); }

    public boolean isFriend(String name) { ensureLoaded(); return friends.contains(name.toLowerCase()); }
    public boolean isFoe(String name)    { ensureLoaded(); return foes.contains(name.toLowerCase()); }
    public boolean isKnown(String name)  { return isFriend(name) || isFoe(name); }

    public Set<String> getFriends() { ensureLoaded(); return Collections.unmodifiableSet(friends); }
    public Set<String> getFoes()    { ensureLoaded(); return Collections.unmodifiableSet(foes); }

    /** Cycle: unknown → friend → foe → unknown */
    public String toggle(String name) {
        ensureLoaded();
        if (isFriend(name)) {
            addFoe(name);
            return "foe";
        } else if (isFoe(name)) {
            remove(name);
            return "neutral";
        } else {
            addFriend(name);
            return "friend";
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private void ensureLoaded() {
        if (!loaded) { load(); loaded = true; }
    }

    private Path getFile(String name) {
        Minecraft client = Minecraft.getInstance();
        Path base = client != null
                ? client.gameDirectory.toPath()
                : Path.of(System.getProperty("user.home"));
        return base.resolve("mcbot_" + name + ".txt");
    }

    private void load() {
        loadSet(friends, "friends");
        loadSet(foes,    "foes");
    }

    private void loadSet(Set<String> set, String fileName) {
        Path path = getFile(fileName);
        if (!Files.exists(path)) return;
        try (BufferedReader r = Files.newBufferedReader(path)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) set.add(line);
            }
        } catch (IOException e) {
            System.err.println("[MC BOT] Failed to load " + fileName + ": " + e.getMessage());
        }
    }

    private void save() {
        saveSet(friends, "friends");
        saveSet(foes,    "foes");
    }

    private void saveSet(Set<String> set, String fileName) {
        try (BufferedWriter w = Files.newBufferedWriter(getFile(fileName))) {
            for (String entry : set) {
                w.write(entry);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("[MC BOT] Failed to save " + fileName + ": " + e.getMessage());
        }
    }
}
