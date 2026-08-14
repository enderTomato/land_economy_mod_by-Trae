package cn.autoforged.land_economy_mod_1783600667.plot;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端权威地块购买/放弃核心。
 * 所有资金/冲突/边界/上限校验在服务端进行，客户端仅发送请求。
 */
public final class PlotService {

    private PlotService() {}

    public static class Result {
        public final boolean success;
        public final String message;
        public final double newFunds;
        public final List<Long> updatedChunks;
        public Result(boolean s, String m, double f, List<Long> u) {
            success = s; message = m; newFunds = f; updatedChunks = u;
        }
    }

    public static Result process(ServerPlayer p, PlotAction.Action action, List<Long> chunks, String dim) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return new Result(false, "经济数据不可用", 0, List.of());
        if (!"new".equals(data.getPlayerPlotMode(p.getUUID())))
            return new Result(false, "当前为旧版模式，请先 /land mode new", data.getPlayerFunds(p.getUUID()), List.of());
        if (chunks.isEmpty())
            return new Result(false, "未选择任何区块", data.getPlayerFunds(p.getUUID()), List.of());

        RegionData mine = data.getRegionByOwner(p.getUUID());
        List<Long> changed = new ArrayList<>();

        if (action == PlotAction.Action.BUY) {
            double costPer = ModConfig.COMMON.plotCostPerChunk.get();
            int max = ModConfig.COMMON.plotMaxChunksPerPlayer.get();
            int alreadyOwned = mine != null ? mine.getClaimedChunks().size() : 0;
            int wantBuy = 0;
            for (long k : chunks) {
                RegionData owner = data.getRegionOwningChunk(dim, k);
                if (owner != null) continue;                  // 已被他人/自己占用
                wantBuy++;
            }
            if (wantBuy == 0)
                return new Result(false, "所选区块均已被占用", data.getPlayerFunds(p.getUUID()), List.of());
            if (max >= 0 && alreadyOwned + wantBuy > max)
                return new Result(false, "超过最大区块数上限 " + max, data.getPlayerFunds(p.getUUID()), List.of());
            double total = costPer * wantBuy;
            double funds = data.getPlayerFunds(p.getUUID());
            if (total > 0 && funds < total)
                return new Result(false, "资金不足（需 " + total + "，现有 " + funds + "）", funds, List.of());

            // 落地：若无母区域则创建"地块型母区域"
            if (mine == null) {
                mine = new RegionData();
                mine.setOwner(p.getUUID());
                mine.setName(p.getScoreboardName() + "的领地");
                mine.setDimensionId(dim);
                data.createRegion(p.getUUID(), mine);
            }
            for (long k : chunks) {
                if (data.getRegionOwningChunk(dim, k) == null && mine.addChunk(k)) changed.add(k);
            }
            mine.recomputeAABBFromChunks();
            if (total > 0) data.addPlayerFunds(p.getUUID(), -total);
            data.setDirty();
            return new Result(true, "购买 " + changed.size() + " 区块，花费 " + total,
                    data.getPlayerFunds(p.getUUID()), changed);

        } else { // ABANDON
            if (mine == null)
                return new Result(false, "你没有可放弃的地块", data.getPlayerFunds(p.getUUID()), List.of());
            double refundPer = ModConfig.COMMON.plotRefundPerChunk.get();
            int n = 0;
            for (long k : chunks) {
                if (mine.ownsChunk(k) && mine.removeChunk(k)) { changed.add(k); n++; }
            }
            if (n == 0)
                return new Result(false, "所选区块均非你所有", data.getPlayerFunds(p.getUUID()), List.of());
            mine.recomputeAABBFromChunks();
            double refund = refundPer * n;
            if (refund > 0) data.addPlayerFunds(p.getUUID(), refund);
            data.setDirty();
            return new Result(true, "放弃 " + n + " 区块，返还 " + refund,
                    data.getPlayerFunds(p.getUUID()), changed);
        }
    }
}
