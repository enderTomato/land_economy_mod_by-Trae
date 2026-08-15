package cn.autoforged.land_economy_mod_1783600667.claim;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * 服务端区块认领/放弃逻辑。
 * 处理资金校验、冲突校验、距离定价。
 */
public final class ChunkClaimService {

    private ChunkClaimService() {}

    /**
     * 认领新区块。
     * @return 操作结果消息
     */
    public static String claim(ServerPlayer player, List<Long> chunkKeys, String dimId) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return "数据未加载";

        UUID playerId = player.getUUID();
        RegionData mine = data.getRegionByOwner(playerId);

        // 如果没有区域，先创建一个基础区域
        if (mine == null) {
            mine = new RegionData();
            mine.setOwner(playerId);
            mine.setName(player.getName().getString() + "的领地");
            mine.setDimensionId(dimId);
            data.createRegion(playerId, mine);
        }

        // 冲突校验
        for (long k : chunkKeys) {
            RegionData owner = data.getRegionOwningChunk(dimId, k);
            if (owner != null && !owner.getOwner().equals(playerId)) {
                int cx = ChunkPos.getX(k);
                int cz = ChunkPos.getZ(k);
                return "区块 [" + cx + ", " + cz + "] 已被 " + owner.getName() + " 认领";
            }
            if (mine.ownsChunk(k)) {
                int cx = ChunkPos.getX(k);
                int cz = ChunkPos.getZ(k);
                return "区块 [" + cx + ", " + cz + "] 已属于你";
            }
        }

        // 上限校验
        int max = ModConfig.COMMON.chunkMaxPerPlayer.get();
        if (max > 0) {
            int current = mine.getClaimedChunks().size();
            int newCount = 0;
            for (long k : chunkKeys) {
                if (!mine.ownsChunk(k)) newCount++;
            }
            if (current + newCount > max) {
                return "超出最大区块认领数 (" + max + ")，当前: " + current;
            }
        }

        // 资金校验
        double cost = calculateCost(mine, chunkKeys);
        double funds = data.getPlayerFunds(playerId);
        if (funds < cost) {
            return "资金不足，需要 " + String.format("%.2f", cost) + " (现有: " + String.format("%.2f", funds) + ")";
        }

        // 扣费 + 认领
        data.addPlayerFunds(playerId, -cost);
        for (long k : chunkKeys) {
            mine.addChunk(k);
        }
        data.setDirty();

        return "成功认领 " + chunkKeys.size() + " 个区块，花费 " + String.format("%.2f", cost);
    }

    /**
     * 放弃区块。
     * @return 操作结果消息
     */
    public static String unclaim(ServerPlayer player, List<Long> chunkKeys, String dimId) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return "数据未加载";

        UUID playerId = player.getUUID();
        RegionData mine = data.getRegionByOwner(playerId);
        if (mine == null) return "你没有认领任何区块";

        // 所有权校验
        for (long k : chunkKeys) {
            if (!mine.ownsChunk(k)) {
                int cx = ChunkPos.getX(k);
                int cz = ChunkPos.getZ(k);
                return "区块 [" + cx + ", " + cz + "] 不属于你";
            }
        }

        // 退款
        double refund = ModConfig.COMMON.chunkUnclaimRefund.get() * chunkKeys.size();
        data.addPlayerFunds(playerId, refund);

        // 移除
        for (long k : chunkKeys) {
            mine.removeChunk(k);
        }
        data.setDirty();

        return "成功放弃 " + chunkKeys.size() + " 个区块，返还 " + String.format("%.2f", refund);
    }

    /**
     * 计算购买新区块的总费用。
     * 距离定价：每个新区块到原区域所有已认领区块的最近 Chebyshev 距离。
     * 价格 = 基础价格 × (1 + 距离系数 × 最近距离)
     */
    private static double calculateCost(RegionData region, List<Long> newChunks) {
        double basePrice = ModConfig.COMMON.chunkClaimCost.get();
        double multiplier = ModConfig.COMMON.chunkExpandDistanceMultiplier.get();

        Set<Long> existing = region.getClaimedChunks();
        if (existing.isEmpty()) {
            return basePrice * newChunks.size();
        }

        double total = 0;
        for (long newKey : newChunks) {
            int nx = ChunkPos.getX(newKey);
            int nz = ChunkPos.getZ(newKey);
            int minDist = Integer.MAX_VALUE;
            for (long exKey : existing) {
                int ex = ChunkPos.getX(exKey);
                int ez = ChunkPos.getZ(exKey);
                int dist = Math.max(Math.abs(nx - ex), Math.abs(nz - ez));
                if (dist < minDist) minDist = dist;
            }
            total += basePrice * (1.0 + multiplier * minDist);
        }
        return total;
    }
}