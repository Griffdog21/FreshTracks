"""Writes the JSON side of the resource pack: item definitions, item models, sounds.json and en_us.json.

Keep content listed here in step with the Java registries. Run: uv run python tools/gen_assets.py
"""
from __future__ import annotations

import json
import os

import advancement_data
import race_data
import resort_data
import ski_world_data
try:
    import snowmass_world_data  # the private Snowmass add-on's world type (not in the public repository)
except ImportError:
    snowmass_world_data = None
import village_data
import worldgen_data

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "freshtracks")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "freshtracks"


def write_data(rel: str, data) -> None:
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# tag file (relative to data/) -> values. Several writers add to the same tags, so they are written once at the end.
TAGS: dict[str, list[str]] = {}


def tag(rel: str, *values: str) -> None:
    entries = TAGS.setdefault(rel, [])
    for v in values:
        if v not in entries:
            entries.append(v)


def write_json(rel: str, data) -> None:
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ------------------------------------------------------------------------------------------------ items
# name -> (English name, model kind). "generated" = flat sprite, "handheld" = held like a tool.
ITEMS: dict[str, tuple[str, str]] = {
    "wooden_skis": ("Wooden Skis", "handheld"),
    "alpine_skis": ("Alpine Skis", "handheld"),
    "race_skis": ("Race Skis", "handheld"),
    "powder_skis": ("Powder Skis", "handheld"),
    "park_skis": ("Park Skis", "handheld"),
    "snurfer": ("Snurfer", "handheld"),
    "all_mountain_board": ("All-Mountain Snowboard", "handheld"),
    "powder_board": ("Powder Snowboard", "handheld"),
    "park_board": ("Park Snowboard", "handheld"),
    "lift_cable": ("Lift Cable", "generated"),
    "ski_poles": ("Ski Poles", "handheld"),
    "ski_wax": ("Ski Wax", "generated"),
    "hot_cocoa": ("Hot Cocoa", "generated"),
    "ski_boots": ("Ski Boots", "generated"),
    "snowboard_boots": ("Snowboard Boots", "generated"),
    "carving_skis": ("Carving Skis", "handheld"),
    "freeride_skis": ("Freeride Skis", "handheld"),
    "touring_skis": ("Touring Skis", "handheld"),
    "retro_skis": ("Retro Skis", "handheld"),
    "freeride_board": ("Freeride Snowboard", "handheld"),
    "carving_board": ("Carving Snowboard", "handheld"),
    "splitboard": ("Splitboard", "handheld"),
    "retro_board": ("Retro Snowboard", "handheld"),
}

# Dyeable gear: name -> (English, default colour, equipment layer kind)
DYEABLE: dict[str, tuple[str, str, str]] = {
    "ski_goggles": ("Ski Goggles", "#2B2D33", "humanoid"),
    "ski_helmet": ("Ski Helmet", "#F2F4F7", "humanoid"),
    "beanie": ("Beanie", "#D8392B", "humanoid"),
    "ski_jacket": ("Ski Jacket", "#FF7A2E", "humanoid"),
    "snow_pants": ("Snow Pants", "#2A3550", "humanoid_leggings"),
    "puffer_jacket": ("Puffer Jacket", "#2B6CD9", "humanoid"),
    "shell_jacket": ("Shell Jacket", "#3FA65E", "humanoid"),
    "anorak": ("Anorak", "#E0A93A", "humanoid"),
    "retro_jacket": ("Retro Jacket", "#7B3FD9", "humanoid"),
    "race_suit": ("Race Suit", "#D8392B", "humanoid"),
}
PLAIN_ARMOR = {"ski_boots": "humanoid", "snowboard_boots": "humanoid"}
ARMOR_SLOT_TAGS = {
    "head_armor": ["ski_goggles", "ski_helmet", "beanie"],
    "chest_armor": ["ski_jacket", "puffer_jacket", "shell_jacket", "anorak", "retro_jacket", "race_suit"],
    "leg_armor": ["snow_pants"],
    "foot_armor": ["ski_boots", "snowboard_boots"],
}


def argb(hex_colour: str) -> int:
    v = 0xFF000000 | int(hex_colour.lstrip("#"), 16)
    return v - (1 << 32)


def write_gear() -> None:
    for name, (english, colour, layer) in DYEABLE.items():
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}",
                                                    "tints": [{"type": "minecraft:dye", "default": argb(colour)},
                                                              {"type": "minecraft:constant", "value": -1}]}})
        write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated",
                                                "textures": {"layer0": f"{NS}:item/{name}", "layer1": f"{NS}:item/{name}_overlay"}})
        layers = [{"texture": f"{NS}:{name}", "dyeable": {"color_when_undyed": argb(colour)}}, {"texture": f"{NS}:{name}_overlay"}]
        write_json(f"equipment/{name}.json", {"layers": {layer: layers}})
        LANG[f"item.{NS}.{name}"] = english
        write_data(f"{NS}/recipe/{name}_dyed.json", {"type": "minecraft:crafting_dye", "dye": "#minecraft:dyes",
                                                      "group": "freshtracks_dyed_gear", "result": {"id": f"{NS}:{name}"},
                                                      "target": f"{NS}:{name}"})
    for name, layer in PLAIN_ARMOR.items():
        write_json(f"equipment/{name}.json", {"layers": {layer: [{"texture": f"{NS}:{name}"}]}})
    wearables = list(DYEABLE) + list(PLAIN_ARMOR)
    tag("minecraft/tags/item/freeze_immune_wearables.json", *[f"{NS}:{n}" for n in wearables])
    for tag_name, names in ARMOR_SLOT_TAGS.items():
        tag(f"minecraft/tags/item/{tag_name}.json", *[f"{NS}:{n}" for n in names])


def shaped(name, pattern, key, count=1, category="equipment"):
    write_data(f"{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key,
                                             "pattern": pattern, "result": {"id": f"{NS}:{name}", "count": count}})


def shapeless(name, ingredients, count=1, category="misc", result=None):
    write_data(f"{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category,
                                             "ingredients": ingredients, "result": {"id": result or f"{NS}:{name}", "count": count}})


def write_recipes() -> None:
    shaped("wooden_skis", ["P P", "P P", "S S"], {"P": "#minecraft:planks", "S": "minecraft:string"})
    shaped("alpine_skis", ["I I", "P P", "S S"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "S": "minecraft:string"})
    shaped("snurfer", ["  S", "PPP"], {"P": "#minecraft:planks", "S": "minecraft:string"})
    shaped("carving_skis", ["I I", "I I", "R R"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone"})
    shaped("freeride_skis", ["I I", "P P", "F F"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "F": "minecraft:feather"})
    shaped("touring_skis", ["P P", "L L", "S S"], {"P": "#minecraft:planks", "L": "minecraft:leather", "S": "minecraft:string"})
    shaped("freeride_board", ["IFI", "PPP"], {"I": "minecraft:iron_ingot", "F": "minecraft:feather", "P": "#minecraft:planks"})
    shaped("carving_board", ["III", "PRP"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "P": "#minecraft:planks"})
    shaped("splitboard", ["PLP", "P P"], {"P": "#minecraft:planks", "L": "minecraft:leather"})
    shaped("puffer_jacket", ["W W", "WFW", "WWW"], {"W": "#minecraft:wool", "F": "minecraft:feather"})
    shaped("shell_jacket", ["L L", "LSL", "LLL"], {"L": "minecraft:leather", "S": "minecraft:slime_ball"})
    shaped("anorak", ["W W", "WSW", "WWW"], {"W": "#minecraft:wool", "S": "minecraft:string"})
    shaped("retro_jacket", ["W W", "WGW", "WWW"], {"W": "#minecraft:wool", "G": "minecraft:glowstone_dust"})
    shaped("race_suit", ["L L", "LGL", "LLL"], {"L": "minecraft:leather", "G": "minecraft:gold_ingot"})
    shaped("all_mountain_board", ["III", "PPP"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks"})
    shaped("ski_poles", ["S S", "S S", "N N"], {"S": "minecraft:stick", "N": "minecraft:iron_nugget"})
    shaped("ski_goggles", ["SSS", "GNG"], {"S": "minecraft:string", "G": "minecraft:glass_pane", "N": "minecraft:iron_nugget"})
    shapeless("ski_helmet", [f"{NS}:ski_goggles", "minecraft:iron_helmet"], category="equipment")
    shaped("beanie", ["WWW", "W W"], {"W": "#minecraft:wool"})
    shaped("ski_jacket", ["W W", "WLW", "WWW"], {"W": "#minecraft:wool", "L": "minecraft:leather"})
    shaped("snow_pants", ["WWW", "L L", "W W"], {"W": "#minecraft:wool", "L": "minecraft:leather"})
    shaped("ski_boots", ["I I", "L L"], {"I": "minecraft:iron_ingot", "L": "minecraft:leather"})
    shaped("snowboard_boots", ["S S", "L L"], {"S": "minecraft:string", "L": "minecraft:leather"})
    shapeless("ski_wax", ["minecraft:honeycomb", "minecraft:snowball"], count=2)
    shapeless("hot_cocoa", ["minecraft:bowl", "minecraft:cocoa_beans", "minecraft:milk_bucket", "minecraft:sugar"])
    shaped("snowy_road", ["SSS", "GGG"], {"S": "minecraft:snow", "G": "minecraft:gravel"}, count=6, category="building")
    shaped("lift_terminal", ["I I", "IRI", "SSS"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "S": "minecraft:smooth_stone"}, category="building")
    shaped("lift_tower", ["B", "B"], {"B": "minecraft:iron_bars"}, count=2, category="building")
    shaped("lift_tower_head", ["III", " B "], {"I": "minecraft:iron_ingot", "B": "minecraft:iron_bars"}, category="building")
    shaped("lift_cable", ["SSS", "SIS", "SSS"], {"S": "minecraft:string", "I": "minecraft:iron_ingot"}, category="misc")
    shaped("magic_carpet", ["WWW", "IRI"], {"W": "minecraft:black_wool", "I": "minecraft:iron_ingot", "R": "minecraft:redstone"}, count=6, category="building")
    shaped("ski_bench", ["WW", "PP", "PP"], {"W": f"{NS}:ski_wax", "P": "#minecraft:planks"}, category="building")
    shaped("board_press", ["III", "P P", "PPP"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks"}, category="building")
    shaped("lift_control", ["LRL", "SSS"], {"L": "minecraft:lever", "R": "minecraft:redstone", "S": "minecraft:smooth_stone"}, category="building")

# ------------------------------------------------------------------------------------------------ blocks
# name -> (English name, model kind, extra blockstate behaviour)
BLOCKS: dict[str, tuple[str, str]] = {
    "lift_terminal": ("Lift Terminal", "pad"),
    "lift_tower": ("Lift Tower Section", "pole"),
    "lift_tower_head": ("Lift Tower Head", "head"),
    "magic_carpet": ("Magic Carpet", "carpet"),
}
SNOW_LIKE = {"fresh_powder": "Fresh Powder"}
PICKAXE = ["lift_terminal", "lift_tower", "lift_tower_head"]
FACING_BLOCKS = {"lift_terminal", "magic_carpet"}


def element(frm, to, textures, uv_side=None):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        tex = textures.get(face, textures.get("side" if face not in ("up", "down") else face, textures.get("all")))
        entry = {"texture": tex}
        if uv_side and face not in ("up", "down"):
            entry["uv"] = uv_side
        if face == "down":
            entry["cullface"] = "down"
        faces[face] = entry
    return {"from": frm, "to": to, "faces": faces}


def block_model(name: str, kind: str):
    if kind == "pad":
        tex = {"up": "#top", "down": "#side", "side": "#side"}
        return {"parent": "minecraft:block/block",
                "textures": {"particle": f"{NS}:block/{name}_side", "top": f"{NS}:block/{name}_top", "side": f"{NS}:block/{name}_side"},
                "elements": [element([0, 0, 0], [16, 10, 16], tex, [0, 6, 16, 16])]}
    if kind == "pole":
        return {"parent": "minecraft:block/block",
                "textures": {"particle": f"{NS}:block/{name}", "all": f"{NS}:block/{name}"},
                "elements": [element([5, 0, 5], [11, 16, 11], {"all": "#all"})]}
    if kind == "carpet":
        return {"parent": "minecraft:block/block",
                "textures": {"particle": f"{NS}:block/{name}", "top": f"{NS}:block/{name}", "side": f"{NS}:block/{name}_side"},
                "elements": [element([0, 0, 0], [16, 2, 16], {"up": "#top", "down": "#side", "side": "#side"}, [0, 14, 16, 16])]}
    if kind == "head":
        return {"parent": "minecraft:block/block",
                "textures": {"particle": f"{NS}:block/{name}", "all": f"{NS}:block/{name}"},
                "elements": [element([4, 0, 4], [12, 12, 12], {"all": "#all"})]}
    raise ValueError(kind)


def write_groomed() -> None:
    """Corduroy snow: snow-layer heights with a ridged top; the lines turn with the axis."""
    name = "groomed_snow"
    variants = {}
    for layers in range(1, 9):
        h = layers * 2
        model = f"{NS}:block/{name}_height{h}"
        tex = {"up": "#top", "down": "#side", "side": "#side"}
        write_json(f"models/block/{name}_height{h}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": "minecraft:block/snow", "top": f"{NS}:block/{name}", "side": "minecraft:block/snow"},
            "elements": [element([0, 0, 0], [16, h, 16], tex, [0, 16 - h, 16, 16])]})
        for axis, rot in (("z", 0), ("x", 90)):
            v = {"model": model}
            if rot:
                v["y"] = rot
            variants[f"axis={axis},layers={layers}"] = v
    write_json(f"blockstates/{name}.json", {"variants": variants})
    write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}_height2"}})
    LANG[f"block.{NS}.{name}"] = "Groomed Snow"
    write_data(f"{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": "minecraft:snowball", "conditions": [{"condition": "minecraft:match_tool",
                                                                                 "predicate": {"items": "#minecraft:shovels"}}]}]}]})
    tag("minecraft/tags/block/mineable/shovel.json", f"{NS}:{name}")
    tag("minecraft/tags/block/snow.json", f"{NS}:{name}")


def write_snowy_road() -> None:
    """A service road under packed snow: snow-layer heights, gravel and tyre ruts on top; the ruts turn with the axis."""
    name = "snowy_road"
    variants = {}
    for layers in range(1, 9):
        h = layers * 2
        model = f"{NS}:block/{name}_height{h}"
        tex = {"up": "#top", "down": "#side", "side": "#side"}
        write_json(f"models/block/{name}_height{h}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": f"{NS}:block/{name}", "top": f"{NS}:block/{name}", "side": f"{NS}:block/{name}_side"},
            "elements": [element([0, 0, 0], [16, h, 16], tex, [0, 16 - h, 16, 16])]})
        for axis, rot in (("z", 0), ("x", 90)):
            v = {"model": model}
            if rot:
                v["y"] = rot
            variants[f"axis={axis},layers={layers}"] = v
    write_json(f"blockstates/{name}.json", {"variants": variants})
    write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}_height2"}})
    LANG[f"block.{NS}.{name}"] = "Snowy Road"
    write_data(f"{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tag("minecraft/tags/block/mineable/shovel.json", f"{NS}:{name}")
    tag("minecraft/tags/block/snow.json", f"{NS}:{name}")


def write_snow_like() -> None:
    for name, english in SNOW_LIKE.items():
        heights = {1: 2, 2: 4, 3: 6, 4: 8, 5: 10, 6: 12, 7: 14}
        variants = {}
        for layers in range(1, 9):
            if layers == 8:
                model = f"{NS}:block/{name}_block"
                write_json(f"models/block/{name}_block.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
            else:
                model = f"{NS}:block/{name}_height{heights[layers]}"
                write_json(f"models/block/{name}_height{heights[layers]}.json", {"parent": f"minecraft:block/snow_height{heights[layers]}",
                                                                                "textures": {"particle": f"{NS}:block/{name}", "texture": f"{NS}:block/{name}"}})
            variants[f"layers={layers}"] = {"model": model}
        write_json(f"blockstates/{name}.json", {"variants": variants})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}_height2"}})
        LANG[f"block.{NS}.{name}"] = english
        # same drops as vanilla snow layers (snowballs per layer)
        vanilla = os.path.join(ROOT, "..", "FreshTracks-ref", "mc-assets", "data", "minecraft", "loot_table", "blocks", "snow.json")
        with open(vanilla, encoding="utf-8") as f:
            loot = f.read().replace('"minecraft:snow"', f'"{NS}:{name}"')
        write_data(f"{NS}/loot_table/blocks/{name}.json", json.loads(loot))
    tag("minecraft/tags/block/mineable/shovel.json", *[f"{NS}:{n}" for n in SNOW_LIKE])
    tag("minecraft/tags/block/snow.json", *[f"{NS}:{n}" for n in SNOW_LIKE])


def write_particles() -> None:
    write_json("particles/snow_spray.json", {"textures": [f"{NS}:spray_{i}" for i in range(4)]})
    write_json("particles/wind_snow.json", {"textures": [f"{NS}:wind_snow_{i}" for i in range(3)]})
    write_json("particles/sparkle.json", {"textures": [f"{NS}:sparkle_{i}" for i in range(4)]})


def write_blocks() -> None:
    for name, (english, kind) in BLOCKS.items():
        write_json(f"models/block/{name}.json", block_model(name, kind))
        if name in FACING_BLOCKS:
            variants = {f"facing={d}": {"model": f"{NS}:block/{name}", "y": r} for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
            for v in variants.values():
                if v["y"] == 0:
                    del v["y"]
            write_json(f"blockstates/{name}.json", {"variants": variants})
        else:
            write_json(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        LANG[f"block.{NS}.{name}"] = english
        write_data(f"{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "condition": {"type": "minecraft:survives_explosion"}}],
            "random_sequence": f"{NS}:blocks/{name}"})
    tag("minecraft/tags/block/mineable/pickaxe.json", *[f"{NS}:{n}" for n in PICKAXE])

# --------------------------------------------------------------------------------------------- sounds
# event -> (files, subtitle, stream)
SOUNDS: dict[str, tuple[list[str], str, bool]] = {
    "rig.click_in": (["rig/click_in1", "rig/click_in2", "rig/click_in3"], "Bindings click", False),
    "rig.click_out": (["rig/click_out1", "rig/click_out2", "rig/click_out3"], "Bindings release", False),
    "rig.carve": (["rig/carve"], "Skis carve", False),
    "rig.glide": (["rig/glide"], "Skis glide", False),
    "rig.powder": (["rig/powder"], "Powder whooshes", False),
    "rig.skid": (["rig/skid"], "Skis skid", False),
    "rig.ice_scrape": (["rig/ice_scrape"], "Edges scrape ice", False),
    "rig.rock_scrape": (["rig/rock_scrape"], "Base scrapes rock", False),
    "rig.wind": (["rig/wind"], "Wind rushes", False),
    "rig.pop": (["rig/pop1", "rig/pop2", "rig/pop3"], "Rider pops", False),
    "rig.land": (["rig/land1", "rig/land2", "rig/land3"], "Rider lands", False),
    "rig.land_powder": (["rig/land_powder1", "rig/land_powder2", "rig/land_powder3"], "Rider lands in powder", False),
    "rig.wipeout": (["rig/wipeout1", "rig/wipeout2", "rig/wipeout3"], "Rider wipes out", False),
    "rig.core_shot": (["rig/core_shot"], "Base gouged", False),
    "rig.trick": (["rig/trick"], "Trick landed", False),
    "rig.bonk": (["rig/bonk1", "rig/bonk2", "rig/bonk3"], "Rider bonks", False),
    "lift.hum": (["lift/hum"], "Lift motor hums", False),
    "lift.chair_clack": (["lift/chair_clack1", "lift/chair_clack2", "lift/chair_clack3"], "Chair rolls over sheaves", False),
    "lift.bell": (["lift/bell"], "Lift bell rings", False),
    "weather.blizzard": (["weather/blizzard"], "Blizzard howls", True),
}

LANG: dict[str, str] = {
    "key.category.freshtracks.riding": "Fresh Tracks riding",
    "key.freshtracks.ride_camera": "Toggle helmet / chase camera",
    "key.freshtracks.ride_settings": "Riding settings",
    "ride.freshtracks.camera": "Riding view",
    "ride.freshtracks.camera.chase": "Chase (third person)",
    "ride.freshtracks.camera.helmet": "Helmet (first person)",
    "ride.freshtracks.camera.keep": "Keep current view",
    "ride.freshtracks.comfort": "Reduced motion keeps the horizon level.",
    "ride.freshtracks.effects": "Speed effects",
    "ride.freshtracks.effects.full": "Full",
    "ride.freshtracks.effects.off": "Off",
    "ride.freshtracks.effects.reduced": "Reduced motion",
    "ride.freshtracks.hint": "%s: helmet / chase view  ·  %s: riding settings",
    "ride.freshtracks.state.air": "AIR",
    "ride.freshtracks.state.brake": "BRAKING",
    "ride.freshtracks.state.carve": "CARVING",
    "ride.freshtracks.state.glide": "GLIDING",
    "ride.freshtracks.state.skid": "SKIDDING",
    "ride.freshtracks.state.tow": "LIFT TOW",
    "ride.freshtracks.state.tuck": "TUCKING",
    "ride.freshtracks.wind": "Wind rush",
    "screen.freshtracks.riding": "Fresh Tracks · Riding",
    "itemGroup.freshtracks": "Fresh Tracks",
    "entity.freshtracks.rig": "Skis",
    "tooltip.freshtracks.click_in": "Use to click in. Sneak when stopped to step out.",
    "tooltip.freshtracks.stat.speed": "Speed       ",
    "tooltip.freshtracks.stat.grip": "Edge grip   ",
    "tooltip.freshtracks.stat.turn": "Carving     ",
    "tooltip.freshtracks.stat.float": "Powder float",
    "tooltip.freshtracks.stat.pop": "Pop         ",
    "tooltip.freshtracks.stat.spin": "Spin        ",
    "message.freshtracks.core_shot": "Core shot! Your base needs a tune.",
    "entity.freshtracks.lift_seat": "Lift Chair",
    "message.freshtracks.lift.unlinked": "This terminal isn't connected yet. Click it with a Lift Cable, then click the other terminal.",
    "message.freshtracks.lift.started": "%s is running",
    "message.freshtracks.lift.stopped": "%s is closed",
    "message.freshtracks.lift.renamed": "Lift renamed to %s",
    "message.freshtracks.lift.running": "OPEN",
    "message.freshtracks.lift.closed": "CLOSED",
    "message.freshtracks.cable.started": "Cable clipped on. Click tower heads in order (or skip), then the other terminal.",
    "message.freshtracks.cable.cancelled": "Cable unclipped.",
    "message.freshtracks.cable.already_linked": "That terminal already has a lift.",
    "message.freshtracks.cable.start_gone": "The first terminal is gone. Start again.",
    "message.freshtracks.cable.too_short": "Terminals must be at least 12 blocks apart.",
    "message.freshtracks.cable.too_long": "That's too long for one lift (1200 blocks max).",
    "message.freshtracks.cable.need_terminal": "Click a lift terminal first.",
    "message.freshtracks.cable.tower_added": "Tower %s added to the line.",
    "message.freshtracks.cable.done": "%s is open! %s towers, %s blocks of rope. Stand at the loading spot to ride.",
    "message.freshtracks.no_room": "No room for your skis here. Step away from the wall.",
    "tooltip.freshtracks.cable.howto": "Click a lift terminal, then the other terminal.",
    "effect.freshtracks.warmth": "Warmth",
    "weather.freshtracks.clear": "Clear Skies",
    "weather.freshtracks.clear.desc": "Nothing special. Good day to ride.",
    "weather.freshtracks.bluebird": "Bluebird Day",
    "weather.freshtracks.bluebird.desc": "Not a cloud in the sky. Bring goggles, the glare is fierce.",
    "weather.freshtracks.powder_day": "POWDER DAY!",
    "weather.freshtracks.powder_day.desc": "Fresh snow fell overnight. Go get first tracks!",
    "weather.freshtracks.snowstorm": "Snowstorm",
    "weather.freshtracks.snowstorm.desc": "It's dumping. Fresh powder is piling up.",
    "weather.freshtracks.blizzard": "Blizzard",
    "weather.freshtracks.blizzard.desc": "Whiteout and howling wind. Dress warm, goggles on. Lifts run slow.",
    "weather.freshtracks.thundersnow": "Thundersnow",
    "weather.freshtracks.thundersnow.desc": "Lightning in a snowstorm! Lifts are closed until it passes.",
    "weather.freshtracks.high_wind": "High Wind",
    "weather.freshtracks.high_wind.desc": "Big gusts up high. Expect wind holds on the lifts.",
    "weather.freshtracks.cold_snap": "Cold Snap",
    "weather.freshtracks.cold_snap.desc": "Bitter cold and icy groomers. Bundle up.",
    "weather.freshtracks.flat_light": "Flat Light",
    "weather.freshtracks.flat_light.desc": "Fog on the mountain. Hard to see the bumps.",
    "weather.freshtracks.spring_slush": "Spring Slush",
    "weather.freshtracks.spring_slush.desc": "Warm sun and soft snow. Perfect for pond skimming.",
    "message.freshtracks.cold": "You're freezing! Dress warmer (need %s warmth), drink hot cocoa or find a fire.",
    "message.freshtracks.lift.wind_slow": "Lift running slow for the wind",
    "message.freshtracks.lift.lightning_hold": "Lightning hold: the lift is stopped",
    "tooltip.freshtracks.warmth": "Warmth",
    "tooltip.freshtracks.perk.goggles": "Cuts snow glare and helps you see in blizzards",
    "tooltip.freshtracks.perk.ski_boots": "Better edge grip on skis. Walk on powder snow.",
    "tooltip.freshtracks.perk.board_boots": "Better edge grip on a board. Walk on powder snow.",
    "tooltip.freshtracks.perk.race": "Skin-tight: a little faster on skis and boards (but not warm)",
    "tooltip.freshtracks.wax": "Hold skis or a board in your other hand and use",
    "tooltip.freshtracks.waxed": "Waxed: %s min of fast glide",
    "message.freshtracks.wax.howto": "Hold your skis or board in your other hand to wax them",
    "message.freshtracks.wax.done": "Waxed! %s minutes of fast glide",
    "tooltip.freshtracks.magic_carpet": "Carries anyone standing on it the way the arrows point.",
    "mount.freshtracks.rig": "Clicked in! Hold %s to brake. Hold it when stopped to step out.",
    "mount.freshtracks.chair": "Enjoy the ride. Bar down! (hold %s to jump off, careful)",
    "tooltip.freshtracks.cable.linking": "Clipped to %s (%s towers)",
}


def write_poles() -> None:
    """Ski poles: flat icon in menus, a real 3D pole in each hand while skiing (AvatarRendererMixin copies the poles to
    the other hand)."""
    def face(uv):
        return {"uv": uv, "texture": "#pole"}

    def box(frm, to, side, end):
        return {"from": frm, "to": to, "faces": {"north": face(side), "south": face(side), "east": face(side),
                                                 "west": face(side), "up": face(end), "down": face(end)}}
    held = {"textures": {"pole": f"{NS}:item/ski_pole_model", "particle": "#pole"},
            "elements": [box([7, 22, 7], [9, 28, 9], [0, 0, 2, 6], [0, 0, 2, 2]),
                         box([7.5, -2, 7.5], [8.5, 22, 8.5], [3, 0, 4, 16], [3, 0, 4, 1]),
                         box([5.5, 0, 5.5], [10.5, 0.75, 10.5], [6, 0, 11, 1], [6, 2, 11, 7]),
                         box([7.75, -4, 7.75], [8.25, -2, 8.25], [13, 0, 14, 2], [13, 0, 14, 1]),
                         box([6.75, 25, 7.5], [7, 27.5, 8.5], [15, 0, 16, 3], [15, 0, 16, 1])],
            "display": {k: v for k, v in POLE_DISPLAY.items()}}
    write_json("models/item/ski_pole_held.json", held)
    write_json("items/ski_poles.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": f"{NS}:item/ski_poles"}}],
        "fallback": {"type": "minecraft:model", "model": f"{NS}:item/ski_pole_held"}}})


POLE_DISPLAY = {
    "thirdperson_righthand": {"rotation": [25, 0, 0], "translation": [0, -10.0, -4.67], "scale": [0.65, 0.65, 0.65]},
    "thirdperson_lefthand": {"rotation": [25, 0, 0], "translation": [0, -10.0, -4.67], "scale": [0.65, 0.65, 0.65]},
    "firstperson_righthand": {"rotation": [20, -10, 0], "translation": [0, -4, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [20, 10, 0], "translation": [0, -4, 0], "scale": [0.5, 0.5, 0.5]},
}


def main() -> None:
    write_blocks()
    write_snow_like()
    write_groomed()
    write_snowy_road()
    write_particles()
    write_gear()
    write_recipes()
    worldgen_data.write(write_json, write_data, LANG, tag)
    village_data.write(write_json, write_data, LANG, tag, element)
    race_data.write(write_json, write_data, LANG, tag, shaped, shapeless)
    resort_data.write(write_json, write_data, LANG, tag, shaped, shapeless)
    ski_world_data.write(write_data, LANG, tag)
    if snowmass_world_data is not None:
        snowmass_world_data.write(write_data, LANG, tag)
    advancement_data.write(write_data, LANG, tag)
    for name, (english, kind) in ITEMS.items():
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        parent = "minecraft:item/handheld" if kind == "handheld" else "minecraft:item/generated"
        write_json(f"models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})
        LANG[f"item.{NS}.{name}"] = english

    write_poles()

    sounds = {}
    for event, (files, subtitle, stream) in SOUNDS.items():
        entries = [{"name": f"{NS}:{f}", "stream": True} if stream else f"{NS}:{f}" for f in files]
        key = f"subtitles.{NS}.{event}"
        sounds[event] = {"sounds": entries, "subtitle": key}
        LANG[key] = subtitle
    write_json("sounds.json", sounds)
    write_json("lang/en_us.json", dict(sorted(LANG.items())))
    for rel, values in TAGS.items():
        write_data(rel, {"values": values})
    print(f"assets: {len(ITEMS)} items, {len(SOUNDS)} sound events, {len(LANG)} lang keys")


if __name__ == "__main__":
    main()
