package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端平移地图到新区域时，请求该视图中心周围的区块归属数据。
 * 服务端响应并下发 PacketS2CChunkData。
 */
public class PacketC2SRequestChunkData {
    private final int cx;
    private final int cz;

    public PacketC2SRequestChunkData(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public static void enc(PacketC2SRequestChunkData m, FriendlyByteBuf b) {
        b.writeInt(m.cx);
        b.writeInt(m.cz);
    }

    public static PacketC2SRequestChunkData dec(FriendlyByteBuf b) {
        return new PacketC2SRequestChunkData(b.readInt(), b.readInt());
    }

    public static void handle(PacketC2SRequestChunkData m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            EconomySavedData data = LandEconomyMod.getEconomyData();
            if (data == null) return;
            int r = 16;
            String dim = p.level().dimension().location().toString();
            var cells = data.snapshotChunkCells(dim, m.cx - r, m.cz - r, m.cx + r, m.cz + r);
            ModMessages.sendToPlayer(p, new PacketS2CChunkData(cells, m.cx - r, m.cz - r, m.cx + r, m.cz + r));
        });
        ctx.get().setPacketHandled(true);
    }
}