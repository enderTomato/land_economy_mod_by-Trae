package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 第三方地图全屏鼠标事件捕获器。
 *
 * 监听 Forge ScreenEvent，当检测到当前屏幕是 JourneyMap 全屏或 Xaero's World Map 全屏时，
 * 拦截可配置键位的鼠标点击事件：
 * - 购买键位（默认中键）拖拽 = 选框购买
 * - 放弃键位（默认右键）单击 = 放弃区块
 *
 * 边界渲染已通过 RenderLevelStageEvent 实现，在第三方地图上可见。
 */
public final class MapScreenEventHandler {

    private static final String JOURNEYMAP_FULLSCREEN = "journeymap.client.ui.fullscreen.Fullscreen";
    private static final String XAERO_WORLDMAP_GUI = "xaero.map.gui.GuiMap";

    private static boolean initialized = false;

    private MapScreenEventHandler() {}

    public static void init() {
        if (initialized) return;
        MinecraftForge.EVENT_BUS.register(new MapScreenEventHandler());
        initialized = true;
        LandEconomyMod.LOGGER.info("[MapScreenEventHandler] Initialized.");
    }

    public static void shutdown() {
        initialized = false;
    }

    @SubscribeEvent
    public void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        var screen = event.getScreen();
        if (screen == null) return;

        String className = screen.getClass().getName();
        boolean isJourneyMap = className.equals(JOURNEYMAP_FULLSCREEN);
        boolean isXaeroWorldMap = className.equals(XAERO_WORLDMAP_GUI);

        if (!isJourneyMap && !isXaeroWorldMap) return;

        int button = event.getButton();

        // 购买键位（中键）= 开始选框购买
        if (PlotKeyBindings.isBuyButton(button)) {
            // 使用玩家当前位置作为选框起始点
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            int blockX = player.blockPosition().getX();
            int blockZ = player.blockPosition().getZ();
            XaeroWorldMapIntegration.startSelection(true, blockX, blockZ);
            event.setCanceled(true);
            return;
        }

        // 放弃键位（右键）= 放弃当前区块
        if (PlotKeyBindings.isAbandonButton(button)) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            int blockX = player.blockPosition().getX();
            int blockZ = player.blockPosition().getZ();
            XaeroWorldMapIntegration.handleSingleAbandon(blockX, blockZ);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        var screen = event.getScreen();
        if (screen == null) return;

        String className = screen.getClass().getName();
        boolean isJourneyMap = className.equals(JOURNEYMAP_FULLSCREEN);
        boolean isXaeroWorldMap = className.equals(XAERO_WORLDMAP_GUI);

        if (!isJourneyMap && !isXaeroWorldMap) return;

        int button = event.getButton();

        if (PlotKeyBindings.isBuyButton(button)) {
            XaeroWorldMapIntegration.endSelection();
        }
    }
}