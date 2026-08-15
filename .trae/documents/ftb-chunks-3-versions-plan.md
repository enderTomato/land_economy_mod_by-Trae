# 计划：删除旧地块系统 + 模仿 FTB Chunks 重做 /land map（3版方案）

---

## FTB Chunks 深度调研总结

### 核心架构
FTB Chunks 采用 **common/fabric/neoforge** 多平台架构，核心逻辑在 `common/` 中，平台特定代码在 `fabric/` 和 `neoforge/` 中。

### 关键功能特性（基于源码和Changelog研究）
| 功能 | 实现方式 |
|------|----------|
| 大屏地图 | `LargeMapScreen` 类，继承 `Screen`，全屏渲染 |
| 区块网格 | 每个16×16区块渲染为纯色方块，叠加地形纹理（可选） |
| 颜色编码 | 蓝色=我的团队、红色=其他团队、绿色=荒野、白色=选中 |
| 左键认领 | 点击 `Claimed Chunks` 按钮后，左键拖拽框选购买 |
| 右键放弃 | 同上模式，右键拖拽框选放弃 |
| Shift+点击 | 切换强制加载（forceload） |
| 路标系统 | 右键地图任意位置创建路标，右键编辑/删除 |
| 死亡点 | 自动记录死亡位置，右键编辑/删除 |
| 队友共享 | 互相添加为盟友后共享地图数据 |
| 性能优化 | 地图图片写入磁盘在后台线程；GPU纹理上传优化；LRU 区域内存释放（默认32区域）；缩放时区域数/内存比例限制 |
| 缩放记忆 | 大屏地图缩放级别跨打开/关闭记忆 |
| 实体图标 | 玩家/实体在地图上显示为图标，带暗色边框提高对比度 |
| 地表扫描 | 可配置最小Y值扫描地表方块，忽略草/灌木等 |
| 左侧面板 | 按钮：Claimed Chunks、Allies、Team Properties、Settings、Minimap Info、Dimension Selector |

### 关键交互流程
1. 打开地图 → `LargeMapScreen.openMap()`
2. 地图渲染 → 按区域(Region)加载地形纹理 + 覆盖区块网格
3. 认领 → 点击 `Claimed Chunks` 按钮进入认领模式 → 左键拖拽框选 → 发送网络包 → 服务端处理
4. 放弃 → 同上，右键拖拽框选
5. 强制加载 → Shift+左键点击已认领区块

---

## 三版方案对比总览

| 维度 | 方案一：极简轻量 | 方案二：标准均衡 | 方案三：完整特性 |
|------|:---:|:---:|:---:|
| **新增文件数** | 3 | 6 | 10 |
| **网络包数** | 2 | 4 | 5 |
| **区块网格渲染** | 纯色 | 纯色+边框 | 纯色+边框+地形纹理（可选） |
| **WASD平移** | 支持 | 支持 | 支持 |
| **滚轮缩放** | 固定步长 | 平滑缩放 | 平滑缩放+记忆 |
| **左键框选购买** | 矩形选区 | 矩形选区 | 矩形选区+区块预览 |
| **右键框选放弃** | 矩形选区 | 矩形选区 | 矩形选区+区块预览 |
| **强制加载** | 不支持 | 不支持 | Shift+左键切换 |
| **路标/死亡点** | 不支持 | 不支持 | 右键创建路标 |
| **队友/盟友视图** | 不支持 | 不支持 | 支持 |
| **左侧按钮面板** | 无 | 无 | 有（认领/设置/维度） |
| **客户端缓存** | 无 | 有 | 有+LRU淘汰 |
| **性能优化** | 基础 | 基础 | 离线渲染+纹理图集 |
| **代码量（估算）** | ~400行 | ~800行 | ~1500行 |
| **开发难度** | 低 | 中 | 高 |
| **FTB还原度** | 40% | 65% | 90% |

---

## 共同的前置步骤：删除旧代码

无论选择哪个方案，以下删除操作完全相同：

### 删除整个文件（18个）

**client/plot/ 目录（4个）**:
- `FallbackPlotScreen.java`
- `PlotKeyBindings.java`
- `PlotMapHandler.java`
- `PlotClientCache.java`

**client/integration/ 目录（8个）**:
- `IMapIntegration.java`
- `JourneyMapIntegration.java`
- `XaeroWorldMapIntegration.java`
- `XaeroMinimapIntegration.java`
- `MapBoundaryRenderer.java`
- `MapIntegrationManager.java`
- `MapScreenEventHandler.java`
- `MapSelectionConfirmScreen.java`

**plot/ 目录（2个）**:
- `PlotService.java`
- `PlotAction.java`

**network/ 目录（4个纯plot包）**:
- `PacketC2SOpenPlotMap.java`
- `PacketC2SPlotAction.java`
- `PacketS2CPlotActionResult.java`
- `PacketS2CForceExitPlot.java`

### 修改现有文件（清理plot相关代码）

**ModConfig.java** — 删除以下字段及builder定义:
- `plotSystemEnabled`, `plotCostPerChunk`, `plotRefundPerChunk`, `plotMaxChunksPerPlayer`
- `plotMapViewRadius`, `plotMessageBoardSize`, `plotExpandDistanceMultiplier`
- `plotMapIntegrationEnabled`, `plotJourneyMapIntegration`, `plotXaeroMinimapIntegration`, `plotXaeroWorldMapIntegration`

**ModMessages.java** — 删除以下包注册:
- `PacketC2SOpenPlotMap`, `PacketC2SPlotAction`, `PacketS2CPlotActionResult`
- `PacketS2CForceExitPlot`

**ClientPacketReceivers.java** — 删除方法:
- `onPlotActionResult()`, `onForceExit()`
- 保留 `onPlotChunkData()` 和 `onOpenScreen()` 用于后续修改

**RegionCommandHandler.java** — 删除方法:
- `setPlotMode()` (含 /land mode 命令)
- 删除 plot 相关 import

**ModCommands.java** — 删除:
- `/land mode` 子命令注册
- `setPlotMode()` 方法
- `suggestPlotModes()` 方法

**ClientModEvents.java** — 删除:
- `MapIntegrationManager.init()` 调用
- `PlotKeyBindings.register()` 调用
- 相关 import

**RegionEventListener.java** — 删除:
- `forceExitPlotIfActive()` 及相关逻辑

**EconomySavedData.java** — 删除:
- `playerPlotMode` 字段和序列化
- `playersInPlotMode` 字段
- `getPlayerPlotMode()`, `setPlayerPlotMode()`, `isInPlotMode()`, `setInPlotMode()` 方法

**zh_cn.json** — 删除所有 `economy.plot_*` 和 `key.land_economy.*` 条目

### 保留不动
- `EconomySavedData.snapshotPlotCells()` 和 `PlotCell` record — 数据查询层保留
- `EconomySavedData.getRegionOwningChunk()` — 根据区块键查找归属区域
- `RegionData.claimedChunks` — 区块归属存储
- `PacketC2SRequestPlotData.java` — 客户端请求区块数据（将复用）
- `PacketS2CPlotChunkData.java` — 服务端下发区块数据（将复用）
- `PacketS2COpenScreen.java` — 改为只保留 CHEST 类型
- `PacketS2CRegionDetail.java` 和 `PacketC2SRequestRegionDetail.java` — 区域详情功能保留

---

# 方案一：极简轻量

## 设计理念
最小改动，最快实现。只做一个最简单的全屏地图，纯色渲染区块网格，WASD平移+滚轮缩放+左键框选购买+右键框选放弃。

## 新增文件（3个）

### 1. `ChunkClaimScreen.java`
- 路径: `client/screen/ChunkClaimScreen.java`
- 继承 `Screen`，实现全屏地图
- **渲染**:
  - 深灰背景 (`0xFF1A1A2E`)
  - 遍历可见区块范围，每个区块渲染16×16像素纯色方块
  - 颜色: 蓝色=我的(`0x600000FF`)、红色=他人(`0x60FF0000`)、绿色=空(`0x2000FF00`)、白色=选中(`0x80FFFFFF`)
  - 区块边框: 1px 深灰线 (`0xFF333355`)
  - 玩家位置: 白色小圆点/十字
  - 顶部 HUD: 中心区块坐标 + 已选区块数
- **交互**:
  - WASD 平移（每帧 8px）
  - 滚轮缩放（blockSize 在 4~32px 之间，步长 2px）
  - 左键按下 → 开始框选购买 → 拖拽 → 释放 → 确认选区
  - 右键按下 → 开始框选放弃 → 拖拽 → 释放 → 确认选区
  - 回车 → 发送确认包给服务端
  - Esc → 关闭
- **数据流**: 打开时发送 `PacketC2SRequestPlotData`，收到 `PacketS2CPlotChunkData` 后直接存入 `Map<Long, Cell>` 本地字段

### 2. `PacketC2SChunkClaimAction.java`
- 路径: `network/PacketC2SChunkClaimAction.java`
- 字段: `Action action (CLAIM/UNCLAIM)`, `List<Long> chunkKeys`, `String dimensionId`
- 服务端 handle: 调用 EconomySavedData 的现有方法完成认领/放弃

### 3. `PacketS2CChunkClaimResult.java`
- 路径: `network/PacketS2CChunkClaimResult.java`
- 字段: `boolean success`, `String message`, `List<Long> affectedChunks`
- 客户端 handle: 显示提示消息，清除本地缓存中受影响区块，重新请求数据

## 修改文件

### `ModConfig.java` — 新增2个配置项
```java
public final ForgeConfigSpec.DoubleValue chunkClaimCost;
public final ForgeConfigSpec.DoubleValue chunkUnclaimRefund;
```
在 `plot` section 下定义（复用 section 名保持兼容）

### `ModMessages.java` — 注册2个新包
- `PacketC2SChunkClaimAction`
- `PacketS2CChunkClaimResult`

### `ClientPacketReceivers.java` — 修改
- `onPlotChunkData()` → 改为 `onChunkData()`：直接传给 `ChunkClaimScreen` 实例
- 新增 `onChunkClaimResult()`：处理操作结果
- `onOpenScreen()` → PLOT_MAP 改为打开 `ChunkClaimScreen`

### `RegionCommandHandler.java` — 修改
- 删除 `openMap()` 中 `playerPlotMode` 检查，直接发送 `PacketS2COpenScreen(PLOT_MAP)`
- 删除 `setPlotMode()` 整个方法

### `ModCommands.java` — 保留 `/land map`，删除 `/land mode`

### `ClientModEvents.java` — 无需特殊初始化

### `RegionEventListener.java` — 新增 `forceExitClaimMap()` 方法

---

# 方案二：标准均衡

## 设计理念
在方案一基础上增加客户端缓存层、独立服务端逻辑类、更丰富的交互体验。目标是达到 FTB Chunks 65% 的功能还原度。

## 新增文件（6个）

### 包含方案一全部3个文件 + 额外3个:

### 4. `ChunkClaimCache.java`
- 路径: `client/screen/ChunkClaimCache.java`
- 功能: 客户端区块归属缓存
- 存储: `ConcurrentHashMap<Long, CacheEntry>` (key=chunkKey, value=owner+regionName+color)
- 方法: `put()`, `get()`, `invalidate(List<Long>)`, `clear()`, `getOwner()`, `isMine(UUID)`, `isClaimed()`
- 缓存颜色预计算: 存储时直接算好 ARGB 颜色值，渲染时直接取用

### 5. `ChunkClaimService.java`
- 路径: `claim/ChunkClaimService.java`（新包）
- 功能: 服务端区块认领/放弃逻辑
- 方法:
  - `claim(ServerPlayer, List<Long>, String dim)` — 认领新区块
    - 资金校验（使用 `playerPersonalFunds`）
    - 上限校验（`chunkMaxPerPlayer`）
    - 冲突校验（区块是否已被他人认领）
    - 距离定价（`calculateExpandCost`）
    - 写入 `RegionData.claimedChunks`
  - `unclaim(ServerPlayer, List<Long>, String dim)` — 放弃区块
    - 所有权校验
    - 退款计算
    - 从 `RegionData.claimedChunks` 移除
  - `calculateExpandCost(RegionData, List<Long>)` — 距离定价
    - 每个新区块到原区域所有区块的最近 Chebyshev 距离
    - 价格 = 基础价格 × (1 + 距离系数 × 最近距离)

### 6. `PacketS2CChunkData.java`（新增简化版）
- 路径: `network/PacketS2CChunkData.java`
- 比 `PacketS2CPlotChunkData` 更精简，去掉 `isFlyland` 等无用字段
- 字段: `List<ChunkEntry> entries, int minCX, int minCZ, int maxCX, int maxCZ`
- `ChunkEntry(long chunkKey, UUID owner, String regionName, int colorARGB)`

## 修改文件（相比方案一更多）

### `ModConfig.java` — 新增4个配置项
```java
public final ForgeConfigSpec.DoubleValue chunkClaimCost;       // 每区块购买费用（默认 100.0）
public final ForgeConfigSpec.DoubleValue chunkUnclaimRefund;    // 放弃每区块返还（默认 50.0）
public final ForgeConfigSpec.IntValue    chunkMaxPerPlayer;     // 最大区块数（-1=不限）
public final ForgeConfigSpec.DoubleValue chunkExpandDistanceMultiplier; // 距离系数（默认 0.05）
```

### `ModMessages.java` — 注册4个网络包
- 保留 `PacketC2SRequestPlotData`（复用）
- 保留 `PacketS2CPlotChunkData`（复用）
- 新增 `PacketC2SChunkClaimAction`
- 新增 `PacketS2CChunkClaimResult`

### `ClientPacketReceivers.java` — 修改
- `onPlotChunkData()` → 改为写入 `ChunkClaimCache` 并通知 `ChunkClaimScreen`
- 新增 `onChunkClaimResult()`
- `onOpenScreen(PLOT_MAP)` → 打开 `ChunkClaimScreen`

### `RegionCommandHandler.java` — 修改
- `openMap()` 简化，去掉 mode 检查，直接发包
- 保留 `inPlotMode` 追踪用于强制退出

### `EconomySavedData.java` — 保留
- `snapshotPlotCells()` 和 `PlotCell` 保留不动
- 删除 `playerPlotMode` 和 `playersInPlotMode`

---

# 方案三：完整特性

## 设计理念
高度还原 FTB Chunks 的核心体验，包括路标系统、队友视图、强制加载、左侧面板、性能优化等。目标是达到 FTB Chunks 90% 的功能还原度。

## 新增文件（10个）

### 包含方案二全部6个文件 + 额外4个:

### 7. `WaypointData.java`
- 路径: `client/screen/WaypointData.java`
- 功能: 路标数据模型
- 字段: `String name`, `int blockX`, `int blockZ`, `int color`, `String dimension`, `boolean isDeathPoint`, `long timestamp`

### 8. `WaypointManager.java`
- 路径: `client/screen/WaypointManager.java`
- 功能: 客户端路标管理器（仅本地存储，不网络同步）
- 方法: `add()`, `remove()`, `edit()`, `getAll()`, `getInDimension(String)`, `save()`, `load()`
- 存储: 本地 JSON 文件 `land_economy/waypoints.json`

### 9. `ChunkClaimScreen.java`（完整版，比方案二更丰富）
- 路径: `client/screen/ChunkClaimScreen.java`
- **渲染增强**:
  - 区块网格 + 可选地形纹理（通过 `ChunkRenderTask` 异步加载地形颜色）
  - 路标渲染: 彩色小旗标 + 名称标签
  - 死亡点渲染: 骷髅图标
  - 玩家指示: 方向箭头 + 名称标签
  - 实体图标: 其他玩家在地图上显示为彩色圆点
  - 强制加载区块: 黄色边框叠加
- **左侧按钮面板**（FTB Chunks 风格）:
  - "认领区块" 按钮 → 进入认领模式
  - "设置" 按钮 → 打开简单设置界面
  - "维度" 按钮 → 切换维度（如果有多个维度数据）
- **交互增强**:
  - 左键点击区块 → 认领（在认领模式下）
  - 右键点击区块 → 放弃（在认领模式下）
  - Shift+左键已认领区块 → 切换强制加载
  - 右键空白区域 → 创建路标
  - 右键路标/死亡点 → 编辑或删除
  - 中键拖拽 → 平移（替代方案）
  - 鼠标滚轮缩放 → 平滑缩放 + 缩放记忆
  - 双击 → 传送到该位置（如果 OP 或有权限）
- **HUD 增强**:
  - 左上: 维度名称 + 中心坐标
  - 右上: 缩放级别 (如 "2x")
  - 底部: 选中区块数 + 操作提示
  - 悬停提示: 鼠标悬停区块时显示归属信息

### 10. `ChunkClaimSettingsScreen.java`
- 路径: `client/screen/ChunkClaimSettingsScreen.java`
- 功能: 地图设置界面
- 选项: 显示网格、显示路标、显示死亡点、显示实体、颜色方案

### 配套修改

**ModConfig.java** — 方案二配置 + 额外:
```java
public final ForgeConfigSpec.BooleanValue chunkClaimForceLoadEnabled;  // 是否启用强制加载
public final ForgeConfigSpec.IntValue     chunkClaimForceLoadMax;       // 最大强制加载数
public final ForgeConfigSpec.BooleanValue chunkClaimShowTerrain;        // 是否显示地形纹理
```

**ModMessages.java** — 方案二网络包 + 额外:
- `PacketC2SToggleForceLoad` — 切换强制加载
- 或复用 `PacketC2SChunkClaimAction` 增加 `Action.TOGGLE_FORCELOAD` 枚举值

**ClientModEvents.java** — 新增:
- 注册 `WaypointManager` 初始化
- 注册 `ChunkClaimScreen` 静态配置加载

---

## 方案选择建议

| 场景 | 推荐方案 |
|------|----------|
| 快速上线，功能够用就行 | **方案一** |
| 需要较好的用户体验，但时间有限 | **方案二** |
| 追求完整 FTB Chunks 体验，愿意投入更多时间 | **方案三** |

---

## 通用验证步骤

1. 运行 `./gradlew build --no-daemon` 确保编译通过
2. 进入游戏测试 `/land map` 命令打开地图
3. 测试 WASD 平移、滚轮缩放
4. 测试左键框选购买区块
5. 测试右键框选放弃区块
6. 测试回车确认操作
7. 测试 Esc 关闭地图
8. 验证受击/传送时自动关闭地图
9. 验证 `claimedChunks` 数据正确持久化