package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方地图模组集成管理器。
 * 在客户端初始化时检测已安装的地图模组，注册对应的集成实现。
 */
public final class MapIntegrationManager {

    private static final List<IMapIntegration> ACTIVE = new ArrayList<>();

    private MapIntegrationManager() {}

    public static void init() {
        if (!ModConfig.COMMON.plotMapIntegrationEnabled.get()) return;

        if (ModList.get().isLoaded("journeymap") && ModConfig.COMMON.plotJourneyMapIntegration.get()) {
            try {
                IMapIntegration jm = new JourneyMapIntegration();
                jm.init();
                ACTIVE.add(jm);
                LandEconomyMod.LOGGER.info("[MapIntegration] JourneyMap integration enabled.");
            } catch (Exception e) {
                LandEconomyMod.LOGGER.warn("[MapIntegration] Failed to init JourneyMap integration: {}", e.getMessage());
            }
        }

        if (ModList.get().isLoaded("xaerominimap") && ModConfig.COMMON.plotXaeroMinimapIntegration.get()) {
            try {
                IMapIntegration xm = new XaeroMinimapIntegration();
                xm.init();
                ACTIVE.add(xm);
                LandEconomyMod.LOGGER.info("[MapIntegration] Xaero's Minimap integration enabled.");
            } catch (Exception e) {
                LandEconomyMod.LOGGER.warn("[MapIntegration] Failed to init Xaero's Minimap integration: {}", e.getMessage());
            }
        }

        if (ModList.get().isLoaded("xaeroworldmap") && ModConfig.COMMON.plotXaeroWorldMapIntegration.get()) {
            try {
                IMapIntegration xw = new XaeroWorldMapIntegration();
                xw.init();
                ACTIVE.add(xw);
                LandEconomyMod.LOGGER.info("[MapIntegration] Xaero's World Map integration enabled.");
            } catch (Exception e) {
                LandEconomyMod.LOGGER.warn("[MapIntegration] Failed to init Xaero's World Map integration: {}", e.getMessage());
            }
        }

        // 初始化鼠标事件捕获器（用于第三方地图全屏中的 Ctrl+点击操作）
        if (!ACTIVE.isEmpty()) {
            MapScreenEventHandler.init();
        }
    }

    public static void shutdown() {
        for (IMapIntegration i : ACTIVE) {
            try { i.shutdown(); } catch (Exception ignored) {}
        }
        ACTIVE.clear();
    }

    public static List<IMapIntegration> getActive() {
        return ACTIVE;
    }
}