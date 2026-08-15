package cn.autoforged.land_economy_mod_1783600667.client.plot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DynMap 风格的俯视地形颜色渲染器。
 *
 * 采样客户端已加载区块的顶层方块，通过 BlockColors 获取真实颜色，
 * 回退到生物群系颜色。结果缓存 256 个区块，超出时 LRU 淘汰。
 *
 * 仅在 cellSize >= 16 时启用地形渲染；更小缩放时回退纯色以提高性能。
 */
public final class PlotMapTerrainRenderer {

    private static final int CACHE_MAX = 256;

    /** 线程安全的 LRU 缓存：chunkKey -> 16x16 ARGB 颜色数组 */
    private static final Map<Long, int[]> TERRAIN_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(CACHE_MAX + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    private PlotMapTerrainRenderer() {}

    /**
     * 对指定区块采样地形颜色。
     * @return 16x16 的 ARGB 颜色数组；区块未加载时返回 null
     */
    public static int[] sampleTerrain(ClientLevel level, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        int[] cached = TERRAIN_CACHE.get(key);
        if (cached != null) return cached;

        if (!level.hasChunk(cx, cz)) return null;

        LevelChunk chunk = level.getChunk(cx, cz);
        var blockColors = Minecraft.getInstance().getBlockColors();
        int[] colors = new int[256];
        int minY = chunk.getMinBuildHeight();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                        (cx << 4) + lx, y, (cz << 4) + lz);
                BlockState state = chunk.getBlockState(pos);

                // 向下查找第一个非空气方块
                while (y > minY && state.isAir()) {
                    y--;
                    pos.setY(y);
                    state = chunk.getBlockState(pos);
                }

                int color;
                if (state.isAir()) {
                    // 完全空洞：使用生物群系颜色
                    color = BiomeColors.getAverageGrassColor(level, pos.immutable());
                } else {
                    color = blockColors.getColor(state, level, pos, 0);
                }

                // 确保 alpha 不透明
                colors[lx * 16 + lz] = color | 0xFF000000;
            }
        }

        TERRAIN_CACHE.put(key, colors);
        return colors;
    }

    /** 清除指定区块缓存 */
    public static void invalidate(int cx, int cz) {
        TERRAIN_CACHE.remove(ChunkPos.asLong(cx, cz));
    }

    /** 清除全部缓存 */
    public static void clearAll() {
        TERRAIN_CACHE.clear();
    }
}