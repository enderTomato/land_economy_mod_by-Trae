package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/**
 * JourneyMap 集成。
 *
 * 通过 JourneyMap 公开 API（journeymap.client.api）实现：
 * - 在全屏地图上渲染区域边界（通过 RenderLevelStageEvent + MapBoundaryRenderer）
 * - 监听全屏地图鼠标事件实现 Ctrl+拖动选框
 *
 * 使用反射避免编译期依赖 JourneyMap API jar。
 */
public class JourneyMapIntegration implements IMapIntegration {

    private static boolean isSelecting = false;
    private static int selectionButton = -1;
    private static int dragStartBlockX, dragStartBlockZ;
    private static int dragEndBlockX, dragEndBlockZ;

    // JourneyMap API 引用
    private Object apiInstance;
    private boolean apiAvailable = false;

    @Override
    public String getModName() { return "JourneyMap"; }

    @Override
    public boolean supportsSelection() { return true; }

    @Override
    public boolean supportsOverlay() { return true; }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        tryInitJourneyMapApi();
        LandEconomyMod.LOGGER.info("[JourneyMapIntegration] Initialized. apiAvailable={}", apiAvailable);
    }

    @Override
    public void shutdown() {
        MinecraftForge.EVENT_BUS.unregister(this);
        isSelecting = false;
        selectionButton = -1;
        apiInstance = null;
        apiAvailable = false;
    }

    /** 尝试通过反射获取 JourneyMap API 实例 */
    private void tryInitJourneyMapApi() {
        try {
            Class<?> clientApiClass = Class.forName("journeymap.client.api.ClientAPI");
            Object api = clientApiClass.getMethod("getInstance").invoke(null);
            apiInstance = api;
            apiAvailable = true;
        } catch (Exception e) {
            LandEconomyMod.LOGGER.warn("[JourneyMapIntegration] Failed to get API: {}", e.getMessage());
        }
    }

    /**
     * 在 RenderLevelStage 绘制区域边界。
     * JourneyMap 的 minimap 和全屏地图会渲染世界空间的内容。
     */
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!apiAvailable || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        MapBoundaryRenderer.drawWorldBoundaries(event);
    }

    /**
     * 处理 JourneyMap 全屏地图中的鼠标事件。
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
                PlotClientCache.Cell cell = PlotClientCache.get(key);
                if (selectionButton == 0) {
                    if (cell == null || (!cell.isMine() && !cell.isOthers())) {
                        buy.add(key);
                    }
                } else if (selectionButton == 1) {
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