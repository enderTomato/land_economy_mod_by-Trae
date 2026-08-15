package cn.autoforged.land_economy_mod_1783600667.client;

import cn.autoforged.land_economy_mod_1783600667.client.gui.RegionDetailScreen;
import cn.autoforged.land_economy_mod_1783600667.client.gui.LandChestScreen;
import cn.autoforged.land_economy_mod_1783600667.client.screen.ChunkClaimCache;
import cn.autoforged.land_economy_mod_1783600667.client.screen.ChunkClaimScreen;
import cn.autoforged.land_economy_mod_1783600667.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
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

    /** 服务端下发某区块范围的区块归属快照 → 写入缓存并通知 Screen */
    public static void onChunkData(PacketS2CChunkData m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) return;
            UUID me = p.getUUID();
            for (PacketS2CChunkData.CellDTO c : m.getCells()) {
                ChunkClaimCache.put(c.chunkKey(), c.owner(), c.regionName(), me);
            }
            if (mc.screen instanceof ChunkClaimScreen ccs) {
                ccs.onChunkDataUpdated();
            }
        });
    }

    /** 服务端下发区块操作结果 */
    public static void onChunkClaimResult(PacketS2CChunkClaimResult m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return;
            if (m.isSuccess()) {
                p.sendSystemMessage(Component.literal("§a" + m.getMessage()));
                ChunkClaimCache.invalidate(m.getAffectedChunks());
            } else {
                p.sendSystemMessage(Component.literal("§c" + m.getMessage()));
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChunkClaimScreen ccs) {
                ccs.clearSelection();
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

    /** 服务端要求客户端打开某 Screen（区块认领地图/箱子GUI） */
    public static void onOpenScreen(PacketS2COpenScreen m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (m.getType()) {
                case CHUNK_CLAIM_MAP -> mc.setScreen(new ChunkClaimScreen());
                case CHEST  -> mc.setScreen(new LandChestScreen());
                case CLOSE_MAP -> {
                    if (mc.screen instanceof ChunkClaimScreen) {
                        mc.screen.onClose();
                        mc.setScreen(null);
                    }
                }
            }
        });
    }
}