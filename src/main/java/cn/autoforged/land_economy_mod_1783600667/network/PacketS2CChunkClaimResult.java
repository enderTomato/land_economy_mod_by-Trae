package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.client.ClientPacketReceivers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端 区块认领操作结果。
 */
public class PacketS2CChunkClaimResult {

    private final boolean success;
    private final String message;
    private final List<Long> affectedChunks;
    private final PacketC2SChunkClaimAction.Action action;

    public PacketS2CChunkClaimResult(boolean success, String message,
                                     List<Long> affectedChunks, PacketC2SChunkClaimAction.Action action) {
        this.success = success;
        this.message = message;
        this.affectedChunks = affectedChunks;
        this.action = action;
    }

    public static void enc(PacketS2CChunkClaimResult m, FriendlyByteBuf b) {
        b.writeBoolean(m.success);
        b.writeUtf(m.message);
        b.writeVarInt(m.affectedChunks.size());
        for (long k : m.affectedChunks) b.writeLong(k);
        b.writeEnum(m.action);
    }

    public static PacketS2CChunkClaimResult dec(FriendlyByteBuf b) {
        boolean success = b.readBoolean();
        String msg = b.readUtf();
        int n = b.readVarInt();
        List<Long> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) keys.add(b.readLong());
        PacketC2SChunkClaimAction.Action action = b.readEnum(PacketC2SChunkClaimAction.Action.class);
        return new PacketS2CChunkClaimResult(success, msg, keys, action);
    }

    public static void handle(PacketS2CChunkClaimResult m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onChunkClaimResult(m, ctx));
        ctx.get().setPacketHandled(true);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<Long> getAffectedChunks() { return affectedChunks; }
    public PacketC2SChunkClaimAction.Action getAction() { return action; }
}