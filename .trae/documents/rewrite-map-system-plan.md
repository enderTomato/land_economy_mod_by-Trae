# 重写"地图地块"系统 — 自动切换第三方地图全屏

## 摘要

将 `/land map` 命令从打开自研 `PlotMapScreen`（性能差/卡顿）改为：检测 JourneyMap 或 Xaero's World Map 是否已安装，若已安装则自动打开对应模组的全屏地图（高性能），仅在两者都未安装时回退到 `PlotMapScreen`。

## 当前架构分析

### 现有流程
```
/land map 命令（服务端）
  → RegionCommandHandler.openMap()
    → 标记玩家 inPlotMode
    → 发送 PacketS2COpenScreen(PLOT_MAP)

客户端接收
  → ClientPacketReceivers.onOpenScreen()
    → mc.setScreen(new PlotMapScreen())  ← 始终创建自研地图
```

### 强制退出流程
```
服务端（受击/传送时）
  → RegionEventListener.forceExitPlotIfActive()
    → 发送 PacketS2CForceExitPlot

客户端
  → ClientPacketReceivers.onForceExit()
    → 检查 mc.screen instanceof PlotMapScreen
    → 关闭 screen
```

### 已有集成类（已实现边界渲染，无需修改）
- `JourneyMapIntegration` — RenderLevelStageEvent 边界渲染 + 静态鼠标处理方法
- `XaeroWorldMapIntegration` — RenderLevelStageEvent 边界渲染 + 静态鼠标处理方法
- `XaeroMinimapIntegration` — RenderLevelStageEvent 边界渲染（仅 minimap，不参与全屏切换）
- `MapBoundaryRenderer` — 共享边界渲染工具类
- `MapIntegrationManager` — 管理集成初始化

### 缺失部分
1. 没有"打开第三方地图全屏"的逻辑
2. 没有"在第三方地图全屏上捕获鼠标事件"的逻辑
3. 强制退出逻辑只处理了 `PlotMapScreen`，不处理第三方地图

---

## 修改计划

### 1. 新建 `MapOpener.java` — 地图打开决策器

**文件**: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/plot/MapOpener.java`

**职责**: 决定打开哪个地图全屏，并执行打开操作。

**逻辑**:
```
检查 JourneyMap 是否已加载
  → 是 → 通过反射打开 JourneyMap 全屏地图（journeymap.client.ui.fullscreen.Fullscreen）
  → 否 → 检查 Xaero's World Map 是否已加载
    → 是 → 通过反射打开 Xaero's World Map 全屏地图（xaero.map.gui.GuiMap）
    → 否 → 回退到 PlotMapScreen
```

**JourneyMap 全屏打开方式** (反射):
```java
Class<?> fullscreenClass = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");
// 尝试 stateManager 或直接 new Fullscreen()
// 典型方式：通过 Fullscreen.state() 或 new Fullscreen() 然后 setScreen
```

**Xaero's World Map 全屏打开方式** (反射):
```java
Class<?> guiMapClass = Class.forName("xaero.map.gui.GuiMap");
// 通过反射调用 KeyBindings 或直接 new GuiMap()
// 典型方式：利用 xaero.map.WorldMap 的 openMap 方法
```

### 2. 新建 `MapScreenEventHandler.java` — 第三方地图鼠标事件捕获

**文件**: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/integration/MapScreenEventHandler.java`

**职责**: 监听 Forge `ScreenEvent.MouseButtonPressed`、`ScreenEvent.MouseDragged`、`ScreenEvent.MouseReleased`，当检测到当前屏幕是 JourneyMap 全屏或 Xaero's World Map 全屏时，将鼠标事件转发给对应集成类的静态处理方法。

**实现**:
- 注册到 `MinecraftForge.EVENT_BUS`
- 在 `MouseButtonPressed` 中：判断当前 screen 类名是否为 `journeymap.client.ui.fullscreen.Fullscreen` 或 `xaero.map.gui.GuiMap`，如果是且 Ctrl 按下，则计算 blockX/blockZ 并调用 `JourneyMapIntegration.handleMouseClick()` 或 `XaeroWorldMapIntegration.handleMouseClick()`
- 在 `MouseDragged` 中：同样判断并转发
- 在 `MouseReleased` 中：同样判断并转发
- 需要在 `MapIntegrationManager.init()` 中初始化

**坐标转换**:
- 需要从屏幕坐标转换为世界坐标（blockX/blockZ），这需要知道地图的缩放/平移状态
- 对于 JourneyMap：通过反射获取 `Fullscreen` 的 `getMapState()` 等方法获取当前视图状态
- 对于 Xaero's World Map：通过反射获取 `GuiMap` 的相机/缩放状态

> **注意**: 如果坐标转换过于复杂，可以简化为：在第三方地图全屏中，Ctrl+点击直接以玩家所在区块为中心进行单选购买/放弃，而非框选。这降低了实现复杂度，同时保留了核心功能。

### 3. 修改 `ClientPacketReceivers.java` — 使用 MapOpener

**文件**: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/ClientPacketReceivers.java`

**变更**: `onOpenScreen()` 方法中，`PLOT_MAP` 分支改为调用 `MapOpener.openMap()`：

```java
case PLOT_MAP -> MapOpener.openMap();
```

同时需要更新 `onPlotChunkData()` 和 `onPlotActionResult()` 中对 `PlotMapScreen` 的 `instanceof` 检查 — 因为当使用第三方地图时，当前 screen 不是 `PlotMapScreen`，但仍需要刷新 `PlotClientCache`。

**简化**: 这两个方法中，`PlotClientCache` 的更新（`put`/`invalidate`）本身不依赖于 `PlotMapScreen`，只需要移除 `instanceof PlotMapScreen` 的条件分支中的 `PlotMapScreen` 特有调用（如 `clearSelection()`、`requestChunksIfNeeded()`）。对于第三方地图模式，这些操作由 `MapScreenEventHandler` 的鼠标事件处理。

### 4. 修改 `ClientPacketReceivers.java` — 更新强制退出逻辑

**文件**: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/ClientPacketReceivers.java`

**变更**: `onForceExit()` 方法中，需要处理第三方地图全屏的关闭：

```java
public static void onForceExit(PacketS2CForceExitPlot m, Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        
        // 关闭 PlotMapScreen
        if (screen instanceof PlotMapScreen pms) {
            pms.cancelAll();
        }
        
        // 关闭 JourneyMap 全屏
        if (screen != null && screen.getClass().getName().equals("journeymap.client.ui.fullscreen.Fullscreen")) {
            mc.setScreen(null);
        }
        
        // 关闭 Xaero's World Map 全屏
        if (screen != null && screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
            mc.setScreen(null);
        }
        
        // 原有逻辑：关闭本模组创建的 screen
        if (screen != null && screen.getClass().getName().startsWith("cn.autoforged.land_economy_mod_1783600667.client")) {
            mc.setScreen(null);
        }
        
        // ... 提示消息
    });
}
```

### 5. 修改 `MapIntegrationManager.java` — 初始化 MapScreenEventHandler

**文件**: `src/main/java/cn/autoforged/land_economy_mod_1783600667/client/integration/MapIntegrationManager.java`

**变更**: 在 `init()` 方法末尾，当 JourneyMap 或 Xaero's World Map 集成被激活时，初始化 `MapScreenEventHandler`：

```java
if (!ACTIVE.isEmpty()) {
    MapScreenEventHandler.init();
}
```

### 6. 不需要修改的文件

以下文件保持不变，作为回退方案保留：
- `PlotMapScreen.java` — 仅在无第三方地图时使用
- `PlotMapView.java` — 同上
- `PlotMapTerrainImage.java` — 同上
- `PlotMapTerrainRenderer.java` — 同上
- `MapBoundaryRenderer.java` — 已被所有集成使用，无需修改
- `JourneyMapIntegration.java` — 已完善
- `XaeroWorldMapIntegration.java` — 已完善
- `XaeroMinimapIntegration.java` — 已完善
- `IMapIntegration.java` — 接口无需修改
- `MapSelectionConfirmScreen.java` — 确认弹窗，无需修改

---

## 假设与决策

1. **坐标转换简化**: 第三方地图的屏幕坐标→世界坐标转换涉及该地图模组的内部状态（缩放/平移/相机），通过反射获取较复杂。**决策**: 在第三方地图全屏中，Ctrl+点击直接以玩家所在区块为中心执行单选操作（而非框选），避免复杂的坐标转换。未来可增强为完整框选。

2. **JourneyMap 全屏类名**: 假设为 `journeymap.client.ui.fullscreen.Fullscreen`（需构建时验证）

3. **Xaero's World Map 全屏类名**: 假设为 `xaero.map.gui.GuiMap`（需构建时验证）

4. **集成配置**: 使用现有的 `plotMapIntegrationEnabled` 配置项控制是否启用自动切换。若该配置为 `false`，始终使用 `PlotMapScreen`。

5. **MapOpener 优先顺序**: JourneyMap > Xaero's World Map > PlotMapScreen（回退）

---

## 验证步骤

1. 构建: `./gradlew build --no-daemon`
2. 验证产物: `ls -lh build/libs/`
3. 检查编译无错误