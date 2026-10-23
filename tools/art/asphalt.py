"""Asphalt for the resort's streets, and the double yellow centre line painted on it (RoadLineBlock).

The asphalt is fine dark aggregate with light and dark stones in it and a little road sand. Each painted block
carries the piece of line that crosses it, from one edge point to another (RoadLineBlock.POINTS, five a side, the
pairs on different sides: 150 pieces), so a line follows a road's curve without stepping. Writes the textures, the
block and item models, the blockstates and the loot tables.

Run: uv run --with pillow python tools/art/asphalt.py [preview_dir]
"""
from __future__ import annotations

import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "freshtracks")
DATA = os.path.join(ROOT, "data", "freshtracks")

# Must match RoadLineBlock.POINTS (sixteenths along each edge) and its pair order.
POINTS = [3, 5.5, 8, 10.5, 13]
EDGE_POINTS = 4 * len(POINTS)


def pairs() -> list[tuple[int, int]]:
    out = []
    for a in range(EDGE_POINTS):
        for b in range(a + 1, EDGE_POINTS):
            if a // len(POINTS) != b // len(POINTS):
                out.append((a, b))
    return out


def at(point: int) -> tuple[float, float]:
    """An edge point on the block's top in sixteenths: (x, z); u runs east with x, v south with z."""
    k = POINTS[point % len(POINTS)]
    return [(k, 0), (16, k), (k, 16), (0, k)][point // len(POINTS)]


def asphalt() -> Image.Image:
    rnd = random.Random(7)
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 68 + rnd.gauss(0, 3.5)
            r = rnd.random()
            if r < 0.07:
                v += rnd.uniform(14, 22)  # a pale stone in the mix
            elif r < 0.17:
                v -= rnd.uniform(7, 11)  # a dark one
            sand = rnd.random() < 0.05  # road sand, a little warm
            c = (v + (10 if sand else 0), v + (7 if sand else 0), v + 2 + (2 if sand else 0))
            px[x, y] = tuple(max(0, min(255, int(round(ch)))) for ch in c)
    return img


def painted(base: Image.Image, seg: int, a: int, b: int) -> Image.Image:
    """The base with the double line from edge point a to edge point b: two 2/16 stripes with a 2/16 gap."""
    rnd = random.Random(1000 + seg)
    img = base.copy()
    px = img.load()
    (ax, az), (bx, bz) = at(a), at(b)
    dx, dz = bx - ax, bz - az
    length = (dx * dx + dz * dz) ** 0.5
    for y in range(16):
        for x in range(16):
            cx, cz = x + 0.5, y + 0.5
            d = (dx * (cz - az) - dz * (cx - ax)) / length
            if not 0.9 <= abs(d) < 2.9:
                continue
            r = rnd.random()
            if r < 0.05:
                continue  # worn through to the asphalt
            under = px[x, y]
            if r < 0.16:
                c = (196, 150, 46)  # grimy
            else:
                c = (230, 180, 44)
            # the paint takes a little of the stone's light and dark through it
            shade = (sum(under) / 3 - 68) * 0.25
            px[x, y] = tuple(max(0, min(255, int(ch + shade + rnd.gauss(0, 3)))) for ch in c)
    return img


def write_json(path: str, obj) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def stairs_blockstate(name: str) -> dict:
    """The standard stairs blockstate: the straight, inner and outer models turned for every facing, half and shape."""
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, fy in base.items():
        for half in ("bottom", "top"):
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                model = f"freshtracks:block/{name}" + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = fy
                if half == "bottom" and shape.endswith("_left"):
                    y -= 90
                if half == "top" and shape.endswith("_right"):
                    y += 90
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                y %= 360
                if y:
                    v["y"] = y
                if half == "top" or y:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def loot(name: str, slab: bool = False) -> dict:
    entry = {"type": "minecraft:item", "name": f"freshtracks:{name}"}
    if slab:
        entry["functions"] = [{
            "function": "minecraft:set_count", "add": False, "count": 2,
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"freshtracks:{name}",
                            "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if not slab:
        pool["conditions"] = [{"condition": "minecraft:survives_explosion"}]
    return {"type": "minecraft:block", "pools": [pool], "random_sequence": f"freshtracks:blocks/{name}"}


def main(preview_dir: str | None) -> None:
    tex = os.path.join(ASSETS, "textures", "block")
    base = asphalt()
    base.save(os.path.join(tex, "asphalt.png"))
    os.makedirs(os.path.join(tex, "asphalt_line"), exist_ok=True)
    pieces = []
    for seg, (a, b) in enumerate(pairs()):
        img = painted(base, seg, a, b)
        img.save(os.path.join(tex, "asphalt_line", f"{seg}.png"))
        pieces.append(img)
        write_json(os.path.join(ASSETS, "models", "block", "asphalt_line", f"{seg}.json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"freshtracks:block/asphalt_line/{seg}", "bottom": "freshtracks:block/asphalt",
                         "side": "freshtracks:block/asphalt"}})

    t = "freshtracks:block/asphalt"
    three = {"bottom": t, "side": t, "top": t}
    models = os.path.join(ASSETS, "models", "block")
    write_json(os.path.join(models, "asphalt.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": t}})
    write_json(os.path.join(models, "asphalt_slab.json"), {"parent": "minecraft:block/slab", "textures": three})
    write_json(os.path.join(models, "asphalt_slab_top.json"), {"parent": "minecraft:block/slab_top", "textures": three})
    write_json(os.path.join(models, "asphalt_stairs.json"), {"parent": "minecraft:block/stairs", "textures": three})
    write_json(os.path.join(models, "asphalt_stairs_inner.json"), {"parent": "minecraft:block/inner_stairs", "textures": three})
    write_json(os.path.join(models, "asphalt_stairs_outer.json"), {"parent": "minecraft:block/outer_stairs", "textures": three})

    states = os.path.join(ASSETS, "blockstates")
    # turned at random block to block, so the stones don't repeat in a grid down the street
    write_json(os.path.join(states, "asphalt.json"), {"variants": {"": [{"model": t, **({"y": y} if y else {})} for y in (0, 90, 180, 270)]}})
    write_json(os.path.join(states, "asphalt_slab.json"), {"variants": {
        "type=bottom": {"model": "freshtracks:block/asphalt_slab"}, "type=double": {"model": t},
        "type=top": {"model": "freshtracks:block/asphalt_slab_top"}}})
    write_json(os.path.join(states, "asphalt_stairs.json"), stairs_blockstate("asphalt_stairs"))
    write_json(os.path.join(states, "asphalt_line.json"), {"variants": {
        f"seg={seg}": {"model": f"freshtracks:block/asphalt_line/{seg}"} for seg in range(len(pieces))}})

    items = os.path.join(ASSETS, "items")
    for name, model in (("asphalt", "asphalt"), ("asphalt_slab", "asphalt_slab"), ("asphalt_stairs", "asphalt_stairs"),
                        ("asphalt_line", "asphalt_line/" + str(pairs().index((2, 12))))):
        write_json(os.path.join(items, f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"freshtracks:block/{model}"}})

    tables = os.path.join(DATA, "loot_table", "blocks")
    write_json(os.path.join(tables, "asphalt.json"), loot("asphalt"))
    write_json(os.path.join(tables, "asphalt_stairs.json"), loot("asphalt_stairs"))
    write_json(os.path.join(tables, "asphalt_slab.json"), loot("asphalt_slab", slab=True))
    write_json(os.path.join(tables, "asphalt_line.json"), loot("asphalt_line"))

    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        cols = 15
        sheet = Image.new("RGB", (cols * 17, (len(pieces) + cols - 1) // cols * 17), (255, 0, 255))
        for i, img in enumerate(pieces):
            sheet.paste(img, ((i % cols) * 17, (i // cols) * 17))
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(os.path.join(preview_dir, "asphalt_lines.png"))
        # a straight run and a curve, laid block by block, to check the pieces meet
        road = Image.new("RGB", (16 * 12, 16 * 6))
        for x in range(12):
            for z in range(6):
                road.paste(base, (x * 16, z * 16))
        for x in range(12):
            road.paste(pieces[pairs().index((7, 17))], (x * 16, 1 * 16))  # west 8 -> east 8 runs along a row
        road.resize((road.width * 4, road.height * 4), Image.NEAREST).save(os.path.join(preview_dir, "asphalt_road.png"))
    print("asphalt: wrote", len(pieces), "line pieces")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
