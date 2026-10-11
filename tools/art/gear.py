"""Ski gear: worn textures (64x32 humanoid armor layers) and 16x16 item icons.

Dyeable pieces have a grayscale base layer (tinted by dye) and an undyed overlay with the details.
Run: uv run --with pillow --with numpy python tools/art/gear.py [preview_dir]
"""
from __future__ import annotations

import os
import sys

from pix import canvas, contact_sheet, hexc, mix, noise2, outline_inside, paint_box, put, rect, save, shade

T = (0, 0, 0, 0)
G_L = hexc("#EDEDED")
G_M = hexc("#C9C9C9")
G_D = hexc("#9C9C9C")
G_DD = hexc("#767676")
BLACK = hexc("#1E2026")
DARK = hexc("#2E323B")
SILVER = hexc("#C8D0DA")
WHITE = hexc("#F5F7FA")


def lens(x, y, rows):
    """Iridescent mirror lens colour for a pixel, top row deep blue to a gold flash low down."""
    t = y / max(1, rows - 1)
    c = mix(hexc("#2B3FA8"), hexc("#27B4C9"), t * 1.4)
    if t > 0.6:
        c = mix(c, hexc("#F2C14E"), (t - 0.6) * 2)
    if (x + y) % 5 == 0:
        c = shade(c, 0.35)  # glint
    return c


# --------------------------------------------------------------------------------------------- armor layers

def head_faces():
    """Absolute face rectangles of the head box at (0, 0), 8x8x8."""
    return {"top": (8, 0, 16, 8), "bottom": (16, 0, 24, 8), "right": (0, 8, 8, 16), "front": (8, 8, 16, 16),
            "left": (16, 8, 24, 16), "back": (24, 8, 32, 16)}


def goggles_layers():
    base = canvas(64, 32)
    over = canvas(64, 32)
    f = head_faces()
    # strap all the way round at eye height (rows 11-12 of the texture)
    for face in ("right", "left", "back"):
        x0, y0, x1, y1 = f[face]
        for x in range(x0, x1):
            for y in (11, 12):
                put(base, x, y, G_M if y == 11 else G_D)
    # frame on the front, rows 10-13, lens inside rows 11-12
    x0, y0, x1, y1 = f["front"]
    for x in range(x0, x1):
        for y in range(10, 14):
            put(base, x, y, G_L if y == 10 else G_D)
    for x in range(x0 + 1, x1 - 1):
        for y in (11, 12):
            put(over, x, y, lens(x - x0, y - 11, 2))
    put(over, x0 + 3, 11, BLACK)  # nose bridge shadow between the eyes
    put(over, x0 + 4, 11, BLACK)
    return base, over


def helmet_layers():
    base = canvas(64, 32)
    over = canvas(64, 32)
    f = head_faces()
    # shell: top, upper part of every side, vent ridges on top
    x0, y0, x1, y1 = f["top"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(base, x, y, G_M if (x - x0) in (2, 5) else G_L)
    for face in ("right", "left", "back", "front"):
        x0, y0, x1, y1 = f[face]
        rows = range(8, 11) if face == "front" else range(8, 14)
        for y in rows:
            for x in range(x0, x1):
                put(base, x, y, G_L if y < 12 else G_M)
    # goggles strapped on, undyed (black strap, mirror lens)
    for face in ("right", "left", "back"):
        x0, y0, x1, y1 = f[face]
        for x in range(x0, x1):
            put(over, x, 13, BLACK)
    x0, y0, x1, y1 = f["front"]
    for x in range(x0, x1):
        put(over, x, 11, DARK)
        put(over, x, 14, DARK)
    for x in range(x0 + 1, x1 - 1):
        for y in (12, 13):
            put(over, x, y, lens(x - x0, y - 12, 2))
    return base, over


def beanie_layers():
    base = canvas(64, 32)
    over = canvas(64, 32)
    f = head_faces()
    x0, y0, x1, y1 = f["top"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(base, x, y, G_L if (x + y) % 2 == 0 else G_M)
    for face in ("right", "left", "back", "front"):
        x0, y0, x1, y1 = f[face]
        for y in range(8, 12):
            for x in range(x0, x1):
                rib = G_L if (x - x0) % 2 == 0 else G_M
                put(base, x, y, G_D if y == 11 else rib)  # folded cuff
    # a white stripe knitted around the middle, and a little logo tag on the front cuff
    for face in ("right", "left", "back", "front"):
        x0, y0, x1, y1 = f[face]
        for x in range(x0, x1):
            put(over, x, 9, WHITE)
    put(over, 13, 11, hexc("#E23B3B"))
    put(over, 14, 11, hexc("#E23B3B"))
    # pom-pom fluff on top
    for (x, y) in ((11, 3), (12, 3), (11, 4), (12, 4), (13, 4), (10, 4)):
        put(over, x, y, WHITE)
    return base, over


def box_rects(u, v, w, h, d):
    return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


BODY = box_rects(16, 16, 8, 12, 4)
ARM = box_rects(40, 16, 4, 12, 4)
LEG = box_rects(0, 16, 4, 12, 4)


def fill_box(img, rects, color_fn, faces=None, rows=None):
    for face, (x0, y0, x1, y1) in rects.items():
        if faces and face not in faces:
            continue
        for y in range(y0, y1):
            if rows and face not in ("top", "bottom") and not (rows[0] <= y - y0 < rows[1]):
                continue
            for x in range(x0, x1):
                put(img, x, y, color_fn(face, x - x0, y - y0, x, y))


def jacket_layers():
    base = canvas(64, 32)
    over = canvas(64, 32)

    def quilted(face, fx, fy, x, y):
        c = G_L
        if fy % 4 == 3:
            c = G_M  # quilting seams
        if noise2(x, y, 31) > 0.9:
            c = shade(c, -0.05)
        return c
    fill_box(base, BODY, quilted)
    fill_box(base, ARM, quilted)
    # zipper, collar, hem, cuffs, arm stripes, chest logo
    fx0, fy0, fx1, fy1 = BODY["front"]
    for y in range(fy0, fy1):
        put(over, fx0 + 3, y, DARK)
        put(over, fx0 + 4, y, SILVER if y % 3 == 0 else DARK)
    for x in range(BODY["front"][0], BODY["back"][2]):
        put(over, x, 20, DARK)       # collar
        put(over, x, 31, DARK)       # hem
    for x in range(BODY["right"][0], BODY["right"][2]):
        put(over, x, 20, DARK)
        put(over, x, 31, DARK)
    for (x, y) in ((25, 22), (26, 23), (25, 24), (27, 22), (27, 24), (26, 22), (26, 24)):
        put(over, x, y, WHITE)       # snowflake badge on the chest
    for x in range(ARM["right"][0], ARM["back"][2]):
        put(over, x, 23, WHITE)
        put(over, x, 24, WHITE)
        put(over, x, 30, DARK)
        put(over, x, 31, DARK)
    return base, over


def pants_layers():
    base = canvas(64, 32)
    over = canvas(64, 32)

    def fabric(face, fx, fy, x, y):
        return G_M if (fx + fy) % 6 == 0 else G_L
    fill_box(base, LEG, fabric)
    fill_box(base, BODY, fabric, faces=("front", "back", "right", "left"), rows=(6, 12))
    # knee pads, gaiter cuffs, thigh vent zips
    for x in range(LEG["front"][0], LEG["front"][2]):
        for y in (25, 26):
            put(over, x, y, DARK)
    for x in range(LEG["right"][0], LEG["back"][2]):
        put(over, x, 31, BLACK)
    for y in range(20, 25):
        put(over, LEG["left"][0] + 1, y, SILVER)
    for x in range(BODY["front"][0], BODY["back"][2]):
        put(over, x, 26, DARK)       # waistband
    return base, over


def boots_layer(shell, accent, soft=False):
    img = canvas(64, 32)

    def boot(face, fx, fy, x, y):
        if fy < 6:
            return None
        c = shell if fy < 11 else BLACK
        if not soft and face == "front" and fy in (7, 9):
            return SILVER   # buckles
        if soft and face == "front" and 6 <= fy < 11 and (fx + fy) % 2 == 0:
            return WHITE    # laces
        if fy == 6:
            return accent   # cuff
        return c
    for face, (x0, y0, x1, y1) in LEG.items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if face in ("top",):
                    continue
                c = boot(face, x - x0, y - y0, x, y) if face != "bottom" else BLACK
                if c is not None:
                    put(img, x, y, c)
    return img


# --------------------------------------------------------------------------------------------- icons

def icon_goggles():
    base = canvas(16, 16)
    over = canvas(16, 16)
    for x in range(1, 15):
        put(base, x, 7, G_M)
        put(base, x, 8, G_D)
    rect(base, 3, 5, 13, 11, G_L)
    rect(over, 4, 6, 12, 10, T)
    for y in range(6, 10):
        for x in range(4, 12):
            put(over, x, y, lens(x, y - 6, 4))
    put(over, 7, 9, BLACK)
    put(over, 8, 9, BLACK)
    return outline_inside(base, 0.4), over


def icon_helmet():
    base = canvas(16, 16)
    over = canvas(16, 16)
    for y in range(2, 11):
        for x in range(2, 14):
            dx = (x - 7.5) / 6.0
            dy = (y - 10.0) / 8.0
            if dx * dx + dy * dy <= 1.0:
                put(base, x, y, G_L if y < 7 else G_M)
    for x in (5, 8, 10):
        put(base, x, 3, G_D)
    rect(over, 3, 9, 13, 12, DARK)
    for y in (10, 11):
        for x in range(4, 12):
            put(over, x, y, lens(x, y - 10, 2))
    return outline_inside(base, 0.4), over


def icon_beanie():
    base = canvas(16, 16)
    over = canvas(16, 16)
    for y in range(5, 13):
        for x in range(3, 13):
            dx = (x - 7.5) / 5.0
            dy = (y - 12.0) / 7.0
            if dx * dx + dy * dy <= 1.0 or y >= 10:
                put(base, x, y, G_D if y >= 11 else (G_L if (x % 2) else G_M))
    for x in range(3, 13):
        put(over, x, 8, WHITE)
    for (x, y) in ((7, 2), (8, 2), (6, 3), (7, 3), (8, 3), (9, 3), (7, 4), (8, 4)):
        put(over, x, y, WHITE)
    return outline_inside(base, 0.4), over


def icon_jacket():
    base = canvas(16, 16)
    over = canvas(16, 16)
    rect(base, 4, 3, 12, 14, G_L)
    rect(base, 1, 4, 4, 12, G_M)
    rect(base, 12, 4, 15, 12, G_M)
    for y in range(3, 14):
        put(over, 8, y, DARK)
    rect(over, 6, 2, 11, 4, DARK)
    for x in (1, 2, 3, 12, 13, 14):
        put(over, x, 7, WHITE)
    put(over, 10, 6, WHITE)
    return outline_inside(base, 0.45), over


def icon_pants():
    base = canvas(16, 16)
    over = canvas(16, 16)
    rect(base, 4, 2, 12, 6, G_L)
    rect(base, 4, 6, 7, 15, G_L)
    rect(base, 9, 6, 12, 15, G_L)
    rect(over, 4, 2, 12, 3, DARK)
    for (x0, x1) in ((4, 7), (9, 12)):
        rect(over, x0, 9, x1, 11, DARK)
        rect(over, x0, 14, x1, 15, BLACK)
    return outline_inside(base, 0.45), over


def icon_boots(shell, accent, soft=False):
    img = canvas(16, 16)
    for ox in (1, 8):
        rect(img, ox + 1, 4, ox + 5, 12, shell)
        rect(img, ox + 1, 11, ox + 7, 14, shell)
        rect(img, ox + 1, 13, ox + 7, 14, BLACK)
        put(img, ox + 1, 4, accent)
        put(img, ox + 2, 4, accent)
        put(img, ox + 3, 4, accent)
        put(img, ox + 4, 4, accent)
        if soft:
            for y in range(5, 11, 2):
                put(img, ox + 2, y, WHITE)
                put(img, ox + 3, y + 1, WHITE)
        else:
            put(img, ox + 4, 7, SILVER)
            put(img, ox + 4, 9, SILVER)
    return outline_inside(img, 0.4)


def icon_poles():
    img = canvas(16, 16)
    for (sx, d) in ((3, 1), (12, -1)):
        x = sx
        for y in range(1, 15):
            put(img, int(x), y, SILVER if y > 3 else BLACK)
            x += d * 0.18
        put(img, int(x), 12, DARK)
        put(img, int(x) - 1, 12, DARK)
        put(img, int(x) + 1, 12, DARK)
    put(img, 2, 1, hexc("#E23B3B"))
    put(img, 12, 1, hexc("#E23B3B"))
    return img


def icon_wax():
    img = canvas(16, 16)
    rect(img, 3, 5, 13, 11, hexc("#F2B33D"))
    rect(img, 3, 5, 13, 6, hexc("#FFD77A"))
    rect(img, 3, 10, 13, 11, hexc("#C98618"))
    rect(img, 6, 7, 10, 9, hexc("#2F6FD0"))
    put(img, 7, 7, WHITE)
    return outline_inside(img, 0.4)


def icon_cocoa():
    img = canvas(16, 16)
    mug = hexc("#D8392B")
    rect(img, 3, 6, 11, 14, mug)
    rect(img, 3, 6, 11, 7, hexc("#5A3221"))
    rect(img, 11, 8, 13, 9, mug)
    rect(img, 12, 8, 13, 12, mug)
    rect(img, 11, 11, 13, 12, mug)
    put(img, 5, 6, WHITE)
    put(img, 8, 6, WHITE)
    for (x, y) in ((5, 4), (6, 3), (8, 4), (9, 2), (7, 2)):
        put(img, x, y, (230, 230, 240, 150))
    return outline_inside(img, 0.4)


def icon_warmth():
    img = canvas(18, 18)
    flame = [(8, 3), (9, 3), (7, 4), (8, 4), (9, 4), (10, 4), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5)]
    for y in range(6, 14):
        w = 6 - abs(y - 10) // 2
        for x in range(9 - w, 9 + w):
            put(img, x, y, hexc("#F28C28") if abs(x - 8.5) > w - 2 else hexc("#FFD24A"))
    for (x, y) in flame:
        put(img, x, y, hexc("#F25C28"))
    return outline_inside(img, 0.3)


DEFAULTS = {
    "ski_goggles": "#2B2D33",
    "ski_helmet": "#F2F4F7",
    "beanie": "#D8392B",
    "ski_jacket": "#FF7A2E",
    "snow_pants": "#2A3550",
}


def pole_model_texture():
    """UV sheet for the 3D pole held while skiing (models/item/ski_pole_held.json)."""
    img = canvas(16, 16)
    rect(img, 0, 0, 2, 6, hexc("#1E1F24"))            # grip
    put(img, 0, 1, hexc("#3A3C44"))
    put(img, 1, 4, hexc("#3A3C44"))
    for y in range(16):                                   # shaft
        put(img, 3, y, hexc("#9CC0E6") if y % 5 else hexc("#7FA3CC"))
    rect(img, 6, 0, 11, 7, hexc("#22242A"))               # basket
    rect(img, 7, 3, 10, 6, hexc("#34373F"))
    rect(img, 13, 0, 14, 2, hexc("#B9C0C8"))              # tip
    rect(img, 15, 0, 16, 3, hexc("#D3342C"))              # strap
    return img


def main(preview_dir: str | None):
    layers = {
        "ski_goggles": goggles_layers(),
        "ski_helmet": helmet_layers(),
        "beanie": beanie_layers(),
        "ski_jacket": jacket_layers(),
    }
    sheet = []
    for name, (base, over) in layers.items():
        save(base, f"textures/entity/equipment/humanoid/{name}.png")
        save(over, f"textures/entity/equipment/humanoid/{name}_overlay.png")
        sheet += [(name, base), (name + "_o", over)]
    base, over = pants_layers()
    save(base, "textures/entity/equipment/humanoid_leggings/snow_pants.png")
    save(over, "textures/entity/equipment/humanoid_leggings/snow_pants_overlay.png")
    sheet += [("pants", base), ("pants_o", over)]
    ski_boots = boots_layer(hexc("#D8392B"), hexc("#1E2026"))
    board_boots = boots_layer(hexc("#5A3A22"), hexc("#F28C28"), soft=True)
    save(ski_boots, "textures/entity/equipment/humanoid/ski_boots.png")
    save(board_boots, "textures/entity/equipment/humanoid/snowboard_boots.png")
    sheet += [("ski_boots", ski_boots), ("board_boots", board_boots)]

    icons = {
        "ski_goggles": icon_goggles(), "ski_helmet": icon_helmet(), "beanie": icon_beanie(),
        "ski_jacket": icon_jacket(), "snow_pants": icon_pants(),
    }
    icon_sheet = []
    for name, (base, over) in icons.items():
        save(base, f"textures/item/{name}.png")
        save(over, f"textures/item/{name}_overlay.png")
        icon_sheet += [(name, base), (name + "_o", over)]
    singles = {
        "ski_boots": icon_boots(hexc("#D8392B"), hexc("#1E2026")),
        "snowboard_boots": icon_boots(hexc("#5A3A22"), hexc("#F28C28"), soft=True),
        "ski_pole_model": pole_model_texture(), "ski_poles": icon_poles(), "ski_wax": icon_wax(), "hot_cocoa": icon_cocoa(),
    }
    for name, img in singles.items():
        save(img, f"textures/item/{name}.png")
        icon_sheet.append((name, img))
    save(icon_warmth(), "textures/mob_effect/warmth.png")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet(sheet, factor=6, cols=4).save(os.path.join(preview_dir, "gear_layers.png"))
        contact_sheet(icon_sheet, factor=8, cols=8).save(os.path.join(preview_dir, "gear_icons.png"))
    print("gear: wrote", len(layers) + 3, "layers,", len(icons) + len(singles), "icons")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
