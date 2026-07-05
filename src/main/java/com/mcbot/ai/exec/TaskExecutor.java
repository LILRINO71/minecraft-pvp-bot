package com.mcbot.ai.exec;

import net.minecraft.client.Minecraft;

/**
 * TaskExecutor — a self-contained, verifiable sub-goal the bot can run (gather N iron, travel to
 * coords, ...). Executors are the building blocks of autonomy: the TaskQueue drives one at a time,
 * and the LLM planner (BotBrain) composes them into larger goals.
 *
 * <p>Lifecycle: {@link #start} once, then {@link #tick} every game tick until it returns DONE or
 * FAILED; {@link #stop} is called on completion/cancel to clean up (e.g. cancel Baritone).
 */
public interface TaskExecutor {

    enum Status { RUNNING, DONE, FAILED }

    void start(Minecraft client);

    Status tick(Minecraft client);

    default void stop(Minecraft client) {}

    /** Short human-readable progress, shown occasionally in chat. */
    default String progress() { return ""; }

    /** Reason for the most recent FAILED status. */
    default String failReason() { return ""; }
}
