package cn.autoforged.land_economy_mod_1783600667.client.plot;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

/**
 * 地图打开决策器。
 *
 * 当 /land map 命令触发时，按优先级检测已安装的第三方地图模组，
 * 自动打开对应的高性能全屏地图，仅在无第三方地图时回退到自研 PlotMapScreen。
 *
 * 优先级: JourneyMap > Xaero's World Map > PlotMapScreen（回退）
 */
public final class MapOpener {

    private MapOpener() {}

    /**
     * 打开最合适的地图全屏。
     * 由 ClientPacketReceivers.onOpenScreen() 调用。
     */
    public static void openMap() {
        Minecraft mc = Minecraft.getInstance();

        // 未启用集成 → 直接使用自研地图
        if (!ModConfig.COMMON.plotMapIntegrationEnabled.get()) {
            mc.setScreen(new PlotMapScreen());
            return;
        }

        // 优先 JourneyMap
        if (ModList.get().isLoaded("journeymap") && ModConfig.COMMON.plotJourneyMapIntegration.get()) {
            if (openJourneyMapFullscreen(mc)) {
                LandEconomyMod.LOGGER.info("[MapOpener] Opened JourneyMap fullscreen.");
                return;
            }
        }

        // 其次 Xaero's World Map
        if (ModList.get().isLoaded("xaeroworldmap") && ModConfig.COMMON.plotXaeroWorldMapIntegration.get()) {
            if (openXaeroWorldMapFullscreen(mc)) {
                LandEconomyMod.LOGGER.info("[MapOpener] Opened Xaero's World Map fullscreen.");
                return;
            }
        }

        // 回退：自研 PlotMapScreen
        LandEconomyMod.LOGGER.info("[MapOpener] No third-party map available, falling back to PlotMapScreen.");
        mc.setScreen(new PlotMapScreen());
    }

    /**
     * 通过反射打开 JourneyMap 全屏地图。
     */
    private static boolean openJourneyMapFullscreen(Minecraft mc) {
        try {
            // JourneyMap 全屏地图类: journeymap.client.ui.fullscreen.Fullscreen
            Class<?> fullscreenClass = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");

            // 尝试通过 state() 静态方法获取或创建实例
            try {
                java.lang.reflect.Method stateMethod = fullscreenClass.getMethod("state");
                Object state = stateMethod.invoke(null);
                // 如果 state 存在，通过 Fullscreen 构造函数打开
                java.lang.reflect.Constructor<?> ctor = fullscreenClass.getDeclaredConstructor(state.getClass());
                ctor.setAccessible(true);
                Object fullscreen = ctor.newInstance(state);
                mc.setScreen((net.minecraft.client.gui.screens.Screen) fullscreen);
                return true;
            } catch (NoSuchMethodException e1) {
                // 尝试无参构造
                try {
                    java.lang.reflect.Constructor<?> ctor = fullscreenClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object fullscreen = ctor.newInstance();
                    mc.setScreen((net.minecraft.client.gui.screens.Screen) fullscreen);
                    return true;
                } catch (NoSuchMethodException e2) {
                    // 尝试通过 Minecraft 参数构造
                    java.lang.reflect.Constructor<?> ctor = fullscreenClass.getDeclaredConstructor(Minecraft.class);
                    ctor.setAccessible(true);
                    Object fullscreen = ctor.newInstance(mc);
                    mc.setScreen((net.minecraft.client.gui.screens.Screen) fullscreen);
                    return true;
                }
            }
        } catch (Exception e) {
            LandEconomyMod.LOGGER.warn("[MapOpener] Failed to open JourneyMap fullscreen: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 通过反射打开 Xaero's World Map 全屏地图。
     */
    private static boolean openXaeroWorldMapFullscreen(Minecraft mc) {
        try {
            // Xaero's World Map 全屏地图类: xaero.map.gui.GuiMap
            Class<?> guiMapClass = Class.forName("xaero.map.gui.GuiMap");

            // 尝试通过 WorldMap 的静态方法打开
            try {
                Class<?> worldMapClass = Class.forName("xaero.map.WorldMap");
                java.lang.reflect.Method openMethod = worldMapClass.getMethod("openMap");
                openMethod.invoke(null);
                return true;
            } catch (Exception e1) {
                // 尝试直接构造 GuiMap
                try {
                    // GuiMap() 无参构造
                    java.lang.reflect.Constructor<?> ctor = guiMapClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object guiMap = ctor.newInstance();
                    mc.setScreen((net.minecraft.client.gui.screens.Screen) guiMap);
                    return true;
                } catch (NoSuchMethodException e2) {
                    // GuiMap(Minecraft) 构造
                    java.lang.reflect.Constructor<?> ctor = guiMapClass.getDeclaredConstructor(Minecraft.class);
                    ctor.setAccessible(true);
                    Object guiMap = ctor.newInstance(mc);
                    mc.setScreen((net.minecraft.client.gui.screens.Screen) guiMap);
                    return true;
                }
            }
        } catch (Exception e) {
            LandEconomyMod.LOGGER.warn("[MapOpener] Failed to open Xaero's World Map fullscreen: {}", e.getMessage());
            return false;
        }
    }
}