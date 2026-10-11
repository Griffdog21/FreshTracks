"""The Fresh Tracks advancement tab. Called from gen_assets.py.

Most criteria use the mod's own trigger: {"trigger": "freshtracks:ride", "conditions": {"event": ...}} fired from code
(advancement/ModCriteria.java). A few use vanilla triggers.
"""
from __future__ import annotations

NS = "freshtracks"
RIGS = ["wooden_skis", "alpine_skis", "race_skis", "powder_skis", "park_skis", "carving_skis", "freeride_skis", "touring_skis",
        "retro_skis", "snurfer", "all_mountain_board", "powder_board", "park_board", "freeride_board", "carving_board", "splitboard",
        "retro_board"]
SHOP_GOODS = ["race_skis", "powder_skis", "park_skis", "powder_board", "park_board", "retro_skis", "retro_board"]


def ride(event: str) -> dict:
    return {"trigger": f"{NS}:ride", "conditions": {"event": event}}


# id -> (parent, icon, frame, title, description, criterion)
ADVANCEMENTS = {
    "root": (None, "alpine_skis", "task", "Fresh Tracks", "Get your hands on skis or a snowboard",
             {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"#{NS}:rigs"}]}}),
    "click_in": ("root", "wooden_skis", "task", "Clicked In", "Click into skis or a snowboard and point it downhill",
                 ride("click_in")),
    "yard_sale": ("click_in", "ski_poles", "task", "Yard Sale", "Wipe out so hard your gear goes everywhere", ride("yard_sale")),
    "big_air": ("click_in", "park_skis", "task", "Send It", "Stay in the air for a second and a half", ride("big_air")),
    "stomped": ("big_air", "park_board", "goal", "Stomped It", "Land a trick worth 1,000 points", ride("trick_1000")),
    "speed_100": ("click_in", "race_skis", "goal", "Ton Up", "Hit 100 km/h", ride("speed_100")),
    "first_tracks": ("click_in", "powder_skis", "task", "First Tracks", "Carve the first line through fresh powder", ride("fresh_tracks")),
    "race_day": ("click_in", "finish_line", "goal", "Race Day", "Finish a timed run between a start and a finish line",
                 ride("race_finish")),
    "first_chair": ("click_in", "lift_cable", "task", "First Chair", "Ride a chairlift", ride("lift")),
    "lift_mechanic": ("first_chair", "lift_terminal", "challenge", "Lift Mechanic", "Build your own chairlift with a Lift Cable",
                      ride("build_lift")),
    "dressed": ("root", "ski_goggles", "task", "Dressed for the Mountain", "Wear goggles or a helmet, a jacket, snow pants and boots",
                ride("dressed")),
    "hot_cocoa": ("dressed", "hot_cocoa", "task", "Après Ski", "Warm up with a hot cocoa",
                  {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": f"{NS}:hot_cocoa"}}}),
    "shop_local": ("root", "ski_bench", "task", "Shop Local", "Buy premium gear from a Ski Tech or Board Tech",
                   {"trigger": "minecraft:villager_trade", "conditions": {"item": {"items": f"#{NS}:shop_goods"}}}),
}


def write(write_data, lang: dict, tag) -> None:
    tag(f"{NS}/tags/item/rigs.json", *[f"{NS}:{r}" for r in RIGS])
    tag(f"{NS}/tags/item/shop_goods.json", *[f"{NS}:{r}" for r in SHOP_GOODS])
    for adv_id, (parent, icon, frame, title, description, criterion) in ADVANCEMENTS.items():
        display = {
            "icon": {"id": f"{NS}:{icon}"},
            "title": {"translate": f"advancements.{NS}.{adv_id}.title"},
            "description": {"translate": f"advancements.{NS}.{adv_id}.description"},
            "frame": frame,
            "show_toast": parent is not None,
            "announce_to_chat": parent is not None,
        }
        if parent is None:
            display["background"] = "minecraft:block/packed_ice"
        body = {"criteria": {adv_id: criterion}, "display": display, "requirements": [[adv_id]]}
        if parent is not None:
            body["parent"] = f"{NS}:{parent}"
        write_data(f"{NS}/advancement/{adv_id}.json", body)
        lang[f"advancements.{NS}.{adv_id}.title"] = title
        lang[f"advancements.{NS}.{adv_id}.description"] = description
