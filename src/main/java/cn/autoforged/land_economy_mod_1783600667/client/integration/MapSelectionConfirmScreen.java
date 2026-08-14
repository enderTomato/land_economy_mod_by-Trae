package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.client.gui.RegionNameInputScreen;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SPlotAction;
import cn.autoforged.land_economy_mod_1783600667.plot.PlotAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方地图中选框完成后的确认界面。
 * 显示待购买/放弃区块数量，回车确认后发送网络包。
 * 若玩家是首次购买，先弹出命名界面。
 */
public class MapSelectionConfirmScreen extends Screen {

    private final List<Long> buyChunks;
    private final List<Long> abandonChunks;
    private final String dimensionId;

    public MapSelectionConfirmScreen(List<Long> buyChunks, List<Long> abandonChunks, String dim) {
        super(Component.literal("确认地块操作"));
        this.buyChunks = buyChunks;
        this.abandonChunks = abandonChunks;
        this.dimensionId = dim;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);

        int bw = 320, bh = 130;
        int bx = (width - bw) / 2, by = (height - bh) / 2;
        g.fill(bx, by, bx + bw, by + bh, 0xEE202020);
        g.renderOutline(bx, by, bw, bh, 0xFFFFFFFF);

        var font = Minecraft.getInstance().font;
        int ty = by + 10;
        g.drawCenteredString(font, "§e=== 确认地块操作 ===", width / 2, ty, 0xFFFFFFFF); ty += 16;

        if (!buyChunks.isEmpty()) {
            g.drawCenteredString(font, "§a待购买 " + buyChunks.size() + " 区块", width / 2, ty, 0xFFFFFFFF); ty += 14;
        }
        if (!abandonChunks.isEmpty()) {
            g.drawCenteredString(font, "§c待放弃 " + abandonChunks.size() + " 区块", width / 2, ty, 0xFFFFFFFF); ty += 14;
        }

        g.drawCenteredString(font, "§a[回车] 确认  |  §c[ESC] 取消", width / 2, by + bh - 18, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeConfirmed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void executeConfirmed() {
        if (!buyChunks.isEmpty()) {
            // 首次购买检查
            if (!PlotClientCache.hasMine()) {
                Minecraft.getInstance().setScreen(new RegionNameInputScreen(
                    name -> {
                        ModMessages.sendToServer(new PacketC2SPlotAction(
                                PlotAction.Action.BUY, new ArrayList<>(buyChunks), dimensionId, name));
                        Minecraft.getInstance().setScreen(null);
                    },
                    () -> Minecraft.getInstance().setScreen(null)
                ));
                return;
            }
            ModMessages.sendToServer(new PacketC2SPlotAction(
                    PlotAction.Action.BUY, new ArrayList<>(buyChunks), dimensionId));
        }
        if (!abandonChunks.isEmpty()) {
            ModMessages.sendToServer(new PacketC2SPlotAction(
                    PlotAction.Action.ABANDON, new ArrayList<>(abandonChunks), dimensionId));
        }
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}