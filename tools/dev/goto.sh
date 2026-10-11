#!/usr/bin/env bash
# Run chat commands through the dev bridge: tools/dev/goto.sh "cmd one" "cmd two" ...
# JAVA_HOME (JDK 25) and PY (python) come from your environment, or from tools/dev/local.env if you make one
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
PY="${PY:-python}"
for c in "$@"; do
  curl -s "http://127.0.0.1:25599/cmd?c=$("$PY" -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$c")" >/dev/null
  sleep 1
done
