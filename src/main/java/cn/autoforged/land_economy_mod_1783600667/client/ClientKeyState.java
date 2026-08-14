package cn.autoforged.land_economy_mod_1783600667.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * 客户端 WASD 按键状态跟踪器（用于地块地图视角平移）。
 *
 * 仅在 PlotMapScreen 打开期间采样；不注册全局 KeyMapping，避免与玩家实体移动冲突。
 * 使用 GLFW 直接查询按键状态，而非 Minecraft 的 KeyMapping.isDown()（后者会被
 * Screen 抢占事件后不会更新）。
 */
public final class ClientKeyState {

    private ClientKeyState() {}

    public static boolean isW() { return isDown(InputConstants.KEY_W); }
    public static boolean isA() { return isDown(InputConstants.KEY_A); }
    public static boolean isS() { return isDown(InputConstants.KEY_S); }
    public static boolean isD() { return isDown(InputConstants.KEY_D); }

    public static boolean isSpace() { return isDown(InputConstants.KEY_SPACE); }
    public static boolean isEsc()   { return isDown(InputConstants.KEY_ESCAPE); }
    public static boolean isEnter() { return isDown(InputConstants.KEY_RETURN); }

    /** 查询 GLFW 按键是否处于按下状态（不消费事件） */
    private static boolean isDown(int keyCode) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, keyCode);
    }
}
