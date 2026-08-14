package cn.autoforged.land_economy_mod_1783600667.client;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化：目前仅作占位，后续可注册 KeyMapping、自定义渲染等。
 * 不注册全局 WASD KeyMapping — 地块地图 WASD 平移通过 GLFW 直接采样，
 * 避免在 Screen 关闭时仍抢占玩家实体移动。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LandEconomyMod.LOGGER.info("Land Economy Mod client setup complete.");
    }
}
