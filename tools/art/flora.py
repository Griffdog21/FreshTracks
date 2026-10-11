"""Mountain plants: larch needles, aspen leaves, their saplings, and icicles.

Leaf textures are tileable with see-through gaps (the game renders any texture with clear pixels as cut-out).
Run: uv run --with pillow --with numpy python tools/art/flora.py [preview_dir]
"""
from __future__ import annotations

import math
import os
import sys

from pix import canvas, contact_sheet, hexc, mix, noise2, outline_inside, put, save, seeded, shade


def _wrap_put(img, x, y, c):
    put(img, x % 16, y % 16, c)


def _nearest(x, y, centres):
    best = None
    for i, (cx, cy) in enumerate(centres):
        for ox in (-16, 0, 16):
            for oy in (-16, 0, 16):
                dx, dy = x + 0.5 - (cx + ox), y + 0.5 - (cy + oy)
                d = math.hypot(dx, dy)
                if best is None or d < best[0]:
                    best = (d, math.atan2(dy, dx), i)
    return best


def larch_needles():
    """Golden larch: starbursts of needles around little spurs, with gaps between the rays."""
    deep, dark, mid, light, high = hexc("#5E3B0E"), hexc("#9C6314"), hexc("#D08F26"), hexc("#F0BD48"), hexc("#FFE28C")
    olive = hexc("#8F8C34")
    centres = [(2.5, 2.5), (8.5, 1.5), (14, 4.5), (5, 7.5), (11, 8.5), (1.5, 11), (7.5, 13), (13.5, 13.5), (9.5, 5)]
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d, ang, i = _nearest(x, y, centres)
            r = 3.0 + noise2(x, y, 11) * 0.9
            if d > r:
                continue
            arms = 7
            ray = ((ang + math.pi) / math.tau * arms + i * 0.37) % 1.0
            on_ray = abs(ray - 0.5) < 0.3
            tip = d / r
            if not on_ray and tip > 0.4:
                continue
            lit = 0.5 - 0.5 * math.cos(ang + 0.8)  # rays pointing up-left are lit
            if d < 0.9:
                c = deep
            elif not on_ray:
                c = dark
            else:
                c = mix(mid, light, lit) if tip < 0.7 else mix(light, high, lit * 0.8)
            if i in (3, 7) and d < 1.8:
                c = mix(c, olive, 0.55)
            put(img, x, y, c)
    return img


def aspen_leaves():
    """Round gold aspen leaves overlapping; a few orange, a few still green."""
    rng = seeded(78)
    gold = [hexc("#734A12"), hexc("#B07618"), hexc("#E0A62A"), hexc("#F6CC44"), hexc("#FFEA92")]
    orange = [hexc("#733610"), hexc("#AE5419"), hexc("#DD7A2A"), hexc("#F39C46"), hexc("#FFC38A")]
    green = [hexc("#4C561A"), hexc("#798626"), hexc("#A3AC3A"), hexc("#C6CC60"), hexc("#E2E598")]
    centres = []
    for gy in range(5):
        for gx in range(5):
            centres.append((gx * 3.2 + rng.uniform(0.3, 2.9), gy * 3.2 + rng.uniform(0.3, 2.9)))
    palettes = [orange if i in (4, 17) else green if i in (9, 21) else gold for i in range(len(centres))]
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d, ang, i = _nearest(x, y, centres)
            if d > 1.75:
                continue
            pal = palettes[i]
            lit = 0.5 - 0.5 * math.cos(ang + 0.8)
            k = 1 if d > 1.3 and lit < 0.5 else 2 if lit < 0.35 else 3 if lit < 0.8 else 4
            if d < 0.6:
                k = 3
            put(img, x, y, pal[k])
    return img


def snowy_fir_needles():
    """Snowmass spruce and fir in winter: dark blue-green needle sprays, clumps of snow sitting on top of them."""
    rng = seeded(41)
    needle = [hexc("#16261C"), hexc("#203A28"), hexc("#2C4D34"), hexc("#3D6446"), hexc("#55805C")]
    snow = [hexc("#BFD3E6"), hexc("#DCE8F4"), hexc("#F2F7FC"), hexc("#FFFFFF")]
    img = canvas(16, 16)
    # needle sprays: short diagonal strokes radiating from little twigs
    for _ in range(30):
        cx, cy = rng.uniform(0, 16), rng.uniform(0, 16)
        for k in range(rng.randint(6, 9)):
            ang = rng.uniform(-math.pi, math.pi)
            ln = rng.uniform(1.5, 3.2)
            for step in range(int(ln * 2) + 1):
                t = step / 2
                x, y = cx + math.cos(ang) * t, cy + math.sin(ang) * t
                lit = 0.5 - 0.5 * math.sin(ang)  # needles pointing up catch light
                c = needle[1 + int(lit * 2.9)] if step else needle[0]
                _wrap_put(img, int(x), int(y), c)
    # snow clumps: soft blobs on the upper side of the sprays
    for _ in range(9):
        cx, cy = rng.uniform(0, 16), rng.uniform(0, 16)
        r = rng.uniform(1.2, 2.3)
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                d = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.4)
                if d <= r and img[y % 16, x % 16, 3] > 0 or d <= r * 0.6:
                    k = 3 if y < cy - r * 0.2 else 2 if d < r * 0.7 else 1
                    if d > r * 0.85:
                        k = 0
                    _wrap_put(img, x, y, snow[k])
    return img


def aspen_twigs():
    """Aspen crowns in winter: bare grey twigs, a few buds, see-through between them."""
    rng = seeded(91)
    twig = [hexc("#4A4540"), hexc("#6B655D"), hexc("#8C857A"), hexc("#B5ADA0")]
    img = canvas(16, 16)

    def branch(x, y, ang, length, depth):
        steps = int(length * 2)
        for step in range(steps):
            x += math.cos(ang) * 0.5
            y += math.sin(ang) * 0.5
            k = 1 if depth == 0 else 2 if depth == 1 else 3
            _wrap_put(img, int(x), int(y), twig[k] if (step % 5) else twig[0])
            if depth < 2 and step > 1 and rng.random() < 0.18:
                branch(x, y, ang + rng.choice((-1, 1)) * rng.uniform(0.4, 0.9), length * 0.55, depth + 1)
    for _ in range(8):
        branch(rng.uniform(0, 16), rng.uniform(0, 16), rng.uniform(-math.pi, 0), rng.uniform(5, 8), 0)
    for _ in range(6):
        _wrap_put(img, rng.randint(0, 15), rng.randint(0, 15), hexc("#9A7B4E"))  # buds
    return img


def larch_sapling():
    img = canvas(16, 16)
    bark, bark_d = hexc("#7A4A2A"), hexc("#4E2E1A")
    needle = [hexc("#9C6314"), hexc("#D08F26"), hexc("#F0BD48"), hexc("#FFE28C")]
    for y in range(11, 16):
        put(img, 7, y, bark)
        put(img, 8, y, bark_d)
    for y in range(2, 12):
        half = (y - 1) // 2
        tier_shade = 0 if y % 3 == 2 else 1
        for x in range(8 - half - 1, 8 + half + 1):
            edge = x in (8 - half - 1, 8 + half)
            c = needle[tier_shade] if edge else needle[2 if x < 8 else 1]
            if not edge and (x + y) % 4 == 0:
                c = needle[3]
            put(img, x, y, c)
    put(img, 7, 1, needle[3])
    put(img, 8, 1, needle[2])
    return img


def aspen_sapling():
    img = canvas(16, 16)
    bark, nick = hexc("#EDEBE2"), hexc("#3C3A36")
    leaf = [hexc("#B07618"), hexc("#E0A62A"), hexc("#F6CC44"), hexc("#FFEA92")]
    for y in range(6, 16):
        put(img, 8, y, bark)
        if y in (9, 13):
            put(img, 8, y, nick)
    put(img, 9, 9, bark)
    put(img, 10, 8, bark)
    clusters = [(6, 2), (9, 1), (4, 5), (10, 4), (7, 5), (11, 7)]
    for (cx, cy) in clusters:
        for (dx, dy, k) in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (1, 1, 1), (1, 2, 0)):
            put(img, cx + dx, cy + dy, leaf[k])
    return img


ICE = [hexc("#6FAFDC"), hexc("#97CDEF"), hexc("#C6E7FA"), hexc("#EEF8FF"), hexc("#FFFFFF")]


def _icicle_column(img, top: int, bottom: int, width_at, seed: int):
    for y in range(top, bottom):
        w = width_at(y)
        if w <= 0:
            continue
        x0 = 8 - (w + 1) // 2
        for i in range(w):
            x = x0 + i
            s = (i + 0.5) / w
            k = 3 if s < 0.35 else 2 if s < 0.7 else 1
            if w >= 3 and i == w - 1:
                k = 0
            if noise2(x, y, seed) > 0.86:
                k = min(4, k + 1)
            put(img, x, y, ICE[k])


def icicle_tip():
    img = canvas(16, 16)
    _icicle_column(img, 0, 13, lambda y: 4 if y < 3 else 3 if y < 6 else 2 if y < 9 else 1, 3)
    return img


def icicle_body():
    img = canvas(16, 16)
    _icicle_column(img, 0, 16, lambda y: 6 if (y % 7) < 4 else 5, 4)
    return img


def icicle_item():
    img = canvas(16, 16)
    _icicle_column(img, 1, 15, lambda y: 6 if y < 4 else 5 if y < 7 else 4 if y < 9 else 3 if y < 11 else 2 if y < 13 else 1, 5)
    for x in range(4, 12):
        put(img, x, 0, hexc("#F4FAFF"))
    return outline_inside(img, 0.3)


def main(preview_dir: str | None):
    out = {
        "block/larch_needles": larch_needles(),
        "block/aspen_leaves": aspen_leaves(),
        "block/snowy_fir_needles": snowy_fir_needles(),
        "block/aspen_twigs": aspen_twigs(),
        "block/larch_sapling": larch_sapling(),
        "block/aspen_sapling": aspen_sapling(),
        "block/icicle_tip": icicle_tip(),
        "block/icicle_body": icicle_body(),
        "item/icicle": icicle_item(),
    }
    for name, img in out.items():
        save(img, f"textures/{name}.png")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        tiles = []
        for name in ("block/larch_needles", "block/aspen_leaves", "block/snowy_fir_needles", "block/aspen_twigs"):
            import numpy as np
            tiled = np.concatenate([np.concatenate([out[name]] * 3, axis=1)] * 3, axis=0)
            tiles.append((name + "_tiled", tiled))
        contact_sheet(list(out.items()), factor=10, cols=7).save(os.path.join(preview_dir, "flora.png"))
        contact_sheet(tiles, factor=6, cols=2, bg=(120, 160, 220, 255)).save(os.path.join(preview_dir, "flora_tiled.png"))
    print("flora: wrote", len(out), "textures")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
