# Minecraft Forge 1.20.1 模组打包实战指南（4GB 内存受限环境）

> 适用范围：Minecraft 1.20.1 + Forge 47.1.x + ModDevGradle Legacy（`net.neoforged.moddev.legacyforge`）+ Java 17，在 4GB cgroup 硬限制的容器/CI 环境中完成从源码到 mod jar 的全流程构建。

---

## 1. 环境检查（必须通过，跳过必踩坑）

### 1.1 JDK 17 确认
Minecraft 1.20.1 强制要求 Java 17。CI 环境下 `JAVA_HOME` 常指向 Java 21/25，需显式覆盖：

```bash
# 检查可用 JDK 17
ls /root/.local/share/mise/installs/java/17.0.2/bin/java
/root/.local/share/mise/installs/java/17.0.2/bin/java -version
# 期望输出：openjdk 17.0.x（非 21/25）
```

执行 gradlew 前必须设置环境变量：
```bash
export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export PATH=$JAVA_HOME/bin:$PATH
```

### 1.2 cgroup 内存硬限制
这决定后续是否能「一键构建」还是必须「分步构建」：

```bash
cat /sys/fs/cgroup/memory.max
# 4294967296 (=4GB) → 必须分步
# 8589934592 (=8GB) → 可直接 gradlew build
```

### 1.3 清理残留 Java 进程
残留的 Gradle daemon / ForgeFlower 会竞争内存：

```bash
pkill -9 -f gradle
pkill -9 -f forgeflower
pkill -9 -f neoform-runtime
sleep 2
ps aux | grep java | grep -v grep | wc -l   # 期望 0
free -m   # 期望 available ≥ 5GB（含 buff/cache）
```

---

## 2. 构建配置预检查

### 2.1 gradle/wrapper/gradle-wrapper.properties（国内镜像）
```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.14.5-bin.zip
# 阿里云备选：https\://maven.aliyun.com/repository/gradle/gradle-8.14.5-bin.zip
```

### 2.2 gradle.properties（关键：压 Gradle daemon 内存）
```properties
# 注意：-Xmx 不超过 256m，4GB 环境中 256m 是上限
org.gradle.jvmargs=-Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false   # 禁止：ModDevGradle 不兼容！
```

> 沙箱代理设置（CI 环境必填，否则依赖解析失败）：
> ```properties
> systemProp.http.proxyHost=127.0.0.1
> systemProp.http.proxyPort=18080
> systemProp.https.proxyHost=127.0.0.1
> systemProp.https.proxyPort=18080
> systemProp.https.protocols=TLSv1.2,TLSv1.3
> ```

### 2.3 build.gradle repositories（国内镜像优先）
```groovy
repositories {
    maven { url 'https://maven.aliyun.com/repository/public' }
    maven { url 'https://repo.huaweicloud.com/repository/maven' }
    maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-public' }
    mavenCentral()
    maven { url 'https://maven.minecraftforge.net' }
    maven { url 'https://maven.parchmentmc.org' }
}
```

---

## 3. 4GB 内存核心策略：手动 nfrt + gradlew 两步走

### 3.1 为什么不能直接 `gradlew build`？
ModDevGradle 的 `createMinecraftArtifacts` 任务内部调用 ForgeFlower 反编译 Minecraft 源码，**默认申请 `-Xmx3700m` 堆**。叠加 Gradle daemon 占的 250MB，合计接近 4GB 限制，任何微小波动都会触发 cgroup OOM Killer：

```
dmesg | tail -3
# Memory cgroup out of memory: Killed process XXXX (java) anon-rss:3663240kB
```

### 3.2 分步方案
- **第 1 步**：手动启动 nfrt（自身只占 64~1024MB）执行 createMinecraftArtifacts，让 ForgeFlower 独享 3700m
- **第 2 步**：ForgeFlower 产物全部缓存后，运行 `gradlew build`，此时 createMinecraftArtifacts 全部 UP-TO-DATE

### 3.3 第 1 步：手动运行 nfrt（分三轮，依 OOM 点调整）

#### 准备：校验路径
把 `<项目根>` 替换为实际路径（本仓库是 `/workspace`），确认以下文件存在：
```bash
ls /root/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java            # nfrt 需 Java 21
ls /root/.local/share/mise/installs/java/17.0.2/bin/java                     # 子进程需 Java 17
find /root/.gradle/caches -name "parchment-1.20.1-2023.09.03.zip"           # parchment
find /root/.gradle/caches -name "neoform-runtime-1.0.24-all.jar"            # nfrt jar
mkdir -p <项目根>/build/moddev/artifacts <项目根>/build/tmp/createMinecraftArtifacts
```

#### 1-A：首轮（主跑 decompile，nfrt 自己 64m）
目标：让 ForgeFlower 拿 3700m 不被 OOM Killer 杀。

```bash
/root/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java \
  -Xmx64m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=18080 \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=18080 \
  -Dhttps.protocols=TLSv1.2,TLSv1.3 \
  -jar /root/.gradle/caches/modules-2/files-2.1/net.neoforged/neoform-runtime/1.0.24/c4f091f75fdf76506a31e00bbabc4646030ab20a/neoform-runtime-1.0.24-all.jar \
  --home-dir /root/.gradle/caches/neoformruntime \
  --work-dir <项目根>/build/tmp/neoformruntime \
  run \
  --java-executable /root/.local/share/mise/installs/java/17.0.2/bin/java \
  --parchment-data <parchment-zip完整路径> \
  --parchment-conflict-prefix p_ \
  --neoforge net.minecraftforge:forge:1.20.1-47.1.3:userdev \
  --dist joined \
  --write-result namedToIntermediaryMapping:<项目根>/build/moddev/artifacts/namedToIntermediate.tsrg \
  --write-result intermediaryToNamedMapping:<项目根>/build/moddev/artifacts/intermediateToNamed.srg \
  --write-result csvMapping:<项目根>/build/moddev/artifacts/intermediateToNamed.zip \
  --write-result clientResources:<项目根>/build/moddev/artifacts/client-extra-1.20.1-47.1.3.jar \
  --write-result compiledWithNeoForge:<项目根>/build/moddev/artifacts/forge-1.20.1-47.1.3.jar \
  --write-result sourcesWithNeoForge:<项目根>/build/moddev/artifacts/forge-1.20.1-47.1.3-sources.jar \
  --write-result sourcesAndCompiledWithNeoForge:<项目根>/build/moddev/artifacts/forge-1.20.1-47.1.3-merged.jar \
  --problems-report <项目根>/build/tmp/createMinecraftArtifacts/nfrt-problem-report.json \
  --artifact-manifest <项目根>/build/tmp/createMinecraftArtifacts/nfrt_artifact_manifest.properties \
  --warn-on-artifact-manifest-miss
```

监控（另一个 shell）：
```bash
watch -n 3 "
  ps aux | grep forgeflower | grep -v grep | awk '{print \"ForgeFlower RSS:\",\$6/1024\"MB\"}'
  echo cgroup current: \$(cat /sys/fs/cgroup/memory.current) bytes
  echo '--- OOM last ---'
  dmesg 2>/dev/null | grep 'killed process' | tail -1
"
```
- 安全区间：cgroup memory.current **< 4050000000**（3.77GB）
- 若出现 `dmesg killed process` → 需减小 ForgeFlower 堆，见 3.4
- 若 decompile 内部 `OutOfMemoryError`（但 output.jar 正常）→ 个别类反编译失败，不影响编译通过，可继续

#### 1-B：二轮（decompile 已缓存 → 跑到 recompile，nfrt 自己 256m）
若 1-A 在 `remapSrgSourcesToOfficial` 或 `applyParchment` 报 `String.split OOM`，把 nfrt 堆升到 256m 再跑一次：

```bash
/root/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java \
  -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC \
  ... <其余参数和 1-A 完全相同> ...
```

#### 1-C：三轮（跑 recompile，nfrt 自己 1024m）
若 recompile 编译 5400 个 Java 文件时报 javac 内部 OOM：

```bash
/root/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java \
  -Xmx1024m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC \
  ... <其余参数和 1-A 完全相同> ...
```

> 此时 decompile/inject/patch/transformSources/remap/applyParchment **全部走缓存**（`♻ Used cache of XXX`），不会再触发 ForgeFlower 大内存。recompile 成功后整个 nfrt 流程完成。

#### nfrt 成功验收标准
```bash
# 退出码 0，控制台末尾显示 Total runtime: XXs
# artifacts 目录必须至少包含：
ls -la <项目根>/build/moddev/artifacts/
# forge-1.20.1-47.1.3.jar ≈ 19MB
# forge-1.20.1-47.1.3-merged.jar ≈ 27MB
# client-extra-*.jar ≈ 8MB
# intermediateToNamed.srg ≈ 22MB

# 验证 forge jar 完整性：
jar tf <项目根>/build/moddev/artifacts/forge-1.20.1-47.1.3.jar | wc -l
# 期望 ≥ 10000 个条目；过少说明 recompile 未完整执行
```

### 3.4 可选：调 MCP config 中的 ForgeFlower 堆
若 3700m 仍频繁触发 cgroup OOM，修改 mcp_config zip 中 `config.json` 的 `functions.decompile.jvmargs`：

```bash
# 解压
cd /tmp && rm -rf mcp_cfg && mkdir mcp_cfg && cd mcp_cfg
ZIP=/root/.gradle/caches/neoformruntime/artifacts/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip
unzip -o $ZIP

# 编辑 functions.decompile.jvmargs（注意：只能改 functions，不能改 steps）
python3 <<'PY'
import json
d=json.load(open('config.json'))
d['functions']['decompile']['jvmargs'] = [
    '-Xmx3400m',
    '-XX:ActiveProcessorCount=1',
    '-XX:+UseSerialGC',
    '-XX:MaxMetaspaceSize=256m'
]
json.dump(d, open('config.json','w'), indent=4)
print(d['functions']['decompile']['jvmargs'])
PY

# 写回 zip
zip -r $ZIP config.json
# 清 decompile 缓存（必须）
rm -rf <项目根>/build/tmp/neoformruntime/*_decompile
rm -f /root/.gradle/caches/neoformruntime/intermediate_results/decompile_*
```

> ❌ **反模式**：不要改 `config.json` 中 `steps.joined[].decompile` 内加 args/jvmargs。正确位置是顶层 `functions.decompile`。错改 steps 会引发 `Array must have size 1`（Gson schema 硬校验）。

---

## 4. 第 2 步：gradlew build（源码编译 + 打包）

```bash
cd <项目根>
export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export PATH=$JAVA_HOME/bin:$PATH
./gradlew build --no-daemon --console=plain
```

期望输出：
```
> Task :createMinecraftArtifacts UP-TO-DATE    ← 或全部步骤 ♻ Used cache
> Task :compileJava                             ← 无错误
> Task :jar
> Task :reobfJar
> Task :build
BUILD SUCCESSFUL
```

---

## 5. 高频编译错误（compileJava 阶段）及修复

| 错误信息 | 根因 | 修复 |
|---|---|---|
| `name clash: X(List<A>,i,i,i,i) and X(List<B>,i,i,i,i) have the same erasure` | 两个构造函数泛型 `List<T>` 在字节码层面擦除后完全相同 | 将其中一个参数类型从 `List<T>` 改为 `Collection<T>`。调用处无需改 |
| `cannot find symbol: variable isClientSide` | 1.20.1 中 `LevelAccessor` / `Level` 的客户端判断是**方法**，不是字段 | 全文替换：`.isClientSide` → `.isClientSide()` |
| `incompatible types: M cannot be converted to PacketTarget at SimpleChannel.send(msg, target)` | Forge `SimpleChannel.send()` 第一个参数是 PacketTarget，第二个是消息，写反了 | `INSTANCE.send(msg, target)` → `INSTANCE.send(target, msg)`。加 `@SuppressWarnings({"rawtypes"})`，并显式写 `PacketDistributor.PacketTarget target = ...` |
| `package net.minecraft.xxx does not exist` / `cannot find symbol: ServerPlayer` | forge jar 未生成。**99% 是 createMinecraftArtifacts 失败** | 回到第 3 步确认 nfrt 完整跑通，`forge-1.20.1-47.1.3.jar` 存在且 ≥ 18MB |

---

## 6. 构建成功验收

```bash
# 6.1 定位 jar
ls -la <项目根>/build/libs/
# 输出示例：land_economy_mod-1.8.0.jar（178KB）

# 6.2 验证 jar 结构
jar tf build/libs/<name>.jar
# 必须包含：
#   META-INF/MANIFEST.MF
#   META-INF/mods.toml
#   assets/<modId>/lang/*.json
#   <你的顶层包>/**/*.class

# 6.3 核对版本
jar xf build/libs/<name>.jar META-INF/mods.toml
grep -E '^version=' META-INF/mods.toml   # 应与 gradle.properties mod_version 一致
rm -rf META-INF
```

---

## 7. 故障排查速查表

| 症状 | 99% 原因 | 解决 |
|---|---|---|
| `dmesg` 出现 `Memory cgroup out of memory: Killed process XXX (java)` | ForgeFlower 3700m + Gradle daemon ≥ 4GB | 立刻切「手动 nfrt 分步」，杀掉 daemon 后重跑 |
| decompile `output.jar` 报 `zip END header not found` 且大小 < 100KB | ForgeFlower 写 jar 中途被 cgroup kill（OOM） | 同上，或把 ForgeFlower 堆降到 3400m |
| decompile console 末尾有 `java.lang.OutOfMemoryError` in `Blocks` 类 | ForgeFlower 自己 3000m 堆不够 | 调 `functions.decompile.jvmargs` 的 `-Xmx` 到 3500m+ |
| `NodeExecutionException: remapSrgSourcesToOfficial failed` + `String.split` OOM | nfrt 自身堆不足 | nfrt -Xmx 从 64m → 256m |
| `NodeExecutionException: recompile failed` + `Compilation failed`（无具体语法错） | javac 编译 5441 文件堆不够 | nfrt -Xmx 升到 1024m |
| `java.lang.UnsupportedClassVersionError` 启动 nfrt jar | 用 Java 17 运行 nfrt 了（nfrt 本身要 Java 21） | 执行 nfrt jar 用 `/root/.gradle/jdks/eclipse_adoptium-21/.../bin/java`，`--java-executable` 指向 Java 17（子进程） |
| `JsonSyntaxException: Array must have size 1, but has size 16` | 错误地在 `steps.*.decompile` 里加了 `args: [...]`/`jvmargs: [...]` | 恢复 steps 结构，只能改 `functions.decompile` |
| gradle wrapper 下载失败 / 依赖解析超时 / `TLS 握手失败` | 未走代理或镜像 | 确认 systemProp.https.proxyHost=127.0.0.1，build.gradle repositories 开头有国内 Maven 镜像 |
| `No such property: createMinecraftArtifacts for task set` | 使用了 `net.minecraftforge.gradle`（ForgeGradle v5）而非 ModDevGradle Legacy | build.gradle 应为 `id 'net.neoforged.moddev.legacyforge' version '2.0.91'` |

---

## 8. 反模式（严禁做）

1. ❌ 不要妄想在 4GB 环境直接 `gradlew build` 成功——概率为 0
2. ❌ 不要改 `steps.*.decompile` 结构加 args/jvmargs（schema 错误）
3. ❌ 不要打开 `org.gradle.configuration-cache=true`（ModDevGradle 不兼容）
4. ❌ 不要先 `gradlew build` 失败了再跑 nfrt——Gradle daemon 残留占内存，必须先 `pkill -9 -f gradle`
5. ❌ 不要用系统 `gradle` 命令——必须用 `./gradlew`（保证 wrapper 版本、镜像配置生效）
6. ❌ 不要把 nfrt 的 `--java-executable` 指向 Java 21（反编译出的 Minecraft 源码是 Java 17 target，javac 版本错会报不兼容）

---

## 9. 构建产出清单（成功交付物）

| 路径 | 大小参考 | 说明 |
|---|---|---|
| `build/libs/<archivesName>-<version>.jar` | ~150–500KB | 最终交付给玩家/服主的模组 jar |
| `build/moddev/artifacts/forge-1.20.1-47.1.3.jar` | ~19MB | 本地 Maven 模拟的 Forge 产物（compile classpath）|
| `build/moddev/artifacts/forge-1.20.1-47.1.3-sources.jar` | ~8MB | Forge + Minecraft 反编译源码 |
| `build/moddev/artifacts/forge-1.20.1-47.1.3-merged.jar` | ~27MB | 源码+编译合并 jar |

---

## 10. 本次实战数据（供参考）

| 步骤 | 时间 | 峰值 RSS |
|---|---|---|
| nfrt（decompile -Xmx3700m） | ~238s | 3826MB |
| nfrt（inject） | ~27s | — |
| nfrt（applyParchment） | ~42s | — |
| nfrt（recompile 5441 源文件） | ~35s | — |
| gradlew build（compileJava 到 jar） | ~27s | < 1GB |
