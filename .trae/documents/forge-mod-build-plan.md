# Forge 模组打包计划：领地经济 (Land Economy) v1.8.0

## 项目理解摘要

| 项目 | 值 |
|------|------|
| 模组名称 | 领地经济 (Land Economy) |
| Mod ID | `land_economy_mod_1783600667` |
| 版本 | 1.8.0 |
| Minecraft | 1.20.1 |
| Forge | 47.1.3 |
| Java | JDK 17 (toolchain 指定) |
| Gradle | 8.14.4 (wrapper, 阿里云镜像) |
| 构建插件 | `net.neoforged.moddev.legacyforge` 2.0.91 |
| Parchment | 2023.09.03 for 1.20.1 |
| 产出 JAR | `land_economy_mod-1.8.0.jar` |

**功能概述**：该模组是一个 Minecraft 服务器端领地经济系统，提供领地创建/管理、GDP 计算（基于区块内物品扫描）、人口增长模拟、银行存/取款、产业分类（采掘/农业/林业/渔业/制造/冶炼/建筑）、领地权限管理等。共约 12 个 Java 源文件，无外部模组依赖。

**关键构建配置**：
- `mods.toml` 模板位于 `src/main/templates/META-INF/mods.toml`，通过 `generateModMetadata` 任务做变量替换后输出到 `build/generated/sources/modMetadata`
- 使用 NeoForge ModDevGradle Legacy 插件，不依赖传统 ForgeGradle
- 无 `reobfJar` 任务（由插件自动处理混淆）
- 阿里云 Gradle 镜像已配置在 `gradle-wrapper.properties`

## 当前状态分析

### 发现的问题
1. **JDK 版本不匹配**：`gradle.properties` 中 `org.gradle.java.installations.paths=/usr/lib/jvm/java-21-openjdk-amd64` 指向 JDK 21，但 `build.gradle` 第 29 行指定 `JavaLanguageVersion.of(17)`
2. **JVM 参数需调整**：`gradle.properties` 中 `org.gradle.jvmargs=-Xmx3G`，需改为 `-Xmx4G -XX:+UseG1GC -XX:MaxMetaspaceSize=1G`
3. **Daemon 和 Parallel 需关闭**：当前 `org.gradle.daemon=true`、`org.gradle.parallel=true`，需改为 `false`
4. **无 `reobfJar` 任务**：NeoForge ModDevGradle Legacy 插件不使用 `reobfJar`，直接 `jar` 或 `build` 即可产出混淆后的 JAR

## 执行步骤

### 步骤 1：检查 Java 环境
- 检查系统已安装的 Java 版本（`/usr/lib/jvm/` 下）
- 记录各版本路径
- 若 JDK 17 不存在，检查 SDKMAN 等沙箱工具
- 若均无，从国内镜像下载 JDK 17

### 步骤 2：配置 gradle.properties
修改 `/workspace/gradle.properties`：
- `org.gradle.jvmargs` → `-Xmx4G -XX:+UseG1GC -XX:MaxMetaspaceSize=1G`
- `org.gradle.daemon` → `false`
- `org.gradle.parallel` → `false`
- `org.gradle.java.installations.paths` → 指向 JDK 17 路径

### 步骤 3：设置 JAVA_HOME
- 设置 `JAVA_HOME` 环境变量指向 JDK 17
- 验证 `java -version` 输出

### 步骤 4：清理旧缓存
- 运行 `./gradlew clean --no-daemon`

### 步骤 5：分步构建
由于 NeoForge ModDevGradle 不使用 `reobfJar`，改为：
1. `./gradlew compileJava --no-daemon --console=plain` - 编译 Java
2. `./gradlew processResources --no-daemon --console=plain` - 处理资源
3. `./gradlew jar --no-daemon --console=plain` - 打包 JAR

每步若超 10 分钟无日志输出，终止并加 `--info` 重试。

### 步骤 6：验证 JAR 输出
- 确认 `build/libs/land_economy_mod-1.8.0.jar` 存在
- 检查 JAR 内容：`META-INF/mods.toml`、核心 class、资源文件
- 输出 JAR 绝对路径和文件大小

## 假设与决策
- **假设 JDK 17 可能未安装**，需准备从国内镜像下载的方案
- **不使用 `--offline`**：首次构建需要下载 Forge 依赖（约数百 MB），离线模式必然失败
- **不使用 `build` 任务**（会触发 test 等），仅执行 `compileJava` + `processResources` + `jar`
- **不启动客户端/服务端测试**，仅完成打包

## 验证步骤
1. `java -version` 确认 JDK 17
2. `./gradlew clean` 成功
3. `./gradlew compileJava` 编译通过
4. `./gradlew processResources` 资源处理成功
5. `./gradlew jar` 产出 JAR
6. `jar tf build/libs/land_economy_mod-1.8.0.jar | head -50` 检查内容
7. `ls -lh build/libs/land_economy_mod-1.8.0.jar` 确认文件大小