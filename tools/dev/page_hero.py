"""Hero shots for the website: riding with the HUD hidden. Also checks where a new player spawns in the packaged test
world. Needs gradlew prodClient running (bridge 25601) with the test world unzipped into run-prod/saves.
"""
from __future__ import annotations

import math
import time

import page_shots as ps


def main() -> None:
    ps.wait_ready()
    if "pos" not in ps.state():
        ps.call("/world", action="load", name=ps.WORLD)
        while "pos" not in ps.state():
            s = ps.state()
            if s.get("screen") in ("BackupConfirmScreen", "DetailedBackupConfirmScreen"):
                ps.call("/click", text="I Know What I'm Doing!")
            time.sleep(2)
    s = ps.state()
    print("spawned at", s.get("pos"), "biome", s.get("biome"), "game mode check:", s.get("health"))
    ps.call("/clean")
    time.sleep(1)
    print(ps.call("/shot", name="page_spawn", scale="1")["file"])
    for c in ("gamerule send_command_feedback false", "gamerule show_advancement_messages false", "time set 2500",
              "weather clear", "gamerule advance_time false", "freshtracks weather set bluebird",
              "advancement grant @s from freshtracks:root"):
        ps.cmd(c)
    time.sleep(1.5)
    ps.call("/clean")
    run = ps.run_points()
    ps.call("/hud", hidden="true")
    ps.cmd("clear @s")
    ps.cmd("give @s freshtracks:alpine_skis")
    for start, name in ((18, "hero1"), (30, "hero2")):
        p, q = run[start], run[start + 1]
        yaw = math.degrees(math.atan2(-(q[0] - p[0]), q[2] - p[2]))
        ps.cmd("ride @s dismount")
        ps.cmd("give @s freshtracks:alpine_skis")
        ps.cmd(f"tp @s {p[0]:.1f} {p[1] + 1:.1f} {p[2]:.1f} {yaw:.1f} 12", 5)
        ps.call("/slot", n="0")
        ps.call("/look", yaw=f"{yaw:.1f}", pitch="12")
        time.sleep(0.5)
        ps.call("/use")
        time.sleep(1.5)
        ps.call("/clean")
        ps.call("/camera", mode="back")
        ps.call("/timeline", spec=f"f:46;r:14@page_{name}a;l:18@page_{name}b;r:18@page_{name}c;l:14")
        time.sleep(7)
    ps.cmd("ride @s dismount")
    ps.call("/camera", mode="first")
    ps.call("/hud", hidden="false")
    for c in ("gamerule send_command_feedback true", "gamerule show_advancement_messages true", "gamerule advance_time true",
              "freshtracks weather auto true"):
        ps.cmd(c)


if __name__ == "__main__":
    main()
