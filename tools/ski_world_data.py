"""The Ski Mountains world type: noise settings built on our ski_terrain density functions, a taller overworld
(peaks reach ~420), biomes placed by height / steepness / moisture, and the world preset itself (listed under
World Type in Create World). Called from gen_assets.py.
"""
from __future__ import annotations

import copy
import json
import os

NS = "freshtracks"
FIELDS = ["density", "height", "elevation", "steepness", "moisture", "variety", "depth"]
MIN_Y = -64
HEIGHT = 512


def _range(lo: float, hi: float) -> list[float]:
    return [lo, hi]


def _point(biome: str, continentalness, erosion=(-1.0, 1.0), humidity=(-1.0, 1.0), weirdness=(-1.0, 1.0)) -> dict:
    return {"biome": biome, "parameters": {
        "temperature": _range(-1.0, 1.0),
        "humidity": _range(*humidity),
        "continentalness": _range(*continentalness),
        "erosion": _range(*erosion),
        "depth": _range(-1.0, 1.0),
        "weirdness": _range(*weirdness),
        "offset": 0.0}}


def elevation(height: float) -> float:
    """Ground height -> the -1..1 'continentalness' our terrain reports (see SkiTerrain.elevation)."""
    return max(-1.0, min(1.0, (height - 72) / 330 * 2 - 1))


def steepness(degrees: float) -> float:
    return max(-1.0, min(1.0, degrees / 45 * 2 - 1))


def biome_points() -> list[dict]:
    valley, lower, upper, alpine, summit = elevation(96), elevation(170), elevation(268), elevation(322), 1.0
    steep = steepness(30)
    return [
        # valley floors: aspen groves where it's wetter, larch and spruce where it's drier (villages live here)
        _point(f"{NS}:aspen_hollow", (-1.0, valley), humidity=(0.05, 1.0)),
        _point(f"{NS}:larch_basin", (-1.0, valley), humidity=(-1.0, 0.05)),
        # lower mountain: gladed trees, larch on the dry side
        _point(f"{NS}:frosted_glades", (valley, lower), humidity=(-0.35, 1.0)),
        _point(f"{NS}:larch_basin", (valley, lower), humidity=(-1.0, -0.35)),
        # upper mountain: above the trees, open powder bowls; tree fingers where it's wet
        _point(f"{NS}:powder_bowls", (lower, alpine), humidity=(-1.0, 0.55)),
        _point(f"{NS}:frosted_glades", (lower, upper), humidity=(0.55, 1.0)),
        _point(f"{NS}:powder_bowls", (upper, alpine), humidity=(0.55, 1.0)),
        # the tops: craggy where steep, glacier on the high benches
        _point(f"{NS}:alpenglow_peaks", (alpine, summit), erosion=(steep, 1.0)),
        _point(f"{NS}:glacier", (alpine, summit), erosion=(-1.0, steep)),
    ]


def noise_settings() -> dict:
    ref = lambda field: f"{NS}:ski/{field}"
    return {
        "sea_level": -64,
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "default_block": "minecraft:stone",
        "default_fluid": "minecraft:water",
        "noise": {"min_y": MIN_Y, "height": HEIGHT},
        "noise_router": {
            "chunk_surface_level": ref("height"),
            "continents": ref("elevation"),
            "depth": ref("depth"),
            "erosion": ref("steepness"),
            "final_density": ref("density"),
            "ridges": ref("variety"),
            "temperature": -0.5,
            "vegetation": ref("moisture"),
        },
        "material_rule": "minecraft:overworld",
        # spawn on a flat bit of valley floor
        "spawn_target": [{ref("elevation"): [-1.0, elevation(84)], ref("steepness"): [-1.0, steepness(8)]}],
        "debug_functions": [{"function": ref(f), "label": f[0].upper()} for f in ("density", "elevation", "steepness", "moisture")],
    }


def dimension_type(ref_dir: str) -> dict:
    with open(os.path.join(ref_dir, "dimension_type", "overworld.json"), encoding="utf-8") as f:
        d = json.load(f)
    d = copy.deepcopy(d)
    d["min_y"] = MIN_Y
    d["height"] = HEIGHT
    d["logical_height"] = HEIGHT
    # clouds drift around the upper mountain; the summits poke through
    d.setdefault("attributes", {})["minecraft:visual/cloud_height"] = 330.33
    return d


def world_preset(ref_dir: str) -> dict:
    with open(os.path.join(ref_dir, "worldgen", "world_preset", "normal.json"), encoding="utf-8") as f:
        preset = json.load(f)
    preset = copy.deepcopy(preset)
    preset["dimensions"]["minecraft:overworld"] = {
        "type": f"{NS}:ski_mountains",
        "generator": {
            "type": "minecraft:noise",
            "biome_source": {"type": "minecraft:multi_noise", "biomes": biome_points()},
            "settings": f"{NS}:ski_mountains",
        },
    }
    return preset


def write(write_data, lang: dict, tag) -> None:
    ref_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "FreshTracks-ref", "mc-assets", "data", "minecraft"))
    for field in FIELDS:
        write_data(f"{NS}/worldgen/density_function/ski/{field}.json", {"type": f"{NS}:ski_terrain", "field": field})
    write_data(f"{NS}/worldgen/noise_settings/ski_mountains.json", noise_settings())
    write_data(f"{NS}/dimension_type/ski_mountains.json", dimension_type(ref_dir))
    write_data(f"{NS}/worldgen/world_preset/ski_mountains.json", world_preset(ref_dir))
    tag("minecraft/tags/worldgen/world_preset/normal.json", f"{NS}:ski_mountains")
    lang[f"generator.{NS}.ski_mountains"] = "Ski Mountains"
