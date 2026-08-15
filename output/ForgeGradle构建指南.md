# ForgeGradle 构建成功总结

## 问题背景

项目原本使用 `net.neoforged.moddev.legacyforge`（ModDevGradle）插件，该插件在构建时需要 **反编译（decompile）Minecraft 源码**，通过 ForgeFlower 工具完成。在 4GB 内存限制的沙箱环境中，ForgeFlower 反编译器需要约 3.2GB+ 内存，加上 Gradle Daemon 和 neoform-runtime 的开销，总内存超过 4GB cgroup 限制，导致反复被 OOM Killer 杀死，构建无法完成。

## 解决方案

切换到 **ForgeGradle**（`net.minecraftforge.gradle`），它使用预编译的 **userdev 产物**（`forge-1.20.1-47.1.3-userdev.jar`），无需反编译 Minecraft，大幅降低内存消耗和构建时间。

## 关键文件修改

### 1. `gradle/wrapper/gradle-wrapper.properties`

使用腾讯云镜像加速 Gradle 下载：

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

配置阿里云镜像 + ForgeGradle 插件仓库：

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
```

### 3. `build.gradle`

核心变更：将 `net.neoforged.moddev.legacyforge` 替换为 `net.minecraftforge.gradle`，并添加 Parchment 映射支持：

```groovy
plugins {
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
    id 'org.parchmentmc.librarian.forgegradle' version '1.+'
}

minecraft {
    mappings channel: 'parchment', version: "${parchment_minecraft_version}-${parchment_mappings_version}-${minecraft_version}"
    // ...
}

dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
}
```

### 4. `gradle.properties`

调整 JVM 参数（ForgeGradle 不需要大内存）：

```properties
org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true

# 沙箱代理
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=18080
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=18080

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

构建产物位于 `build/libs/land_economy_mod-1.8.0.jar`。

## 运行时崩溃修复

模组在游戏中打开地图地块界面时崩溃，原因是 `PlotMapTerrainRenderer` 的 `HashMap` 缓存 LRU 淘汰逻辑存在并发问题：

```java
// 错误写法（HashMap 非线程安全）
TERRAIN_CACHE.keySet().iterator().remove();  // ConcurrentModificationException
```

修复为线程安全的 `LinkedHashMap` + `removeEldestEntry` 自动淘汰：

```java
private static final Map<Long, int[]> TERRAIN_CACHE = Collections.synchronizedMap(
    new LinkedHashMap<>(CACHE_MAX + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
            return size() > CACHE_MAX;
        }
    });
```

## 构建时间对比

| 构建系统 | 耗时 | 结果 |
|----------|------|------|
| ModDevGradle | 永远无法完成 | OOM 被杀 |
| ForgeGradle | ~8 分钟（首次）/ ~14 秒（增量） | 成功 |