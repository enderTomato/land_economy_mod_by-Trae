package cn.autoforged.land_economy_mod_1783600667.client;

import cn.autoforged.land_economy_mod_1783600667.client.gui.RegionDetailScreen;
import cn.autoforged.land_economy_mod_1783600667.client.gui.LandChestScreen;
import cn.autoforged.land_economy_mod_1783600667.network.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 S2C 包分发。
 */
public final class ClientPacketReceivers {

    private ClientPacketReceivers() {}

    /** 服务端下发区域详情 → 打开 RegionDetailScreen */
    public static void onRegionDetail(PacketS2CRegionDetail m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new RegionDetailScreen(m));
        });
    }

    /** 服务端要求客户端打开某 Screen（箱子GUI） */
    public static void onOpenScreen(PacketS2COpenScreen m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (m.getType()) {
                case CHEST  -> mc.setScreen(new LandChestScreen());
            }
        });
    }
}