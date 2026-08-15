package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 第三方地图全屏鼠标事件捕获器。
 *
 * 监听 Forge ScreenEvent，当检测到当前屏幕是 JourneyMap 全屏或 Xaero's World Map 全屏时，
 * 拦截 Ctrl+鼠标点击事件，将操作委托给对应集成类的静态处理方法。
 *
 * 简化方案：由于第三方地图的屏幕坐标→世界坐标转换需要深度反射，
 * 当前版本使用玩家所在位置的区块作为操作目标（Ctrl+左键=购买, Ctrl+右键=放弃）。
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
        // 事件总线注册使用实例方法，通过 instance 注销
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
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        // 检查 Ctrl 是否按下
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean ctrlDown = (org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS)
                || (org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);

        if (!ctrlDown) return;

        // 使用玩家当前所在区块作为操作目标
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        int blockX = player.blockPosition().getX();
        int blockZ = player.blockPosition().getZ();

        if (isJourneyMap) {
            JourneyMapIntegration.handleMouseClick(button, true, blockX, blockZ);
        } else {
            XaeroWorldMapIntegration.handleMouseClick(button, true, blockX, blockZ);
        }

        // 消费事件，阻止第三方地图的默认行为
        event.setCanceled(true);
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
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        if (isJourneyMap) {
            JourneyMapIntegration.handleMouseRelease();
        } else {
            XaeroWorldMapIntegration.handleMouseRelease();
        }
    }
}