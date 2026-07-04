package com.mcbot.module.render;

import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.settings.BoolSetting;
import com.mcbot.settings.DoubleSetting;
import com.mcbot.targeting.FriendList;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tracers — draws a line from your view to every living entity, colored by friend/foe. Great for
 * tracking players through walls in a fight. Meteor-equivalent. Shares the same 26.1 line-render
 * path as EntityESP (RenderTypes.lines() + a per-vertex line width).
 */
public class TracersModule extends Module {

    private static TracersModule INSTANCE;
    private static volatile boolean ACTIVE = false;
    private static volatile float width = 1.5f;
    private static volatile boolean playersOnly = false;

    private final DoubleSetting lineWidth = addSetting(new DoubleSetting(
            "width", "Tracer line thickness.", 1.5, 0.5, 5.0, 0.5));
    private final BoolSetting onlyPlayers = addSetting(new BoolSetting(
            "playersOnly", "Only draw tracers to players.", true));

    public TracersModule() {
        super("Tracers", "Lines from your view to entities (friend/foe colored).", ModuleCategory.RENDER);
        INSTANCE = this;
    }

    @Override protected void onEnable()  { ACTIVE = true; }
    @Override protected void onDisable() { ACTIVE = false; }

    @Override
    protected void onTick(Minecraft client) {
        width = (float) (double) lineWidth.get();
        playersOnly = onlyPlayers.get();
    }

    /** Called every frame from the Fabric LevelRenderEvents callback (wired in MCBotClient). */
    public static void render(LevelRenderContext ctx) {
        if (!ACTIVE || ctx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;
        Vec3 cam = camera.position();

        PoseStack poseStack = ctx.poseStack();
        MultiBufferSource.BufferSource buffers = ctx.bufferSource();
        if (poseStack == null || buffers == null) return;
        VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        // Start just below the eye so all tracers fan out from your view.
        float sx = (float) cam.x, sy = (float) (cam.y - 0.15), sz = (float) cam.z;

        ClientLevel level = mc.level;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e == mc.player) continue;
            if (playersOnly && !(living instanceof Player)) continue;

            int[] c = colorFor(living);
            Vec3 pos = e.position();
            float ex = (float) pos.x;
            float ey = (float) (pos.y + e.getBbHeight() / 2.0);
            float ez = (float) pos.z;

            float nx = ex - sx, ny = ey - sy, nz = ez - sz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len == 0) continue;
            nx /= len; ny /= len; nz /= len;

            lines.addVertex(pose, sx, sy, sz).setColor(c[0], c[1], c[2], 255).setNormal(pose, nx, ny, nz).setLineWidth(width);
            lines.addVertex(pose, ex, ey, ez).setColor(c[0], c[1], c[2], 255).setNormal(pose, nx, ny, nz).setLineWidth(width);
        }

        poseStack.popPose();
        buffers.endBatch(RenderTypes.lines());
    }

    private static int[] colorFor(LivingEntity living) {
        if (living instanceof Player player) {
            String key = player.getGameProfile().name();
            if (FriendList.get().isFoe(key))    return new int[]{255, 60, 60};
            if (FriendList.get().isFriend(key)) return new int[]{60, 255, 60};
            return new int[]{255, 255, 255};
        }
        if (living instanceof Enemy) return new int[]{255, 165, 0};
        return new int[]{255, 255, 0};
    }
}
