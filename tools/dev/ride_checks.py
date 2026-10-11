"""Scripted checks for the riding complaints: getting stuck at walls, sinking into snow, the S+turn edge check,
riding off a ledge without flipping, and powder. Run in the dev client with "FT Flat" loaded:

  python tools/dev/ride_checks.py [wall] [buried] [sink] [edge] [ledge] [powder]   (no args = all)
"""
from __future__ import annotations

import math
import sys
import time

sys.path.insert(0, __file__.rsplit("\\", 1)[0].rsplit("/", 1)[0])
import playtest as pt  # noqa: E402


def rows_of(csv: str) -> list[dict]:
    lines = csv.strip().splitlines()
    head = lines[0].split(",")
    out = []
    for line in lines[1:]:
        parts = line.split(",")
        out.append(dict(zip(head, parts)))
    return out


def fnum(row, key):
    return float(row[key])


def setup_rider(x: float, y: float, z: float, yaw: float, item: str = "alpine_skis") -> dict:
    pt.cmd("ride @s dismount")
    pt.cmd("kill @e[type=freshtracks:rig]")
    pt.cmd("clear @s")
    pt.cmd(f"give @s freshtracks:{item}")
    pt.call("/slot", n="0")
    pt.cmd(f"tp @s {x} {y} {z} {yaw} 10")
    pt.call("/look", yaw=str(yaw), pitch="10")
    time.sleep(0.8)
    pt.call("/use")
    time.sleep(0.6)
    return pt.state()


def run_timeline(spec: str) -> list[dict]:
    pt.call("/telemetry", action="start")
    pt.call("/timeline", spec=spec)
    pt.wait_keys_idle()
    time.sleep(0.3)
    pt.call("/telemetry", action="stop")
    return rows_of(pt.call("/telemetry")["csv"])


RESULTS = []


def report(name: str, ok: bool, detail: str) -> None:
    RESULTS.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + ": " + detail)


def check_wall() -> None:
    # a wall right in front of the rider; click in facing it, swing into it, then try to get away
    pt.cmd("fill 296 3 296 312 3 316 minecraft:snow_block")
    pt.cmd("fill 296 4 296 312 9 316 minecraft:air")
    pt.cmd("fill 300 4 300 300 7 312 minecraft:stone")
    s = setup_rider(301.45, 4, 306.5, 90)
    if s.get("vehicle") != "rig":
        report("wall: click in facing a wall", False, f"no rig (vehicle={s.get('vehicle')})")
        return
    report("wall: click in facing a wall", True, "clicked in")
    start = s["pos"]
    rows = run_timeline("r:25;f:30;l:45;f:50")
    end = pt.state()["pos"]
    moved = math.dist(start, end)
    inside = [r for r in rows if fnum(r, "x") < 300.9]
    report("wall: turn and push away from it", moved > 1.5 and not inside, f"moved {moved:.2f} blocks, ticks inside the wall {len(inside)}")


def check_buried() -> None:
    # a block appears where the rider's body is (snow piled up, a teleport, a bad landing): must still get out
    pt.cmd("fill 296 3 296 312 3 316 minecraft:snow_block")
    pt.cmd("fill 296 4 296 312 9 316 minecraft:air")
    s = setup_rider(304.5, 4, 302.5, 0)
    start = s["pos"]
    pt.cmd("setblock 304 5 302 minecraft:stone")
    run_timeline("f:50")
    end = pt.state()["pos"]
    moved = math.dist(start, end)
    report("buried: body inside a block, push forward", moved > 1.0, f"moved {moved:.2f} blocks")
    pt.cmd("setblock 304 5 302 minecraft:air")


def check_sink() -> None:
    pt.ride("f:40;-:260", name="sink_check")
    rows = rows_of(pt.call("/telemetry")["csv"])
    ground = [r for r in rows if r["grounded"] == "true" and "POWDER" not in r["surface"]]
    sinks = [fnum(r, "top") - fnum(r, "y") for r in ground]
    worst = max(sinks) if sinks else 0
    deep = sum(1 for v in sinks if v > 0.02)
    report("sink: skis never below the snow (groomed / packed)", deep == 0, f"worst {worst:.3f} blocks below the top, {deep} of {len(ground)} ticks")
    powder = [r for r in rows if r["grounded"] == "true" and "POWDER" in r["surface"] and fnum(r, "speed") > 0.4]
    if powder:
        worst_p = max(fnum(r, "top") - fnum(r, "y") for r in powder)
        report("sink: planing on powder at speed", worst_p < 0.06, f"worst {worst_p:.3f} blocks under the powder surface at speed")


def check_edge() -> None:
    # the steep test run (no kicker): build speed, then S + A to scrub it on the steep pitch
    pt.STEEP = True
    rows = pt.ride("f:60;-:45;bl:80", name="edge_check")
    pt.STEEP = False
    # find when the edge check started: the last 80 ticks
    tail = rows[-80:] if len(rows) >= 80 else rows
    start_speed = fnum(tail[0], "speed")
    stop_at = next((i for i, r in enumerate(tail) if fnum(r, "speed") < 0.05), None)
    crashed = any("CRASH" in r["events"] for r in tail)
    report("edge check: S + A scrubs speed to a stop", stop_at is not None and not crashed,
           f"from {start_speed * 72:.0f} km/h, stopped after {stop_at} ticks" if stop_at is not None else f"still {fnum(tail[-1], 'speed') * 72:.0f} km/h")


def check_ledge() -> None:
    # a flat shelf 7 blocks up, ending in a drop onto a gentle slope; ride off it holding left + forward, no jump
    pt.cmd("ride @s dismount")
    pt.cmd("tp @s 405.5 30 420.5")  # building only works where chunks are loaded
    time.sleep(3)
    pt.cmd("fill 396 3 396 414 24 480 minecraft:air")
    pt.cmd("fill 398 15 398 412 15 440 minecraft:snow_block")
    # a 7-block drop at z = 441 onto a gentle landing slope (one block down every 3)
    for k in range(0, 36):
        y = 3 - k // 3
        pt.cmd(f"fill 398 {y} {441 + k} 412 {y} {441 + k} minecraft:snow_block")
        pt.cmd(f"fill 398 {y + 1} {441 + k} 412 {y + 3} {441 + k} minecraft:air")
    s = setup_rider(405.5, 16, 428.5, 0)
    rows = run_timeline("f:90;lf:40;-:40")
    air = [r for r in rows if r["grounded"] == "false"]
    if not air:
        report("ledge: steer without flipping", False, "never left the ground")
        return
    flip = max(abs(fnum(r, "flip")) for r in air)
    spin = max(abs(fnum(r, "spin")) for r in air)
    turned = abs(((fnum(air[-1], "heading") - fnum(air[0], "heading")) + 180) % 360 - 180)
    crashed = any("CRASH" in r["events"] for r in rows)
    report("ledge: steer without flipping", flip < 5 and spin < 5 and not crashed,
           f"{len(air)} air ticks, flip {flip:.0f}, spin {spin:.0f}, turned {turned:.0f} degrees, crashed={crashed}")
    # same ledge, tapping jump in the air: tricks allowed
    setup_rider(405.5, 16, 428.5, 0)
    rows = run_timeline("f:90;ljf:40;-:40")
    air = [r for r in rows if r["grounded"] == "false"]
    spin = max((abs(fnum(r, "spin")) for r in air), default=0)
    report("ledge: tap jump in the air to trick", spin > 90, f"spin {spin:.0f} degrees")


def check_powder() -> None:
    # turn the test run's powder section into untracked fresh powder, then link turns through it
    pt.slope()
    x0 = pt.RUN_X
    for y0 in range(20, 110, 10):  # a single fill is limited to 32768 blocks
        pt.cmd(f"fill {x0 - 10} {y0} 150 {x0 + 12} {y0 + 9} 196 freshtracks:fresh_powder[layers=4] replace minecraft:powder_snow")
    # start on the pitch just above the powder (i = 125) and link short turns down through it
    setup_rider(pt.RUN_X + 0.5, 49.3, 138.5, 0)
    rows = run_timeline("f:10;-:30;l:6;r:12;l:12;r:12;l:12;r:12;l:12;r:6;-:20")
    turns = [r["events"] for r in rows if "Powder Turn" in r["events"] or "First Tracks" in r["events"]]
    report("powder: linked turns score", len(turns) >= 3, f"{len(turns)} powder turns: {turns[:3]}")
    fast = [r for r in rows if r["grounded"] == "true" and "POWDER" in r["surface"] and fnum(r, "speed") > 0.4]
    if fast:
        sink = max(fnum(r, "top") - fnum(r, "y") for r in fast)
        report("powder: floating on top at speed", sink < 0.06, f"worst {sink:.3f} blocks under the surface")


def check_steep() -> None:
    # straight down the steep test run: should stay on the snow (no surprise airs), no crash
    pt.STEEP = True
    rows = pt.ride("f:60;-:220", name="steep_check")
    pt.STEEP = False
    on_run = [r for r in rows if fnum(r, "speed") > 0.2]
    air = sum(1 for r in on_run if r["grounded"] == "false")
    crashed = any("CRASH" in r["events"] for r in rows)
    top = max(fnum(r, "kmh") for r in rows)
    report("steep: straight run stays on the snow", air <= 25 and not crashed, f"{air} air ticks of {len(on_run)}, top {top:.0f} km/h, crashed={crashed}")


CHECKS = {"steep": check_steep, "wall": check_wall, "buried": check_buried, "sink": check_sink, "edge": check_edge, "ledge": check_ledge, "powder": check_powder}


def main(argv):
    names = argv or list(CHECKS)
    pt.cmd("gamemode creative")
    for n in names:
        CHECKS[n]()
    print()
    failed = [r for r in RESULTS if not r[1]]
    print(f"{len(RESULTS) - len(failed)} passed, {len(failed)} failed")


if __name__ == "__main__":
    main(sys.argv[1:])
