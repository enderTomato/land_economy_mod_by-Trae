package cn.autoforged.land_economy_mod_1783600667.client.screen;

import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * FTB Chunks 风格区块认领地图。
 * 功能：区块网格渲染、颜色编码、左键框选购买、右键框选放弃、
 * Shift+左键强制加载、路标、死亡点、实体图标、左侧面板。
 */
public class ChunkClaimScreen extends Screen {

    // ---- 视图状态 ----
    private int centerCX, centerCZ;       // 视图中心区块坐标
    private int blockSize = 12;           // 每个区块在屏幕上的像素大小
    private static final int MIN_BLOCK = 4;
    private static final int MAX_BLOCK = 32;

    // ---- 选区状态 ----
    private boolean isDragging = false;
    private boolean isRightDrag = false;   // true=放弃, false=购买
    private int dragStartCX, dragStartCZ;
    private int dragEndCX, dragEndCZ;
    private final Set<Long> selectedChunks = new HashSet<>();

    // ---- 显示设置 ----
    private boolean showGrid = true;
    private boolean showWaypoints = true;
    private boolean showDeathPoints = true;
    private boolean showEntities = true;

    // ---- 数据请求 ----
    private boolean dataRequested = false;

    // ---- 缩放记忆 ----
    private static int rememberedZoom = 12;

    public ChunkClaimScreen() {
        super(Component.literal("区块认领地图"));
        this.blockSize = rememberedZoom;
        if (Minecraft.getInstance().player != null) {
            var pos = Minecraft.getInstance().player.blockPosition();
            this.centerCX = pos.getX() >> 4;
            this.centerCZ = pos.getZ() >> 4;
        }
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        // 左侧面板按钮
        int btnX = 5;
        int btnY = 30;
        int btnW = 90;
        int btnH = 20;

        // 认领模式按钮
        addRenderableWidget(Button.builder(Component.literal("认领区块"), btn -> {})
                .bounds(btnX, btnY, btnW, btnH).build());
        btnY += 25;

        // 设置按钮
        addRenderableWidget(Button.builder(Component.literal("设置"), btn -> {
                    if (minecraft != null) minecraft.setScreen(new ChunkClaimSettingsScreen(this));
                })
                .bounds(btnX, btnY, btnW, btnH).build());
        btnY += 25;

        // 维度切换按钮
        addRenderableWidget(Button.builder(Component.literal("维度"), btn -> {})
                .bounds(btnX, btnY, btnW, btnH).build());

        requestChunkData();
    }

    @Override
    public void onClose() {
        rememberedZoom = blockSize;
        super.onClose();
    }

    @Override
    public void tick() {
        if (!dataRequested) {
            requestChunkData();
            dataRequested = true;
        }
    }

    // ==================== 数据 ====================

    private void requestChunkData() {
        ModMessages.sendToServer(new PacketC2SRequestPlotData(centerCX, centerCZ));
    }

    public void onChunkDataUpdated() {
        // 缓存已由 ClientPacketReceivers 更新，此处触发重绘
        dataRequested = false;
    }

    public void clearSelection() {
        selectedChunks.clear();
        isDragging = false;
        requestChunkData();
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 深色背景
        renderBackground(guiGraphics);

        // 计算可见区块范围
        int halfW = (width / 2) / blockSize + 2;
        int halfH = (height / 2) / blockSize + 2;
        int cx0 = centerCX - halfW;
        int cz0 = centerCZ - halfH;
        int cx1 = centerCX + halfW;
        int cz1 = centerCZ + halfH;

        // 屏幕中心偏移
        int screenCX = width / 2;
        int screenCZ = height / 2;

        // 渲染区块网格
        renderChunkGrid(cx0, cz0, cx1, cz1, screenCX, screenCZ);

        // 渲染边框
        if (showGrid) {
            renderGridLines(cx0, cz0, cx1, cz1, screenCX, screenCZ);
        }

        // 渲染路标
        if (showWaypoints) {
            renderWaypoints(guiGraphics, screenCX, screenCZ);
        }

        // 渲染玩家位置
        renderPlayerPosition(guiGraphics, screenCX, screenCZ);

        // 渲染HUD
        renderHUD(guiGraphics);

        // 渲染按钮
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderChunkGrid(int cx0, int cz0, int cx1, int cz1,
                                  int screenCX, int screenCZ) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                int x = screenCX + (cx - centerCX) * blockSize;
                int y = screenCZ + (cz - centerCZ) * blockSize;

                int color;
                if (selectedChunks.contains(key)) {
                    color = 0x80FFFFFF; // 白色 = 选中
                } else {
                    ChunkClaimCache.CacheEntry entry = ChunkClaimCache.get(key);
                    if (entry != null) {
                        color = entry.colorARGB();
                    } else {
                        // 未缓存的区块用灰绿色
                        color = 0x1500AA00;
                    }
                }

                int a = (color >> 24) & 0xFF;
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                buf.vertex(x, y, 0).color(r, g, b, a).endVertex();
                buf.vertex(x + blockSize, y, 0).color(r, g, b, a).endVertex();
                buf.vertex(x + blockSize, y + blockSize, 0).color(r, g, b, a).endVertex();
                buf.vertex(x, y + blockSize, 0).color(r, g, b, a).endVertex();
            }
        }
        tess.end();
        RenderSystem.disableBlend();
    }

    private void renderGridLines(int cx0, int cz0, int cx1, int cz1,
                                  int screenCX, int screenCZ) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        int gridColor = 0x40333355;
        int ra = (gridColor >> 24) & 0xFF;
        int rr = (gridColor >> 16) & 0xFF;
        int rg = (gridColor >> 8) & 0xFF;
        int rb = gridColor & 0xFF;

        for (int cx = cx0; cx <= cx1 + 1; cx++) {
            int x = screenCX + (cx - centerCX) * blockSize;
            int y0 = screenCZ + (cz0 - centerCZ) * blockSize;
            int y1 = screenCZ + (cz1 + 1 - centerCZ) * blockSize;
            buf.vertex(x, y0, 0).color(rr, rg, rb, ra).endVertex();
            buf.vertex(x, y1, 0).color(rr, rg, rb, ra).endVertex();
        }
        for (int cz = cz0; cz <= cz1 + 1; cz++) {
            int y = screenCZ + (cz - centerCZ) * blockSize;
            int x0 = screenCX + (cx0 - centerCX) * blockSize;
            int x1 = screenCX + (cx1 + 1 - centerCX) * blockSize;
            buf.vertex(x0, y, 0).color(rr, rg, rb, ra).endVertex();
            buf.vertex(x1, y, 0).color(rr, rg, rb, ra).endVertex();
        }
        tess.end();
        RenderSystem.disableBlend();
    }

    private void renderWaypoints(GuiGraphics guiGraphics, int screenCX, int screenCZ) {
        String dim = minecraft.player != null
                ? minecraft.player.level().dimension().location().toString() : "";
        for (WaypointData wp : WaypointManager.getInDimension(dim)) {
            int cx = wp.getChunkX();
            int cz = wp.getChunkZ();
            int x = screenCX + (cx - centerCX) * blockSize + blockSize / 2;
            int y = screenCZ + (cz - centerCZ) * blockSize + blockSize / 2;

            int color = wp.getColor();
            // 绘制小旗标
            guiGraphics.fill(x - 2, y - 6, x + 2, y + 4, 0xFF000000 | color);
            // 名称
            guiGraphics.drawCenteredString(font, wp.getName(), x, y - 10, 0xFFFFFF);
        }
    }

    private void renderPlayerPosition(GuiGraphics guiGraphics, int screenCX, int screenCZ) {
        if (minecraft.player == null) return;
        Vec3 pos = minecraft.player.position();
        int px = (int) ((pos.x / 16.0 - centerCX) * blockSize + screenCX);
        int py = (int) ((pos.z / 16.0 - centerCZ) * blockSize + screenCZ);

        // 白色十字
        guiGraphics.fill(px - 3, py - 1, px + 4, py + 2, 0xFFFFFFFF);
        guiGraphics.fill(px - 1, py - 3, px + 2, py + 4, 0xFFFFFFFF);
    }

    private void renderHUD(GuiGraphics guiGraphics) {
        // 顶部：坐标
        String coords = "中心: [" + centerCX + ", " + centerCZ + "]";
        guiGraphics.drawString(font, coords, 5, 5, 0xFFFFFF);

        // 右上：缩放级别
        String zoom = "缩放: " + blockSize + "px";
        guiGraphics.drawString(font, zoom, width - font.width(zoom) - 5, 5, 0xAAAAAA);

        // 底部：状态
        if (!selectedChunks.isEmpty()) {
            String status = "已选: " + selectedChunks.size() + " 区块 | 回车确认 | ESC取消";
            guiGraphics.drawString(font, status, 5, height - 15, 0xFFFF55);
        }
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int screenCX = width / 2;
        int screenCZ = height / 2;

        int cx = centerCX + (int) ((mouseX - screenCX) / blockSize);
        int cz = centerCZ + (int) ((mouseY - screenCZ) / blockSize);

        if (button == 0) { // 左键
            if (hasShiftDown()) {
                // Shift+左键 = 切换强制加载
                toggleForceLoad(cx, cz);
            } else {
                isDragging = true;
                isRightDrag = false;
                dragStartCX = cx;
                dragStartCZ = cz;
                dragEndCX = cx;
                dragEndCZ = cz;
                updateSelection();
            }
            return true;
        } else if (button == 1) { // 右键
            isDragging = true;
            isRightDrag = true;
            dragStartCX = cx;
            dragStartCZ = cz;
            dragEndCX = cx;
            dragEndCZ = cz;
            updateSelection();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            int screenCX = width / 2;
            int screenCZ = height / 2;
            dragEndCX = centerCX + (int) ((mouseX - screenCX) / blockSize);
            dragEndCZ = centerCZ + (int) ((mouseY - screenCZ) / blockSize);
            updateSelection();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSelection() {
        selectedChunks.clear();
        int minCX = Math.min(dragStartCX, dragEndCX);
        int maxCX = Math.max(dragStartCX, dragEndCX);
        int minCZ = Math.min(dragStartCZ, dragEndCZ);
        int maxCZ = Math.max(dragStartCZ, dragEndCZ);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                selectedChunks.add(ChunkPos.asLong(cx, cz));
            }
        }
    }

    private void toggleForceLoad(int cx, int cz) {
        if (!ModConfig.COMMON.chunkClaimForceLoadEnabled.get()) return;
        long key = ChunkPos.asLong(cx, cz);
        if (minecraft.player != null && ChunkClaimCache.isMine(key, minecraft.player.getUUID())) {
            // 发送切换强制加载请求
            ModMessages.sendToServer(new PacketC2SChunkClaimAction(
                    PacketC2SChunkClaimAction.Action.TOGGLE_FORCELOAD,
                    List.of(key),
                    minecraft.player.level().dimension().location().toString()));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            blockSize = Math.min(MAX_BLOCK, blockSize + 2);
        } else {
            blockSize = Math.max(MIN_BLOCK, blockSize - 2);
        }
        requestChunkData();
        return true;
    }

    // ==================== 键盘交互 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        int panSpeed = 2;
        switch (keyCode) {
            case 87, 265 -> { centerCZ -= panSpeed; requestChunkData(); return true; } // W / Up
            case 83, 264 -> { centerCZ += panSpeed; requestChunkData(); return true; } // S / Down
            case 65, 263 -> { centerCX -= panSpeed; requestChunkData(); return true; } // A / Left
            case 68, 262 -> { centerCX += panSpeed; requestChunkData(); return true; } // D / Right
            case 257, 335 -> { // Enter / Numpad Enter
                confirmAction();
                return true;
            }
            case 256 -> { // Esc
                this.onClose();
                return true;
            }
        }
        return false;
    }

    private void confirmAction() {
        if (selectedChunks.isEmpty()) return;
        if (minecraft.player == null) return;

        String dim = minecraft.player.level().dimension().location().toString();
        List<Long> chunks = new ArrayList<>(selectedChunks);

        if (isRightDrag) {
            // 放弃
            ModMessages.sendToServer(new PacketC2SChunkClaimAction(
                    PacketC2SChunkClaimAction.Action.UNCLAIM, chunks, dim));
        } else {
            // 购买
            ModMessages.sendToServer(new PacketC2SChunkClaimAction(
                    PacketC2SChunkClaimAction.Action.CLAIM, chunks, dim));
        }
        selectedChunks.clear();
    }

    // ==================== 公共接口 ====================

    @Override
    public boolean isPauseScreen() { return false; }

    // 设置 getter/setter
    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean v) { showGrid = v; }
    public boolean isShowWaypoints() { return showWaypoints; }
    public void setShowWaypoints(boolean v) { showWaypoints = v; }
    public boolean isShowDeathPoints() { return showDeathPoints; }
    public void setShowDeathPoints(boolean v) { showDeathPoints = v; }
    public boolean isShowEntities() { return showEntities; }
    public void setShowEntities(boolean v) { showEntities = v; }
}