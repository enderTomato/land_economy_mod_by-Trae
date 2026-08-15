package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Xaero's Minimap 集成。
 *
 * 在 minimap 上渲染区域边界（通过 RenderLevelStageEvent 在世界空间绘制）。
 * 不支持选框（minimap 太小）。
 */
public class XaeroMinimapIntegration implements IMapIntegration {

    @Override
    public String getModName() { return "Xaero's Minimap"; }

    @Override
    public boolean supportsSelection() { return false; }

    @Override
    public boolean supportsOverlay() { return true; }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        LandEconomyMod.LOGGER.info("[XaeroMinimapIntegration] Initialized.");
    }

    @Override
    public void shutdown() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        MapBoundaryRenderer.drawWorldBoundaries(event);
    }
}