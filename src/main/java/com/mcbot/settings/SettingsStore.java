package com.mcbot.settings;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SettingsStore — persists every module's settings to {@code mcbot_settings.txt} in the game
 * directory (one {@code module.setting=value} line each). Loaded once at startup; saved whenever
 * a setting changed (checked from the client tick loop, so saves are off the hot path).
 */
public final class SettingsStore {

    private SettingsStore() {}

    private static volatile boolean dirty = false;

    /** Flag that something changed; the tick loop calls {@link #saveIfDirty}. */
    public static void markDirty() { dirty = true; }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("mcbot_settings.txt");
    }

    /** Apply persisted values on top of defaults. Call once after ModuleManager is built. */
    public static void load(ModuleManager mm) {
        Path path = file();
        if (!Files.exists(path)) return;
        try (BufferedReader r = Files.newBufferedReader(path)) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                int dot = line.indexOf('.');
                if (eq < 0 || dot < 0 || dot > eq) continue;
                String moduleName = line.substring(0, dot);
                String settingName = line.substring(dot + 1, eq);
                String value = line.substring(eq + 1);
                Module m = mm.getModule(moduleName);
                if (m == null) continue;
                Setting<?> s = m.getSetting(settingName);
                if (s != null) s.parse(value);
            }
        } catch (IOException e) {
            System.err.println("[MC BOT] Failed to load settings: " + e.getMessage());
        }
        dirty = false; // loading isn't a user change
    }

    /** Saves if anything changed since the last save. Cheap to call every tick. */
    public static void saveIfDirty(ModuleManager mm) {
        if (!dirty) return;
        dirty = false;
        try (BufferedWriter w = Files.newBufferedWriter(file())) {
            for (Module m : mm.getAll()) {
                for (Setting<?> s : m.getSettings()) {
                    w.write(m.getName() + "." + s.getName() + "=" + s.serialize());
                    w.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("[MC BOT] Failed to save settings: " + e.getMessage());
        }
    }
}
