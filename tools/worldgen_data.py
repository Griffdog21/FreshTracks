"""Worldgen data for Fresh Tracks: the six alpine biomes, their trees and decorations, tags, and the JSON for the
new plant blocks. Called from gen_assets.py.

Biomes start from a copy of their vanilla parent (read from the local reference copy of the game data in
../FreshTracks-ref/mc-assets) so spawns, ores, caves and music stay vanilla; we swap the vegetation and the colours.
"""
from __future__ import annotations

import copy
import json
import os

NS = "freshtracks"
VEGETAL = 9
TOP_LAYER = 10

# Our step-9 features in one fixed order. Every biome lists a subset in this order, after its vanilla ones, so the
# game's feature-order check never finds a cycle.
STEP9_ORDER = ["trees_powder_bowls", "trees_frosted_glades", "trees_larch_basin", "trees_aspen_hollow",
               "pillows", "wind_lip", "icicles", "snow_on_branches"]


def biome_specs():
    return {
        # Wide open snowfields: few lone trees, pillow lines, natural kickers, ice crystals glinting in the air.
        "powder_bowls": dict(parent="snowy_slopes", english="Powder Bowls", temperature=-0.5,
                             sky="#6EA6FF", fog="#DCEBFF", particles=[(f"{NS}:sparkle", 0.0012)],
                             remove=[], add=["trees_powder_bowls", "pillows", "wind_lip", "icicles", "snow_on_branches"]),
        # Spaced, heavily frosted spruce and pine: tree skiing.
        "frosted_glades": dict(parent="grove", english="Frosted Glades", temperature=-0.45,
                               sky="#7FA2FF", fog="#D6E4F5", particles=[("minecraft:snowflake", 0.0005)],
                               remove=["minecraft:trees_grove"], add=["trees_frosted_glades", "pillows", "icicles", "snow_on_branches"]),
        # Craggy summits with a rosy haze, like the last light on the peaks.
        "alpenglow_peaks": dict(parent="jagged_peaks", english="Alpenglow Peaks", temperature=-0.7,
                                sky="#7FA0FF", fog="#DCD6F0", remove=[], add=["wind_lip", "icicles"]),
        # Blue ice and packed ice under thin snow.
        "glacier": dict(parent="frozen_peaks", english="Glacier", temperature=-0.8,
                        sky="#7FC6FF", fog="#CDEFFD", water="#76C9F2", particles=[(f"{NS}:sparkle", 0.0025)],
                        remove=[], add=["icicles"]),
        # Golden larches among dark spruce.
        "larch_basin": dict(parent="snowy_taiga", english="Larch Basin", temperature=-0.45,
                            fog="#D2DCE9", remove=["minecraft:trees_taiga"],
                            add=["trees_larch_basin", "pillows", "icicles", "snow_on_branches"]),
        # White-barked aspen groves with gold leaves over fresh snow (a nod to Snowmass).
        "aspen_hollow": dict(parent="meadow", english="Aspen Hollow", temperature=-0.3, downfall=0.6,
                             fog="#D2DEEF", grass="#C4C27E", foliage="#E8C44A",
                             keep=["minecraft:glow_lichen"], add=["trees_aspen_hollow", "pillows", "icicles", "snow_on_branches"]),
    }


def tree(trunk: str, leaves: str, trunk_placer: dict, foliage_placer: dict, size: dict | None = None) -> dict:
    return {
        "type": "minecraft:tree",
        "below_trunk_provider": "minecraft:soil_beneath_tree",
        "decorators": [],
        "foliage_placer": foliage_placer,
        "foliage_provider": {"id": leaves, "properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}},
        "ignore_vines": True,
        "minimum_size": size or {"type": "minecraft:two_layers_feature_size", "limit": 2, "upper_size": 2},
        "trunk_placer": trunk_placer,
        "trunk_provider": {"id": trunk, "properties": {"axis": "y"}},
    }


def uniform(lo: int, hi: int) -> dict:
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}


def weighted(pairs: list[tuple[int, int]]) -> dict:
    return {"type": "minecraft:weighted_list", "distribution": [{"data": d, "weight": w} for d, w in pairs]}


def tree_placement(count: dict) -> list[dict]:
    return [count, {"type": "minecraft:in_square"},
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
            {"type": "minecraft:biome"}]


def on_snow(feature: str) -> dict:
    """Same filter vanilla uses for grove trees: step out of powder snow, then need snow underneath."""
    return {"feature": feature, "placement": [
        {"type": "minecraft:environment_scan", "direction_of_search": "up", "max_steps": 8,
         "target_condition": {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:powder_snow"}}},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "offset": [0, -1, 0],
                                                                   "blocks": ["minecraft:snow_block", "minecraft:powder_snow"]}}]}


def selector(default: str, options: list[tuple[float, str]]) -> dict:
    return {"type": "minecraft:random_selector", "default": default,
            "features": [{"chance": c, "feature": f} for c, f in options]}


def features() -> tuple[dict, dict]:
    """(configured features, placed features) keyed by path under our namespace."""
    conf: dict[str, dict] = {}
    placed: dict[str, dict] = {}

    # Larch: tall, narrow golden cone on a long bare trunk.
    conf["larch"] = tree("minecraft:spruce_log", f"{NS}:larch_needles",
                         {"type": "minecraft:straight_trunk_placer", "base_height": 8, "height_rand_a": 3, "height_rand_b": 2},
                         {"type": "minecraft:spruce_foliage_placer", "offset": uniform(0, 1), "radius": uniform(2, 3),
                          "trunk_height": uniform(2, 4)})
    # Aspen: slim white trunk with a tall oval crown.
    conf["aspen"] = tree("minecraft:birch_log", f"{NS}:aspen_leaves",
                         {"type": "minecraft:straight_trunk_placer", "base_height": 9, "height_rand_a": 3, "height_rand_b": 2},
                         {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 4},
                         {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1})
    placed["larch_on_snow"] = on_snow(f"{NS}:larch")
    placed["aspen_on_snow"] = on_snow(f"{NS}:aspen")
    placed["mega_spruce_on_snow"] = on_snow("minecraft:mega_spruce")

    conf["trees_powder_bowls"] = selector("minecraft:spruce_on_snow", [(0.5, "minecraft:pine_on_snow")])
    conf["trees_frosted_glades"] = selector("minecraft:spruce_on_snow", [
        (0.05, f"{NS}:mega_spruce_on_snow"), (0.33, "minecraft:pine_on_snow"), (0.12, f"{NS}:larch_on_snow")])
    conf["trees_larch_basin"] = selector(f"{NS}:larch_on_snow", [(0.25, "minecraft:spruce_on_snow")])
    conf["trees_aspen_hollow"] = selector(f"{NS}:aspen_on_snow", [(0.08, "minecraft:spruce_on_snow")])

    placed["trees_powder_bowls"] = {"feature": f"{NS}:trees_powder_bowls",
                                    "placement": tree_placement({"type": "minecraft:count", "count": weighted([(0, 7), (1, 2), (2, 1)])})}
    placed["trees_frosted_glades"] = {"feature": f"{NS}:trees_frosted_glades",
                                      "placement": tree_placement({"type": "minecraft:count", "count": weighted([(2, 3), (3, 4), (4, 3)])})}
    placed["trees_larch_basin"] = {"feature": f"{NS}:trees_larch_basin",
                                   "placement": tree_placement({"type": "minecraft:count", "count": weighted([(4, 5), (5, 4), (7, 1)])})}
    # Aspens grow in groves: dense where the biome noise is high, scattered elsewhere.
    placed["trees_aspen_hollow"] = {"feature": f"{NS}:trees_aspen_hollow",
                                    "placement": tree_placement({"type": "minecraft:noise_threshold_count", "noise_level": 0.0,
                                                                 "below_noise": 3, "above_noise": 8})}

    # Code features (no settings).
    for name in ("alpine_surface", "pillows", "wind_lip", "icicles", "snow_on_branches"):
        conf[name] = {"type": f"{NS}:{name}"}
    # Whole-chunk passes: no placement, the origin is the chunk corner and the code checks biomes per column.
    placed["alpine_surface"] = {"feature": f"{NS}:alpine_surface", "placement": []}
    placed["snow_on_branches"] = {"feature": f"{NS}:snow_on_branches", "placement": []}
    placed["pillows"] = {"feature": f"{NS}:pillows", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": "minecraft:biome"}]}
    placed["wind_lip"] = {"feature": f"{NS}:wind_lip", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": "minecraft:biome"}]}
    placed["icicles"] = {"feature": f"{NS}:icicles", "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": "minecraft:biome"}]}
    return conf, placed


def build_biome(ref_dir: str, spec: dict) -> dict:
    with open(os.path.join(ref_dir, "worldgen", "biome", spec["parent"] + ".json"), encoding="utf-8") as f:
        b = json.load(f)
    b = copy.deepcopy(b)
    b["temperature"] = spec["temperature"]
    if "downfall" in spec:
        b["downfall"] = spec["downfall"]
    b["has_precipitation"] = True
    attrs = b.setdefault("attributes", {})
    if "sky" in spec:
        attrs["minecraft:visual/sky_color"] = spec["sky"]
    if "fog" in spec:
        attrs["minecraft:visual/fog_color"] = spec["fog"]
    if spec.get("particles"):
        attrs["minecraft:visual/ambient_particles"] = {
            "argument": [{"particle": {"type": p}, "probability": prob} for p, prob in spec["particles"]],
            "modifier": "append"}
    effects = b.setdefault("effects", {})
    if "water" in spec:
        effects["water_color"] = spec["water"]
    if "grass" in spec:
        effects["grass_color"] = spec["grass"]
    if "foliage" in spec:
        effects["foliage_color"] = spec["foliage"]
    steps = b["features"]
    while len(steps) <= TOP_LAYER:
        steps.append([])
    veg = steps[VEGETAL]
    if "keep" in spec:
        veg = [f for f in veg if f in spec["keep"]]
    veg = [f for f in veg if f not in spec.get("remove", [])]
    for name in STEP9_ORDER:
        if name in spec["add"]:
            veg.append(f"{NS}:{name}")
    steps[VEGETAL] = veg
    steps[TOP_LAYER] = [f"{NS}:alpine_surface"] + [f for f in steps[TOP_LAYER] if f != f"{NS}:alpine_surface"]
    return b


# ------------------------------------------------------------------------------------------- surface rules
# The ground of each biome comes from the game's material rules (data/minecraft/worldgen/material_rule). Vanilla
# picks a per-biome rule in overworld/biome_surface (top block) and overworld/under_biome_surface (the few blocks
# below); we put our biomes at the front of both lists and keep everything else as vanilla wrote it.
SURFACE_STYLE = {"powder_bowls": "powder_bowls", "frosted_glades": "snowy_woods", "larch_basin": "snowy_woods",
                 "aspen_hollow": "snowy_woods", "alpenglow_peaks": "alpenglow_peaks", "glacier": "glacier"}


def _block(state: str) -> dict:
    return {"type": "minecraft:block", "result_state": state}


def _when(condition, then) -> dict:
    return {"type": "minecraft:condition", "if_true": condition, "then_run": then}


def _seq(*rules) -> dict:
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def _noise(name: str, lo: float, hi: float) -> dict:
    return {"type": "minecraft:noise_threshold", "noise": name, "min_threshold": lo, "max_threshold": hi}


def material_rules() -> dict[str, dict]:
    steep = {"type": "minecraft:steep"}
    dry = "minecraft:not_underwater"
    snow = _when(dry, _block("minecraft:snow_block"))
    rosy_rock = _seq(_when(_noise("minecraft:surface", 0.2, 0.55), _block("minecraft:granite")), _block("minecraft:stone"))
    return {
        "surface/powder_bowls": _seq(_when(steep, snow), "minecraft:overworld/powder_snow_surface", snow),
        "under_surface/powder_bowls": _seq(_when(steep, snow), "minecraft:overworld/powder_snow_under_surface", snow),
        "surface/snowy_woods": _seq("minecraft:overworld/powder_snow_surface", snow),
        "under_surface/snowy_woods": _seq("minecraft:overworld/powder_snow_under_surface", snow),
        "surface/alpenglow_peaks": _seq(_when(steep, rosy_rock), snow),
        "under_surface/alpenglow_peaks": _seq(_when(steep, rosy_rock), snow),
        "surface/glacier": _seq(_when(steep, _block("minecraft:packed_ice")),
                                _when(_noise("minecraft:ice", 0.0, 0.08), _block("minecraft:blue_ice")),
                                _when(_noise("minecraft:packed_ice", 0.0, 0.3), _block("minecraft:packed_ice")), snow),
        "under_surface/glacier": _seq(_when(steep, _block("minecraft:packed_ice")),
                                      _when(_noise("minecraft:packed_ice", -0.5, 0.3), _block("minecraft:packed_ice")),
                                      _when(_noise("minecraft:ice", -0.1, 0.1), _block("minecraft:blue_ice")), snow),
    }


# How far below the snow surface the ski biomes stay snow (or glacier ice), so cliff faces and the sides of steps show
# snow instead of stone. Alpenglow Peaks keeps its rock: it's the big craggy mountain you look at, not ride.
DEEP_SNOW = 24
DEEP_SNOW_BIOMES = ["powder_bowls", "frosted_glades", "larch_basin", "aspen_hollow"]


def deep_snow_rule() -> dict:
    below_top = {"type": "minecraft:not", "invert": "minecraft:on_floor"}
    within = {"type": "minecraft:stone_depth", "offset": DEEP_SNOW, "surface_type": "floor", "add_surface_depth": True,
              "secondary_depth_range": 0}
    dry = "minecraft:not_underwater"
    snow = {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [f"{NS}:{b}" for b in DEEP_SNOW_BIOMES]},
            "then_run": _block("minecraft:snow_block")}
    ice = {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": f"{NS}:glacier"},
           "then_run": _seq(_when(_noise("minecraft:ice", -0.12, 0.12), _block("minecraft:blue_ice")), _block("minecraft:packed_ice"))}
    return _when(below_top, _when(within, _when(dry, _seq(snow, ice))))


def surface_overrides(ref_dir: str) -> dict[str, dict]:
    out = {}
    # The whole overworld surface pass: our deep snow goes first (top blocks still come from biome_surface below).
    with open(os.path.join(ref_dir, "worldgen", "material_rule", "overworld", "surface.json"), encoding="utf-8") as f:
        surface = json.load(f)
    surface["sequence"] = [deep_snow_rule()] + [r for r in surface["sequence"] if not (isinstance(r, dict) and r.get("__freshtracks"))]
    out["surface"] = surface
    for vanilla, ours in (("biome_surface", "surface"), ("under_biome_surface", "under_surface")):
        with open(os.path.join(ref_dir, "worldgen", "material_rule", "overworld", vanilla + ".json"), encoding="utf-8") as f:
            rule = json.load(f)
        styles: dict[str, list[str]] = {}
        for biome, style in SURFACE_STYLE.items():
            styles.setdefault(style, []).append(f"{NS}:{biome}")
        front = [_when({"type": "minecraft:biome", "biome_is": biomes}, f"{NS}:{ours}/{style}") for style, biomes in styles.items()]
        rule["sequence"] = front + [r for r in rule["sequence"] if not (isinstance(r, dict) and str(r.get("then_run", "")).startswith(f"{NS}:"))]
        out[vanilla] = rule
    return out


# biome tag -> our biomes in it
def biome_tags(all_biomes: list[str]) -> dict[str, list[str]]:
    everything = list(all_biomes)
    return {
        f"{NS}:alpine": everything,
        # fresh powder doesn't settle here: the bowls and glades are where the stashes are
        f"{NS}:powder_stash": ["powder_bowls", "frosted_glades", "glacier"],
        "minecraft:is_overworld": everything,
        "minecraft:is_mountain": ["powder_bowls", "alpenglow_peaks", "glacier", "aspen_hollow"],
        "minecraft:is_forest": ["frosted_glades"],
        "minecraft:is_taiga": ["larch_basin"],
        "minecraft:spawns_snow_foxes": everything,
        "minecraft:spawns_white_rabbits": everything,
        "minecraft:spawns_cold_variant_farm_animals": everything,
        "minecraft:spawns_cold_variant_frogs": everything,
        "minecraft:stronghold_biased_to": everything,
        "minecraft:has_structure/trial_chambers": everything,
        "minecraft:has_structure/igloo": ["powder_bowls", "larch_basin"],
        "minecraft:has_structure/pillager_outpost": ["frosted_glades"],
        "minecraft:has_structure/village_snowy": ["aspen_hollow", "larch_basin"],
        "minecraft:has_structure/abandoned_camp_snowy_taiga": ["larch_basin"],
        # Fabric/NeoForge conventional tags, so other mods understand our biomes.
        "c:is_overworld": everything,
        "c:is_cold": everything,
        "c:is_cold/overworld": everything,
        "c:is_snowy": everything,
        "c:is_mountain": ["powder_bowls", "alpenglow_peaks", "glacier", "aspen_hollow"],
        "c:is_mountain/peak": ["alpenglow_peaks", "glacier"],
        "c:is_mountain/slope": ["powder_bowls", "aspen_hollow"],
        "c:is_icy": ["glacier"],
        "c:is_forest": ["frosted_glades", "larch_basin", "aspen_hollow"],
        "c:is_taiga": ["larch_basin"],
        "c:is_tree/coniferous": ["frosted_glades", "larch_basin"],
        "c:primary_wood_type/birch": ["aspen_hollow"],
        "c:is_sparse_vegetation/overworld": ["powder_bowls", "alpenglow_peaks", "glacier"],
    }


def leaves_loot(leaves: str, sapling: str) -> dict:
    shears_or_silk = {"type": "minecraft:any_of", "terms": ["minecraft:tool/can_shear", "minecraft:tool/can_silk_touch"]}
    return {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": shears_or_silk, "name": leaves},
                {"type": "minecraft:item", "name": sapling, "condition": {"type": "minecraft:all_of", "terms": [
                    {"type": "minecraft:survives_explosion"},
                    {"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.05, 0.0625, 0.083333336, 0.1]}]}}]}]},
            {"rolls": 1, "condition": {"type": "minecraft:inverted", "term": shears_or_silk}, "entries": [
                {"type": "minecraft:item", "name": "minecraft:stick",
                 "condition": {"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                               "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]},
                 "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                              {"type": "minecraft:explosion_decay"}]}]},
        ],
        "random_sequence": f"{NS}:blocks/{leaves.split(':')[1]}",
    }


def self_drop(name: str) -> dict:
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                  "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
            "random_sequence": f"{NS}:blocks/{name}"}


PLANTS = {
    "larch_needles": ("Larch Needles", "leaves", "larch_sapling"),
    "aspen_leaves": ("Aspen Leaves", "leaves", "aspen_sapling"),
    "snowy_fir_needles": ("Snowy Fir Boughs", "leaves", "minecraft:spruce_sapling"),
    "aspen_twigs": ("Aspen Twigs", "leaves", "aspen_sapling"),
    "larch_sapling": ("Larch Sapling", "sapling", None),
    "aspen_sapling": ("Aspen Sapling", "sapling", None),
    "icicle": ("Icicle", "icicle", None),
}


def write(write_json, write_data, lang: dict, tag) -> None:
    ref_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "FreshTracks-ref", "mc-assets", "data", "minecraft"))
    specs = biome_specs()
    for name, spec in specs.items():
        write_data(f"{NS}/worldgen/biome/{name}.json", build_biome(ref_dir, spec))
        lang[f"biome.{NS}.{name}"] = spec["english"]
    conf, placed = features()
    for name, body in conf.items():
        write_data(f"{NS}/worldgen/feature/{name}.json", body)
    for name, body in placed.items():
        write_data(f"{NS}/worldgen/placed_feature/{name}.json", body)
    for name, body in material_rules().items():
        write_data(f"{NS}/worldgen/material_rule/{name}.json", body)
    for name, body in surface_overrides(ref_dir).items():
        write_data(f"minecraft/worldgen/material_rule/overworld/{name}.json", body)
    for tag_name, members in biome_tags(list(specs)).items():
        ns, path = tag_name.split(":")
        tag(f"{ns}/tags/worldgen/biome/{path}.json", *[f"{NS}:{m}" for m in members])

    for name, (english, kind, sapling) in PLANTS.items():
        lang[f"block.{NS}.{name}"] = english
        if kind == "leaves":
            write_json(f"models/block/{name}.json", {"parent": "minecraft:block/leaves", "textures": {"all": f"{NS}:block/{name}"}})
            write_json(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
            write_data(f"{NS}/loot_table/blocks/{name}.json", leaves_loot(f"{NS}:{name}", sapling if ":" in sapling else f"{NS}:{sapling}"))
            tag("minecraft/tags/block/leaves.json", f"{NS}:{name}")
            tag("minecraft/tags/item/leaves.json", f"{NS}:{name}")
        elif kind == "sapling":
            write_json(f"models/block/{name}.json", {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/{name}"}})
            write_json(f"blockstates/{name}.json", {"variants": {"stage=0": {"model": f"{NS}:block/{name}"},
                                                                  "stage=1": {"model": f"{NS}:block/{name}"}}})
            write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{name}"}})
            write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
            write_data(f"{NS}/loot_table/blocks/{name}.json", self_drop(name))
            tag("minecraft/tags/block/saplings.json", f"{NS}:{name}")
            tag("minecraft/tags/item/saplings.json", f"{NS}:{name}")
        elif kind == "icicle":
            for part in ("tip", "body"):
                write_json(f"models/block/{name}_{part}.json", {"parent": "minecraft:block/cross",
                                                                 "textures": {"cross": f"{NS}:block/{name}_{part}"}})
            write_json(f"blockstates/{name}.json", {"variants": {"tip=true": {"model": f"{NS}:block/{name}_tip"},
                                                                  "tip=false": {"model": f"{NS}:block/{name}_body"}}})
            write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
            write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
            write_data(f"{NS}/loot_table/blocks/{name}.json", {
                "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
                "pools": [{"rolls": 1, "condition": "minecraft:tool/can_silk_touch",
                           "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}]})
