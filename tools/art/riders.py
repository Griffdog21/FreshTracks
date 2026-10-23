"""Skins for the skiers riding the lifts (LiftRenderer): standard 64x64 player skins dressed for the mountain, a helmet
and goggles, a jacket in one of the shop colours, gloves, snow pants and boots. Eight riders, so a busy lift isn't
a row of twins.

Run: uv run --with pillow --with numpy python tools/art/riders.py [preview_dir]
"""
from __future__ import annotations

import os
import sys

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "freshtracks", "textures", "entity", "rider")

# (jacket, jacket shade, pants, helmet, skin)
RIDERS = [
    ((216, 57, 43), (160, 38, 30), (42, 53, 80), (240, 242, 246), (231, 188, 152)),
    ((43, 108, 217), (28, 74, 156), (36, 38, 44), (32, 34, 40), (201, 150, 112)),
    ((63, 166, 94), (40, 118, 64), (52, 46, 40), (240, 242, 246), (241, 204, 170)),
    ((224, 169, 58), (170, 122, 32), (42, 53, 80), (214, 57, 43), (156, 106, 72)),
    ((123, 63, 217), (86, 40, 160), (30, 30, 34), (240, 242, 246), (231, 188, 152)),
    ((255, 122, 46), (196, 84, 22), (44, 48, 56), (36, 38, 44), (110, 74, 52)),
    ((236, 238, 242), (196, 200, 208), (58, 64, 74), (43, 108, 217), (241, 204, 170)),
    ((32, 34, 40), (20, 22, 26), (90, 32, 36), (224, 169, 58), (201, 150, 112)),
]

# Box-UV origins of the skin (u, v, w, h, d), base layer and overlay layer.
HEAD, HAT = (0, 0, 8, 8, 8), (32, 0, 8, 8, 8)
BODY, JACKET = (16, 16, 8, 12, 4), (16, 32, 8, 12, 4)
R_ARM, R_SLEEVE = (40, 16, 4, 12, 4), (40, 32, 4, 12, 4)
L_ARM, L_SLEEVE = (32, 48, 4, 12, 4), (48, 48, 4, 12, 4)
R_LEG, R_PANTS = (0, 16, 4, 12, 4), (0, 32, 4, 12, 4)
L_LEG, L_PANTS = (16, 48, 4, 12, 4), (0, 48, 4, 12, 4)


def faces(box):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(img, box, painter):
    px = img.load()
    for face, (fx, fy, fw, fh) in faces(box).items():
        for y in range(fh):
            for x in range(fw):
                c = painter(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c + (255,) if len(c) == 3 else c


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def rider(jacket, jacket_d, pants, helmet, skin):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    goggle = (24, 26, 30)
    lens = (232, 150, 52)
    glove = (30, 32, 36)
    boot = (46, 48, 54)

    def head(face, x, y, w, h):
        if face == "top":
            return helmet
        if face == "bottom":
            return skin
        if y < 3:
            return helmet  # the helmet down to the brow
        if face == "front":
            if y in (3, 4):
                return lens if 1 <= x <= 6 and y == 3 else goggle  # the goggles
            if y == 6 and 2 <= x <= 5:
                return shade(skin, 0.8)  # mouth
            return skin
        if face == "back" and y < 6:
            return helmet
        return goggle if y == 3 else skin  # the goggle strap round the sides

    def body(face, x, y, w, h):
        if y == 0 and face in ("front", "back"):
            return shade(jacket, 0.8)  # the collar
        if face == "front" and x == w // 2 and y >= 1:
            return shade(jacket, 0.7)  # the zip
        if y >= h - 2:
            return jacket_d  # the hem
        if face in ("left", "right"):
            return jacket_d
        return jacket

    def arm(face, x, y, w, h):
        if y >= h - 3:
            return glove
        if face == "top":
            return jacket
        return jacket if face in ("front", "back") or x < w - 1 else jacket_d

    def leg(face, x, y, w, h):
        if y >= h - 4 or face == "bottom":
            return boot if not (face == "front" and y == h - 4) else shade(boot, 1.4)  # boot buckle
        return pants if face != "right" else shade(pants, 0.85)

    paint(img, HEAD, head)
    paint(img, BODY, body)
    paint(img, R_ARM, arm)
    paint(img, L_ARM, arm)
    paint(img, R_LEG, leg)
    paint(img, L_LEG, leg)

    # overlay: the helmet's shell stands proud of the head, and the goggles' frame
    def hat(face, x, y, w, h):
        if face == "top":
            return helmet
        if face == "front":
            if y == 3:
                return goggle if x in (0, 7) else None
            return helmet if y < 2 else None
        if face == "bottom":
            return None
        return helmet if y < 2 else (goggle if y == 3 else None)

    paint(img, HAT, hat)
    return img


def main(preview_dir: str | None):
    os.makedirs(OUT, exist_ok=True)
    imgs = []
    for i, r in enumerate(RIDERS):
        img = rider(*r)
        img.save(os.path.join(OUT, f"rider_{i}.png"))
        imgs.append(img)
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        sheet = Image.new("RGBA", (64 * len(imgs), 64))
        for i, img in enumerate(imgs):
            sheet.paste(img, (64 * i, 0))
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(os.path.join(preview_dir, "riders.png"))
    print("riders: wrote", len(imgs), "skins")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
