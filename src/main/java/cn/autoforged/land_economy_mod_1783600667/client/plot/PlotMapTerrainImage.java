package cn.autoforged.land_economy_mod_1783600667.client.plot;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * 将地形颜色预渲染到单个 NativeImage 纹理，避免每帧数万次 g.fill() 调用。
 *
 * 仅在 view 位置/缩放变化时重新渲染纹理；其余帧直接绘制缓存的纹理。
 */
public final class PlotMapTerrainImage {

    private static final ResourceLocation TEX_ID = new ResourceLocation("land_economy_mod", "plot_terrain");

    private DynamicTexture dynamicTexture;
    private NativeImage image;
    private int lastCenterX = Integer.MIN_VALUE, lastCenterZ = Integer.MIN_VALUE;
    private int lastCellSize = -1;
    private int lastScreenW, lastScreenH;

    /** 绘制地形纹理到屏幕 */
    public void render(GuiGraphics g, PlotMapView view, int screenW, int screenH) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        int cellSize = (int) view.cellSize;
        if (cellSize < 16) return; // 小缩放不用地形

        int cx0 = (int) Math.floor((view.centerX - (screenW / 2.0) * (16.0 / cellSize)) / 16.0);
        int cz0 = (int) Math.floor((view.centerZ - (screenH / 2.0) * (16.0 / cellSize)) / 16.0);
        int cx1 = (int) Math.ceil((view.centerX + (screenW / 2.0) * (16.0 / cellSize)) / 16.0);
        int cz1 = (int) Math.ceil((view.centerZ + (screenH / 2.0) * (16.0 / cellSize)) / 16.0);

        int viewCenterX = (int) view.centerX;
        int viewCenterZ = (int) view.centerZ;

        // 检查是否需要重建纹理
        if (image == null || viewCenterX != lastCenterX || viewCenterZ != lastCenterZ
                || cellSize != lastCellSize || screenW != lastScreenW || screenH != lastScreenH) {
            rebuild(level, cx0, cz0, cx1, cz1, viewCenterX, viewCenterZ, cellSize, screenW, screenH);
            lastCenterX = viewCenterX;
            lastCenterZ = viewCenterZ;
            lastCellSize = cellSize;
            lastScreenW = screenW;
            lastScreenH = screenH;
        }

        if (image == null) return;

        // 绑定纹理并绘制
        RenderSystem.setShaderTexture(0, TEX_ID);
        RenderSystem.enableBlend();
        g.blit(TEX_ID, 0, 0, 0, 0, screenW, screenH, screenW, screenH);
        RenderSystem.disableBlend();
    }

    private void rebuild(ClientLevel level, int cx0, int cz0, int cx1, int cz1,
                         int viewCenterX, int viewCenterZ, int cellSize, int screenW, int screenH) {
        if (image == null || image.getWidth() != screenW || image.getHeight() != screenH) {
            image = new NativeImage(screenW, screenH, false);
        }

        double scale = cellSize / 16.0;
        int halfW = screenW / 2;
        int halfH = screenH / 2;

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                int[] terrain = PlotMapTerrainRenderer.sampleTerrain(level, cx, cz);
                if (terrain == null) continue;

                // 计算区块在屏幕上的起始像素位置
                int px = (int) (halfW + (cx * 16 - viewCenterX) * scale);
                int py = (int) (halfH + (cz * 16 - viewCenterZ) * scale);

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int tx = px + (int) (lx * scale);
                        int ty = py + (int) (lz * scale);
                        int tw = (int) Math.ceil(scale);
                        int th = (int) Math.ceil(scale);

                        int color = terrain[lx * 16 + lz];
                        // 填充像素块
                        for (int dy = 0; dy < th && (ty + dy) < screenH; dy++) {
                            for (int dx = 0; dx < tw && (tx + dx) < screenW; dx++) {
                                int sx = tx + dx;
                                int sy = ty + dy;
                                if (sx >= 0 && sx < screenW && sy >= 0 && sy < screenH) {
                                    image.setPixelRGBA(sx, sy, color);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 上传到 GPU 纹理
        if (dynamicTexture != null) {
            dynamicTexture.close();
        }
        dynamicTexture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(TEX_ID, dynamicTexture);
        dynamicTexture.upload();
    }

    public void invalidate() {
        lastCenterX = Integer.MIN_VALUE;
        lastCenterZ = Integer.MIN_VALUE;
    }

    public void close() {
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        if (image != null) {
            image.close();
            image = null;
        }
    }
}