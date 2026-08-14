package cn.autoforged.land_economy_mod_1783600667.command;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionType;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketS2COpenScreen;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class RegionCommandHandler {

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    // (replaced by the new help below)

    public static int claimLandWithPos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }
        // 新版模式下提示使用 /land map（旧指令仍可执行）
        if ("new".equals(data.getPlayerPlotMode(player.getUUID()))) {
            ctx.getSource().sendSuccess(() -> Component.literal("[提示] 你处于新版地图地块模式，建议使用 /land map 通过图形化方式购买地块")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        RegionData existing = data.getRegionByOwner(player.getUUID());
        if (existing != null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.claim.exists"));
            return 0;
        }

        int x1 = ctx.getArgument("x1", Integer.class);
        int z1 = ctx.getArgument("z1", Integer.class);
        int x2 = ctx.getArgument("x2", Integer.class);
        int z2 = ctx.getArgument("z2", Integer.class);

        int minX = Math.min(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxZ = Math.max(z1, z2);

        if (maxX - minX < 1 || maxZ - minZ < 1) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.claim.too_small"));
            return 0;
        }

        // Check outlay cost
        double cost = ModConfig.COMMON.claimOutlayNew.get();
        if (cost > 0) {
            double playerFunds = data.getPlayerFunds(player.getUUID());
            if (playerFunds < cost) {
                ctx.getSource().sendFailure(Component.literal("资金不足，创建领地需要 " + DF.format(cost) + "（现有: " + DF.format(playerFunds) + "）"));
                return 0;
            }
            data.addPlayerFunds(player.getUUID(), -cost);
        }

        // Check overlap with other root regions
        RegionData testRegion = new RegionData();
        testRegion.setMinX(minX);
        testRegion.setMinZ(minZ);
        testRegion.setMaxX(maxX);
        testRegion.setMaxZ(maxZ);
        testRegion.setDimensionId(player.level().dimension().location().toString());
        for (RegionData r : data.getAllRegions()) {
            if (r.isRootRegion() && testRegion.overlapsWith(r)) {
                ctx.getSource().sendFailure(Component.literal("该区域已被他人声明"));
                return 0;
            }
        }

        // Optional name parameter
        String regionName;
        try {
            regionName = ctx.getArgument("name", String.class);
        } catch (IllegalArgumentException e) {
            regionName = player.getScoreboardName() + "的领地";
        }

        RegionData region = new RegionData();
        region.setOwner(player.getUUID());
        region.setCenter(new BlockPos((minX + maxX) / 2, player.blockPosition().getY(), (minZ + maxZ) / 2));
        region.setMinX(minX);
        region.setMinZ(minZ);
        region.setMaxX(maxX);
        region.setMaxZ(maxZ);
        region.setDimensionId(player.level().dimension().location().toString());
        region.setName(regionName);

        data.createRegion(player.getUUID(), region);

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.claim.success")
                .withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("区域: " + region.getName()
                + " | 范围: " + minX + "," + minZ + " ~ " + maxX + "," + maxZ
                + " | 大小: " + width + "x" + depth + " 格")
                .withStyle(ChatFormatting.GRAY), true);

        showRegionBorders(player, region);

        return 1;
    }

    // === Expand Region ===

    public static int expandRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.isRootRegion() && r.getOwner() != null && r.getOwner().equals(player.getUUID())) {
                region = r;
                break;
            }
        }

        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你没有可扩大的领地，请先使用 /land claim 创建领地"));
            return 0;
        }

        if (!region.getDimensionId().equals(player.level().dimension().location().toString())) {
            ctx.getSource().sendFailure(Component.literal("你必须在领地所在维度才能扩大区域"));
            return 0;
        }

        int x1 = ctx.getArgument("x1", Integer.class);
        int z1 = ctx.getArgument("z1", Integer.class);
        int x2 = ctx.getArgument("x2", Integer.class);
        int z2 = ctx.getArgument("z2", Integer.class);

        int newMinX = Math.min(Math.min(region.getMinX(), x1), x2);
        int newMinZ = Math.min(Math.min(region.getMinZ(), z1), z2);
        int newMaxX = Math.max(Math.max(region.getMaxX(), x1), x2);
        int newMaxZ = Math.max(Math.max(region.getMaxZ(), z1), z2);

        if (newMinX == region.getMinX() && newMinZ == region.getMinZ()
                && newMaxX == region.getMaxX() && newMaxZ == region.getMaxZ()) {
            ctx.getSource().sendFailure(Component.literal("新选点未超出当前区域范围，无需扩大"));
            return 0;
        }

        // Check outlay cost for expansion
        double cost = ModConfig.COMMON.claimOutlayExpand.get();
        if (cost > 0) {
            double playerFunds = data.getPlayerFunds(player.getUUID());
            if (playerFunds < cost) {
                ctx.getSource().sendFailure(Component.literal("资金不足，扩大区域需要 " + DF.format(cost) + "（现有: " + DF.format(playerFunds) + "）"));
                return 0;
            }
            data.addPlayerFunds(player.getUUID(), -cost);
        }

        // Check overlap with other root regions (excluding self)
        RegionData testRegion = new RegionData();
        testRegion.setMinX(newMinX);
        testRegion.setMinZ(newMinZ);
        testRegion.setMaxX(newMaxX);
        testRegion.setMaxZ(newMaxZ);
        testRegion.setDimensionId(region.getDimensionId());
        for (RegionData r : data.getAllRegions()) {
            if (r == region) continue;
            if (r.isRootRegion() && testRegion.overlapsWith(r)) {
                ctx.getSource().sendFailure(Component.literal("该区域已被他人声明"));
                return 0;
            }
        }

        region.setMinX(newMinX);
        region.setMinZ(newMinZ);
        region.setMaxX(newMaxX);
        region.setMaxZ(newMaxZ);
        region.setCenter(new BlockPos((newMinX + newMaxX) / 2,
                region.getCenter() != null ? region.getCenter().getY() : player.blockPosition().getY(),
                (newMinZ + newMaxZ) / 2));
        data.setDirty();

        final RegionData finalRegion = region;
        ctx.getSource().sendSuccess(() -> Component.literal("区域已扩大成功！").withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("区域: " + finalRegion.getName()
                + " | 范围: " + newMinX + "," + newMinZ + " ~ " + newMaxX + "," + newMaxZ
                + " | 大小: " + (newMaxX - newMinX + 1) + "x" + (newMaxZ - newMinZ + 1) + " 格")
                .withStyle(ChatFormatting.GRAY), true);

        showRegionBorders(player, region);

        return 1;
    }

    private static void showRegionBorders(ServerPlayer player, RegionData region) {
        int minX = region.getMinX();
        int minZ = region.getMinZ();
        int maxX = region.getMaxX();
        int maxZ = region.getMaxZ();

        player.sendSystemMessage(Component.literal("区域范围: (" + minX + ", " + minZ + ") ~ (" + maxX + ", " + maxZ + ")")
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("四角坐标:").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  A(" + minX + ", " + minZ + ")  B(" + maxX + ", " + minZ + ")")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  C(" + minX + ", " + maxZ + ")  D(" + maxX + ", " + maxZ + ")")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("区块范围: " + (minX >> 4) + "~" + (maxX >> 4) + ", " + (minZ >> 4) + "~" + (maxZ >> 4))
                .withStyle(ChatFormatting.DARK_GRAY));

        ServerLevel level = (ServerLevel) player.level();
        int y = player.blockPosition().getY() + 1;
        int step = Math.max(1, (maxX - minX + maxZ - minZ) / 40);
        for (int x = minX; x <= maxX; x += step) {
            level.sendParticles(ParticleTypes.END_ROD, x, y, minZ, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, x, y, maxZ, 1, 0, 0, 0, 0);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            level.sendParticles(ParticleTypes.END_ROD, minX, y, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, maxX, y, z, 1, 0, 0, 0, 0);
        }
    }

    public static int unclaimLandBlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不属于任何母区域，无法放弃母区域"));
            return 0;
        }

        if (!region.isRootRegion()) {
            ctx.getSource().sendFailure(Component.literal("你所在的区域不是根区域，无法使用此指令"));
            return 0;
        }

        if (!region.getOwner().equals(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("只有区域创建者才能放弃该区域"));
            return 0;
        }

        // Remove child regions (sub-regions and flylands) when parent is abandoned
        for (UUID childId : new HashSet<>(region.getChildRegionIds())) {
            data.removeRegion(childId);
        }
        region.getChildRegionIds().clear();

        data.removeRegion(region.getRegionId());
        ctx.getSource().sendSuccess(() -> Component.literal("母区域已放弃，下辖子区域和飞地自动解散")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int unclaimLandChild(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData child = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.getParentRegionId() != null && !r.isFlyland() && r.isMember(player.getUUID())) {
                child = r;
                break;
            }
        }

        if (child == null) {
            ctx.getSource().sendFailure(Component.literal("你不属于任何子区域，无法放弃子区域"));
            return 0;
        }

        if (!child.getOwner().equals(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("只有子区域创建者才能放弃该子区域"));
            return 0;
        }

        // Remove from parent's child list
        RegionData parent = data.getRegion(child.getParentRegionId());
        if (parent != null) {
            parent.getChildRegionIds().remove(child.getRegionId());
        }

        data.removeRegion(child.getRegionId());
        ctx.getSource().sendSuccess(() -> Component.literal("子区域已放弃").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int unclaimLandFlyland(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData flyland = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.isFlyland() && r.isMember(player.getUUID())) {
                flyland = r;
                break;
            }
        }

        if (flyland == null) {
            ctx.getSource().sendFailure(Component.literal("你不属于任何飞地，无法放弃飞地"));
            return 0;
        }

        if (!flyland.getOwner().equals(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("只有飞地创建者才能放弃该飞地"));
            return 0;
        }

        // Remove from parent's child list
        RegionData parent = data.getRegion(flyland.getParentRegionId());
        if (parent != null) {
            parent.getChildRegionIds().remove(flyland.getRegionId());
        }

        data.removeRegion(flyland.getRegionId());
        ctx.getSource().sendSuccess(() -> Component.literal("飞地已放弃").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // (replaced by the new landInfo below)

    public static int listLands(CommandContext<CommandSourceStack> ctx) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        Collection<RegionData> regions = data.getAllRegions();
        if (regions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.list.empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== 领地列表 (" + regions.size() + ") ===").withStyle(ChatFormatting.GOLD), false);

        for (RegionData region : regions) {
            RegionType type = region.getRegionType();
            ctx.getSource().sendSuccess(() -> Component.literal("- " + region.getName()
                    + " | " + type.getDisplayName()
                    + " | GDP: " + DF.format(region.getGdp())
                    + " | 人口: " + region.getPopulation()).withStyle(ChatFormatting.WHITE), false);
        }

        return 1;
    }

    public static int setLandName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = ctx.getArgument("name", String.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        region.setName(name);
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.land_economy_mod_1783600667.rename.success")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === Region entry display mode (title / actionbar) ===

    public static int setDisplayMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String mode = ctx.getArgument("mode", String.class).toLowerCase();
        if (!mode.equals("title") && !mode.equals("actionbar")) {
            ctx.getSource().sendFailure(Component.literal("无效的显示方式: " + mode + "（可用: title / actionbar）"));
            return 0;
        }

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        data.setPlayerDisplayMode(player.getUUID(), mode);
        ctx.getSource().sendSuccess(() -> Component.literal("区域进入提示已切换为 "
                        + (mode.equals("title") ? "屏幕标题 (Traveler's Title 风格)" : "原版 ActionBar"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int showDisplayMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        String mode = data.getPlayerDisplayMode(player.getUUID());
        if (mode == null) {
            mode = ModConfig.COMMON.regionDisplayMode.get();
        }
        boolean isActionbar = mode.equalsIgnoreCase("actionbar");

        MutableComponent msg = Component.literal("当前区域进入提示方式: ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(isActionbar ? "原版 ActionBar" : "屏幕标题 (Traveler's Title 风格)")
                        .withStyle(isActionbar ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        MutableComponent toggle = Component.literal("  [点击切换为 " + (isActionbar ? "屏幕标题" : "原版 ActionBar") + "]")
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/land display " + (isActionbar ? "title" : "actionbar"))));
        msg.append(toggle);
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    public static int setPermission(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String permName = ctx.getArgument("permission", String.class);
        boolean value = ctx.getArgument("value", Boolean.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        int idx = RegionData.getPermissionIndex(permName);
        if (idx < 0) {
            ctx.getSource().sendFailure(Component.literal("未知权限: " + permName
                    + "。使用 /land permissions 查看所有权限。"));
            return 0;
        }

        region.setPermission(idx, value);
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("权限 '" + RegionData.getPermissionName(idx) + "' 已设置为 " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // (listPermissions and getPermissionChinese replaced below)

    public static int invitePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        if (region.isMember(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("该玩家已在领地中"));
            return 0;
        }

        if (region.addMember(target.getUUID())) {
            data.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal("已邀请 " + target.getScoreboardName() + " 加入领地")
                    .withStyle(ChatFormatting.GREEN), true);
            target.sendSystemMessage(Component.literal("你已被邀请加入 " + region.getName()).withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    public static int kickPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        if (region.removeMember(target.getUUID())) {
            data.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal("已移除 " + target.getScoreboardName())
                    .withStyle(ChatFormatting.GREEN), true);
            target.sendSystemMessage(Component.literal("你已被移出 " + region.getName()).withStyle(ChatFormatting.RED));
        } else {
            ctx.getSource().sendFailure(Component.literal("该玩家不是领地成员"));
        }
        return 1;
    }

    public static int leaveRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            region = data.getRegionByOwner(player.getUUID());
            if (region != null) {
                ctx.getSource().sendFailure(Component.literal("你是领地所有者，不能离开"));
                return 0;
            }
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        if (region.getOwner() != null && region.getOwner().equals(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("你是领地所有者，不能离开"));
            return 0;
        }

        final RegionData r = region;
        if (r.removeMember(player.getUUID())) {
            data.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal("已离开 " + r.getName())
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("你不是该领地的成员"));
        }
        return 1;
    }

    // (claimFlyland and flylandInfo replaced below)

    // === Bank Commands ===

    public static int bankDepositSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double amount = ctx.getArgument("amount", Double.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(Component.literal("金额必须大于0"));
            return 0;
        }

        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不在任何领地中"));
            return 0;
        }

        if (!region.isMember(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("你不是当前领地成员"));
            return 0;
        }

        double playerFunds = data.getPlayerFunds(player.getUUID());
        if (playerFunds < amount) {
            ctx.getSource().sendFailure(Component.literal("你的个人资金不足（现有: " + DF.format(playerFunds) + "）"));
            return 0;
        }

        data.addPlayerFunds(player.getUUID(), -amount);
        region.setBankDeposits(region.getBankDeposits() + amount);
        data.setDirty();

        ctx.getSource().sendSuccess(() -> Component.literal("已存入 " + DF.format(amount) + " 到区域银行")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int bankDeposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double amount = ctx.getArgument("amount", Double.class);
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(Component.literal("金额必须大于0"));
            return 0;
        }

        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不在任何领地中"));
            return 0;
        }

        if (!region.isMember(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("目标玩家不在当前领地中"));
            return 0;
        }

        double playerFunds = data.getPlayerFunds(target.getUUID());
        if (playerFunds < amount) {
            ctx.getSource().sendFailure(Component.literal(target.getScoreboardName() + "的个人资金不足（现有: " + DF.format(playerFunds) + "）"));
            return 0;
        }

        data.addPlayerFunds(target.getUUID(), -amount);
        region.setBankDeposits(region.getBankDeposits() + amount);
        data.setDirty();

        ctx.getSource().sendSuccess(() -> Component.literal("已从 " + target.getScoreboardName() + " 的个人资金中存入 " + DF.format(amount) + " 到区域银行")
                .withStyle(ChatFormatting.GREEN), true);
        target.sendSystemMessage(Component.literal("已存入 " + DF.format(amount) + " 到 " + region.getName() + " 的银行")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    public static int bankWithdrawSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double amount = ctx.getArgument("amount", Double.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(Component.literal("金额必须大于0"));
            return 0;
        }

        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不在任何领地中"));
            return 0;
        }

        if (!region.isMember(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("你不是当前领地成员"));
            return 0;
        }

        double bankDeposits = region.getBankDeposits();
        if (bankDeposits <= 0) {
            ctx.getSource().sendFailure(Component.literal("当前银行存款不足"));
            return 0;
        }
        if (bankDeposits < amount) {
            ctx.getSource().sendFailure(Component.literal("当前银行存款不足（现有: " + DF.format(bankDeposits) + "）"));
            return 0;
        }

        region.setBankDeposits(bankDeposits - amount);
        data.addPlayerFunds(player.getUUID(), amount);
        data.setDirty();

        ctx.getSource().sendSuccess(() -> Component.literal("已从区域银行取出 " + DF.format(amount))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int bankWithdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double amount = ctx.getArgument("amount", Double.class);
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        if (amount <= 0) {
            ctx.getSource().sendFailure(Component.literal("金额必须大于0"));
            return 0;
        }

        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不在任何领地中"));
            return 0;
        }

        if (!region.isMember(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("目标玩家不在当前领地中"));
            return 0;
        }

        double bankDeposits = region.getBankDeposits();
        if (bankDeposits <= 0) {
            ctx.getSource().sendFailure(Component.literal("当前银行存款不足"));
            return 0;
        }
        if (bankDeposits < amount) {
            ctx.getSource().sendFailure(Component.literal("当前银行存款不足（现有: " + DF.format(bankDeposits) + "）"));
            return 0;
        }

        region.setBankDeposits(bankDeposits - amount);
        data.addPlayerFunds(target.getUUID(), amount);
        data.setDirty();

        ctx.getSource().sendSuccess(() -> Component.literal("已从区域银行取出 " + DF.format(amount) + " 到 " + target.getScoreboardName() + " 的个人资金")
                .withStyle(ChatFormatting.GREEN), true);
        target.sendSystemMessage(Component.literal("已从 " + region.getName() + " 的银行取出 " + DF.format(amount))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // === Join Region Commands ===

    public static int applyJoinRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String regionName = ctx.getArgument("region_name", String.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData targetRegion = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.getName().equalsIgnoreCase(regionName) && !r.isFlyland()) {
                targetRegion = r;
                break;
            }
        }

        if (targetRegion == null) {
            ctx.getSource().sendFailure(Component.literal("未找到名为 \"" + regionName + "\" 的领地（飞地不可申请加入）"));
            return 0;
        }

        if (targetRegion.isMember(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("你已是该领地的成员"));
            return 0;
        }

        long cooldownMs = 5 * 60 * 1000L; // 5 minutes
        if (!targetRegion.canRequestJoin(player.getUUID(), cooldownMs)) {
            long remainingMs = cooldownMs - (System.currentTimeMillis() - targetRegion.getPendingJoinRequests().get(player.getUUID()));
            long remainingSec = (remainingMs / 1000) + 1;
            ctx.getSource().sendFailure(Component.literal("请等待 " + remainingSec + " 秒后再申请"));
            return 0;
        }

        targetRegion.addJoinRequest(player.getUUID());
        data.setDirty();

        final RegionData finalTargetRegion = targetRegion;
        ctx.getSource().sendSuccess(() -> Component.literal("已向领地 \"" + finalTargetRegion.getName() + "\" 发送加入申请，请等待创建者审批")
                .withStyle(ChatFormatting.GREEN), true);

        // Notify creator
        UUID ownerId = targetRegion.getOwner();
        if (ownerId != null) {
            ServerPlayer owner = ctx.getSource().getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                MutableComponent msg = Component.literal("[待审批] ").withStyle(ChatFormatting.YELLOW);
                MutableComponent applicantName = Component.literal(player.getScoreboardName())
                        .withStyle(ChatFormatting.AQUA)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/land join " + player.getScoreboardName() + " confirm"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("点击审批 " + player.getScoreboardName()))));
                msg.append(applicantName);

                MutableComponent hint = Component.literal("  (点击名称审批)")
                        .withStyle(ChatFormatting.GRAY);
                msg.append(hint);

                owner.sendSystemMessage(msg);
            }
        }

        return 1;
    }

    public static int handleJoinRequest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String applicantName = ctx.getArgument("player_name", String.class);
        String action = ctx.getArgument("action", String.class);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        ServerPlayer applicant = ctx.getSource().getServer().getPlayerList().getPlayerByName(applicantName);
        if (applicant == null) {
            ctx.getSource().sendFailure(Component.literal("玩家 " + applicantName + " 不在线"));
            return 0;
        }

        UUID applicantId = applicant.getUUID();
        if (!region.hasPendingRequest(applicantId)) {
            ctx.getSource().sendFailure(Component.literal(applicantName + " 没有待审批的加入申请"));
            return 0;
        }

        boolean approved = action.equalsIgnoreCase("confirm");
        region.removeJoinRequest(applicantId);

        if (approved) {
            if (region.isMember(applicantId)) {
                ctx.getSource().sendFailure(Component.literal(applicantName + " 已是该领地成员"));
                return 0;
            }
            region.addMember(applicantId);
            data.setDirty();

            ctx.getSource().sendSuccess(() -> Component.literal("已同意 " + applicantName + " 加入领地")
                    .withStyle(ChatFormatting.GREEN), true);

            MutableComponent notifyMsg = Component.literal(player.getScoreboardName())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" 已通过您的加入申请").withStyle(ChatFormatting.GREEN));
            applicant.sendSystemMessage(notifyMsg);
        } else {
            data.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal("已拒绝 " + applicantName + " 的加入申请")
                    .withStyle(ChatFormatting.RED), true);

            applicant.sendSystemMessage(Component.literal(player.getScoreboardName() + " 拒绝了您的加入申请")
                    .withStyle(ChatFormatting.RED));
        }

        return 1;
    }

    public static int listPendingRequests(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        Map<UUID, Long> requests = region.getPendingJoinRequests();
        if (requests.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("当前没有待审批的加入申请").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== 待审批名单 ===").withStyle(ChatFormatting.GOLD), false);
        MutableComponent listMsg = Component.literal("[待审批] ").withStyle(ChatFormatting.YELLOW);

        boolean first = true;
        for (UUID applicantId : requests.keySet()) {
            if (!first) {
                listMsg.append(Component.literal(" "));
            }
            first = false;

            String name = "未知";
            ServerPlayer ap = ctx.getSource().getServer().getPlayerList().getPlayer(applicantId);
            if (ap != null) {
                name = ap.getScoreboardName();
            }
            final String finalName = name;

            MutableComponent nameComp = Component.literal(finalName)
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/land join " + finalName + " confirm"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("点击同意 " + finalName + " 加入"))));
            listMsg.append(nameComp);
        }

        ctx.getSource().sendSuccess(() -> listMsg, false);
        ctx.getSource().sendSuccess(() -> Component.literal("点击名称同意加入，使用 /land join <玩家> refuse 拒绝")
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    // === Sub-region ===

    public static int claimChildRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        int x1 = ctx.getArgument("x1", Integer.class);
        int z1 = ctx.getArgument("z1", Integer.class);
        int x2 = ctx.getArgument("x2", Integer.class);
        int z2 = ctx.getArgument("z2", Integer.class);

        int minX = Math.min(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxZ = Math.max(z1, z2);

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        // Find the parent region (player must be a member of a region that can have children)
        RegionData parentRegion = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.isRootRegion() && r.isMember(player.getUUID())
                    && r.getDimensionId().equals(player.level().dimension().location().toString())
                    && r.containsPos(new BlockPos((minX + maxX) / 2, player.blockPosition().getY(), (minZ + maxZ) / 2))) {
                parentRegion = r;
                break;
            }
        }

        if (parentRegion == null) {
            ctx.getSource().sendFailure(Component.literal("你必须是某个区域成员才能创建子区域，且子区域必须在母区域内"));
            return 0;
        }

        // Check parent area >= 4x4 chunks
        int parentChunkW = (parentRegion.getMaxX() - parentRegion.getMinX()) >> 4;
        int parentChunkD = (parentRegion.getMaxZ() - parentRegion.getMinZ()) >> 4;
        if (parentChunkW < 4 || parentChunkD < 4) {
            ctx.getSource().sendFailure(Component.literal("区域面积过小（母区域至少需要长4个区块宽4个区块才能创建子区域）"));
            return 0;
        }

        // Sub-region must be within parent bounds
        if (minX < parentRegion.getMinX() || maxX > parentRegion.getMaxX()
                || minZ < parentRegion.getMinZ() || maxZ > parentRegion.getMaxZ()) {
            ctx.getSource().sendFailure(Component.literal("子区域不能超出母区域边界"));
            return 0;
        }

        // Sub-region must be <= parent/2 size
        int parentWidth = parentRegion.getMaxX() - parentRegion.getMinX();
        int parentDepth = parentRegion.getMaxZ() - parentRegion.getMinZ();
        int childWidth = maxX - minX;
        int childDepth = maxZ - minZ;
        if (childWidth > parentWidth / 2 || childDepth > parentDepth / 2) {
            ctx.getSource().sendFailure(Component.literal("子区域大小不能超过母区域的一半"));
            return 0;
        }

        // Check overlap with existing regions
        RegionData testRegion = new RegionData();
        testRegion.setMinX(minX);
        testRegion.setMinZ(minZ);
        testRegion.setMaxX(maxX);
        testRegion.setMaxZ(maxZ);
        testRegion.setDimensionId(player.level().dimension().location().toString());

        for (RegionData r : data.getAllRegions()) {
            if (r == parentRegion) continue;
            if (r.isRootRegion() && testRegion.overlapsWith(r)) {
                ctx.getSource().sendFailure(Component.literal("该区域已被他人声明"));
                return 0;
            }
        }

        // Optional name parameter
        String childName;
        try {
            childName = ctx.getArgument("name", String.class);
        } catch (IllegalArgumentException e) {
            childName = player.getScoreboardName() + "的子区域";
        }

        RegionData child = new RegionData();
        child.setName(childName);
        child.setOwner(parentRegion.getOwner());
        child.setCenter(new BlockPos((minX + maxX) / 2, player.blockPosition().getY(), (minZ + maxZ) / 2));
        child.setMinX(minX);
        child.setMinZ(minZ);
        child.setMaxX(maxX);
        child.setMaxZ(maxZ);
        child.setDimensionId(player.level().dimension().location().toString());
        child.setParentRegionId(parentRegion.getRegionId());

        // Copy permissions from parent
        for (int i = 0; i < RegionData.TOTAL_PERMISSIONS; i++) {
            child.setPermission(i, parentRegion.getPermission(i));
        }

        // Add member (the creator)
        child.addMember(player.getUUID());

        data.createRegion(player.getUUID(), child);
        parentRegion.getChildRegionIds().add(child.getRegionId());
        data.setDirty();

        ctx.getSource().sendSuccess(() -> Component.literal("子区域创建成功！").withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("范围: " + minX + "," + minZ + " ~ " + maxX + "," + maxZ
                + " | 大小: " + (childWidth + 1) + "x" + (childDepth + 1) + " 格")
                .withStyle(ChatFormatting.GRAY), true);

        showRegionBorders(player, child);

        return 1;
    }

    // === Updated flyland claim ===

    public static int claimFlyland(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int width = ctx.getArgument("width", Integer.class);
        int length = ctx.getArgument("length", Integer.class);

        int maxW = ModConfig.COMMON.flylandMaxWidth.get();
        int maxL = ModConfig.COMMON.flylandMaxLength.get();

        if (width > maxW || length > maxL) {
            ctx.getSource().sendFailure(Component.literal("飞地大小超过限制 (" + maxW + "x" + maxL + " 区块)"));
            return 0;
        }

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        // Must be member of a root region to create flyland
        RegionData parentRegion = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.isRootRegion() && r.isMember(player.getUUID())
                    && r.getDimensionId().equals(player.level().dimension().location().toString())) {
                parentRegion = r;
                break;
            }
        }

        if (parentRegion == null) {
            ctx.getSource().sendFailure(Component.literal("你必须是某个区域的成员才能创建飞地"));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        int blockWidth = width * 16;
        int blockLength = length * 16;
        int offsetX = (blockWidth / 2);
        int offsetZ = (blockLength / 2);

        RegionData region = new RegionData(player.getUUID(),
                new BlockPos(chunkX * 16 + 8, pos.getY(), chunkZ * 16 + 8),
                offsetX, offsetZ);
        region.setDimensionId(player.level().dimension().location().toString());
        region.setFlyland(true);
        region.setParentRegionId(parentRegion.getRegionId());

        // Check overlap with other regions (including parent)
        for (RegionData r : data.getAllRegions()) {
            if (r == parentRegion) {
                if (region.overlapsWith(parentRegion)) {
                    ctx.getSource().sendFailure(Component.literal("飞地不能与母区域重合"));
                    return 0;
                }
                continue;
            }
            if (r.isRootRegion() && region.overlapsWith(r)) {
                ctx.getSource().sendFailure(Component.literal("该区域已被他人声明"));
                return 0;
            }
        }

        data.createRegion(player.getUUID(), region);
        parentRegion.getChildRegionIds().add(region.getRegionId());
        data.setDirty();

        final RegionData finalParentRegion = parentRegion;
        ctx.getSource().sendSuccess(() -> Component.literal("飞地认领成功！母区域: " + finalParentRegion.getName()
                + " | 大小: " + width + "x" + length + " 区块 (" + blockWidth + "x" + blockLength + " 格)")
                .withStyle(ChatFormatting.GREEN), true);

        showRegionBorders(player, region);

        return 1;
    }

    public static int flylandInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== 飞地信息 ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("最大宽度: " + ModConfig.COMMON.flylandMaxWidth.get() + " 区块")
                .withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("最大长度: " + ModConfig.COMMON.flylandMaxLength.get() + " 区块")
                .withStyle(ChatFormatting.WHITE), false);

        return 1;
    }

    // === Land Data Commands ===

    public static int landDataAddWrite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String folderName = ctx.getArgument("folder_name", String.class);
        return writeGdpData(ctx, folderName);
    }

    public static int landDataWrite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String regionName = ctx.getArgument("region_name", String.class);
        return writeGdpData(ctx, regionName);
    }

    private static int writeGdpData(CommandContext<CommandSourceStack> ctx, String folderName) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        var server = ctx.getSource().getServer();
        File gdpDataDir = server.getFile("GDPdata");
        if (!gdpDataDir.exists()) {
            gdpDataDir.mkdirs();
        }

        File targetDir = new File(gdpDataDir, folderName);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File csvFile = new File(targetDir, "gdp_data_" + timestamp + ".csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            writer.write("区域名称,区域GDP,区域人口,区域创建者,数据统计时间");
            writer.newLine();

            for (RegionData region : data.getAllRegions()) {
                String ownerName = "未知";
                if (region.getOwner() != null) {
                    var player = ctx.getSource().getServer().getPlayerList().getPlayer(region.getOwner());
                    if (player != null) {
                        ownerName = player.getScoreboardName();
                    }
                }
                String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                writer.write(String.format("%s,%.2f,%d,%s,%s",
                        escapeCsv(region.getName()),
                        region.getGdp(),
                        region.getPopulation(),
                        escapeCsv(ownerName),
                        timeStr));
                writer.newLine();
            }

            ctx.getSource().sendSuccess(() -> Component.literal("GDP数据已写入: " + csvFile.getAbsolutePath())
                    .withStyle(ChatFormatting.GREEN), true);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("写入文件失败: " + e.getMessage()));
            LandEconomyMod.LOGGER.error("Failed to write GDP data CSV", e);
            return 0;
        }

        return 1;
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // === Set Outlay Commands ===

    public static int setOutlayNew(CommandContext<CommandSourceStack> ctx) {
        double amount = ctx.getArgument("amount", Double.class);
        ModConfig.COMMON.claimOutlayNew.set(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("新建区域所需金额已设为 " + DF.format(amount))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    public static int setOutlayAdd(CommandContext<CommandSourceStack> ctx) {
        double amount = ctx.getArgument("amount", Double.class);
        ModConfig.COMMON.claimOutlayExpand.set(amount);
        ctx.getSource().sendSuccess(() -> Component.literal("扩大区域所需金额已设为 " + DF.format(amount))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === Updated help ===

    public static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("=== 领地经济 Mod 帮助 ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);

        ctx.getSource().sendSuccess(() -> Component.literal("【领地指令】").withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land claim pos1 <x> <z> pos2 <x> <z> [名称] — 直接指定坐标创建领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land add pos1 <x> <z> pos2 <x> <z> — 扩大领地面积（y轴无限高）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land claim child pos1 <x> <z> pos2 <x> <z> [名称] — 在母区域内创建子区域").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land unclaim Block — 放弃母区域（下辖飞地和子区域自动解散）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land unclaim Child — 放弃子区域").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land unclaim flyland — 放弃飞地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land info — 查看当前领地信息（含下辖飞地/子区域）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land list — 列出所有领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land setname <名称> — 重命名领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land display [title/actionbar] — 选择进入区域提示的显示方式（屏幕标题或原版 ActionBar）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land invite <玩家> — 邀请玩家加入领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land kick <玩家> — 将玩家踢出领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land leave — 离开当前所在领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land bank deposit <金额> [玩家] — 存入资金到区域银行").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land bank withdraw <金额> [玩家] — 从区域银行取出资金").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land join <区域名称> — 申请加入领地（可按Tab补全）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land join <玩家> <confirm/refuse> — 审批加入申请").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land join list — 查看待审批名单").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land permissions — 查看并点击切换所有权限状态").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land permissions set <权限> <true/false> — 设置权限").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land flyland claim <宽> <长> — 认领飞地（需是某区域成员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land flyland info — 查看飞地信息").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land data <add <名称> | 区域名称> — 导出GDP数据到CSV").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land setOutlay <new/add> <金额> — 设置新建/扩大区域费用（管理员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);

        ctx.getSource().sendSuccess(() -> Component.literal("【地块系统（新版）】").withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land map — 打开地图地块界面（俯视购买/放弃区块）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land mode <new|old> — 切换新版（地块系统）/旧版（区域声明）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land gui — 打开领地箱子GUI（图形化操作所有功能）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /land message <留言> — 在当前领地留言板发布留言").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);

        ctx.getSource().sendSuccess(() -> Component.literal("【经济指令】").withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /economy gdp — 查看GDP总览（含进度条）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /economy gdpdetail — 查看各领地GDP详情").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /economy population — 查看人口信息").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /economy regiontype — 查看区域类型").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /economy status — 查看经济系统状态").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /economy set Blocktype <类型> <GDP> <人口> — 修改区域类型条件（管理员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /math gdp — 计算当前所在区域GDP并显示详情（显示给全体成员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /math settime <数值> <min/hour/day> — 设置非管理员GDP计算时限（管理员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /value add <选择器> <金额> — 增加玩家资金（管理员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /value set <选择器> <金额> — 设置玩家资金（管理员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /value dec <选择器> <金额> — 减少玩家资金（管理员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);

        ctx.getSource().sendSuccess(() -> Component.literal("【权限说明】").withStyle(ChatFormatting.YELLOW), false);
        for (int i = 0; i < RegionData.TOTAL_PERMISSIONS; i++) {
            String name = RegionData.getPermissionName(i);
            String chinese = getPermissionChinese(name);
            int idx = i;
            ctx.getSource().sendSuccess(() -> Component.literal("  " + (idx + 1) + ". " + chinese + " (" + name + ")")
                    .withStyle(ChatFormatting.WHITE), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("【教程】").withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  1. 使用 /land claim pos1 <x> <z> pos2 <x> <z> [名称] 创建领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  2. 使用 /land invite <玩家> 邀请其他玩家加入").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  3. 使用 /land permissions 查看并点击切换权限").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  4. GDP将自动计算，也可使用 /math gdp 手动计算（结果通知全体成员）").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  5. 使用 /economy status 查看系统状态").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  6. 使用 /land bank deposit/withdraw 进行银行操作").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  7. 使用 /land join <区域名称> 申请加入现有领地").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  8. 使用 /land data <add <名称> | 区域名称> write 导出GDP统计表格").withStyle(ChatFormatting.WHITE), false);

        return 1;
    }

    // === Updated landInfo with hierarchy ===

    public static int landInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            region = data.getRegionByOwner(player.getUUID());
        }

        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        RegionData finalRegion = region;
        RegionType type = region.getRegionType();

        ctx.getSource().sendSuccess(() -> Component.literal("=== 领地信息 ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("名称: " + finalRegion.getName()).withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal("类型: " + type.getDisplayName()
                + (finalRegion.isFlyland() ? " (飞地)" : "")
                + (finalRegion.getParentRegionId() != null ? " (子区域)" : "")).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("范围: " + finalRegion.getMinX() + "," + finalRegion.getMinZ()
                + " ~ " + finalRegion.getMaxX() + "," + finalRegion.getMaxZ()).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("面积: " + (finalRegion.getMaxX() - finalRegion.getMinX() + 1) + " x "
                + (finalRegion.getMaxZ() - finalRegion.getMinZ() + 1) + " 格").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("GDP: " + DF.format(finalRegion.getGdp())).withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("人口: " + finalRegion.getPopulation()).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("银行存款: " + DF.format(finalRegion.getBankDeposits())).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("成员数: " + (1 + finalRegion.getMembers().size())).withStyle(ChatFormatting.LIGHT_PURPLE), false);

        // Hierarchy info
        if (finalRegion.isRootRegion() && !finalRegion.getChildRegionIds().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
            ctx.getSource().sendSuccess(() -> Component.literal("=== 下辖区域 ===").withStyle(ChatFormatting.GOLD), false);
            for (UUID childId : finalRegion.getChildRegionIds()) {
                RegionData child = data.getRegion(childId);
                if (child != null) {
                    String childType = child.isFlyland() ? "飞地" : "子区域";
                    ctx.getSource().sendSuccess(() -> Component.literal("  [" + childType + "] " + child.getName()
                            + " | GDP: " + DF.format(child.getGdp())
                            + " | 人口: " + child.getPopulation()
                            + " | 范围: " + child.getMinX() + "," + child.getMinZ()
                            + " ~ " + child.getMaxX() + "," + child.getMaxZ())
                            .withStyle(ChatFormatting.WHITE), false);
                }
            }
        }

        if (finalRegion.getParentRegionId() != null) {
            RegionData parent = data.getRegion(finalRegion.getParentRegionId());
            if (parent != null) {
                ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
                ctx.getSource().sendSuccess(() -> Component.literal("母区域: " + parent.getName())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }

    // === Updated clickable permissions ===

    public static int listPermissions(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }

        RegionData region = data.getRegionByOwner(player.getUUID());
        if (region == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.no_region"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== " + region.getName() + " 权限设置（点击切换） ===")
                .withStyle(ChatFormatting.GOLD), false);

        for (int i = 0; i < RegionData.TOTAL_PERMISSIONS; i++) {
            String name = RegionData.getPermissionName(i);
            boolean value = region.getPermission(i);
            ChatFormatting color = value ? ChatFormatting.GREEN : ChatFormatting.RED;
            String status = value ? "✓ 开启" : "✗ 关闭";
            int idx = i;

            MutableComponent permLine = Component.literal("  " + (idx + 1) + ". " + getPermissionChinese(name) + " (" + name + "): ")
                    .withStyle(ChatFormatting.WHITE);
            MutableComponent toggle = Component.literal(status)
                    .withStyle(color)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/land permissions set " + name + " " + (!value)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("点击切换为 " + (!value)))));
            permLine.append(toggle);

            ctx.getSource().sendSuccess(() -> permLine, false);
        }

        return 1;
    }

    private static String getPermissionChinese(String permName) {
        return switch (permName) {
            case "explode" -> "爆炸";
            case "undead_spawn" -> "亡灵生物生成";
            case "phantom_spawn" -> "幻翼生成";
            case "friendly_fire" -> "友伤";
            case "pvp" -> "PVP";
            case "explosion_block_damage" -> "爆炸破坏方块";
            case "container_access" -> "非成员使用容器";
            case "redstone_interact" -> "非成员交互红石元件";
            case "ender_pearl" -> "末影珍珠使用";
            case "fire_spread" -> "火焰蔓延";
            case "block_place_break" -> "非成员破坏与放置方块";
            case "region_fly" -> "区域飞行";
            case "block_update" -> "区域方块更新(关闭=区域冻结)";
            default -> permName;
        };
    }

    private static RegionData findRegionAtPlayer(ServerPlayer player, EconomySavedData data) {
        BlockPos pos = player.blockPosition();
        String dimId = player.level().dimension().location().toString();
        for (RegionData region : data.getAllRegions()) {
            if (region.getDimensionId() != null && region.getDimensionId().equals(dimId) && region.containsPos(pos)) {
                return region;
            }
        }
        return null;
    }

    // ==================== 地块系统相关命令实现 ====================

    /** /land map — 打开地图地块界面 */
    public static int openMap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }
        // 新版模式下，提示客户端打开 PlotMapScreen；旧版模式提示切换
        String mode = data.getPlayerPlotMode(player.getUUID());
        if ("old".equals(mode)) {
            ctx.getSource().sendFailure(Component.literal("当前为旧版模式，请先使用 /land mode new 切换到新版地图地块系统")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        // 进入地块界面：服务端记录在线状态（用于强制退出）
        data.setInPlotMode(player.getUUID(), true);
        ModMessages.sendToPlayer(player, new PacketS2COpenScreen(PacketS2COpenScreen.Type.PLOT_MAP));
        ctx.getSource().sendSuccess(() -> Component.literal("正在打开地图地块界面... 按 空格/ESC 退出")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /** /land mode <new|old> — 切换玩家个人地块模式 */
    public static int setPlotMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String mode = ctx.getArgument("mode", String.class);
        if (!"new".equals(mode) && !"old".equals(mode)) {
            ctx.getSource().sendFailure(Component.literal("模式必须为 new 或 old"));
            return 0;
        }
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }
        // 切换到新版：自动迁移旧版 AABB 到 chunk 集合
        if ("new".equals(mode)) {
            RegionData mine = data.getRegionByOwner(player.getUUID());
            if (mine != null) data.migrateLegacyAABBToChunks(mine);
        }
        data.setPlayerPlotMode(player.getUUID(), mode);
        data.setDirty();
        String label = "new".equals(mode) ? "新版（地图地块系统）" : "旧版（区域声明）";
        ctx.getSource().sendSuccess(() -> Component.literal("已切换到" + label)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** /land gui — 打开箱子GUI */
    public static int openChestGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }
        ModMessages.sendToPlayer(player, new PacketS2COpenScreen(PacketS2COpenScreen.Type.CHEST));
        ctx.getSource().sendSuccess(() -> Component.literal("正在打开领地GUI...")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /** /land message <text> — 在当前所在区域留言板发布留言 */
    public static int postMessage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String text = ctx.getArgument("text", String.class);
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data"));
            return 0;
        }
        RegionData region = findRegionAtPlayer(player, data);
        if (region == null) {
            ctx.getSource().sendFailure(Component.literal("你不在任何领地中"));
            return 0;
        }
        if (!region.isMember(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("仅领地成员可留言"));
            return 0;
        }
        int max = ModConfig.COMMON.plotMessageBoardSize.get();
        region.addMessage(player.getUUID(), player.getScoreboardName(), text, max);
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("已在 " + region.getName() + " 留言板发布留言")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}