package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端请求某 chunk 所属区域的详情（点击自己/他人地块时触发）。
 * 服务端解析 chunk 归属 → 下发 PacketS2CRegionDetail。
 */
public class PacketC2SRequestRegionDetail {
    private final long chunkKey;
    private final String dimensionId;

    public PacketC2SRequestRegionDetail(long chunkKey, String dim) {
        this.chunkKey = chunkKey;
        this.dimensionId = dim;
    }

    public static void enc(PacketC2SRequestRegionDetail m, FriendlyByteBuf b) {
        b.writeLong(m.chunkKey);
        b.writeUtf(m.dimensionId);
    }

    public static PacketC2SRequestRegionDetail dec(FriendlyByteBuf b) {
        return new PacketC2SRequestRegionDetail(b.readLong(), b.readUtf());
    }

    public static void handle(PacketC2SRequestRegionDetail m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            EconomySavedData data = LandEconomyMod.getEconomyData();
            if (data == null) return;
            RegionData r = data.getRegionOwningChunk(m.dimensionId, m.chunkKey);
            if (r == null) return;
            String ownerName = resolveName(p.getServer(), r.getOwner());
            List<String> memberNames = new ArrayList<>();
            if (r.getOwner() != null) memberNames.add(resolveName(p.getServer(), r.getOwner()));
            for (UUID mem : r.getMembers()) memberNames.add(resolveName(p.getServer(), mem));
            boolean isMine = r.isMember(p.getUUID());
            ModMessages.sendToPlayer(p, new PacketS2CRegionDetail(
                    r.getRegionId(), r.getName(), ownerName, memberNames,
                    r.getGdp(), r.getPopulation(), r.getBankDeposits(),
                    r.getMessages(), isMine));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String resolveName(MinecraftServer server, UUID id) {
        if (id == null) return "未知";
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getScoreboardName();
            var profile = server.getProfileCache().get(id);
            if (profile.isPresent() && profile.get().getName() != null) return profile.get().getName();
        }
        return id.toString().substring(0, 8);
    }
}
