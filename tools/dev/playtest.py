"""Drives the dev client through the dev bridge for repeatable play tests.

  python tools/dev/playtest.py load "FT Flat"
  python tools/dev/playtest.py slope                 rebuild the test run and stand at the top
  python tools/dev/playtest.py ride "f:50;-:40;r:18@carve" [--slot 0] [--name run1] [--no-rebuild] [--race]
        --race lays a start line on the start pad and a finish line near the bottom (after the rebuild)
  python tools/dev/playtest.py cmd "time set noon"
  python tools/dev/playtest.py state
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.parse
import urllib.request

BRIDGE = "http://127.0.0.1:25599"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "run", "playtest")


def call(path: str, **params) -> dict:
    url = BRIDGE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def state() -> dict:
    return call("/state")


def wait_in_game(timeout: float = 90) -> dict:
    end = time.time() + timeout
    while time.time() < end:
        s = state()
        if s.get("screen") == "BackupConfirmScreen":
            call("/click", text="I Know What I'm Doing!")
        if s.get("screen") is None and "pos" in s:
            return s
        time.sleep(1)
    raise SystemExit("timed out waiting to get in game: " + json.dumps(state()))


def wait_keys_idle(timeout: float = 120) -> None:
    end = time.time() + timeout
    while time.time() < end:
        if state().get("keys") == "idle":
            return
        time.sleep(0.25)


def cmd(c: str) -> None:
    call("/cmd", c=c)
    time.sleep(0.3)


def load(name: str) -> None:
    s = state()
    if "pos" in s:
        # already in a world: leave it first (it saves), then open the requested one
        call("/world", action="leave")
        end = time.time() + 60
        while time.time() < end and "pos" in state():
            time.sleep(1)
    # Right after start-up the title screen can ignore the request while resources finish loading; retry.
    for attempt in range(6):
        time.sleep(3)
        call("/world", action="load", name=name)
        end = time.time() + 15
        while time.time() < end:
            s = state()
            if s.get("screen") not in ("TitleScreen", None) or "pos" in s:
                break
            time.sleep(1)
        if s.get("screen") != "TitleScreen":
            break
    wait_in_game()
    for c in ("time set 1000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false"):
        cmd(c)


STEEP = False
RUN_X = -90
STEEP_X = 200


def slope() -> None:
    # get off whatever we're riding first: clicking in while still on a rig is ignored
    cmd("ride @s dismount")
    cmd("kill @e[type=freshtracks:rig]")
    # the two test runs are built side by side so they never overlap (the steep one is much wider)
    cmd(f"tp @s {STEEP_X if STEEP else RUN_X}.5 4 10.5 0 10")
    time.sleep(1.5)
    cmd("freshtracks slope steep" if STEEP else "freshtracks slope")
    time.sleep(2.0)
    cmd("kill @e[type=item]")


def race_lines() -> None:
    """Start line 7 blocks down the test run, finish line at 232. slope() always builds from (10, 99, 13) facing south."""
    x, oy, oz = RUN_X, 99, 13
    for i, block in ((7, "freshtracks:start_line"), (232, "freshtracks:finish_line")):
        surface = oy - sum(slope_at(k) for k in range(i))
        cmd(f"fill {x - 7} {int(surface // 1)} {oz + i} {x + 7} {int(surface // 1)} {oz + i} {block}")


def slope_at(i: int) -> float:
    """Mirror of TestSlopeBuilder.slopeAt."""
    for limit, drop in ((10, 0.0), (50, 0.25), (82, 0.55), (92, 0.15), (100, 0.15), (104, 1.6), (140, 0.62),
                        (180, 0.33), (190, 0.3), (222, 0.4)):
        if i < limit:
            return drop
    return 0.05


def ride(spec: str, slot: int = 0, name: str = "run", rebuild: bool = True, race: bool = False) -> list[dict]:
    if rebuild:
        slope()
    if race:
        race_lines()
    cmd("clear @s")
    for item in ("alpine_skis", "all_mountain_board", "park_skis", "park_board", "powder_skis", "race_skis"):
        cmd(f"give @s freshtracks:{item}")
    call("/slot", n=str(slot))
    cmd("tp @s ~ ~ ~ 0 12")  # face downhill on the server too (the rig takes the server's view of your yaw)
    call("/look", yaw="0", pitch="12")
    time.sleep(0.8)
    print("use:", call("/use"))
    time.sleep(0.5)
    call("/telemetry", action="start")
    print("timeline:", call("/timeline", spec=spec))
    wait_keys_idle()
    time.sleep(0.5)
    call("/telemetry", action="stop")
    csv = call("/telemetry")["csv"]
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".csv"), "w", encoding="utf-8") as f:
        f.write(csv)
    rows = parse(csv)
    summarize(rows)
    return rows


def parse(csv: str) -> list[dict]:
    lines = csv.strip().split("\n")
    head = lines[0].split(",")
    rows = []
    for line in lines[1:]:
        parts = line.split(",", len(head) - 1)
        rows.append(dict(zip(head, parts)))
    return rows


def summarize(rows: list[dict]) -> None:
    if not rows:
        print("no telemetry rows (not riding?)")
        return
    top = max(float(r["kmh"]) for r in rows)
    air = sum(1 for r in rows if r["grounded"] == "false")
    print(f"{len(rows)} ticks, top speed {top:.0f} km/h, {air} ticks airborne")
    for r in rows:
        if r["events"]:
            print(f"  t={r['tick']} z={float(r['z']):.1f} y={float(r['y']):.1f} {float(r['kmh']):.0f}km/h {r['surface']}: {r['events']}")
    last = rows[-1]
    print(f"end: x={float(last['x']):.1f} y={float(last['y']):.1f} z={float(last['z']):.1f} {float(last['kmh']):.0f} km/h")


def main(argv: list[str]) -> None:
    if not argv:
        print(__doc__)
        return
    op = argv[0]
    if op == "load":
        load(argv[1] if len(argv) > 1 else "FT Flat")
    elif op == "slope":
        slope()
    elif op == "ride":
        slot = 0
        name = "run"
        rebuild = True
        race = False
        args = argv[2:]
        while args:
            a = args.pop(0)
            if a == "--slot":
                slot = int(args.pop(0))
            elif a == "--name":
                name = args.pop(0)
            elif a == "--no-rebuild":
                rebuild = False
            elif a == "--race":
                race = True
            elif a == "--steep":
                global STEEP
                STEEP = True
        ride(argv[1], slot, name, rebuild, race)
    elif op == "cmd":
        cmd(argv[1])
    elif op == "state":
        print(json.dumps(state(), indent=1))
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
