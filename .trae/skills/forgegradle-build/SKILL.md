---
name: "forgegradle-build"
description: "Minecraft Forge 1.20.1 模组在低内存环境（4GB以下）构建指南。当 ModDevGradle（legacyForge）因 OOM 无法构建时，切换到 ForgeGradle。Invoke when Forge 1.20.1 mod build fails with OOM, decompile errors, or ForgeFlower crashes in low-memory sandbox environments."
---

# ForgeGradle 构建指南

## 问题背景

当 Minecraft Forge 1.20.1 模组项目使用 `net.neoforged.moddev.legacyforge`（ModDevGradle）插件时，构建过程需要 **反编译（decompile）Minecraft 源码**（通过 ForgeFlower 工具）。在 4GB 内存限制的沙箱环境中，ForgeFlower 需要约 3.2GB+ 内存，加上 Gradle Daemon 和 neoform-runtime 开销，总内存超过 cgroup 限制，导致反复被 OOM Killer 杀死。

## 解决方案

切换到 **ForgeGradle**（`net.minecraftforge.gradle`），它使用预编译的 **userdev 产物**，无需反编译 Minecraft，大幅降低内存消耗。

## 关键文件修改

### 1. `gradle/wrapper/gradle-wrapper.properties`

使用腾讯云镜像加速 Gradle 下载，确保 Gradle 版本 >= 8.5（兼容 Java 21+）：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.14.5-bin.zip
networkTimeout=120000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

### 2. `settings.gradle`

配置阿里云镜像加速 + ForgeGradle 和 Parchment 插件仓库：

```groovy
pluginManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        gradlePluginPortal()
        maven {
            name = 'MinecraftForge'
            url = 'https://maven.minecraftforge.net/'
        }
        maven { url 'https://maven.parchmentmc.org' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.5.0'
}
```

### 3. `build.gradle`

核心变更：将 `net.neoforged.moddev.legacyforge` 替换为 `net.minecraftforge.gradle`，添加 Parchment 映射支持：

```groovy
plugins {
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
    id 'org.parchmentmc.librarian.forgegradle' version '1.+'
}

minecraft {
    mappings channel: 'parchment', version: "${parchment_minecraft_version}-${parchment_mappings_version}-${minecraft_version}"
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory project.file('run')
            property 'forge.logging.markers', 'REGISTRIES'
            property 'forge.logging.console.level', 'debug'
            mods { "${mod_id}" { source sourceSets.main } }
        }
        client { property 'forge.enabledGameTestNamespaces', mod_id }
        server { property 'forge.enabledGameTestNamespaces', mod_id; args '--nogui' }
        gameTestServer { property 'forge.enabledGameTestNamespaces', mod_id }
        data {
            workingDirectory project.file('run-data')
            args '--mod', mod_id, '--all', '--output', file('src/generated/resources/'), '--existing', file('src/main/resources/')
        }
    }
}

dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
}

// processResources 中替换 mods.toml 和 pack.mcmeta 的变量占位符
tasks.named('processResources', ProcessResources).configure {
    var replaceProperties = [
        minecraft_version: minecraft_version,
        forge_version: forge_version,
        mod_id: mod_id, mod_name: mod_name, mod_version: mod_version,
        mod_authors: mod_authors, mod_description: mod_description,
    ]
    inputs.properties replaceProperties
    filesMatching(['META-INF/mods.toml', 'pack.mcmeta']) {
        expand replaceProperties + [project: project]
    }
}
```

### 4. `gradle.properties`

调整 JVM 参数（ForgeGradle 不需要大内存，512MB 足够）：

```properties
org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true

# 版本配置
minecraft_version=1.20.1
forge_version=47.1.3
parchment_minecraft_version=1.20.1
parchment_mappings_version=2023.09.03
```

## 构建命令

```bash
./gradlew build --no-daemon
```

构建产物位于 `build/libs/<mod_id>-<version>.jar`。

**注意**：如果构建环境有 HTTP 代理（如沙箱），需要在 `gradle.properties` 中配置：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=18080
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=18080
```

## 常见运行时崩溃修复

### PlotMapTerrainRenderer 并发修改异常

**现象**：打开地图地块界面时游戏崩溃，报 `ConcurrentModificationException`。

**原因**：`HashMap` 的 `keySet().iterator().remove()` 在渲染线程中非线程安全。

**修复**：使用 `Collections.synchronizedMap` + `LinkedHashMap` 的 `removeEldestEntry` 自动 LRU 淘汰：

```java
// 修复前（错误）
private static final Map<Long, int[]> TERRAIN_CACHE = new HashMap<>();
if (TERRAIN_CACHE.size() >= CACHE_MAX) {
    TERRAIN_CACHE.keySet().iterator().remove();  // 并发不安全
}

// 修复后（正确）
private static final Map<Long, int[]> TERRAIN_CACHE = Collections.synchronizedMap(
    new LinkedHashMap<>(CACHE_MAX + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
            return size() > CACHE_MAX;
        }
    });
```

**注意**：`LinkedHashMap` 构造函数的 `accessOrder=true` 参数让最近访问的条目排在最后，`removeEldestEntry` 自动淘汰最旧的条目，无需手动迭代删除。

## 构建时间对比

| 构建系统 | 耗时 | 结果 |
|----------|------|------|
| ModDevGradle (legacyForge) | 永远无法完成 | OOM 被杀 |
| ForgeGradle (首次) | ~8 分钟 | 成功 |
| ForgeGradle (增量) | ~14 秒 | 成功 |