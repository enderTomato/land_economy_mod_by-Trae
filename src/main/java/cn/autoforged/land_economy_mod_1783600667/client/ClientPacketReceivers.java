package cn.autoforged.land_economy_mod_1783600667.client;

import cn.autoforged.land_economy_mod_1783600667.client.gui.RegionDetailScreen;
import cn.autoforged.land_economy_mod_1783600667.client.gui.LandChestScreen;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotMapScreen;
import cn.autoforged.land_economy_mod_1783600667.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 S2C 包分发：把服务端下发的数据写入本地缓存或打开 Screen。
 *
 * 注意：本类只能由客户端线程访问；所有方法内部假定 Minecraft.getInstance() 非空。
 * 网络包 handle 通过 DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...) 包裹，
 * 确保专用服务端不会尝试加载本类。
 */
public final class ClientPacketReceivers {

    private ClientPacketReceivers() {}

    /** 服务端下发某区块范围的地块归属快照 → 写入 PlotClientCache */
    public static void onPlotChunkData(PacketS2CPlotChunkData m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            UUID me = p.getUUID();
            for (PacketS2CPlotChunkData.CellDTO c : m.getCells()) {
                boolean isMine = c.owner() != null && c.owner().equals(me);
                boolean isOthers = c.owner() != null && !isMine;
                PlotClientCache.put(c.chunkKey(), isMine, isOthers, c.regionName(), c.isFlyland(), c.owner());
            }
            // 强制刷新正在显示的 PlotMapScreen
            if (Minecraft.getInstance().screen instanceof PlotMapScreen pms) {
                pms.onChunkDataUpdated(m.getCx0(), m.getCz0(), m.getCx1(), m.getCz1());
            }
        });
    }

    /** 服务端下发地块操作结果 → 提示玩家 + 清除选区 + 重新拉取变更范围 */
    public static void onPlotActionResult(PacketS2CPlotActionResult m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            if (m.isSuccess()) {
                p.sendSystemMessage(Component.literal("§a" + m.getMessage()));
                // 清除本地选区并刷新缓存
                if (Minecraft.getInstance().screen instanceof PlotMapScreen pms) {
                    pms.clearSelection();
                }
                PlotClientCache.invalidate(m.getUpdatedChunks());
                // 重新拉取受影响区块的最新归属
                String dim = Minecraft.getInstance().level.dimension().location().toString();
                if (Minecraft.getInstance().screen instanceof PlotMapScreen pms) {
                    pms.requestChunksIfNeeded(dim);
                }
            } else {
                p.sendSystemMessage(Component.literal("§c" + m.getMessage()));
            }
        });
    }

    /** 服务端下发区域详情 → 打开 RegionDetailScreen */
    public static void onRegionDetail(PacketS2CRegionDetail m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new RegionDetailScreen(m));
        });
    }

    /** 服务端要求客户端打开某 Screen（地块图/箱子GUI） */
    public static void onOpenScreen(PacketS2COpenScreen m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (m.getType()) {
                case PLOT_MAP -> mc.setScreen(new PlotMapScreen());
                case CHEST  -> mc.setScreen(new LandChestScreen());
            }
        });
    }

    /** 服务端强制退出地块界面（受击/传送/位移时下发） */
    public static void onForceExit(PacketS2CForceExitPlot m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof PlotMapScreen pms) {
                pms.cancelAll();
            }
            if (mc.screen != null && mc.screen.getClass().getName().startsWith("cn.autoforged.land_economy_mod_1783600667.client")) {
                mc.setScreen(null);
            }
            LocalPlayer p = mc.player;
            if (p != null) {
                p.sendSystemMessage(Component.literal("§e[地块界面] 已被服务端强制退出（受击/移动/传送）"));
            }
        });
    }

    /** 便利：将一组长区块键转为字符串（调试用） */
    @SuppressWarnings("unused")
    private static String fmtChunks(Set<Long> keys) {
        Set<String> s = new HashSet<>();
        for (long k : keys) s.add("[" + ChunkPos.getX(k) + "," + ChunkPos.getZ(k) + "]");
        return s.toString();
    }
}
