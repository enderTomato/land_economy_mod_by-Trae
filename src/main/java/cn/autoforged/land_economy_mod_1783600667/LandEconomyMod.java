package cn.autoforged.land_economy_mod_1783600667;

import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.economy.GDPEngine;
import cn.autoforged.land_economy_mod_1783600667.economy.PopulationEngine;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(LandEconomyMod.MOD_ID)
public class LandEconomyMod {
    public static final String MOD_ID = "land_economy_mod_1783600667";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LandEconomyMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModConfig.register();

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("Land Economy Mod initialized");
        });
    }

    private boolean enginesRegistered = false;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        GDPEngine engine = GDPEngine.getInstance();
        PopulationEngine popEngine = PopulationEngine.getInstance();
        engine.startScheduledTasks(event.getServer());
        popEngine.startScheduledTasks(event.getServer());
        if (!enginesRegistered) {
            MinecraftForge.EVENT_BUS.register(engine);
            MinecraftForge.EVENT_BUS.register(popEngine);
            enginesRegistered = true;
        }
        LOGGER.info("Land Economy Mod server started");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        GDPEngine.getInstance().shutdown();
        PopulationEngine.getInstance().shutdown();
        LOGGER.info("Land Economy Mod server stopping");
    }

    public static EconomySavedData getEconomyData() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        Level overworld = server.overworld();
        if (overworld == null) return null;
        return EconomySavedData.get(overworld);
    }
}
