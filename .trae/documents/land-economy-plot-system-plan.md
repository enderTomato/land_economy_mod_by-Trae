# 领地经济模组 实施计划：区域飞行 / 地图地块系统 / 区域冻结 / 箱子GUI / 新旧兼容

> 目标仓库：`https://github.com/enderTomato/land_economy_mod_by-Trae`（已克隆确认，本地工作副本位于 `/workspace`）
> Minecraft 1.20.1 · Forge 47.1.3 · Java 17 · mod_id=`land_economy_mod_1783600667` · 包根 `cn.autoforged.land_economy_mod_1783600667`

---

## 0. 用户确认的决策（已锁定）

1. **地图地块视图方案 = B**：全屏 2D 俯视“地块地图”`Screen`（WASD 平移、滚轮缩放、绘制区块网格与四色高亮框、左/右键单击与框选购买/放弃、回车确认）。客户端不实现 3D 脱离式摄像机、不实现区块方块级数据流。
   - “视角移动到未渲染区块时请求服务器加载并发送该区块数据” → **重定义为**：客户端平移地图到新区域时，通过自定义网络包向服务器请求该区块范围内的**地块所有权数据**（非方块级 chunk data），服务器即使玩家实体未移动也响应并下发。该数据用于在 2D 地图上绘制地块色块与四色高亮框。
2. **数据模型 = 扩展 `RegionData` 加 chunk 集合**：每玩家一个母区域（沿用现有 owner/members/permissions/gdp/bank/economy），新增 `Set<Long> claimedChunks`。旧 `/land claim` 的 AABB 等价于一次性写入该范围全部 chunk。
3. **额外需求**：在地块界面点击自己/他人的已购买区块 → 弹出**区域详情面板**（含 GDP、区域玩家、留言板、区域银行存款）。

---

## 1. 源码理解摘要（已完成阅读）

### 项目类型与构建
- Forge 1.20.1 模组，构建系统为 **ModDevGradle Legacy**（`net.neoforged.moddev.legacyforge`）；`build.gradle` 使用 parchment 1.20.1:2023.09.03 映射，Java 17。`mods.toml` `side="BOTH"`（默认），未启用 Mixin。

### 模块划分（`src/main/java/cn/autoforged/land_economy_mod_1783600667/`）
- `LandEconomyMod` — `@Mod` 主类；`commonSetup`（`enqueueWork`）；`onServerStarting/Stopping` 启动 GDP/Population 引擎；静态 `getEconomyData()`。
- `ModConfig` — `ForgeConfigSpec`（COMMON）。已有 `claimOutlayNew`/`claimOutlayExpand`/`flylandMaxWidth`/`flylandMaxLength`/`regionDisplayMode` 等。
- `data/RegionData` — 领地数据模型（下详）。
- `data/EconomySavedData` — `extends SavedData`，挂在 `server.overworld().getDataStorage()`（**服务器权威、存于服务器主机**），多人/服务器数据持久化在此。✅
- `data/RegionType` / `data/IndustryClassification`。
- `command/ModCommands` — 注册 `/land`、`/economy`、`/math`、`/value`。`/land` 子树含 `claim`/`add`/`unclaim`/`info`/`list`/`permissions`/`invite`/`kick`/`leave`/`bank`/`join`/`display`/`data`/`setOutlay`/`flyland`。
- `command/RegionCommandHandler` — 所有 `/land` 处理逻辑（69KB，1467 行）。
- `command/EconomyCommandHandler` — `/economy` 等处理。
- `economy/GDPEngine` / `economy/PopulationEngine` — 定时 GDP/人口计算。
- `RegionEventListener` — `@Mod.EventBusSubscriber`，服务端事件取消（爆炸、生成、PVP、容器、红石、末影珍珠、方块放置/破坏）。
- `RegionTitleHandler` — 服务端 `PlayerTickEvent` 检测区域变化 → 下发 title/actionbar 包。
- `api/LandEconomyAPI` — 对外 API。

### 关键数据模型 `RegionData`
- 权限：`TOTAL_PERMISSIONS=11`，`boolean[] permissions`，`PERMISSION_NAMES={explode, undead_spawn, phantom_spawn, friendly_fire, pvp, explosion_block_damage, container_access, redstone_interact, ender_pearl, fire_spread, block_place_break}`。NBT 存为 `byte[11]`。
- 约定：`permission[i]=true` → 该受限行为**被允许**（多数作用于非成员，成员经 `isMember()` 直接放行；见 `onBlockBreak: !isMember && !perm(10) → cancel`）。
- `isFlyland` 是“飞地（exclave）”区域分类（经济层级概念），**与飞行无关**。
- 区域几何：`minX/minZ/maxX/maxZ`（AABB，y 无限）+ `dimensionId` + `center`。`containsPos(pos)` 按 AABB 判定。
- `getPermissionIndex(name)` / `getPermissionName(i)` / `setPermission(i,bool)`。
- `overlapsWith(other)`、`isMember`、`members`、`parentRegionId`/`childRegionIds`、`bankDeposits`、`gdp`、`population`。

### 权限指令（已确认自动适配机制）
- `/land permissions set <name> <bool>` 调 `RegionData.getPermissionIndex` + `setPermission`。
- `/land permissions`（`listPermissions`）与 `/land ?`（`help`）遍历 `TOTAL_PERMISSIONS` 并调 `getPermissionChinese(name)`。
- ⇒ **新增权限只需**：`TOTAL_PERMISSIONS+1`、追加 `PERMISSION_NAMES`、`getPermissionChinese` 加 case。命令/列表/帮助自动包含新权限。

### 现有领地指令
- `claimLandWithPos`（`/land claim pos1..pos2 [name]`）：校验每玩家仅一个母区域（`getRegionByOwner`）、最小 2×2、扣 `claimOutlayNew`、与 root 区域 `overlapsWith` 校验、`createRegion`。
- `expandRegion`（`/land add pos1..pos2`）：扩大 AABB、扣 `claimOutlayExpand`、overlap 校验。
- `unclaimLandBlock/Child/Flyland`：放弃母区域/子区域/飞地。
- `claimChildRegion`、`claimFlyland`、`flylandInfo`、`landInfo`、`setPermission`、`findRegionAtPlayer` 等。

### 现状缺口（本计划需从零搭建）
- ❌ 无客户端包（client/） ❌ 无网络包（network/） ❌ 无 GUI（`MenuType`/`Screen`） ❌ 无渲染 ❌ 无飞行逻辑 ❌ 无“冻结”逻辑 ❌ 无留言板。

✅ **确认**：本模组适配多人/服务器游玩；多人或处于服务器时，所有领地/经济数据保存在 `EconomySavedData`（挂于服务器 overworld）即**服务器主机**上，客户端仅发送请求与展示。

---

## 2. 总体架构与新增/修改文件清单

### 新增包与文件（包根 `cn.autoforged.land_economy_mod_1783600667`）

```
network/
  ModMessages.java                  # SimpleChannel 注册 + 包 id 分配
  PacketC2SOpenPlotMap.java         # 进入地块视图
  PacketC2SRequestPlotData.java     # 请求某区块范围的地块所有权
  PacketS2CPlotChunkData.java       # 下发地块所有权网格
  PacketC2SPlotAction.java          # 购买/放弃（含批量）
  PacketS2CPlotActionResult.java    # 操作结果 + 新余额
  PacketC2SRequestRegionDetail.java # 请求区域详情
  PacketS2CRegionDetail.java        # 下发详情(GDP/玩家/留言/银行)
  PacketC2SPostMessage.java         # 发布留言
  PacketS2COpenScreen.java          # 服务端→客户端 打开某 Screen（地块图/箱子GUI/详情）
  PacketS2CForceExitPlot.java       # 强制退出地块界面
plot/
  PlotService.java                  # 服务端地块购买/放弃/校验核心
client/
  ClientPacketReceivers.java        # 客户端包接收分发(DistExecutor)
  ClientModEvents.java              # @Mod.EventBusSubscriber(Dist.CLIENT)：键位、打开 GUI
  client/plot/PlotMapView.java      # 视图状态(中心/缩放/选区) + 渲染计算
  client/plot/PlotMapScreen.java    # 全屏 2D 俯视地块地图 Screen
  client/plot/PlotClientCache.java  # 客户端地块所有权缓存
  client/gui/RegionDetailScreen.java# 区域详情面板
  client/gui/LandChestScreen.java   # 箱子GUI 启动器
  client/gui/ModScreens.java        # Screen 工厂入口（避免类加载污染）
```

### 修改文件
| 文件 | 改动要点 |
|---|---|
| `data/RegionData.java` | 权限数组 11→13（+`region_fly`,`block_update`）；新增 `claimedChunks`、留言板 `messages`；`containsPos` 优先查 chunk 集合；NBT 增/删字段；`recomputeAABBFromChunks` |
| `data/EconomySavedData.java` | `getRegionOwningChunk(dim,chunk)`；玩家地块模式 `playerPlotMode`（new/old）+ NBT；`migrateLegacyAABBToChunks`；强制退出状态 `playersInPlotMode` |
| `ModConfig.java` | 新增 `plot` 配置段（默认模式/每块费用/上限/视图半径/留言上限/旧指令启用） |
| `LandEconomyMod.java` | `commonSetup` 内 `enqueueWork(ModMessages::register)` |
| `command/ModCommands.java` | 新增 `/land map`、`/land mode <new\|old>`、`/land gui`、`/land message <区域> <文本>` |
| `command/RegionCommandHandler.java` | `getPermissionChinese` 加两 case；`openMap`/`setMode`/`postMessage`；旧指令在 new 模式下提示 |
| `RegionEventListener.java` | “区域冻结”（`block_update` 权限）事件取消；强制退出地块界面（受击/传送/位移） |
| 新增 `FlightPermissionHandler.java` | 服务端 `region_fly` 权限 → `mayfly`/`flying` 授予与撤销 |
| `resources/assets/.../lang/zh_cn.json` | 新增权限/命令/GUI 文案 key |

---

## 3. 数据层修改

### 3.1 `data/RegionData.java`（关键补丁）

```java
package cn.autoforged.land_economy_mod_1783600667.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class RegionData {

    // 11 -> 13：新增 region_fly(11)、block_update(12)
    public static final int TOTAL_PERMISSIONS = 13;

    private static final String[] PERMISSION_NAMES = {
            "explode", "undead_spawn", "phantom_spawn", "friendly_fire", "pvp",
            "explosion_block_damage", "container_access", "redstone_interact", "ender_pearl",
            "fire_spread", "block_place_break",
            "region_fly",   // 11：true=允许区域内成员飞行
            "block_update"  // 12：true=允许方块更新(默认)；false=区域冻结(禁止红石/流体/作物/活塞/爆炸/非房主放置破坏)
    };

    // ... 原有字段 ...

    // —— 地块系统：已购买区块集合（chunkKey = ChunkPos.asLong(x,z)）——
    private Set<Long> claimedChunks = new HashSet<>();
    // —— 留言板 ——
    private final List<MessageEntry> messages = new ArrayList<>();

    public static class MessageEntry {
        public final UUID author;
        public final String authorName;
        public final String text;
        public final long time;
        public MessageEntry(UUID author, String authorName, String text, long time) {
            this.author = author; this.authorName = authorName; this.text = text; this.time = time;
        }
        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Author", author);
            t.putString("AuthorName", authorName);
            t.putString("Text", text);
            t.putLong("Time", time);
            return t;
        }
        public static MessageEntry fromNbt(CompoundTag t) {
            return new MessageEntry(t.getUUID("Author"), t.getString("AuthorName"),
                    t.getString("Text"), t.getLong("Time"));
        }
    }

    // ====== 区块集合 API ======
    public static long chunkKey(BlockPos pos) { return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4); }
    public static long chunkKey(int cx, int cz) { return ChunkPos.asLong(cx, cz); }

    public Set<Long> getClaimedChunks() { return claimedChunks; }

    /** 新模式是否已启用（有任意已购买区块） */
    public boolean hasPlots() { return !claimedChunks.isEmpty(); }

    public boolean ownsChunk(long key) { return claimedChunks.contains(key); }
    public boolean ownsChunk(BlockPos pos) { return claimedChunks.contains(chunkKey(pos)); }
    public boolean addChunk(long key) { setDirty(); return claimedChunks.add(key); }
    public boolean removeChunk(long key) { setDirty(); return claimedChunks.remove(key); }

    /** 由 AABB 一次性写入其覆盖的全部区块（旧→新迁移/旧指令兼容） */
    public void addAllChunksInAABB() {
        if (minX == Integer.MAX_VALUE) return;
        int cx0 = minX >> 4, cx1 = maxX >> 4, cz0 = minZ >> 4, cz1 = maxZ >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++)
                claimedChunks.add(chunkKey(cx, cz));
        setDirty();
    }

    /** 由当前 chunk 集合反算 AABB（新→旧显示兼容） */
    public void recomputeAABBFromChunks() {
        if (claimedChunks.isEmpty()) return;
        int mnX = Integer.MAX_VALUE, mnZ = Integer.MAX_VALUE, mxX = Integer.MIN_VALUE, mxZ = Integer.MIN_VALUE;
        for (long k : claimedChunks) {
            int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
            mnX = Math.min(mnX, cx << 4); mnZ = Math.min(mnZ, cz << 4);
            mxX = Math.max(mxX, (cx << 4) + 15); mxZ = Math.max(mxZ, (cz << 4) + 15);
        }
        this.minX = mnX; this.minZ = mnZ; this.maxX = mxX; this.maxZ = mxZ;
        if (center == null) center = new BlockPos((mnX + mxX) / 2, 64, (mnZ + mxZ) / 2);
        setDirty();
    }

    // ====== containsPos：优先 chunk 集合，回退 AABB（旧数据） ======
    public boolean containsPos(BlockPos pos) {
        if (!claimedChunks.isEmpty()) return ownsChunk(pos);
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public int getAreaSize() {
        if (!claimedChunks.isEmpty()) return claimedChunks.size() * 256;
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    // ====== 留言板 ======
    public List<MessageEntry> getMessages() { return Collections.unmodifiableList(messages); }
    public void addMessage(UUID author, String authorName, String text, int max) {
        messages.add(new MessageEntry(author, authorName, text, System.currentTimeMillis()));
        while (messages.size() > max) messages.remove(0);
        setDirty();
    }

    private void setDirty() { /* 标记外部 SavedData 脏：由 EconomySavedData.setDirty() 兜底 */
        EconomySavedData data = cn.autoforged.land_economy_mod_1783600667.LandEconomyMod.getEconomyData();
        if (data != null) data.setDirty();
    }

    // ====== NBT ======
    public static RegionData fromNbt(CompoundTag tag) {
        RegionData data = new RegionData();
        // ... 原有读取 ...
        data.regionId = tag.getUUID("RegionId");
        data.name = tag.getString("Name");
        if (tag.hasUUID("Owner")) data.owner = tag.getUUID("Owner");
        if (tag.contains("Center")) data.center = NbtUtils.readBlockPos(tag.getCompound("Center"));
        data.minX = tag.getInt("MinX"); data.minZ = tag.getInt("MinZ");
        data.maxX = tag.getInt("MaxX"); data.maxZ = tag.getInt("MaxZ");
        data.dimensionId = tag.getString("DimensionId");
        data.gdp = tag.getDouble("GDP"); data.population = tag.getInt("Population");
        data.lastGdpCalcTime = tag.getLong("LastGDPCalcTime");
        data.lastPopulationCalcTime = tag.getLong("LastPopCalcTime");
        data.consecutiveGrowthChecks = tag.getInt("ConsecutiveGrowthChecks");
        data.bankDeposits = tag.getDouble("BankDeposits");
        data.personalFunds = tag.getDouble("PersonalFunds");
        if (tag.contains("Permissions")) {
            byte[] permBytes = tag.getByteArray("Permissions");
            for (int i = 0; i < Math.min(permBytes.length, TOTAL_PERMISSIONS); i++)
                data.permissions[i] = permBytes[i] == 1;
        }
        // members / parent / children / isFlyland / pendingJoinRequests（保持原样，略）...
        data.isFlyland = tag.getBoolean("IsFlyland");

        // —— 新增：chunk 集合 ——
        if (tag.contains("ClaimedChunks")) {
            long[] arr = tag.getLongArray("ClaimedChunks");
            for (long k : arr) data.claimedChunks.add(k);
        }
        // —— 新增：留言板 ——
        if (tag.contains("Messages")) {
            ListTag ml = tag.getList("Messages", Tag.TAG_COMPOUND);
            for (int i = 0; i < ml.size(); i++) data.messages.add(MessageEntry.fromNbt(ml.getCompound(i)));
        }
        return data;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        // ... 原有写入（与原实现一致）...
        tag.putUUID("RegionId", regionId);
        tag.putString("Name", name);
        if (owner != null) tag.putUUID("Owner", owner);
        if (center != null) tag.put("Center", NbtUtils.writeBlockPos(center));
        tag.putInt("MinX", minX); tag.putInt("MinZ", minZ);
        tag.putInt("MaxX", maxX); tag.putInt("MaxZ", maxZ);
        tag.putString("DimensionId", dimensionId);
        tag.putDouble("GDP", gdp); tag.putInt("Population", population);
        tag.putLong("LastGDPCalcTime", lastGdpCalcTime);
        tag.putLong("LastPopCalcTime", lastPopulationCalcTime);
        tag.putInt("ConsecutiveGrowthChecks", consecutiveGrowthChecks);
        tag.putDouble("BankDeposits", bankDeposits);
        tag.putDouble("PersonalFunds", personalFunds);
        byte[] permBytes = new byte[TOTAL_PERMISSIONS];
        for (int i = 0; i < TOTAL_PERMISSIONS; i++) permBytes[i] = (byte) (permissions[i] ? 1 : 0);
        tag.putByteArray("Permissions", permBytes);
        // members / parent / children / isFlyland / pendingJoinRequests（保持原样，略）...
        tag.putBoolean("IsFlyland", isFlyland);

        // —— 新增 ——
        tag.putLongArray("ClaimedChunks", claimedChunks.stream().mapToLong(Long::longValue).toArray());
        ListTag ml = new ListTag();
        for (MessageEntry m : messages) ml.add(m.toNbt());
        tag.put("Messages", ml);
        return tag;
    }

    // 权限命名/索引沿用原实现，无需改逻辑（自动覆盖新索引）
    public static String getPermissionName(int index) { /* 原样 */ return index >= 0 && index < PERMISSION_NAMES.length ? PERMISSION_NAMES[index] : "unknown_" + index; }
    public static int getPermissionIndex(String name) { /* 原样 */ for (int i = 0; i < PERMISSION_NAMES.length; i++) if (PERMISSION_NAMES[i].equalsIgnoreCase(name)) return i; return -1; }
    // getPermission / setPermission / 其余 getter/setter 原样
}
```

> 注意：`RegionData` 是 POJO（非 `SavedData`），原本不自带 `setDirty`。上面的 `setDirty()` 私有辅助通过 `LandEconomyMod.getEconomyData().setDirty()` 兜底标记脏；调用点（`EconomySavedData` 内）已统一 `setDirty()`，二者一致。

### 3.2 `data/EconomySavedData.java`（新增补丁）

```java
// —— 新增字段 ——
private final Map<UUID, String> playerPlotMode = new ConcurrentHashMap<>();   // "new"/"old"
private final Set<UUID> playersInPlotMode = ConcurrentHashMap.newKeySet();     // 服务端追踪地块界面在线玩家

// —— 区块归属查询 ——
public RegionData getRegionOwningChunk(String dimId, long chunkKey) {
    for (RegionData r : regions.values()) {
        if (r.getDimensionId() != null && r.getDimensionId().equals(dimId) && r.ownsChunk(chunkKey))
            return r;
    }
    return null;
}

/** 返回某区块范围内（cx0..cx1, cz0..cz1）的地块归属快照，供客户端渲染四色高亮 */
public record PlotCell(long chunkKey, UUID owner, String regionName, boolean isFlyland) {}
public List<PlotCell> snapshotPlotCells(String dimId, int cx0, int cz0, int cx1, int cz1) {
    List<PlotCell> out = new ArrayList<>();
    for (int cx = cx0; cx <= cx1; cx++)
        for (int cz = cz0; cz <= cz1; cz++) {
            long key = RegionData.chunkKey(cx, cz);
            RegionData r = getRegionOwningChunk(dimId, key);
            if (r != null) out.add(new PlotCell(key, r.getOwner(), r.getName(), r.isFlyland()));
        }
    return out;
}

// —— 玩家地块模式 ——
public String getPlayerPlotMode(UUID id) {
    String m = playerPlotMode.get(id);
    return m != null ? m : (ModConfig.COMMON.plotSystemEnabled.get() ? "new" : "old");
}
public void setPlayerPlotMode(UUID id, String mode) {
    if (!"new".equals(mode) && !"old".equals(mode)) return;
    playerPlotMode.put(id, mode); setDirty();
}

// —— 地块界面在线状态（服务端强制退出用） ——
public boolean isInPlotMode(UUID id) { return playersInPlotMode.contains(id); }
public void setInPlotMode(UUID id, boolean v) { if (v) playersInPlotMode.add(id); else playersInPlotMode.remove(id); }

// —— 旧→新 迁移：对单个 region 把 AABB 转为 chunk 集合（幂等） ——
public void migrateLegacyAABBToChunks(RegionData r) {
    if (r.hasPlots()) return;            // 已是 chunk 模式
    if (r.getMinX() == 0 && r.getMaxX() == 0 && r.getMinZ() == 0 && r.getMaxZ() == 0) return; // 空区域
    r.addAllChunksInAABB();
    setDirty();
}
```

在 `EconomySavedData(CompoundTag)` 与 `save()` 中追加 `PlayerPlotMode`（ListTag of {Player,Mode}）的读写（仿 `PlayerDisplayModes`）。`playersInPlotMode` 不持久化（运行期状态，重启自动清空）。

> `getRegionByOwner` 仍返回玩家的母区域；`PlotService` 在母区域上增删 chunk。若无母区域而玩家用 `/land map` 购买 → `PlotService` 自动创建一个“地块型母区域”（`claimedChunks` 非空、AABB 由 chunk 反算）。

---

## 4. 配置层 `ModConfig.java`（新增 `plot` 段）

```java
// 字段
public final ForgeConfigSpec.BooleanValue plotSystemEnabled;     // 全局默认地块系统
public final ForgeConfigSpec.DoubleValue plotCostPerChunk;       // 每区块购买费用
public final ForgeConfigSpec.DoubleValue plotRefundPerChunk;     // 放弃每区块返还（通常 < 购买）
public final ForgeConfigSpec.IntValue    plotMaxChunksPerPlayer; // -1=不限
public final ForgeConfigSpec.IntValue    plotMapViewRadius;      // 一次请求的区块半径
public final ForgeConfigSpec.IntValue    plotMessageBoardSize;  // 留言板上限
public final ForgeConfigSpec.BooleanValue legacyCommandsEnabled; // 旧指令全局开关（默认 false）

// 在 Common(...) 构造内：
builder.push("plot");
builder.comment("Enable the new Cities-Skylines-style plot system by default (per-player can override via /land mode).");
this.plotSystemEnabled = builder.define("plotSystemEnabled", true);
builder.comment("Cost (player funds) to buy one chunk in plot mode.");
this.plotCostPerChunk = builder.defineInRange("plotCostPerChunk", 0.0, 0.0, 1000000.0);
builder.comment("Funds refunded per chunk when abandoning in plot mode.");
this.plotRefundPerChunk = builder.defineInRange("plotRefundPerChunk", 0.0, 0.0, 1000000.0);
builder.comment("Max chunks a single player can own (-1 = unlimited).");
this.plotMaxChunksPerPlayer = builder.defineInRange("plotMaxChunksPerPlayer", -1, -1, 1000000);
builder.comment("Chunk radius fetched per plot-data request (client pan).");
this.plotMapViewRadius = builder.defineInRange("plotMapViewRadius", 16, 4, 64);
builder.comment("Max message-board entries per region.");
this.plotMessageBoardSize = builder.defineInRange("plotMessageBoardSize", 20, 0, 200);
builder.comment("Globally enable legacy /land claim|add|unclaim (also require player mode=old).");
this.legacyCommandsEnabled = builder.define("legacyCommandsEnabled", false);
builder.pop();
```

---

## 5. 网络层 `network/`

### 5.1 `ModMessages.java`（注册全部包）

> Forge 1.20.1（47.x）`NetworkRegistry.newSimpleChannel` 仍可用（已弃用但可编译运行）。若希望改用 `ChannelBuilder`，等价替换即可。

```java
package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.client.ClientPacketReceivers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModMessages {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LandEconomyMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;
    private static int next() { return id++; }

    public static void register() {
        // C2S（客户端→服务端）
        INSTANCE.registerMessage(next(), PacketC2SOpenPlotMap.class,        PacketC2SOpenPlotMap::enc,        PacketC2SOpenPlotMap::dec,        (m,c)->PacketC2SOpenPlotMap.handle(m,c));
        INSTANCE.registerMessage(next(), PacketC2SRequestPlotData.class,   PacketC2SRequestPlotData::enc,    PacketC2SRequestPlotData::dec,    (m,c)->PacketC2SRequestPlotData.handle(m,c));
        INSTANCE.registerMessage(next(), PacketC2SPlotAction.class,        PacketC2SPlotAction::enc,         PacketC2SPlotAction::dec,         (m,c)->PacketC2SPlotAction.handle(m,c));
        INSTANCE.registerMessage(next(), PacketC2SRequestRegionDetail.class,PacketC2SRequestRegionDetail::enc,PacketC2SRequestRegionDetail::dec,(m,c)->PacketC2SRequestRegionDetail.handle(m,c));
        INSTANCE.registerMessage(next(), PacketC2SPostMessage.class,       PacketC2SPostMessage::enc,        PacketC2SPostMessage::dec,        (m,c)->PacketC2SPostMessage.handle(m,c));
        // S2C（服务端→客户端）
        INSTANCE.registerMessage(next(), PacketS2CPlotChunkData.class,     PacketS2CPlotChunkData::enc,     PacketS2CPlotChunkData::dec,     (m,c)->ClientPacketReceivers.onPlotChunkData(m,c));
        INSTANCE.registerMessage(next(), PacketS2CPlotActionResult.class,  PacketS2CPlotActionResult::enc,  PacketS2CPlotActionResult::dec,  (m,c)->ClientPacketReceivers.onPlotActionResult(m,c));
        INSTANCE.registerMessage(next(), PacketS2CRegionDetail.class,      PacketS2CRegionDetail::enc,      PacketS2CRegionDetail::dec,      (m,c)->ClientPacketReceivers.onRegionDetail(m,c));
        INSTANCE.registerMessage(next(), PacketS2COpenScreen.class,        PacketS2COpenScreen::enc,         PacketS2COpenScreen::dec,        (m,c)->ClientPacketReceivers.onOpenScreen(m,c));
        INSTANCE.registerMessage(next(), PacketS2CForceExitPlot.class,     PacketS2CForceExitPlot::enc,      PacketS2CForceExitPlot::dec,      (m,c)->ClientPacketReceivers.onForceExit(m,c));
    }

    public static <M> void sendToServer(M msg) { INSTANCE.sendToServer(msg); }
    public static <M> void sendToPlayer(net.minecraft.server.level.ServerPlayer p, M msg) {
        INSTANCE.send(msg, net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p));
    }
}
```

> `LandEconomyMod.commonSetup` 中：`event.enqueueWork(ModMessages::register);`

### 5.2 代表性包：`PacketC2SPlotAction.java`（购买/放弃，服务端权威）

```java
package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.plot.PlotService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketC2SPlotAction {
    public enum Action { BUY, ABANDON }
    private final Action action;
    private final List<Long> chunks;   // 待操作的 chunkKey 列表
    private final String dimensionId;
    public PacketC2SPlotAction(Action a, List<Long> chunks, String dim) { this.action=a; this.chunks=chunks; this.dimensionId=dim; }

    public static void enc(PacketC2SPlotAction m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeVarInt(m.chunks.size());
        for (long k : m.chunks) b.writeLong(k);
        b.writeUtf(m.dimensionId);
    }
    public static PacketC2SPlotAction dec(FriendlyByteBuf b) {
        Action a = b.readEnum(Action.class);
        int n = b.readVarInt();
        List<Long> list = new ArrayList<>(n);
        for (int i=0;i<n;i++) list.add(b.readLong());
        return new PacketC2SPlotAction(a, list, b.readUtf());
    }
    public static void handle(PacketC2SPlotAction m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            PlotService.Result r = PlotService.process(p, m.action, m.chunks, m.dimensionId);
            ModMessages.sendToPlayer(p, new PacketS2CPlotActionResult(r.success, r.message, r.newFunds, r.updatedChunks));
        });
        ctx.get().setPacketHandled(true);
    }
}
```

其余 C2S 包结构同构（`enc/dec/handle`），`handle` 内 `enqueueWork` 调对应服务端方法；S2C 包 `handle` 委托 `ClientPacketReceivers`（经 `DistExecutor` 隔离客户端类，见 §7.4）。所有包字段如下：

| 包 | 字段 | 服务端行为 |
|---|---|---|
| `PacketC2SOpenPlotMap` | 无 | 校验 `mode==new` → `data.setInPlotMode(p,true)` → 下发初始 `S2CPlotChunkData`（玩家周围 `plotMapViewRadius`）+ `S2COpenScreen(PLOT_MAP)` |
| `PacketC2SRequestPlotData` | `int cx,cz`（视图中心 chunk 坐标） | 服务端取 `plotMapViewRadius` 范围 `snapshotPlotCells` → 下发 `S2CPlotChunkData` |
| `PacketC2SPlotAction` | `Action`+`List<Long>`+`dim` | `PlotService.process`（见 §6.3） |
| `PacketC2SRequestRegionDetail` | `long chunkKey`+`dim` | 解析该 chunk 所属 region → 下发 `S2CRegionDetail` |
| `PacketC2SPostMessage` | `UUID regionId`+`String text` | 校验成员 → `region.addMessage(...)` → 回 `S2CRegionDetail`（刷新） |
| `PacketS2CPlotChunkData` | `List<PlotCell>`（每项 chunkKey/ownerUUID/regionName/isFlyland）+ `int cx0,cz0,cx1,cz1` | 客户端写入 `PlotClientCache` |
| `PacketS2CPlotActionResult` | `bool success,String msg,double newFunds,List<Long> updated` | 客户端提示 + 刷新余额 + 标记重拉该范围 |
| `PacketS2CRegionDetail` | `regionId,name,ownerName,List<String> members,double gdp,int pop,double bank,List<MessageEntry>,boolean isMine` | 客户端打开/刷新 `RegionDetailScreen` |
| `PacketS2COpenScreen` | `enum {PLOT_MAP, CHEST, REGION_DETAIL}`(+可选 regionId) | 客户端打开对应 Screen |
| `PacketS2CForceExitPlot` | 无 | 客户端关闭 `PlotMapScreen` 并清空选区 |

---

## 6. 服务端逻辑

### 6.1 区域飞行权限 `FlightPermissionHandler.java`（新增，服务端权威）

> 权限 `region_fly(11)`：`true`=允许区域**成员**（owner+members）在该区域内飞行。`false`=不授飞行（维持原版生存行为）。离开区域 → 撤销 `mayfly`/`flying`。

```java
package cn.autoforged.land_economy_mod_1783600667;

import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID)
public class FlightPermissionHandler {
    private static final int PERIOD = 10; // 每 10 tick 检查一次
    private static final Set<UUID> FLY_GRANTED = ConcurrentHashMap.newKeySet(); // 当前由本模组授飞行的玩家

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % PERIOD != 0) return;

        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return;
        // 复用 RegionEventListener.getRegionAt 思路：找玩家所在最小面积区域
        RegionData region = regionAt(sp.level(), sp.blockPosition(), data);

        boolean shouldGrant = region != null
                && region.getPermission(11)            // region_fly 开启
                && region.isMember(sp.getUUID());     // 仅成员
        boolean granted = FLY_GRANTED.contains(sp.getUUID());

        if (shouldGrant && !granted) {
            grant(sp); FLY_GRANTED.add(sp.getUUID());
        } else if (!shouldGrant && granted) {
            revoke(sp); FLY_GRANTED.remove(sp.getUUID());
        }
    }

    private static void grant(ServerPlayer sp) {
        var ab = sp.getAbilities();
        if (!ab.mayfly) { ab.mayfly = true; sp.onUpdateAbilities(); }
    }
    private static void revoke(ServerPlayer sp) {
        var ab = sp.getAbilities();
        // 不剥夺创造/旁观飞行权限
        if (sp.isCreative() || sp.isSpectator()) return;
        if (ab.mayfly) { ab.mayfly = false; }
        if (ab.flying) { ab.flying = false; sp.fallDistance = 0f; } // 离开授权区域：解除飞行
        sp.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        FLY_GRANTED.remove(e.getEntity().getUUID());
    }

    private static RegionData regionAt(Level l, net.minecraft.core.BlockPos pos, EconomySavedData data) {
        String dim = l.dimension().location().toString();
        RegionData best = null;
        for (RegionData r : data.getAllRegions()) {
            if (r.getDimensionId() == null || !r.getDimensionId().equals(dim) || !r.containsPos(pos)) continue;
            if (best == null || r.getAreaSize() < best.getAreaSize()) best = r;
        }
        return best;
    }
}
```

> 说明：飞行由服务端 `Abilities` 控制（原版同步），客户端无法在未授权区域维持飞行；离开区域立即撤销并解除 `flying`。创造/旁观不受影响。

### 6.2 区域冻结权限（`RegionEventListener.java` 扩展补丁）

> 权限 `block_update(12)`：`true`(默认)=允许方块更新；`false`=**区域冻结**。冻结时取消：邻居通知、活塞、流体放置、作物生长、爆炸；并对**非房主**取消放置/破坏（房主仍可建）。

```java
// 在 RegionEventListener 新增：

@SubscribeEvent
public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
    if (e.getLevel().isClientSide()) return;
    RegionData r = getRegionAt((Level) e.getLevel(), e.getPos());
    if (r != null && !r.getPermission(12)) e.setCanceled(true);   // 冻结：禁止方块更新传播
}

@SubscribeEvent
public static void onPiston(BlockEvent.PistonEvent e) {
    if (e.getLevel().isClientSide()) return;
    RegionData r = getRegionAt((Level) e.getLevel(), e.getPos());
    if (r != null && !r.getPermission(12)) e.setCanceled(true);    // 冻结：禁止活塞
}

@SubscribeEvent
public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent e) {
    if (e.getLevel().isClientSide()) return;
    RegionData r = getRegionAt((Level) e.getLevel(), e.getPos());
    if (r != null && !r.getPermission(12)) e.setCanceled(true);    // 冻结：禁止流体流动产生方块
}

@SubscribeEvent
public static void onCropGrow(BlockEvent.CropGrowEvent.Pre e) {
    if (e.getLevel().isClientSide()) return;
    RegionData r = getRegionAt((Level) e.getLevel(), e.getPos());
    if (r != null && !r.getPermission(12)) e.setCanceled(true);    // 冻结：禁止作物生长
}

// 爆炸：原 onExplosionStart 已用 perm(0)；冻结时额外强制取消
@SubscribeEvent
public static void onExplosionStart(ExplosionEvent.Start event) {
    Level level = event.getLevel();
    BlockPos center = BlockPos.containing(event.getExplosion().getPosition());
    RegionData region = getRegionAt(level, center);
    if (region == null) return;
    if (!region.getPermission(0) || !region.getPermission(12)) { event.setCanceled(true); return; }
    if (!region.getPermission(9) && region.getPermission(0)) event.getExplosion().clearToBlow();
}

// 放置/破坏：冻结时仅房主可建（其余取消）
@SubscribeEvent
public static void onBlockBreak(BlockEvent.BreakEvent event) {
    if (event.getLevel().isClientSide()) return;
    Player player = event.getPlayer(); if (player == null) return;
    RegionData region = getRegionAt((Level) event.getLevel(), event.getPos());
    if (region == null) return;
    boolean frozen = !region.getPermission(12);
    boolean isOwner = region.getOwner() != null && region.getOwner().equals(player.getUUID());
    if (frozen && !isOwner) { event.setCanceled(true); return; }            // 冻结：非房主禁建
    if (!region.isMember(player.getUUID()) && !region.getPermission(10)) event.setCanceled(true); // 原逻辑
}

@SubscribeEvent
public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
    if (event.getLevel().isClientSide()) return;
    if (!(event.getEntity() instanceof Player player)) return;
    RegionData region = getRegionAt((Level) event.getLevel(), event.getPos());
    if (region == null) return;
    boolean frozen = !region.getPermission(12);
    boolean isOwner = region.getOwner() != null && region.getOwner().equals(player.getUUID());
    if (frozen && !isOwner) { event.setCanceled(true); return; }
    if (!region.isMember(player.getUUID()) && !region.getPermission(10)) event.setCanceled(true);
}

// 强制退出地块界面（受击/传送/被位移） —— 6.4
@SubscribeEvent
public static void onLivingHurt(LivingHurtEvent e) {
    if (e.getEntity().level().isClientSide) return;
    if (!(e.getEntity() instanceof ServerPlayer sp)) return;
    forceExitPlotIfActive(sp);
}
@SubscribeEvent
public static void onTeleport(EntityTeleportEvent e) {
    if (e.getEntity().level().isClientSide) return;
    if (!(e.getEntity() instanceof ServerPlayer sp)) return;
    forceExitPlotIfActive(sp);
}
```

### 6.3 地块购买/放弃核心 `plot/PlotService.java`（服务端权威）

```java
package cn.autoforged.land_economy_mod_1783600667.plot;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import cn.autoforged.land_economy_mod_1783600667.ModConfig;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public final class PlotService {
    public static class Result {
        public final boolean success; public final String message;
        public final double newFunds; public final List<Long> updatedChunks;
        public Result(boolean s,String m,double f,List<Long> u){success=s;message=m;newFunds=f;updatedChunks=u;}
    }

    public static Result process(ServerPlayer p, PlotAction.Action action, List<Long> chunks, String dim) {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data == null) return new Result(false, "经济数据不可用", 0, List.of());
        if (!"new".equals(data.getPlayerPlotMode(p.getUUID())))
            return new Result(false, "当前为旧版模式，请先 /land mode new", data.getPlayerFunds(p.getUUID()), List.of());
        if (chunks.isEmpty()) return new Result(false, "未选择任何区块", data.getPlayerFunds(p.getUUID()), List.of());

        RegionData mine = data.getRegionByOwner(p.getUUID());
        List<Long> changed = new ArrayList<>();

        if (action == PlotAction.Action.BUY) {
            double costPer = ModConfig.COMMON.plotCostPerChunk.get();
            int max = ModConfig.COMMON.plotMaxChunksPerPlayer.get();
            // 校验每个 chunk 未被占用、未重复购买
            int alreadyOwned = mine != null ? mine.getClaimedChunks().size() : 0;
            int wantBuy = 0;
            for (long k : chunks) {
                RegionData owner = data.getRegionOwningChunk(dim, k);
                if (owner != null) continue;                  // 已被他人/自己占用
                wantBuy++;
            }
            if (wantBuy == 0) return new Result(false, "所选区块均已被占用", data.getPlayerFunds(p.getUUID()), List.of());
            if (max >= 0 && alreadyOwned + wantBuy > max)
                return new Result(false, "超过最大区块数上限 " + max, data.getPlayerFunds(p.getUUID()), List.of());
            double total = costPer * wantBuy;
            double funds = data.getPlayerFunds(p.getUUID());
            if (total > 0 && funds < total)
                return new Result(false, "资金不足（需 " + total + "，现有 " + funds + "）", funds, List.of());

            // 落地
            if (mine == null) {
                mine = new RegionData();
                mine.setOwner(p.getUUID());
                mine.setName(p.getScoreboardName() + "的领地");
                mine.setDimensionId(dim);
                data.createRegion(p.getUUID(), mine);
            }
            for (long k : chunks) {
                if (data.getRegionOwningChunk(dim, k) == null && mine.addChunk(k)) changed.add(k);
            }
            mine.recomputeAABBFromChunks();
            if (total > 0) data.addPlayerFunds(p.getUUID(), -total);
            data.setDirty();
            return new Result(true, "购买 " + changed.size() + " 区块，花费 " + total,
                    data.getPlayerFunds(p.getUUID()), changed);

        } else { // ABANDON
            if (mine == null) return new Result(false, "你没有可放弃的地块", data.getPlayerFunds(p.getUUID()), List.of());
            double refundPer = ModConfig.COMMON.plotRefundPerChunk.get();
            int n = 0;
            for (long k : chunks) {
                if (mine.ownsChunk(k) && mine.removeChunk(k)) { changed.add(k); n++; }
            }
            if (n == 0) return new Result(false, "所选区块均非你所有", data.getPlayerFunds(p.getUUID()), List.of());
            mine.recomputeAABBFromChunks();
            double refund = refundPer * n;
            if (refund > 0) data.addPlayerFunds(p.getUUID(), refund);
            data.setDirty();
            return new Result(true, "放弃 " + n + " 区块，返还 " + refund,
                    data.getPlayerFunds(p.getUUID()), changed);
        }
    }
}
```

> 所有资金/冲突/边界校验在服务端；客户端仅发请求。`getRegionOwningChunk` 保证不会买到他人地块。

### 6.4 强制退出地块界面

在 `RegionEventListener`（上文 `forceExitPlotIfActive`）与 `FlightPermissionHandler` 同侧服务端实现：

```java
private static void forceExitPlotIfActive(ServerPlayer sp) {
    EconomySavedData data = LandEconomyMod.getEconomyData();
    if (data == null || !data.isInPlotMode(sp.getUUID())) return;
    data.setInPlotMode(sp.getUUID(), false);
    cn.autoforged.land_economy_mod_1783600667.network.ModMessages.sendToPlayer(
        sp, new cn.autoforged.land_economy_mod_1783600667.network.PacketS2CForceExitPlot());
}
```

并在 `RegionTitleHandler` 的 `onPlayerTick` 末尾加一段：若 `data.isInPlotMode(sp)` 且玩家自进入后位置变化超过阈值（或被位移），同样 `forceExitPlotIfActive`。进入地块界面时记录 `LAST_PLOT_POS`（仿 `LAST_POS`）。

### 6.5 命令扩展

`command/ModCommands.java`（在 `/land` 树内追加，作为 `claim`/`add` 的同级 `.then`）：

```java
.then(Commands.literal("map")
        .executes(ModCommands::openMap))
.then(Commands.literal("mode")
        .then(Commands.argument("mode", StringArgumentType.word())
                .suggests((c,b)->{ b.suggest("new"); b.suggest("old"); return b.buildFuture(); })
                .executes(ModCommands::setPlotMode))
        .executes(ModCommands::showPlotMode))
.then(Commands.literal("gui")
        .executes(ModCommands::openChestGui))
.then(Commands.literal("message")
        .then(Commands.argument("region", StringArgumentType.string())
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ModCommands::postMessage))))
```

并在私有转发区加 `openMap/setPlotMode/showPlotMode/openChestGui/postMessage` → `RegionCommandHandler.xxx(ctx)`。

`command/RegionCommandHandler.java` 补丁：

```java
// getPermissionChinese 新增：
case "region_fly" -> "区域飞行";
case "block_update" -> "方块更新(区域冻结)";

// /land map
public static int openMap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer p = ctx.getSource().getPlayerOrException();
    EconomySavedData data = LandEconomyMod.getEconomyData();
    if (data == null) { ctx.getSource().sendFailure(Component.translatable("command.land_economy_mod_1783600667.error.no_data")); return 0; }
    if (!"new".equals(data.getPlayerPlotMode(p.getUUID()))) {
        ctx.getSource().sendFailure(Component.literal("当前为旧版模式，请先 /land mode new 切换到地块系统")); return 0;
    }
    data.setInPlotMode(p.getUUID(), true);
    // 下发初始地块数据 + 打开 Screen
    net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(p.blockPosition());
    var cells = data.snapshotPlotCells(p.level().dimension().location().toString(),
            cp.x, cp.z, cp.x, cp.z); // 占位；实际用 plotMapViewRadius
    cn.autoforged.land_economy_mod_1783600667.network.ModMessages.sendToPlayer(p,
        new cn.autoforged.land_economy_mod_1783600667.network.PacketS2COpenScreen(
            cn.autoforged.land_economy_mod_1783600667.network.PacketS2COpenScreen.Type.PLOT_MAP));
    return 1;
}

// /land mode <new|old>
public static int setPlotMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer p = ctx.getSource().getPlayerOrException();
    String m = ctx.getArgument("mode", String.class).toLowerCase();
    if (!m.equals("new") && !m.equals("old")) { ctx.getSource().sendFailure(Component.literal("仅支持 new/old")); return 0; }
    EconomySavedData data = LandEconomyMod.getEconomyData();
    if (data == null) return 0;
    data.setPlayerPlotMode(p.getUUID(), m);
    // 切到 new：把旧 AABB 迁移为 chunk 集合（保留原区域）
    if (m.equals("new")) {
        RegionData r = data.getRegionByOwner(p.getUUID());
        if (r != null) data.migrateLegacyAABBToChunks(r);
    }
    ctx.getSource().sendSuccess(() -> Component.literal("已切换为 " + ("new".equals(m)?"新版(地块系统)":"旧版(区域声明)")).withStyle(ChatFormatting.GREEN), true);
    return 1;
}

// /land gui
public static int openChestGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer p = ctx.getSource().getPlayerOrException();
    cn.autoforged.land_economy_mod_1783600667.network.ModMessages.sendToPlayer(p,
        new cn.autoforged.land_economy_mod_1783600667.network.PacketS2COpenScreen(
            cn.autoforged.land_economy_mod_1783600667.network.PacketS2COpenScreen.Type.CHEST));
    return 1;
}

// /land message <region> <text>
public static int postMessage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer p = ctx.getSource().getPlayerOrException();
    String name = ctx.getArgument("region", String.class);
    String text = ctx.getArgument("text", String.class);
    EconomySavedData data = LandEconomyMod.getEconomyData();
    if (data == null) return 0;
    RegionData r = null;
    for (RegionData x : data.getAllRegions()) if (x.getName().equalsIgnoreCase(name)) { r = x; break; }
    if (r == null) { ctx.getSource().sendFailure(Component.literal("未找到该区域")); return 0; }
    int max = cn.autoforged.land_economy_mod_1783600667.ModConfig.COMMON.plotMessageBoardSize.get();
    r.addMessage(p.getUUID(), p.getScoreboardName(), text, max);
    ctx.getSource().sendSuccess(() -> Component.literal("留言已发布").withStyle(ChatFormatting.GREEN), true);
    return 1;
}
```

**旧指令在新版模式下的提示**（`claimLandWithPos`/`expandRegion`/`unclaimLandBlock` 开头加）：

```java
if (!ModConfig.COMMON.legacyCommandsEnabled.get()) {
    // 旧指令需玩家处于 old 模式或管理员开启 legacyCommandsEnabled
}
EconomySavedData data = LandEconomyMod.getEconomyData();
if (data != null && "new".equals(data.getPlayerPlotMode(player.getUUID()))) {
    ctx.getSource().sendFailure(Component.literal("新版(地块系统)模式下请使用 /land map 进行地块购买/放弃；如需旧版请 /land mode old"));
    return 0;
}
```

> 注意：`/land unclaim Block` 在 new 模式仍可用以放弃整个母区域（清空 `claimedChunks` + 删 region），与旧版语义一致；提示文案保持。

---

## 7. 客户端逻辑

### 7.1 `client/plot/PlotMapScreen.java`（核心，2D 俯视地块地图）

```java
package cn.autoforged.land_economy_mod_1783600667.client.plot;

import cn.autoforged.land_economy_mod_1783600667.client.gui.RegionDetailScreen;
import cn.autoforged.land_economy_mod_1783600667.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

public class PlotMapScreen extends Screen {
    // 视图：以 block 坐标为中心
    private double centerX, centerZ;     // 玩家进入时所在 block 坐标
    private double cellSize = 24.0;      // 每区块像素（滚轮缩放）
    private final Set<Long> selectedBuy = new HashSet<>();
    private final Set<Long> selectedAbandon = new HashSet<>();
    private boolean dragging = false; private int dragButton = -1;
    private double dragStartX, dragStartY; private int dragLastMX, dragLastMY;

    public PlotMapScreen(BlockPos origin) { super(net.minecraft.network.chat.Component.literal("地块地图")); this.centerX=origin.getX(); this.centerZ=origin.getZ(); }

    @Override public void tick() {
        // WASD 平移（不改变玩家实体）
        double speed = 6.0 / cellSize * 16.0; // 像素→block
        if (ClientKeyState.forward) centerZ -= speed;
        if (ClientKeyState.back)    centerZ += speed;
        if (ClientKeyState.left)    centerX -= speed;
        if (ClientKeyState.right)   centerX += speed;
        // 请求视图内 chunk 数据（按需节流）
        PlotClientCache.requestIfStale(centerX, centerZ, cellSize);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int W=width, H=height;
        int cx0 = (int)Math.floor((centerX - (W/2.0)/cellSize*16)/16);
        int cz0 = (int)Math.floor((centerZ - (H/2.0)/cellSize*16)/16);
        int cx1 = (int)Math.ceil ((centerX + (W/2.0)/cellSize*16)/16);
        int cz1 = (int)Math.ceil ((centerZ + (H/2.0)/cellSize*16)/16);
        for (int cx=cx0; cx<=cx1; cx++) for (int cz=cz0; cz<=cz1; cz++) {
            long key = ChunkPos.asLong(cx, cz);
            double px = W/2.0 + (cx*16 - centerX)/16.0*cellSize;
            double pz = H/2.0 + (cz*16 - centerZ)/16.0*cellSize;
            PlotClientCache.Cell cell = PlotClientCache.get(key);
            int color;
            if (selectedBuy.contains(key)) color = 0xFFFFFFFF;          // 白：选中(待购买)
            else if (selectedAbandon.contains(key)) color = 0xFFFFFFFF;
            else if (cell==null) color = 0x33000000;                     // 未请求/未渲染
            else if (cell.isMine) color = 0x400000AA | 0xFF000000;      // 蓝：自己已购买
            else if (cell.isOthers) color = 0x400000FF | 0xFF000000;    // 红：他人已购买
            else color = 0x4000AA00 | 0xFF000000;                        // 绿：未购买
            g.fill((int)px, (int)pz, (int)(px+cellSize), (int)(pz+cellSize), color);
            // 四色高亮框：Create 风格描边
            int border = selectedBuy.contains(key)||selectedAbandon.contains(key)?0xFFFFFFFF
                    : cell==null?0xFF888888 : cell.isMine?0xFF0000FF : cell.isOthers?0xFFFF0000 : 0xFF00AA00;
            g.renderOutline((int)px, (int)pz, (int)cellSize, (int)cellSize, border);
        }
        // 框选矩形（dragging 且移动距离>阈值）
        if (dragging && movedEnough()) drawSelectionBox(g, mx, my);
        // HUD：费用提示、操作提示
        g.drawString(font, "WASD:平移  滚轮:缩放  左键:购买  右键:放弃  回车:确认  ESC/空格:退出", 8, H-14, 0xFFFFFF);
        double cost = (selectedBuy.size()-selectedAbandon.size())* cn.autoforged.land_economy_mod_1783600667.ModConfig.COMMON.plotCostPerChunk.get();
        g.drawString(font, "待购买:"+selectedBuy.size()+" 待放弃:"+selectedAbandon.size()+" 预计费用:"+cost, 8, 8, 0xFFFF00);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        long k = pickChunk(mx, my);
        if (btn==0) { dragging=true; dragButton=0; dragStartX=mx; dragStartY=my; }
        else if (btn==1) { dragging=true; dragButton=1; dragStartX=mx; dragStartY=my; }
        else if (btn==0 && Screen.hasShiftDown()) { /* 单击已购买→详情 */ openDetail(k); }
        return true;
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (!dragging) return true;
        if (movedEnough()) { // 框选
            Set<Long> range = chunksInRect(dragStartX, dragStartY, mx, my);
            if (dragButton==0) { for(long k:range) selectedBuy.add(k); }
            else { for(long k:range) if(PlotClientCache.isMine(k)) selectedAbandon.add(k); }
        } else { // 单击
            long k = pickChunk(mx, my);
            if (dragButton==0) { if (selectedAbandon.remove(k)) {} else selectedBuy.add(k); }
            else { if (selectedBuy.remove(k)) {} else if (PlotClientCache.isMine(k)) selectedAbandon.add(k); else openDetail(k); }
        }
        dragging=false; dragButton=-1; return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double d) { cellSize=Math.max(6,Math.min(64,cellSize - d*2)); return true; }
    @Override public boolean keyPressed(int k,int sc,int m) {
        if (k==org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || k==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (k==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) { confirm(); return true; }
        ClientKeyState.set(k, true); return super.keyPressed(k,sc,m);
    }
    @Override public boolean keyReleased(int k,int sc,int m){ ClientKeyState.set(k,false); return super.keyReleased(k,sc,m); }

    private void confirm() {
        if (!selectedBuy.isEmpty()) ModMessages.sendToServer(new PacketC2SPlotAction(PacketC2SPlotAction.Action.BUY, new ArrayList<>(selectedBuy), dim()));
        if (!selectedAbandon.isEmpty()) ModMessages.sendToServer(new PacketC2SPlotAction(PacketC2SPlotAction.Action.ABANDON, new ArrayList<>(selectedAbandon), dim()));
        selectedBuy.clear(); selectedAbandon.clear();
    }
    private String dim() { return minecraft.level.dimension().location().toString(); }
    private long pickChunk(double mx,double my){ int cx=(int)Math.floor((centerX + (mx-W/2.0)/cellSize*16)/16); int cz=(int)Math.floor((centerZ + (my-H/2.0)/cellSize*16)/16); return ChunkPos.asLong(cx,cz); }
    private boolean movedEnough(){ return Math.hypot(dragStartX-minecraft.mouseHandler.xpos(), dragStartY-minecraft.mouseHandler.ypos())>6; }
    private Set<Long> chunksInRect(double x0,double y0,double x1,double y1){ /* 遍历矩形内 chunk 返回 key 集合 */ return new HashSet<>(); }
    private void drawSelectionBox(GuiGraphics g,int mx,int my){ /* 画矩形选区 */ }
    private void openDetail(long k){ ModMessages.sendToServer(new PacketC2SRequestRegionDetail(k, dim())); }
}
```

> `ClientKeyState`：客户端按键状态枚举（forward/back/left/right）；`PlotClientCache`：`Map<Long,Cell>` + 节流请求（避免每 tick 发包）。框选/单击区分阈值见 `movedEnough()`。
> 高亮框仅本客户端在 `PlotMapScreen` 内绘制，**不广播**。

### 7.2 `client/gui/RegionDetailScreen.java`（区域详情：GDP/玩家/留言/银行）

由 `PacketS2CRegionDetail` 触发打开。绘制：区域名、房主、成员列表、GDP、人口、银行存款、留言列表（滚动）、输入框发布留言（`TextFieldWidget` → `PacketC2SPostMessage`）。

```java
public class RegionDetailScreen extends Screen {
    private final PacketS2CRegionDetail d;
    private net.minecraft.client.gui.components.EditBox input;
    private int scroll = 0;
    public RegionDetailScreen(PacketS2CRegionDetail d){ super(Component.literal("区域详情")); this.d=d; }
    @Override protected void init(){ input = new net.minecraft.client.gui.components.EditBox(font, width/2-100, height-30, 200, 16, Component.empty()); addRenderableWidget(input); }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        g.fill(20,20,width-20,height-40, 0xC0101010);
        g.drawString(font,"区域: "+d.name, 30, 30, 0xFFD700);
        g.drawString(font,"房主: "+d.ownerName+(d.isMine?" (你)":""), 30, 44, 0xFFFF00);
        g.drawString(font,"成员: "+String.join(", ", d.members), 30, 58, 0xFFFFFF);
        g.drawString(font,"GDP: "+fmt(d.gdp)+"  人口: "+d.pop+"  银行: "+fmt(d.bank), 30, 72, 0x55FF55);
        g.drawString(font,"留言板:", 30, 92, 0xAAAAFF);
        int y=106; for (int i=scroll;i<d.messages.size() && y<height-50;i++){ var m=d.messages.get(i); g.drawString(font,"["+m.authorName+"] "+m.text, 40, y, 0xDDDDDD); y+=12; }
        g.drawString(font,"输入留言后回车发布（仅成员可发布）", width/2-100, height-44, 0x888888);
        super.render(g,mx,my,pt);
    }
    @Override public boolean keyPressed(int k,int sc,int m){
        if (k==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && input!=null && !input.getValue().isBlank() && d.isMine)
            ModMessages.sendToServer(new PacketC2SPostMessage(d.regionId, input.getValue()));
        return super.keyPressed(k,sc,m);
    }
    private String fmt(double v){ return String.format("%,.2f", v); }
}
```

### 7.3 `client/gui/LandChestScreen.java`（箱子GUI 启动器）

> 视觉为 27 格箱子（使用原版 `container/generic_54` 纹理上半），每格为一个功能按钮，点击等价于对应指令。复用现有指令逻辑（通过发包触发服务端）。

```java
public class LandChestScreen extends Screen {
    private static final List<MenuEntry> ENTRIES = List.of(
        new MenuEntry("区域声明/地块界面", () -> ModMessages.sendToServer(new PacketC2SOpenPlotMap())),
        new MenuEntry("区域权限管理", () -> minecraft.setScreen(new PermScreen())),     // 复用 /land permissions 的可点击列表渲染
        new MenuEntry("区域信息", () -> minecraft.player.connection.sendCommand("land info")),
        new MenuEntry("区域银行(存/取)", () -> minecraft.player.connection.sendCommand("land bank deposit 0")), // 弹子菜单
        new MenuEntry("申请加入领地", () -> {}),
        new MenuEntry("切换 新/旧 版模式", () -> minecraft.player.connection.sendCommand("land mode new")),
        new MenuEntry("留言板", () -> {}),
        new MenuEntry("帮助", () -> minecraft.player.connection.sendCommand("land help"))
    );
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        g.blit(TEXTURE, (width-176)/2, (height-166)/2, 0,0,176,166); // 箱子纹理
        for (int i=0;i<ENTRIES.size();i++){ int x=(width-176)/2+8+(i%9)*18, y=(height-166)/2+18+(i/9)*18; g.renderItem(ICON, x, y); }
        // hover 提示
    }
    @Override public boolean mouseClicked(double mx,double my,int b){
        int idx = slotAt(mx,my); if (idx>=0 && idx<ENTRIES.size()) ENTRIES.get(idx).run.run();
        return true;
    }
    record MenuEntry(String name, Runnable run){}
}
```

> 说明：`PermScreen` 复用 `listPermissions` 的服务端结果（可由 `S2CRegionDetail` 扩展或新增小包拉取权限列表）。`land info`/`land bank` 等通过 `player.connection.sendCommand(...)` 触发服务端指令，**操作结果与指令一致**。

### 7.4 客户端包接收 + 类加载隔离 `client/ClientPacketReceivers.java`

```java
package cn.autoforged.land_economy_mod_1783600667.client;

import cn.autoforged.land_economy_mod_1783600667.client.gui.*;
import cn.autoforged.land_economy_mod_1783600667.client.plot.*;
import cn.autoforged.land_economy_mod_1783600667.network.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public final class ClientPacketReceivers {
    public static void onPlotChunkData(PacketS2CPlotChunkData m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> PlotClientCache.merge(m)); ctx.get().setPacketHandled(true);
    }
    public static void onPlotActionResult(PacketS2CPlotActionResult m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> { Minecraft mc=Minecraft.getInstance(); if(mc.player!=null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(m.message)); }); ctx.get().setPacketHandled(true);
    }
    public static void onRegionDetail(PacketS2CRegionDetail m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> Minecraft.getInstance().setScreen(new RegionDetailScreen(m))); ctx.get().setPacketHandled(true);
    }
    public static void onOpenScreen(PacketS2COpenScreen m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> {
            switch (m.type){
                case PLOT_MAP -> Minecraft.getInstance().setScreen(new PlotMapScreen(Minecraft.getInstance().player.blockPosition()));
                case CHEST -> Minecraft.getInstance().setScreen(new LandChestScreen());
                default -> {}
            }
        }); ctx.get().setPacketHandled(true);
    }
    public static void onForceExit(PacketS2CForceExitPlot m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> { var s=Minecraft.getInstance().screen; if (s instanceof PlotMapScreen) s.onClose(); }); ctx.get().setPacketHandled(true);
    }
}
```

> `ClientPacketReceivers` 仅在客户端类路径出现；服务端通过 `DistExecutor` 不会加载。S2C 包的 `handle` 在 `ModMessages` 中直接引用 `ClientPacketReceivers` 静态方法 → 需用 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onX(m,ctx))` 包裹，避免服务端类加载崩溃。示例（对每个 S2C 包）：

```java
(m,c)-> net.minecraftforge.fml.loading.FMLEnvironment.dist.equals(net.minecraftforge.api.distmarker.Dist.CLIENT)
   ? net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
        () -> () -> ClientPacketReceivers.onPlotChunkData(m,c))
   : c.get().setPacketHandled(true);
```

> 简化做法：把 S2C 包 `handle` 写为 `c.get().enqueueWork(()-> ClientPacketReceivers.onX(m,c))`，并在 `ClientPacketReceivers` 仅以 `@OnlyIn(Dist.CLIENT)` 或由客户端启动加载保证。最终实现时统一用 `DistExecutor` 包裹以兼容专用服务端。

### 7.5 客户端事件 `client/ClientModEvents.java`

```java
@Mod.EventBusSubscriber(modid = LandEconomyMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModEvents {
    @SubscribeEvent public static void registerKey(RegisterKeyMappingsEvent e){ /* 注册打开箱子GUI 的键位 */ }
}
```

或在 Forge bus 上监听 `InputEvent` 用键位打开 `LandChestScreen`（客户端直接打开，无需服务端）。`/land map`/`/land gui` 则由服务端回包打开。

---

## 8. 国际化 `zh_cn.json`（追加）

```json
"command.land_economy_mod_1783600667.map.opened": "已进入地块地图",
"command.land_economy_mod_1783600667.map.exit": "已退出地块地图",
"command.land_economy_mod_1783600667.mode.set_new": "已切换到新版(地块系统)",
"command.land_economy_mod_1783600667.mode.set_old": "已切换到旧版(区域声明)",
"economy.region_fly": "区域飞行",
"economy.block_update": "方块更新(区域冻结)",
"economy.plot_buy": "购买地块",
"economy.plot_abandon": "放弃地块",
"economy.plot_select": "选中地块",
"economy.plot_owned_self": "你的地块",
"economy.plot_owned_other": "他人地块",
"economy.plot_unowned": "未购买",
"economy.region_detail": "区域详情",
"economy.message_board": "留言板",
"economy.chest_gui": "领地经济 箱子界面"
```

---

## 9. 数据迁移 / 新旧兼容

- **统一数据模型**：新旧版共用 `RegionData`。新版用 `claimedChunks`，旧版用 AABB（`claimedChunks` 为空时 `containsPos` 回退 AABB）。
- **旧→新（自动）**：`setPlotMode(new)` 时对玩家母区域调 `migrateLegacyAABBToChunks`（把 AABB 覆盖的所有 chunk 写入集合）。幂等、无损，原有区域以地块形式完整保留。
- **新→旧（兼容）**：地块购买/放弃后 `recomputeAABBFromChunks()` 维护 AABB 为所有 chunk 的外接矩形；切回 old 模式后 `/land add` 扩大仍以 AABB 工作，`containsPos` 回退 AABB，不会丢数据。
- **新模式默认**：`ModConfig.plotSystemEnabled=true` + 每玩家 `playerPlotMode` 默认 `"new"`。旧指令在新模式下提示使用 `/land map` 并拒绝执行（`claim/add`）；`unclaim Block` 保留以放弃整块母区域。
- **`region_fly`/`block_update` 默认值**：权限数组默认全 `true`（沿用原构造），即默认不开飞行、不冻结，行为与升级前一致。
- **NBT 向前兼容**：旧存档无 `ClaimedChunks`/`Messages` 字段 → 读为空集/空列表；旧 `Permissions` 为 `byte[11]` → `fromNbt` 用 `Math.min(len,13)` 读前 11 位，新两位默认 `true`。

---

## 10. 关键代码补丁汇总

> 已在 §3、§4、§5、§6、§7 给出可编译级补丁。实现顺序建议：
> 1. `RegionData`（权限+chunk+留言+NBT）→ 2. `EconomySavedData`（chunk 查询+模式+迁移）→ 3. `ModConfig` → 4. `network` 全部包 + `ModMessages` + `LandEconomyMod.commonSetup` 注册 → 5. `PlotService` + `FlightPermissionHandler` + `RegionEventListener` 扩展 → 6. 命令扩展 → 7. 客户端 `Screen` + `ClientPacketReceivers` → 8. `zh_cn.json`。
>
> 每个 `packet` 类需补齐 `enc/dec`（仿 §5.2）；`PlotClientCache`、`ClientKeyState`、`PermScreen`、`PacketS2COpenScreen`/`PacketS2CRegionDetail`/`PacketS2CPlotChunkData`/`PacketS2CPlotActionResult`/`PacketS2CForceExitPlot` 的 `enc/dec` 与对应 S2C 客户端处理在 §7 已给签名/行为。

---

## 11. 编译与测试说明

### 编译
```bash
./gradlew --refresh-dependencies   # 首次拉依赖
./gradlew compileJava             # 仅编译（快速验证语法/包注册）
./gradlew runClient               # 启动客户端开发环境
./gradlew runServer               # 启动服务端开发环境
./gradlew build                   # 完整产物
```
- 关键编译风险点：`NetworkRegistry.newSimpleChannel`（弃用但可用）；S2C 包对客户端类的 `DistExecutor` 隔离；`Screen`/`GuiGraphics`/`EditBox` 的客户端 API 签名（1.20.1 official+parchment）。
- 若 `NetworkRegistry` 在目标 Forge 小版本被移除，替换为 `ChannelBuilder.named(...).networkProtocolVersion(1).simpleChannel()`。

### 测试
1. **区域飞行**：owner `/land permissions set region_fly true` → 进区域按空格起飞；走出区域立即下落；创造模式不受影响；服务端断开/重连后状态一致。
2. **区域冻结**：`/land permissions set block_update false` → 红石/水流/作物/活塞/爆炸停止；房主仍可放置/破坏，非房主不可；`block_update true` 恢复。
3. **地块系统**：
   - `/land mode new` → 旧 AABB 区域自动迁移为 chunk 集合，地图上显示蓝色。
   - `/land map` 打开 2D 地图；WASD 平移、滚轮缩放；左键单击/框选变白选中；回车购买扣 `plotCostPerChunk`；右键放弃返还 `plotRefundPerChunk`；他人地块红色、不可购买。
   - 点击他人/自己地块 → 弹出区域详情（GDP/玩家/留言/银行）；成员可发留言。
   - 受击/传送/被位移 → 强制退出地块界面并清空选区。
   - `/land mode old` → 切回旧版，旧 `/land claim`/`add`/`unclaim` 正常工作；AABB 与 chunk 集合互相兼容。
4. **箱子GUI**：`/land gui`（或键位）打开；各按钮等价于对应指令，结果一致。
5. **多人/服务端**：数据持久化于 `land_economy_mod_data.dat`（overworld `SavedData`）；客户端仅展示与发请求，服务端权威校验通过。
6. **回归**：原有 `/land claim`/`add`/`unclaim`/`permissions`/`bank`/`join`/`flyland` 在 old 模式下行为不变；GDP/人口引擎不受影响。

### 注意事项
- 客户端绝不应直接修改区域数据；所有地块操作走 `PacketC2SPlotAction` → `PlotService`。
- 高亮框/选区仅为客户端 `PlotMapScreen` 内绘制，不广播。
- 地块视图移动不改玩家实体位置（仅 `PlotMapScreen` 内 `centerX/Z` 与客户端缓存）；禁止用 `/tp`/`setPosition` 实现。
