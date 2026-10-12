"""Resort villagers: trades, trade sets, job-site tags and the three workstation blocks. Called from gen_assets.py.

Professions (Java: village/ModVillagers.java): Ski Tech at a Ski Bench, Board Tech at a Board Press, Liftie at a Lift
Control panel. Premium rigs (race/powder/park skis, powder/park boards) only come from these shops.
"""
from __future__ import annotations

NS = "freshtracks"
EM = "minecraft:emerald"
WAX_FULL = 72000

PROFESSIONS = {
    "ski_tech": ("Ski Tech", "ski_bench", "Ski Bench"),
    "board_tech": ("Board Tech", "board_press", "Board Press"),
    "liftie": ("Liftie", "lift_control", "Lift Control"),
    "ski_patrol": ("Ski Patrol", "first_aid_kit", "First Aid Kit"),
}


def _dyed() -> list[dict]:
    return [{"type": "minecraft:set_random_dyes",
             "number_of_dyes": {"type": "minecraft:add", "inputs": [1, {"type": "minecraft:binomial", "n": 2, "p": 0.75}]}}]


def buy(item: str, count: int, xp: int = 2, uses: int = 16) -> dict:
    """The villager buys `count` of item for one emerald."""
    return {"wants": {"id": item, "count": count}, "gives": {"id": EM}, "max_uses": uses, "xp": xp, "reputation_discount": 0.05}


def sell(item: str, price: int, xp: int, count: int = 1, uses: int = 12, waxed: bool = False, dyed: bool = False) -> dict:
    gives = {"id": item}
    if count > 1:
        gives["count"] = count
    if waxed:
        gives["components"] = {f"{NS}:wax": WAX_FULL}
    t = {"wants": {"id": EM, "count": price}, "gives": gives, "max_uses": uses, "xp": xp, "reputation_discount": 0.2}
    if dyed:
        t["given_item_modifier"] = _dyed()
    return t


def trades() -> dict[str, dict[int, dict[str, dict]]]:
    i = lambda n: f"{NS}:{n}"
    return {
        "ski_tech": {
            1: {"string_emerald": buy("minecraft:string", 12), "honeycomb_emerald": buy("minecraft:honeycomb", 3),
                "emerald_wax": sell(i("ski_wax"), 2, 1, count=4), "emerald_wooden_skis": sell(i("wooden_skis"), 3, 2)},
            2: {"iron_emerald": buy("minecraft:iron_ingot", 4, xp=10, uses=12), "emerald_poles": sell(i("ski_poles"), 6, 5),
                "emerald_ski_boots": sell(i("ski_boots"), 8, 5), "emerald_alpine_skis": sell(i("alpine_skis"), 10, 10)},
            3: {"leather_emerald": buy("minecraft:leather", 6, xp=20, uses=12), "emerald_goggles": sell(i("ski_goggles"), 7, 10, dyed=True),
                "emerald_powder_skis": sell(i("powder_skis"), 16, 15), "emerald_carving_skis": sell(i("carving_skis"), 14, 15)},
            4: {"emerald_race_skis": sell(i("race_skis"), 18, 15), "emerald_helmet": sell(i("ski_helmet"), 12, 15, dyed=True),
                "emerald_wax_bulk": sell(i("ski_wax"), 6, 10, count=12), "emerald_freeride_skis": sell(i("freeride_skis"), 16, 15),
                "emerald_touring_skis": sell(i("touring_skis"), 14, 15)},
            5: {"emerald_park_skis": sell(i("park_skis"), 20, 30), "emerald_tuned_race_skis": sell(i("race_skis"), 26, 30, waxed=True),
                "emerald_retro_skis": sell(i("retro_skis"), 24, 30), "emerald_race_suit": sell(i("race_suit"), 14, 30, dyed=True)},
        },
        "board_tech": {
            1: {"leather_emerald": buy("minecraft:leather", 6), "string_emerald": buy("minecraft:string", 12),
                "emerald_wax": sell(i("ski_wax"), 2, 1, count=4), "emerald_snurfer": sell(i("snurfer"), 3, 2)},
            2: {"emerald_board_boots": sell(i("snowboard_boots"), 7, 5), "emerald_all_mountain": sell(i("all_mountain_board"), 10, 10),
                "emerald_beanie": sell(i("beanie"), 2, 5, dyed=True), "white_wool_emerald": buy("minecraft:white_wool", 10, xp=10),
                "emerald_anorak": sell(i("anorak"), 6, 5, dyed=True)},
            3: {"emerald_jacket": sell(i("ski_jacket"), 8, 10, dyed=True), "emerald_pants": sell(i("snow_pants"), 7, 10, dyed=True),
                "emerald_powder_board": sell(i("powder_board"), 16, 15), "emerald_puffer": sell(i("puffer_jacket"), 10, 10, dyed=True),
                "emerald_freeride_board": sell(i("freeride_board"), 16, 15)},
            4: {"emerald_park_board": sell(i("park_board"), 18, 15), "emerald_goggles": sell(i("ski_goggles"), 7, 15, dyed=True),
                "emerald_carving_board": sell(i("carving_board"), 16, 15), "emerald_splitboard": sell(i("splitboard"), 14, 15),
                "emerald_shell": sell(i("shell_jacket"), 9, 15, dyed=True)},
            5: {"emerald_tuned_powder_board": sell(i("powder_board"), 24, 30, waxed=True),
                "emerald_tuned_park_board": sell(i("park_board"), 24, 30, waxed=True), "emerald_retro_board": sell(i("retro_board"), 24, 30),
                "emerald_retro_jacket": sell(i("retro_jacket"), 12, 30, dyed=True)},
        },
        "liftie": {
            1: {"coal_emerald": buy("minecraft:coal", 15), "emerald_cocoa": sell(i("hot_cocoa"), 1, 1, count=2),
                "emerald_magic_carpet": sell(i("magic_carpet"), 3, 2, count=6)},
            2: {"iron_emerald": buy("minecraft:iron_ingot", 4, xp=10, uses=12), "emerald_towers": sell(i("lift_tower"), 4, 5, count=4),
                "redstone_emerald": buy("minecraft:redstone", 6, xp=10)},
            3: {"emerald_cable": sell(i("lift_cable"), 8, 10), "emerald_tower_head": sell(i("lift_tower_head"), 5, 10, count=2)},
            4: {"emerald_terminal": sell(i("lift_terminal"), 12, 15), "emerald_cocoa_bulk": sell(i("hot_cocoa"), 4, 15, count=8)},
            5: {"emerald_terminal_pair": sell(i("lift_terminal"), 20, 30, count=2)},
        },
        # ski patrol put up the closures, rope lines and warning signs, and keep a thermos going
        "ski_patrol": {
            1: {"paper_emerald": buy("minecraft:paper", 24), "emerald_cocoa": sell(i("hot_cocoa"), 1, 1, count=2),
                "emerald_rope_line": sell(i("rope_line"), 1, 2, count=8)},
            2: {"string_emerald": buy("minecraft:string", 12, xp=10), "emerald_closure_fence": sell(i("closure_fence"), 2, 5, count=8),
                "emerald_closed_sign": sell(i("closed_sign"), 2, 5, count=2)},
            3: {"emerald_hazard_pole": sell(i("hazard_pole"), 2, 10, count=4), "emerald_slow_zone": sell(i("slow_zone_sign"), 2, 10, count=2),
                "emerald_goggles": sell(i("ski_goggles"), 7, 10, dyed=True)},
            4: {"emerald_boundary_sign": sell(i("boundary_sign"), 3, 15, count=2), "emerald_cliff_sign": sell(i("cliff_sign"), 3, 15, count=2),
                "emerald_helmet": sell(i("ski_helmet"), 10, 15, dyed=True)},
            5: {"emerald_experts_only": sell(i("experts_only_sign"), 3, 30, count=2), "emerald_first_aid": sell(i("first_aid_kit"), 8, 30)},
        },
    }


WANDERING = {
    "larch_sapling": sell(f"{NS}:larch_sapling", 5, 1, uses=8),
    "aspen_sapling": sell(f"{NS}:aspen_sapling", 5, 1, uses=8),
    "ski_wax": sell(f"{NS}:ski_wax", 1, 1, count=2, uses=8),
}


def write(write_json, write_data, lang: dict, tag, element) -> None:
    for prof, levels in trades().items():
        english, block, block_english = PROFESSIONS[prof]
        lang[f"entity.{NS}.villager.{prof}"] = english
        for level, entries in levels.items():
            ids = []
            for name, body in entries.items():
                write_data(f"{NS}/villager_trade/{prof}/{level}/{name}.json", body)
                ids.append(f"{NS}:{prof}/{level}/{name}")
            write_data(f"{NS}/trade_set/{prof}/level_{level}.json",
                       {"amount": 2, "random_sequence": f"{NS}:trade_set/{prof}/level_{level}", "trades": ids})
        tag("minecraft/tags/point_of_interest_type/acquirable_job_site.json", f"{NS}:{prof}")
    for name, body in WANDERING.items():
        write_data(f"{NS}/villager_trade/wandering_trader/{name}.json", body)
        tag("minecraft/tags/villager_trade/wandering_trader/common.json", f"{NS}:wandering_trader/{name}")

    # Workstation blocks
    models = {
        # bench: thick top, cabinet body, a ski clamped on top
        "ski_bench": {"parent": "minecraft:block/block",
                      "textures": {"particle": f"{NS}:block/ski_bench_side", "top": f"{NS}:block/ski_bench_top",
                                   "side": f"{NS}:block/ski_bench_side", "front": f"{NS}:block/ski_bench_front",
                                   "bottom": "minecraft:block/spruce_planks", "ski": f"{NS}:block/ski_bench_ski"},
                      "elements": [
                          element([1, 0, 1], [15, 12, 15], {"up": "#top", "down": "#bottom", "north": "#front", "side": "#side"}, [1, 4, 15, 16]),
                          element([0, 12, 0], [16, 16, 16], {"up": "#top", "down": "#bottom", "side": "#side"}, [0, 0, 16, 4]),
                          element([2, 16, 6], [14, 17, 10], {"all": "#ski"}),
                          element([7, 16, 5], [9, 19, 11], {"all": "#ski"}, [0, 13, 2, 16]),
                      ]},
        "board_press": {"parent": "minecraft:block/block",
                        "textures": {"particle": f"{NS}:block/board_press_side", "top": f"{NS}:block/board_press_top",
                                     "side": f"{NS}:block/board_press_side", "front": f"{NS}:block/board_press_front",
                                     "bottom": "minecraft:block/iron_block"},
                        "elements": [
                            element([1, 0, 1], [15, 12, 15], {"up": "#top", "down": "#bottom", "north": "#front", "side": "#side"}, [1, 4, 15, 16]),
                            element([0, 12, 0], [16, 16, 16], {"up": "#top", "down": "#bottom", "side": "#side"}, [0, 0, 16, 4]),
                        ]},
        "lift_control": {"parent": "minecraft:block/block",
                         "textures": {"particle": f"{NS}:block/lift_control_side", "top": f"{NS}:block/lift_control_top",
                                      "side": f"{NS}:block/lift_control_side", "front": f"{NS}:block/lift_control_front",
                                      "bottom": "minecraft:block/iron_block"},
                         "elements": [element([1, 0, 3], [15, 15, 13], {"up": "#top", "down": "#bottom", "north": "#front", "side": "#side"},
                                              [1, 1, 15, 16])]},
        # a red cabinet with a white cross, a carry handle on top
        "first_aid_kit": {"parent": "minecraft:block/block",
                          "textures": {"particle": f"{NS}:block/first_aid_kit_side", "top": f"{NS}:block/first_aid_kit_top",
                                       "side": f"{NS}:block/first_aid_kit_side", "front": f"{NS}:block/first_aid_kit_front",
                                       "bottom": "minecraft:block/iron_block"},
                          "elements": [element([1, 0, 3], [15, 14, 13], {"up": "#top", "down": "#bottom", "north": "#front", "side": "#side"},
                                               [1, 2, 15, 16]),
                                       element([5, 14, 7], [11, 15, 9], {"all": "#bottom"})]},
    }
    for prof, (_, block, block_english) in PROFESSIONS.items():
        write_json(f"models/block/{block}.json", models[block])
        variants = {}
        for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"{NS}:block/{block}"}
            if r:
                v["y"] = r
            variants[f"facing={d}"] = v
        write_json(f"blockstates/{block}.json", {"variants": variants})
        write_json(f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})
        lang[f"block.{NS}.{block}"] = block_english
        write_data(f"{NS}/loot_table/blocks/{block}.json", {
            "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{block}",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}]}]})
    tag("minecraft/tags/block/mineable/axe.json", f"{NS}:ski_bench")
    tag("minecraft/tags/block/mineable/pickaxe.json", f"{NS}:board_press", f"{NS}:lift_control", f"{NS}:first_aid_kit")
    lang[f"tooltip.{NS}.workstation"] = "A villager without a job will work here."

    # The ski lodge: a standalone structure in the snowfield biomes (both shops, a lift desk and three workers).
    # The village shops are added to the snowy village house pool in code (village/VillageHouses.java).
    write_data(f"{NS}/worldgen/template_pool/ski_lodge.json", {
        "fallback": "minecraft:empty",
        "elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:ski_lodge",
                                  "processors": {"processors": []}, "projection": "rigid"}, "weight": 1}]})
    write_data(f"{NS}/worldgen/structure/ski_lodge.json", {
        "type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/ski_lodge", "max_distance_from_center": 32,
        "project_start_to_heightmap": "WORLD_SURFACE_WG", "size": 1, "spawn_overrides": {}, "start_height": {"absolute": 0},
        "start_pool": f"{NS}:ski_lodge", "step": "surface_structures", "terrain_adaptation": "beard_box", "use_expansion_hack": False})
    write_data(f"{NS}/worldgen/structure_set/ski_lodges.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 20261009, "separation": 12, "spacing": 32},
        "structures": [{"structure": f"{NS}:ski_lodge", "weight": 1}]})
    tag(f"{NS}/tags/worldgen/biome/has_structure/ski_lodge.json",
        *[f"{NS}:{b}" for b in ("powder_bowls", "frosted_glades")])

    # Dyed gear washes off in a cauldron like leather armour.
    tag("minecraft/tags/item/cauldron_can_remove_dye.json", *[f"{NS}:{n}" for n in ("ski_goggles", "ski_helmet", "beanie", "ski_jacket", "snow_pants",
                                                                                    "puffer_jacket", "shell_jacket", "anorak", "retro_jacket", "race_suit")])
