"""More hero shots in the production client (run page_hero.py once first, for the survey cache): the gondola station
from in front of its nose, a skier from the front throwing spray, and a jump in the terrain park against the low sun.
"""
from __future__ import annotations

import math
import time

import page_hero as ph

P = 25601


def ride(start, yaw: float, spec: str, wait: float, pitch: float = 8) -> None:
    ph.cmd(P, "ride @s dismount")
    ph.cmd(P, f"tp @s {start[0]:.1f} {start[1] + 1:.1f} {start[2]:.1f} {yaw:.1f} {pitch}", 5)
    ph.call(P, "/slot", n="0")
    ph.call(P, "/look", yaw=f"{yaw:.1f}", pitch=f"{pitch}")
    time.sleep(0.5)
    ph.call(P, "/use")
    time.sleep(1.5)
    ph.call(P, "/clean")
    ph.call(P, "/timeline", spec=spec)
    time.sleep(wait)


def main() -> None:
    data = ph.survey()
    run = data["run"]
    base = run[-1]
    rope = min(data["ropes"], key=lambda r: math.hypot(r[0][0] - base[0], r[0][2] - base[2]))
    b, nxt = rope[0], rope[1]
    n = math.hypot(nxt[0] - b[0], nxt[2] - b[2]) or 1
    ux, uz = (nxt[0] - b[0]) / n, (nxt[2] - b[2]) / n
    sx, sz = -uz, ux
    ground = b[1] - 3.1
    for c in ("time set 11400", "weather clear", "gamerule advance_time false"):
        ph.cmd(P, c)
    ph.call(P, "/hud", hidden="true")
    ph.cmd(P, "gamemode spectator")
    # the station from up the line, both sides, looking back at its nose and name
    for k, side in enumerate((1, -1)):
        cam = (b[0] + ux * 24 + sx * 10 * side, ground + 8, b[2] + uz * 24 + sz * 10 * side)
        ph.look_at(P, cam, (b[0] + ux * 4, ground + 3.5, b[2] + uz * 4), 25 if k == 0 else 12)
        ph.shot(P, f"nose{k}")
    # a skier from the front: the camera ahead of them, the spray flying up behind
    ph.cmd(P, "gamemode creative")
    ph.cmd(P, "clear @s")
    ph.cmd(P, "give @s freshtracks:race_skis")
    ph.call(P, "/camera", mode="front")
    for start, name in ((int(len(run) * 0.6), "front1"), (int(len(run) * 0.72), "front2")):
        p, q = run[start], run[start + 1]
        yaw = math.degrees(math.atan2(-(q[0] - p[0]), q[2] - p[2]))
        ride(p, yaw, f"f:36;r:10@page_{name}a;r:6@page_{name}b;l:14@page_{name}c;l:6@page_{name}d", 6)
    # the park: straight at the middle kicker, frames through the air
    park = run[int(len(run) * 0.55):int(len(run) * 0.85)]
    (x0, _, z0, _), (x1, _, z1, _) = park[0], park[-1]
    n = math.hypot(x1 - x0, z1 - z0) or 1
    pux, puz = (x1 - x0) / n, (z1 - z0) / n
    px0, pz0 = x0 - puz * 26, z0 + pux * 26
    kick = (px0 + pux * n / 2, pz0 + puz * n / 2)
    yaw = math.degrees(math.atan2(-pux, puz))
    for cam, label in (("back", "jumpb"), ("front", "jumpf")):
        ph.call(P, "/camera", mode=cam)
        start = (kick[0] - pux * 34, park[len(park) // 2][1] + 12, kick[1] - puz * 34)
        ph.cmd(P, f"tp @s {start[0]:.1f} 400 {start[2]:.1f}", 2)
        ph.cmd(P, f"spreadplayers {start[0]:.1f} {start[2]:.1f} 0 1 false @s", 3)
        s = ph.call(P, "/state")
        here = s.get("pos", [start[0], start[1], start[2]])
        ride(here, yaw, f"f:58@page_{label}0;f:5@page_{label}1;f:5@page_{label}2;f:5@page_{label}3;f:5@page_{label}4;f:5@page_{label}5;f:10",
             9, pitch=4)
    ph.cmd(P, "ride @s dismount")
    ph.call(P, "/camera", mode="first")
    ph.call(P, "/hud", hidden="false")


if __name__ == "__main__":
    main()
