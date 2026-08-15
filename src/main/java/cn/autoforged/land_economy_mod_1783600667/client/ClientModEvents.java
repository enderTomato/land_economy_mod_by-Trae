package cn.autoforged.land_economy_mod_1783600667.client;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.integration.MapIntegrationManager;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotKeyBindings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化：注册第三方地图模组集成和键位。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MapIntegrationManager.init();
            PlotKeyBindings.register();
        });
        LandEconomyMod.LOGGER.info("Land Economy Mod client setup complete.");
    }
}
