"""Hero shots for the website: the new gondola station at golden hour, and riding past it.

Needs both clients: the dev client (bridge 25599, reads the test world's fall line and lift positions) and
gradlew prodClient -PprodSize=1920x1080 (bridge 25601, shaders on), both with "Fresh Tracks Test World" in their saves.
Writes run-prod/screenshots/page_*.png.
"""
from __future__ import annotations

import json
import math
import os
import re
import time
import urllib.parse
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
WORLD = "Fresh Tracks Test World"
DEV_LOG = os.path.join(ROOT, "run", "logs", "latest.log")
CACHE = os.path.join(ROOT, "run-prod", "test_world_points.json")


def call(port: int, path: str, **params) -> dict:
    url = f"http://127.0.0.1:{port}{path}" + ("?" + urllib.parse.urlencode(params) if params else "")
    with urllib.request.urlopen(url, timeout=90) as r:
        return json.loads(r.read().decode("utf-8"))


def cmd(port: int, c: str, wait: float = 0.4) -> None:
    call(port, "/cmd", c=c)
    time.sleep(wait)


def load(port: int, world: str) -> None:
    s = call(port, "/state")
    if "pos" in s:
        call(port, "/world", action="leave")
        while "pos" in call(port, "/state"):
            time.sleep(1)
        time.sleep(3)
    call(port, "/world", action="load", name=world)
    while "pos" not in call(port, "/state"):
        st = call(port, "/state")
        if st.get("screen") in ("BackupConfirmScreen", "DetailedBackupConfirmScreen"):
            call(port, "/click", text="I Know What I’m Doing!")
        time.sleep(2)
    time.sleep(4)


def log_size() -> int:
    return os.path.getsize(DEV_LOG)


def wait_log(pattern: str, since: int, timeout: float = 60) -> list[re.Match]:
    end = time.time() + timeout
    while time.time() < end:
        with open(DEV_LOG, encoding="utf-8", errors="replace") as f:
            f.seek(since)
            text = f.read()
        found = list(re.finditer(pattern, text))
        if found:
            time.sleep(1)
            with open(DEV_LOG, encoding="utf-8", errors="replace") as f:
                f.seek(since)
                return list(re.finditer(pattern, f.read()))
        time.sleep(1)
    raise SystemExit("no log line for " + pattern)


def survey() -> dict:
    """Fall line of the main run and every lift's rope start, from the dev client."""
    if os.path.exists(CACHE):
        return json.load(open(CACHE))
    load(25599, WORLD)
    cmd(25599, "gamemode creative")
    since = log_size()
    cmd(25599, "tp @s 100 380 -1100", 3)
    cmd(25599, "ftdev fallline", 2)
    m = wait_log(r"\[skitools\] fallline(.*)", since)[-1]
    pts = [tuple(float(v) for v in seg.split()) for seg in m.group(1).split("|") if seg.strip()]
    since = log_size()
    cmd(25599, "freshtracks lift list", 2)
    ropes = [[tuple(float(v) for v in p.split()) for p in re.findall(r"\(([-\d. ]+)\)", r.group(1))]
             for r in wait_log(r"rope:(.*)", since)]
    data = {"run": pts, "ropes": ropes}
    json.dump(data, open(CACHE, "w"))
    return data


def look_at(port, cam, tgt, settle: float) -> None:
    dx, dy, dz = tgt[0] - cam[0], tgt[1] - cam[1], tgt[2] - cam[2]
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    cmd(port, f"tp @s {cam[0]:.1f} {cam[1]:.1f} {cam[2]:.1f} {yaw:.1f} {pitch:.1f}")
    time.sleep(settle)


def shot(port, name: str) -> None:
    call(port, "/clean")
    time.sleep(0.3)
    print(call(port, "/shot", name="page_" + name, scale="1")["file"])


def main() -> None:
    data = survey()
    run = data["run"]
    # the gondola: the lift whose bottom station is nearest the bottom of the main run
    base = run[-1]
    rope = min(data["ropes"], key=lambda r: math.hypot(r[0][0] - base[0], r[0][2] - base[2]))
    b, nxt = rope[0], rope[1]
    n = math.hypot(nxt[0] - b[0], nxt[2] - b[2]) or 1
    ux, uz = (nxt[0] - b[0]) / n, (nxt[2] - b[2]) / n
    sx, sz = -uz, ux
    ground = b[1] - 3.1
    P = 25601
    load(P, WORLD)
    for c in ("gamemode creative", "gamerule send_command_feedback false", "gamerule show_advancement_messages false",
              "time set 11600", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
              "freshtracks weather set bluebird", "advancement grant @s from freshtracks:root"):
        cmd(P, c)
    call(P, "/clean")
    call(P, "/hud", hidden="true")
    cmd(P, "gamemode spectator")
    # 1. the station: low, from the side and a little ahead, the housing and its name with the line climbing behind
    mid = (b[0] + ux * 5, ground + 3.2, b[2] + uz * 5)
    look_at(P, (b[0] + ux * 17 + sx * 15, ground + 4.5, b[2] + uz * 17 + sz * 15), mid, 30)
    shot(P, "station")
    look_at(P, (b[0] - ux * 10 + sx * 18, ground + 7, b[2] - uz * 10 + sz * 18), (b[0] + ux * 30, ground + 12, b[2] + uz * 30), 12)
    shot(P, "station2")
    # 2. riding down the run past the lift, the camera following
    cmd(P, "gamemode creative")
    cmd(P, "clear @s")
    cmd(P, "give @s freshtracks:race_skis")
    for start, name in ((int(len(run) * 0.62), "hero1"), (int(len(run) * 0.75), "hero2")):
        p, q = run[start], run[start + 1]
        yaw = math.degrees(math.atan2(-(q[0] - p[0]), q[2] - p[2]))
        cmd(P, "ride @s dismount")
        cmd(P, f"tp @s {p[0]:.1f} {p[1] + 1:.1f} {p[2]:.1f} {yaw:.1f} 10", 5)
        call(P, "/slot", n="0")
        call(P, "/look", yaw=f"{yaw:.1f}", pitch="10")
        time.sleep(0.5)
        call(P, "/use")
        time.sleep(1.5)
        call(P, "/clean")
        call(P, "/camera", mode="back")
        call(P, "/timeline", spec=f"f:40;r:14@page_{name}a;l:18@page_{name}b;r:18@page_{name}c;l:14")
        time.sleep(7)
    cmd(P, "ride @s dismount")
    call(P, "/camera", mode="first")
    call(P, "/hud", hidden="false")
    for c in ("gamerule send_command_feedback true", "gamerule show_advancement_messages true", "gamerule advance_time true"):
        cmd(P, c)


if __name__ == "__main__":
    main()
