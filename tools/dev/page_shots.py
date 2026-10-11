"""Takes the showcase screenshots for the mod's GitHub page in the production client (shaders on), in the test world.

  python tools/dev/page_shots.py            (gradlew prodClient must be running: bridge on port 25601)

Writes run-prod/screenshots/page_*.png.
"""
from __future__ import annotations

import json
import math
import os
import time
import urllib.parse
import urllib.request

BRIDGE = "http://127.0.0.1:25601"
WORLD = "Fresh Tracks Test World"
LOG = os.path.join(os.path.dirname(__file__), "..", "..", "run", "client-console.log")


def call(path: str, **params) -> dict:
    url = BRIDGE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def state() -> dict:
    return call("/state")


def cmd(c: str, wait: float = 0.4) -> None:
    call("/cmd", c=c)
    time.sleep(wait)


def wait_ready() -> None:
    while True:
        try:
            s = state()
            if s.get("screen") == "TitleScreen" or "pos" in s:
                break
        except Exception:
            pass
        time.sleep(3)
    steady = 0
    while steady < 8 and "pos" not in state():
        steady = steady + 1 if state().get("screen") == "TitleScreen" else 0
        time.sleep(1)


def run_points() -> list[tuple]:
    line = [l for l in open(LOG, encoding="utf-8", errors="replace") if "[skitools] fallline" in l][-1]
    pts = [tuple(float(v) for v in seg.split()) for seg in line.split("fallline", 1)[1].split("|") if seg.strip()]
    return pts[3:]


def look_at(cam, tgt, settle: float) -> None:
    dx, dy, dz = tgt[0] - cam[0], tgt[1] - cam[1], tgt[2] - cam[2]
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    cmd(f"tp @s {cam[0]:.1f} {cam[1]:.1f} {cam[2]:.1f} {yaw:.1f} {pitch:.1f}")
    time.sleep(settle)


def shot(name: str) -> None:
    call("/clean")
    time.sleep(0.3)
    print(call("/shot", name="page_" + name, scale="1")["file"])


def guns_on(x: float, y: float, z: float, r: int = 14) -> None:
    for f in ("north", "south", "east", "west"):
        cmd(f"fill {int(x) - r} {int(y) - r} {int(z) - r} {int(x) + r} {int(y) + r} {int(z) + r} "
            f"freshtracks:snow_gun[facing={f},mode=on] replace freshtracks:snow_gun[facing={f}]", 0.2)


def main() -> None:
    wait_ready()
    if "pos" not in state():
        call("/world", action="load", name=WORLD)
        while "pos" not in state():
            s = state()
            if s.get("screen") in ("BackupConfirmScreen", "DetailedBackupConfirmScreen"):
                call("/click", text="I Know What I'm Doing!")
            time.sleep(2)
    for c in ("gamemode creative", "gamerule send_command_feedback false", "gamerule show_advancement_messages false",
              "time set 2500", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
              "freshtracks weather set bluebird", "advancement grant @s from freshtracks:root"):
        cmd(c)
    time.sleep(2)
    call("/clean")
    run = run_points()
    base = run[-1]
    # --- scenery, no HUD
    call("/hud", hidden="true")
    cmd("gamemode spectator")
    a, b = run[16], run[26]
    look_at((a[0] + 8, a[1] + 9, a[2] - 10), (b[0], b[1] - 4, b[2]), 30)
    shot("run")
    look_at((base[0] - 30, base[1] + 18, base[2] - 30), (base[0] + 10, base[1], base[2] + 20), 25)
    shot("base")
    # the terrain park: look down the line of jumps (same layout as build_test_world.py)
    park = run[int(len(run) * 0.55):int(len(run) * 0.85)]
    (x0, y0, z0, _), (x1, _, z1, _) = park[0], park[-1]
    n = math.hypot(x1 - x0, z1 - z0) or 1
    ux, uz = (x1 - x0) / n, (z1 - z0) / n
    px0, pz0 = x0 - uz * 26, z0 + ux * 26
    k1 = (px0 + ux * n / 4, park[len(park) // 4][1], pz0 + uz * n / 4)
    look_at((k1[0] - ux * 22 + uz * 8, k1[1] + 9, k1[2] - uz * 22 - ux * 8), (k1[0] + ux * 20, k1[1] - 8, k1[2] + uz * 20), 20)
    shot("park")
    # the trail map board by the lodge
    tm = (base[0] + 6.5, base[1], base[2] + 6.5)
    look_at((tm[0] + 0.5, tm[1] + 3.5, tm[2] - 6), (tm[0], tm[1] + 3.2, tm[2]), 12)
    shot("trailmap")
    # snow guns blowing in the late afternoon light
    cmd("time set 11200")
    g = run[44]
    guns_on(g[0], g[1], g[2], 20)
    look_at((g[0] + 16, g[1] + 7, g[2] - 12), (g[0] - 8, g[1] + 1, g[2] + 6), 15)
    shot("snowguns")
    # northern lights over the mountain
    cmd("time set 18000")
    call("/aurora", v="1")
    look_at((a[0], a[1] + 30, a[2]), (b[0] + 200, b[1] + 90, b[2] + 200), 20)
    shot("aurora")
    call("/aurora", v="-1")
    # --- riding, with the HUD (speed, trick scores)
    cmd("time set 2500")
    call("/hud", hidden="false")
    cmd("gamemode creative")
    cmd("clear @s")
    cmd("give @s freshtracks:alpine_skis")
    p, q = run[18], run[19]
    yaw = math.degrees(math.atan2(-(q[0] - p[0]), q[2] - p[2]))
    cmd(f"tp @s {p[0]:.1f} {p[1] + 1:.1f} {p[2]:.1f} {yaw:.1f} 15", 5)
    call("/slot", n="0")
    call("/look", yaw=f"{yaw:.1f}", pitch="15")
    time.sleep(0.5)
    call("/use")
    time.sleep(1.5)
    call("/clean")
    call("/camera", mode="back")
    call("/timeline", spec="f:50;r:16@page_ride1;l:20@page_ride2;r:20@page_ride3;l:18")
    time.sleep(8)
    cmd("ride @s dismount")
    call("/camera", mode="first")
    cmd("gamerule send_command_feedback true")
    cmd("gamerule show_advancement_messages true")


if __name__ == "__main__":
    main()
