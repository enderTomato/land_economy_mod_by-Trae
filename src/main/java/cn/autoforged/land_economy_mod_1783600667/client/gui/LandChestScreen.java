package cn.autoforged.land_economy_mod_1783600667.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 领地经济"箱子GUI"。
 *
 * 不是真正的 ContainerMenu（避免引入服务端 Container）— 而是一个 Screen，
 * 通过按钮触发与服务端指令等价的操作（发送聊天命令或网络包）。
 *
 * 包含：区域声明入口、权限管理、地图地块入口、银行、邀请/踢出、留言等。
 * 与指令功能等价，操作结果与使用指令一致。
 */
public class LandChestScreen extends Screen {

    private static final int W = 200, COLS = 2, ROWS = 7, BTN_H = 18, BTN_GAP = 4;

    public LandChestScreen() {
        super(Component.literal("领地经济 GUI"));
    }

    @Override
    protected void init() {
        super.init();
        int totalW = COLS * W + (COLS - 1) * BTN_GAP;
        int startX = (width - totalW) / 2;
        int startY = 30;

        // 左列
        addButton(startX, startY, "§a打开地图地块界面 (/land map)",      "/land map");
        addButton(startX, startY + 1 * (BTN_H + BTN_GAP), "§b切换模式 (/land mode new)", "/land mode new");
        addButton(startX, startY + 2 * (BTN_H + BTN_GAP), "§b切换模式 (/land mode old)", "/land mode old");
        addButton(startX, startY + 3 * (BTN_H + BTN_GAP), "§e查看领地信息 (/land info)", "/land info");
        addButton(startX, startY + 4 * (BTN_H + BTN_GAP), "§e列出所有领地 (/land list)", "/land list");
        addButton(startX, startY + 5 * (BTN_H + BTN_GAP), "§6权限管理 (/land permissions)", "/land permissions");
        addButton(startX, startY + 6 * (BTN_H + BTN_GAP), "§f查看经济总览 (/economy status)", "/economy status");

        // 右列
        int rx = startX + W + BTN_GAP;
        addButton(rx, startY, "§a创建领地（提示用 /land claim）", "/land ?");
        addButton(rx, startY + 1 * (BTN_H + BTN_GAP), "§c放弃母区域 (/land unclaim Block)", "/land unclaim Block");
        addButton(rx, startY + 2 * (BTN_H + BTN_GAP), "§a存入银行 100 (/land bank deposit 100)", "/land bank deposit 100");
        addButton(rx, startY + 3 * (BTN_H + BTN_GAP), "§c取出银行 100 (/land bank withdraw 100)", "/land bank withdraw 100");
        addButton(rx, startY + 4 * (BTN_H + BTN_GAP), "§f列出待审批 (/land join list)", "/land join list");
        addButton(rx, startY + 5 * (BTN_H + BTN_GAP), "§f飞地信息 (/land flyland info)", "/land flyland info");
        addButton(rx, startY + 6 * (BTN_H + BTN_GAP), "§c关闭 GUI", null, b -> onClose());

        addRenderableWidget(Button.builder(Component.literal("§c✕ 关闭"), b -> onClose())
                .bounds(width - 70, 8, 60, 16).build());
    }

    private void addButton(int x, int y, String label, String command) {
        addButton(x, y, label, command, null);
    }

    private void addButton(int x, int y, String label, String command, net.minecraft.client.gui.components.Button.OnPress custom) {
        net.minecraft.client.gui.components.Button.OnPress action = custom;
        if (action == null && command != null) {
            action = b -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
                }
            };
        }
        if (action == null) action = b -> {};
        addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(x, y, W, BTN_H).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);

        // 标题
        g.drawCenteredString(Minecraft.getInstance().font, "§e=== 领地经济 GUI ===",
                width / 2, 8, 0xFFFFFFFF);
        g.drawCenteredString(Minecraft.getInstance().font, "§7点击按钮执行对应指令（与命令等效）",
                width / 2, 18, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
