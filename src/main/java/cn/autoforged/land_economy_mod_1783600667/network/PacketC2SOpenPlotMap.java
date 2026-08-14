package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Supplier;

/**
 * 客户端请求进入地块地图视图。
 * 服务端校验玩家地块模式 == "new" → 标记地块界面在线 →
 * 下发初始地块所有权数据 + PacketS2COpenScreen(PLOT_MAP)。
 */
public class PacketC2SOpenPlotMap {
    public PacketC2SOpenPlotMap() {}

    public static void enc(PacketC2SOpenPlotMap m, FriendlyByteBuf b) {}

    public static PacketC2SOpenPlotMap dec(FriendlyByteBuf b) {
        return new PacketC2SOpenPlotMap();
    }

    public static void handle(PacketC2SOpenPlotMap m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            EconomySavedData data = LandEconomyMod.getEconomyData();
            if (data == null) return;
            if (!"new".equals(data.getPlayerPlotMode(p.getUUID()))) return; // 旧版不进入地块视图
            data.setInPlotMode(p.getUUID(), true);
            // 下发玩家周围地块所有权快照
            int r = ModConfig.COMMON.plotMapViewRadius.get();
            ChunkPos cp = new ChunkPos(p.blockPosition());
            String dim = p.level().dimension().location().toString();
            var cells = data.snapshotPlotCells(dim, cp.x - r, cp.z - r, cp.x + r, cp.z + r);
            ModMessages.sendToPlayer(p, new PacketS2CPlotChunkData(cells, cp.x - r, cp.z - r, cp.x + r, cp.z + r));
            // 打开地块视图 Screen
            ModMessages.sendToPlayer(p, new PacketS2COpenScreen(PacketS2COpenScreen.Type.PLOT_MAP));
        });
        ctx.get().setPacketHandled(true);
    }
}
