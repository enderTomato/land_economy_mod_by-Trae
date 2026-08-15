package cn.autoforged.land_economy_mod_1783600667.client.screen;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端区块认领缓存。
 * 存储区块归属信息，预计算渲染颜色，供 ChunkClaimScreen 使用。
 */
public final class ChunkClaimCache {

    public record CacheEntry(UUID owner, String regionName, int colorARGB) {}

    private static final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private ChunkClaimCache() {}

    /** 存入一条区块缓存 */
    public static void put(long chunkKey, UUID owner, String regionName, UUID myUuid) {
        int color;
        if (owner == null) {
            color = 0x2000FF00; // 绿色透明 = 空
        } else if (owner.equals(myUuid)) {
            color = 0x600000FF; // 蓝色半透明 = 我的
        } else {
            color = 0x60FF0000; // 红色半透明 = 他人
        }
        cache.put(chunkKey, new CacheEntry(owner, regionName, color));
    }

    /** 获取缓存 */
    public static CacheEntry get(long chunkKey) {
        return cache.get(chunkKey);
    }

    /** 删除指定区块缓存 */
    public static void invalidate(java.util.Collection<Long> keys) {
        for (long k : keys) cache.remove(k);
    }

    /** 清空缓存 */
    public static void clear() {
        cache.clear();
    }

    /** 判断区块是否已被认领 */
    public static boolean isClaimed(long chunkKey) {
        CacheEntry e = cache.get(chunkKey);
        return e != null && e.owner() != null;
    }

    /** 判断区块是否属于我 */
    public static boolean isMine(long chunkKey, UUID myUuid) {
        CacheEntry e = cache.get(chunkKey);
        return e != null && e.owner() != null && e.owner().equals(myUuid);
    }

    /** 获取区块所有者 */
    public static UUID getOwner(long chunkKey) {
        CacheEntry e = cache.get(chunkKey);
        return e != null ? e.owner() : null;
    }
}