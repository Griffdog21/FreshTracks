"""Builds the "Fresh Tracks Demo" world: a small resort on a real Ski Mountains mountain, for play testing.

Creates the world (Ski Mountains, seed 7) if needed, finds the biggest mountain near spawn and its fall line, then
builds a gondola to near the summit, an express quad to mid-mountain, a T-bar on the upper slopes and a beginner
double chair, with a lodge and shops at the base, rope-line mazes, trail and resort signs. Dev client must be running.

  python tools/dev/build_demo.py
"""
from __future__ import annotations

import math
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import playtest as pt  # noqa: E402

LOG = os.path.join(os.path.dirname(__file__), "..", "..", "run", "client-console.log")
WORLD = "Fresh Tracks Demo"


def log_size() -> int:
    return os.path.getsize(LOG)


def wait_log(pattern: str, since: int, timeout: float = 60) -> re.Match:
    end = time.time() + timeout
    while time.time() < end:
        with open(LOG, encoding="utf-8", errors="replace") as f:
            f.seek(since)
            text = f.read()
        m = re.search(pattern, text)
        if m:
            return m
        time.sleep(0.5)
    raise RuntimeError("timed out waiting for " + pattern)


def cmd(c: str, wait: float = 0.35) -> None:
    pt.call("/cmd", c=c)
    time.sleep(wait)


def ensure_world() -> None:
    saves = os.path.join(os.path.dirname(__file__), "..", "..", "run", "saves", WORLD)
    s = pt.state()
    if "pos" in s:
        pt.call("/world", action="leave")
        while "pos" in pt.state():
            time.sleep(1)
        time.sleep(3)
    if os.path.isdir(saves):
        pt.call("/world", action="load", name=WORLD)
    else:
        pt.call("/world", action="create", name=WORLD, seed="7", preset="freshtracks:ski_mountains")
    pt.wait_in_game(240)
    for c in ("gamemode creative", "time set 1000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false"):
        cmd(c)


def forceload_line(a, b, margin=40) -> list[str]:
    """Force-loads the chunks along the line a -> b (in pieces under the 256-chunk limit); returns undo commands."""
    undo = []
    length = math.dist((a[0], a[2]), (b[0], b[2]))
    steps = max(1, int(length // 120))
    for k in range(steps):
        t0, t1 = k / steps, (k + 1) / steps
        x0, z0 = a[0] + (b[0] - a[0]) * t0, a[2] + (b[2] - a[2]) * t0
        x1, z1 = a[0] + (b[0] - a[0]) * t1, a[2] + (b[2] - a[2]) * t1
        lo_x, hi_x = int(min(x0, x1)) - margin, int(max(x0, x1)) + margin
        lo_z, hi_z = int(min(z0, z1)) - margin, int(max(z0, z1)) + margin
        cmd(f"forceload add {lo_x} {lo_z} {hi_x} {hi_z}", 0.6)
        undo.append(f"forceload remove {lo_x} {lo_z} {hi_x} {hi_z}")
    return undo


def build_lift(name: str, a, b, style: str) -> str:
    undo = forceload_line(a, b)
    time.sleep(6)  # let the chunks generate
    since = log_size()
    cmd(f'freshtracks lift create "{name}" {int(a[0])} {int(a[1]) + 30} {int(a[2])} {int(b[0])} {int(b[1]) + 30} {int(b[2])} {style}', 2)
    try:
        m = wait_log(re.escape(name) + r" is open: .*", since, 120)
        print(" ", m.group(0))
    except RuntimeError:
        print("  !! lift", name, "not confirmed")
    for u in undo:
        cmd(u, 0.2)
    return name


def surface(x, z, block: str) -> None:
    cmd(f"execute positioned {int(x)} 0 {int(z)} positioned over motion_blocking_no_leaves run setblock ~ ~ ~ {block}")


def cut_run(pts, half_width: int = 12) -> None:
    """Clears the trees along a fall line, like a resort cutting a run through the forest."""
    for (x0, h0, z0, _), (x1, h1, z1, _) in zip(pts, pts[1:]):
        steps = max(1, int(math.dist((x0, z0), (x1, z1)) // 8))
        for k in range(steps):
            t = k / steps
            x, z = x0 + (x1 - x0) * t, z0 + (z1 - z0) * t
            y = int(min(h0, h1)) - 6
            for kind in ("#minecraft:logs", "#minecraft:leaves"):
                cmd(f"fill {int(x) - half_width} {y} {int(z) - half_width} {int(x) + half_width} {y + 34} {int(z) + half_width} "
                    f"minecraft:air replace {kind}", 0.12)
    cmd("kill @e[type=item]")


def clear_area(cx: float, cz: float, half: int, y: float) -> None:
    for kind in ("#minecraft:logs", "#minecraft:leaves"):
        for ox in range(-half, half, 24):
            for oz in range(-half, half, 24):
                cmd(f"fill {int(cx) + ox} {int(y) - 6} {int(cz) + oz} {int(cx) + ox + 23} {int(y) + 30} {int(cz) + oz + 23} "
                    f"minecraft:air replace {kind}", 0.12)
    cmd("kill @e[type=item]")


def main() -> None:
    ensure_world()
    since = log_size()
    cmd("tp @s 0 200 0")
    cmd("ftdev peak 1100", 1)
    m = wait_log(r"\[skitools\] peak (-?\d+) (\d+) (-?\d+)", since)
    peak = (int(m.group(1)), int(m.group(2)), int(m.group(3)))
    print("peak", peak)
    since = log_size()
    cmd(f"tp @s {peak[0]} {peak[1] + 5} {peak[2]}", 2)
    cmd("ftdev fallline", 1)
    m = wait_log(r"\[skitools\] fallline(.*)", since)
    pts = [tuple(float(v) for v in seg.split()) for seg in m.group(1).split("|") if seg.strip()]
    print(len(pts), "fall line points, from", pts[0], "to", pts[-1])
    # first point below the summit that's still a reasonable pitch (the gondola top)
    top_i = next((i for i, p in enumerate(pts) if i > 2 and p[3] < 0.75), 3)
    top = pts[top_i]
    base = pts[-1]
    mid = pts[top_i + (len(pts) - 1 - top_i) // 2]
    # sideways direction (perpendicular to the base -> top line)
    dx, dz = top[0] - base[0], top[2] - base[2]
    norm = math.hypot(dx, dz) or 1.0
    ux, uz = dx / norm, dz / norm
    px, pz = -uz, ux
    off = lambda p, side, back=0.0: (p[0] + px * side - ux * back, p[1], p[2] + pz * side - uz * back)
    print("base", base, "mid", mid, "top", top)

    print("cutting the main run")
    for k in range(0, len(pts), 6):
        p = pts[k]
        cmd(f"forceload add {int(p[0]) - 32} {int(p[2]) - 32} {int(p[0]) + 32} {int(p[2]) + 32}", 0.3)
    time.sleep(8)
    cut_run(pts)
    clear_area(base[0], base[2], 72, base[1])
    for k in range(0, len(pts), 6):
        p = pts[k]
        cmd(f"forceload remove {int(p[0]) - 32} {int(p[2]) - 32} {int(p[0]) + 32} {int(p[2]) + 32}", 0.2)
    print("building lifts")
    build_lift("Summit Gondola", off(base, 0), off(top, 0), "gondola")
    build_lift("Cruiser Express", off(base, 45), off(mid, 45), "quad_express")
    upper_i = top_i + max(2, (len(pts) - 1 - top_i) // 5)
    build_lift("Upper Bowl T-Bar", off(pts[upper_i], -45), off(top, -45, 20), "t_bar")
    beginner_top = pts[max(top_i, len(pts) - 1 - 8)]
    build_lift("Bunny Double", off(base, -70, 10), off(beginner_top, -70), "double")

    print("base area")
    lodge = off(base, -10, 60)
    cmd(f"forceload add {int(lodge[0]) - 48} {int(lodge[2]) - 48} {int(lodge[0]) + 48} {int(lodge[2]) + 48}", 4)
    cmd(f"execute positioned {int(lodge[0])} 0 {int(lodge[2])} positioned over motion_blocking_no_leaves run place template freshtracks:ski_lodge ~ ~ ~", 2)
    shop = off(base, 30, 60)
    cmd(f"execute positioned {int(shop[0])} 0 {int(shop[2])} positioned over motion_blocking_no_leaves run place template freshtracks:village/ski_shop ~ ~ ~", 2)
    board = off(base, 50, 70)
    cmd(f"execute positioned {int(board[0])} 0 {int(board[2])} positioned over motion_blocking_no_leaves run place template freshtracks:village/board_shop ~ ~ ~", 2)
    # rope-line mazes behind the gondola and express loading spots, slow zone signs at the base
    for side in (0, 45):
        for row in range(3):
            for k in range(-3, 4):
                p = off(base, side + k, 8 + row * 2)
                surface(p[0], p[2], "freshtracks:rope_line")
    for side in (-20, 25, 70):
        p = off(base, side, 25)
        surface(p[0], p[2], "freshtracks:slow_zone_sign[facing=north]")
    # trail signs at the top of the runs
    names = [("Long Cruise", "green"), ("Powder Bowl", "black"), ("Ridge Line", "double_black"), ("Home Run", "blue")]
    for idx, (name, symbol) in enumerate(names):
        p = off(top, -30 + idx * 15, 12)
        cmd(f"execute positioned {int(p[0])} 0 {int(p[2])} positioned over motion_blocking_no_leaves run setblock ~ ~ ~ "
            f"freshtracks:trail_sign[facing=north,symbol={symbol}]{{name:\"{name}\"}}")
    for u in [f"forceload remove {int(lodge[0]) - 48} {int(lodge[2]) - 48} {int(lodge[0]) + 48} {int(lodge[2]) + 48}"]:
        cmd(u)
    cmd("freshtracks weather set powder_day", 1)
    spawn = off(base, 20, 40)
    cmd(f"execute positioned {int(spawn[0])} 0 {int(spawn[2])} positioned over motion_blocking_no_leaves run setworldspawn ~ ~ ~")
    cmd(f"execute positioned {int(spawn[0])} 0 {int(spawn[2])} positioned over motion_blocking_no_leaves run tp @s ~ ~ ~")
    print("done; spawn at", spawn)


if __name__ == "__main__":
    main()
