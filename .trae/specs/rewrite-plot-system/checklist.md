# Checklist

## Phase 1: 清理旧代码
- [x] `PlotMapScreen.java` 已删除
- [x] `PlotMapView.java` 已删除
- [x] `PlotMapTerrainImage.java` 已删除
- [x] `PlotMapTerrainRenderer.java` 已删除
- [x] `MapOpener.java` 已删除
- [x] `ClientPacketReceivers.java` 不再引用 `PlotMapScreen`
- [x] `ClientKeyState.java` 不再引用 `PlotMapScreen`

## Phase 2: 新建核心系统
- [x] `PlotMapHandler.java` 已创建，支持 `openMap()` / `closeMap()` / `isMapOpen()`
- [x] `PlotMapHandler` 按优先级 JourneyMap > XaeroWorldMap > FallbackPlotScreen 打开地图
- [x] `FallbackPlotScreen.java` 已创建，支持纯色渲染 + 边界线 + WASD 平移 + 选框交互
- [x] `PlotKeyBindings.java` 已创建，注册购买键位（默认中键）和放弃键位（默认右键）
- [x] `ModConfig.java` 新增 `plotExpandDistanceMultiplier` 配置项

## Phase 3: 重写集成类
- [x] `JourneyMapIntegration.java` 保留 `RenderLevelStageEvent` 边界渲染
- [x] `JourneyMapIntegration` 鼠标事件通过 `MapScreenEventHandler` 统一处理
- [x] `XaeroWorldMapIntegration.java` 新增 `startSelection/updateSelection/endSelection` 选框方法
- [x] `XaeroWorldMapIntegration` 支持中键拖拽框选购买、右键放弃
- [x] `XaeroMinimapIntegration.java` 保留边界渲染，不支持选框
- [x] `MapScreenEventHandler.java` 使用 `PlotKeyBindings` 判断键位

## Phase 4: 扩建定价系统
- [x] `PlotService.java` 新增 `calculateExpandCost()` 距离定价方法
- [x] 距离定价公式: 基础价格 × (1 + 距离系数 × 最近距离)
- [x] `ModConfig.plotExpandDistanceMultiplier` 可配置

## Phase 5: 整合与清理
- [x] `ClientPacketReceivers.onOpenScreen(PLOT_MAP)` 调用 `PlotMapHandler.openMap()`
- [x] `ClientPacketReceivers.onForceExit()` 调用 `PlotMapHandler.closeMap()`
- [x] `ClientModEvents` 注册 `PlotKeyBindings`
- [x] 构建 `./gradlew build --no-daemon` 成功，无编译错误
- [x] 产物 `build/libs/land_economy_mod-1.8.0.jar` 存在（200KB）