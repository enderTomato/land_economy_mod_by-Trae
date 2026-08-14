package cn.autoforged.land_economy_mod_1783600667.command;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("land")
                .then(Commands.literal("?")
                        .executes(ctx -> RegionCommandHandler.help(ctx)))
                .then(Commands.literal("help")
                        .executes(ctx -> RegionCommandHandler.help(ctx)))
                // /land claim pos1 <x1> <z1> pos2 <x2> <z2> [name]
                .then(Commands.literal("claim")
                        .then(Commands.literal("child")
                                .then(Commands.literal("pos1")
                                        .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                        .then(Commands.literal("pos2")
                                                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                        .executes(ModCommands::claimChildRegion))
                                                                                .executes(ModCommands::claimChildRegion)))))))
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Component.literal("请使用 /land claim child pos1 <x> <z> pos2 <x> <z> [名称] 创建子区域"));
                                    return 0;
                                }))
                        .then(Commands.literal("pos1")
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                .then(Commands.literal("pos2")
                                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                .executes(ModCommands::claimLandWithPos))
                                                                        .executes(ModCommands::claimLandWithPos)))))))
                        .executes(ModCommands::claimLand))
                // /land add pos1 <x1> <z1> pos2 <x2> <z2>
                .then(Commands.literal("add")
                        .then(Commands.literal("pos1")
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                .then(Commands.literal("pos2")
                                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                        .executes(ModCommands::expandRegion)))))))
                        .executes(ctx -> {
                            ctx.getSource().sendFailure(Component.literal("请使用 /land add pos1 <x> <z> pos2 <x> <z> 扩大区域"));
                            return 0;
                        }))
                // /land unclaim <Block/Child/flyland>
                .then(Commands.literal("unclaim")
                        .then(Commands.literal("Block")
                                .executes(ModCommands::unclaimLandBlock))
                        .then(Commands.literal("Child")
                                .executes(ModCommands::unclaimLandChild))
                        .then(Commands.literal("flyland")
                                .executes(ModCommands::unclaimLandFlyland)))
                .then(Commands.literal("info")
                        .executes(ModCommands::landInfo))
                .then(Commands.literal("list")
                        .executes(ModCommands::listLands))
                .then(Commands.literal("setname")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ModCommands::setLandName)))
                // /land display [title|actionbar] — 选择区域进入提示的显示方式
                .then(Commands.literal("display")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(ModCommands::suggestDisplayModes)
                                .executes(ModCommands::setDisplayMode))
                        .executes(ModCommands::showDisplayMode))
                .then(Commands.literal("permissions")
                        .then(Commands.literal("set")
                                .then(Commands.argument("permission", StringArgumentType.word())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ModCommands::setPermission))))
                        .executes(ModCommands::listPermissions))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ModCommands::invitePlayer)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ModCommands::kickPlayer)))
                .then(Commands.literal("leave")
                        .executes(ModCommands::leaveRegion))
                // /land bank deposit/withdraw <amount> [player]
                .then(Commands.literal("bank")
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ModCommands::bankDeposit))
                                        .executes(ModCommands::bankDepositSelf)))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ModCommands::bankWithdraw))
                                        .executes(ModCommands::bankWithdrawSelf))))
                // /land join <blockName> (with tab completion)
                .then(Commands.literal("join")
                        .then(Commands.literal("list")
                                .executes(ModCommands::listPendingRequests))
                        .then(Commands.argument("player_name", StringArgumentType.word())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .executes(ModCommands::handleJoinRequest)))
                        .then(Commands.argument("region_name", StringArgumentType.greedyString())
                                .suggests(ModCommands::suggestJoinRegions)
                                .executes(ModCommands::applyJoinRegion)))
                // /land data add <Name> — add folder under GDPdata and write CSV
                // /land data <regionName> — write CSV under GDPdata/<regionName>
                .then(Commands.literal("data")
                        .then(Commands.literal("add")
                                .then(Commands.argument("folder_name", StringArgumentType.word())
                                        .executes(ModCommands::landDataAddWrite)))
                        .then(Commands.argument("region_name", StringArgumentType.string())
                                .suggests(ModCommands::suggestRegionNames)
                                .executes(ModCommands::landDataWrite)))
                // /land setOutlay <new/add> <digit>
                .then(Commands.literal("setOutlay")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("new")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ModCommands::setOutlayNew)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ModCommands::setOutlayAdd))))
                .then(Commands.literal("flyland")
                        .then(Commands.literal("claim")
                                .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                                .executes(ModCommands::claimFlyland))))
                        .then(Commands.literal("info")
                                .executes(ModCommands::flylandInfo)))
        );

        dispatcher.register(Commands.literal("economy")
                .then(Commands.literal("gdp")
                        .executes(ModCommands::showGdp))
                .then(Commands.literal("gdpdetail")
                        .executes(ModCommands::showGdpDetail))
                .then(Commands.literal("population")
                        .executes(ModCommands::showPopulation))
                .then(Commands.literal("regiontype")
                        .executes(ModCommands::showRegionType))
                .then(Commands.literal("status")
                        .executes(ModCommands::showStatus))
                .then(Commands.literal("calcgdp")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::forceCalcGdp))
                .then(Commands.literal("checkpop")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::forceCheckPop))
                .then(Commands.literal("addvalue")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01))
                                .executes(ModCommands::addItemValue)))
                .then(Commands.literal("removevalue")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("item", StringArgumentType.string())
                                .executes(ModCommands::removeItemValue)))
                .then(Commands.literal("addindustry")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("industry", StringArgumentType.word())
                                .executes(ModCommands::addIndustryItem)))
                .then(Commands.literal("reloadconfig")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::reloadConfig))
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("containerOnly")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ModCommands::setContainerOnly)))
                        .then(Commands.literal("maxConcurrent")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 8))
                                        .executes(ModCommands::setMaxConcurrent)))
                        .then(Commands.literal("multiThreaded")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ModCommands::setMultiThreaded)))
                        .then(Commands.literal("gdpInterval")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(5, 2880))
                                        .executes(ModCommands::setGdpInterval)))
                        .then(Commands.literal("popCheckHours")
                                .then(Commands.argument("hours", IntegerArgumentType.integer(5, 2880))
                                        .executes(ModCommands::setPopCheckHours)))
                        .then(Commands.literal("Blocktype")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("gdp", DoubleArgumentType.doubleArg(0))
                                                .then(Commands.argument("population", IntegerArgumentType.integer(1))
                                                        .executes(ModCommands::setBlockType))))))
        );

        // /math gdp, /math settime
        dispatcher.register(Commands.literal("math")
                .then(Commands.literal("gdp")
                        .executes(ModCommands::mathGdp))
                .then(Commands.literal("settime")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .then(Commands.argument("unit", StringArgumentType.word())
                                        .executes(ModCommands::setMathGdpCooldown))))
        );

        dispatcher.register(Commands.literal("value")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(ModCommands::valueAdd))))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ModCommands::valueSet))))
                .then(Commands.literal("dec")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(ModCommands::valueDec))))
        );
    }

    // Tab completion for /land join <region_name>
    private static CompletableFuture<Suggestions> suggestJoinRegions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data != null) {
            for (RegionData r : data.getAllRegions()) {
                if (!r.isFlyland()) {
                    String name = r.getName();
                    if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                        builder.suggest(name);
                    }
                }
            }
        }
        return builder.buildFuture();
    }

    // Tab completion for /land display <mode>
    private static CompletableFuture<Suggestions> suggestDisplayModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("title");
        builder.suggest("actionbar");
        return builder.buildFuture();
    }

    // Tab completion for /land data <region_name>
    private static CompletableFuture<Suggestions> suggestRegionNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data != null) {
            for (RegionData r : data.getAllRegions()) {
                String name = r.getName();
                if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }

    private static int claimLand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ctx.getSource().sendFailure(Component.literal("请使用 /land claim pos1 <x> <z> pos2 <x> <z> [名称] 创建领地"));
        return 0;
    }

    private static int claimLandWithPos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.claimLandWithPos(ctx);
    }

    private static int claimChildRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.claimChildRegion(ctx);
    }

    private static int unclaimLandBlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.unclaimLandBlock(ctx);
    }

    private static int unclaimLandChild(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.unclaimLandChild(ctx);
    }

    private static int unclaimLandFlyland(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.unclaimLandFlyland(ctx);
    }

    private static int landInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.landInfo(ctx);
    }

    private static int listLands(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.listLands(ctx);
    }

    private static int setLandName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.setLandName(ctx);
    }

    private static int setDisplayMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.setDisplayMode(ctx);
    }

    private static int showDisplayMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.showDisplayMode(ctx);
    }

    private static int setPermission(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.setPermission(ctx);
    }

    private static int listPermissions(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.listPermissions(ctx);
    }

    private static int invitePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.invitePlayer(ctx);
    }

    private static int kickPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.kickPlayer(ctx);
    }

    private static int leaveRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.leaveRegion(ctx);
    }

    private static int bankDeposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.bankDeposit(ctx);
    }

    private static int bankDepositSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.bankDepositSelf(ctx);
    }

    private static int bankWithdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.bankWithdraw(ctx);
    }

    private static int bankWithdrawSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.bankWithdrawSelf(ctx);
    }

    private static int applyJoinRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.applyJoinRegion(ctx);
    }

    private static int handleJoinRequest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.handleJoinRequest(ctx);
    }

    private static int listPendingRequests(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.listPendingRequests(ctx);
    }

    private static int landDataAddWrite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.landDataAddWrite(ctx);
    }

    private static int landDataWrite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.landDataWrite(ctx);
    }

    private static int setOutlayNew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.setOutlayNew(ctx);
    }

    private static int setOutlayAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.setOutlayAdd(ctx);
    }

    private static int claimFlyland(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.claimFlyland(ctx);
    }

    private static int expandRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.expandRegion(ctx);
    }

    private static int flylandInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return RegionCommandHandler.flylandInfo(ctx);
    }

    private static int showGdp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.showGdp(ctx);
    }

    private static int showGdpDetail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.showGdpDetail(ctx);
    }

    private static int showPopulation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.showPopulation(ctx);
    }

    private static int showRegionType(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.showRegionType(ctx);
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.showStatus(ctx);
    }

    private static int forceCalcGdp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.forceCalcGdp(ctx);
    }

    private static int forceCheckPop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.forceCheckPop(ctx);
    }

    private static int addItemValue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.addItemValue(ctx);
    }

    private static int removeItemValue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.removeItemValue(ctx);
    }

    private static int addIndustryItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.addIndustryItem(ctx);
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.reloadConfig(ctx);
    }

    private static int mathGdp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.mathGdp(ctx);
    }

    private static int setMathGdpCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setMathGdpCooldown(ctx);
    }

    private static int setBlockType(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setBlockType(ctx);
    }

    private static int valueAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.valueAdd(ctx);
    }

    private static int valueSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.valueSet(ctx);
    }

    private static int valueDec(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.valueDec(ctx);
    }

    private static int setContainerOnly(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setContainerOnly(ctx);
    }

    private static int setMaxConcurrent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setMaxConcurrent(ctx);
    }

    private static int setMultiThreaded(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setMultiThreaded(ctx);
    }

    private static int setGdpInterval(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setGdpInterval(ctx);
    }

    private static int setPopCheckHours(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EconomyCommandHandler.setPopCheckHours(ctx);
    }
}
