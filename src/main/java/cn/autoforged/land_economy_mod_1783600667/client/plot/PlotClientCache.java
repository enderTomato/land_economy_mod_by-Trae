package cn.autoforged.land_economy_mod_1783600667.client.plot;

import net.minecraft.world.level.ChunkPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端地块归属缓存。
 *
 * 数据来源：服务端下发的 PacketS2CPlotChunkData。
 * 仅用于绘制四色高亮框；不参与服务端权威校验。
 *
 * 线程安全：使用 ConcurrentHashMap，因网络包在 Netty 线程 → enqueueWork 转主线程
 * 期间可能被并发读取。
 */
public final class PlotClientCache {

    public record Cell(boolean isMine, boolean isOthers, String regionName, boolean isFlyland, UUID owner) {}

    private static final ConcurrentHashMap<Long, Cell> CACHE = new ConcurrentHashMap<>();

    private PlotClientCache() {}

    public static void put(long chunkKey, boolean isMine, boolean isOthers,
                            String regionName, boolean isFlyland, UUID owner) {
        if (regionName == null) regionName = "";
        CACHE.put(chunkKey, new Cell(isMine, isOthers, regionName, isFlyland, owner));
    }

    public static Cell get(long chunkKey) {
        return CACHE.get(chunkKey);
    }

    public static Cell get(int cx, int cz) {
        return CACHE.get(ChunkPos.asLong(cx, cz));
    }

    /** 失效一批区块（购买/放弃操作后由服务端重新下发） */
    public static void invalidate(Iterable<Long> keys) {
        for (long k : keys) CACHE.remove(k);
    }

    public static void clear() {
        CACHE.clear();
    }

    /** 缓存内条目数（调试用） */
    public static int size() {
        return CACHE.size();
    }
}
