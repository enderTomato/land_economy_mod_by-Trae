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
    INSERTED_THR=false
    INSERTED_LVT=false
    for arg in "$@"; do
        case "$arg" in
            -Xmx*) ;;
            -log=TRACE) NEW_ARGS+=("-log=WARN") ;;
            -log=DEBUG) NEW_ARGS+=("-log=WARN") ;;
            -log=INFO)  NEW_ARGS+=("-log=WARN") ;;
            -lvt=*) ;;
            *.jar)
                NEW_ARGS+=("$arg")
                if [[ "$INSERTED_THR" == "false" ]]; then
                    NEW_ARGS+=("-thr=1")
                    INSERTED_THR=true
                fi
                if [[ "$INSERTED_LVT" == "false" ]]; then
                    NEW_ARGS+=("-lvt=0")
                    INSERTED_LVT=true
                fi
                ;;
            *) NEW_ARGS+=("$arg") ;;
        esac
    done
    exec "$REAL_JAVA" \
        -Xmx2800M \
        -XX:+UseSerialGC \
        -XX:MaxMetaspaceSize=200M \
        -XX:SoftRefLRUPolicyMSPerMB=50 \
        -Djava.util.concurrent.ForkJoinPool.common.parallelism=1 \
        "${NEW_ARGS[@]}"
else
    exec "$REAL_JAVA" "$@"
fi
