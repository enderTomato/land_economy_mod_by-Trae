package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.client.ClientPacketReceivers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端强制玩家退出地块界面（受击/传送/被位移时下发）。
 */
public class PacketS2CForceExitPlot {
    public PacketS2CForceExitPlot() {}

    public static void enc(PacketS2CForceExitPlot m, FriendlyByteBuf b) {}

    public static PacketS2CForceExitPlot dec(FriendlyByteBuf b) {
        return new PacketS2CForceExitPlot();
    }

    public static void handle(PacketS2CForceExitPlot m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onForceExit(m, ctx));
        ctx.get().setPacketHandled(true);
    }
}
