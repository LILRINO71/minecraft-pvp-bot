package com.mcbot.module.render;

import com.mcbot.MCBotClient;
import com.mcbot.module.Module;
import com.mcbot.module.ModuleCategory;
import com.mcbot.module.ModuleManager;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Entity ESP - draws colored outline (line) boxes around living entities every frame.
 *
 * Colors:
 *   FOE players     = red
 *   FRIEND players  = green
 *   other players   = white
 *   hostile mobs    = orange
 *   other living    = yellow
 *
 * The actual drawing happens in {@link #render(LevelRenderContext)}, invoked from a global
 * Fabric LevelRenderEvents callback registered in MCBotClient.onInitializeClient(). Because
 * that callback is global, rendering is gated on the static {@link #ACTIVE} flag, which
 * mirrors this module's enabled state (set in onEnable/onDisable).
 *
 * MC 26.1 API notes (all javap-verified against minecraft-merged.jar and
 * fabric-api 0.150.0+26.1.2):
 *   - The old WorldRenderEvents / WorldRenderContext are gone. Replaced by
 *     net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents and
 *     LevelRenderContext. LevelRenderContext exposes poseStack():PoseStack and
 *     bufferSource():MultiBufferSource.BufferSource. There is no camera() accessor, so we
 *     take the camera from Minecraft.getInstance().gameRenderer.getMainCamera().position().
 *   - RenderType moved to net.minecraft.client.renderer.rendertype.RenderType and the line
 *     render type factory is RenderTypes.lines() (in the same package).
 *   - ShapeRenderer.renderLineBox / LevelRenderer.renderLineBox no longer exist, so the 12
 *     edges of each AABB are emitted manually to a lines VertexConsumer. The vanilla lines
 *     render type requires both a color and a per-vertex normal, so setNormal is set for
 *     each segment (direction of the edge).
 *   - VertexConsumer.addVertex(PoseStack.Pose, float,float,float) transforms by the pose;
 *     we translate the PoseStack by -camPos so world-space box coords land correctly.
 */
public class EntityESPModule extends Module {

    private static EntityESPModule INSTANCE;
    public static EntityESPModule get() { return INSTANCE; }

    /** Global gate for the world-render callback (the callback is registered once, globally). */
    private static volatile boolean ACTIVE = false;

    // ARGB-ish components (0-255). Alpha kept high so lines are clearly visible.
    private static final int A = 255;

    private final com.mcbot.settings.DoubleSetting lineWidth = addSetting(new com.mcbot.settings.DoubleSetting(
            "width", "Outline thickness in pixels.", 2.0, 0.5, 6.0, 0.5));
    private final com.mcbot.settings.BoolSetting playersOnly = addSetting(new com.mcbot.settings.BoolSetting(
            "playersOnly", "Only draw boxes around players (ignore mobs).", false));

    /** Mirror of the width setting readable from the static render loop. */
    private static volatile float activeWidth = 2.0f;

    public EntityESPModule() {
        super("EntityESP", "Colored outline boxes around living entities.", ModuleCategory.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        ACTIVE = true;
    }

    @Override
    protected void onDisable() {
        ACTIVE = false;
    }

    @Override
    protected void onTick(Minecraft client) {
        // Sync tunables into the static fields the render loop reads.
        activeWidth = (float) (double) lineWidth.get();
    }

    /**
     * Called every frame from the Fabric LevelRenderEvents callback (see MCBotClient wiring).
     * Safe to call unconditionally; it no-ops unless the module is active.
     */
    public static void render(LevelRenderContext ctx) {
        if (!ACTIVE) return;
        if (ctx == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // Double-check against the real module (defensive; ACTIVE should already match).
        MCBotClient bot = MCBotClient.get();
        if (bot == null) return;
        ModuleManager mm = bot.getModuleManager();
        if (mm == null) return;
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;

        ClientLevel level = mc.level;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;
        Vec3 camPos = camera.position();
        double camX = camPos.x;
        double camY = camPos.y;
        double camZ = camPos.z;

        PoseStack poseStack = ctx.poseStack();
        MultiBufferSource.BufferSource buffers = ctx.bufferSource();
        if (poseStack == null || buffers == null) return;

        VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());

        Entity self = mc.getCameraEntity();
        if (self == null) self = mc.player;

        poseStack.pushPose();
        // Translate world-space so that the camera sits at the origin.
        poseStack.translate(-camX, -camY, -camZ);
        PoseStack.Pose pose = poseStack.last();

        boolean playersOnly = INSTANCE.playersOnly.get();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == self) continue;               // skip the local player / camera entity
            if (entity == mc.player) continue;
            if (playersOnly && !(living instanceof Player)) continue;

            int[] rgb = colorFor(living);
            AABB box = entity.getBoundingBox();
            drawLineBox(pose, lines, box, rgb[0], rgb[1], rgb[2]);
        }

        poseStack.popPose();

        // Flush the lines batch immediately so the boxes are drawn this frame.
        buffers.endBatch(RenderTypes.lines());
    }

    /** Returns {r,g,b} (0-255) for the given living entity per the color rules. */
    private static int[] colorFor(LivingEntity living) {
        if (living instanceof Player player) {
            String key = player.getGameProfile().name();
            FriendList fl = FriendList.get();
            if (fl.isFoe(key))    return new int[]{255, 60, 60};   // red
            if (fl.isFriend(key)) return new int[]{60, 255, 60};   // green
            return new int[]{255, 255, 255};                        // white (neutral player)
        }
        if (living instanceof Enemy) {
            return new int[]{255, 165, 0};                          // orange (hostile mob)
        }
        return new int[]{255, 255, 0};                              // yellow (other living)
    }

    /**
     * Emits the 12 edges of the AABB as line segments. Each vertex gets the color and each
     * segment gets a normal (the vanilla "lines" render type requires a normal per vertex).
     */
    private static void drawLineBox(PoseStack.Pose pose, VertexConsumer buf, AABB box,
                                    int r, int g, int b) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Bottom rectangle
        edge(pose, buf, x1, y1, z1, x2, y1, z1, r, g, b);
        edge(pose, buf, x2, y1, z1, x2, y1, z2, r, g, b);
        edge(pose, buf, x2, y1, z2, x1, y1, z2, r, g, b);
        edge(pose, buf, x1, y1, z2, x1, y1, z1, r, g, b);

        // Top rectangle
        edge(pose, buf, x1, y2, z1, x2, y2, z1, r, g, b);
        edge(pose, buf, x2, y2, z1, x2, y2, z2, r, g, b);
        edge(pose, buf, x2, y2, z2, x1, y2, z2, r, g, b);
        edge(pose, buf, x1, y2, z2, x1, y2, z1, r, g, b);

        // Vertical pillars
        edge(pose, buf, x1, y1, z1, x1, y2, z1, r, g, b);
        edge(pose, buf, x2, y1, z1, x2, y2, z1, r, g, b);
        edge(pose, buf, x2, y1, z2, x2, y2, z2, r, g, b);
        edge(pose, buf, x1, y1, z2, x1, y2, z2, r, g, b);
    }

    /** One line segment from (ax,ay,az) to (bx,by,bz). Both endpoints share color + normal. */
    private static void edge(PoseStack.Pose pose, VertexConsumer buf,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             int r, int g, int b) {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0.0f) return;
        nx /= len; ny /= len; nz /= len;

        // 26.1's lines vertex format REQUIRES a per-vertex LineWidth element — omitting it
        // crashes with "Missing elements in vertex: LineWidth" (see crash-2026-07-03_15.25.15).
        buf.addVertex(pose, ax, ay, az).setColor(r, g, b, A).setNormal(pose, nx, ny, nz).setLineWidth(activeWidth);
        buf.addVertex(pose, bx, by, bz).setColor(r, g, b, A).setNormal(pose, nx, ny, nz).setLineWidth(activeWidth);
    }
}
