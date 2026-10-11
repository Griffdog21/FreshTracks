"""Builds the public "Fresh Tracks Test World" from the demo resort (tools/dev/build_demo.py): grooms the main run into
corduroy, puts snow guns along it, adds a terrain park with jumps, hazard poles and a cliff sign up top, boundary
signs, an experts-only gate, a closed run with fencing, and a trail map board at the base. Dev client must be running.

  uv run --with numpy python tools/dev/build_test_world.py
"""
from __future__ import annotations

import math
import os
import re
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import build_demo as demo  # noqa: E402
import playtest as pt  # noqa: E402

SAVES = os.path.join(os.path.dirname(__file__), "..", "..", "run", "saves")
SOURCE = "Fresh Tracks Demo"
WORLD = "Fresh Tracks Test World"


def cmd(c: str, wait: float = 0.3) -> None:
    pt.call("/cmd", c=c)
    time.sleep(wait)


def facing(dx: float, dz: float) -> str:
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def on_ground(x: float, z: float, block: str, nbt: str = "") -> None:
    cmd(f"execute positioned {int(math.floor(x))} 0 {int(math.floor(z))} positioned over motion_blocking_no_leaves run "
        f"setblock ~ ~ ~ {block}{nbt}", 0.15)


def main() -> None:
    src, dst = os.path.join(SAVES, SOURCE), os.path.join(SAVES, WORLD)
    s = pt.state()
    if "pos" in s:
        pt.call("/world", action="leave")
        while "pos" in pt.state():
            time.sleep(1)
        time.sleep(3)
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("session.lock"))
    pt.call("/world", action="load", name=WORLD)
    pt.wait_in_game(300)
    for c in ("gamemode creative", "time set 1000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false"):
        cmd(c)
    since = demo.log_size()
    cmd("tp @s 100 380 -1100", 3)
    cmd("ftdev fallline", 1)
    m = demo.wait_log(r"\[skitools\] fallline(.*)", since)
    pts = [tuple(float(v) for v in seg.split()) for seg in m.group(1).split("|") if seg.strip()]
    top_i = next((i for i, p in enumerate(pts) if i > 2 and p[3] < 0.75), 3)
    run = pts[top_i:]
    print(len(run), "run points from", run[0], "to", run[-1])

    def load_along(a: int, b: int) -> None:
        for k in range(a, b, 4):
            p = run[k]
            cmd(f"forceload add {int(p[0]) - 40} {int(p[2]) - 40} {int(p[0]) + 40} {int(p[2]) + 40}", 0.4)
        time.sleep(6)

    def unload_along(a: int, b: int) -> None:
        for k in range(a, b, 4):
            p = run[k]
            cmd(f"forceload remove {int(p[0]) - 40} {int(p[2]) - 40} {int(p[0]) + 40} {int(p[2]) + 40}", 0.1)

    load_along(0, len(run))
    # 1. corduroy down the main run
    for (x0, _, z0, _), (x1, _, z1, _) in zip(run, run[1:]):
        cmd(f"ftbuild groom {x0:.1f} {z0:.1f} {x1:.1f} {z1:.1f} 8", 0.15)
    print("groomed the main run")
    # 2. snow guns down the lower half, alternating sides, aimed across the run
    lower = run[len(run) // 2:]
    for k in range(0, len(lower) - 1, 5):
        (x0, _, z0, _), (x1, _, z1, _) = lower[k], lower[min(len(lower) - 1, k + 1)]
        n = math.hypot(x1 - x0, z1 - z0) or 1
        ux, uz = (x1 - x0) / n, (z1 - z0) / n
        side = 1 if (k // 5) % 2 == 0 else -1
        gx, gz = x0 - uz * 10 * side, z0 + ux * 10 * side
        on_ground(gx, gz, f"freshtracks:snow_gun[facing={facing(uz * side, -ux * side)},mode=auto]")
    print("snow guns placed")
    # 3. a terrain park beside the lower run: a line of jumps, its own sign
    park = run[int(len(run) * 0.55):int(len(run) * 0.85)]
    if len(park) > 4:
        (x0, _, z0, _), (x1, _, z1, _) = park[0], park[-1]
        n = math.hypot(x1 - x0, z1 - z0) or 1
        ux, uz = (x1 - x0) / n, (z1 - z0) / n
        off = 26
        px0, pz0 = x0 - uz * off, z0 + ux * off
        demo.clear_area(px0 + ux * n / 2, pz0 + uz * n / 2, int(n / 2) + 20, park[len(park) // 2][1])
        yaw = math.degrees(math.atan2(-ux, uz))
        for j in range(1, 4):
            kx, kz = px0 + ux * (j * n / 4), pz0 + uz * (j * n / 4)
            cmd(f"ftbuild kicker {kx:.1f} {kz:.1f} {yaw:.1f} 2.3 9 7", 0.4)
        for (ax, _, az, _), (bx, _, bz, _) in zip(park, park[1:]):
            cmd(f"ftbuild groom {ax - uz * off:.1f} {az + ux * off:.1f} {bx - uz * off:.1f} {bz + ux * off:.1f} 6", 0.15)
        on_ground(px0 - ux * 4 + uz * 5, pz0 - uz * 4 - ux * 5, f"freshtracks:trail_sign[facing={facing(-ux, -uz)},symbol=park]", '{name:"Terrain Park"}')
        print("park built")
    # 4. hazard poles and a cliff sign where the top of the run is steepest
    steep = max(range(len(run) // 3), key=lambda k: run[k][3])
    (x0, _, z0, _), (x1, _, z1, _) = run[steep], run[min(len(run) - 1, steep + 1)]
    n = math.hypot(x1 - x0, z1 - z0) or 1
    ux, uz = (x1 - x0) / n, (z1 - z0) / n
    for j in range(-2, 3):
        on_ground(x0 + ux * j * 4 - uz * 11, z0 + uz * j * 4 + ux * 11, f"freshtracks:hazard_pole[facing={facing(uz, -ux)}]")
    on_ground(x0 - ux * 12 - uz * 10, z0 - uz * 12 + ux * 10, f"freshtracks:cliff_sign[facing={facing(-ux, -uz)},wall=false]")
    on_ground(x0 - ux * 14 + uz * 12, z0 - uz * 14 - ux * 12, f"freshtracks:experts_only_sign[facing={facing(-ux, -uz)},wall=false]")
    # 5. boundary signs along the far side of the top, a closed run with orange fencing
    for j in range(0, 4):
        p = run[min(len(run) - 1, j * 3)]
        on_ground(p[0] + uz * 30, p[2] - ux * 30, f"freshtracks:boundary_sign[facing={facing(-uz, ux)},wall=false]")
    p = run[min(len(run) - 1, len(run) // 4)]
    cx, cz = p[0] + uz * 22, p[2] - ux * 22
    for j in range(-4, 5):
        on_ground(cx + ux * j, cz + uz * j, "freshtracks:closure_fence")
    on_ground(cx - uz * 2, cz + ux * 2, f"freshtracks:closed_sign[facing={facing(-uz, ux)},wall=false]")
    print("signs and fencing placed")
    # 6. a trail map board by the lodge
    base = run[-1]
    on_ground(base[0] + 6, base[2] + 6, "freshtracks:trail_map[facing=north]")
    unload_along(0, len(run))
    for c in ("time set 1000", "weather clear", "gamerule advance_time true", "gamerule advance_weather true",
              "freshtracks weather auto true"):
        cmd(c)
    spawn = run[-1]
    cmd(f"execute positioned {int(spawn[0]) + 10} 0 {int(spawn[2]) + 20} positioned over motion_blocking_no_leaves run tp @s ~ ~ ~", 2)
    print("done")


if __name__ == "__main__":
    main()
