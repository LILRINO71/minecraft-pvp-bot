package com.mcbot.macro;

import com.mcbot.ai.Task;

import java.util.List;

/**
 * BotMacro — a named, hardcoded sequence of Tasks.
 * No Claude API required — runs entirely offline.
 */
public class BotMacro {

    public final String name;
    public final String description;
    public final List<Task> tasks;

    public BotMacro(String name, String description, List<Task> tasks) {
        this.name        = name;
        this.description = description;
        this.tasks       = List.copyOf(tasks);
    }

    @Override
    public String toString() { return name + " (" + tasks.size() + " tasks)"; }
}
