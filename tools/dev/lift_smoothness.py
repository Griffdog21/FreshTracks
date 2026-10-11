"""Rides a lift and checks it's smooth: python tools/dev/lift_smoothness.py X Y Z [seconds]

Stands the player at the loading spot (X Y Z, facing +Z), waits to be picked up, records every rendered frame through
the dev bridge's /frames probe, then reports how far the rider is drawn from their seat and how jerky the rider's
motion is measured against game time (a lift that shudders shows big frame-to-frame speed changes).
"""
from __future__ import annotations

import json
import math
import sys
import time
import urllib.parse
import urllib.request

BRIDGE = "http://127.0.0.1:25599"


def call(path: str, **params) -> dict:
    url = BRIDGE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode())


def main() -> None:
    x, y, z = (float(v) for v in sys.argv[1:4])
    seconds = float(sys.argv[4]) if len(sys.argv) > 4 else 8.0
    call("/cmd", c="ride @s dismount")
    time.sleep(0.5)
    call("/cmd", c=f"tp @s {x} {y} {z} 0 10")
    deadline = time.time() + 30
    while call("/state").get("vehicle") != "lift_seat":
        if time.time() > deadline:
            sys.exit("never got picked up")
        time.sleep(0.2)
    call("/camera", mode="first")
    call("/frames", action="start")
    time.sleep(seconds)
    call("/frames", action="stop")
    rows = [list(map(float, line.split(","))) for line in call("/frames")["csv"].strip().splitlines()[1:]]
    offsets = [math.dist(r[2:5], r[5:8]) for r in rows]
    speeds = []
    for a, b in zip(rows, rows[1:]):
        dt = b[1] - a[1]
        if dt > 1e-4:
            speeds.append(math.dist(a[2:5], b[2:5]) / dt)
    jumps = sorted(abs(b - a) for a, b in zip(speeds, speeds[1:]))
    backwards = sum(1 for a, b in zip(rows, rows[1:]) if b[1] < a[1])
    print(f"frames {len(rows)}, game-time steps backwards {backwards}")
    print(f"rider drawn {min(offsets):.3f}..{max(offsets):.3f} blocks from the seat")
    print(f"rider speed {min(speeds):.3f}..{max(speeds):.3f} blocks/tick")
    print(f"frame-to-frame speed change (blocks/tick): median {jumps[len(jumps) // 2]:.4f}, "
          f"p99 {jumps[int(len(jumps) * 0.99)]:.4f}, max {jumps[-1]:.4f}")


if __name__ == "__main__":
    main()
