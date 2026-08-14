package cn.autoforged.land_economy_mod_1783600667.command;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.IndustryClassification;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionType;
import cn.autoforged.land_economy_mod_1783600667.economy.GDPEngine;
import cn.autoforged.land_economy_mod_1783600667.economy.PopulationEngine;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.MutableComponent;

public class EconomyCommandHandler {

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_DF = new DecimalFormat("#,##0");
    private static final DecimalFormat PCT_DF = new DecimalFormat("0.00");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static int showGdp(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        double totalGdp = data.getTotalGdp();
        double avgGdp = data.getAverageGdp();
        int regionCount = data.getRegionCount();

        ctx.getSource().sendSuccess(() -> Component.literal("=== GDP 报告 ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("GDP 总值: " + DF.format(totalGdp)).withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("平均 GDP: " + DF.format(avgGdp)).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("领地数量: " + regionCount).withStyle(ChatFormatting.YELLOW), false);

        // GDP growth indicator bar
        Component growthBar = buildGdpProgressBar(avgGdp > 0 ? 50.0 : 0.0);
        ctx.getSource().sendSuccess(() -> Component.literal("成长趋势: ").withStyle(ChatFormatting.GRAY)
                .copy().append(growthBar), false);

        ctx.getSource().sendSuccess(() -> Component.literal("总计算次数: " + data.getTotalGdpCalculations()).withStyle(ChatFormatting.GRAY), false);

        if (data.getLastGdpCalcTime() > 0) {
            String timeStr = formatTime(data.getLastGdpCalcTime());
            ctx.getSource().sendSuccess(() -> Component.literal("上次计算: " + timeStr).withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    public static int showGdpDetail(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        if (data.getRegionCount() == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.list.empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== GDP 详情 ===").withStyle(ChatFormatting.GOLD), false);

        for (RegionData region : data.getAllRegions()) {
            double gdp = region.getGdp();
            RegionType type = region.getRegionType();
            int pop = region.getPopulation();

            ctx.getSource().sendSuccess(() -> Component.literal("领地: " + region.getName())
                    .withStyle(ChatFormatting.YELLOW), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  类型: " + type.getDisplayName()
                    + " | GDP: " + DF.format(gdp) + " | 人口: " + pop)
                    .withStyle(ChatFormatting.WHITE), false);
        }

        // Show top GDP regions with progress bars
        if (data.getRegionCount() > 0) {
            double totalGdp = data.getTotalGdp();
            ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
            ctx.getSource().sendSuccess(() -> Component.literal("=== GDP 占比 ===").withStyle(ChatFormatting.GOLD), false);
            java.util.List<RegionData> sorted = new java.util.ArrayList<>(data.getAllRegions());
            sorted.sort((a, b) -> Double.compare(b.getGdp(), a.getGdp()));
            for (RegionData region : sorted) {
                double share = totalGdp > 0 ? (region.getGdp() / totalGdp * 100.0) : 0;
                Component bar = buildGdpProgressBar(share);
                ctx.getSource().sendSuccess(() -> Component.literal("  " + region.getName() + " ").withStyle(ChatFormatting.WHITE)
                        .copy().append(bar).append(Component.literal(" " + PCT_DF.format(share) + "%")), false);
            }
        }

        return 1;
    }

    public static int showPopulation(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        int totalPop = data.getTotalPopulation();
        int regionCount = data.getRegionCount();

        ctx.getSource().sendSuccess(() -> Component.literal("=== 人口报告 ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("虚拟人口总数: " + INT_DF.format(totalPop)).withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("平均每个领地: " + (regionCount > 0 ? INT_DF.format(totalPop / regionCount) : "0")).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("人口检查次数: " + data.getTotalPopulationChecks()).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("增长基数: " + ModConfig.COMMON.virtualPopGrowthBase.get()
                + " | 指数: " + ModConfig.COMMON.virtualPopGrowthExponent.get()
                + " | 所需检查: " + ModConfig.COMMON.virtualPopGrowthCheckCount.get()).withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    public static int showRegionType(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== 区域类型（当前生效值） ===").withStyle(ChatFormatting.GOLD), false);
        for (RegionType type : RegionType.values()) {
            double effectiveGdp = type.getMinGdp();
            int effectivePop = type.getMinPopulation();
            boolean isOverridden = effectiveGdp != type.getOriginalMinGdp() || effectivePop != type.getOriginalMinPopulation();
            String suffix = isOverridden ? " §e(已修改)" : "";
            ctx.getSource().sendSuccess(() -> Component.literal(type.getDisplayName()
                    + " - 最低 GDP: " + DF.format(effectiveGdp)
                    + " | 最低人口: " + effectivePop + suffix)
                    .withStyle(ChatFormatting.WHITE), false);
        }

        return 1;
    }

    public static int showStatus(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== 经济系统状态 ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("领地数量: " + data.getRegionCount()).withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal("GDP 总值: " + DF.format(data.getTotalGdp())).withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("人口总数: " + INT_DF.format(data.getTotalPopulation())).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("多线程 GDP: " + (ModConfig.COMMON.enableMultiThreadedGdpCalc.get() ? "启用" : "禁用")).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("GDP 间隔: " + ModConfig.COMMON.gdpCalcIntervalMinutes.get() + " 分钟").withStyle(ChatFormatting.GRAY), false);

        if (data.getLastGdpCalcTime() > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("上次 GDP 计算: " + formatTime(data.getLastGdpCalcTime())).withStyle(ChatFormatting.GRAY), false);
        }
        if (data.getLastPopulationCheckTime() > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("上次人口检查: " + formatTime(data.getLastPopulationCheckTime())).withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    public static int forceCalcGdp(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.gdp.calc_start")
                .withStyle(ChatFormatting.YELLOW), true);
        GDPEngine.getInstance().triggerGdpCalculation(data);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.gdp.calc_done")
                .withStyle(ChatFormatting.GREEN), true);

        double totalGdp = data.getTotalGdp();
        ctx.getSource().sendSuccess(() -> Component.literal("GDP 总值: " + DF.format(totalGdp)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static int mathGdp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        // Check cooldown for non-admin players
        if (!ctx.getSource().hasPermission(2)) {
            int cooldownMinutes = ModConfig.COMMON.mathGdpCooldownMinutes.get();
            if (cooldownMinutes > 0) {
                long cooldownMs = cooldownMinutes * 60L * 1000L;
                if (System.currentTimeMillis() - data.getLastGdpCalcTime() < cooldownMs) {
                    long remainingMs = cooldownMs - (System.currentTimeMillis() - data.getLastGdpCalcTime());
                    long remainingMin = (remainingMs / 60000) + 1;
                    ctx.getSource().sendFailure(Component.literal("请等待 " + remainingMin + " 分钟后再次计算GDP（管理员可使用 /math settime 调整时限）"));
                    return 0;
                }
            }
        }

        BlockPos pos = player.blockPosition();
        String dimId = player.level().dimension().location().toString();

        RegionData region = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.getDimensionId() != null && r.getDimensionId().equals(dimId) && r.containsPos(pos)) {
                region = r;
                break;
            }
        }

        if (region == null) {
            region = data.getRegionByOwner(player.getUUID());
        }

        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不在任何领地中，也没有拥有的领地"));
            return 0;
        }

        final RegionData r = region;

        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.gdp.calc_start")
                .withStyle(ChatFormatting.YELLOW), true);

        var result = GDPEngine.getInstance().calculateSingleRegionGdp(r);
        if (result == null) {
            ctx.getSource().sendFailure(Component.literal("GDP 计算失败"));
            return 0;
        }

        Map<IndustryClassification.IndustryType, Double> industryGdp = result.getKey();
        double growthRate = result.getValue();
        double totalGdp = r.getGdp();

        RegionType type = r.getRegionType();
        String growthStr = growthRate >= 0 ? "+" + PCT_DF.format(growthRate) + "%" : PCT_DF.format(growthRate) + "%";
        ChatFormatting growthColor = growthRate >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;

        // Build GDP progress bar
        MutableComponent gdpBar = Component.literal("").withStyle(ChatFormatting.WHITE);
        double totalGrowthRate = growthRate; // total growth
        Component bar = buildGdpProgressBar(totalGrowthRate);
        gdpBar.append(Component.literal("GDP 增长: ").withStyle(ChatFormatting.YELLOW));
        gdpBar.append(bar);
        gdpBar.append(Component.literal(" " + growthStr).withStyle(growthColor));

        // Build result message list
        List<Component> resultMessages = new java.util.ArrayList<>();
        resultMessages.add(Component.literal("=== GDP 计算结果 ===").withStyle(ChatFormatting.GOLD));
        resultMessages.add(Component.literal("领地: " + r.getName()).withStyle(ChatFormatting.YELLOW));
        resultMessages.add(Component.literal("区域类型: " + type.getDisplayName()).withStyle(ChatFormatting.AQUA));
        resultMessages.add(Component.literal("GDP 总值: " + DF.format(totalGdp)).withStyle(ChatFormatting.GREEN));
        resultMessages.add(gdpBar);
        resultMessages.add(Component.literal("人口: " + r.getPopulation()).withStyle(ChatFormatting.AQUA));

        for (Map.Entry<IndustryClassification.IndustryType, Double> entry : industryGdp.entrySet()) {
            double industryValue = entry.getValue();
            double proportion = totalGdp > 0 ? (industryValue / totalGdp * 100.0) : 0;
            String sectorName = switch (entry.getKey().getSector()) {
                case PRIMARY -> "第一产业";
                case SECONDARY -> "第二产业";
                case TERTIARY -> "第三产业";
            };
            Component industryBar = buildGdpProgressBar(proportion);
            resultMessages.add(Component.literal("  " + sectorName + " - " + entry.getKey().getName()
                    + ": " + DF.format(industryValue) + " ").withStyle(ChatFormatting.WHITE)
                    .copy().append(industryBar).append(Component.literal(" " + PCT_DF.format(proportion) + "%")));
        }

        double tertiaryExtra = r.getBankDeposits() + r.getPersonalFunds();
        if (tertiaryExtra > 0) {
            double proportion = totalGdp > 0 ? (tertiaryExtra / totalGdp * 100.0) : 0;
            resultMessages.add(Component.literal("  第三产业 - 资金: " + DF.format(tertiaryExtra) + " (" + PCT_DF.format(proportion) + "%)")
                    .withStyle(ChatFormatting.GRAY));
        }

        // Send to executor
        for (Component msg : resultMessages) {
            ctx.getSource().sendSuccess(() -> msg, false);
        }

        // Broadcast to all online members of the region
        for (UUID memberId : r.getMembers()) {
            ServerPlayer member = ctx.getSource().getServer().getPlayerList().getPlayer(memberId);
            if (member != null && !member.getUUID().equals(player.getUUID())) {
                member.sendSystemMessage(Component.literal("=== " + r.getName() + " GDP 计算结果 ===").withStyle(ChatFormatting.GOLD));
                member.sendSystemMessage(Component.literal("GDP 总值: " + DF.format(totalGdp) + " | 增长率: " + growthStr).withStyle(growthColor));
                member.sendSystemMessage(gdpBar);
                member.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
        // Also notify owner if not member
        if (r.getOwner() != null && !r.getOwner().equals(player.getUUID())) {
            ServerPlayer owner = ctx.getSource().getServer().getPlayerList().getPlayer(r.getOwner());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("=== " + r.getName() + " GDP 计算结果 ===").withStyle(ChatFormatting.GOLD));
                owner.sendSystemMessage(Component.literal("GDP 总值: " + DF.format(totalGdp) + " | 增长率: " + growthStr).withStyle(growthColor));
                owner.sendSystemMessage(gdpBar);
                owner.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        return 1;
    }

    private static Component buildGdpProgressBar(double percentage) {
        StringBuilder sb = new StringBuilder();
        if (percentage > 100.0) {
            sb.append("§c□□§6□□□§e□□□□§a□§r");
        } else if (percentage < 10.0) {
            sb.append("§c□§r□□□□□□□□□");
        } else {
            int tens = Math.min(9, (int) (percentage / 10.0));
            for (int i = 0; i < tens; i++) {
                sb.append("§a□");
            }
            if (tens < 10) {
                sb.append("§6□");
                for (int i = tens + 1; i < 10; i++) {
                    sb.append("§r□");
                }
            }
        }

        // Add growth arrow indicators
        if (percentage >= 90.0) {
            sb.append(" §c↑↑↑");
        } else if (percentage >= 50.0) {
            sb.append(" §e↑↑");
        } else if (percentage >= 0.01) {
            sb.append(" §a↑");
        }

        return Component.literal(sb.toString());
    }

    public static int forceCheckPop(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.pop.check_start")
                .withStyle(ChatFormatting.YELLOW), true);
        PopulationEngine.getInstance().forceCheck();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.pop.check_done")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int addItemValue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double value = ctx.getArgument("value", Double.class);
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("请手持一个物品"));
            return 0;
        }
        String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
        IndustryClassification.setPresetValue(itemId, value);
        ctx.getSource().sendSuccess(() -> Component.literal("已设置 " + itemId + " 的预设价值为 " + DF.format(value))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int removeItemValue(CommandContext<CommandSourceStack> ctx) {
        String itemId = ctx.getArgument("item", String.class);
        IndustryClassification.setPresetValue(itemId, ModConfig.COMMON.baseItemPresetValue.get());
        ctx.getSource().sendSuccess(() -> Component.literal("已重置 " + itemId + " 的预设价值为默认值")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int addIndustryItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String industryName = ctx.getArgument("industry", String.class);
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("请手持一个物品"));
            return 0;
        }
        String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();

        try {
            IndustryClassification.IndustryType type = IndustryClassification.IndustryType.valueOf(industryName.toUpperCase());
            if (IndustryClassification.addItemToIndustry(itemId, type)) {
                ctx.getSource().sendSuccess(() -> Component.literal("已添加 " + itemId + " 到产业 " + type.getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                ctx.getSource().sendFailure(Component.literal("添加物品到产业失败"));
            }
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("未知产业: " + industryName
                    + "。有效值: mining, agriculture, forestry, fishing, manufacturing, smelting, construction"));
        }
        return 1;
    }

    public static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("正在从配置重新加载产业分类...").withStyle(ChatFormatting.YELLOW), true);
        IndustryClassification.reset();
        IndustryClassification.initialize();
        ctx.getSource().sendSuccess(() -> Component.literal("配置已重新加载。").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === Value commands ===

    public static int valueAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        double amount = ctx.getArgument("amount", Double.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        for (ServerPlayer target : targets) {
            data.addPlayerFunds(target.getUUID(), amount);
            target.sendSystemMessage(Component.literal("你的个人资金增加了 " + DF.format(amount) + " （当前: " + DF.format(data.getPlayerFunds(target.getUUID())) + "）")
                    .withStyle(ChatFormatting.GREEN));
        }

        ctx.getSource().sendSuccess(() -> Component.literal("已为 " + targets.size() + " 名玩家增加 " + DF.format(amount) + " 资金")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int valueSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        double amount = ctx.getArgument("amount", Double.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        for (ServerPlayer target : targets) {
            data.setPlayerFunds(target.getUUID(), amount);
            target.sendSystemMessage(Component.literal("你的个人资金已设置为 " + DF.format(amount))
                    .withStyle(ChatFormatting.GREEN));
        }

        ctx.getSource().sendSuccess(() -> Component.literal("已设置 " + targets.size() + " 名玩家的资金为 " + DF.format(amount))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int valueDec(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        double amount = ctx.getArgument("amount", Double.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        for (ServerPlayer target : targets) {
            double current = data.getPlayerFunds(target.getUUID());
            if (current < amount) {
                ctx.getSource().sendFailure(Component.literal(target.getScoreboardName() + " 资金不足（现有: " + DF.format(current) + "，需要: " + DF.format(amount) + "）"));
                return 0;
            }
            data.addPlayerFunds(target.getUUID(), -amount);
            target.sendSystemMessage(Component.literal("你的个人资金减少了 " + DF.format(amount) + " （当前: " + DF.format(data.getPlayerFunds(target.getUUID())) + "）")
                    .withStyle(ChatFormatting.RED));
        }

        ctx.getSource().sendSuccess(() -> Component.literal("已为 " + targets.size() + " 名玩家减少 " + DF.format(amount) + " 资金")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === Admin config commands ===

    public static int setContainerOnly(CommandContext<CommandSourceStack> ctx) {
        boolean value = ctx.getArgument("value", Boolean.class);
        ModConfig.COMMON.enableContainerOnlyGdpCalc.set(value);
        ctx.getSource().sendSuccess(() -> Component.literal("容器外物品GDP计算已" + (value ? "关闭" : "开启"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setMaxConcurrent(CommandContext<CommandSourceStack> ctx) {
        int value = ctx.getArgument("value", Integer.class);
        ModConfig.COMMON.maxConcurrentGdpCalc.set(value);
        ctx.getSource().sendSuccess(() -> Component.literal("最大并发GDP计算数已设为 " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setMultiThreaded(CommandContext<CommandSourceStack> ctx) {
        boolean value = ctx.getArgument("value", Boolean.class);
        ModConfig.COMMON.enableMultiThreadedGdpCalc.set(value);
        ctx.getSource().sendSuccess(() -> Component.literal("多线程GDP计算已" + (value ? "开启" : "关闭"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setGdpInterval(CommandContext<CommandSourceStack> ctx) {
        int minutes = ctx.getArgument("minutes", Integer.class);
        ModConfig.COMMON.gdpCalcIntervalMinutes.set(minutes);
        ctx.getSource().sendSuccess(() -> Component.literal("GDP计算间隔已设为 " + minutes + " 分钟")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setPopCheckHours(CommandContext<CommandSourceStack> ctx) {
        int hours = ctx.getArgument("hours", Integer.class);
        ModConfig.COMMON.virtualPopGrowthCheckHours.set(hours);
        ctx.getSource().sendSuccess(() -> Component.literal("人口检查间隔已设为 " + hours + " 分钟")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setMathGdpCooldown(CommandContext<CommandSourceStack> ctx) {
        int value = ctx.getArgument("value", Integer.class);
        String unit = ctx.getArgument("unit", String.class);

        int minutes;
        switch (unit.toLowerCase()) {
            case "hour":
            case "hours":
            case "h":
                minutes = value * 60;
                break;
            case "day":
            case "days":
            case "d":
                minutes = value * 60 * 24;
                break;
            default:
                minutes = value;
                break;
        }

        if (minutes < 0 || minutes > 10080) {
            ctx.getSource().sendFailure(Component.literal("时限范围为 0-10080 分钟（7天）"));
            return 0;
        }

        ModConfig.COMMON.mathGdpCooldownMinutes.set(minutes);
        ctx.getSource().sendSuccess(() -> Component.literal("非管理员使用 /math gdp 的时限已设为 " + minutes + " 分钟")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setBlockType(CommandContext<CommandSourceStack> ctx) {
        String typeName = ctx.getArgument("type", String.class);
        double gdp = ctx.getArgument("gdp", Double.class);
        int population = ctx.getArgument("population", Integer.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        // Validate region type
        RegionType type;
        try {
            type = RegionType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("未知区域类型。有效值: WASTELAND, VILLAGE, TOWNSHIP, TOWN, CITY"));
            return 0;
        }

        data.setRegionTypeOverride(type.name(), gdp, population);
        ctx.getSource().sendSuccess(() -> Component.literal("已设置 " + type.getDisplayName()
                + " 的 GDP条件为 " + DF.format(gdp) + "，人口条件为 " + population)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String formatTime(long millis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return dt.format(TIME_FMT);
    }
}