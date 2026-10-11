"""Map-building blocks: start/finish timing lines and trail difficulty markers. Called from gen_assets.py."""
from __future__ import annotations

NS = "freshtracks"
SYMBOLS = ["green", "blue", "black", "double_black", "park", "closed", "extreme", "lift"]


def _face(texture: str, uv=None, cull=None) -> dict:
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def marker_model(symbol: str) -> dict:
    pole = "#pole"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/trail_marker_{symbol}", "face": f"{NS}:block/trail_marker_{symbol}",
                     "back": f"{NS}:block/trail_marker_back", "pole": "minecraft:block/stripped_spruce_log"},
        "elements": [
            {"from": [7, 0, 9], "to": [9, 16, 11], "faces": {
                "north": _face(pole, [7, 0, 9, 16]), "south": _face(pole, [7, 0, 9, 16]), "east": _face(pole, [9, 0, 11, 16]),
                "west": _face(pole, [5, 0, 7, 16]), "up": _face(pole, [7, 7, 9, 9]), "down": _face(pole, [7, 7, 9, 9], "down")}},
            {"from": [1, 4, 8], "to": [15, 16, 9], "faces": {
                "north": _face("#face", [1, 0, 15, 12]), "south": _face("#back", [1, 2, 15, 14]),
                "east": _face("#back", [0, 0, 1, 12]), "west": _face("#back", [0, 0, 1, 12]),
                "up": _face("#back", [1, 0, 15, 1]), "down": _face("#back", [1, 0, 15, 1])}},
        ],
    }


def write(write_json, write_data, lang: dict, tag, shaped, shapeless) -> None:
    for name, english in (("start_line", "Start Line"), ("finish_line", "Finish Line")):
        write_json(f"models/block/{name}.json", {"parent": "minecraft:block/carpet", "textures": {"wool": f"{NS}:block/{name}"}})
        write_json(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        lang[f"block.{NS}.{name}"] = english
        write_data(f"{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}]})
    lang[f"tooltip.{NS}.start_line"] = "Lay a row across a run. Riding over it starts your clock."
    lang[f"tooltip.{NS}.finish_line"] = "Riding over it stops your clock. Best times are kept per course."

    variants = {}
    for symbol in SYMBOLS:
        write_json(f"models/block/trail_marker_{symbol}.json", marker_model(symbol))
        for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"{NS}:block/trail_marker_{symbol}"}
            if r:
                v["y"] = r
            variants[f"facing={d},symbol={symbol}"] = v
    write_json("blockstates/trail_marker.json", {"variants": variants})
    write_json("items/trail_marker.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/trail_marker_blue"}})
    lang[f"block.{NS}.trail_marker"] = "Trail Marker"
    lang[f"tooltip.{NS}.trail_marker"] = "Use with an empty hand to change the symbol."
    write_data(f"{NS}/loot_table/blocks/trail_marker.json", {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/trail_marker",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:trail_marker"}]}]})
    tag("minecraft/tags/block/mineable/axe.json", f"{NS}:trail_marker")

    shapeless("start_line", ["minecraft:white_carpet", "minecraft:white_carpet", "minecraft:white_carpet", "minecraft:green_dye"],
              count=3, category="building")
    shapeless("finish_line", ["minecraft:white_carpet", "minecraft:white_carpet", "minecraft:white_carpet", "minecraft:black_dye"],
              count=3, category="building")
    shapeless("trail_marker", ["#minecraft:signs", "minecraft:stick"], count=1, category="building")
