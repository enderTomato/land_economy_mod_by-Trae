package cn.autoforged.land_economy_mod_1783600667.client.integration;

/**
 * 第三方地图模组集成接口。
 * 每个实现对应一个地图模组（JourneyMap / Xaero's Minimap / Xaero's World Map）。
 */
public interface IMapIntegration {

    /** 初始化集成（注册事件监听器、渲染钩子） */
    void init();

    /** 注销集成 */
    void shutdown();

    /** 返回此集成支持的模组名称 */
    String getModName();

    /** 是否支持选框购买（JourneyMap 全屏 / Xaero's World Map） */
    boolean supportsSelection();

    /** 是否支持边界渲染（所有地图） */
    boolean supportsOverlay();
}