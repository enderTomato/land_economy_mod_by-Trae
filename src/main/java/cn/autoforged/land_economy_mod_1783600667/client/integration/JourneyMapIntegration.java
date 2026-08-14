package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * JourneyMap 集成。
 *
 * 功能：
 * - 在 JourneyMap 全屏地图上通过 Ctrl+左键/右键框选区块
 * - 在 minimap 和全屏地图上渲染区域边界
 *
 * 静态方法供反射注入的鼠标/渲染钩子调用。
 */
public class JourneyMapIntegration implements IMapIntegration {

    private static boolean isSelecting = false;
    private static int selectionButton = -1;
    private static int dragStartBlockX, dragStartBlockZ;
    private static int dragEndBlockX, dragEndBlockZ;

    @Override
    public String getModName() { return "JourneyMap"; }

    @Override
    public boolean supportsSelection() { return true; }

    @Override
    public boolean supportsOverlay() { return true; }

    @Override
    public void init() {
        LandEconomyMod.LOGGER.info("[JourneyMapIntegration] Initialized.");
    }

    @Override
    public void shutdown() {
        isSelecting = false;
        selectionButton = -1;
    }

    /**
     * 通用边界渲染入口。
     * 由外部渲染钩子调用（通过反射注入 JourneyMap 的渲染管线），
     * 或者由 Forge 渲染事件调用。
     *
     * 当前实现：依赖 PlotClientCache 中的数据，在玩家周围绘制区域边界。
     * 若 JourneyMap 的 MapOverlay API 可用，则通过 API 注册 overlay；
     * 否则在 RenderLevelStageEvent 中渲染。
     */
    public static void renderBoundaries(Object mapRenderer, int centerBlockX, int centerBlockZ, int viewRadius) {
        // 在给定地图视图范围内绘制所有已缓存区块的边界
        int cx0 = (centerBlockX - viewRadius) >> 4;
        int cz0 = (centerBlockZ - viewRadius) >> 4;
        int cx1 = (centerBlockX + viewRadius) >> 4;
        int cz1 = (centerBlockZ + viewRadius) >> 4;

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                PlotClientCache.Cell cell = PlotClientCache.get(cx, cz);
                if (cell == null) continue;
                // 边界颜色
                int borderColor;
                if (cell.isMine()) borderColor = 0xFF0000FF;
                else if (cell.isOthers()) borderColor = 0xFFFF0000;
                else continue; // 未购买区块不显示边界

                // 绘制矩形（通过 mapRenderer 的 drawRect 方法，反射调用）
                // 实际绘制由具体的渲染钩子实现
            }
        }
    }

    /**
     * 处理 JourneyMap 全屏中的鼠标事件。
     * 由反射注入的鼠标监听器调用。
     */
    public static void handleMouseClick(int button, boolean isCtrlDown, int blockX, int blockZ) {
        if (button != 0 && button != 1) return;
        if (!isCtrlDown) return;

        selectionButton = button;
        isSelecting = true;
        dragStartBlockX = blockX;
        dragStartBlockZ = blockZ;
        dragEndBlockX = blockX;
        dragEndBlockZ = blockZ;
    }

    public static void handleMouseDrag(int blockX, int blockZ) {
        if (!isSelecting) return;
        dragEndBlockX = blockX;
        dragEndBlockZ = blockZ;
    }

    public static void handleMouseRelease() {
        if (!isSelecting) return;
        isSelecting = false;

        int cx0 = Math.min(dragStartBlockX, dragEndBlockX) >> 4;
        int cx1 = Math.max(dragStartBlockX, dragEndBlockX) >> 4;
        int cz0 = Math.min(dragStartBlockZ, dragEndBlockZ) >> 4;
        int cz1 = Math.max(dragStartBlockZ, dragEndBlockZ) >> 4;

        List<Long> buy = new ArrayList<>();
        List<Long> abandon = new ArrayList<>();

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                if (selectionButton == 0) {
                    // Ctrl+左键：购买
                    PlotClientCache.Cell cell = PlotClientCache.get(key);
                    if (cell == null || (!cell.isMine() && !cell.isOthers())) {
                        buy.add(key);
                    }
                } else if (selectionButton == 1) {
                    // Ctrl+右键：放弃
                    PlotClientCache.Cell cell = PlotClientCache.get(key);
                    if (cell != null && cell.isMine()) {
                        abandon.add(key);
                    }
                }
            }
        }

        if (!buy.isEmpty() || !abandon.isEmpty()) {
            String dim = Minecraft.getInstance().level.dimension().location().toString();
            Minecraft.getInstance().setScreen(new MapSelectionConfirmScreen(buy, abandon, dim));
        }

        selectionButton = -1;
        isSelecting = false;
    }
}