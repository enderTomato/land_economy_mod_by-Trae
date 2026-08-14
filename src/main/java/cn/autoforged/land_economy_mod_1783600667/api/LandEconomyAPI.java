package cn.autoforged.land_economy_mod_1783600667.api;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;

import java.util.*;

/**
 * Public API for other mods to interact with the Land Economy system.
 *
 * <区域名称_区域创建者名称> - BlockName_BlockCrafterName
 * <申请加入者名称> - ApplicantName
 * <区域名称_创建者方名单> - BlockName_BlockCrafterList
 * <全部_创建者发名单> - all_BlockCrafterList
 */
public class LandEconomyAPI {

    private static EconomySavedData getData() {
        return LandEconomyMod.getEconomyData();
    }

    /**
     * @return current player's personal funds
     */
    public static double getPlayerFunds(UUID playerId) {
        EconomySavedData data = getData();
        if (data == null) return 0;
        return data.getPlayerFunds(playerId);
    }

    /**
     * @return map of all players' personal funds (UUID -> amount)
     */
    public static Map<UUID, Double> getAllPlayerFunds() {
        EconomySavedData data = getData();
        if (data == null) return Map.of();
        return data.getAllPlayerFunds();
    }

    /**
     * @return current region's bank deposits for the region containing the given position
     */
    public static double getRegionBankDeposits(UUID regionId) {
        EconomySavedData data = getData();
        if (data == null) return 0;
        RegionData region = data.getRegion(regionId);
        return region != null ? region.getBankDeposits() : 0;
    }

    /**
     * BlockName_BlockCrafterName: region name_owner UUID
     */
    public static String getBlockName_BlockCrafterName(UUID regionId) {
        EconomySavedData data = getData();
        if (data == null) return "";
        RegionData region = data.getRegion(regionId);
        if (region == null) return "";
        return region.getName() + "_" + (region.getOwner() != null ? region.getOwner().toString() : "unknown");
    }

    /**
     * ApplicantName: returns the applicant's name for a pending join request
     */
    public static String getApplicantName(UUID regionId, UUID applicantId) {
        EconomySavedData data = getData();
        if (data == null) return "";
        RegionData region = data.getRegion(regionId);
        if (region == null || !region.hasPendingRequest(applicantId)) return "";
        return applicantId.toString();
    }

    /**
     * BlockName_BlockCrafterList: list of pending applicants for a region
     */
    public static List<UUID> getBlockName_BlockCrafterList(UUID regionId) {
        EconomySavedData data = getData();
        if (data == null) return List.of();
        RegionData region = data.getRegion(regionId);
        if (region == null) return List.of();
        return new ArrayList<>(region.getPendingJoinRequests().keySet());
    }

    /**
     * all_BlockCrafterList: all pending join requests across all regions
     */
    public static Map<UUID, List<UUID>> getAll_BlockCrafterList() {
        EconomySavedData data = getData();
        if (data == null) return Map.of();
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (RegionData region : data.getAllRegions()) {
            if (!region.getPendingJoinRequests().isEmpty()) {
                result.put(region.getRegionId(), new ArrayList<>(region.getPendingJoinRequests().keySet()));
            }
        }
        return result;
    }
}
