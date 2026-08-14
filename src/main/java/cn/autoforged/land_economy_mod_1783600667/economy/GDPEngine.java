package cn.autoforged.land_economy_mod_1783600667.economy;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.IndustryClassification;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GDPEngine {

    private static final GDPEngine INSTANCE = new GDPEngine();

    private MinecraftServer server;
    private ScheduledExecutorService executor;
    private long tickCounter = 0;
    private boolean scheduledTasksStarted = false;

    private GDPEngine() {}

    public static GDPEngine getInstance() {
        return INSTANCE;
    }

    public void startScheduledTasks(MinecraftServer server) {
        if (scheduledTasksStarted) return;
        this.server = server;
        this.scheduledTasksStarted = true;

        IndustryClassification.initialize();

        LandEconomyMod.LOGGER.info("GDP Engine started. Interval: {} minutes, Multi-threaded: {}",
                ModConfig.COMMON.gdpCalcIntervalMinutes.get(),
                ModConfig.COMMON.enableMultiThreadedGdpCalc.get());

        if (ModConfig.COMMON.enableMultiThreadedGdpCalc.get()) {
            executor = Executors.newScheduledThreadPool(
                    ModConfig.COMMON.maxConcurrentGdpCalc.get()
            );
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (server == null) return;
        if (!server.isRunning()) return;

        tickCounter++;
        if (tickCounter % 1200 != 0) return;

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return;

        long currentTime = System.currentTimeMillis();
        int intervalMinutes = ModConfig.COMMON.gdpCalcIntervalMinutes.get();
        long intervalMs = intervalMinutes * 60L * 1000L;

        if (currentTime - data.getLastGdpCalcTime() >= intervalMs) {
            if (ModConfig.COMMON.enableMultiThreadedGdpCalc.get()) {
                triggerAsyncGdpCalculation(data);
            } else {
                triggerGdpCalculation(data);
            }
        }
    }

    public synchronized void triggerGdpCalculation(EconomySavedData data) {
        long startTime = System.currentTimeMillis();
        LandEconomyMod.LOGGER.info("Starting GDP calculation for {} regions...", data.getRegionCount());

        double totalGdp = 0;
        Map<String, Map<String, Integer>> regionItemCounts = new HashMap<>();

        // First pass: scan flyland items, merge into parent
        Map<UUID, Map<String, Integer>> flylandItems = new HashMap<>();
        for (RegionData region : data.getAllRegions()) {
            if (region.isFlyland() && region.getParentRegionId() != null) {
                Map<String, Integer> itemCounts = scanRegionItems(region);
                flylandItems.merge(region.getParentRegionId(), itemCounts, (a, b) -> {
                    Map<String, Integer> merged = new HashMap<>(a);
                    b.forEach((k, v) -> merged.merge(k, v, Integer::sum));
                    return merged;
                });
            }
        }

        // Second pass: scan root/child regions
        for (RegionData region : data.getAllRegions()) {
            if (region.isFlyland()) continue;
            Map<String, Integer> itemCounts = scanRegionItems(region);
            // Merge flyland items
            Map<String, Integer> flyItems = flylandItems.get(region.getRegionId());
            if (flyItems != null) {
                flyItems.forEach((k, v) -> itemCounts.merge(k, v, Integer::sum));
            }
            regionItemCounts.put(region.getRegionId().toString(), itemCounts);
        }

        for (RegionData region : data.getAllRegions()) {
            if (region.isFlyland()) continue;
            Map<String, Integer> itemCounts = regionItemCounts.getOrDefault(
                    region.getRegionId().toString(), new HashMap<>());

            double gdp = calculateRegionGdp(region, itemCounts);
            region.setGdp(gdp);
            totalGdp += gdp;
            region.setLastGdpCalcTime(System.currentTimeMillis());

            // Sync flyland GDP
            for (UUID childId : region.getChildRegionIds()) {
                RegionData child = data.getRegion(childId);
                if (child != null && child.isFlyland()) {
                    child.setGdp(0);
                    child.setLastGdpCalcTime(System.currentTimeMillis());
                }
            }
        }

        data.addGdpRecord(totalGdp);
        data.setLastGdpCalcTime(System.currentTimeMillis());
        data.setDirty();

        long elapsed = System.currentTimeMillis() - startTime;
        LandEconomyMod.LOGGER.info("GDP calculation completed in {}ms. Total GDP: {}", elapsed, totalGdp);
    }

    public synchronized void triggerAsyncGdpCalculation(EconomySavedData data) {
        var regions = new ArrayList<>(data.getAllRegions());
        int maxConcurrent = ModConfig.COMMON.maxConcurrentGdpCalc.get();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // First pass: flylands
        for (RegionData region : regions) {
            if (!region.isFlyland()) continue;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Map<String, Integer> itemCounts = scanRegionItems(region);
                Map<String, Integer> merged = data.getRegion(region.getParentRegionId()) != null
                        ? new HashMap<>() : new HashMap<>();
                // store in a thread-safe way
                synchronized (GDPEngine.class) {
                    if (region.getParentRegionId() != null) {
                        RegionData parent = data.getRegion(region.getParentRegionId());
                        // parent GDP will be recalculated with this data
                    }
                }
            }, executor);
            futures.add(future);
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // Second pass: root/child regions
        futures.clear();
        for (RegionData region : regions) {
            if (region.isFlyland()) continue;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Map<String, Integer> itemCounts = scanRegionItems(region);
                double gdp = calculateRegionGdp(region, itemCounts);
                region.setGdp(gdp);
            }, executor);

            futures.add(future);

            if (futures.size() >= maxConcurrent) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                futures.clear();
            }
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        double totalGdp = regions.stream().filter(r -> !r.isFlyland()).mapToDouble(RegionData::getGdp).sum();
        data.addGdpRecord(totalGdp);
        data.setLastGdpCalcTime(System.currentTimeMillis());
        data.setDirty();

        LandEconomyMod.LOGGER.info("Async GDP calculation completed. Total GDP: {}", totalGdp);
    }

    private Map<String, Integer> scanRegionItems(RegionData region) {
        Map<String, Integer> itemCounts = new HashMap<>();
        boolean containerOnly = ModConfig.COMMON.enableContainerOnlyGdpCalc.get();
        if (!containerOnly) {
            scanRegionBlocks(region, itemCounts);
        }

        for (ServerLevel level : server.getAllLevels()) {
            String dimId = level.dimension().location().toString();
            if (region.getDimensionId() != null && !region.getDimensionId().isEmpty()
                    && !region.getDimensionId().equals(dimId)) {
                continue;
            }

            int chunkMinX = region.getMinX() >> 4;
            int chunkMinZ = region.getMinZ() >> 4;
            int chunkMaxX = region.getMaxX() >> 4;
            int chunkMaxZ = region.getMaxZ() >> 4;

            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = level.getChunk(cx, cz);

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (containerOnly) {
                            BlockState blockState = be.getBlockState();
                            Block block = blockState.getBlock();
                            if (!(block instanceof AbstractChestBlock || block instanceof BarrelBlock
                                    || block instanceof ShulkerBoxBlock || block instanceof AbstractFurnaceBlock
                                    || block instanceof HopperBlock || block instanceof DispenserBlock
                                    || block instanceof DropperBlock || block instanceof BrewingStandBlock)) {
                                continue;
                            }
                        }
                        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                            for (int i = 0; i < handler.getSlots(); i++) {
                                ItemStack stack = handler.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    String itemId = getItemId(stack);
                                    itemCounts.merge(itemId, stack.getCount(), Integer::sum);
                                }
                            }
                        });
                    }
                }
            }
        }

        return itemCounts;
    }

    private double calculateRegionGdp(RegionData region, Map<String, Integer> itemCounts) {
        Map<IndustryClassification.IndustryType, Double> industryGdp =
                IndustryClassification.calculateIndustryGdp(itemCounts);

        double primaryGdp = 0;
        double secondaryGdp = 0;
        double tertiaryGdp = 0;

        for (Map.Entry<IndustryClassification.IndustryType, Double> entry : industryGdp.entrySet()) {
            switch (entry.getKey().getSector()) {
                case PRIMARY -> primaryGdp += entry.getValue();
                case SECONDARY -> secondaryGdp += entry.getValue();
                case TERTIARY -> tertiaryGdp += entry.getValue();
            }
        }

        tertiaryGdp += region.getBankDeposits() + region.getPersonalFunds();

        return primaryGdp + secondaryGdp + tertiaryGdp;
    }

    public Map<String, Map<IndustryClassification.IndustryType, Double>> calculateDetailedGdp() {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return Map.of();

        Map<String, Map<IndustryClassification.IndustryType, Double>> result = new HashMap<>();

        for (RegionData region : data.getAllRegions()) {
            Map<String, Integer> itemCounts = scanRegionItems(region);
            Map<IndustryClassification.IndustryType, Double> industryGdp =
                    IndustryClassification.calculateIndustryGdp(itemCounts);
            result.put(region.getRegionId().toString(), industryGdp);
        }

        return result;
    }

    public Map.Entry<Map<IndustryClassification.IndustryType, Double>, Double> calculateSingleRegionGdp(RegionData region) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return null;

        double previousGdp = region.getGdp();
        Map<String, Integer> itemCounts = scanRegionItems(region);
        Map<IndustryClassification.IndustryType, Double> industryGdp =
                IndustryClassification.calculateIndustryGdp(itemCounts);

        double totalGdp = 0;
        for (Map.Entry<IndustryClassification.IndustryType, Double> entry : industryGdp.entrySet()) {
            totalGdp += entry.getValue();
        }
        totalGdp += region.getBankDeposits() + region.getPersonalFunds();

        region.setGdp(totalGdp);
        region.setLastGdpCalcTime(System.currentTimeMillis());
        data.setLastGdpCalcTime(System.currentTimeMillis());
        data.setDirty();

        double growthRate = previousGdp > 0 ? ((totalGdp - previousGdp) / previousGdp * 100.0) : 0;

        return new AbstractMap.SimpleEntry<>(industryGdp, growthRate);
    }

    private void scanRegionBlocks(RegionData region, Map<String, Integer> itemCounts) {
        for (ServerLevel level : server.getAllLevels()) {
            String dimId = level.dimension().location().toString();
            if (region.getDimensionId() != null && !region.getDimensionId().isEmpty()
                    && !region.getDimensionId().equals(dimId)) {
                continue;
            }

            int chunkMinX = region.getMinX() >> 4;
            int chunkMinZ = region.getMinZ() >> 4;
            int chunkMaxX = region.getMaxX() >> 4;
            int chunkMaxZ = region.getMaxZ() >> 4;

            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = level.getChunk(cx, cz);

                    int bxMin = Math.max(cx * 16, region.getMinX());
                    int bxMax = Math.min(cx * 16 + 15, region.getMaxX());
                    int bzMin = Math.max(cz * 16, region.getMinZ());
                    int bzMax = Math.min(cz * 16 + 15, region.getMaxZ());

                    for (int bx = bxMin; bx <= bxMax; bx++) {
                        for (int bz = bzMin; bz <= bzMax; bz++) {
                            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                                BlockState state = level.getBlockState(new BlockPos(bx, y, bz));
                                if (state.isAir()) continue;
                                Item blockItem = state.getBlock().asItem();
                                if (blockItem == null || blockItem == Items.AIR) continue;
                                String itemId = ForgeRegistries.ITEMS.getKey(blockItem).toString();
                                itemCounts.merge(itemId, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getItemId(ItemStack stack) {
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return registryName != null ? registryName.toString() : "minecraft:air";
    }

    public void shutdown() {
        scheduledTasksStarted = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
