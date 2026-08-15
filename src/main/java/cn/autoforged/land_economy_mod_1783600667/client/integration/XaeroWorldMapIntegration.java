package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotKeyBindings;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SPlotAction;
import cn.autoforged.land_economy_mod_1783600667.plot.PlotAction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/**
 * Xaero's World Map 集成。
 *
 * 功能：
 * - 在全屏世界地图上通过中键拖拽框选区块（购买）、右键放弃
 * - 在世界地图上渲染区域边界（通过 RenderLevelStageEvent + MapBoundaryRenderer）
 *
 * 通过反射和 ScreenEvent 实现，因为 Xaero's World Map 不提供公开 API。
 */
public class XaeroWorldMapIntegration implements IMapIntegration {

    private static final int SELECT_BUY = 0;
    private static final int SELECT_ABANDON = 1;

    private static boolean isSelecting = false;
    private static int selectionMode = -1;
    private static int dragStartBlockX, dragStartBlockZ;
    private static int dragEndBlockX, dragEndBlockZ;

    @Override
    public String getModName() { return "Xaero's World Map"; }

    @Override
    public boolean supportsSelection() { return true; }

    @Override
    public boolean supportsOverlay() { return true; }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        LandEconomyMod.LOGGER.info("[XaeroWorldMapIntegration] Initialized.");
    }

    @Override
    public void shutdown() {
        MinecraftForge.EVENT_BUS.unregister(this);
        isSelecting = false;
        selectionMode = -1;
    }

    /**
     * 在世界空间渲染区域边界。
     */
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        MapBoundaryRenderer.drawWorldBoundaries(event);
    }

    /**
     * 开始选框操作。
     *
     * @param isBuy true=购买选框, false=放弃选框
     * @param blockX 起始世界X坐标
     * @param blockZ 起始世界Z坐标
     */
    public static void startSelection(boolean isBuy, int blockX, int blockZ) {
        selectionMode = isBuy ? SELECT_BUY : SELECT_ABANDON;
        isSelecting = true;
        dragStartBlockX = blockX;
        dragStartBlockZ = blockZ;
        dragEndBlockX = blockX;
        dragEndBlockZ = blockZ;
    }

    /**
     * 更新选框范围。
     */
    public static void updateSelection(int blockX, int blockZ) {
        if (!isSelecting) return;
        dragEndBlockX = blockX;
        dragEndBlockZ = blockZ;
    }

    /**
     * 结束选框，计算框内区块并发送操作。
     */
    public static void endSelection() {
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
                if (selectionMode == SELECT_BUY) {
                    if (cell == null || (!cell.isMine() && !cell.isOthers())) {
                        buy.add(key);
                    }
                } else {
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

        selectionMode = -1;
    }

    /**
     * 处理单个区块的放弃操作（右键单击）。
     */
    public static void handleSingleAbandon(int blockX, int blockZ) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        long key = ChunkPos.asLong(cx, cz);
        PlotClientCache.Cell cell = PlotClientCache.get(key);
        if (cell != null && cell.isMine()) {
            String dim = Minecraft.getInstance().level.dimension().location().toString();
            Minecraft.getInstance().setScreen(new MapSelectionConfirmScreen(
                    List.of(), List.of(key), dim));
        }
    }
}