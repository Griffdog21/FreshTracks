#!/usr/bin/env bash
# Stops the dev client cleanly (or by force after 60 s) and waits until it has really exited, so the next one can open
# the same world. Start the new client yourself afterwards (gradlew runClient in the background).
PID=$(netstat -ano | grep ":25599.*LISTEN" | awk '{print $5}' | head -1)
if [ -z "$PID" ]; then echo "no dev client running"; exit 0; fi
curl -s -m 5 127.0.0.1:25599/quit >/dev/null
for i in $(seq 1 60); do
  powershell -c "Get-Process -Id $PID -ErrorAction Stop" >/dev/null 2>&1 || { echo "dev client $PID exited"; exit 0; }
  sleep 1
done
powershell -c "Stop-Process -Id $PID -Force"
echo "dev client $PID killed"
