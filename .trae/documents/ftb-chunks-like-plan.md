# 计划：删除旧地块系统 + 模仿 FTB Chunks 重做 /land map

## 摘要

1. 完全删除上一轮"新地图地块系统"的全部代码（客户端地图、集成、网络包、配置）
2. 保留 `RegionData.claimedChunks` 数据模型（区块归属存储）
3. 新建一个高度模仿 FTB Chunks 的简洁全屏地图界面

## FTB Chunks 核心特征（目标）

| 特征 | 说明 |
|------|------|
| 区块网格渲染 | 每个 16×16 区块显示为纯色方块，不渲染地形纹理 |
| 颜色编码 | 蓝色=我的、红色=他人的、绿色=空、白色=选中 |
| 左键拖拽框选 | 框选购买区块 |
| 右键拖拽框选 | 框选放弃区块 |
| 玩家位置指示 | 地图上显示玩家头像/箭头 |
| 坐标显示 | 顶部显示中心区块坐标 |
| WASD 平移 | 键盘平移视角 |
| 滚轮缩放 | 缩放区块大小 |
| 回车确认 | 确认购买/放弃操作 |
| 性能优异 | 零纹理加载，纯 GPU 绘制矩形 |

## 第一步：删除旧代码

### 删除整个文件（18 个文件）

**client/plot/ 目录（5 个文件）**:
- `FallbackPlotScreen.java`
- `PlotKeyBindings.java`
- `PlotMapHandler.java`
- `PlotClientCache.java`

**client/integration/ 目录（8 个文件）**:
- `IMapIntegration.java`
- `JourneyMapIntegration.java`
- `XaeroWorldMapIntegration.java`
- `XaeroMinimapIntegration.java`
- `MapBoundaryRenderer.java`
- `MapIntegrationManager.java`
- `MapScreenEventHandler.java`
- `MapSelectionConfirmScreen.java`

**plot/ 目录（1 个文件）**:
- `PlotService.java` — 服务端逻辑（将用新的替换）
- `PlotAction.java` — 操作枚举（将用新的替换）

**network/ 目录（7 个文件）**:
- `PacketC2SPlotAction.java`
- `PacketC2SRequestPlotData.java`
- `PacketS2CPlotChunkData.java`
- `PacketS2CPlotActionResult.java`
- `PacketS2COpenScreen.java`
- `PacketS2CForceExitPlot.java`
- `PacketC2SOpenPlotMap.java`

### 修改现有文件（清理 plot 相关代码）

**ModConfig.java**:
- 删除字段: `plotSystemEnabled`, `plotCostPerChunk`, `plotRefundPerChunk`, `plotMaxChunksPerPlayer`, `plotMapViewRadius`, `plotMessageBoardSize`, `plotExpandDistanceMultiplier`, `plotMapIntegrationEnabled`, `plotJourneyMapIntegration`, `plotXaeroMinimapIntegration`, `plotXaeroWorldMapIntegration`
- 删除对应的 builder 定义

**ModMessages.java**:
- 删除 7 个 plot 包的注册（PacketC2SOpenPlotMap, PacketC2SRequestPlotData, PacketC2SPlotAction, PacketS2CPlotChunkData, PacketS2CPlotActionResult, PacketS2COpenScreen, PacketS2CForceExitPlot）

**ClientPacketReceivers.java**:
- 删除方法: `onPlotChunkData()`, `onPlotActionResult()`, `onOpenScreen()`, `onForceExit()`
- 删除 plot 相关 import

**RegionCommandHandler.java**:
- 删除方法: `openMap()`, `setPlotMode()`, `handlePlotMode()`
- 删除 plot 相关 import

**ModCommands.java**:
- 删除 `map` 和 `mode` 子命令注册

**RegionEventListener.java**:
- 删除 `forceExitPlotIfActive()` 及相关逻辑

**EconomySavedData.java**:
- 删除字段: `playerPlotMode`, `playersInPlotMode`
- 删除方法: `getPlayerPlotMode()`, `setPlayerPlotMode()`, `isInPlotMode()`, `setInPlotMode()`, `snapshotPlotCells()`, `getRegionOwningChunk()`
- 删除 `PlotCell` record
- 删除序列化/反序列化中的 plot 相关代码

**ClientModEvents.java**:
- 删除 `MapIntegrationManager.init()` 和 `PlotKeyBindings.register()` 调用
- 删除相关 import

**ClientKeyState.java**:
- 删除 plot 相关注释（保留 WASD 工具方法供新地图使用）

**zh_cn.json**:
- 删除所有 `economy.plot_*` 和 `key.land_economy.*` 条目

## 第二步：新建 FTB Chunks 风格地图

### 新建文件

**`ChunkClaimScreen.java`** — 核心地图界面
- 路径: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/screen/ChunkClaimScreen.java`
- 继承 `Screen`
- 渲染:
  - 深色背景 + 区块网格
  - 每个区块渲染为纯色矩形（基于 PlotClientCache 数据）
  - 颜色: 蓝色=我的(0x400000FF)、红色=他人(0x40FF0000)、绿色=空(0x4000FF00)、白色=选中(0x60FFFFFF)
  - 边框线: 实色边框线
  - 玩家位置: 白色小圆点/十字
  - 坐标显示: 顶部文字显示当前视图中心区块坐标
- 交互:
  - WASD 平移（使用 ClientKeyState）
  - 滚轮缩放（8px ~ 64px 范围）
  - 左键拖拽框选 = 购买
  - 右键拖拽框选 = 放弃
  - 回车 = 确认操作
  - Esc = 关闭
- 数据请求: 通过 `PacketC2SRequestChunkData` 请求服务器数据

**`ChunkClaimService.java`** — 服务端区块认领/放弃逻辑
- 路径: `src/main/java/cn/autoforged/land_economy_mod_1783600667/claim/ChunkClaimService.java`
- 简化版 PlotService:
  - `claim(ServerPlayer, List<Long> chunks, String dim)` — 购买区块
  - `unclaim(ServerPlayer, List<Long> chunks, String dim)` — 放弃区块
  - 保持现有逻辑: 资金校验、上限校验、冲突校验
  - 新增: 距离定价（保留 `calculateExpandCost`）

**网络包（3 个新包）**:

1. **`PacketC2SRequestChunkData.java`** — 客户端请求区块数据
   - 字段: `int centerCX, int centerCZ`
   - 服务端: 返回视图范围内的区块归属

2. **`PacketS2CChunkData.java`** — 服务端下发区块数据
   - 字段: `List<ChunkCell> cells, int cx0, int cz0, int cx1, int cz1`
   - `ChunkCell(long chunkKey, UUID owner, String regionName)`

3. **`PacketC2SChunkAction.java`** — 客户端请求购买/放弃
   - 字段: `Action action, List<Long> chunks, String dim`
   - 服务端: 调用 ChunkClaimService

4. **`PacketS2CChunkActionResult.java`** — 服务端下发操作结果
   - 字段: `boolean success, String message, List<Long> updatedChunks`

**`ChunkClaimCache.java`** — 客户端缓存
- 路径: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/screen/ChunkClaimCache.java`
- 简化版 PlotClientCache: 存储 `Map<Long, Cell>` 缓存区块归属

### 修改现有文件

**ModConfig.java**:
- 新增简洁配置:
  - `chunkCostPerChunk` (Double, 默认 100.0): 每区块购买费用
  - `chunkRefundPerChunk` (Double, 默认 50.0): 放弃每区块返还
  - `chunkMaxPerPlayer` (Int, 默认 -1): 最大区块数
  - `chunkExpandDistanceMultiplier` (Double, 默认 0.05): 扩大距离系数

**ModMessages.java**:
- 注册 4 个新网络包

**ClientPacketReceivers.java**:
- 新增 `onChunkData()`, `onChunkActionResult()` 方法

**RegionCommandHandler.java**:
- 新增 `openClaimMap()` 方法（/land map 命令实现）
- 逻辑: 标记玩家 inPlotMode，发送打开屏幕包

**ModCommands.java**:
- 注册 `/land map` 命令（指向 `openClaimMap`）

**ClientModEvents.java**:
- 无需特殊初始化（新地图不依赖第三方模组）

**RegionEventListener.java**:
- 新增 `forceExitClaimMap()` 方法（受击/传送时关闭地图）

**zh_cn.json**:
- 新增简洁的区块认领相关条目

## 第三步：构建验证

- 运行 `./gradlew build --no-daemon`
- 验证产物 `build/libs/land_economy_mod-1.8.0.jar`

---

## 假设与决策

1. **RegionData.claimedChunks 保留**: 这是区块归属的实际存储，不删除
2. **EconomySavedData 保留 RegionData 管理**: 通过 `getRegionByOwner()` 等方法操作
3. **不依赖第三方地图**: 新系统完全自给，不集成 JourneyMap/Xaero
4. **不渲染地形**: 纯色区块网格，性能远优于旧系统
5. **简化网络协议**: 4 个包替代旧的 7 个包
6. **保留消息板功能**: 与 plot 无关的消息板功能保留