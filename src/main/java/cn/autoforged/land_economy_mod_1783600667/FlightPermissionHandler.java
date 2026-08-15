package cn.autoforged.land_economy_mod_1783600667;

import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端区域飞行权限控制器。
 *
 * 权限 region_fly(11)：true=允许区域成员（owner+members）在该区域内飞行；
 * false=不授飞行（维持原版生存行为）。离开授权区域立即撤销 mayfly/flying。
 * 创造/旁观模式不受影响。
 *
 * 飞行由服务端 Abilities 控制（原版同步），客户端无法在未授权区域维持飞行。
 */
@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID)
public class FlightPermissionHandler {

    private static final int PERIOD = 10; // 每 10 tick 检查一次
    private static final Set<UUID> FLY_GRANTED = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % PERIOD != 0) return;
        if (sp.level().isClientSide) return;

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return;
        RegionData region = regionAt(sp.level(), sp.blockPosition(), data);

        boolean shouldGrant = region != null
                && region.getPermission(11)            // region_fly 开启
                && region.isMember(sp.getUUID());      // 仅成员
        boolean granted = FLY_GRANTED.contains(sp.getUUID());

        if (shouldGrant && !granted) {
            grant(sp);
            FLY_GRANTED.add(sp.getUUID());
        } else if (!shouldGrant && granted) {
            revoke(sp);
            FLY_GRANTED.remove(sp.getUUID());
        }
    }

    private static void grant(ServerPlayer sp) {
        var ab = sp.getAbilities();
        if (!ab.mayfly) {
            ab.mayfly = true;
            sp.onUpdateAbilities();
        }
    }

    private static void revoke(ServerPlayer sp) {
        var ab = sp.getAbilities();
        if (sp.isCreative() || sp.isSpectator()) return;
        if (ab.mayfly) ab.mayfly = false;
        if (ab.flying) {
            ab.flying = false;
            sp.fallDistance = 0f;     // 离开授权区域：解除飞行
        }
        sp.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() == null) return;
        FLY_GRANTED.remove(e.getEntity().getUUID());
    }

    /** 取玩家所在最小面积区域（处理嵌套） */
    private static RegionData regionAt(Level l, BlockPos pos, EconomySavedData data) {
        String dim = l.dimension().location().toString();
        RegionData best = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.getDimensionId() == null || !r.getDimensionId().equals(dim)) continue;
            if (!r.containsPos(pos)) continue;
            if (best == null || r.getAreaSize() < best.getAreaSize()) best = r;
        }
        return best;
    }
}
