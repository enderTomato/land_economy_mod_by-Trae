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
 * 服务端下发地块操作结果（购买/放弃）。
 * 客户端提示玩家 + 刷新余额 + 标记重拉受影响范围。
 */
public class PacketS2CPlotActionResult {
    private final boolean success;
    private final String message;
    private final double newFunds;
    private final List<Long> updatedChunks;

    public PacketS2CPlotActionResult(boolean success, String message, double newFunds, List<Long> updatedChunks) {
        this.success = success;
        this.message = message;
        this.newFunds = newFunds;
        this.updatedChunks = updatedChunks;
    }

    public static void enc(PacketS2CPlotActionResult m, FriendlyByteBuf b) {
        b.writeBoolean(m.success);
        b.writeUtf(m.message);
        b.writeDouble(m.newFunds);
        b.writeVarInt(m.updatedChunks.size());
        for (long k : m.updatedChunks) b.writeLong(k);
    }

    public static PacketS2CPlotActionResult dec(FriendlyByteBuf b) {
        boolean s = b.readBoolean();
        String msg = b.readUtf();
        double f = b.readDouble();
        int n = b.readVarInt();
        List<Long> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(b.readLong());
        return new PacketS2CPlotActionResult(s, msg, f, list);
    }

    public static void handle(PacketS2CPlotActionResult m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onPlotActionResult(m, ctx));
        ctx.get().setPacketHandled(true);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public double getNewFunds() { return newFunds; }
    public List<Long> getUpdatedChunks() { return updatedChunks; }
}
