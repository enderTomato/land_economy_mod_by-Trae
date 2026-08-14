# 模组改进实施计划

## 概述
基于 `land_economy_mod_by-Trae` Forge 1.20.1 模组，实现四项改进：
1. **优化1**：鼠标中键移动"地图区域视图"
2. **新增1**：JourneyMap / Xaero's Minimap / Xaero's World Map 第三方地图集成
3. **改进1**：DynMap 风格真实方块颜色俯视地图渲染
4. **改进2**：玩家首次购买区块时要求为区域命名

---

## 一、当前状态分析

### 现有架构
```
客户端 (client/)
├── ClientKeyState.java        — GLFW 按键状态查询（WASD/空格/ESC/回车）
├── ClientModEvents.java        — 客户端初始化占位
├── ClientPacketReceivers.java  — S2C 网络包分发（写缓存 / 开 Screen）
├── gui/
│   ├── LandChestScreen.java    — 箱子 GUI
│   └── RegionDetailScreen.java — 区域详情面板（GDP/人口/留言板）
└── plot/
    ├── PlotMapScreen.java      — 2D 俯视地块地图 Screen（核心）
    ├── PlotMapView.java        — 视角状态管理（centerX/Z, cellSize, 坐标转换）
    └── PlotClientCache.java   — 客户端地块归属缓存

服务端
├── plot/
│   ├── PlotService.java        — 服务端权威地块购买/放弃核心
│   └── PlotAction.java         — 操作枚举（BUY/ABANDON）
├── data/
│   ├── RegionData.java         — 区域数据模型（含 claimedChunks/messages）
│   └── EconomySavedData.java   — 经济数据管理（含 snapshotPlotCells/plotMode）
└── network/
    ├── ModMessages.java        — 网络注册中心（9个包，id 0-8）
    ├── PacketC2SOpenPlotMap.java     — 进入地块视图
    ├── PacketC2SRequestPlotData.java — 请求地块数据
    ├── PacketC2SPlotAction.java      — 购买/放弃请求
    ├── PacketC2SRequestRegionDetail.java — 请求区域详情
    ├── PacketC2SPostMessage.java     — 发布留言
    ├── PacketS2CPlotChunkData.java   — 下发地块归属快照
    ├── PacketS2CPlotActionResult.java — 下发操作结果
    ├── PacketS2CRegionDetail.java    — 下发区域详情
    ├── PacketS2COpenScreen.java      — 打开 Screen
    └── PacketS2CForceExitPlot.java   — 强制退出地块界面
```

### 关键数据流
```
[PlotMapScreen]  ──(WASD 平移)──> PlotMapView.pan() ──> requestChunksIfNeeded()
      │                                                        │
      │                                          PacketC2SRequestPlotData
      │                                                        │
      │                                              [Server] EconomySavedData
      │                                                        │
      │                                          PacketS2CPlotChunkData
      │                                                        │
      └──<──── PlotClientCache.put() ───<──── ClientPacketReceivers.onPlotChunkData()

[PlotMapScreen]  ──(回车确认)──> executeConfirmed()
      │
      ├── PacketC2SPlotAction(BUY, chunks) ──> [Server] PlotService.process()
      │                                                │
      │                              PacketS2CPlotActionResult ──> 清除选区/刷新
      └── PacketC2SPlotAction(ABANDON, chunks) ──> [Server] PlotService.process()
```

### 现有交互模型
- **鼠标事件**：左键(button=0)购买/框选，右键(button=1)放弃/框选，滚轮缩放
- **键盘事件**：WASD 平移，空格/ESC 退出，回车确认
- **拖拽检测**：180ms 阈值区分单击与拖拽，4px 移动阈值
- **确认流程**：回车 → `openConfirm()` 弹窗 → 再回车 → `executeConfirmed()` 发送网络包

---

## 二、详细修改方案

### 2.1 优化1：鼠标中键移动"地图区域视图"

**目标**：在 `PlotMapScreen` 中支持鼠标中键拖拽平移地图，与现有 WASD 平移互补。

**修改文件**：
- `client/plot/PlotMapScreen.java` — 添加中键拖拽处理器
- `client/ClientKeyState.java` — 可选：添加鼠标中键状态查询

**实现要点**：

1. **在 `PlotMapScreen` 中添加中键拖拽状态字段**：
   ```java
   private boolean isMiddleDragging = false;
   private double middleDragLastX, middleDragLastY;
   ```

2. **修改 `mouseClicked()`**：在 `button==0||button==1` 分支外，单独处理 `button==GLFW.GLFW_MOUSE_BUTTON_MIDDLE`：
   ```java
   if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
       isMiddleDragging = true;
       middleDragLastX = mouseX;
       middleDragLastY = mouseY;
       return true;
   }
   ```

3. **修改 `mouseDragged()`**：在中键拖拽时计算增量并调用 `view.pan()`：
   ```java
   if (isMiddleDragging) {
       double dx = mouseX - middleDragLastX;
       double dy = mouseY - middleDragLastY;
       view.pan((int)-dx, (int)-dy);  // 反向：拖拽方向与视角移动方向相反
       middleDragLastX = mouseX;
       middleDragLastY = mouseY;
       String dim = Minecraft.getInstance().level.dimension().location().toString();
       requestChunksIfNeeded(dim);
       return true;
   }
   ```

4. **修改 `mouseReleased()`**：重置中键拖拽状态：
   ```java
   if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
       isMiddleDragging = false;
       return true;
   }
   ```

5. **更新 HUD 提示**：在 `drawHud()` 中添加"中键拖拽: 移动视角"提示。

6. **处理与左键/右键拖拽的冲突**：通过 `isMiddleDragging` 标志优先处理中键，不影响现有左键/右键框选逻辑。

---

### 2.2 改进1：DynMap 风格真实方块颜色俯视地图渲染

**目标**：将 `PlotMapScreen` 的区块渲染从纯色填充改为读取客户端已加载区块的真实方块颜色，类似 DynMap 俯视图。

**修改/新增文件**：
- `client/plot/PlotMapScreen.java` — 修改 `render()` 方法调用地形渲染器
- `client/plot/PlotMapTerrainRenderer.java` — **新增**：地形颜色采样与缓存
- `client/plot/PlotMapView.java` — 无需修改

**实现要点**：

1. **新建 `PlotMapTerrainRenderer` 类**：
   ```java
   public final class PlotMapTerrainRenderer {
       // 缓存：chunkKey -> 16x16 颜色数组（ARGB int）
       private static final Map<Long, int[]> TERRAIN_CACHE = new HashMap<>();
       private static final int CACHE_MAX = 256; // 最多缓存 256 个区块

       /**
        * 对指定区块采样地形颜色。
        * 仅当区块在客户端已加载时返回有效数据。
        * @return 16x16 的 ARGB 颜色数组，未加载区块返回 null
        */
       public static int[] sampleTerrain(ClientLevel level, int cx, int cz) {
           long key = ChunkPos.asLong(cx, cz);
           int[] cached = TERRAIN_CACHE.get(key);
           if (cached != null) return cached;

           // 检查区块是否已加载
           if (!level.hasChunk(cx, cz)) return null;

           int[] colors = new int[256];
           LevelChunk chunk = level.getChunk(cx, cz);
           BlockColors blockColors = Minecraft.getInstance().getBlockColors();

           for (int lx = 0; lx < 16; lx++) {
               for (int lz = 0; lz < 16; lz++) {
                   // 从最高点向下查找第一个非空气方块
                   int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                   BlockPos pos = new BlockPos((cx << 4) + lx, y, (cz << 4) + lz);
                   BlockState state = chunk.getBlockState(pos);

                   // 向下查找直到找到非空气/非透明方块
                   while (y > chunk.getMinBuildHeight() && state.isAir()) {
                       y--;
                       pos = new BlockPos((cx << 4) + lx, y, (cz << 4) + lz);
                       state = chunk.getBlockState(pos);
                   }

                   int color = blockColors.getColor(state, level, pos, 0);
                   // 如果颜色太暗（如洞穴），使用 biome 颜色回退
                   if (color == 0 || (color & 0xFF000000) == 0) {
                       color = getBiomeColor(level, pos);
                   }
                   colors[lx * 16 + lz] = color;
               }
           }

           // LRU 缓存管理
           if (TERRAIN_CACHE.size() >= CACHE_MAX) {
               TERRAIN_CACHE.keySet().iterator().remove();
           }
           TERRAIN_CACHE.put(key, colors);
           return colors;
       }

       /** 清除缓存（区块卸载/维度和切换时） */
       public static void invalidate(int cx, int cz) {
           TERRAIN_CACHE.remove(ChunkPos.asLong(cx, cz));
       }
       public static void clearAll() { TERRAIN_CACHE.clear(); }
   }
   ```

2. **修改 `PlotMapScreen.render()` 中的区块渲染循环**：
   在绘制每个区块时，先尝试获取地形颜色进行填充，回退到原有纯色方案：
   ```java
   int[] terrain = PlotMapTerrainRenderer.sampleTerrain(level, cx, cz);
   if (terrain != null) {
       // 绘制 16x16 地形采样，拉伸到 cellSize 像素
       float scale = (float) view.cellSize / 16f;
       for (int lx = 0; lx < 16; lx++) {
           for (int lz = 0; lz < 16; lz++) {
               int color = terrain[lx * 16 + lz];
               int tx = x0 + (int)(lx * scale);
               int ty = y0 + (int)(lz * scale);
               int tw = (int)Math.ceil(scale);
               int th = (int)Math.ceil(scale);
               g.fill(tx, ty, tx + tw, ty + th, color | 0xFF000000);
           }
       }
   } else {
       // 回退：纯色填充（现有逻辑）
       g.fill(x0, y0, x1, y1, fill);
   }
   ```

3. **性能优化**：
   - 每帧渲染时只采样可见区块
   - 地形缓存仅在区块数据变化时失效（监听 `ChunkEvent.Unload` 或每次 `requestChunksIfNeeded` 时清除对应区域）
   - 当 `cellSize` 较小时（<16），跳过地形渲染直接用纯色（性能保护）

4. **在 `PlotMapScreen.onClose()` 中清除地形缓存**。

---

### 2.3 新增1：第三方地图模组集成（JourneyMap / Xaero's）

**目标**：当玩家安装了 JourneyMap、Xaero's Minimap 或 Xaero's World Map 时，在这些模组的地图上显示区域边界，并支持 Ctrl+左键/右键框选购买/放弃区域。

**修改/新增文件**：
- `client/integration/MapIntegrationManager.java` — **新增**：检测已安装地图模组，注册/注销集成
- `client/integration/IMapIntegration.java` — **新增**：统一集成接口
- `client/integration/JourneyMapIntegration.java` — **新增**：JourneyMap 全屏地图集成
- `client/integration/XaeroMinimapIntegration.java` — **新增**：Xaero's Minimap 集成（边界显示）
- `client/integration/XaeroWorldMapIntegration.java` — **新增**：Xaero's World Map 集成（边界显示 + 选框购买）
- `client/ClientModEvents.java` — 修改：在客户端初始化时触发集成注册
- `ModConfig.java` — 修改：新增第三方地图集成开关
- `plot/PlotAction.java` — 无需修改
- `network/PacketC2SPlotAction.java` — 无需修改（复用现有网络包）

**架构设计**：

#### 2.3.1 集成接口 `IMapIntegration`
```java
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
```

#### 2.3.2 JourneyMap 集成
- **检测方式**：`net.minecraftforge.fml.ModList.get().isLoaded("journeymap")`
- **边界渲染**：通过 JourneyMap API 的 `MapOverlay` 接口，在 minimap 和全屏地图上绘制区域边界
- **选框购买**（JourneyMap 全屏）：
  - 监听 JourneyMap 全屏地图打开事件
  - 注册 `MouseListener` 监听 Ctrl+左键/右键拖拽
  - 将屏幕坐标转换为世界坐标 → 区块坐标
  - 选框完成后弹出确认对话框（方案B：地图内直接确认）
  - 确认后直接调用 `ModMessages.sendToServer(new PacketC2SPlotAction(...))` 发送网络包
  - 不经过 `PlotMapScreen`，直接在 JourneyMap 层完成

#### 2.3.3 Xaero's Minimap 集成
- **检测方式**：`ModList.get().isLoaded("xaerominimap")`
- **边界渲染**：通过 Mixin 或反射注入 `MinimapRenderer` 的渲染钩子，在 minimap 帧上绘制区域边界
- **不支持选框**：Minimap 太小，不支持选框购买

#### 2.3.4 Xaero's World Map 集成
- **检测方式**：`ModList.get().isLoaded("xaeroworldmap")`
- **边界渲染**：通过反射注入 World Map 的 `MapRenderer`，在顶层绘制区域边界
- **选框购买**（全屏世界地图）：
  - 监听 World Map 全屏打开事件
  - 注册鼠标事件监听 Ctrl+左键/右键拖拽
  - 屏幕坐标 → 世界坐标 → 区块坐标转换
  - 选框完成后弹出确认对话框
  - 确认后直接发送网络包

#### 2.3.5 管理器 `MapIntegrationManager`
```java
public final class MapIntegrationManager {
    private static final List<IMapIntegration> ACTIVE = new ArrayList<>();

    public static void init() {
        // 仅在客户端执行
        if (ModList.get().isLoaded("journeymap")) {
            ACTIVE.add(new JourneyMapIntegration());
        }
        if (ModList.get().isLoaded("xaerominimap")) {
            ACTIVE.add(new XaeroMinimapIntegration());
        }
        if (ModList.get().isLoaded("xaeroworldmap")) {
            ACTIVE.add(new XaeroWorldMapIntegration());
        }
        for (IMapIntegration i : ACTIVE) i.init();
    }

    public static void shutdown() {
        for (IMapIntegration i : ACTIVE) i.shutdown();
        ACTIVE.clear();
    }
}
```

#### 2.3.6 确认对话框
在第三方地图中选框完成后，弹出简单的确认 GUI（类似 `PlotMapScreen.drawConfirmDialog()` 但独立实现）：
```java
public class MapSelectionConfirmScreen extends Screen {
    // 显示：待购买/放弃区块数量、费用
    // 回车确认 → 发送 PacketC2SPlotAction
    // ESC 取消 → 关闭
}
```

#### 2.3.7 配置项
在 `ModConfig.java` 的 `plot` 段中新增：
```java
public final ForgeConfigSpec.BooleanValue plotMapIntegrationEnabled;      // 第三方地图集成总开关
public final ForgeConfigSpec.BooleanValue plotJourneyMapIntegration;      // JourneyMap 集成
public final ForgeConfigSpec.BooleanValue plotXaeroMinimapIntegration;    // Xaero's Minimap 集成
public final ForgeConfigSpec.BooleanValue plotXaeroWorldMapIntegration;   // Xaero's World Map 集成
```

#### 2.3.8 客户端初始化
修改 `ClientModEvents.onClientSetup()`：
```java
@SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    if (ModConfig.COMMON.plotMapIntegrationEnabled.get()) {
        MapIntegrationManager.init();
    }
    LandEconomyMod.LOGGER.info("Land Economy Mod client setup complete.");
}
```

---

### 2.4 改进2：首次购买区块时要求区域命名

**目标**：当玩家已购买区块数为 0（首次购买）时，在确认购买前弹出区域命名输入框，玩家输入名称后才能完成购买。

**修改文件**：
- `network/PacketC2SPlotAction.java` — 修改：新增可选 `regionName` 字段
- `plot/PlotService.java` — 修改：使用 `regionName` 而非自动生成名称
- `client/plot/PlotMapScreen.java` — 修改：在 `executeConfirmed()` 前检查是否需要命名
- `client/gui/RegionNameInputScreen.java` — **新增**：区域命名输入界面

**实现要点**：

1. **修改 `PacketC2SPlotAction`**：添加可选的 `regionName` 字段：
   ```java
   private final String regionName; // null 表示不需要命名（非首次购买）

   public PacketC2SPlotAction(PlotAction.Action action, List<Long> chunks, String dim, @Nullable String regionName) {
       this.action = action;
       this.chunks = chunks;
       this.dimensionId = dim;
       this.regionName = regionName;
   }

   public static void enc(PacketC2SPlotAction m, FriendlyByteBuf b) {
       b.writeEnum(m.action);
       b.writeVarInt(m.chunks.size());
       for (long k : m.chunks) b.writeLong(k);
       b.writeUtf(m.dimensionId);
       b.writeBoolean(m.regionName != null);
       if (m.regionName != null) b.writeUtf(m.regionName);
   }

   public static PacketC2SPlotAction dec(FriendlyByteBuf b) {
       PlotAction.Action a = b.readEnum(PlotAction.Action.class);
       int n = b.readVarInt();
       List<Long> list = new ArrayList<>(n);
       for (int i = 0; i < n; i++) list.add(b.readLong());
       String dim = b.readUtf();
       String name = b.readBoolean() ? b.readUtf(64) : null;
       return new PacketC2SPlotAction(a, list, dim, name);
   }
   ```

2. **修改 `PlotService.process()`**：在 BUY 分支中，当 `mine == null` 时使用传入的 `regionName`：
   ```java
   if (mine == null) {
       mine = new RegionData();
       mine.setOwner(p.getUUID());
       // 使用传入的名称，若为空则回退到默认名
       String name = (m.regionName != null && !m.regionName.isBlank())
               ? m.regionName : (p.getScoreboardName() + "的领地");
       mine.setName(name);
       mine.setDimensionId(dim);
       data.createRegion(p.getUUID(), mine);
   }
   ```

3. **修改 `PlotMapScreen.executeConfirmed()`**：在发送 BUY 前检查是否需要命名：
   ```java
   private void executeConfirmed() {
       confirmMode = false;
       String dim = Minecraft.getInstance().level.dimension().location().toString();
       if (!selectedBuy.isEmpty()) {
           // 检查是否需要命名（玩家已购区块数为 0）
           boolean needsName = isPlayerFirstPurchase();
           if (needsName) {
               Minecraft.getInstance().setScreen(new RegionNameInputScreen(
                   name -> {
                       // 命名完成后回到 PlotMapScreen 并发送请求
                       ModMessages.sendToServer(new PacketC2SPlotAction(
                           PlotAction.Action.BUY, new ArrayList<>(selectedBuy), dim, name));
                       clearSelection();
                   },
                   () -> {
                       // 取消：回到 PlotMapScreen
                       Minecraft.getInstance().setScreen(this);
                   }
               ));
               return;
           }
           ModMessages.sendToServer(new PacketC2SPlotAction(
                   PlotAction.Action.BUY, new ArrayList<>(selectedBuy), dim, null));
       }
       if (!selectedAbandon.isEmpty()) {
           ModMessages.sendToServer(new PacketC2SPlotAction(
                   PlotAction.Action.ABANDON, new ArrayList<>(selectedAbandon), dim, null));
       }
   }

   /** 判断玩家是否首次购买（已购区块为 0） */
   private boolean isPlayerFirstPurchase() {
       for (var entry : PlotClientCache.entrySet()) {
           if (entry.getValue().isMine()) return false;
       }
       return true;
   }
   ```

4. **新建 `RegionNameInputScreen`**：
   ```java
   public class RegionNameInputScreen extends Screen {
       private final Consumer<String> onConfirm;
       private final Runnable onCancel;
       private EditBox nameInput;

       // 简单界面：标题 + 输入框 + 确认/取消按钮
       // 限制名称长度 2-32 字符
       // 确认时调用 onConfirm.accept(name)，取消时调用 onCancel.run()
   }
   ```

5. **第三方地图中的命名流程**：在 `MapSelectionConfirmScreen` 中也应用相同的逻辑，勾选 `isPlayerFirstPurchase()` 后先弹出命名界面再发送。

---

## 三、修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `client/plot/PlotMapScreen.java` | 修改 | 中键拖拽 + DynMap 地形渲染 + 首次购买命名流程 |
| `client/plot/PlotMapTerrainRenderer.java` | **新增** | 真实方块颜色采样与缓存 |
| `client/plot/PlotMapView.java` | 无需修改 | 现有 API 已足够 |
| `client/plot/PlotClientCache.java` | 修改 | 添加 `entrySet()` 遍历方法（用于判断是否首次购买） |
| `client/ClientKeyState.java` | 无需修改 | 中键拖拽通过 mouseDragged 直接处理 |
| `client/ClientModEvents.java` | 修改 | 初始化 `MapIntegrationManager` |
| `client/ClientPacketReceivers.java` | 无需修改 | 现有逻辑已足够 |
| `client/gui/RegionNameInputScreen.java` | **新增** | 区域命名输入 GUI |
| `client/integration/MapIntegrationManager.java` | **新增** | 第三方地图集成管理器 |
| `client/integration/IMapIntegration.java` | **新增** | 集成接口 |
| `client/integration/JourneyMapIntegration.java` | **新增** | JourneyMap 集成 |
| `client/integration/XaeroMinimapIntegration.java` | **新增** | Xaero's Minimap 集成 |
| `client/integration/XaeroWorldMapIntegration.java` | **新增** | Xaero's World Map 集成 |
| `client/integration/MapSelectionConfirmScreen.java` | **新增** | 第三方地图中选框确认 GUI |
| `network/PacketC2SPlotAction.java` | 修改 | 新增可选 `regionName` 字段 |
| `plot/PlotService.java` | 修改 | 使用传入的 `regionName` |
| `ModConfig.java` | 修改 | 新增第三方地图集成配置项 |

---

## 四、假设与决策

1. **JourneyMap API 可用性**：假设 JourneyMap 1.20.1 版本提供 `journeymap-api` 依赖，包含 `MapOverlay`、`Fullscreen` 事件等接口。如果 API 不可用，JourneyMap 集成降级为纯反射方案。

2. **Xaero's 模组反射方案**：Xaero's Minimap 和 World Map 不提供公开 API，将通过反射注入其渲染管线。使用 `ObfuscationReflectionHelper` 或 `AccessTransformer` 获取私有字段。

3. **地块数据共享**：第三方地图集成中的边界渲染直接读取 `PlotClientCache`，与 `PlotMapScreen` 共享同一数据源，无需额外网络请求。

4. **选框确认对话框**：第三方地图的确认对话框（`MapSelectionConfirmScreen`）是独立 Screen，与 `PlotMapScreen` 无依赖关系，避免同时打开两个 Screen 导致层级问题。

5. **DynMap 渲染性能**：地形颜色采样在 `cellSize >= 16` 时启用；当缩放到更小时（`cellSize < 16`），回退到原有纯色方案，避免对 16x16 采样做无意义的精细渲染。

6. **首次购买判定**：在客户端通过 `PlotClientCache` 中是否有 `isMine()==true` 的条目判定。如果缓存为空（刚进入地图），默认视为首次购买，要求命名。

---

## 五、验证步骤

1. **鼠标中键平移**：
   - 打开 `/land map` 进入地块视图
   - 按住鼠标中键拖拽，验证视角平滑移动
   - 验证中键拖拽不触发左键/右键框选
   - 验证视角移动后自动请求新区块数据

2. **DynMap 地形渲染**：
   - 进入地块视图，验证已加载区块显示真实地形颜色
   - 缩放到 cellSize=8 时验证回退到纯色方案
   - 缩放到 cellSize=64 时验证地形颜色细节
   - 平移视角到未加载区块，验证回退到纯色
   - 退出地块视图，验证地形缓存被清除

3. **第三方地图集成**：
   - 不安装任何地图模组时，验证不影响原有功能
   - 安装 JourneyMap 时，验证 minimap 和全屏地图显示区域边界
   - 在 JourneyMap 全屏中 Ctrl+左键框选，验证确认对话框弹出
   - 确认后验证区块被成功购买
   - 安装 Xaero's Minimap 时，验证 minimap 显示区域边界
   - 安装 Xaero's World Map 时，验证全屏地图显示边界 + 选框购买

4. **首次购买命名**：
   - 新玩家首次进入地块视图，选择区块后回车
   - 验证弹出命名输入框
   - 输入名称并确认，验证购买成功且区域使用该名称
   - 再次购买区块，验证不再弹出命名输入框
   - 取消命名，验证回到地块视图且未发送购买请求

---

## 六、实施顺序

建议按以下顺序实施，每步完成后再进行下一步：

1. **优化1：鼠标中键移动**（最简单，改动最小）
2. **改进2：首次购买命名**（涉及网络包修改，需同步客户端和服务端）
3. **改进1：DynMap 地形渲染**（纯客户端渲染，无网络影响）
4. **新增1：第三方地图集成**（最复杂，需处理多个模组的反射/API）