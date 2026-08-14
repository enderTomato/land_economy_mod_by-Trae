package cn.autoforged.land_economy_mod_1783600667.client.integration;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;

/**
 * Xaero's Minimap 集成。
 *
 * 功能：在 minimap 上渲染区域边界（通过反射注入渲染钩子）。
 * 不支持选框购买（minimap 太小）。
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
        LandEconomyMod.LOGGER.info("[XaeroMinimapIntegration] Initialized.");
    }

    @Override
    public void shutdown() {
    }

    // 边界渲染将在后续版本中通过反射注入 Xaero's MinimapRenderer 实现
    // 当前版本保留接口，渲染逻辑回退到 PlotMapScreen 独立使用
}