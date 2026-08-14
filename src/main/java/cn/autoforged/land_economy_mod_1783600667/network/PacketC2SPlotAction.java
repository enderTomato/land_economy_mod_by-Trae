package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.plot.PlotAction;
import cn.autoforged.land_economy_mod_1783600667.plot.PlotService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端请求购买/放弃地块（含批量）。
 * 服务端权威校验后下发 PacketS2CPlotActionResult（成功/失败 + 新余额 + 实际变更的区块）。
 */
public class PacketC2SPlotAction {
    private final PlotAction.Action action;
    private final List<Long> chunks;
    private final String dimensionId;

    public PacketC2SPlotAction(PlotAction.Action action, List<Long> chunks, String dim) {
        this.action = action;
        this.chunks = chunks;
        this.dimensionId = dim;
    }

    public static void enc(PacketC2SPlotAction m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeVarInt(m.chunks.size());
        for (long k : m.chunks) b.writeLong(k);
        b.writeUtf(m.dimensionId);
    }

    public static PacketC2SPlotAction dec(FriendlyByteBuf b) {
        PlotAction.Action a = b.readEnum(PlotAction.Action.class);
        int n = b.readVarInt();
        List<Long> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(b.readLong());
        return new PacketC2SPlotAction(a, list, b.readUtf());
    }

    public static void handle(PacketC2SPlotAction m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            PlotService.Result r = PlotService.process(p, m.action, m.chunks, m.dimensionId);
            ModMessages.sendToPlayer(p, new PacketS2CPlotActionResult(r.success, r.message, r.newFunds, r.updatedChunks));
        });
        ctx.get().setPacketHandled(true);
    }
}
