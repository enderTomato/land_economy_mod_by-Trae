package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * 共享的区块边界世界空间渲染器。
 *
 * 在 RenderLevelStageEvent.AFTER_TRIPWIRE_BLOCKS 阶段绘制半透明四边形，
 * 使边界在 JourneyMap、Xaero's Minimap、Xaero's World Map 等第三方地图上可见。
 *
 * 三个集成类（JourneyMapIntegration / XaeroMinimapIntegration / XaeroWorldMapIntegration）
 * 共用此渲染器，避免代码重复。
 */
public final class MapBoundaryRenderer {

    /** 渲染半径（区块数） */
    private static final int RENDER_RADIUS = 8;

    private MapBoundaryRenderer() {}

    /**
     * 在世界空间绘制玩家周围的区域边界。
     * 应在 RenderLevelStageEvent 处理器中调用。
     */
    public static void drawWorldBoundaries(RenderLevelStageEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;

        PoseStack poseStack = event.getPoseStack();
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        var camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);

        for (int cx = playerChunkX - RENDER_RADIUS; cx <= playerChunkX + RENDER_RADIUS; cx++) {
            for (int cz = playerChunkZ - RENDER_RADIUS; cz <= playerChunkZ + RENDER_RADIUS; cz++) {
                PlotClientCache.Cell cell = PlotClientCache.get(cx, cz);
                if (cell == null || (!cell.isMine() && !cell.isOthers())) continue;

                int borderColor = cell.isMine() ? 0x440000FF : 0x44FF0000;
                int worldX = cx << 4;
                int worldZ = cz << 4;

                float r = ((borderColor >> 16) & 0xFF) / 255f;
                float g = ((borderColor >> 8) & 0xFF) / 255f;
                float b = (borderColor & 0xFF) / 255f;
                float a = ((borderColor >> 24) & 0xFF) / 255f;

                poseStack.pushPose();
                poseStack.translate(worldX - camX, 0 - camY, worldZ - camZ);

                var matrix = poseStack.last().pose();
                // 底部四边形（16x16 区块边界）
                vertexConsumer.vertex(matrix, 0, 0, 0).color(r, g, b, a).normal(0, 1, 0).endVertex();
                vertexConsumer.vertex(matrix, 16, 0, 0).color(r, g, b, a).normal(0, 1, 0).endVertex();
                vertexConsumer.vertex(matrix, 16, 0, 16).color(r, g, b, a).normal(0, 1, 0).endVertex();
                vertexConsumer.vertex(matrix, 0, 0, 16).color(r, g, b, a).normal(0, 1, 0).endVertex();

                poseStack.popPose();
            }
        }
        // 立即提交渲染
        bufferSource.endBatch();
    }
}