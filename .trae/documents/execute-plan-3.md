# 执行方案3：完整FTB Chunks风格区块认领地图

---

## 一、当前状态分析

### 1.1 已完成的清理工作

| 类别 | 状态 | 说明 |
|------|:---:|------|
| `client/plot/` 目录 | 已清空 | 4个旧文件已删除 |
| `client/integration/` 目录 | 已清空 | 8个旧文件已删除 |
| `plot/` 目录 | 已清空 | 2个旧文件已删除 |
| 旧plot网络包 | 已删除 | 4个plot专用包已不注册 |
| ModConfig plot配置 | 已删除 | 旧plot配置项已移除 |
| RegionEventListener | 已清理 | 无plot相关代码 |

### 1.2 已创建的新文件

| 文件 | 路径 | 状态 |
|------|------|:---:|
| ChunkClaimScreen.java | `client/screen/` | 已完成 |
| ChunkClaimSettingsScreen.java | `client/screen/` | 已完成 |
| ChunkClaimCache.java | `client/screen/` | 已完成 |
| WaypointData.java | `client/screen/` | 已完成 |
| WaypointManager.java | `client/screen/` | 已完成 |
| ChunkClaimService.java | `claim/` | 已完成 |
| PacketC2SChunkClaimAction.java | `network/` | 已完成 |
| PacketS2CChunkClaimResult.java | `network/` | 已完成 |

### 1.3 已修改的现有文件

| 文件 | 修改内容 |
|------|----------|
| ModConfig.java | 新增 `chunk_claim` 配置段（6个配置项） |
| ModMessages.java | 注册了 `PacketC2SChunkClaimAction` 和 `PacketS2CChunkClaimResult` |
| ClientPacketReceivers.java | 新增 `onChunkClaimResult`，修改 `onPlotChunkData` 写入 ChunkClaimCache |
| RegionEventListener.java | 移除 plot 强制退出逻辑 |
| RegionCommandHandler.java | `openMap()` 简化为直接发送 PLOT_MAP 类型 |

### 1.4 保留的旧网络包（复用）

| 文件 | 用途 |
|------|------|
| PacketC2SRequestPlotData.java | 客户端请求区块数据 |
| PacketS2CPlotChunkData.java | 服务端下发区块归属快照 |
| PacketS2COpenScreen.java | 打开屏幕（含 PLOT_MAP 类型） |

---

## 二、需要修复的问题

### 问题1：`PacketC2SRequestPlotData` 引用了不存在的配置项

**文件**: `network/PacketC2SRequestPlotData.java` 第40行
```java
int r = ModConfig.COMMON.plotMapViewRadius.get();  // 该配置项已从 ModConfig 中删除
```

**修复**: 改为硬编码的请求半径常量（如 `16`），或新增 `chunkClaimViewRadius` 配置项。

### 问题2：`PacketS2COpenScreen.Type.PLOT_MAP` 命名不准确

**文件**: `network/PacketS2COpenScreen.java`
- `PLOT_MAP` 枚举值名称暗示旧"地块地图"系统，应重命名为 `CHUNK_CLAIM_MAP` 或保留但仅内部使用。

**修复**: 重命名枚举值为 `CHUNK_CLAIM_MAP`，同步更新所有引用处。

### 问题3：`snapshotPlotCells` 和 `PlotCell` 命名遗留

**文件**: `data/EconomySavedData.java`
- `PlotCell` record 和 `snapshotPlotCells()` 方法名含 "plot"，应重命名为 `ChunkCell` 和 `snapshotChunkCells()`。

**修复**: 重命名并更新所有引用处。

### 问题4：`ChunkClaimScreen` 中 `minecraft` 字段使用不一致

**文件**: `client/screen/ChunkClaimScreen.java`
- 使用了 `minecraft`（来自 `Screen` 父类）和 `Minecraft.getInstance()` 混用，应统一使用 `minecraft`。

**修复**: 统一使用 `minecraft` 字段。

---

## 三、剩余工作

### 3.1 修复编译问题（必须）

| 步骤 | 文件 | 操作 |
|:---|------|------|
| 1 | `PacketC2SRequestPlotData.java` | 修改 `plotMapViewRadius` 为固定值 `16` 或新增配置 |
| 2 | `PacketS2COpenScreen.java` | 重命名 `PLOT_MAP` → `CHUNK_CLAIM_MAP` |
| 3 | `EconomySavedData.java` | 重命名 `PlotCell` → `ChunkCell`，`snapshotPlotCells` → `snapshotChunkCells` |
| 4 | `PacketS2CPlotChunkData.java` | 更新引用（重命名后） |
| 5 | `ClientPacketReceivers.java` | 更新引用（重命名后） |
| 6 | `RegionCommandHandler.java` | 更新 `PLOT_MAP` → `CHUNK_CLAIM_MAP` |

### 3.2 可选增强（非必须）

| 步骤 | 文件 | 操作 |
|:---|------|------|
| 7 | `ChunkClaimScreen.java` | 统一 `minecraft` 引用 |
| 8 | `ClientModEvents.java` | 添加 `WaypointManager.load()` 初始化调用 |
| 9 | `RegionEventListener.java` | 添加 `forceExitClaimMap()` 方法（受击/传送时关闭地图） |

### 3.3 构建验证（必须）

| 步骤 | 操作 |
|:---|------|
| 10 | 运行 `./gradlew build --no-daemon` 确保编译通过 |
| 11 | 检查产物 `build/libs/land_economy_mod-*.jar` |

---

## 四、文件变更汇总

### 需要修改的文件（6个）

| 文件 | 变更类型 |
|------|----------|
| `network/PacketC2SRequestPlotData.java` | 修复配置引用 |
| `network/PacketS2COpenScreen.java` | 重命名枚举值 |
| `data/EconomySavedData.java` | 重命名 record + 方法 |
| `network/PacketS2CPlotChunkData.java` | 更新引用 |
| `client/ClientPacketReceivers.java` | 更新引用 |
| `command/RegionCommandHandler.java` | 更新枚举引用 |

### 可选修改的文件（3个）

| 文件 | 变更类型 |
|------|----------|
| `client/screen/ChunkClaimScreen.java` | 统一引用 |
| `client/ClientModEvents.java` | 添加初始化 |
| `RegionEventListener.java` | 添加关屏逻辑 |

---

## 五、验证步骤

1. 运行 `./gradlew build --no-daemon` 确保编译通过（零错误）
2. 检查产物 `build/libs/land_economy_mod-*.jar` 存在
3. 可选：运行 `./gradlew runClient` 进入游戏测试 `/land map` 命令