"""Resort furniture: lift loading mats, rope-line posts for lift mazes, orange closure fencing, resort signs and
nameable trail signs. Called from gen_assets.py.
"""
from __future__ import annotations

NS = "freshtracks"
SIGNS = {
    "slow_zone_sign": ("Slow Zone Sign", "minecraft:yellow_dye"),
    "closed_sign": ("Run Closed Sign", "minecraft:orange_dye"),
    "experts_only_sign": ("Experts Only Sign", "minecraft:black_dye"),
    "singles_sign": ("Singles Line Sign", "minecraft:light_blue_dye"),
    "boundary_sign": ("Ski Area Boundary Sign", "minecraft:orange_dye"),
    "cliff_sign": ("Caution Cliffs Sign", "minecraft:yellow_dye"),
}
SYMBOLS = ["green", "blue", "black", "double_black", "park", "closed", "extreme", "lift"]
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def _face(texture: str, uv=None, cull=None) -> dict:
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def _loot(write_data, name: str) -> None:
    write_data(f"{NS}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/{name}",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}]})


def sign_models(face: str, back: str) -> tuple[dict, dict]:
    """(standing, wall) models for a sign facing north. The board face uses the top 16x10 of its texture."""
    post = "#post"
    board_faces = lambda: {
        "north": _face("#face", [0, 0, 16, 10]), "south": _face("#back", [0, 6, 16, 16]),
        "east": _face("#back", [7, 6, 8.5, 16]), "west": _face("#back", [7, 6, 8.5, 16]),
        "up": _face("#back", [0, 7, 16, 8.5]), "down": _face("#back", [0, 7, 16, 8.5])}
    textures = {"particle": face, "face": face, "back": back, "post": "minecraft:block/stripped_spruce_log"}
    standing = {
        "parent": "minecraft:block/block", "textures": textures,
        "elements": [
            {"from": [0, 6, 7], "to": [16, 16, 8.5], "faces": board_faces()},
            {"from": [7, 0, 8.5], "to": [9, 15, 10.5], "faces": {
                "north": _face(post, [7, 1, 9, 16]), "south": _face(post, [7, 1, 9, 16]), "east": _face(post, [5, 1, 7, 16]),
                "west": _face(post, [9, 1, 11, 16]), "up": _face(post, [7, 7, 9, 9]), "down": _face(post, [7, 7, 9, 9], "down")}},
        ],
    }
    wall = {
        "parent": "minecraft:block/block", "textures": textures,
        "elements": [{"from": [0, 3, 14.5], "to": [16, 13, 16], "faces": {
            "north": _face("#face", [0, 0, 16, 10]), "south": _face("#back", [0, 3, 16, 13], "south"),
            "east": _face("#back", [0, 3, 1.5, 13]), "west": _face("#back", [14.5, 3, 16, 13]),
            "up": _face("#back", [0, 14.5, 16, 16]), "down": _face("#back", [0, 0, 16, 1.5])}}],
    }
    return standing, wall


def sign_variants(model_base: str, extra: str = "") -> dict:
    variants = {}
    for d, r in FACINGS:
        for wall in (False, True):
            v = {"model": f"{NS}:block/{model_base}{'_wall' if wall else ''}"}
            if r:
                v["y"] = r
            key = f"facing={d}"
            if extra:
                key += "," + extra
            key += f",wall={'true' if wall else 'false'}"
            variants[key] = v
    return variants


def write(write_json, write_data, lang: dict, tag, shaped, shapeless) -> None:
    # --- LOAD HERE / WAIT HERE mats
    for name, english, carpet in (("load_here", "Load Here Mat", "minecraft:orange_carpet"),
                                  ("wait_here", "Wait Here Mat", "minecraft:blue_carpet")):
        side = f"{NS}:block/{name}_side"
        write_json(f"models/block/{name}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": side, "top": f"{NS}:block/{name}", "side": side},
            "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {
                "up": _face("#top", [0, 0, 16, 16]), "down": _face("#side", [0, 0, 16, 16], "down"),
                "north": _face("#side", [0, 15, 16, 16]), "south": _face("#side", [0, 15, 16, 16]),
                "east": _face("#side", [0, 15, 16, 16]), "west": _face("#side", [0, 15, 16, 16])}}]})
        variants = {}
        for d, r in FACINGS:
            v = {"model": f"{NS}:block/{name}"}
            if r:
                v["y"] = r
            variants[f"facing={d}"] = v
        write_json(f"blockstates/{name}.json", {"variants": variants})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        lang[f"block.{NS}.{name}"] = english
        _loot(write_data, name)
        shapeless(name, [carpet, "minecraft:white_dye"], count=2, category="building")
    lang[f"tooltip.{NS}.load_here"] = "Where riders stand to board a lift. Lifts lay these out for you."
    lang[f"tooltip.{NS}.wait_here"] = "Where the next group waits while the chair in front loads."

    # --- rope line (fence-style posts that rope together)
    post_tex = f"{NS}:block/rope_line_post"
    rope_tex = f"{NS}:block/rope_line_rope"
    post_el = {"from": [7, 0, 7], "to": [9, 15, 9], "faces": {
        "north": _face("#post", [7, 1, 9, 16]), "south": _face("#post", [7, 1, 9, 16]), "east": _face("#post", [7, 1, 9, 16]),
        "west": _face("#post", [7, 1, 9, 16]), "up": _face("#post", [7, 7, 9, 9]), "down": _face("#post", [7, 7, 9, 9], "down")}}
    write_json("models/block/rope_line_post.json", {"parent": "minecraft:block/block",
                                                     "textures": {"particle": post_tex, "post": post_tex}, "elements": [post_el]})
    write_json("models/block/rope_line_side.json", {"parent": "minecraft:block/block", "textures": {"particle": rope_tex, "rope": rope_tex},
        "elements": [{"from": [7.5, 10, 0], "to": [8.5, 11, 7], "faces": {
            "north": _face("#rope", [7, 5, 8, 6], "north"), "east": _face("#rope", [0, 5, 7, 6]), "west": _face("#rope", [0, 5, 7, 6]),
            "up": _face("#rope", [7, 0, 8, 7]), "down": _face("#rope", [7, 0, 8, 7])}}]})
    write_json("models/block/rope_line_inventory.json", {"parent": "minecraft:block/block",
        "textures": {"particle": post_tex, "post": post_tex, "rope": rope_tex},
        "display": {"gui": {"rotation": [30, 135, 0], "scale": [0.625, 0.625, 0.625]}},
        "elements": [
            {"from": [2, 0, 7], "to": [4, 15, 9], "faces": {f: _face("#post", [7, 1, 9, 16]) for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [12, 0, 7], "to": [14, 15, 9], "faces": {f: _face("#post", [7, 1, 9, 16]) for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [4, 10, 7.5], "to": [12, 11, 8.5], "faces": {f: _face("#rope", [0, 5, 8, 6]) for f in ("north", "south", "up", "down")}},
        ]})
    parts = [{"apply": {"model": f"{NS}:block/rope_line_post"}}]
    for d, r in FACINGS:
        apply = {"model": f"{NS}:block/rope_line_side"}
        if r:
            apply["y"] = r
        parts.append({"when": {d: "true"}, "apply": apply})
    write_json("blockstates/rope_line.json", {"multipart": parts})
    write_json("items/rope_line.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/rope_line_inventory"}})
    lang[f"block.{NS}.rope_line"] = "Rope Line"
    lang[f"tooltip.{NS}.rope_line"] = "Bamboo posts that rope together: build lift mazes and lines."
    _loot(write_data, "rope_line")
    tag("minecraft/tags/block/fences.json", f"{NS}:rope_line")
    tag("minecraft/tags/block/mineable/axe.json", f"{NS}:rope_line")
    shapeless("rope_line", ["minecraft:bamboo", "minecraft:bamboo", "minecraft:string", "minecraft:blue_dye"], count=6, category="building")

    # --- rope span: rope strung between rope-line posts, no post of its own (so posts can stand a few blocks apart)
    parts = []
    for d, r in FACINGS:
        apply = {"model": f"{NS}:block/rope_line_side"}
        if r:
            apply["y"] = r
        parts.append({"when": {d: "true"}, "apply": apply})
    write_json("blockstates/rope_span.json", {"multipart": parts})
    write_json("models/item/rope_span.json", {"parent": "minecraft:item/generated", "textures": {"layer0": rope_tex}})
    write_json("items/rope_span.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/rope_span"}})
    lang[f"block.{NS}.rope_span"] = "Rope Span"
    lang[f"tooltip.{NS}.rope_span"] = "Rope with no post: string it between rope-line posts set a few blocks apart."
    _loot(write_data, "rope_span")
    tag("minecraft/tags/block/fences.json", f"{NS}:rope_span")
    shapeless("rope_span", ["minecraft:string", "minecraft:blue_dye"], count=4, category="building")

    # --- hazard pole: banded bamboo pole with an orange disc
    hz = f"{NS}:block/hazard_pole"
    write_json("models/block/hazard_pole.json", {
        "parent": "minecraft:block/block", "textures": {"particle": hz, "pole": hz},
        "elements": [
            {"from": [7, 0, 7], "to": [9, 16, 9], "faces": {f: _face("#pole", [7, 0, 9, 16]) for f in ("north", "south", "east", "west")}
                | {"up": _face("#pole", [7, 0, 9, 2]), "down": _face("#pole", [7, 0, 9, 2], "down")}},
            {"from": [4, 9, 7.5], "to": [12, 16, 8.5], "faces": {f: _face("#pole", [4, 0, 12, 2]) for f in ("north", "south")}
                | {f: _face("#pole", [0, 0, 1, 7]) for f in ("east", "west", "up", "down")}},
        ]})
    write_json("blockstates/hazard_pole.json", {"variants": {f"facing={d}": ({"model": f"{NS}:block/hazard_pole", "y": r} if r else
                                                                             {"model": f"{NS}:block/hazard_pole"}) for d, r in FACINGS}})
    write_json("items/hazard_pole.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/hazard_pole"}})
    lang[f"block.{NS}.hazard_pole"] = "Hazard Pole"
    lang[f"tooltip.{NS}.hazard_pole"] = "Marks rocks, cliffs and drop-offs next to a run."
    _loot(write_data, "hazard_pole")
    shapeless("hazard_pole", ["minecraft:bamboo", "minecraft:orange_dye", "minecraft:black_dye"], count=4, category="building")

    # --- snowmaking: fan gun on a carriage, tower gun head on a steel pole
    body, front, back_t, base, pole = (f"{NS}:block/snow_gun_body", f"{NS}:block/snow_gun_front", f"{NS}:block/snow_gun_back",
                                       f"{NS}:block/snow_gun_base", f"{NS}:block/snow_gun_pole")
    all6 = lambda tex: {f: _face(tex) for f in ("north", "south", "east", "west", "up", "down")}
    write_json("models/block/snow_gun.json", {
        "parent": "minecraft:block/block", "textures": {"particle": body, "body": body, "front": front, "back": back_t, "base": base},
        "elements": [
            {"from": [3, 0, 2], "to": [13, 4, 14], "faces": all6("#base")},
            {"from": [7, 4, 7], "to": [9, 10, 9], "faces": all6("#base")},
            {"from": [3, 10, 2], "to": [13, 20, 14], "faces": {
                "north": _face("#front"), "south": _face("#back"), "east": _face("#body"), "west": _face("#body"),
                "up": _face("#body"), "down": _face("#body")}},
        ]})
    write_json("models/block/snow_gun_tower.json", {
        "parent": "minecraft:block/block", "textures": {"particle": pole, "pole": pole, "base": base, "front": front},
        "elements": [
            {"from": [7, 0, 7], "to": [9, 6, 9], "faces": all6("#pole")},
            {"from": [6, 5, 2], "to": [10, 9, 11], "faces": {"north": _face("#front"), "south": _face("#base"), "east": _face("#base"),
                                                           "west": _face("#base"), "up": _face("#base"), "down": _face("#base")}},
        ]})
    write_json("models/block/snow_gun_pole.json", {
        "parent": "minecraft:block/block", "textures": {"particle": pole, "pole": pole},
        "elements": [{"from": [6.5, 0, 6.5], "to": [9.5, 16, 9.5], "faces": {f: _face("#pole") for f in ("north", "south", "east", "west")}
                      | {"up": _face("#pole", [6, 6, 9, 9]), "down": _face("#pole", [6, 6, 9, 9])}}]})
    for name in ("snow_gun", "snow_gun_tower"):
        variants = {}
        for d, r in FACINGS:
            for mode in ("auto", "on", "off"):
                v = {"model": f"{NS}:block/{name}"}
                if r:
                    v["y"] = r
                variants[f"facing={d},mode={mode}"] = v
        write_json(f"blockstates/{name}.json", {"variants": variants})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        _loot(write_data, name)
    write_json("blockstates/snow_gun_pole.json", {"variants": {"": {"model": f"{NS}:block/snow_gun_pole"}}})
    write_json("items/snow_gun_pole.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/snow_gun_pole"}})
    _loot(write_data, "snow_gun_pole")
    lang[f"block.{NS}.snow_gun"] = "Fan Snow Gun"
    lang[f"block.{NS}.snow_gun_tower"] = "Tower Snow Gun Head"
    lang[f"block.{NS}.snow_gun_pole"] = "Steel Pole"
    lang[f"tooltip.{NS}.snow_gun"] = "Makes snow at night (use it to switch ON / OFF / AUTO). Builds up the run where the plume lands."
    lang["message.freshtracks.snow_gun.auto"] = "Snow gun: AUTO (runs at night)"
    lang["message.freshtracks.snow_gun.on"] = "Snow gun: ON"
    lang["message.freshtracks.snow_gun.off"] = "Snow gun: OFF"
    shaped("snow_gun", ["III", "IWI", " I "], {"I": "minecraft:iron_ingot", "W": "minecraft:water_bucket"}, count=1, category="building")
    shaped("snow_gun_tower", ["I", "W"], {"I": "minecraft:iron_ingot", "W": "minecraft:water_bucket"}, count=1, category="building")
    shaped("snow_gun_pole", ["I", "I", "I"], {"I": "minecraft:iron_ingot"}, count=6, category="building")
    for name in ("snow_gun", "snow_gun_tower", "snow_gun_pole"):
        tag("minecraft/tags/block/mineable/pickaxe.json", f"{NS}:{name}")

    # --- trail map board (drawn by TrailMapRenderer; the block itself has no model)
    write_json("models/block/trail_map.json", {"textures": {"particle": "minecraft:block/spruce_planks"}})
    write_json("blockstates/trail_map.json", {"variants": {f"facing={d}": {"model": f"{NS}:block/trail_map"} for d, _ in FACINGS}})
    write_json("models/item/trail_map.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/trail_map"}})
    write_json("items/trail_map.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/trail_map"}})
    lang[f"block.{NS}.trail_map"] = "Trail Map Board"
    lang[f"tooltip.{NS}.trail_map"] = "The Snowmass trail map, with a dot where it stands."
    _loot(write_data, "trail_map")
    shaped("trail_map", ["PMP", "S S"], {"P": "minecraft:spruce_planks", "M": "minecraft:map", "S": "minecraft:stick"}, count=1, category="building")

    # --- tower pad
    pad, pad_top = f"{NS}:block/tower_pad", f"{NS}:block/tower_pad_top"
    write_json("models/block/tower_pad.json", {
        "parent": "minecraft:block/block", "textures": {"particle": pad, "side": pad, "top": pad_top},
        "elements": [{"from": [2, 0, 2], "to": [14, 16, 14], "faces": {
            "north": _face("#side"), "south": _face("#side"), "east": _face("#side"), "west": _face("#side"),
            "up": _face("#top"), "down": _face("#top")}}]})
    write_json("blockstates/tower_pad.json", {"variants": {"": {"model": f"{NS}:block/tower_pad"}}})
    write_json("items/tower_pad.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/tower_pad"}})
    lang[f"block.{NS}.tower_pad"] = "Lift Tower Pad"
    lang[f"tooltip.{NS}.tower_pad"] = "Padding for the foot of a lift tower that stands on a run."
    _loot(write_data, "tower_pad")
    shapeless("tower_pad", ["minecraft:blue_wool", "minecraft:white_wool"], count=2, category="building")

    # --- orange closure fencing (pane-style)
    mesh = f"{NS}:block/closure_fence"
    edge = f"{NS}:block/closure_fence_edge"
    for part in ("post", "side", "side_alt", "noside", "noside_alt"):
        write_json(f"models/block/closure_fence_{part}.json", {"parent": f"minecraft:block/template_glass_pane_{part}",
                                                                "textures": {"pane": mesh, "edge": edge, "particle": mesh}})
    pane = [{"apply": {"model": f"{NS}:block/closure_fence_post"}}]
    for d, model, y in (("north", "side", 0), ("east", "side", 90), ("south", "side_alt", 0), ("west", "side_alt", 90)):
        apply = {"model": f"{NS}:block/closure_fence_{model}"}
        if y:
            apply["y"] = y
        pane.append({"when": {d: "true"}, "apply": apply})
    for d, model, y in (("north", "noside", 0), ("east", "noside_alt", 0), ("south", "noside_alt", 90), ("west", "noside", 270)):
        apply = {"model": f"{NS}:block/closure_fence_{model}"}
        if y:
            apply["y"] = y
        pane.append({"when": {d: "false"}, "apply": apply})
    write_json("blockstates/closure_fence.json", {"multipart": pane})
    write_json("models/item/closure_fence.json", {"parent": "minecraft:item/generated", "textures": {"layer0": mesh}})
    write_json("items/closure_fence.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/closure_fence"}})
    lang[f"block.{NS}.closure_fence"] = "Closure Fence"
    lang[f"tooltip.{NS}.closure_fence"] = "Orange snow fencing for closed runs, cliffs and the edge of the trail."
    _loot(write_data, "closure_fence")
    shaped("closure_fence", ["SOS", "SOS"], {"S": "minecraft:stick", "O": "minecraft:orange_wool"}, count=8, category="building")

    # --- resort signs
    back = f"{NS}:block/resort_sign_back"
    for name, (english, dye) in SIGNS.items():
        standing, wall = sign_models(f"{NS}:block/{name}", back)
        write_json(f"models/block/{name}.json", standing)
        write_json(f"models/block/{name}_wall.json", wall)
        write_json(f"blockstates/{name}.json", {"variants": sign_variants(name)})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        lang[f"block.{NS}.{name}"] = english
        _loot(write_data, name)
        tag("minecraft/tags/block/mineable/axe.json", f"{NS}:{name}")
        shapeless(name, ["#minecraft:signs", dye], count=1, category="building")
    lang[f"tooltip.{NS}.resort_sign"] = "Stands on a post, or hangs on a wall, tree or lift tower."

    # --- trail signs: symbol in the texture, run name drawn by the block entity renderer
    variants = {}
    for symbol in SYMBOLS:
        standing, wall = sign_models(f"{NS}:block/trail_sign_{symbol}", "minecraft:block/spruce_planks")
        write_json(f"models/block/trail_sign_{symbol}.json", standing)
        write_json(f"models/block/trail_sign_{symbol}_wall.json", wall)
        variants.update(sign_variants(f"trail_sign_{symbol}", f"symbol={symbol}"))
    # blockstate keys must list properties in the order the game sorts them (facing, symbol, wall)
    write_json("blockstates/trail_sign.json", {"variants": variants})
    write_json("items/trail_sign.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/trail_sign_blue"}})
    lang[f"block.{NS}.trail_sign"] = "Trail Sign"
    lang[f"tooltip.{NS}.trail_sign"] = "Use it to name the run and pick its difficulty. Rename it in an anvil to start with that name."
    _loot(write_data, "trail_sign")
    tag("minecraft/tags/block/mineable/axe.json", f"{NS}:trail_sign")
    shapeless("trail_sign", ["#minecraft:signs", "minecraft:white_dye"], count=1, category="building")

    # --- screens, HUD, lift types
    lang.update({
        "screen.freshtracks.trail_sign": "Trail Sign",
        "screen.freshtracks.trail_sign.name": "Run name",
        "screen.freshtracks.trail_sign.hint": "Run name, e.g. Big Burn",
        "screen.freshtracks.trail_sign.symbol": "Difficulty",
        "screen.freshtracks.trail_sign.arrow": "Arrow",
        "screen.freshtracks.lift": "Lift Control",
        "screen.freshtracks.lift.name": "Lift name",
        "screen.freshtracks.lift.type": "Type",
        "screen.freshtracks.lift.speed": "Speed: %s m/s (%s km/h)",
        "screen.freshtracks.lift.spacing": "Carriers every %s blocks",
        "screen.freshtracks.lift.status": "Status",
        "screen.freshtracks.lift.info": "%s blocks long, %s towers, ride time %s",
        "screen.freshtracks.lift.grip.fixed": "Fixed grip: chairs load at rope speed",
        "screen.freshtracks.lift.grip.detachable": "Detachable: carriers slow right down in the stations",
        "screen.freshtracks.lift.grip.surface": "Surface lift: riders stay on their skis and get pulled up",
        "lift_style.freshtracks.double": "Double Chair",
        "lift_style.freshtracks.triple": "Triple Chair",
        "lift_style.freshtracks.quad": "Quad Chair",
        "lift_style.freshtracks.six": "Six-Pack Express",
        "lift_style.freshtracks.gondola": "Gondola",
        "lift_style.freshtracks.quad_express": "Express Quad",
        "lift_style.freshtracks.t_bar": "T-Bar",
        "lift_style.freshtracks.platter": "Platter (Poma)",
        "lift_style.freshtracks.gondola_8": "8-Seat Gondola",
        "lift_style.freshtracks.pulse_gondola": "Pulse Gondola",
        "hud.freshtracks.lift.go_load": "Stand on the LOAD HERE mat to ride %s",
        "hud.freshtracks.lift.stay": "Stay on LOAD HERE: %s arriving in %s s",
        "hud.freshtracks.lift.sit": "Here it comes... sit back!",
        "hud.freshtracks.lift.grab": "Here it comes... grab the bar!",
        "hud.freshtracks.lift.closed": "%s is closed",
        "hud.freshtracks.lift.need_skis": "Click into skis or a board to ride the %s",
        "hud.freshtracks.lift.towed": "Hang on, the %s pulls you up. Sneak to let go.",
        "hud.freshtracks.lift.riding": "%s: top in %s. Hold sneak to jump off.",
        "hud.freshtracks.lift.riding_down": "%s: riding back down. Hold sneak to jump off.",
        "hud.freshtracks.lift.mid_station": "%s mid station: hold sneak to get off here, or stay on for the top.",
        "hud.freshtracks.lift.bail": "Jumping off in %s s... (let go of sneak to stay on)",
        "message.freshtracks.lift.need_skis": "You need skis or a board on to ride the %s",
        "message.freshtracks.lift.no_permission": "Only operators or creative-mode players can change lifts",
    })
