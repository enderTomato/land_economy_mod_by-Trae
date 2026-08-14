package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 模组自定义网络包注册中心。
 * C2S：客户端请求（进入地块视图、请求地块数据、购买/放弃、请求区域详情、发布留言）
 * S2C：服务端下发（地块所有权网格、操作结果、区域详情、打开 Screen、强制退出地块）
 *
 * 为兼容专用服务端，S2C 包的 handle 内部使用 DistExecutor.unsafeRunWhenOn 包裹客户端逻辑，
 * 避免 ClientPacketReceivers 类在服务端被加载导致 ClassNotFoundException。
 */
public final class ModMessages {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LandEconomyMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;
    private static int next() { return id++; }

    private ModMessages() {}

    public static void register() {
        // C2S
        INSTANCE.registerMessage(next(), PacketC2SOpenPlotMap.class,
                PacketC2SOpenPlotMap::enc, PacketC2SOpenPlotMap::dec, PacketC2SOpenPlotMap::handle);
        INSTANCE.registerMessage(next(), PacketC2SRequestPlotData.class,
                PacketC2SRequestPlotData::enc, PacketC2SRequestPlotData::dec, PacketC2SRequestPlotData::handle);
        INSTANCE.registerMessage(next(), PacketC2SPlotAction.class,
                PacketC2SPlotAction::enc, PacketC2SPlotAction::dec, PacketC2SPlotAction::handle);
        INSTANCE.registerMessage(next(), PacketC2SRequestRegionDetail.class,
                PacketC2SRequestRegionDetail::enc, PacketC2SRequestRegionDetail::dec, PacketC2SRequestRegionDetail::handle);
        INSTANCE.registerMessage(next(), PacketC2SPostMessage.class,
                PacketC2SPostMessage::enc, PacketC2SPostMessage::dec, PacketC2SPostMessage::handle);
        // S2C
        INSTANCE.registerMessage(next(), PacketS2CPlotChunkData.class,
                PacketS2CPlotChunkData::enc, PacketS2CPlotChunkData::dec, PacketS2CPlotChunkData::handle);
        INSTANCE.registerMessage(next(), PacketS2CPlotActionResult.class,
                PacketS2CPlotActionResult::enc, PacketS2CPlotActionResult::dec, PacketS2CPlotActionResult::handle);
        INSTANCE.registerMessage(next(), PacketS2CRegionDetail.class,
                PacketS2CRegionDetail::enc, PacketS2CRegionDetail::dec, PacketS2CRegionDetail::handle);
        INSTANCE.registerMessage(next(), PacketS2COpenScreen.class,
                PacketS2COpenScreen::enc, PacketS2COpenScreen::dec, PacketS2COpenScreen::handle);
        INSTANCE.registerMessage(next(), PacketS2CForceExitPlot.class,
                PacketS2CForceExitPlot::enc, PacketS2CForceExitPlot::dec, PacketS2CForceExitPlot::handle);
    }

    public static <M> void sendToServer(M msg) { INSTANCE.sendToServer(msg); }

    public static <M> void sendToPlayer(ServerPlayer p, M msg) {
        INSTANCE.send(msg, PacketDistributor.PLAYER.with(() -> p));
    }

    /** 便利：从 BiConsumer 适配到 Forge 的 message handler 函数式接口 */
    public static <M> net.minecraftforge.network.simple.SimpleChannel.MessageBuilder<M> build(
            Class<M> type, int idx) {
        return INSTANCE.messageBuilder(type, idx);
    }

    @SuppressWarnings("unused")
    private static <T> Supplier<T> memoize(Supplier<T> s) { return s; }
}
