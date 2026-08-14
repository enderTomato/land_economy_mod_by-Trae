package cn.autoforged.land_economy_mod_1783600667;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class ModConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    public static class Common {

        public final ForgeConfigSpec.IntValue gdpCalcIntervalMinutes;
        public final ForgeConfigSpec.IntValue virtualPopGrowthBase;
        public final ForgeConfigSpec.IntValue virtualPopGrowthExponent;
        public final ForgeConfigSpec.IntValue virtualPopGrowthCheckCount;
        public final ForgeConfigSpec.IntValue virtualPopGrowthCheckHours;
        public final ForgeConfigSpec.DoubleValue populationGdpConditionThresholdPercent;
        public final ForgeConfigSpec.DoubleValue populationFundsConditionThresholdPercent;
        public final ForgeConfigSpec.BooleanValue enableMultiThreadedGdpCalc;
        public final ForgeConfigSpec.BooleanValue enableContainerOnlyGdpCalc;
        public final ForgeConfigSpec.IntValue maxConcurrentGdpCalc;
        public final ForgeConfigSpec.IntValue flylandMaxWidth;
        public final ForgeConfigSpec.IntValue flylandMaxLength;
        public final ForgeConfigSpec.IntValue gdpHistoryLength;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> buildingBlocks;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> primaryMiningItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> primaryAgricultureItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> primaryForestryItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> primaryFishingItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> secondaryManufacturingItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> secondarySmeltingItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> secondaryConstructionItems;
        public final ForgeConfigSpec.DoubleValue baseItemPresetValue;
        public final ForgeConfigSpec.DoubleValue claimOutlayNew;
        public final ForgeConfigSpec.DoubleValue claimOutlayExpand;
        public final ForgeConfigSpec.IntValue mathGdpCooldownMinutes;
        public final ForgeConfigSpec.ConfigValue<String> regionDisplayMode;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("gdp");
            builder.comment("GDP calculation interval in minutes");
            this.gdpCalcIntervalMinutes = builder
                    .defineInRange("gdpCalcIntervalMinutes", 120, 1, 10080);
            builder.comment("Enable multi-threaded GDP calculation (default: false)");
            this.enableMultiThreadedGdpCalc = builder
                    .define("enableMultiThreadedGdpCalc", false);
            builder.comment("Only scan container items for GDP (skip non-container blocks)");
            this.enableContainerOnlyGdpCalc = builder
                    .define("enableContainerOnlyGdpCalc", false);
            builder.comment("Max concurrent GDP calculation tasks (only when multi-threaded enabled)");
            this.maxConcurrentGdpCalc = builder
                    .defineInRange("maxConcurrentGdpCalc", 1, 1, 8);
            builder.comment("GDP history length (number of records to keep)");
            this.gdpHistoryLength = builder
                    .defineInRange("gdpHistoryLength", 50, 10, 200);
            builder.pop();

            builder.push("population");
            builder.comment("Virtual population growth base value");
            this.virtualPopGrowthBase = builder
                    .defineInRange("virtualPopGrowthBase", 1, 1, 100);
            builder.comment("Virtual population growth exponent");
            this.virtualPopGrowthExponent = builder
                    .defineInRange("virtualPopGrowthExponent", 2, 1, 10);
            builder.comment("Number of consecutive checks required for population growth");
            this.virtualPopGrowthCheckCount = builder
                    .defineInRange("virtualPopGrowthCheckCount", 4, 1, 100);
            builder.comment("Hours between each population growth check");
            this.virtualPopGrowthCheckHours = builder
                    .defineInRange("virtualPopGrowthCheckHours", 6, 1, 168);
            builder.comment("GDP threshold percentage for population growth (0 = disabled)");
            this.populationGdpConditionThresholdPercent = builder
                    .defineInRange("populationGdpConditionThresholdPercent", 0.0, 0.0, 100.0);
            builder.comment("Funds threshold percentage for population growth (0 = disabled)");
            this.populationFundsConditionThresholdPercent = builder
                    .defineInRange("populationFundsConditionThresholdPercent", 0.0, 0.0, 100.0);
            builder.pop();

            builder.push("flyland");
            builder.comment("Maximum width of a flyland (in chunks)");
            this.flylandMaxWidth = builder
                    .defineInRange("flylandMaxWidth", 2, 1, 32);
            builder.comment("Maximum length of a flyland (in chunks)");
            this.flylandMaxLength = builder
                    .defineInRange("flylandMaxLength", 2, 1, 32);
            builder.pop();

            builder.push("industry_primary");
            this.primaryMiningItems = builder
                    .comment("Items classified as mining industry")
                    .defineListAllowEmpty("miningItems", List.of(), s -> s instanceof String);
            this.primaryAgricultureItems = builder
                    .comment("Items classified as agriculture industry")
                    .defineListAllowEmpty("agricultureItems",
                            List.of("minecraft:wheat", "minecraft:carrot", "minecraft:potato",
                                    "minecraft:beetroot", "minecraft:beef", "minecraft:porkchop",
                                    "minecraft:mutton", "minecraft:rabbit"),
                            s -> s instanceof String);
            this.primaryForestryItems = builder
                    .comment("Items classified as forestry industry")
                    .defineListAllowEmpty("forestryItems",
                            List.of("minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
                                    "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
                                    "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling",
                                    "minecraft:jungle_sapling", "minecraft:acacia_sapling", "minecraft:dark_oak_sapling"),
                            s -> s instanceof String);
            this.primaryFishingItems = builder
                    .comment("Items classified as fishing industry")
                    .defineListAllowEmpty("fishingItems",
                            List.of("minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish"),
                            s -> s instanceof String);
            builder.pop();

            builder.push("industry_secondary");
            this.secondaryManufacturingItems = builder
                    .comment("Items classified as manufacturing industry")
                    .defineListAllowEmpty("manufacturingItems",
                            List.of("minecraft:leather_helmet", "minecraft:leather_chestplate",
                                    "minecraft:leather_leggings", "minecraft:leather_boots",
                                    "minecraft:iron_sword", "minecraft:iron_pickaxe",
                                    "minecraft:iron_shovel", "minecraft:iron_axe"),
                            s -> s instanceof String);
            this.secondarySmeltingItems = builder
                    .comment("Items classified as smelting industry")
                    .defineListAllowEmpty("smeltingItems",
                            List.of("minecraft:gold_block", "minecraft:iron_block",
                                    "minecraft:copper_block", "minecraft:diamond_block",
                                    "minecraft:gold_ingot", "minecraft:iron_ingot",
                                    "minecraft:copper_ingot", "minecraft:netherite_ingot"),
                            s -> s instanceof String);
            this.secondaryConstructionItems = builder
                    .comment("Items classified as construction industry (building blocks)")
                    .defineListAllowEmpty("constructionItems",
                            List.of("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                                    "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                                    "minecraft:cobblestone", "minecraft:stone", "minecraft:smooth_stone",
                                    "minecraft:stone_bricks", "minecraft:nether_bricks", "minecraft:end_stone",
                                    "minecraft:bricks", "minecraft:quartz_block", "minecraft:prismarine",
                                    "minecraft:glass", "minecraft:glass_pane"),
                            s -> s instanceof String);
            builder.pop();

            builder.push("economy");
            builder.comment("Base preset value for items not explicitly valued");
            this.baseItemPresetValue = builder
                    .defineInRange("baseItemPresetValue", 1.0, 0.01, 1000.0);
            builder.comment("Cost to claim a new region");
            this.claimOutlayNew = builder
                    .defineInRange("claimOutlayNew", 0.0, 0.0, 1000000.0);
            builder.comment("Cost to expand a region");
            this.claimOutlayExpand = builder
                    .defineInRange("claimOutlayExpand", 0.0, 0.0, 1000000.0);
            builder.comment("Cooldown in minutes for non-admin /math gdp usage");
            this.mathGdpCooldownMinutes = builder
                    .defineInRange("mathGdpCooldownMinutes", 0, 0, 10080);
            builder.pop();

            builder.push("display");
            builder.comment("Default region-entry notification style: title (Traveler's Title style, screen title) or actionbar (vanilla). Each player can override per-player with /land display.");
            this.regionDisplayMode = builder
                    .define("regionDisplayMode", "title");
            builder.pop();

            builder.push("building_blocks");
            builder.comment("Full list of building blocks for construction GDP");
            this.buildingBlocks = builder
                    .defineListAllowEmpty("blocks",
                            List.of("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                                    "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
                                    "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:birch_slab",
                                    "minecraft:jungle_slab", "minecraft:acacia_slab", "minecraft:dark_oak_slab",
                                    "minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:birch_stairs",
                                    "minecraft:jungle_stairs", "minecraft:acacia_stairs", "minecraft:dark_oak_stairs",
                                    "minecraft:fence", "minecraft:fence_gate", "minecraft:oak_door", "minecraft:oak_trapdoor",
                                    "minecraft:oak_sign", "minecraft:oak_hanging_sign",
                                    "minecraft:cobblestone", "minecraft:stone", "minecraft:smooth_stone",
                                    "minecraft:polished_andesite", "minecraft:polished_granite", "minecraft:polished_diorite",
                                    "minecraft:tuff", "minecraft:polished_tuff", "minecraft:stone_bricks",
                                    "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks",
                                    "minecraft:polished_deepslate", "minecraft:polished_deepslate_bricks", "minecraft:polished_deepslate_tiles",
                                    "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:smooth_sandstone",
                                    "minecraft:cut_sandstone", "minecraft:chiseled_sandstone",
                                    "minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks",
                                    "minecraft:nether_bricks", "minecraft:red_nether_bricks",
                                    "minecraft:basalt", "minecraft:polished_basalt",
                                    "minecraft:end_stone", "minecraft:end_stone_bricks",
                                    "minecraft:quartz_block", "minecraft:chiseled_quartz_block", "minecraft:quartz_pillar",
                                    "minecraft:quartz_bricks", "minecraft:smooth_quartz",
                                    "minecraft:bricks", "minecraft:glowstone",
                                    "minecraft:prismarine", "minecraft:sea_lantern", "minecraft:dark_prismarine", "minecraft:prismarine_bricks",
                                    "minecraft:terracotta",
                                    "minecraft:white_glazed_terracotta", "minecraft:orange_glazed_terracotta",
                                    "minecraft:magenta_glazed_terracotta", "minecraft:light_blue_glazed_terracotta",
                                    "minecraft:yellow_glazed_terracotta", "minecraft:lime_glazed_terracotta",
                                    "minecraft:pink_glazed_terracotta", "minecraft:gray_glazed_terracotta",
                                    "minecraft:light_gray_glazed_terracotta", "minecraft:cyan_glazed_terracotta",
                                    "minecraft:purple_glazed_terracotta", "minecraft:blue_glazed_terracotta",
                                    "minecraft:brown_glazed_terracotta", "minecraft:green_glazed_terracotta",
                                    "minecraft:red_glazed_terracotta", "minecraft:black_glazed_terracotta",
                                    "minecraft:white_concrete", "minecraft:orange_concrete", "minecraft:magenta_concrete",
                                    "minecraft:light_blue_concrete", "minecraft:yellow_concrete", "minecraft:lime_concrete",
                                    "minecraft:pink_concrete", "minecraft:gray_concrete", "minecraft:light_gray_concrete",
                                    "minecraft:cyan_concrete", "minecraft:purple_concrete", "minecraft:blue_concrete",
                                    "minecraft:brown_concrete", "minecraft:green_concrete", "minecraft:red_concrete",
                                    "minecraft:black_concrete",
                                    "minecraft:white_wool", "minecraft:orange_wool", "minecraft:magenta_wool",
                                    "minecraft:light_blue_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
                                    "minecraft:pink_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
                                    "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:blue_wool",
                                    "minecraft:brown_wool", "minecraft:green_wool", "minecraft:red_wool",
                                    "minecraft:black_wool",
                                    "minecraft:white_carpet", "minecraft:orange_carpet", "minecraft:magenta_carpet",
                                    "minecraft:light_blue_carpet", "minecraft:yellow_carpet", "minecraft:lime_carpet",
                                    "minecraft:pink_carpet", "minecraft:gray_carpet", "minecraft:light_gray_carpet",
                                    "minecraft:cyan_carpet", "minecraft:purple_carpet", "minecraft:blue_carpet",
                                    "minecraft:brown_carpet", "minecraft:green_carpet", "minecraft:red_carpet",
                                    "minecraft:black_carpet",
                                    "minecraft:white_concrete_powder", "minecraft:orange_concrete_powder",
                                    "minecraft:magenta_concrete_powder", "minecraft:light_blue_concrete_powder",
                                    "minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder",
                                    "minecraft:pink_concrete_powder", "minecraft:gray_concrete_powder",
                                    "minecraft:light_gray_concrete_powder", "minecraft:cyan_concrete_powder",
                                    "minecraft:purple_concrete_powder", "minecraft:blue_concrete_powder",
                                    "minecraft:brown_concrete_powder", "minecraft:green_concrete_powder",
                                    "minecraft:red_concrete_powder", "minecraft:black_concrete_powder",
                                    "minecraft:white_stained_glass", "minecraft:orange_stained_glass",
                                    "minecraft:magenta_stained_glass", "minecraft:light_blue_stained_glass",
                                    "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass",
                                    "minecraft:pink_stained_glass", "minecraft:gray_stained_glass",
                                    "minecraft:light_gray_stained_glass", "minecraft:cyan_stained_glass",
                                    "minecraft:purple_stained_glass", "minecraft:blue_stained_glass",
                                    "minecraft:brown_stained_glass", "minecraft:green_stained_glass",
                                    "minecraft:red_stained_glass", "minecraft:black_stained_glass",
                                    "minecraft:copper_block", "minecraft:cut_copper", "minecraft:cut_copper_slab",
                                    "minecraft:cut_copper_stairs", "minecraft:waxed_copper_block",
                                    "minecraft:waxed_cut_copper", "minecraft:waxed_cut_copper_slab",
                                    "minecraft:waxed_cut_copper_stairs",
                                    "minecraft:torch", "minecraft:soul_torch", "minecraft:lantern", "minecraft:soul_lantern",
                                    "minecraft:sea_lantern", "minecraft:end_rod", "minecraft:pearlescent_froglight",
                                    "minecraft:candle", "minecraft:jack_o_lantern", "minecraft:glowstone"),
                            s -> s instanceof String);
            builder.pop();
        }
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_SPEC);
    }
}
