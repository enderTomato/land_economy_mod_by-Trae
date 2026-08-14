#!/bin/bash
# 完整预下载 net.neoforged:neoform-runtime:1.0.24 所有文件
# 解决沙箱代理下 Gradle Apache HttpClient 与 maven.neoforged.net 的 CDN77 TLS 握手失败

set -e
PKG_DIR=/root/.m2/repository/net/neoforged/neoform-runtime/1.0.24
mkdir -p "$PKG_DIR"
BASE=https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/1.0.24

# 完整文件清单（从目录列表抓取）
FILES=(
    neoform-runtime-1.0.24.pom
    neoform-runtime-1.0.24.jar
    neoform-runtime-1.0.24.module
    neoform-runtime-1.0.24-all.jar
    neoform-runtime-1.0.24-sources.jar
    neoform-runtime-1.0.24-changelog.txt
)

echo "=== 下载 neoform-runtime 1.0.24 完整文件集 ==="
for f in "${FILES[@]}"; do
    if [ ! -s "$PKG_DIR/$f" ]; then
        echo "下载: $f"
        curl -sSL --connect-timeout 15 -o "$PKG_DIR/$f" "$BASE/$f" || echo "  跳过 $f"
    else
        echo "已存在: $f"
    fi
done

# 同时下载 sha1 校验文件
for f in "${FILES[@]}"; do
    if [ ! -s "$PKG_DIR/$f.sha1" ]; then
        curl -sSL --connect-timeout 15 -o "$PKG_DIR/$f.sha1" "$BASE/$f.sha1" 2>/dev/null || true
    fi
done

echo "=== 完成 ==="
ls -la "$PKG_DIR"
