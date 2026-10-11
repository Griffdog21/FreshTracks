"""Dev helper: try several third-person display angles for the held ski pole and screenshot each.

Writes the held-pole model straight into build/resources (what the dev client reads), reloads resources through the
bridge, and takes a side and a front screenshot per angle. The rider must already be clicked in with poles.
  python tools/dev/pole_angles.py 90 60 35
"""
import json
import math
import os
import sys
import time
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "src", "main", "resources", "assets", "freshtracks", "models", "item", "ski_pole_held.json")
BUILD = os.path.join(ROOT, "build", "resources", "main", "assets", "freshtracks", "models", "item", "ski_pole_held.json")
GRIP = 17 * 0.65  # grip centre above the model centre, after scaling


def bridge(path: str) -> None:
    urllib.request.urlopen("http://127.0.0.1:25599" + path, timeout=30).read()


def main() -> None:
    model = json.load(open(SRC, encoding="utf-8"))
    for arg in sys.argv[1:]:
        theta = float(arg)
        t = [0.0, round(-GRIP * math.cos(math.radians(theta)), 2), round(-GRIP * math.sin(math.radians(theta)), 2)]
        for hand in ("thirdperson_righthand", "thirdperson_lefthand"):
            model["display"][hand] = {"rotation": [theta, 0, 0], "translation": t, "scale": [0.65, 0.65, 0.65]}
        with open(BUILD, "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)
        bridge("/reloadres")
        time.sleep(6)
        bridge("/camera?mode=front")
        bridge("/look?yaw=90&pitch=12")
        time.sleep(1)
        bridge(f"/shot?name=pole_{int(theta)}_side")
        bridge("/look?yaw=0&pitch=12")
        time.sleep(1)
        bridge(f"/shot?name=pole_{int(theta)}_front")
        print("angle", theta, "translation", t)


if __name__ == "__main__":
    main()
