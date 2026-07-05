package com.mcbot.ai.exec;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * TravelExecutor — routes to a destination with Baritone and finishes when you arrive.
 *
 * <p>Accepts 2 args (x z → travel to that column) or 3 args (x y z → travel to that exact block).
 * Completion is distance-based (arrived within ~1.5 blocks). If Baritone stops pathing well short of
 * the goal for a while, it reports FAILED (no route).
 */
public class TravelExecutor implements TaskExecutor {

    private final double x, y, z;
    private final boolean hasY;
    private String fail = "";
    private int stuckTicks = 0;

    private TravelExecutor(double x, double y, double z, boolean hasY) {
        this.x = x; this.y = y; this.z = z; this.hasY = hasY;
    }

    /** Parses "x z" or "x y z" into a TravelExecutor, or null if the args aren't numbers. */
    public static TravelExecutor fromArgs(String[] args) {
        try {
            if (args.length == 2) {
                return new TravelExecutor(Integer.parseInt(args[0].trim()), 0, Integer.parseInt(args[1].trim()), false);
            } else if (args.length >= 3) {
                return new TravelExecutor(Integer.parseInt(args[0].trim()),
                        Integer.parseInt(args[1].trim()), Integer.parseInt(args[2].trim()), true);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    @Override
    public void start(Minecraft client) {
        var goal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
        if (hasY) goal.setGoalAndPath(new GoalBlock((int) x, (int) y, (int) z));
        else      goal.setGoalAndPath(new GoalXZ((int) x, (int) z));
    }

    @Override
    public Status tick(Minecraft client) {
        if (client.player == null) return Status.RUNNING;
        Vec3 p = client.player.position();

        double dist = hasY
                ? Math.sqrt(sq(p.x - (x + 0.5)) + sq(p.y - y) + sq(p.z - (z + 0.5)))
                : Math.hypot(p.x - (x + 0.5), p.z - (z + 0.5));
        if (dist <= 1.5) return Status.DONE;

        boolean pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        if (!pathing) {
            // Not pathing and not there yet — either finished calc or gave up. Allow a grace window.
            if (++stuckTicks > 200) { fail = "couldn't find a path to the destination"; return Status.FAILED; }
        } else {
            stuckTicks = 0;
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(Minecraft client) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    @Override
    public String progress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "travelling";
        Vec3 p = mc.player.position();
        double d = Math.hypot(p.x - (x + 0.5), p.z - (z + 0.5));
        return String.format("%.0f blocks to go", d);
    }

    @Override
    public String failReason() { return fail; }

    private static double sq(double v) { return v * v; }
}
