package cn.autoforged.land_economy_mod_1783600667;

import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.core.BlockPos;
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

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        Explosion explosion = event.getExplosion();
        BlockPos center = BlockPos.containing(explosion.getPosition());
        RegionData region = getRegionAt(level, center);
        if (region == null) return;

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
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
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
    }
}
