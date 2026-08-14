package cn.autoforged.land_economy_mod_1783600667.economy;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PopulationEngine {

    private static final PopulationEngine INSTANCE = new PopulationEngine();

    private MinecraftServer server;
    private long tickCounter = 0;
    private boolean started = false;

    private PopulationEngine() {}

    public static PopulationEngine getInstance() {
        return INSTANCE;
    }

    public void startScheduledTasks(MinecraftServer server) {
        if (started) return;
        this.server = server;
        this.started = true;
        LandEconomyMod.LOGGER.info("Population Engine started.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (server == null) return;
        if (!server.isRunning()) return;

        tickCounter++;
        int checkIntervalTicks = ModConfig.COMMON.virtualPopGrowthCheckHours.get() * 60 * 60 * 20;
        if (tickCounter % Math.max(checkIntervalTicks, 1200) != 0) return;

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return;

        checkPopulationGrowth(data);
    }

    public synchronized void checkPopulationGrowth(EconomySavedData data) {
        int base = ModConfig.COMMON.virtualPopGrowthBase.get();
        int exponent = ModConfig.COMMON.virtualPopGrowthExponent.get();
        int requiredChecks = ModConfig.COMMON.virtualPopGrowthCheckCount.get();
        double gdpThreshold = ModConfig.COMMON.populationGdpConditionThresholdPercent.get() / 100.0;
        double fundsThreshold = ModConfig.COMMON.populationFundsConditionThresholdPercent.get() / 100.0;

        long currentTime = System.currentTimeMillis();

        for (RegionData region : data.getAllRegions()) {
            if (region.isFlyland()) continue;
            int population = region.getPopulation();
            double gdp = region.getGdp();
            double funds = region.getBankDeposits() + region.getPersonalFunds();

            boolean gdpConditionMet = gdpThreshold <= 0 || gdp >= population * gdpThreshold;
            boolean fundsConditionMet = fundsThreshold <= 0 || funds >= population * fundsThreshold;

            if (gdpConditionMet && fundsConditionMet) {
                region.setConsecutiveGrowthChecks(region.getConsecutiveGrowthChecks() + 1);
            } else {
                region.setConsecutiveGrowthChecks(0);
            }

            if (region.getConsecutiveGrowthChecks() >= requiredChecks) {
                int growth = (int) Math.pow(base, exponent);
                region.setPopulation(population + growth);
                region.setConsecutiveGrowthChecks(0);

                LandEconomyMod.LOGGER.debug("Population grew in region {}: {} -> {}",
                        region.getName(), population, population + growth);
            }

            region.setLastPopulationCalcTime(currentTime);
        }

        data.incrementPopulationChecks();
        data.setLastPopulationCheckTime(currentTime);
        data.setDirty();
    }

    public void forceCheck() {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data != null) {
            checkPopulationGrowth(data);
        }
    }

    public void shutdown() {
        started = false;
    }
}
