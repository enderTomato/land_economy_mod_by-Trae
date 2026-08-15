package cn.autoforged.land_economy_mod_1783600667.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 区块认领地图设置界面。
 */
public class ChunkClaimSettingsScreen extends Screen {

    private final ChunkClaimScreen parent;
    private Checkbox showGrid;
    private Checkbox showWaypoints;
    private Checkbox showDeathPoints;
    private Checkbox showEntities;

    public ChunkClaimSettingsScreen(ChunkClaimScreen parent) {
        super(Component.literal("地图设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;

        showGrid = new Checkbox(centerX - 100, y, 200, 20,
                Component.literal("显示区块网格"), parent.isShowGrid());
        addRenderableWidget(showGrid);
        y += 30;

        showWaypoints = new Checkbox(centerX - 100, y, 200, 20,
                Component.literal("显示路标"), parent.isShowWaypoints());
        addRenderableWidget(showWaypoints);
        y += 30;

        showDeathPoints = new Checkbox(centerX - 100, y, 200, 20,
                Component.literal("显示死亡点"), parent.isShowDeathPoints());
        addRenderableWidget(showDeathPoints);
        y += 30;

        showEntities = new Checkbox(centerX - 100, y, 200, 20,
                Component.literal("显示实体图标"), parent.isShowEntities());
        addRenderableWidget(showEntities);
        y += 40;

        addRenderableWidget(Button.builder(Component.literal("完成"), btn -> {
            parent.setShowGrid(showGrid.selected());
            parent.setShowWaypoints(showWaypoints.selected());
            parent.setShowDeathPoints(showDeathPoints.selected());
            parent.setShowEntities(showEntities.selected());
            this.onClose();
        }).bounds(centerX - 50, y, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}