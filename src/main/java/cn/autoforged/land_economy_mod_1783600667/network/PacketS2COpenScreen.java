package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.client.ClientPacketReceivers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端 打开某 Screen（地块图 / 箱子GUI）。
 */
public class PacketS2COpenScreen {
    public enum Type { CHUNK_CLAIM_MAP, CHEST, CLOSE_MAP }

    private final Type type;

    public PacketS2COpenScreen(Type type) { this.type = type; }

    public static void enc(PacketS2COpenScreen m, FriendlyByteBuf b) {
        b.writeEnum(m.type);
    }

    public static PacketS2COpenScreen dec(FriendlyByteBuf b) {
        return new PacketS2COpenScreen(b.readEnum(Type.class));
    }

    public static void handle(PacketS2COpenScreen m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onOpenScreen(m, ctx));
        ctx.get().setPacketHandled(true);
    }

    public Type getType() { return type; }
}
