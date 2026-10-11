"""Resort villager uniforms (profession overlays) and their workstation blocks.

The overlays use the vanilla villager UV layout (64x64): robe box at (0,38) 8x20x6, crossed arms at (44,22) 4x8x4 and
(40,38) 8x4x4, hat layer at (32,0) 8x10x8. Zombie villagers share the layout, so the same image is used for both.
Run: uv run --with pillow --with numpy python tools/art/village.py [preview_dir]
"""
from __future__ import annotations

import json
import os
import sys

from pix import ASSETS, box_faces, canvas, contact_sheet, hexc, mix, noise2, put, rect, save, shade


def _paint(img, u, v, w, h, d, fn):
    """fn(face, x, y, fw, fh) -> colour or None, with x, y local to the face."""
    for face, (x0, y0, x1, y1) in box_faces(u, v, w, h, d).items():
        fw, fh = int(x1 - x0), int(y1 - y0)
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is not None:
                    put(img, int(x0) + x, int(y0) + y, c)


def _cloth(c, x, y, seed):
    """Subtle fabric noise."""
    n = noise2(x, y, seed)
    return shade(c, -0.08) if n < 0.25 else shade(c, 0.05) if n > 0.85 else c


def uniform(jacket, jacket_dark, accent, pants, beanie, band, lens=None, vest=None, hoodie=False, radio=False, seed=1):
    img = canvas(64, 64)

    def robe(face, x, y, fw, fh):
        if face in ("top", "bottom"):
            return _cloth(jacket_dark if face == "top" else pants, x, y, seed)
        if y >= 12:  # pants under the jacket
            c = pants
            if face == "front" and x in (3, 4):
                c = shade(pants, -0.18)  # the gap between the legs
            if y == fh - 1:
                c = shade(pants, -0.25)
            return _cloth(c, x, y + 40, seed)
        if y == 11:
            return shade(jacket_dark, -0.1)  # hem
        c = jacket
        if vest is not None and face in ("front", "back") and 1 <= y <= 10:
            c = vest
            if y in (5, 8):
                c = hexc("#E8EEF2")  # reflective strips
        elif face in ("front", "back", "left", "right") and y in (3, 4) and accent is not None:
            c = accent  # chest stripe
        if y == 0:
            c = jacket_dark  # collar
        if face == "front":
            if vest is None and not hoodie and x == 4 and 1 <= y <= 10:
                c = shade(jacket_dark, -0.25)  # zip
            if hoodie and 6 <= y <= 9 and 1 <= x <= 6:
                c = shade(jacket, -0.2) if y == 6 or x in (1, 6) else shade(jacket, -0.1)  # pocket
            if hoodie and x in (3, 5) and 1 <= y <= 3:
                c = hexc("#F2F2F2")  # drawstrings
            if radio and 1 <= x <= 2 and 2 <= y <= 4:
                c = hexc("#22252B") if y > 2 else hexc("#3A3F48")
        if hoodie and face == "back" and y <= 2:
            c = shade(jacket, -0.22)  # hood resting on the back
        return _cloth(c, x, y + 40, seed)

    _paint(img, 0, 38, 8, 20, 6, robe)

    def sleeves(face, x, y, fw, fh):
        c = jacket
        if accent is not None and y in (3,) and vest is None:
            c = accent
        return _cloth(c, x, y + 20, seed + 1)

    _paint(img, 44, 22, 4, 8, 4, sleeves)
    glove = hexc("#1F2128")

    def hands(face, x, y, fw, fh):
        c = glove if face in ("front", "top", "bottom") or (face in ("left", "right") and x >= 2) else jacket
        return shade(c, 0.08) if noise2(x, y, seed + 2) > 0.8 else c

    _paint(img, 40, 38, 8, 4, 4, hands)

    def hat(face, x, y, fw, fh):
        if face == "top":
            c = beanie
            if (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
                c = band  # pom-pom seen from above
            return _cloth(c, x, y, seed + 3)
        if face == "bottom":
            return None
        if y <= 1:
            return _cloth(beanie, x, y, seed + 3)
        if y == 2:
            if lens is not None and face == "front" and 1 <= x <= 6:
                return lens[0] if x < 4 else lens[1]
            return _cloth(band, x, y, seed + 3)
        if y == 3:
            if lens is not None:
                if face == "front" and 1 <= x <= 6:
                    return lens[1] if x < 4 else lens[2]
                return hexc("#202228")  # goggle strap
            return shade(band, -0.15)
        return None

    _paint(img, 32, 0, 8, 10, 8, hat)
    return img


PROFESSION_LOOKS = {
    # red race jacket with a white stripe, navy beanie, orange mirror goggles
    "ski_tech": dict(jacket=hexc("#D3342C"), jacket_dark=hexc("#8E1F1A"), accent=hexc("#F4F4F4"), pants=hexc("#24262E"),
                     beanie=hexc("#24325E"), band=hexc("#D3342C"), lens=(hexc("#FFD25A"), hexc("#FF8A2A"), hexc("#C9481E")), seed=11),
    # teal hoodie, baggy grey pants, purple beanie, blue mirror goggles
    "board_tech": dict(jacket=hexc("#2C9C8E"), jacket_dark=hexc("#1D6B62"), accent=None, pants=hexc("#4A4E5A"),
                       beanie=hexc("#6E45C4"), band=hexc("#F2C230"), lens=(hexc("#9BE7FF"), hexc("#3C8CFF"), hexc("#2B4FC8")),
                       hoodie=True, seed=21),
    # navy jacket, hi-vis vest, orange beanie, radio
    "liftie": dict(jacket=hexc("#25335C"), jacket_dark=hexc("#172140"), accent=None, pants=hexc("#2B2E36"),
                   beanie=hexc("#F07A22"), band=hexc("#B4541A"), vest=hexc("#E6DB2E"), radio=True, seed=31),
}


# --------------------------------------------------------------------------------------------- blocks

def wood(x, y, seed=3, base=hexc("#7A5534")):
    """Spruce-like planks: horizontal boards 4 px tall."""
    board = y // 4
    c = shade(base, (noise2(board, 0, seed) - 0.5) * 0.2)
    if y % 4 == 3:
        c = shade(base, -0.3)
    if noise2(x // 3 + board * 7, y, seed + 1) > 0.9:
        c = shade(c, -0.12)
    return c


def steel(x, y, seed=5, base=hexc("#8C939C")):
    c = shade(base, (noise2(x, y, seed) - 0.5) * 0.12)
    return c


def ski_bench_top():
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: wood(x, y, 7, hexc("#86603C")))
    for x in range(16):  # edge
        put(img, x, 0, hexc("#5A3E26"))
        put(img, x, 15, hexc("#5A3E26"))
    for y in range(16):
        put(img, 0, y, hexc("#5A3E26"))
        put(img, 15, y, hexc("#5A3E26"))
    # wax drips and a scraper
    for (x, y) in ((3, 3), (4, 3), (3, 4), (12, 11), (11, 12), (12, 12)):
        put(img, x, y, hexc("#9BD3F2"))
    rect(img, 2, 11, 6, 13, hexc("#E0E4EA"))
    rect(img, 2, 11, 6, 12, hexc("#F6F8FA"))
    return img


def ski_bench_side(front=False):
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: wood(x, y, 9, hexc("#7A5534")))
    for x in range(16):
        put(img, x, 3, hexc("#4B321F"))  # under the bench top
    if front:
        for (x0, y0) in ((2, 6), (9, 6)):
            rect(img, x0, y0, x0 + 5, y0 + 4, hexc("#6A4A2E"))
            for x in range(x0, x0 + 5):
                put(img, x, y0, hexc("#4B321F"))
                put(img, x, y0 + 3, hexc("#4B321F"))
            put(img, x0 + 2, y0 + 2, hexc("#C9CED6"))  # handle
        rect(img, 4, 12, 12, 14, hexc("#D3342C"))  # a little ski decal
        rect(img, 4, 12, 12, 13, hexc("#F24A40"))
    return img


def ski_bench_ski():
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = hexc("#D3342C")
            if y % 4 == 1:
                c = hexc("#F4F4F4")
            if y % 4 == 3:
                c = hexc("#8E1F1A")
            put(img, x, y, c)
    rect(img, 0, 13, 2, 16, hexc("#5E646C"))  # vise jaw (uv region)
    rect(img, 0, 13, 2, 14, hexc("#8C939C"))
    return img


def board_press_top():
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: steel(x, y, 13, hexc("#7E858E")))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, hexc("#3E434A"))
        put(img, x + 1, y + 1, hexc("#B9C0C8"))
    # heat platen
    rect(img, 4, 4, 12, 12, hexc("#5A6068"))
    for x in range(5, 11):
        put(img, x, 7, hexc("#E05A2A"))
        put(img, x, 8, hexc("#C2441E"))
    return img


def board_press_side(front=False):
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: steel(x, y, 15, hexc("#868D96")))
    for x in range(16):
        put(img, x, 3, hexc("#3E434A"))
    # a snowboard being pressed: colourful layered edge
    for x in range(1, 15):
        put(img, x, 7, hexc("#6E45C4"))
        put(img, x, 8, hexc("#F2C230"))
        put(img, x, 9, hexc("#2C9C8E"))
    rect(img, 1, 6, 15, 7, hexc("#2E3238"))
    rect(img, 1, 10, 15, 11, hexc("#2E3238"))
    if front:
        # pressure gauge and lever
        for (dx, dy) in ((0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2)):
            put(img, 11 + dx, 12 + dy, hexc("#2E3238"))
        put(img, 12, 13, hexc("#F4F4F4"))
        rect(img, 3, 12, 5, 15, hexc("#C2441E"))
    return img


def lift_control_front():
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, hexc("#E2B21F"))
    for i in range(16):  # hazard border
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, hexc("#1E1F22") if (x + y) % 4 < 2 else hexc("#F2C230"))
    rect(img, 2, 2, 14, 7, hexc("#2B2F36"))  # status screen
    rect(img, 3, 3, 13, 6, hexc("#123B2A"))
    for x in range(4, 12, 2):
        put(img, x, 4, hexc("#46E08A"))
    put(img, 4, 9, hexc("#2FCB5A"))   # start
    put(img, 5, 9, hexc("#2FCB5A"))
    put(img, 4, 10, hexc("#1F9A42"))
    put(img, 5, 10, hexc("#1F9A42"))
    put(img, 8, 9, hexc("#E23A2A"))   # stop
    put(img, 8, 10, hexc("#A82418"))
    rect(img, 10, 9, 13, 12, hexc("#C81E14"))  # big e-stop
    rect(img, 10, 9, 13, 10, hexc("#F24A40"))
    rect(img, 3, 12, 8, 13, hexc("#2B2F36"))  # key slot
    return img


def lift_control_side():
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: shade(hexc("#E2B21F"), (noise2(x, y, 41) - 0.5) * 0.12))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, hexc("#8A6C12"))
    for y in range(4, 12, 2):
        rect(img, 4, y, 12, y + 1, hexc("#A88413"))  # vents
    return img


def lift_control_top():
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: steel(x, y, 43, hexc("#9AA1AA")))
    rect(img, 6, 6, 10, 10, hexc("#F07A22"))  # beacon light
    rect(img, 7, 7, 9, 9, hexc("#FFC07A"))
    return img


def main(preview_dir: str | None):
    out = {}
    for name, look in PROFESSION_LOOKS.items():
        img = uniform(**look)
        out[f"entity/villager/profession/{name}"] = img
        out[f"entity/zombie_villager/profession/{name}"] = img
    out["block/ski_bench_top"] = ski_bench_top()
    out["block/ski_bench_side"] = ski_bench_side()
    out["block/ski_bench_front"] = ski_bench_side(front=True)
    out["block/ski_bench_ski"] = ski_bench_ski()
    out["block/board_press_top"] = board_press_top()
    out["block/board_press_side"] = board_press_side()
    out["block/board_press_front"] = board_press_side(front=True)
    out["block/lift_control_front"] = lift_control_front()
    out["block/lift_control_side"] = lift_control_side()
    out["block/lift_control_top"] = lift_control_top()
    for name, img in out.items():
        save(img, f"textures/{name}.png")
        if "/profession/" in name:
            # our beanies replace the biome hat (e.g. the snowy villagers' fur hat)
            with open(os.path.join(ASSETS, "textures", name + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
                json.dump({"villager": {"hat": "full"}}, f)
                f.write("\n")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet([(k, v) for k, v in out.items() if "zombie" not in k], factor=6, cols=4).save(os.path.join(preview_dir, "village.png"))
    print("village: wrote", len(out), "textures")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
