package cn.autoforged.land_economy_mod_1783600667.data;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static cn.autoforged.land_economy_mod_1783600667.data.RegionType.setOverride;

public class EconomySavedData extends SavedData {

    private static final String NAME = "land_economy_mod_data";
    private static final int CURRENT_VERSION = 1;

    private int dataVersion = CURRENT_VERSION;
    private final Map<UUID, RegionData> regions = new ConcurrentHashMap<>();
    private final List<Double> gdpHistory = Collections.synchronizedList(new ArrayList<>());
    private long totalGdpCalculations = 0;
    private long totalPopulationChecks = 0;
    private long lastGdpCalcTime = 0;
    private long lastPopulationCheckTime = 0;
    private double globalBankDeposits = 0;
    private double globalPersonalFunds = 0;
    private final Map<UUID, Double> playerPersonalFunds = new ConcurrentHashMap<>();
    private final Map<String, double[]> regionTypeOverrides = new ConcurrentHashMap<>();
    // 玩家UUID -> 区域进入提示显示方式（"title" 屏幕标题 / "actionbar" 原版 ActionBar）
    private final Map<UUID, String> playerDisplayModes = new ConcurrentHashMap<>();

    public EconomySavedData() {}

    public EconomySavedData(CompoundTag tag) {
        this.dataVersion = tag.getInt("DataVersion");
        this.totalGdpCalculations = tag.getLong("TotalGDPCalculations");
        this.totalPopulationChecks = tag.getLong("TotalPopulationChecks");
        this.lastGdpCalcTime = tag.getLong("LastGDPCalcTime");
        this.lastPopulationCheckTime = tag.getLong("LastPopulationCheckTime");
        this.globalBankDeposits = tag.getDouble("GlobalBankDeposits");
        this.globalPersonalFunds = tag.getDouble("GlobalPersonalFunds");

        if (tag.contains("PlayerFunds")) {
            ListTag fundsList = tag.getList("PlayerFunds", Tag.TAG_COMPOUND);
            for (int i = 0; i < fundsList.size(); i++) {
                CompoundTag entry = fundsList.getCompound(i);
                playerPersonalFunds.put(entry.getUUID("Player"), entry.getDouble("Funds"));
            }
        }

        if (tag.contains("Regions")) {
            ListTag regionsList = tag.getList("Regions", Tag.TAG_COMPOUND);
            for (int i = 0; i < regionsList.size(); i++) {
                CompoundTag regionTag = regionsList.getCompound(i);
                RegionData region = RegionData.fromNbt(regionTag);
                regions.put(region.getRegionId(), region);
            }
        }

        if (tag.contains("GDPHistory")) {
            ListTag historyList = tag.getList("GDPHistory", Tag.TAG_DOUBLE);
            for (int i = 0; i < historyList.size(); i++) {
                gdpHistory.add(historyList.getDouble(i));
            }
        }

        if (tag.contains("RegionTypeOverrides")) {
            ListTag overrideList = tag.getList("RegionTypeOverrides", Tag.TAG_COMPOUND);
            for (int i = 0; i < overrideList.size(); i++) {
                CompoundTag entry = overrideList.getCompound(i);
                String name = entry.getString("Name");
                double gdp = entry.getDouble("MinGdp");
                double pop = entry.getDouble("MinPopulation");
                regionTypeOverrides.put(name, new double[]{gdp, pop});
                RegionType.setOverride(name, gdp, pop);
            }
        }

        if (tag.contains("PlayerDisplayModes")) {
            ListTag modeList = tag.getList("PlayerDisplayModes", Tag.TAG_COMPOUND);
            for (int i = 0; i < modeList.size(); i++) {
                CompoundTag entry = modeList.getCompound(i);
                playerDisplayModes.put(entry.getUUID("Player"), entry.getString("Mode"));
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", dataVersion);
        tag.putLong("TotalGDPCalculations", totalGdpCalculations);
        tag.putLong("TotalPopulationChecks", totalPopulationChecks);
        tag.putLong("LastGDPCalcTime", lastGdpCalcTime);
        tag.putLong("LastPopulationCheckTime", lastPopulationCheckTime);
        tag.putDouble("GlobalBankDeposits", globalBankDeposits);
        tag.putDouble("GlobalPersonalFunds", globalPersonalFunds);

        ListTag fundsList = new ListTag();
        for (Map.Entry<UUID, Double> entry : playerPersonalFunds.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Player", entry.getKey());
            entryTag.putDouble("Funds", entry.getValue());
            fundsList.add(entryTag);
        }
        tag.put("PlayerFunds", fundsList);

        ListTag regionsList = new ListTag();
        for (RegionData region : regions.values()) {
            regionsList.add(region.toNbt());
        }
        tag.put("Regions", regionsList);

        ListTag historyList = new ListTag();
        synchronized (gdpHistory) {
            for (Double value : gdpHistory) {
                historyList.add(net.minecraft.nbt.DoubleTag.valueOf(value));
            }
        }
        tag.put("GDPHistory", historyList);

        ListTag overrideList = new ListTag();
        for (Map.Entry<String, double[]> entry : regionTypeOverrides.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Name", entry.getKey());
            entryTag.putDouble("MinGdp", entry.getValue()[0]);
            entryTag.putDouble("MinPopulation", entry.getValue()[1]);
            overrideList.add(entryTag);
        }
        tag.put("RegionTypeOverrides", overrideList);

        ListTag modeList = new ListTag();
        for (Map.Entry<UUID, String> entry : playerDisplayModes.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Player", entry.getKey());
            entryTag.putString("Mode", entry.getValue());
            modeList.add(entryTag);
        }
        tag.put("PlayerDisplayModes", modeList);

        return tag;
    }

    // Region management

    public RegionData createRegion(UUID owner, RegionData region) {
        regions.put(region.getRegionId(), region);
        setDirty();
        return region;
    }

    public RegionData getRegion(UUID regionId) {
        return regions.get(regionId);
    }

    public RegionData getRegionByOwner(UUID owner) {
        for (RegionData region : regions.values()) {
            if (owner.equals(region.getOwner())) {
                return region;
            }
        }
        return null;
    }

    public Collection<RegionData> getAllRegions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public boolean removeRegion(UUID regionId) {
        RegionData removed = regions.remove(regionId);
        if (removed != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public int getRegionCount() {
        return regions.size();
    }

    // GDP management

    public List<Double> getGdpHistory() {
        return Collections.unmodifiableList(gdpHistory);
    }

    public void addGdpRecord(double gdp) {
        int maxHistory = cn.autoforged.land_economy_mod_1783600667.ModConfig.COMMON.gdpHistoryLength.get();
        synchronized (gdpHistory) {
            gdpHistory.add(gdp);
            while (gdpHistory.size() > maxHistory) {
                gdpHistory.remove(0);
            }
        }
        totalGdpCalculations++;
        setDirty();
    }

    public double getAverageGdp() {
        synchronized (gdpHistory) {
            if (gdpHistory.isEmpty()) return 0;
            return gdpHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
    }

    public double getTotalGdp() {
        return regions.values().stream().mapToDouble(RegionData::getGdp).sum();
    }

    // Population management

    public int getTotalPopulation() {
        return regions.values().stream().mapToInt(RegionData::getPopulation).sum();
    }

    // Global funds

    public double getGlobalBankDeposits() { return globalBankDeposits; }
    public void setGlobalBankDeposits(double deposits) {
        this.globalBankDeposits = deposits;
        setDirty();
    }

    public double getGlobalPersonalFunds() { return globalPersonalFunds; }
    public void setGlobalPersonalFunds(double funds) {
        this.globalPersonalFunds = funds;
        setDirty();
    }

    // Stats

    // Player personal funds

    public double getPlayerFunds(UUID playerId) {
        return playerPersonalFunds.getOrDefault(playerId, 0.0);
    }

    public void setPlayerFunds(UUID playerId, double amount) {
        if (amount < 0) amount = 0;
        playerPersonalFunds.put(playerId, amount);
        setDirty();
    }

    public void addPlayerFunds(UUID playerId, double amount) {
        double current = getPlayerFunds(playerId);
        double newVal = current + amount;
        if (newVal < 0) newVal = 0;
        playerPersonalFunds.put(playerId, newVal);
        setDirty();
    }

    public Map<UUID, Double> getAllPlayerFunds() {
        return Collections.unmodifiableMap(playerPersonalFunds);
    }

    // Region entry display mode (title / actionbar)

    public String getPlayerDisplayMode(UUID playerId) {
        return playerDisplayModes.get(playerId);
    }

    public void setPlayerDisplayMode(UUID playerId, String mode) {
        if (mode == null) return;
        String normalized = mode.trim().toLowerCase();
        if (!normalized.equals("title") && !normalized.equals("actionbar")) return;
        playerDisplayModes.put(playerId, normalized);
        setDirty();
    }

    // Region type overrides

    public void setRegionTypeOverride(String typeName, double gdp, double pop) {
        regionTypeOverrides.put(typeName, new double[]{gdp, pop});
        RegionType.setOverride(typeName, gdp, pop);
        setDirty();
    }

    public double[] getRegionTypeOverride(String typeName) {
        return regionTypeOverrides.get(typeName);
    }

    public long getTotalGdpCalculations() { return totalGdpCalculations; }
    public long getTotalPopulationChecks() { return totalPopulationChecks; }
    public void incrementPopulationChecks() {
        this.totalPopulationChecks++;
        setDirty();
    }
    public long getLastGdpCalcTime() { return lastGdpCalcTime; }
    public void setLastGdpCalcTime(long time) {
        this.lastGdpCalcTime = time;
        setDirty();
    }
    public long getLastPopulationCheckTime() { return lastPopulationCheckTime; }
    public void setLastPopulationCheckTime(long time) {
        this.lastPopulationCheckTime = time;
        setDirty();
    }

    // Factory

    public static EconomySavedData get(Level level) {
        if (level.isClientSide) {
            LandEconomyMod.LOGGER.warn("Attempted to access EconomySavedData from client side");
            return null;
        }
        var server = level.getServer();
        if (server == null) return null;
        var overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                EconomySavedData::new,
                EconomySavedData::new,
                NAME
        );
    }
}
