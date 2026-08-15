package cn.autoforged.land_economy_mod_1783600667.client.plot;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.ClientKeyState;
import cn.autoforged.land_economy_mod_1783600667.client.integration.MapSelectionConfirmScreen;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SPlotAction;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SRequestPlotData;
import cn.autoforged.land_economy_mod_1783600667.plot.PlotAction;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * 回退地块地图界面（无第三方地图时使用）。
 *
 * 简化 2D 俯视图：纯色区块 + 边界线，WASD 平移，滚轮缩放，
 * 左键拖拽框选购买，右键拖拽框选放弃，回车确认。
 */
public final class FallbackPlotScreen extends Screen {

    // 视角状态
    private double centerX, centerZ;
    private double cellSize = 20.0;
    private static final double MIN_CELL = 4.0, MAX_CELL = 80.0;

    // 拖拽框选
    private boolean dragging = false;
    private int dragButton = -1;
    private int dragStartX, dragStartY;
    private int dragEndX, dragEndY;

    private final Set<Long> selectedBuy = new HashSet<>();
    private final Set<Long> selectedAbandon = new HashSet<>();

    private String dim;

    public FallbackPlotScreen() {
        super(Component.literal("地块地图"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            centerX = mc.player.getX();
            centerZ = mc.player.getZ();
        }
        if (mc.level != null) {
            dim = mc.level.dimension().location().toString();
        }
    }

    @Override
    protected void init() {
        if (dim == null && minecraft != null && minecraft.level != null) {
            dim = minecraft.level.dimension().location().toString();
        }
        requestChunks();
    }

    /** 区块数据更新回调 */
    public void onChunkDataUpdated() {
        // 数据已通过 PlotClientCache 更新，无需额外操作
    }

    /** 请求当前视图范围内的区块数据 */
    private void requestChunks() {
        if (dim == null) return;
        int viewCenterCX = (int) Math.floor(centerX / 16.0);
        int viewCenterCZ = (int) Math.floor(centerZ / 16.0);
        ModMessages.sendToServer(new PacketC2SRequestPlotData(viewCenterCX, viewCenterCZ));
    }

    /** 清除选区（操作成功回调） */
    public void clearSelection() {
        selectedBuy.clear();
        selectedAbandon.clear();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景
        g.fill(0, 0, width, height, 0xCC000000);

        int W = width, H = height;
        int cx0 = (int) Math.floor((centerX - (W / 2.0) * (16.0 / cellSize)) / 16.0);
        int cz0 = (int) Math.floor((centerZ - (H / 2.0) * (16.0 / cellSize)) / 16.0);
        int cx1 = (int) Math.ceil((centerX + (W / 2.0) * (16.0 / cellSize)) / 16.0);
        int cz1 = (int) Math.ceil((centerZ + (H / 2.0) * (16.0 / cellSize)) / 16.0);

        UUID me = minecraft.player != null ? minecraft.player.getUUID() : null;

        // 绘制区块
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                double px = W / 2.0 + (cx * 16 - centerX) * (cellSize / 16.0);
                double pz = H / 2.0 + (cz * 16 - centerZ) * (cellSize / 16.0);
                int x0 = (int) px, y0 = (int) pz;
                int sz = (int) cellSize;

                PlotClientCache.Cell cell = PlotClientCache.get(key);

                int fill;
                if (selectedBuy.contains(key) || selectedAbandon.contains(key)) {
                    fill = 0x60FFFFFF;
                } else if (cell == null) {
                    fill = 0x33000000;
                } else if (cell.isMine()) {
                    fill = 0x500000AA;
                } else if (cell.isOthers()) {
                    fill = 0x50AA0000;
                } else {
                    fill = 0x5000AA00;
                }
                g.fill(x0, y0, x0 + sz, y0 + sz, fill);

                int border;
                if (selectedBuy.contains(key) || selectedAbandon.contains(key)) {
                    border = 0xFFFFFFFF;
                } else if (cell == null) {
                    border = 0xFF666666;
                } else if (cell.isMine()) {
                    border = 0xFF0000FF;
                } else if (cell.isOthers()) {
                    border = 0xFFFF0000;
                } else {
                    border = 0xFF00AA00;
                }
                g.renderOutline(x0, y0, sz, sz, border);
            }
        }

        // 绘制拖拽选框
        if (dragging) {
            int x0 = Math.min(dragStartX, dragEndX);
            int y0 = Math.min(dragStartY, dragEndY);
            int x1 = Math.max(dragStartX, dragEndX);
            int y1 = Math.max(dragStartY, dragEndY);
            int color = dragButton == 0 ? 0x8800FF00 : 0x88FF0000;
            g.fill(x0, y0, x1, y1, 0x28FFFFFF);
            g.renderOutline(x0, y0, x1 - x0, y1 - y0, color);
        }

        // 提示文字
        g.drawString(font, "WASD=平移 | 滚轮=缩放 | 左键拖拽=购买 | 右键拖拽=放弃 | 回车=确认", 10, 10, 0xFFFFFFFF);
        g.drawString(font, "已选购买: " + selectedBuy.size() + " | 已选放弃: " + selectedAbandon.size(), 10, 22, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        double speed = 16.0 / cellSize * 8.0;
        if (ClientKeyState.isW()) centerZ -= speed;
        if (ClientKeyState.isS()) centerZ += speed;
        if (ClientKeyState.isA()) centerX -= speed;
        if (ClientKeyState.isD()) centerX += speed;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scroll) {
        double old = cellSize;
        cellSize = Math.max(MIN_CELL, Math.min(MAX_CELL, cellSize + scroll * 4));
        if (cellSize != old) requestChunks();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            dragging = true;
            dragButton = button;
            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            dragEndX = dragStartX;
            dragEndY = dragStartY;
            selectedBuy.clear();
            selectedAbandon.clear();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == dragButton) {
            dragEndX = (int) mouseX;
            dragEndY = (int) mouseY;
            updateSelection();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == dragButton) {
            dragging = false;
            dragEndX = (int) mouseX;
            dragEndY = (int) mouseY;
            updateSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSelection() {
        int W = width, H = height;
        int x0 = Math.min(dragStartX, dragEndX);
        int y0 = Math.min(dragStartY, dragEndY);
        int x1 = Math.max(dragStartX, dragEndX);
        int y1 = Math.max(dragStartY, dragEndY);

        int cx0 = (int) Math.floor((centerX + ((x0 - W / 2.0) * 16.0 / cellSize)) / 16.0);
        int cz0 = (int) Math.floor((centerZ + ((y0 - H / 2.0) * 16.0 / cellSize)) / 16.0);
        int cx1 = (int) Math.floor((centerX + ((x1 - W / 2.0) * 16.0 / cellSize)) / 16.0);
        int cz1 = (int) Math.floor((centerZ + ((y1 - H / 2.0) * 16.0 / cellSize)) / 16.0);

        selectedBuy.clear();
        selectedAbandon.clear();

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                PlotClientCache.Cell cell = PlotClientCache.get(key);
                if (dragButton == 0) { // 左键 = 购买
                    if (cell == null || (!cell.isMine() && !cell.isOthers())) {
                        selectedBuy.add(key);
                    }
                } else { // 右键 = 放弃
                    if (cell != null && cell.isMine()) {
                        selectedAbandon.add(key);
                    }
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirmSelection();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirmSelection() {
        if (dim == null) return;
        if (!selectedBuy.isEmpty()) {
            ModMessages.sendToServer(new PacketC2SPlotAction(PlotAction.Action.BUY, new ArrayList<>(selectedBuy), dim));
        }
        if (!selectedAbandon.isEmpty()) {
            ModMessages.sendToServer(new PacketC2SPlotAction(PlotAction.Action.ABANDON, new ArrayList<>(selectedAbandon), dim));
        }
        selectedBuy.clear();
        selectedAbandon.clear();
    }

    @Override
    public void onClose() {
        PlotMapHandler.closeMap();
        super.onClose();
    }
}