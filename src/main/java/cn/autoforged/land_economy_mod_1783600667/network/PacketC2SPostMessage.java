package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端发布留言。
 * 服务端校验玩家是该区域成员 → region.addMessage(...) → 回发 PacketS2CRegionDetail（刷新）。
 */
public class PacketC2SPostMessage {
    private final UUID regionId;
    private final String text;

    public PacketC2SPostMessage(UUID regionId, String text) {
        this.regionId = regionId;
        this.text = text;
    }

    public static void enc(PacketC2SPostMessage m, FriendlyByteBuf b) {
        b.writeUUID(m.regionId);
        b.writeUtf(m.text);
    }

    public static PacketC2SPostMessage dec(FriendlyByteBuf b) {
        return new PacketC2SPostMessage(b.readUUID(), b.readUtf(1024));
    }

    public static void handle(PacketC2SPostMessage m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            EconomySavedData data = LandEconomyMod.getEconomyData();
            if (data == null) return;
            RegionData r = data.getRegion(m.regionId);
            if (r == null) return;
            if (!r.isMember(p.getUUID())) return;            // 仅成员可留言
            int max = ModConfig.COMMON.plotMessageBoardSize.get();
            r.addMessage(p.getUUID(), p.getScoreboardName(), m.text, max);
            // 回发刷新后的详情
            String ownerName = r.getOwner() != null ? r.getOwner().toString().substring(0, 8) : "未知";
            ModMessages.sendToPlayer(p, new PacketS2CRegionDetail(
                    r.getRegionId(), r.getName(), ownerName, java.util.List.of(),
                    r.getGdp(), r.getPopulation(), r.getBankDeposits(),
                    r.getMessages(), r.isMember(p.getUUID())));
        });
        ctx.get().setPacketHandled(true);
    }
}
