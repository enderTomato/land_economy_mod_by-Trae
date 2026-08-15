package cn.autoforged.land_economy_mod_1783600667.client.plot;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

/**
 * 地块地图生命周期管理器。
 *
 * 管理 /land map 的打开/关闭/强制退出，维护当前地图类型状态。
 * 优先级: JourneyMap > Xaero's World Map > FallbackPlotScreen（回退）
 */
public final class PlotMapHandler {

    public enum MapType { NONE, JOURNEYMAP, XAERO_WORLDMAP, FALLBACK }

    private static MapType currentMapType = MapType.NONE;

    private PlotMapHandler() {}

    /** 打开最合适的地图全屏 */
    public static void openMap() {
        Minecraft mc = Minecraft.getInstance();

        // 未启用集成 → 直接使用回退地图
        if (!ModConfig.COMMON.plotMapIntegrationEnabled.get()) {
            currentMapType = MapType.FALLBACK;
            mc.setScreen(new FallbackPlotScreen());
            return;
        }

        // 优先 JourneyMap
        if (ModList.get().isLoaded("journeymap") && ModConfig.COMMON.plotJourneyMapIntegration.get()) {
            if (openJourneyMapFullscreen(mc)) {
                currentMapType = MapType.JOURNEYMAP;
                LandEconomyMod.LOGGER.info("[PlotMapHandler] Opened JourneyMap fullscreen.");
                return;
            }
        }

        // 其次 Xaero's World Map
        if (ModList.get().isLoaded("xaeroworldmap") && ModConfig.COMMON.plotXaeroWorldMapIntegration.get()) {
            if (openXaeroWorldMapFullscreen(mc)) {
                currentMapType = MapType.XAERO_WORLDMAP;
                LandEconomyMod.LOGGER.info("[PlotMapHandler] Opened Xaero's World Map fullscreen.");
                return;
            }
        }

        // 回退
        currentMapType = MapType.FALLBACK;
        LandEconomyMod.LOGGER.info("[PlotMapHandler] No third-party map, falling back to FallbackPlotScreen.");
        mc.setScreen(new FallbackPlotScreen());
    }

    /** 关闭当前地图（无论类型） */
    public static void closeMap() {
        Minecraft mc = Minecraft.getInstance();
        var screen = mc.screen;
        if (screen == null) {
            currentMapType = MapType.NONE;
            return;
        }

        String className = screen.getClass().getName();

        // 关闭本模组创建的 screen
        if (className.startsWith("cn.autoforged.land_economy_mod_1783600667.client")) {
            mc.setScreen(null);
        }
        // 关闭 JourneyMap 全屏
        if (className.equals("journeymap.client.ui.fullscreen.Fullscreen")) {
            mc.setScreen(null);
        }
        // 关闭 Xaero's World Map 全屏
        if (className.equals("xaero.map.gui.GuiMap")) {
            mc.setScreen(null);
        }

        currentMapType = MapType.NONE;
    }

    /** 当前是否有地图打开 */
    public static boolean isMapOpen() {
        return currentMapType != MapType.NONE;
    }

    /** 获取当前地图类型 */
    public static MapType getCurrentMapType() {
        return currentMapType;
    }

    /** 区块数据更新回调（来自 ClientPacketReceivers） */
    public static void onChunkDataUpdated() {
        // 回退地图需要刷新渲染
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FallbackPlotScreen fbs) {
            fbs.onChunkDataUpdated();
        }
    }

    /** 操作结果回调（来自 ClientPacketReceivers） */
    public static void onActionResult(boolean success, String message) {
        // 回退地图需要清除选区
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FallbackPlotScreen fbs) {
            fbs.clearSelection();
        }
    }

    // ====== 专用打开逻辑 ======

    private static boolean openJourneyMapFullscreen(Minecraft mc) {
        try {
            Class<?> fullscreenClass = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");
            try {
                java.lang.reflect.Constructor<?> ctor = fullscreenClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object fullscreen = ctor.newInstance();
                mc.setScreen((net.minecraft.client.gui.screens.Screen) fullscreen);
                return true;
            } catch (NoSuchMethodException e1) {
                java.lang.reflect.Constructor<?> ctor = fullscreenClass.getDeclaredConstructor(Minecraft.class);
                ctor.setAccessible(true);
                Object fullscreen = ctor.newInstance(mc);
                mc.setScreen((net.minecraft.client.gui.screens.Screen) fullscreen);
                return true;
            }
        } catch (Exception e) {
            LandEconomyMod.LOGGER.warn("[PlotMapHandler] Failed to open JourneyMap: {}", e.getMessage());
            return false;
        }
    }

    private static boolean openXaeroWorldMapFullscreen(Minecraft mc) {
        try {
            try {
                Class<?> worldMapClass = Class.forName("xaero.map.WorldMap");
                java.lang.reflect.Method openMethod = worldMapClass.getMethod("openMap");
                openMethod.invoke(null);
                return true;
            } catch (Exception e1) {
                Class<?> guiMapClass = Class.forName("xaero.map.gui.GuiMap");
                java.lang.reflect.Constructor<?> ctor = guiMapClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object guiMap = ctor.newInstance();
                mc.setScreen((net.minecraft.client.gui.screens.Screen) guiMap);
                return true;
            }
        } catch (Exception e) {
            LandEconomyMod.LOGGER.warn("[PlotMapHandler] Failed to open Xaero's World Map: {}", e.getMessage());
            return false;
        }
    }
}