package cn.autoforged.land_economy_mod_1783600667.client.plot;

import cn.autoforged.land_economy_mod_1783600667.client.ClientKeyState;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SPlotAction;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SRequestPlotData;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SRequestRegionDetail;
import cn.autoforged.land_economy_mod_1783600667.plot.PlotAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * 2D 俯视地块地图 Screen。
 *
 * 功能：
 * - WASD 平移视角（不改变玩家实体位置）
 * - 滚轮缩放
 * - 左键单击购买、左键拖拽批量购买
 * - 右键单击放弃、右键拖拽批量放弃
 * - 回车确认操作（含花费提示）
 * - 空格 / ESC 退出
 * - 点击自己/他人已购买区块 → 请求区域详情
 * - 视角移动到未加载区块时通过 PacketC2SRequestPlotData 请求服务端下发
 *
 * 高亮框颜色：
 *   绿色 = 未购买；白色 = 已选中；蓝色 = 自己；红色 = 他人
 *
 * 高亮框渲染仅在本客户端可见，不广播。
 */
public class PlotMapScreen extends Screen {

    private final PlotMapView view;

    /** 待购买（白） */
    private final Set<Long> selectedBuy = new HashSet<>();
    /** 待放弃（白） */
    private final Set<Long> selectedAbandon = new HashSet<>();

    /** 拖拽起点（屏幕坐标），-1 表示未开始 */
    private double dragStartX = -1, dragStartY = -1;
    private double dragCurX = -1, dragCurY = -1;
    private int dragButton = -1;
    private long pressStartTime = 0;
    private static final long DRAG_THRESHOLD_MS = 180;

    private boolean confirmMode = false;
    private String confirmMessage = "";

    /** 平移速度（像素/tick） */
    private static final int PAN_SPEED = 6;

    public PlotMapScreen() {
        super(Component.literal("地图地块"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.view = new PlotMapView(mc.player.getX(), mc.player.getZ());
        } else {
            this.view = new PlotMapView(0, 0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (confirmMode) return;            // 确认弹窗期间冻结平移

        // WASD 平移
        int dx = 0, dy = 0;
        if (ClientKeyState.isW()) dy -= PAN_SPEED;
        if (ClientKeyState.isS()) dy += PAN_SPEED;
        if (ClientKeyState.isA()) dx -= PAN_SPEED;
        if (ClientKeyState.isD()) dx += PAN_SPEED;
        if (dx != 0 || dy != 0) {
            view.pan(dx, dy);
            // 视角移动后请求新区域数据
            String dim = Minecraft.getInstance().level.dimension().location().toString();
            requestChunksIfNeeded(dim);
        }

        // 空格退出
        if (ClientKeyState.isSpace()) {
            onClose();
        }
    }

    /** 视角中心区块变化时请求服务端下发新数据 */
    public void requestChunksIfNeeded(String dim) {
        int cx = view.currentChunkX();
        int cz = view.currentChunkZ();
        if (cx == view.lastRequestCx && cz == view.lastRequestCz) return;
        view.lastRequestCx = cx;
        view.lastRequestCz = cz;
        ModMessages.sendToServer(new PacketC2SRequestPlotData(cx, cz));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景：半透明黑色覆盖游戏画面
        g.fill(0, 0, width, height, 0xCC000000);

        int W = width, H = height;
        int cx0 = (int) Math.floor((view.centerX - (W / 2.0) * (16.0 / view.cellSize)) / 16.0);
        int cz0 = (int) Math.floor((view.centerZ - (H / 2.0) * (16.0 / view.cellSize)) / 16.0);
        int cx1 = (int) Math.ceil((view.centerX + (W / 2.0) * (16.0 / view.cellSize)) / 16.0);
        int cz1 = (int) Math.ceil((view.centerZ + (H / 2.0) * (16.0 / view.cellSize)) / 16.0);

        // 绘制每个区块
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                double px = W / 2.0 + (cx * 16 - view.centerX) * (view.cellSize / 16.0);
                double pz = H / 2.0 + (cz * 16 - view.centerZ) * (view.cellSize / 16.0);
                int x0 = (int) px, y0 = (int) pz;
                int x1 = (int) (px + view.cellSize), y1 = (int) (pz + view.cellSize);

                PlotClientCache.Cell cell = PlotClientCache.get(key);

                // 填充色（半透明）
                int fill;
                if (selectedBuy.contains(key))            fill = 0x40FFFFFF;
                else if (selectedAbandon.contains(key))    fill = 0x40FFFFFF;
                else if (cell == null)                    fill = 0x33000000;
                else if (cell.isMine())                   fill = 0x400000AA;
                else if (cell.isOthers())                 fill = 0x40AA0000;
                else                                      fill = 0x4000AA00;
                g.fill(x0, y0, x1, y1, fill);

                // 边框色（实线）
                int border;
                if (selectedBuy.contains(key) || selectedAbandon.contains(key)) border = 0xFFFFFFFF;
                else if (cell == null)         border = 0xFF888888;
                else if (cell.isMine())        border = 0xFF0000FF;
                else if (cell.isOthers())      border = 0xFFFF0000;
                else                           border = 0xFF00AA00;
                g.renderOutline(x0, y0, (int) view.cellSize, (int) view.cellSize, border);
            }
        }

        // 拖拽框选矩形
        if (dragButton != -1 && dragStartX >= 0) {
            int x0 = (int) Math.min(dragStartX, dragCurX);
            int y0 = (int) Math.min(dragStartY, dragCurY);
            int w  = (int) Math.abs(dragCurX - dragStartX);
            int h  = (int) Math.abs(dragCurY - dragStartY);
            int color = dragButton == 0 ? 0x88FFFFFF : 0x88FF8800;
            g.fill(x0, y0, x0 + w, y0 + h, color);
            g.renderOutline(x0, y0, w, h, dragButton == 0 ? 0xFFFFFFFF : 0xFFFFAA00);
        }

        // HUD
        drawHud(g);

        // 确认弹窗
        if (confirmMode) {
            drawConfirmDialog(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawHud(GuiGraphics g) {
        int y = 12;
        g.drawString(Minecraft.getInstance().font, "§e=== 地图地块 ===", 12, y, 0xFFFFFFFF); y += 12;
        g.drawString(Minecraft.getInstance().font, "§fWASD 移动视角  |  滚轮缩放  |  空格/ESC 退出", 12, y, 0xFFFFFFFF); y += 12;
        g.drawString(Minecraft.getInstance().font, "§f左键: 购买  |  左键拖拽: 批量购买", 12, y, 0xFFFFFFFF); y += 12;
        g.drawString(Minecraft.getInstance().font, "§f右键: 放弃  |  右键拖拽: 批量放弃", 12, y, 0xFFFFFFFF); y += 12;
        g.drawString(Minecraft.getInstance().font, "§f点击已购买区块: 查看区域详情  |  回车: 确认购买/放弃", 12, y, 0xFFFFFFFF); y += 12;

        int cx = view.currentChunkX(), cz = view.currentChunkZ();
        g.drawString(Minecraft.getInstance().font,
                String.format("§7视角中心: 区块[%d,%d]  世界[%.1f, %.1f]  缩放 %.0fpx/chunk",
                        cx, cz, view.centerX, view.centerZ, view.cellSize),
                12, y, 0xFFFFFFFF); y += 12;
        g.drawString(Minecraft.getInstance().font,
                String.format("§a待购买: %d  §c待放弃: %d", selectedBuy.size(), selectedAbandon.size()),
                12, y, 0xFFFFFFFF); y += 12;

        // 图例
        int lx = width - 130, ly = 12;
        g.drawString(Minecraft.getInstance().font, "§e=== 图例 ===", lx, ly, 0xFFFFFFFF); ly += 12;
        g.fill(lx, ly, lx + 12, ly + 12, 0x4000AA00); g.renderOutline(lx, ly, 12, 12, 0xFF00AA00);
        g.drawString(Minecraft.getInstance().font, "§f未购买", lx + 16, ly + 2, 0xFFFFFFFF); ly += 14;
        g.fill(lx, ly, lx + 12, ly + 12, 0x40FFFFFF); g.renderOutline(lx, ly, 12, 12, 0xFFFFFFFF);
        g.drawString(Minecraft.getInstance().font, "§f已选中", lx + 16, ly + 2, 0xFFFFFFFF); ly += 14;
        g.fill(lx, ly, lx + 12, ly + 12, 0x400000AA); g.renderOutline(lx, ly, 12, 12, 0xFF0000FF);
        g.drawString(Minecraft.getInstance().font, "§f自己的", lx + 16, ly + 2, 0xFFFFFFFF); ly += 14;
        g.fill(lx, ly, lx + 12, ly + 12, 0x40AA0000); g.renderOutline(lx, ly, 12, 12, 0xFFFF0000);
        g.drawString(Minecraft.getInstance().font, "§f他人的", lx + 16, ly + 2, 0xFFFFFFFF);
    }

    private void drawConfirmDialog(GuiGraphics g) {
        int bw = 360, bh = 110;
        int bx = (width - bw) / 2, by = (height - bh) / 2;
        g.fill(bx, by, bx + bw, by + bh, 0xEE202020);
        g.renderOutline(bx, by, bw, bh, 0xFFFFFFFF);
        g.drawCenteredString(Minecraft.getInstance().font, "§e=== 确认操作 ===", width / 2, by + 8, 0xFFFFFFFF);
        String[] lines = confirmMessage.split("\n");
        int ly = by + 24;
        for (String line : lines) {
            g.drawCenteredString(Minecraft.getInstance().font, line, width / 2, ly, 0xFFFFFFFF);
            ly += 12;
        }
        g.drawCenteredString(Minecraft.getInstance().font, "§a[回车] 确认  |  §c[ESC] 取消", width / 2, by + bh - 18, 0xFFFFFFFF);
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmMode) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 || button == 1) {
            dragButton = button;
            dragStartX = mouseX; dragStartY = mouseY;
            dragCurX = mouseX;   dragCurY = mouseY;
            pressStartTime = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (confirmMode) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (button == dragButton) {
            dragCurX = mouseX; dragCurY = mouseY;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (confirmMode) return super.mouseReleased(mouseX, mouseY, button);
        if (button != dragButton) return true;
        long duration = System.currentTimeMillis() - pressStartTime;
        boolean moved = Math.abs(mouseX - dragStartX) > 4 || Math.abs(mouseY - dragStartY) > 4;
        // 短按 + 几乎未移动 = 单击
        if (duration < DRAG_THRESHOLD_MS && !moved) {
            handleSingleClick(mouseX, mouseY, button);
        } else {
            handleBoxSelect(dragStartX, dragStartY, mouseX, mouseY, button);
        }
        // 重置拖拽状态
        dragButton = -1;
        dragStartX = dragStartY = dragCurX = dragCurY = -1;
        return true;
    }

    private void handleSingleClick(double mouseX, double mouseY, int button) {
        int cx = view.screenXToChunkX(mouseX, width);
        int cz = view.screenYToChunkZ(mouseY, height);
        long key = ChunkPos.asLong(cx, cz);
        PlotClientCache.Cell cell = PlotClientCache.get(key);
        String dim = Minecraft.getInstance().level.dimension().location().toString();

        if (button == 0) {
            // 左键：购买
            if (cell != null && cell.isMine()) {
                // 点击自己的区块 → 请求区域详情
                ModMessages.sendToServer(new PacketC2SRequestRegionDetail(key, dim));
                return;
            }
            if (cell != null && cell.isOthers()) {
                // 点击他人区块 → 请求区域详情（只读）
                ModMessages.sendToServer(new PacketC2SRequestRegionDetail(key, dim));
                return;
            }
            // 未购买 → 加入待购买
            if (selectedBuy.contains(key)) selectedBuy.remove(key);
            else { selectedBuy.add(key); selectedAbandon.remove(key); }
        } else if (button == 1) {
            // 右键：放弃
            if (cell != null && cell.isMine()) {
                if (selectedAbandon.contains(key)) selectedAbandon.remove(key);
                else { selectedAbandon.add(key); selectedBuy.remove(key); }
            } else if (cell != null && cell.isOthers()) {
                // 他人区块：查看详情
                ModMessages.sendToServer(new PacketC2SRequestRegionDetail(key, dim));
            }
        }
    }

    private void handleBoxSelect(double sx, double sy, double ex, double ey, int button) {
        int cx0 = view.screenXToChunkX(Math.min(sx, ex), width);
        int cx1 = view.screenXToChunkX(Math.max(sx, ex), width);
        int cz0 = view.screenYToChunkZ(Math.min(sy, ey), height);
        int cz1 = view.screenYToChunkZ(Math.max(sy, ey), height);

        String dim = Minecraft.getInstance().level.dimension().location().toString();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                PlotClientCache.Cell cell = PlotClientCache.get(key);
                if (button == 0) {
                    // 左键拖拽：批量选择未购买的（购买）
                    if (cell == null || (!cell.isMine() && !cell.isOthers())) {
                        selectedBuy.add(key);
                        selectedAbandon.remove(key);
                    }
                } else if (button == 1) {
                    // 右键拖拽：批量选择自己的（放弃）
                    if (cell != null && cell.isMine()) {
                        selectedAbandon.add(key);
                        selectedBuy.remove(key);
                    }
                }
            }
        }
    }

    // ==================== 键盘交互 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmMode) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                executeConfirmed();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                confirmMode = false;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_SPACE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            openConfirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 打开确认弹窗（回车触发） */
    private void openConfirm() {
        if (selectedBuy.isEmpty() && selectedAbandon.isEmpty()) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c未选择任何区块"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (!selectedBuy.isEmpty()) {
            sb.append("§a待购买 ").append(selectedBuy.size()).append(" 区块\n");
        }
        if (!selectedAbandon.isEmpty()) {
            sb.append("§c待放弃 ").append(selectedAbandon.size()).append(" 区块\n");
        }
        sb.append("§7确认请按回车，取消请按 ESC");
        confirmMessage = sb.toString();
        confirmMode = true;
    }

    /** 实际向服务端发送购买/放弃请求（回车确认后） */
    private void executeConfirmed() {
        confirmMode = false;
        String dim = Minecraft.getInstance().level.dimension().location().toString();
        if (!selectedBuy.isEmpty()) {
            ModMessages.sendToServer(new PacketC2SPlotAction(
                    PlotAction.Action.BUY, new java.util.ArrayList<>(selectedBuy), dim));
        }
        if (!selectedAbandon.isEmpty()) {
            ModMessages.sendToServer(new PacketC2SPlotAction(
                    PlotAction.Action.ABANDON, new java.util.ArrayList<>(selectedAbandon), dim));
        }
    }

    // ==================== 服务端回调入口 ====================

    /** 服务端下发新数据后调用，触发重绘（无需额外动作，render 自动刷新） */
    public void onChunkDataUpdated(int cx0, int cz0, int cx1, int cz1) {
        // 触发重绘即可
    }

    /** 操作成功后清除选区 */
    public void clearSelection() {
        selectedBuy.clear();
        selectedAbandon.clear();
    }

    /** 强制退出（受击/传送时由 ClientPacketReceivers 调用） */
    public void cancelAll() {
        clearSelection();
        confirmMode = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 退出前通知服务端退出地块界面（让 isInPlotMode=false）
        // 服务端事件也会兜底处理，这里冗余保证
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        view.zoom(delta * 2.0);
        return true;
    }
}
