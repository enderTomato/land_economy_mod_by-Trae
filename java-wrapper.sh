#!/bin/bash
# Wrapper for ForgeFlower: reduce -Xmx4G to -Xmx3500M to fit cgroup 4GB limit
REAL_JAVA="/root/.local/share/mise/installs/java/17.0.2/bin/java"
ARGS=()
for arg in "$@"; do
    if [[ "$arg" == "-Xmx4G" ]]; then
        ARGS+=("-Xmx3500M")
    else
        ARGS+=("$arg")
    fi
done
exec "$REAL_JAVA" "${ARGS[@]}"