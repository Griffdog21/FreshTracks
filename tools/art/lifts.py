"""Lift textures: chairs, gondola, terminal frame, bullwheel, tower head, rope, and the lift block textures.

Box-UV layouts mirror LiftModels.java. Run: uv run --with pillow --with numpy python tools/art/lifts.py [preview_dir]
"""
from __future__ import annotations

import os
import sys

from pix import (canvas, contact_sheet, hexc, mix, noise2, outline_inside, paint_box, put, rect, save, shade)

STEEL = hexc("#8A929C")
STEEL_D = hexc("#5E656E")
STEEL_L = hexc("#B9C1CB")
PAD = hexc("#2F6FD0")        # seat padding: a bright Steep-ish blue
PAD_D = hexc("#1E4A93")
PAD_L = hexc("#5A92E6")
BAR = hexc("#23262E")
ORANGE = hexc("#F28C28")
CONCRETE = hexc("#A7A39B")
CONCRETE_D = hexc("#857F76")
YELLOW = hexc("#F2C230")
ROOF = hexc("#C2463A")       # red roof, very alpine
ROOF_D = hexc("#93322A")
WOOD = hexc("#8B5A33")
GLASS = (170, 215, 255, 110)


def metal(face, s, t, x, y):
    c = STEEL
    if face == "top":
        c = STEEL_L
    elif face == "bottom":
        c = STEEL_D
    if noise2(x, y, 9) > 0.9:
        c = shade(c, -0.12)
    return c


def padded(face, s, t, x, y, pad=(PAD, PAD_D, PAD_L)):
    if face == "top":
        stripe = int(s * 8) % 2 == 0
        return pad[2] if stripe else pad[0]
    if face == "bottom":
        return BAR
    return pad[1]


# express chairs (six-packs, detachable quads): red padding so you can tell them apart from the old fixed grips
EXPRESS = (hexc("#C8322B"), hexc("#8E1F1B"), hexc("#E85A4C"))


def chair_texture(pad=(PAD, PAD_D, PAD_L)):
    img = canvas(128, 128)
    widest = 3.1 * 16
    paint_box(img, 0, 0, 3, 4, 6, metal)               # grip
    paint_box(img, 20, 0, 1.5, 1.5, 6, metal)          # arm
    paint_box(img, 40, 0, 1.5, 36, 1.5, metal)         # hanger (longest)
    paint_box(img, 0, 40, widest, 1.5, 9, lambda f, s, t, x, y: padded(f, s, t, x, y, pad))      # seat
    paint_box(img, 0, 52, widest, 1, 1, metal)         # frame
    paint_box(img, 0, 56, widest, 11, 1.2, lambda f, s, t, x, y: padded("top" if f in ("front", "back") else f, s, t, x, y, pad))
    paint_box(img, 0, 70, widest, 1, 1, lambda f, s, t, x, y: BAR)          # bar crossbar
    paint_box(img, 0, 74, 1, 1, 10, lambda f, s, t, x, y: BAR)              # bar arms
    paint_box(img, 24, 74, 1, 13, 1, lambda f, s, t, x, y: BAR)             # bar drop
    paint_box(img, 0, 86, widest - 4, 1, 2.5, lambda f, s, t, x, y: ORANGE if int(s * 10) % 2 == 0 else BAR)  # footrest
    return img


# --------------------------------------------------------------------------------------------- gondola
# A modern 8-seat-style cabin. Each part's box-UV spot is packed here and mirrored in LiftModels.gondola();
# sizes are the texture footprints (rounded up). The glass panes live in their own translucent texture.

CHARCOAL = hexc("#2B2F36")
CHARCOAL_L = hexc("#3C424B")
CABIN_WHITE = hexc("#F2F4F6")
CABIN_RED = hexc("#D8392B")
CEILING = hexc("#F4F6F8")
RUBBER = hexc("#33373D")


def _pack(parts, width):
    """Shelf-packs box-UV footprints; returns {name: (u, v)}."""
    out, x, y, row = {}, 0, 0, 0
    for name, (w, h, d), _ in parts:
        fw, fh = 2 * (d + w) + 1, d + h + 1
        if x + fw > width:
            x, y, row = 0, y + row, 0
        out[name] = (x, y)
        x += fw
        row = max(row, fh)
    return out, y + row


def _roof(face, s, t, x, y):
    if face == "top":
        return ROOF if int(s * 18) % 3 else ROOF_D  # standing seams
    if face == "bottom":
        return STEEL_D
    return CABIN_WHITE if t < 0.5 else ROOF_D


def _ceiling(face, s, t, x, y):
    if face == "bottom":  # what riders see overhead: light panels with seams and two vents
        if (x % 9 == 0) or (y % 8 == 0):
            return hexc("#D5DAE0")
        if 0.1 < s < 0.22 and 0.3 < t < 0.7 and x % 2 == 0:
            return STEEL_D
        if 0.78 < s < 0.9 and 0.3 < t < 0.7 and x % 2 == 0:
            return STEEL_D
        return CEILING
    if face == "top":
        return ROOF
    return CABIN_WHITE


def _lower_wall(face, s, t, x, y):
    if face in ("top", "bottom"):
        return CHARCOAL
    if t < 0.12:
        return CHARCOAL  # window sill
    if 0.62 < t < 0.8:
        return CABIN_RED
    if t >= 0.92:
        return CHARCOAL_L
    return CABIN_WHITE


def _floor(face, s, t, x, y):
    if face == "top":
        return RUBBER if y % 3 else hexc("#272A2F")  # ribbed rubber mat
    return CHARCOAL if face != "bottom" else STEEL_D


def _cushion(face, s, t, x, y):
    if face == "top":
        return PAD_L if int(s * 14) % 2 == 0 else PAD
    return PAD_D


def _plate(face, s, t, x, y):
    # the cabin number: a white 7 on black
    if face == "front":
        px, py = int(s * 8), int(t * 5)
        if (py == 1 and 2 <= px <= 5) or (px == 5 - (py - 1) // 2 and py >= 2):
            return CABIN_WHITE
    return BAR


def _ski(colour):
    def paint(face, s, t, x, y):
        if t < 0.08:
            return shade(colour, 0.25)  # tip
        return colour if face != "back" else BAR
    return paint


def _light(face, s, t, x, y):
    return hexc("#FFFFFF") if face == "bottom" else hexc("#F1ECDA")


def _grey(face, s, t, x, y):
    return STEEL_D


def _pillar(face, s, t, x, y):
    return CHARCOAL if face not in ("top", "bottom") else CHARCOAL_L


GONDOLA_PARTS = [
    ("roof1", (36, 2, 32), _roof),
    ("ceiling", (34, 2, 30), _ceiling),
    ("floor", (34, 2, 30), _floor),
    ("belly", (30, 2, 26), lambda f, s, t, x, y: STEEL_D if f == "bottom" else CHARCOAL),
    ("roof2", (28, 2, 24), _roof),
    ("roof3", (18, 2, 16), _roof),
    ("wall_front", (30, 12, 1), _lower_wall),
    ("wall_side", (1, 12, 26), _lower_wall),
    ("bench_seat", (28, 2, 6), _cushion),
    ("bench_back", (28, 10, 1), _cushion),
    ("bench_base", (26, 6, 4), _grey),
    ("pillar", (2, 30, 2), _pillar),
    ("mullion", (1, 16, 2), _pillar),
    ("fairing", (6, 3, 12), metal),
    ("light", (12, 1, 5), _light),
    ("rack", (1, 1, 6), metal),
    ("ski_a", (1, 22, 2), _ski(ORANGE)),
    ("ski_b", (1, 22, 2), _ski(hexc("#2FA8E0"))),
    ("ski_c", (1, 22, 2), _ski(YELLOW)),
    ("plate", (8, 5, 1), _plate),
    ("hanger", (2, 8, 2), metal),
    ("grip", (3, 4, 6), metal),
]
GONDOLA_UV, _GONDOLA_H = _pack(GONDOLA_PARTS, 256)


def _recolor(painter, accent):
    """Wraps a painter so cabin red and roof red come out in another colour."""
    reds = {CABIN_RED: accent[0], ROOF: accent[0], ROOF_D: accent[1]}

    def paint(face, s, t, x, y):
        c = painter(face, s, t, x, y)
        return reds.get(c, c)
    return paint


def gondola_texture(accent=None):
    """accent: replaces the cabin red (the Sky Cab pulse cabins are blue)."""
    img = canvas(256, 256)
    for name, (w, h, d), painter in GONDOLA_PARTS:
        if accent is not None:
            painter = _recolor(painter, accent)
        u, v = GONDOLA_UV[name]
        paint_box(img, u, v, w, h, d, painter)
    return img


GLASS_PARTS = [
    ("glass_front", (30, 18, 1)),
    ("glass_side", (1, 18, 26)),
]
GLASS_UV, _GLASS_H = _pack([(n, s, None) for n, s in GLASS_PARTS], 128)


def gondola_glass_texture():
    img = canvas(128, 64)

    def glass(face, s, t, x, y):
        # faint blue tint with a soft diagonal reflection streak
        streak = abs(((s * 1.4 - t) % 0.7) - 0.2) < 0.05
        return (225, 240, 255, 105) if streak else (175, 210, 240, 60)
    for name, (w, h, d) in GLASS_PARTS:
        u, v = GLASS_UV[name]
        paint_box(img, u, v, w, h, d, glass)
    return img


def terminal_texture():
    img = canvas(256, 256)
    paint_box(img, 0, 0, 3, 70, 3, metal)        # posts (generous height)
    paint_box(img, 16, 0, 4, 70, 4, lambda f, s, t, x, y: STEEL_D)

    def roof(face, s, t, x, y):
        if face == "top":
            # standing-seam metal roof with snow on it
            snow = noise2(x // 2, y // 2, 3) > 0.55
            return hexc("#F3F7FB") if snow else (ROOF if int(s * 22) % 2 == 0 else ROOF_D)
        if face == "bottom":
            return WOOD
        return ROOF_D
    paint_box(img, 0, 40, 44, 3, 44, roof)

    def house(face, s, t, x, y):
        if face == "top":
            return hexc("#EEF3F8")
        if face == "bottom":
            return STEEL_D
        if 0.3 < t < 0.7 and 0.2 < s < 0.45:
            return hexc("#9CD3FF")  # a window
        return hexc("#3B4A5A") if int(t * 6) % 3 != 2 else hexc("#2E3A47")
    paint_box(img, 0, 140, 28, 12, 28, house)
    return img


def bullwheel_texture():
    img = canvas(64, 64)
    paint_box(img, 0, 0, 6, 4, 6, lambda f, s, t, x, y: YELLOW if f == "top" else shade(YELLOW, -0.3))
    paint_box(img, 0, 12, 14, 3, 3, lambda f, s, t, x, y: shade(YELLOW, -0.1) if f in ("top", "bottom") else BAR)
    paint_box(img, 0, 20, 1.5, 1.5, 32, lambda f, s, t, x, y: STEEL_D)
    return img


def tower_head_texture():
    img = canvas(128, 64)
    paint_box(img, 0, 0, 2 * 32 + 12, 3, 3, metal)
    paint_box(img, 0, 8, 3, 2, 18, lambda f, s, t, x, y: STEEL_D if f != "top" else STEEL)
    paint_box(img, 0, 30, 2, 3, 3, lambda f, s, t, x, y: BAR if f in ("left", "right") else YELLOW)
    return img


def spring_box_texture():
    """The T-bar / platter spring box: a yellow drum on a steel clamp."""
    img = canvas(32, 32)
    paint_box(img, 0, 0, 3, 4, 6, metal)

    def drum(face, s, t, x, y):
        if face in ("top", "bottom"):
            return STEEL_D
        if 0.4 < t < 0.55:
            return BAR
        return YELLOW if face in ("front", "back") else shade(YELLOW, -0.15)
    paint_box(img, 0, 12, 5, 9, 5, drum)
    return img


def flat_texture(color, dark):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            put(img, x, y, dark if noise2(x, y, 4) > 0.8 else color)
    return img


def rope_texture():
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            twist = (x + y) % 4 < 2
            put(img, x, y, hexc("#2A2A2E") if twist else hexc("#3D3D44"))
    return img


# ------------------------------------------------------------------------------------------- block textures

def terminal_block(side: bool):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = mix(CONCRETE, CONCRETE_D, noise2(x, y, 4) * 0.5)
            put(img, x, y, c)
    if side:
        # yellow and black safety stripes along the top edge of the pad
        for y in range(6, 9):
            for x in range(16):
                put(img, x, y, YELLOW if (x + y) % 6 < 3 else BAR)
    else:
        rect(img, 0, 0, 16, 1, YELLOW)
        rect(img, 0, 15, 16, 16, YELLOW)
        rect(img, 0, 0, 1, 16, YELLOW)
        rect(img, 15, 0, 16, 16, YELLOW)
        # a bolt pattern where the bullwheel post stands
        for (px, py) in ((5, 5), (10, 5), (5, 10), (10, 10)):
            put(img, px, py, STEEL_D)
    return img


def tower_block():
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            # lattice steel: diagonal braces
            brace = (x + y) % 8 in (0, 1) or (x - y) % 8 in (0, 1)
            edge = x in (0, 15)
            c = STEEL_L if edge else (STEEL if brace else STEEL_D)
            put(img, x, y, c)
    return img


def tower_head_block():
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            put(img, x, y, YELLOW if y < 3 else (STEEL if (x // 2 + y // 2) % 2 == 0 else STEEL_D))
    return img


def magic_carpet(side: bool):
    img = canvas(16, 16)
    rubber = hexc("#26272B")
    rubber_l = hexc("#34363C")
    for y in range(16):
        for x in range(16):
            put(img, x, y, rubber_l if (y % 4 == 0) else rubber)
    if side:
        rect(img, 0, 14, 16, 15, YELLOW)
        return img
    # yellow edge strips and chevrons pointing up (north = the way the belt runs)
    rect(img, 0, 0, 2, 16, YELLOW)
    rect(img, 14, 0, 16, 16, YELLOW)
    for base in (3, 10):
        for i in range(4):
            put(img, 7 - i, base + i, hexc("#E9EEF4"))
            put(img, 8 + i, base + i, hexc("#E9EEF4"))
    return img


def cable_icon():
    """A spool of haul rope."""
    img = canvas(16, 16)
    wood = hexc("#A06A3C")
    wood_d = hexc("#6E4526")
    rope = hexc("#3A3A40")
    rope_l = hexc("#6A6A74")
    rect(img, 2, 3, 4, 13, wood_d)
    rect(img, 12, 3, 14, 13, wood_d)
    for y in range(4, 12):
        for x in range(4, 12):
            put(img, x, y, rope_l if (x + y * 2) % 4 == 0 else rope)
    rect(img, 1, 2, 3, 14, wood)
    rect(img, 13, 2, 15, 14, wood)
    # loose end of the rope
    for i, (x, y) in enumerate(((11, 12), (12, 13), (13, 14), (14, 14))):
        put(img, x, y, rope)
    return outline_inside(img, 0.35)


def main(preview_dir: str | None):
    out = {
        "entity/lift/chair": chair_texture(),
        "entity/lift/chair_express": chair_texture(EXPRESS),
        "entity/lift/spring_box": spring_box_texture(),
        "entity/lift/tbar": flat_texture(hexc("#E8A21E"), hexc("#B97A12")),
        "entity/lift/platter": flat_texture(hexc("#2B2D33"), hexc("#1C1D21")),
        "entity/lift/gondola": gondola_texture(),
        "entity/lift/gondola_glass": gondola_glass_texture(),
        "entity/lift/gondola_pulse": gondola_texture((hexc("#2F6FD0"), hexc("#1E4A93"))),
        "entity/lift/terminal": terminal_texture(),
        "entity/lift/bullwheel": bullwheel_texture(),
        "entity/lift/tower_head": tower_head_texture(),
        "entity/lift/rope": rope_texture(),
        "block/lift_terminal_top": terminal_block(False),
        "block/lift_terminal_side": terminal_block(True),
        "block/lift_tower": tower_block(),
        "block/lift_tower_head": tower_head_block(),
        "item/lift_cable": cable_icon(),
        "block/magic_carpet": magic_carpet(False),
        "block/magic_carpet_side": magic_carpet(True),
    }
    for name, img in out.items():
        save(img, f"textures/{name}.png")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet([(k, v) for k, v in out.items() if v.shape[0] <= 16], factor=10, cols=6).save(os.path.join(preview_dir, "lift_blocks.png"))
        contact_sheet([(k, v) for k, v in out.items() if v.shape[0] > 16], factor=2, cols=3).save(os.path.join(preview_dir, "lift_entities.png"))
    print("lifts: wrote", len(out), "textures")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
