"""Regenerates chosen regions of a Snowmass world with the current generator, keeping everything else.

  uv run --with nbtlib python tools/dev/refresh_regions.py <world dir> <backup dir> x,z [x,z ...]

Pass - as <backup dir> to keep no copies (the user keeps one backup of the current version only).
For each region: the region, entity and point-of-interest files are copied into <backup dir> and then removed, so
the server builds those chunks fresh next time they load. Player data, lifts, registrations and other saved data
are untouched. The chunks in those regions are also taken off the mountain-details "done" list, so their park
features and rock/pillow details are put back when they regenerate. The world must not be open in a game or server.
"""
from __future__ import annotations

import os
import shutil
import sys

import nbtlib


def main(argv: list[str]) -> None:
    world, backup = argv[0], argv[1]
    regions = {tuple(int(v) for v in a.split(",")) for a in argv[2:]}
    if os.path.exists(os.path.join(world, "session.lock")):
        try:
            os.remove(os.path.join(world, "session.lock"))
        except OSError:
            raise SystemExit("world is open somewhere: " + world)
    dim = os.path.join(world, "dimensions", "minecraft", "overworld")
    removed = 0
    for kind in ("region", "entities", "poi"):
        for (rx, rz) in sorted(regions):
            f = os.path.join(dim, kind, f"r.{rx}.{rz}.mca")
            if os.path.exists(f):
                if backup != "-":
                    dst = os.path.join(backup, kind)
                    os.makedirs(dst, exist_ok=True)
                    shutil.copy2(f, dst)
                os.remove(f)
                removed += 1
    details = os.path.join(dim, "data", "freshtracks", "snowmass_mountain_details.dat")
    dropped = 0
    if os.path.exists(details):
        if backup != "-":
            os.makedirs(backup, exist_ok=True)
            shutil.copy2(details, backup)
        nbt = nbtlib.load(details)
        data = nbt["data"] if "data" in nbt else nbt
        for key in [k for k in data.keys() if k.startswith("completed")]:
            keep = []
            for v in data[key]:
                v = int(v)
                cx = v & 0xFFFFFFFF
                cx = cx - (1 << 32) if cx >= 1 << 31 else cx
                cz = v >> 32
                if (cx >> 5, cz >> 5) in regions:
                    dropped += 1
                else:
                    keep.append(nbtlib.Long(v))
            data[key] = nbtlib.List[nbtlib.Long](keep)
        nbt.save()
    print(f"{world}: removed {removed} region files, {dropped} chunks will get their details back")


if __name__ == "__main__":
    main(sys.argv[1:])
