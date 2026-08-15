package cn.autoforged.land_economy_mod_1783600667;

import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketS2COpenScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID)
public class RegionEventListener {

    private static RegionData getRegionAt(Level level, BlockPos pos) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return null;
        String dimId = level.dimension().location().toString();
        for (RegionData region : data.getAllRegions()) {
            if (region.getDimensionId() != null
                    && region.getDimensionId().equals(dimId)
                    && region.containsPos(pos)) {
                return region;
            }
        }
        return null;
    }

    /** 区域冻结：禁止区域内的方块更新（permission 12 = false 表示冻结） */
    private static boolean isFrozen(Level level, BlockPos pos) {
        RegionData r = getRegionAt(level, pos);
        return r != null && !r.getPermission(12);
    }

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        Explosion explosion = event.getExplosion();
        BlockPos center = BlockPos.containing(explosion.getPosition());
        RegionData region = getRegionAt(level, center);
        if (region == null) return;

        // 区域冻结：禁止爆炸发生
        if (!region.getPermission(12)) {
            event.setCanceled(true);
            return;
        }
        if (!region.getPermission(0)) {
            event.setCanceled(true);
        }
        if (!region.getPermission(9) && region.getPermission(0)) {
            explosion.clearToBlow();
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        Explosion explosion = event.getExplosion();
        BlockPos center = BlockPos.containing(explosion.getPosition());
        RegionData centerRegion = getRegionAt(level, center);
        if (centerRegion != null && !centerRegion.getPermission(5)) {
            explosion.clearToBlow();
            return;
        }
        // Even explosions centered OUTSIDE a region must not destroy blocks inside it
        // when the region disallows explosion (permission 0) or explosion block damage (permission 5).
        explosion.getToBlow().removeIf(pos -> {
            RegionData region = getRegionAt(level, pos);
            return region != null && (!region.getPermission(0) || !region.getPermission(5));
        });
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;

        RegionData region = getRegionAt(level, entity.blockPosition());
        if (region == null) return;

        if (entity instanceof Phantom && !region.getPermission(2)) {
            event.setCanceled(true);
            return;
        }

        if (living.getMobType() == MobType.UNDEAD && !region.getPermission(1)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        RegionData region = getRegionAt(event.getEntity().level(), event.getEntity().blockPosition());
        if (region == null) return;

        boolean attackerIsMember = region.isMember(attacker.getUUID());
        boolean targetIsMember = region.isMember(target.getUUID());

        if (!attackerIsMember && !targetIsMember) return;

        if (region.getPermission(4)) {
            if (targetIsMember && attackerIsMember) {
                event.setCanceled(true);
                return;
            }
        } else if (!region.getPermission(3)) {
            if (targetIsMember && attackerIsMember) {
                event.setCanceled(true);
                return;
            }
        }

        // 玩家受击时关闭区块认领地图
        if (event.getEntity() instanceof ServerPlayer sp) {
            forceExitClaimMap(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        RegionData region = getRegionAt(level, pos);
        if (region == null) return;

        boolean isMember = region.isMember(player.getUUID());
        Block block = level.getBlockState(pos).getBlock();

        // Fire prevention: flint-and-steel / fire charge
        if (!region.getPermission(9)) {
            var heldItem = player.getItemInHand(event.getHand());
            if (heldItem.getItem() instanceof FlintAndSteelItem
                    || heldItem.getItem() instanceof FireChargeItem) {
                event.setCanceled(true);
                return;
            }
        }

        // Block place/break prevention: handled by BreakEvent / EntityPlaceEvent

        // Container access
        if (block instanceof AbstractChestBlock || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock || block instanceof AbstractFurnaceBlock
                || block instanceof HopperBlock || block instanceof DispenserBlock
                || block instanceof BrewingStandBlock) {
            if (!isMember && !region.getPermission(6)) {
                event.setCanceled(true);
            }
            return;
        }

        // Redstone interact - fixed to cover doors, trapdoors, fence gates, levers
        if (block instanceof ButtonBlock || block instanceof BasePressurePlateBlock
                || block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock || block instanceof LeverBlock) {
            if (!isMember && !region.getPermission(7)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getPlayer();
        if (player == null) return;

        RegionData region = getRegionAt((Level) event.getLevel(), event.getPos());
        if (region == null) return;

        // 区域冻结：禁止非母区域成员破坏方块；母区域成员仍可破坏（用于解除冻结）
        if (!region.getPermission(12) && !region.isMember(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (!region.isMember(player.getUUID()) && !region.getPermission(10)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        RegionData region = getRegionAt((Level) event.getLevel(), event.getPos());
        if (region == null) return;

        // 区域冻结：禁止非母区域成员放置方块；母区域成员仍可放置
        if (!region.getPermission(12) && !region.isMember(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (!region.isMember(player.getUUID()) && !region.getPermission(10)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getPlayer();
        BlockPos targetPos = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        RegionData region = getRegionAt(player.level(), targetPos);
        if (region != null && !region.getPermission(8)) {
            event.setCanceled(true);
        }
        // 传送时关闭区块认领地图
        if (player instanceof ServerPlayer sp) {
            forceExitClaimMap(sp);
        }
    }

    // ==================== 区域冻结：禁止方块状态变化与更新 ====================

    /** 邻居方块更新（红石信号传播、方块状态联动） */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        if (isFrozen(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** 活塞推动方块（收回/推出） */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof Level level)) return;
        BlockPos pistonPos = event.getPos();
        // 检查活塞本身位置 + 活塞将影响的所有位置（粗略检查活塞前方一格）
        if (isFrozen(level, pistonPos)) {
            event.setCanceled(true);
            return;
        }
        // 朝向方向
        net.minecraft.core.Direction dir = event.getDirection();
        BlockPos target = pistonPos.relative(dir);
        if (isFrozen(level, target)) {
            event.setCanceled(true);
        }
    }

    /** 流体放置（水/岩浆流入） */
    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        if (isFrozen(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** 远古残骸与作物生长：通过 NeighborNotify 的子事件不一定触发，这里使用 BlockEvent.CropGrowEvent */
    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        if (isFrozen(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    // ==================== 区块认领地图 ====================

    /** 强制关闭玩家的区块认领地图（受击/传送时调用） */
    private static void forceExitClaimMap(ServerPlayer player) {
        ModMessages.sendToPlayer(player, new PacketS2COpenScreen(PacketS2COpenScreen.Type.CLOSE_MAP));
    }
}
