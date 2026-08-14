package cn.autoforged.land_economy_mod_1783600667.client.plot;

import net.minecraft.world.level.ChunkPos;

/**
 * 地块地图视角状态：屏幕中心对应的"世界坐标"，以及每区块像素尺寸（缩放）。
 *
 * 视角移动只改变本对象中的 centerX/centerZ，不会修改玩家实体位置。
 * 客户端在 tick 中根据 WASD 调整本对象，并根据中心点判断是否需要向服务端请求新的区块数据。
 */
public final class PlotMapView {

    /** 视角中心对应的世界坐标（X/Z） */
    public double centerX;
    public double centerZ;

    /** 每区块占据的像素尺寸（缩放）：越大越放大 */
    public double cellSize = 24.0;

    /** 上一次请求过数据的区块中心，用于减少请求频率 */
    public int lastRequestCx = Integer.MIN_VALUE;
    public int lastRequestCz = Integer.MIN_VALUE;

    public PlotMapView(double cx, double cz) {
        this.centerX = cx;
        this.centerZ = cz;
    }

    /** WASD 平移：每 tick 移动若干像素（按 cellSize 比例） */
    public void pan(int dxPx, int dyPx) {
        double factor = 16.0 / cellSize;
        centerX += dxPx * factor;
        centerZ += dyPx * factor;
    }

    /** 滚轮缩放：在合理范围内调整 cellSize */
    public void zoom(double delta) {
        cellSize = Math.max(8.0, Math.min(64.0, cellSize - delta));
    }

    /** 屏幕像素坐标 → 区块坐标 */
    public int screenXToChunkX(double screenX, int screenWidth) {
        double worldX = centerX + (screenX - screenWidth / 2.0) * (16.0 / cellSize);
        return (int) Math.floor(worldX / 16.0);
    }

    public int screenYToChunkZ(double screenY, int screenHeight) {
        double worldZ = centerZ + (screenY - screenHeight / 2.0) * (16.0 / cellSize);
        return (int) Math.floor(worldZ / 16.0);
    }

    public int currentChunkX() { return (int) Math.floor(centerX / 16.0); }
    public int currentChunkZ() { return (int) Math.floor(centerZ / 16.0); }

    public long currentChunkKey() {
        return ChunkPos.asLong(currentChunkX(), currentChunkZ());
    }
}
