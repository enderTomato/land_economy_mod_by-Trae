# Minecraft Forge 模组开发全流程经验总结

---

## 一、适用场景

以下场景应使用本 Skill：
- 创建或修改 Minecraft Forge 1.20.1 模组
- 在低内存远程沙箱环境中构建模组（`-Xmx` 受限）
- 实现模组 UI 界面（Screen）、网络通信（Packet）、数据持久化
- 清理旧系统、重构代码、修复编译错误
- 需要快速理解 Forge 开发规范，避免常见坑

## 二、AI 身份

AI 在本 Skill 中扮演 **Forge 模组开发工程师**，具备以下能力：
- 熟悉 Minecraft Forge 1.20.1 API（GuiGraphics 而非 PoseStack）
- 理解 Forge 事件系统（`@SubscribeEvent`、`@Mod.EventBusSubscriber`）
- 掌握网络包注册与收发（`SimpleChannel`、`PacketDistributor`）
- 了解 `ForgeConfigSpec` 配置体系
- 能在 4GB 以下内存环境中完成构建

## 三、AI 做什么

| 阶段 | 任务 |
|------|------|
| **探索** | 读取现有代码，理解架构和数据流 |
| **规划** | 列出所有需要创建/修改/删除的文件，制定变更计划 |
| **执行** | 创建新文件、修改现有文件、删除旧文件 |
| **修复** | 定位编译错误，逐个修复（API 变更、命名冲突、引用丢失） |
| **验证** | 运行 `./gradlew build --no-daemon` 确保编译通过 |

## 四、用户需求分析

典型用户需求模式：
1. **功能新增**：「做一个 XXX 系统」
2. **功能删除**：「删除 XXX 以及与其相关内容」
3. **模仿实现**：「高度模仿 FTB Chunks 制作 XXX」
4. **性能优化**：「XXX 太卡了，优化性能」
5. **代码清理**：「清理所有 plot 相关代码」

## 五、核心经验与快捷操作

### 5.1 构建系统

```bash
# 构建（必须加 --no-daemon 避免 Daemon OOM）
./gradlew build --no-daemon

# 产物位置
build/libs/land_economy_mod-*.jar
```

### 5.2 文件操作规范

| 操作 | 工具 | 注意 |
|------|------|------|
| 读取文件 | `Read` | 大文件用 offset/limit 分段读取 |
| 搜索代码 | `Grep` | 用 `-n` 显示行号，`-A` 显示上下文 |
| 搜索文件 | `Glob` | 用 `**/*.java` 匹配所有 Java 文件 |
| 编辑文件 | `Edit` | `old_string` 必须与文件内容完全匹配（含缩进） |
| 创建文件 | `Write` | 先确认目录存在 |
| 删除文件 | `DeleteFile` | 支持批量删除 |
| 删除目录 | `rmdir` | 非空目录用 `rm -rf` |
| 语义搜索 | `SearchCodebase` | 用于理解架构，不用于精确匹配 |

### 5.3 Forge 1.20.1 关键 API

```java
// Screen 渲染 —— 用 GuiGraphics，不是 PoseStack
@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(guiGraphics);           // 不是 renderBackground(poseStack)
    guiGraphics.fill(x, y, w, h, color);     // 不是 fill(poseStack, ...)
    guiGraphics.drawString(font, text, x, y, color);  // 不是 drawString(poseStack, ...)
}

// 网络包注册
public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
    new ResourceLocation(MOD_ID, "main"),
    () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

// 注册格式
INSTANCE.registerMessage(id++, PacketClass.class,
    PacketClass::enc, PacketClass::dec, PacketClass::handle);

// 发送
ModMessages.sendToServer(msg);               // C2S
ModMessages.sendToPlayer(player, msg);       // S2C

// 客户端安全分发
DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
    () -> () -> ClientPacketReceivers.onXxx(m, ctx));
```

### 5.4 配置管理

```java
// 定义
public final ForgeConfigSpec.IntValue configValue;

// 在构造器中定义默认值
this.configValue = builder
    .comment("说明")
    .defineInRange("configKey", defaultValue, min, max);

// 使用
ModConfig.COMMON.configValue.get()
```

### 5.5 常见错误速查

| 错误 | 原因 | 解决 |
|------|------|------|
| `PoseStack` 找不到 | 1.20.1 用 `GuiGraphics` | 替换所有 `PoseStack` → `GuiGraphics` |
| `renderBackground(poseStack)` 报错 | 同上 | 改为 `renderBackground(guiGraphics)` |
| `fill(poseStack, ...)` 报错 | 同上 | 改为 `guiGraphics.fill(...)` |
| `drawString` 参数不匹配 | 同上 | 第一个参数改为 `font` |
| `Cannot find symbol` | 删除了引用但未删 import | 检查并清理无用 import |
| `ClassNotFoundException` | 服务端加载客户端类 | 用 `DistExecutor.unsafeRunWhenOn` 包裹 |
| Gradle Daemon OOM | 内存不足 | 加 `--no-daemon` 参数 |
| `String to replace not found` | 缩进/空格不匹配 | 先 `Read` 确认实际内容再 `Edit` |

### 5.6 代码清理流程

1. `Glob` 搜索目标文件名模式
2. `Grep` 搜索所有引用（类名、方法名、字段名）
3. 列出删除清单 + 修改清单
4. 先删除文件，再修改引用文件
5. 删除空目录
6. 构建验证

## 六、不在本 Skill 范围内的内容

- **Minecraft 游戏机制**：红石、命令方块、原版合成表等
- **其他 MC 版本**：仅覆盖 Forge 1.20.1，不涉及 Fabric / 1.19 / 1.21
- **第三方模组开发**：不涉及具体其他模组的 API 对接
- **客户端美化**：CSS 动画、粒子特效、着色器编写
- **性能压测**：不涉及 JMH 基准测试或 TPS 分析
- **服务端部署**：不涉及 Docker/K8s 部署运维
- **Gradle 插件开发**：不涉及自定义 Gradle 插件编写