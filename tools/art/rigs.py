"""Skis and snowboards: entity textures (box-UV, matches RigModels.java) and 16x16 item icons.

Run:  uv run --with pillow --with numpy tools/art/rigs.py [preview_dir]
"""
from __future__ import annotations

import math
import os
import sys

from pix import (canvas, contact_sheet, hexc, mix, noise2, outline_inside, paint_box, put, rect, save, shade,
                 thick_line)

# --------------------------------------------------------------------------------------------- designs
# Each design paints the topsheet: f(along, across, x, y) -> colour. along: 0 = tail .. 1 = tip/nose;
# across: 0 .. 1 left to right.


def wood(base="#B07443", light="#D49A62", dark="#7A4A27", seed=1):
    b, l, d = hexc(base), hexc(light), hexc(dark)

    def f(along, across, x, y):
        grain = math.sin(along * 40 + across * 3 + noise2(x // 2, y, seed) * 2.5)
        c = mix(b, l, 0.5 + grain * 0.35) if grain > 0 else mix(b, d, -grain * 0.6)
        if noise2(x, y, seed + 7) > 0.93:
            c = d
        return c
    return f


def alpine():
    red, dark_red, white, black = hexc("#E3313C"), hexc("#A81E2B"), hexc("#F2F4F7"), hexc("#1C1F2B")

    def f(along, across, x, y):
        if along > 0.88:
            return white
        if along < 0.07:
            return black
        band = (along * 12 + abs(across - 0.5) * 2.5) % 2.0
        if 0.62 < along < 0.8 and band < 0.55:
            return white
        if 0.22 < along < 0.38 and band < 0.4:
            return black
        return red if noise2(x, y, 3) > 0.1 else dark_red
    return f


def race():
    yellow, black, red, white = hexc("#F7E03B"), hexc("#16161E"), hexc("#E8332E"), hexc("#FFFFFF")

    def f(along, across, x, y):
        if along > 0.88:
            return red
        if 0.45 < along < 0.52:
            return white
        if (int(along * 30) % 2 == 0) and along < 0.3:
            return black
        return yellow if across > 0.15 else shade(yellow, -0.15)
    return f


def powder():
    navy, sky, white, ice = hexc("#1D2E63"), hexc("#3D6FD6"), hexc("#F4F8FF"), hexc("#9CD3FF")

    def f(along, across, x, y):
        # a mountain range silhouette across the middle of the ski
        ridge = 0.55 + 0.12 * math.sin(along * 22) + 0.06 * math.sin(along * 51 + 1.3)
        if along > 0.9:
            return ice
        if across < ridge - 0.15 and 0.2 < along < 0.85:
            return white if across > ridge - 0.3 else ice
        return mix(navy, sky, along)
    return f


def park():
    purple, teal, pink, black = hexc("#5B2A86"), hexc("#21C7B4"), hexc("#FF5DA2"), hexc("#14121C")

    def f(along, across, x, y):
        t = (along * 1.4 + across * 0.3) % 1.0
        c = mix(purple, teal, t)
        if abs((along * 18) % 3 - 1.5) < 0.35:
            c = pink
        if along > 0.9 or along < 0.06:
            c = black
        return c
    return f


def snurfer():
    w = wood("#C9925B", "#E2B17E", "#8F5B31", seed=5)
    red = hexc("#C63A2C")

    def f(along, across, x, y):
        if 0.45 < along < 0.55:
            return red
        return w(along, across, x, y)
    return f


def all_mountain_board():
    orange, teal, cream, navy = hexc("#FF7A2E"), hexc("#14A3A8"), hexc("#FFF1DC"), hexc("#13284A")

    def f(along, across, x, y):
        t = along
        c = mix(teal, orange, t * 1.2 - 0.1)
        stripe = abs((across - 0.5) - (t - 0.5) * 0.9)
        if stripe < 0.09:
            c = cream
        if along > 0.93 or along < 0.07:
            c = navy
        return c
    return f


def powder_board():
    forest, mint, snow, bark = hexc("#164E45"), hexc("#3FB59A"), hexc("#F2FBFF"), hexc("#3B2A20")

    def f(along, across, x, y):
        c = mix(forest, mint, along * 0.8)
        # three little pine trees
        for cx, base in ((0.3, 0.35), (0.55, 0.45), (0.78, 0.38)):
            if base < along < base + 0.28:
                half = (base + 0.28 - along) * 0.9
                if abs(across - cx) < half * 0.5:
                    c = snow if along > base + 0.18 else shade(mint, 0.25)
                if abs(across - cx) < 0.04 and along < base + 0.05:
                    c = bark
        if along > 0.94:
            c = snow
        return c
    return f


def park_board():
    pink, black, white = hexc("#FF3E8A"), hexc("#17141F"), hexc("#FFFFFF")

    def f(along, across, x, y):
        checker = (int(along * 21) + int(across * 7)) % 2 == 0
        c = pink if checker else black
        if 0.44 < along < 0.56:
            c = white if (x + y) % 2 == 0 else pink
        return c
    return f


def carving():
    """Glossy fire gradient with black chevrons pointing at the tip."""
    orange, red, black, white = hexc("#FF8A1E"), hexc("#D9261C"), hexc("#141418"), hexc("#F6F6F6")

    def f(along, across, x, y):
        c = mix(red, orange, along)
        chev = (along * 10 - abs(across - 0.5) * 3) % 1.0
        if 0.25 < along < 0.85 and chev < 0.18:
            c = black
        if along > 0.9:
            c = white
        if along < 0.06:
            c = black
        return c
    return f


def freeride():
    """Matte olive with sandy topo contour lines."""
    olive, sand, dark = hexc("#5E6B3A"), hexc("#C9B98A"), hexc("#323A1E")

    def f(along, across, x, y):
        v = math.sin(along * 26 + math.sin(across * 9 + along * 5) * 1.6)
        c = olive
        if abs(v) < 0.18:
            c = sand
        if along > 0.9 or along < 0.06:
            c = dark
        return c
    return f


def touring():
    """Light and plain: white with a teal stripe and a red skin clip at the tip."""
    white, teal, grey, red = hexc("#EEF1F4"), hexc("#16A6A0"), hexc("#AEB6BF"), hexc("#E0412F")

    def f(along, across, x, y):
        c = white if noise2(x, y, 11) > 0.12 else grey
        if abs(across - 0.5) < 0.14:
            c = teal
        if along > 0.92:
            c = red
        return c
    return f


def retro():
    """80s neon zigzags on black."""
    black, pink, yellow, cyan = hexc("#141218"), hexc("#FF3FA4"), hexc("#FFE23F"), hexc("#2FE0F0")

    def f(along, across, x, y):
        zig = abs(((along * 14) % 2) - 1)
        if abs(across - zig) < 0.18:
            return pink
        if abs(across - (1 - zig)) < 0.12:
            return cyan
        if int(along * 28) % 7 == 0:
            return yellow
        return black
    return f


def freeride_board():
    """Night mountains and a full moon on dark red."""
    maroon, rust, cream, night = hexc("#6E1F25"), hexc("#B6452C"), hexc("#F3E6C8"), hexc("#2A1418")

    def f(along, across, x, y):
        ridge = 0.45 + 0.18 * abs(math.sin(across * 7.0)) - 0.1 * abs(math.sin(across * 15 + 1))
        c = mix(night, maroon, along)
        if along < ridge:
            c = rust if along > ridge - 0.08 else mix(maroon, rust, 0.4)
        if math.hypot(along - 0.78, (across - 0.7) * 0.6) < 0.06:
            c = cream
        return c
    return f


def carving_board():
    """Brushed silver with blue racing stripes."""
    silver, steel, blue, navy = hexc("#C9D1DC"), hexc("#8C96A3"), hexc("#2E6BFF"), hexc("#14204A")

    def f(along, across, x, y):
        c = silver if (x + y) % 5 else steel
        if abs(across - 0.38) < 0.07 or abs(across - 0.62) < 0.07:
            c = blue
        if along > 0.93 or along < 0.07:
            c = navy
        return c
    return f


def splitboard():
    """Forest green with the split down the middle and its clips."""
    green, moss, seam, clip = hexc("#2F5D3A"), hexc("#4F8A4C"), hexc("#1A1F1A"), hexc("#D9DEE3")

    def f(along, across, x, y):
        c = mix(green, moss, (math.sin(along * 9) + 1) / 2 * 0.6)
        if abs(across - 0.5) < 0.04:
            c = seam
        if abs(across - 0.5) < 0.12 and int(along * 16) % 5 == 0:
            c = clip
        return c
    return f


def retro_board():
    """90s purple, teal and yellow triangles."""
    purple, teal, yellow, black = hexc("#6A2C91"), hexc("#18B5A5"), hexc("#FFD23F"), hexc("#151217")

    def f(along, across, x, y):
        tri = (int(along * 10) + int(across * 4)) % 3
        c = (purple, teal, yellow)[tri]
        if ((along * 10) % 1.0) < (across * 4 % 1.0) * 0.3:
            c = black
        return c
    return f


DESIGNS = {
    # name: (kind, topsheet, base colour, sidewall, binding main, binding accent)
    "wooden_skis": ("skis", wood(), "#3A2A20", "#5C3E26", "#B08D57", "#6B4B2E"),
    "alpine_skis": ("skis", alpine(), "#16181F", "#2B2F3A", "#F2F4F7", "#20232C"),
    "race_skis": ("skis", race(), "#101014", "#2A2A30", "#16161E", "#F7E03B"),
    "powder_skis": ("skis", powder(), "#0E1633", "#24305A", "#9CD3FF", "#1D2E63"),
    "park_skis": ("skis", park(), "#14121C", "#2D2340", "#21C7B4", "#FF5DA2"),
    "snurfer": ("board", snurfer(), "#5A3A22", "#6E4A2C", "#C63A2C", "#E8D9B0"),
    "all_mountain_board": ("board", all_mountain_board(), "#13284A", "#1E3A66", "#2B2F3A", "#FF7A2E"),
    "powder_board": ("board", powder_board(), "#0F2E29", "#174A41", "#24302C", "#3FB59A"),
    "park_board": ("board", park_board(), "#17141F", "#2C2338", "#F2F2F2", "#FF3E8A"),
    "carving_skis": ("skis", carving(), "#141418", "#2B2B33", "#F6F6F6", "#D9261C"),
    "freeride_skis": ("skis", freeride(), "#1E2412", "#3A4424", "#C9B98A", "#323A1E"),
    "touring_skis": ("skis", touring(), "#1B2026", "#9AA3AD", "#16A6A0", "#E0412F"),
    "retro_skis": ("skis", retro(), "#141218", "#3A1F3A", "#FFE23F", "#FF3FA4"),
    "freeride_board": ("board", freeride_board(), "#2A1418", "#4A1E24", "#1C1C20", "#F3E6C8"),
    "carving_board": ("board", carving_board(), "#14204A", "#2E3C66", "#E6EAF0", "#2E6BFF"),
    "splitboard": ("board", splitboard(), "#1A2A1E", "#2F4A35", "#D9DEE3", "#4F8A4C"),
    "retro_board": ("board", retro_board(), "#151217", "#3A2350", "#18B5A5", "#FFD23F"),
}

STEEL = hexc("#C9D1DC")


def ski_texture(top, base, side, bind, accent):
    img = canvas(64, 64)
    base_c, side_c, bind_c, acc_c = hexc(base), hexc(side), hexc(bind), hexc(accent)

    def body(face, s, t, x, y):
        if face == "top":
            return top(t * 0.92 + 0.04, s, x, y)
        if face == "bottom":
            stripe = 0.35 < s < 0.65 and int(t * 20) % 5 == 0
            return shade(base_c, 0.25) if stripe else base_c
        if face in ("left", "right"):
            return STEEL
        return side_c
    paint_box(img, 0, 0, 2, 1, 23, body)

    def tip(face, s, t, x, y):
        if face == "top":
            return top(0.97, s, x, y)
        if face == "bottom":
            return base_c
        return STEEL if face in ("left", "right") else side_c
    paint_box(img, 0, 40, 2, 1, 3.5, tip)

    def tail(face, s, t, x, y):
        if face == "top":
            return top(0.02, s, x, y)
        if face == "bottom":
            return base_c
        return STEEL if face in ("left", "right") else side_c
    paint_box(img, 12, 40, 2, 1, 2, tail)

    def binding(face, s, t, x, y):
        if face == "top":
            return acc_c if 0.3 < t < 0.55 else bind_c
        return shade(bind_c, -0.25)
    paint_box(img, 0, 30, 2.6, 1.2, 6, binding)

    def heel(face, s, t, x, y):
        return shade(bind_c, -0.1) if face != "top" else acc_c
    paint_box(img, 20, 30, 2.2, 2, 1.5, heel)
    return img


def board_texture(top, base, side, bind, accent):
    img = canvas(64, 64)
    base_c, side_c, bind_c, acc_c = hexc(base), hexc(side), hexc(bind), hexc(accent)

    def deck(face, s, t, x, y):
        if face == "top":
            return top(t * 0.9 + 0.05, s, x, y)
        if face == "bottom":
            logo = 0.3 < t < 0.7 and 0.25 < s < 0.75 and (int(t * 30) + int(s * 6)) % 3 == 0
            return acc_c if logo else base_c
        if face in ("left", "right"):
            return STEEL if int(t * 10) % 2 == 0 else shade(STEEL, -0.1)
        return side_c
    paint_box(img, 0, 0, 7, 1, 21, deck)

    def end(along):
        def f(face, s, t, x, y):
            if face == "top":
                return top(along, s, x, y)
            if face == "bottom":
                return base_c
            return STEEL if face in ("left", "right") else side_c
        return f
    paint_box(img, 0, 23, 7, 1, 3, end(0.98))
    paint_box(img, 20, 23, 7, 1, 3, end(0.02))

    def binding_base(face, s, t, x, y):
        return bind_c if face == "top" else shade(bind_c, -0.3)
    paint_box(img, 0, 30, 5.2, 0.8, 3.2, binding_base)

    def highback(face, s, t, x, y):
        return acc_c if face in ("right", "left") and 0.3 < t < 0.6 else shade(bind_c, -0.15)
    paint_box(img, 20, 30, 0.8, 2.8, 2.8, highback)

    def strap(face, s, t, x, y):
        return acc_c if face == "top" else shade(acc_c, -0.3)
    paint_box(img, 0, 36, 4, 1, 2.4, strap)
    return img


# --------------------------------------------------------------------------------------------- icons

def ski_icon(top, bind):
    """Two skis lying side by side on the diagonal, tips at the top right, a gap between them."""
    img = canvas(16, 16)
    bind_c = hexc(bind)
    for band, (dmin, dmax) in ((12, (-11, 10)), (17, (-9, 12))):
        for y in range(16):
            for x in range(16):
                s = x + y
                d = x - y
                if s not in (band, band + 1) or not (dmin <= d <= dmax):
                    continue
                along = (d - dmin) / (dmax - dmin)
                # pointed tip and tail: only the upper half of the band at the very ends
                if (d >= dmax - 0 or d <= dmin) and s == band + 1:
                    continue
                across = 0.25 if s == band else 0.75
                c = top(along * 0.94 + 0.03, across, x, y)
                if 0.44 < along < 0.58:
                    c = bind_c if s == band else shade(bind_c, -0.35)
                put(img, x, y, shade(c, 0.16 if s == band else -0.1))
    return outline_inside(img, 0.08)


def board_icon(top, bind):
    img = canvas(16, 16)
    bind_c = hexc(bind)
    path = [(2.6, 13.4), (13.4, 2.6)]
    for x, y, along, across in thick_line(path, 4.6):
        # round the nose and tail
        endness = min(along, 1 - along) * 15.2
        if endness < 0.9 and abs(across) > 0.55:
            continue
        c = top(along, (across + 1) / 2, x, y)
        if abs(along - 0.33) < 0.06 or abs(along - 0.67) < 0.06:
            c = bind_c if abs(across) < 0.75 else shade(bind_c, -0.3)
        light = 0.18 if across < -0.3 else (-0.15 if across > 0.5 else 0)
        put(img, x, y, shade(c, light))
    return outline_inside(img, 0.5)


def main(preview_dir: str | None):
    sheet = []
    for name, (kind, top, base, side, bind, accent) in DESIGNS.items():
        if kind == "skis":
            tex = ski_texture(top, base, side, bind, accent)
            icon = ski_icon(top, bind)
        else:
            tex = board_texture(top, base, side, bind, accent)
            icon = board_icon(top, bind)
        save(tex, f"textures/entity/rig/{name}.png")
        save(icon, f"textures/item/{name}.png")
        sheet.append((name + "_icon", icon))
        sheet.append((name, tex))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        icons = [s for s in sheet if s[0].endswith("_icon")]
        texes = [s for s in sheet if not s[0].endswith("_icon")]
        contact_sheet(icons, factor=10, cols=5).save(os.path.join(preview_dir, "rig_icons.png"))
        contact_sheet(texes, factor=4, cols=5).save(os.path.join(preview_dir, "rig_textures.png"))
    print("rigs: wrote", len(DESIGNS), "designs")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
