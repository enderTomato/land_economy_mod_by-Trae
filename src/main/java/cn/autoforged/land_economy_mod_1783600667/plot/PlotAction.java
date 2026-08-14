package cn.autoforged.land_economy_mod_1783600667.plot;

/**
 * 地块操作类型枚举（购买/放弃）。客户端 -> 服务端请求时携带。
 */
public final class PlotAction {
    public enum Action { BUY, ABANDON }

    private PlotAction() {}
}
