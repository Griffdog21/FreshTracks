#!/usr/bin/env bash
# Two-client multiplayer test setup: host (run/, bridge 25599) opens FT Flat to LAN; "Friend" (run2/, bridge 25600)
# joins it.   tools/dev/mp.sh start|stop
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# JAVA_HOME (JDK 25) and PY (python) come from your environment, or from tools/dev/local.env if you make one
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
PY="${PY:-python}"
case "${1:-}" in
  start)
    "$ROOT/tools/dev/client.sh" start | tail -1
    "$PY" "$ROOT/tools/dev/playtest.py" load "${2:-FT Flat}" | tail -1
    curl -s "http://127.0.0.1:25599/lan?port=25570"; echo
    cd "$ROOT" && (./gradlew --no-daemon runClient2 > run2-console.log 2>&1 &)
    for i in $(seq 1 240); do curl -s -m 2 http://127.0.0.1:25600/state > /dev/null 2>&1 && break; sleep 1; done
    for i in $(seq 1 30); do
      s=$(curl -s http://127.0.0.1:25600/state)
      case "$s" in *TitleScreen*) break;; *AccessibilityOnboarding*) curl -s "http://127.0.0.1:25600/click?text=Continue" > /dev/null;; esac
      sleep 2
    done
    curl -s "http://127.0.0.1:25600/connect?host=localhost&port=25570" > /dev/null
    for i in $(seq 1 60); do
      s=$(curl -s http://127.0.0.1:25600/state)
      case "$s" in *'"pos"'*) echo "friend joined"; exit 0;; *DisconnectedScreen*) echo "friend could not join"; exit 1;; esac
      sleep 2
    done
    echo "timed out joining"; exit 1 ;;
  stop)
    curl -s -m 5 http://127.0.0.1:25600/quit > /dev/null 2>&1
    "$ROOT/tools/dev/client.sh" stop ;;
  *) echo "usage: $0 start [world]|stop" ;;
esac
