package cn.autoforged.land_economy_mod_1783600667;

import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traveler's Title 风格标题显示：玩家进入区域时，在屏幕中央以标题形式显示
 * 该区域名称以及该区域所属玩家（创建者）。仅服务端逻辑，通过 PlayerTickEvent
 * 检测玩家所在区域变化后直接下发 title 数据包到客户端。
 */
@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID)
public class RegionTitleHandler {

    public static final String MODE_TITLE = "title";
    public static final String MODE_ACTIONBAR = "actionbar";

    // 玩家UUID -> 上次所在区域UUID（离开区域时移除条目，ConcurrentHashMap 不支持 null 值）
    private static final Map<UUID, UUID> LAST_REGION = new ConcurrentHashMap<>();
    // 玩家UUID -> 上次检查时的坐标，坐标没变则跳过检测，降低逐 tick 扫描开销
    private static final Map<UUID, BlockPos> LAST_POS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player == null || player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Level level = serverPlayer.level();
        BlockPos pos = serverPlayer.blockPosition();

        BlockPos lastPos = LAST_POS.get(serverPlayer.getUUID());
        if (pos.equals(lastPos)) return;
        LAST_POS.put(serverPlayer.getUUID(), pos);

        RegionData region = getRegionAt(level, pos);
        // 需求: 进入区域时显示该区所属玩家以及区域名称。原实现仅对"他人区域"触发，
        // 用户反馈该功能"完全没有触发"。此处改为: 只要玩家进入任意区域（含自己领地）
        // 就触发提示，与参考模组 Traveler's Title 的"进入已认领区域即提示"行为一致。
        UUID regionId = region != null ? region.getRegionId() : null;
        UUID lastRegionId = LAST_REGION.get(serverPlayer.getUUID());
        if (!Objects.equals(regionId, lastRegionId)) {
            // ConcurrentHashMap 不允许 null 值：离开区域时移除条目，而不是 put(null)
            if (regionId == null) {
                LAST_REGION.remove(serverPlayer.getUUID());
            } else {
                LAST_REGION.put(serverPlayer.getUUID(), regionId);
            }
            if (region != null) {
                showRegionEntry(serverPlayer, region);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() == null) return;
        LAST_REGION.remove(event.getEntity().getUUID());
        LAST_POS.remove(event.getEntity().getUUID());
    }

    /**
     * 取玩家所在区域。区域可能存在嵌套（母区域内含子区域/飞地），
     * 此时返回面积最小的那个区域作为"当前所在区域"。
     */
    private static RegionData getRegionAt(Level level, BlockPos pos) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return null;
        String dimId = level.dimension().location().toString();
        RegionData best = null;
        for (RegionData region : data.getAllRegions()) {
            if (region.getDimensionId() == null || !region.getDimensionId().equals(dimId)) continue;
            if (!region.containsPos(pos)) continue;
            if (best == null || region.getAreaSize() < best.getAreaSize()) {
                best = region;
            }
        }
        return best;
    }

    private static void showRegionEntry(ServerPlayer player, RegionData region) {
        String regionName = region.getName() != null ? region.getName() : "未知区域";
        String ownerName = resolveOwnerName(player, region.getOwner());
        Component titleText = Component.literal(regionName)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitleText = Component.literal("领地主: " + ownerName)
                .withStyle(ChatFormatting.YELLOW);

        // 玩家可在 /land display 中自行选择显示方式：屏幕标题（Traveler's Title 风格）
        // 或原版 ActionBar。未单独设置时使用配置文件默认值。
        EconomySavedData data = LandEconomyMod.getEconomyData();
        String mode = data != null ? data.getPlayerDisplayMode(player.getUUID()) : null;
        if (mode == null) {
            mode = ModConfig.COMMON.regionDisplayMode.get();
        }
        if (MODE_ACTIONBAR.equalsIgnoreCase(mode)) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal(regionName + " | 领地主: " + ownerName)
                            .withStyle(ChatFormatting.GOLD)));
            return;
        }
        // 先发动画时间（淡入/停留/淡出），再发标题与副标题（标题包会触发显示计时）
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(titleText));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
    }

    private static String resolveOwnerName(ServerPlayer player, UUID ownerId) {
        if (ownerId == null) return "未知";
        MinecraftServer server = player.getServer();
        if (server != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner != null) return owner.getScoreboardName();
            // 离线玩家：通过 Usercache（GameProfileCache）解析真实玩家名
            var profile = server.getProfileCache().get(ownerId);
            if (profile.isPresent() && profile.get().getName() != null) {
                return profile.get().getName();
            }
        }
        return ownerId.toString().substring(0, 8);
    }
}
