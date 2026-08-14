package cn.autoforged.land_economy_mod_1783600667.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 区域命名输入界面。
 * 玩家首次购买区块时弹出，要求输入区域名称（2-32 字符）。
 * 确认后回调 onConfirm，取消后回调 onCancel。
 */
public class RegionNameInputScreen extends Screen {

    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private EditBox nameInput;

    private static final int W = 280, H = 90;

    public RegionNameInputScreen(Consumer<String> onConfirm, Runnable onCancel) {
        super(Component.literal("区域命名"));
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - W) / 2;
        int y = (height - H) / 2;

        nameInput = new EditBox(Minecraft.getInstance().font,
                x + 10, y + 30, W - 20, 16, Component.literal("区域名称"));
        nameInput.setMaxLength(32);
        nameInput.setFocused(true);
        addRenderableWidget(nameInput);

        addRenderableWidget(Button.builder(Component.literal("确认"), b -> {
            String name = nameInput.getValue().trim();
            if (name.length() >= 2 && name.length() <= 32) {
                onConfirm.accept(name);
            }
        }).bounds(x + W - 130, y + H - 28, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            onCancel.run();
        }).bounds(x + W - 60, y + H - 28, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        g.fill(0, 0, width, height, 0x80000000);

        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, 0xEE1A1A2E);
        g.renderOutline(x, y, W, H, 0xFFFFFFFF);

        g.drawCenteredString(Minecraft.getInstance().font, "§e首次购买，请为区域命名", width / 2, y + 10, 0xFFFFFFFF);
        g.drawString(Minecraft.getInstance().font, "§7名称 (2-32字符):", x + 10, y + 20, 0xFFFFFFFF);

        if (nameInput != null) nameInput.render(g, mouseX, mouseY, partialTick);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String name = nameInput.getValue().trim();
            if (name.length() >= 2 && name.length() <= 32) {
                onConfirm.accept(name);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onCancel.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}