# Tasks

## Phase 1: 清理旧代码

- [x] Task 1: 移除旧自研地图系统
  - [x] 删除 `PlotMapScreen.java`（自研地图界面）
  - [x] 删除 `PlotMapView.java`（视角状态）
  - [x] 删除 `PlotMapTerrainImage.java`（地形纹理预渲染）
  - [x] 删除 `PlotMapTerrainRenderer.java`（地形颜色采样）
  - [x] 删除 `MapOpener.java`（旧地图决策器，被 Phase 2 的 PlotMapHandler 替代）
  - [x] 更新 `ClientPacketReceivers.java`：移除对 `PlotMapScreen` 的所有 `instanceof` 检查
  - [x] 更新 `ClientKeyState.java`：移除 `PlotMapScreen` 文档引用（保留 WASD 工具方法供回退地图使用）

## Phase 2: 新建核心系统

- [x] Task 2: 创建 `PlotMapHandler.java` — 地图生命周期管理
  - 路径: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/plot/PlotMapHandler.java`
  - 职责: 管理地块地图的打开/关闭/强制退出，维护当前地图类型状态
  - 方法: `openMap()`, `closeMap()`, `isMapOpen()`, `getCurrentMapType()`
  - 逻辑: 检测 JourneyMap → Xaero's World Map → 回退地图，调用对应打开方法

- [x] Task 3: 创建 `FallbackPlotScreen.java` — 简化回退地图
  - 路径: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/plot/FallbackPlotScreen.java`
  - 职责: 无第三方地图时的简化 2D 俯视图
  - 功能: 纯色区块渲染 + 边界线，WASD 平移，滚轮缩放，左键拖拽框选购买，右键拖拽框选放弃，回车确认
  - 简化: 不渲染地形纹理，仅纯色 + 边框

- [x] Task 4: 创建 `PlotKeyBindings.java` — 可配置键位
  - 路径: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/plot/PlotKeyBindings.java`
  - 注册 Forge `KeyMapping`：购买键位（默认中键）、放弃键位（默认右键）
  - 在 `ClientModEvents` 中注册键位

## Phase 3: 重写集成类

- [x] Task 5: 重写 `JourneyMapIntegration.java` — 深度对接 JourneyMap API
  - 完整实现 `IMapIntegration` 接口
  - 保留 `MapBoundaryRenderer` 的 `RenderLevelStageEvent` 边界渲染
  - 鼠标事件通过 `MapScreenEventHandler` 统一处理

- [x] Task 6: 重写 `XaeroWorldMapIntegration.java` — 深度对接 Xaero's World Map
  - 完整实现 `IMapIntegration` 接口
  - 新增 `startSelection()`, `updateSelection()`, `endSelection()` 选框方法
  - 新增 `handleSingleAbandon()` 右键放弃方法
  - 保留 `MapBoundaryRenderer` 的 `RenderLevelStageEvent` 边界渲染

- [x] Task 7: 重写 `XaeroMinimapIntegration.java` — 保持现有边界渲染
  - 保留 `RenderLevelStageEvent` 边界渲染
  - 不支持选框（minimap 太小）

- [x] Task 8: 更新 `MapScreenEventHandler.java` — 适配新键位系统
  - 使用 `PlotKeyBindings` 判断键位（而非硬编码 Ctrl+左键/右键）
  - 中键按下 = 开始选框购买，中键释放 = 结束选框
  - 右键按下 = 放弃当前区块

## Phase 4: 扩建定价系统

- [x] Task 9: 扩建 `PlotService.java` — 距离定价
  - 新增 `calculateExpandCost(RegionData region, List<Long> newChunks)` 方法
  - 逻辑: 计算每个新区块到原区域所有区块的最近 Chebyshev 距离，价格 = 基础价格 × (1 + 距离系数 × 最近距离)
  - 新增 `plotExpandDistanceMultiplier` 配置项（默认 0.05）

- [x] Task 10: 更新 `ModConfig.java` 新增配置项
  - `plotExpandDistanceMultiplier` (Double, 默认 0.05)
  - 键位配置通过 `PlotKeyBindings` 的 Forge KeyMapping 实现（可改键位）

## Phase 5: 整合与清理

- [x] Task 11: 更新 `ClientPacketReceivers.java`
  - `onOpenScreen(PLOT_MAP)` 调用 `PlotMapHandler.openMap()`
  - `onForceExit()` 调用 `PlotMapHandler.closeMap()`
  - 移除所有 `PlotMapScreen` instanceof 检查

- [x] Task 12: 更新 `ClientModEvents.java`
  - 注册 `PlotKeyBindings` 键位

- [x] Task 13: 构建验证
  - 运行 `./gradlew build --no-daemon` 成功
  - 产物 `build/libs/land_economy_mod-1.8.0.jar` (200KB)