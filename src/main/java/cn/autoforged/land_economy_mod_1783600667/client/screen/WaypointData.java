package cn.autoforged.land_economy_mod_1783600667.client.screen;

/**
 * 路标数据模型。
 */
public class WaypointData {
    private String name;
    private final int blockX;
    private final int blockZ;
    private int color;
    private final String dimension;
    private final boolean isDeathPoint;
    private final long timestamp;

    public WaypointData(String name, int blockX, int blockZ, int color,
                        String dimension, boolean isDeathPoint) {
        this.name = name;
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.color = color;
        this.dimension = dimension;
        this.isDeathPoint = isDeathPoint;
        this.timestamp = System.currentTimeMillis();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBlockX() { return blockX; }
    public int getBlockZ() { return blockZ; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public String getDimension() { return dimension; }
    public boolean isDeathPoint() { return isDeathPoint; }
    public long getTimestamp() { return timestamp; }

    /** 获取区块坐标 */
    public int getChunkX() { return blockX >> 4; }
    public int getChunkZ() { return blockZ >> 4; }
}