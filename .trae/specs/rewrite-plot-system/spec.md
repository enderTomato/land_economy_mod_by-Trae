# 重写"地图地块系统" Spec

## Why
当前 `/land map` 自研地图 `PlotMapScreen` 性能极差（卡顿），且与第三方地图模组（JourneyMap、Xaero's World Map）的集成不够深度——仅能在边界渲染层面叠加，无法利用第三方地图的高性能渲染引擎和完整交互。需要彻底重写，以第三方地图为主引擎，自研地图仅作回退。

## What Changes
- **BREAKING**: 移除自研 `PlotMapScreen` 及其关联类（`PlotMapView`、`PlotMapTerrainImage`、`PlotMapTerrainRenderer`、`PlotMapScreen`）
- **BREAKING**: 移除 `MapOpener`（被新的 `PlotMapHandler` 替代）
- 保留 `PlotClientCache`（客户端区块归属缓存）和 `MapBoundaryRenderer`（边界渲染）
- 保留 `MapSelectionConfirmScreen`（确认弹窗）
- 重写集成类，深度对接 JourneyMap 和 Xaero's World Map 的全屏地图 API
- 新增可配置键位系统（中键拖拽=购买选框，右键=放弃，可改键位）
- 新增扩大区域定价系统（距离原区域越远越贵）
- 新增自定义购买/扩大价格配置

## Impact
- Affected specs: 无（全新 spec）
- Affected code: 见下方详细文件列表

---

## ADDED Requirements

### Requirement: 第三方地图为主引擎
系统 SHALL 在 `/land map` 触发时，检测已安装的地图模组，优先使用第三方地图的全屏地图作为地块操作界面，仅在无第三方地图时使用自研回退地图。

#### Scenario: JourneyMap 已安装
- **WHEN** 玩家执行 `/land map` 且 JourneyMap 已加载
- **THEN** 系统通过 JourneyMap API 打开全屏地图，并在其上叠加地块边界渲染和选框交互

#### Scenario: Xaero's World Map 已安装（无 JourneyMap）
- **WHEN** 玩家执行 `/land map` 且 Xaero's World Map 已加载、JourneyMap 未加载
- **THEN** 系统通过反射打开 Xaero's World Map 全屏地图，并在其上叠加地块边界渲染和选框交互

#### Scenario: 无第三方地图
- **WHEN** 玩家执行 `/land map` 且无任何第三方地图模组
- **THEN** 系统打开自研回退地图（简化版 2D 俯视图）

### Requirement: 第三方地图上选框交互
在第三方地图全屏中，玩家 SHALL 能够使用键位拖拽框选区块进行购买/放弃操作。

#### Scenario: 中键拖拽框选购买
- **WHEN** 玩家在第三方地图全屏中按住可配置的"购买键位"（默认鼠标中键）并拖拽
- **THEN** 系统在地图上绘制选框矩形，释放后计算框内可购买区块，弹出确认界面

#### Scenario: 右键单击放弃
- **WHEN** 玩家在第三方地图全屏中按可配置的"放弃键位"（默认鼠标右键）点击已购买区块
- **THEN** 系统将该区块标记为待放弃，弹出确认界面

#### Scenario: 键位可配置
- **WHEN** 管理员修改配置文件中的键位设置
- **THEN** 购买/放弃操作使用新配置的键位

### Requirement: 自定义购买和扩大价格
系统 SHALL 支持配置每区块的基础购买价格和扩大区域的价格公式。

#### Scenario: 基础购买价格
- **WHEN** 配置 `plotCostPerChunk` 为 100
- **THEN** 每购买一个区块花费 100

#### Scenario: 扩大区域距离定价
- **WHEN** 玩家扩大区域，新区块距离原区域中心越远
- **THEN** 价格 = 基础价格 × (1 + 距离系数 × 距离)，距离越远越贵

#### Scenario: 价格配置可热更新
- **WHEN** 管理员通过 `/economy reloadconfig` 重载配置
- **THEN** 新的价格配置立即生效

### Requirement: 自研回退地图
当无第三方地图时，系统 SHALL 提供一个简化的 2D 俯视地图，支持基本的区块浏览和购买/放弃操作。

#### Scenario: 回退地图基本渲染
- **WHEN** 无第三方地图且玩家打开 `/land map`
- **THEN** 显示简化 2D 俯视图（纯色区块 + 边界线，无地形纹理）

#### Scenario: 回退地图选框交互
- **WHEN** 玩家在回退地图中使用左键拖拽框选
- **THEN** 系统绘制选框矩形，释放后弹出确认界面

### Requirement: 边界渲染保留
系统 SHALL 保留现有的 `MapBoundaryRenderer`，在第三方地图和回退地图上均渲染区块归属边界。

### Requirement: 强制退出保留
系统 SHALL 保留受击/传送时强制退出地块界面的逻辑，并适配第三方地图全屏的关闭。

---

## MODIFIED Requirements

### Requirement: 地块操作命令
**原**: `/land map` 仅打开自研 `PlotMapScreen`
**改为**: `/land map` 按优先级自动选择第三方地图或回退地图

### Requirement: 配置项
**新增**:
- `plotExpandDistanceMultiplier` (Double, 默认 0.05): 扩大区域距离系数
- `plotBuyKey` (String, 默认 "middle"): 购买选框键位
- `plotAbandonKey` (String, 默认 "right"): 放弃区块键位

**保留**:
- `plotCostPerChunk`: 每区块基础购买费用
- `plotRefundPerChunk`: 放弃每区块返还
- `plotMaxChunksPerPlayer`: 最大区块数
- `plotMapIntegrationEnabled`: 第三方地图集成开关
- `plotJourneyMapIntegration`: JourneyMap 集成开关
- `plotXaeroWorldMapIntegration`: Xaero's World Map 集成开关

---

## REMOVED Requirements

### Requirement: PlotMapScreen 地形纹理渲染
**Reason**: 性能差，被第三方地图替代
**Migration**: 无，第三方地图自带高性能地形渲染

### Requirement: PlotMapTerrainRenderer 缓存
**Reason**: 随 PlotMapScreen 移除
**Migration**: 无

### Requirement: MapOpener
**Reason**: 被新的 PlotMapHandler 替代
**Migration**: 逻辑合并到新的 PlotMapHandler