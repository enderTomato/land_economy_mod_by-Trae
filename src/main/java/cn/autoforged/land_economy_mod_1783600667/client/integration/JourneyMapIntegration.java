package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotClientCache;
import cn.autoforged.land_economy_mod_1783600667.client.plot.PlotKeyBindings;
import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SPlotAction;
import cn.autoforged.land_economy_mod_1783600667.plot.PlotAction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/**
 * JourneyMap 集成。
 *
 * 通过 JourneyMap 公开 API（journeymap.client.api）实现：
 * - 在全屏地图上渲染区域边界（通过 RenderLevelStageEvent + MapBoundaryRenderer）
 * - 监听全屏地图鼠标事件实现选框交互（通过 MapScreenEventHandler）
 * - 中键拖拽 = 选框购买，右键 = 放弃
 */
public class JourneyMapIntegration implements IMapIntegration {

    private boolean apiAvailable = false;

    @Override
    public String getModName() { return "JourneyMap"; }

    @Override
    public boolean supportsSelection() { return true; }

    @Override
    public boolean supportsOverlay() { return true; }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        tryInitJourneyMapApi();
        LandEconomyMod.LOGGER.info("[JourneyMapIntegration] Initialized. apiAvailable={}", apiAvailable);
    }

    @Override
    public void shutdown() {
        MinecraftForge.EVENT_BUS.unregister(this);
        apiAvailable = false;
    }

    private void tryInitJourneyMapApi() {
        try {
            Class.forName("journeymap.client.api.ClientAPI");
            apiAvailable = true;
        } catch (Exception e) {
            LandEconomyMod.LOGGER.warn("[JourneyMapIntegration] JourneyMap API not found: {}", e.getMessage());
        }
    }

    /**
     * 在世界空间渲染区域边界。
     */
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!apiAvailable || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        MapBoundaryRenderer.drawWorldBoundaries(event);
    }
}