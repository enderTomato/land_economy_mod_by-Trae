package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.claim.ChunkClaimService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端→服务端 区块认领/放弃/强制加载操作。
 */
public class PacketC2SChunkClaimAction {

    public enum Action { CLAIM, UNCLAIM, TOGGLE_FORCELOAD }

    private final Action action;
    private final List<Long> chunkKeys;
    private final String dimensionId;

    public PacketC2SChunkClaimAction(Action action, List<Long> chunkKeys, String dimensionId) {
        this.action = action;
        this.chunkKeys = chunkKeys;
        this.dimensionId = dimensionId;
    }

    public static void enc(PacketC2SChunkClaimAction m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeVarInt(m.chunkKeys.size());
        for (long k : m.chunkKeys) b.writeLong(k);
        b.writeUtf(m.dimensionId);
    }

    public static PacketC2SChunkClaimAction dec(FriendlyByteBuf b) {
        Action action = b.readEnum(Action.class);
        int n = b.readVarInt();
        List<Long> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) keys.add(b.readLong());
        String dim = b.readUtf();
        return new PacketC2SChunkClaimAction(action, keys, dim);
    }

    public static void handle(PacketC2SChunkClaimAction m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            String result;
            switch (m.action) {
                case CLAIM -> result = ChunkClaimService.claim(p, m.chunkKeys, m.dimensionId);
                case UNCLAIM -> result = ChunkClaimService.unclaim(p, m.chunkKeys, m.dimensionId);
                default -> result = "不支持的操作";
            }
            ModMessages.sendToPlayer(p, new PacketS2CChunkClaimResult(
                    m.action == Action.CLAIM || m.action == Action.UNCLAIM,
                    result, m.chunkKeys, m.action));
        });
        ctx.get().setPacketHandled(true);
    }
}