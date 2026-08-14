#!/bin/bash
REAL_JAVA="/root/.local/share/mise/installs/java/17.0.2/bin/java.real"
IS_FORGEFLOWER=false
for arg in "$@"; do
    if [[ "$arg" == *"forgeflower"* ]]; then
        IS_FORGEFLOWER=true
        break
    fi
done
if $IS_FORGEFLOWER; then
    NEW_ARGS=()
    for arg in "$@"; do
        if [[ "$arg" != -Xmx* ]]; then
            NEW_ARGS+=("$arg")
        fi
    done
    exec "$REAL_JAVA" -Xmx2000M -XX:+UseSerialGC "${NEW_ARGS[@]}"
else
    exec "$REAL_JAVA" "$@"
fi