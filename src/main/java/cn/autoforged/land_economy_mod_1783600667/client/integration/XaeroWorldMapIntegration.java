package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Xaero's World Map 集成。
 *
 * 功能：
 * - 在全屏世界地图上通过 Ctrl+左键/右键框选区块
 * - 在世界地图上渲染区域边界
 *
 * 使用反射实现，因为 Xaero's World Map 不提供公开 API。
 */
public class XaeroWorldMapIntegration implements IMapIntegration {

    private static boolean isSelecting = false;
    private static int selectionButton = -1;
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
        selectionButton = -1;
    }

    /**
     * 处理世界地图全屏中的鼠标点击。
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
                    PlotClientCache.Cell cell = PlotClientCache.get(key);
                    if (cell == null || (!cell.isMine() && !cell.isOthers())) {
                        buy.add(key);
                    }
                } else if (selectionButton == 1) {
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