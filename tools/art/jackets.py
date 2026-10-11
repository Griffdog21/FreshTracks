"""More jackets: puffer, shell, anorak, retro and a race suit. Each is a dyeable grayscale base (64x32 humanoid
armor layer, tinted by dye) plus an undyed overlay with the details, and a matching 16x16 icon pair.

Run: uv run --with pillow --with numpy python tools/art/jackets.py [preview_dir]
"""
from __future__ import annotations

import os
import sys

from gear import ARM, BLACK, BODY, DARK, G_D, G_L, G_M, SILVER, WHITE, fill_box
from pix import canvas, contact_sheet, hexc, noise2, outline_inside, put, rect, save, shade

NEON_PINK = hexc("#FF3FA4")
NEON_YELLOW = hexc("#FFE23F")
NEON_CYAN = hexc("#2FE0F0")
RED = hexc("#D8392B")


def _hem_collar(over, colour=DARK):
    for x in range(BODY["right"][0], BODY["back"][2]):
        put(over, x, 20, colour)
        put(over, x, 31, colour)


def _zipper(over, length=12):
    fx0, fy0, _, _ = BODY["front"]
    for y in range(fy0, fy0 + length):
        put(over, fx0 + 3, y, DARK)
        put(over, fx0 + 4, y, SILVER if y % 3 == 0 else DARK)


def puffer_layers():
    """Fat horizontal baffles, shaded so they look stuffed."""
    base = canvas(64, 32)
    over = canvas(64, 32)

    def baffles(face, fx, fy, x, y):
        row = fy % 3
        return (G_L, G_L, G_D)[row] if face not in ("top", "bottom") else G_M
    fill_box(base, BODY, baffles)
    fill_box(base, ARM, baffles)
    _zipper(over)
    _hem_collar(over)
    for x in range(BODY["right"][0], BODY["back"][2]):
        put(over, x, 21, DARK)  # a tall collar
    for x in range(ARM["right"][0], ARM["back"][2]):
        put(over, x, 31, DARK)
    return base, over


def shell_layers():
    """A thin technical shell: diagonal panels, taped seams, pit zips."""
    base = canvas(64, 32)
    over = canvas(64, 32)

    def panels(face, fx, fy, x, y):
        if (fx + fy) % 9 == 0:
            return G_M  # taped seam
        return G_L if noise2(x, y, 51) > 0.08 else G_M
    fill_box(base, BODY, panels)
    fill_box(base, ARM, panels)
    _zipper(over)
    _hem_collar(over)
    # pit zips on the sides, a chest pocket zip
    for y in range(21, 26):
        put(over, BODY["right"][0] + 1, y, SILVER)
        put(over, BODY["left"][0] + 2, y, SILVER)
    for x in range(BODY["front"][0] + 5, BODY["front"][0] + 8):
        put(over, x, 22, SILVER)
    return base, over


def anorak_layers():
    """A pullover: half zip, kangaroo pocket, drawcord at the hem."""
    base = canvas(64, 32)
    over = canvas(64, 32)

    def cotton(face, fx, fy, x, y):
        return G_M if (fx * 3 + fy) % 7 == 0 else G_L
    fill_box(base, BODY, cotton)
    fill_box(base, ARM, cotton)
    _zipper(over, length=5)
    _hem_collar(over)
    fx0 = BODY["front"][0]
    for x in range(fx0 + 1, fx0 + 7):
        put(over, x, 26, DARK)  # pocket top
    for y in range(26, 30):
        put(over, fx0 + 1, y, DARK)
        put(over, fx0 + 6, y, DARK)
    put(over, fx0 + 2, 31, WHITE)  # drawcord toggles
    put(over, fx0 + 5, 31, WHITE)
    return base, over


def retro_layers():
    """An 80s jacket: a neon chevron band across the chest and sleeves."""
    base = canvas(64, 32)
    over = canvas(64, 32)

    def plain(face, fx, fy, x, y):
        return G_L
    fill_box(base, BODY, plain)
    fill_box(base, ARM, plain)
    _zipper(over)
    _hem_collar(over, BLACK)

    def band(face, fx, fy, x, y):
        if 5 <= fy <= 7:
            return (NEON_PINK, NEON_YELLOW, NEON_CYAN)[(fx + fy) % 3]
        return None
    for rects in (BODY, ARM):
        for face, (x0, y0, x1, y1) in rects.items():
            if face in ("top", "bottom"):
                continue
            for y in range(y0, y1):
                for x in range(x0, x1):
                    c = band(face, x - x0, y - y0, x, y)
                    if c:
                        put(over, x, y, c)
    return base, over


def race_suit_layers():
    """Skin-tight with white side panels and a bib number on the chest."""
    base = canvas(64, 32)
    over = canvas(64, 32)

    def lycra(face, fx, fy, x, y):
        return G_L if (x + y) % 11 else G_M
    fill_box(base, BODY, lycra)
    fill_box(base, ARM, lycra)
    for face in ("right", "left"):
        x0, y0, x1, y1 = BODY[face]
        for y in range(y0, y1):
            for x in range(x0 + 1, x1 - 1):
                put(over, x, y, WHITE)
    for face in ("right", "left"):
        x0, y0, x1, y1 = ARM[face]
        for y in range(y0, y1):
            put(over, x0 + 1, y, WHITE)
    fx0, fy0, _, _ = BODY["front"]
    rect(over, fx0 + 1, fy0 + 2, fx0 + 7, fy0 + 7, WHITE)  # race bib
    for (dx, dy) in ((3, 3), (3, 4), (3, 5), (4, 3), (5, 3), (5, 4), (5, 5), (4, 5)):
        put(over, fx0 + dx, fy0 + dy, BLACK)  # a big "0"
    return base, over


def icon(style: str):
    base = canvas(16, 16)
    over = canvas(16, 16)
    rect(base, 4, 3, 12, 14, G_L)
    rect(base, 1, 4, 4, 12, G_M)
    rect(base, 12, 4, 15, 12, G_M)
    if style == "puffer":
        for y in (5, 8, 11):
            for x in range(1, 15):
                put(base, x, y, G_D)
        rect(over, 6, 1, 11, 4, DARK)
    elif style == "shell":
        for i in range(8):
            put(base, 4 + i, 4 + i, G_M)
        put(over, 3, 6, SILVER)
        put(over, 12, 6, SILVER)
    elif style == "anorak":
        rect(over, 5, 9, 11, 13, DARK)
        rect(over, 6, 10, 10, 12, (0, 0, 0, 0))
    elif style == "retro":
        for x in range(1, 15):
            put(over, x, 7, (NEON_PINK, NEON_YELLOW, NEON_CYAN)[x % 3])
            put(over, x, 8, (NEON_YELLOW, NEON_CYAN, NEON_PINK)[x % 3])
    elif style == "race":
        rect(over, 6, 5, 10, 9, WHITE)
        put(over, 7, 6, BLACK)
        put(over, 8, 7, BLACK)
        for y in range(4, 12):
            put(over, 1, y, WHITE)
            put(over, 14, y, WHITE)
    if style not in ("anorak", "race"):
        for y in range(3, 14):
            put(over, 8, y, DARK)
    return outline_inside(base, 0.45), over


JACKETS = {
    "puffer_jacket": (puffer_layers, "puffer"),
    "shell_jacket": (shell_layers, "shell"),
    "anorak": (anorak_layers, "anorak"),
    "retro_jacket": (retro_layers, "retro"),
    "race_suit": (race_suit_layers, "race"),
}


def main(preview_dir: str | None):
    sheet = []
    for name, (layers, style) in JACKETS.items():
        base, over = layers()
        save(base, f"textures/entity/equipment/humanoid/{name}.png")
        save(over, f"textures/entity/equipment/humanoid/{name}_overlay.png")
        ib, io = icon(style)
        save(ib, f"textures/item/{name}.png")
        save(io, f"textures/item/{name}_overlay.png")
        sheet += [(name, base), (name + "_o", over), (name + "_i", ib), (name + "_io", io)]
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet(sheet, factor=6, cols=4).save(os.path.join(preview_dir, "jackets.png"))
    print("jackets: wrote", len(JACKETS), "jackets")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
