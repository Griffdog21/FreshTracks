#!/usr/bin/env bash
# Dev loop helpers for play testing through the dev bridge (127.0.0.1:25599).
#   tools/dev/client.sh start   build + launch the dev client, wait until the bridge answers
#   tools/dev/client.sh stop    ask the client to quit (saves the world), then make sure it is gone
#   tools/dev/client.sh restart
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# JAVA_HOME (JDK 25) and PY (python) come from your environment, or from tools/dev/local.env if you make one
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
PY="${PY:-python}"
BRIDGE=http://127.0.0.1:25599

wait_bridge() {
  for i in $(seq 1 180); do
    if curl -s -m 2 "$BRIDGE/state" >/dev/null 2>&1; then echo "bridge up after ${i}s"; return 0; fi
    if grep -q "BUILD FAILED\|---- Minecraft Crash Report" "$ROOT/run/client-console.log" 2>/dev/null; then
      echo "client failed:"; grep -A20 "BUILD FAILED\|error:\|Crash Report" "$ROOT/run/client-console.log" | head -60; return 1
    fi
    sleep 1
  done
  echo "timed out waiting for bridge"; return 1
}

client_pids() {
  powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*fabric.dli.env=client*' -and \$_.CommandLine -like '*FreshTracks*' } | ForEach-Object { \$_.ProcessId }" | tr -d '\r'
}

case "${1:-}" in
  start)
    cd "$ROOT" && (./gradlew --no-daemon runClient > run/client-console.log 2>&1 &)
    wait_bridge ;;
  stop)
    curl -s -m 5 "$BRIDGE/quit" >/dev/null 2>&1
    for i in $(seq 1 30); do [ -z "$(client_pids)" ] && { echo stopped; exit 0; }; sleep 1; done
    for pid in $(client_pids); do powershell.exe -NoProfile -Command "Stop-Process -Id $pid -Force"; echo "killed $pid"; done ;;
  restart)
    "$0" stop; "$0" start ;;
  *) echo "usage: $0 start|stop|restart" ;;
esac
