package cn.autoforged.land_economy_mod_1783600667.client.plot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 地块地图可配置键位。
 *
 * 注册两个 Forge KeyMapping：
 * - 购买键位（默认鼠标中键）：在第三方地图全屏中拖拽框选购买
 * - 放弃键位（默认鼠标右键）：在第三方地图全屏中单击放弃区块
 */
public final class PlotKeyBindings {

    public static final String CATEGORY = "key.categories.land_economy";

    public static final KeyMapping BUY_KEY = new KeyMapping(
            "key.land_economy.plot_buy",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY
    );

    public static final KeyMapping ABANDON_KEY = new KeyMapping(
            "key.land_economy.plot_abandon",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY
    );

    private static boolean registered = false;

    private PlotKeyBindings() {}

    /** 注册键位到 Forge 事件总线 */
    public static void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
                event.register(BUY_KEY);
                event.register(ABANDON_KEY);
            }
        });
        registered = true;
    }

    /** 检查购买键是否按下 */
    public static boolean isBuyKeyDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, BUY_KEY.getKey().getValue());
    }

    /** 检查放弃键是否按下 */
    public static boolean isAbandonKeyDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, ABANDON_KEY.getKey().getValue());
    }

    /** 判断给定的鼠标 button 是否为购买键 */
    public static boolean isBuyButton(int button) {
        return button == BUY_KEY.getKey().getValue();
    }

    /** 判断给定的鼠标 button 是否为放弃键 */
    public static boolean isAbandonButton(int button) {
        return button == ABANDON_KEY.getKey().getValue();
    }
}