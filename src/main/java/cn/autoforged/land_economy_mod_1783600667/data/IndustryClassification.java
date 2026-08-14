package cn.autoforged.land_economy_mod_1783600667.data;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IndustryClassification {

    public enum IndustrySector {
        PRIMARY, SECONDARY, TERTIARY
    }

    public enum IndustryType {
        MINING(IndustrySector.PRIMARY, "mining"),
        AGRICULTURE(IndustrySector.PRIMARY, "agriculture"),
        FORESTRY(IndustrySector.PRIMARY, "forestry"),
        FISHING(IndustrySector.PRIMARY, "fishing"),
        MANUFACTURING(IndustrySector.SECONDARY, "manufacturing"),
        SMELTING(IndustrySector.SECONDARY, "smelting"),
        CONSTRUCTION(IndustrySector.SECONDARY, "construction"),
        BANK_DEPOSITS(IndustrySector.TERTIARY, "bank_deposits");

        private final IndustrySector sector;
        private final String name;

        IndustryType(IndustrySector sector, String name) {
            this.sector = sector;
            this.name = name;
        }

        public IndustrySector getSector() { return sector; }
        public String getName() { return name; }
    }

    private static final Map<String, IndustryType> ITEM_INDUSTRY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Double> ITEM_PRESET_VALUES = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    public static void initialize() {
        if (initialized) return;
        synchronized (IndustryClassification.class) {
            if (initialized) return;

            ITEM_INDUSTRY_MAP.clear();
            ITEM_PRESET_VALUES.clear();

            registerItems(IndustryType.MINING, ModConfig.COMMON.primaryMiningItems.get());
            registerItems(IndustryType.AGRICULTURE, ModConfig.COMMON.primaryAgricultureItems.get());
            registerItems(IndustryType.FORESTRY, ModConfig.COMMON.primaryForestryItems.get());
            registerItems(IndustryType.FISHING, ModConfig.COMMON.primaryFishingItems.get());
            registerItems(IndustryType.MANUFACTURING, ModConfig.COMMON.secondaryManufacturingItems.get());
            registerItems(IndustryType.SMELTING, ModConfig.COMMON.secondarySmeltingItems.get());
            registerItems(IndustryType.CONSTRUCTION, ModConfig.COMMON.secondaryConstructionItems.get());

            applyDefaultPresetValues();

            initialized = true;
            LandEconomyMod.LOGGER.info("Industry classification initialized with {} items", ITEM_INDUSTRY_MAP.size());
        }
    }

    private static void registerItems(IndustryType type, List<? extends String> itemStrings) {
        for (String itemStr : itemStrings) {
            String normalized = normalizeItemId(itemStr);
            if (normalized != null) {
                ITEM_INDUSTRY_MAP.put(normalized, type);
            }
        }
    }

    private static String normalizeItemId(String id) {
        if (id == null || id.isEmpty()) return null;
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        return id;
    }

    private static void applyDefaultPresetValues() {
        double baseValue = ModConfig.COMMON.baseItemPresetValue.get();

        Set<String> allItems = new HashSet<>(ITEM_INDUSTRY_MAP.keySet());

        Set<String> buildingBlocks = ModConfig.COMMON.buildingBlocks.get()
                .stream().map(s -> normalizeItemId(s)).filter(Objects::nonNull).collect(Collectors.toSet());
        allItems.addAll(buildingBlocks);
        for (String itemId : buildingBlocks) {
            ITEM_INDUSTRY_MAP.putIfAbsent(itemId, IndustryType.CONSTRUCTION);
        }

        for (String itemId : allItems) {
            if (!ITEM_PRESET_VALUES.containsKey(itemId)) {
                IndustryType type = ITEM_INDUSTRY_MAP.get(itemId);
                double value = getDefaultPresetValue(itemId, type, baseValue);
                ITEM_PRESET_VALUES.put(itemId, value);
            }
        }
    }

    private static double getDefaultPresetValue(String itemId, IndustryType type, double baseValue) {
        if (itemId.contains("diamond_block")) return 9.0 * baseValue * 5;
        if (itemId.contains("netherite_ingot")) return 50.0 * baseValue;
        if (itemId.contains("gold_block")) return 9.0 * baseValue * 3;
        if (itemId.contains("iron_block")) return 9.0 * baseValue * 1.5;
        if (itemId.contains("diamond")) return 5.0 * baseValue;
        if (itemId.contains("gold_ingot")) return 3.0 * baseValue;
        if (itemId.contains("iron_ingot")) return 1.5 * baseValue;
        if (itemId.contains("netherite")) return 50.0 * baseValue;

        if (itemId.contains("helmet") || itemId.contains("chestplate") ||
                itemId.contains("leggings") || itemId.contains("boots")) {
            if (itemId.contains("diamond")) return 8.0 * baseValue;
            if (itemId.contains("iron")) return 4.0 * baseValue;
            if (itemId.contains("gold")) return 3.0 * baseValue;
            return 2.0 * baseValue;
        }
        if (itemId.contains("sword") || itemId.contains("pickaxe") ||
                itemId.contains("shovel") || itemId.contains("axe")) {
            if (itemId.contains("diamond")) return 5.0 * baseValue;
            if (itemId.contains("iron")) return 3.0 * baseValue;
            return 2.0 * baseValue;
        }

        if (type == IndustryType.CONSTRUCTION) return baseValue * 0.5;
        if (type == IndustryType.FORESTRY) return baseValue * 0.3;
        if (type == IndustryType.AGRICULTURE) return baseValue * 0.2;
        if (type == IndustryType.FISHING) return baseValue * 0.4;
        if (type == IndustryType.MINING) return baseValue * 0.5;

        return baseValue;
    }

    public static IndustryType classifyItem(String itemId) {
        if (!initialized) initialize();
        return ITEM_INDUSTRY_MAP.get(normalizeItemId(itemId));
    }

    public static double getPresetValue(String itemId) {
        if (!initialized) initialize();
        return ITEM_PRESET_VALUES.getOrDefault(normalizeItemId(itemId),
                ModConfig.COMMON.baseItemPresetValue.get());
    }

    public static Map<IndustryType, Double> calculateIndustryGdp(Map<String, Integer> itemCounts) {
        if (!initialized) initialize();

        Map<IndustryType, Double> industryGdp = new HashMap<>();
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue();
            IndustryType type = ITEM_INDUSTRY_MAP.get(itemId);
            if (type != null) {
                double value = ITEM_PRESET_VALUES.getOrDefault(itemId, ModConfig.COMMON.baseItemPresetValue.get());
                industryGdp.merge(type, count * value, Double::sum);
            }
        }
        return industryGdp;
    }

    public static Set<String> getItemsInIndustry(IndustryType type) {
        if (!initialized) initialize();
        return ITEM_INDUSTRY_MAP.entrySet().stream()
                .filter(e -> e.getValue() == type)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public static boolean addItemToIndustry(String itemId, IndustryType type) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null) return false;
        ITEM_INDUSTRY_MAP.put(normalized, type);
        double value = getDefaultPresetValue(normalized, type, ModConfig.COMMON.baseItemPresetValue.get());
        ITEM_PRESET_VALUES.put(normalized, value);
        return true;
    }

    public static void setPresetValue(String itemId, double value) {
        String normalized = normalizeItemId(itemId);
        if (normalized != null) {
            ITEM_PRESET_VALUES.put(normalized, value);
        }
    }

    public static void reset() {
        initialized = false;
    }
}
