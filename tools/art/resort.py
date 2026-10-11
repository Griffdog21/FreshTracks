"""Resort furniture textures: LOAD HERE / WAIT HERE mats, rope-line posts, orange closure fencing, resort signs and
trail-sign boards (difficulty symbol on the left; the run name is drawn by TrailSignRenderer on the right).

Mats and sign faces are 64x64 so the words stay readable (the font below is 5x7, drawn at 1x or 2x).
Sign boards use the top 64x40 of the image: the model maps the board face to UV [0, 0, 16, 10].
Run: uv run --with pillow --with numpy python tools/art/resort.py [preview_dir]
"""
from __future__ import annotations

import math
import os
import sys

from pix import canvas, contact_sheet, hexc, mix, noise2, put, rect, save, shade

FONT = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": [".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "J": ["..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "Q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."],
    "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    " ": [".....", ".....", ".....", ".....", ".....", ".....", "....."],
}

WHITE = hexc("#F6F7F8")
BLACK = hexc("#17181B")


# narrow versions of the letters whose stroke runs down the middle column (dropping it would erase them)
CONDENSED = {
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    "T": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", ".#."],
    "Y": ["#.#", "#.#", "#.#", ".#.", ".#.", ".#.", ".#."],
}


def glyph(ch: str, condensed: bool) -> list[str]:
    rows = FONT[ch]
    if condensed and ch in CONDENSED:
        return CONDENSED[ch]
    if condensed and ch not in "MWNQ ":
        rows = [r[:2] + r[3:] for r in rows]  # drop the middle column
    if condensed and ch == " ":
        rows = [r[:3] for r in rows]
    return rows


def text_width(text: str, scale: int, condensed: bool, gap: int) -> int:
    widths = [len(glyph(c, condensed)[0]) * scale for c in text]
    return sum(widths) + gap * (len(text) - 1)


def draw_text(img, text: str, cx: float, y: int, color, scale: int = 1, condensed: bool = False, gap: int | None = None,
              tall: bool = False) -> None:
    """tall: 1 wide x 2 high pixels per font dot (a narrow sign font)."""
    sx = 1 if tall else scale
    sy = 2 if tall else scale
    gap = sx if gap is None else gap
    x = int(round(cx - text_width(text, sx, condensed, gap) / 2))
    for ch in text:
        rows = glyph(ch, condensed)
        for gy, row in enumerate(rows):
            for gx, bit in enumerate(row):
                if bit == "#":
                    rect(img, x + gx * sx, y + gy * sy, x + (gx + 1) * sx, y + (gy + 1) * sy, color)
        x += len(rows[0]) * sx + gap


def rubber(base, x, y, seed):
    n = noise2(x // 2, y // 2, seed)
    return shade(base, -0.08) if n > 0.82 else (shade(base, 0.05) if n < 0.12 else base)


# ------------------------------------------------------------------------------------------------ mats

def mat(word1: str, word2: str, base) -> object:
    img = canvas(64, 64)
    rect(img, 0, 0, 64, 64, lambda x, y: rubber(base, x, y, 5))
    # white painted border
    for i in range(64):
        for t in (2, 3, 60, 61):
            put(img, i, t, WHITE)
            put(img, t, i, WHITE)
    draw_text(img, word1, 32, 15, WHITE, scale=2)
    draw_text(img, word2, 32, 35, WHITE, scale=2)
    return img


def mat_side(base) -> object:
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, lambda x, y: rubber(shade(base, -0.35), x, y, 9))
    return img


# ------------------------------------------------------------------------------------------- rope line

def bamboo() -> object:
    img = canvas(16, 16)
    cane = hexc("#D9C27A")
    for y in range(16):
        for x in range(16):
            c = cane if (x % 4) != 0 else shade(cane, -0.12)
            if y % 6 == 0:
                c = shade(cane, -0.3)  # node
            elif y % 6 == 1:
                c = shade(cane, 0.15)
            put(img, x, y, c)
    return img


def rope() -> object:
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            put(img, x, y, hexc("#2A5CC8") if (x + y) % 4 < 2 else WHITE)
    return img


# ------------------------------------------------------------------------------------------- closure fence

def closure_mesh() -> object:
    """Orange plastic snow fence: rows of oblong holes, offset like bricks, with a solid strip top and bottom."""
    img = canvas(16, 16)
    orange = hexc("#F2701D")
    for y in range(16):
        for x in range(16):
            band = y // 3
            offset = (band % 2) * 2
            hole = (y % 3 != 2) and ((x + offset) % 4 != 3)
            if y in (0, 15) or not hole:
                put(img, x, y, orange if noise2(x, y, 2) > 0.15 else shade(orange, -0.15))
    return img


def closure_edge() -> object:
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, hexc("#D95F14"))
    return img


# ------------------------------------------------------------------------------------------- signs

def board(base, border) -> object:
    img = canvas(64, 64)
    rect(img, 0, 0, 64, 40, base)
    for x in range(64):
        for t in (0, 1, 38, 39):
            put(img, x, t, border)
    for y in range(40):
        for t in (0, 1, 62, 63):
            put(img, t, y, border)
    return img


def slow_zone() -> object:
    img = board(hexc("#F7C51E"), BLACK)
    draw_text(img, "SLOW", 32, 5, BLACK, scale=2)
    draw_text(img, "ZONE", 32, 22, BLACK, scale=2)
    return img


def closed() -> object:
    img = board(hexc("#E8541D"), WHITE)
    draw_text(img, "RUN", 32, 5, WHITE, scale=2)
    draw_text(img, "CLOSED", 32, 22, WHITE, scale=2, condensed=True, gap=2)
    return img


def experts_only() -> object:
    img = board(BLACK, hexc("#F2C230"))
    # two black diamonds outlined in white
    for cx in (24, 40):
        for y in range(3, 17):
            for x in range(cx - 8, cx + 9):
                d = abs(x - cx) + abs(y - 10)
                if d <= 6:
                    put(img, x, y, WHITE if d >= 5 else BLACK)
    draw_text(img, "EXPERTS", 32, 19, WHITE)
    draw_text(img, "ONLY", 32, 29, WHITE)
    return img


def singles() -> object:
    img = board(hexc("#1F5FD1"), WHITE)
    draw_text(img, "SINGLES", 32, 5, WHITE, tall=True)
    draw_text(img, "LINE", 32, 22, WHITE, scale=2)
    return img


def boundary() -> object:
    """US ski area boundary sign: orange with black lettering."""
    img = board(hexc("#F07A22"), BLACK)
    draw_text(img, "SKI AREA", 32, 6, BLACK, condensed=True)
    draw_text(img, "BOUNDARY", 32, 16, BLACK, condensed=True)
    draw_text(img, "NO RETURN", 32, 27, BLACK, condensed=True)
    return img


def cliffs() -> object:
    img = board(hexc("#F7C51E"), BLACK)
    draw_text(img, "CAUTION", 32, 6, BLACK, condensed=True)
    draw_text(img, "CLIFFS", 32, 20, BLACK, scale=2, condensed=True, gap=2)
    return img


def hazard_pole() -> object:
    """A bamboo hazard pole: orange and black bands, with an orange disc near the top (drawn on the upper half)."""
    img = canvas(16, 16)
    orange, black = hexc("#F07A22"), hexc("#1F1F22")
    for y in range(16):
        for x in range(16):
            put(img, x, y, orange if (y // 3) % 2 == 0 else black)
    return img


def tower_pad() -> object:
    """Lift tower padding: quilted blue vinyl with a white band."""
    img = canvas(16, 16)
    blue, blue_d = hexc("#1F5FD1"), hexc("#16449A")
    for y in range(16):
        for x in range(16):
            c = blue_d if (x % 8 == 0 or y % 8 == 0) else blue
            if 6 <= y <= 9:
                c = WHITE if y in (7, 8) else shade(WHITE, -0.15)
            put(img, x, y, c)
    return img


def tower_pad_top() -> object:
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, hexc("#16449A"))
    return img


def snow_gun_body() -> object:
    """Fan gun shell: white with a red band and a couple of bolts."""
    img = canvas(16, 16)
    red = hexc("#D23A2A")
    for y in range(16):
        for x in range(16):
            c = WHITE if not 6 <= y <= 9 else red
            if (x in (2, 13)) and y in (2, 13):
                c = hexc("#8A929C")
            if noise2(x, y, 17) > 0.92:
                c = shade(c, -0.08)
            put(img, x, y, c)
    return img


def snow_gun_front() -> object:
    """The business end: a dark grille with a ring of nozzles round the rim."""
    img = canvas(16, 16)
    dark, grille, nozzle = hexc("#2A2D33"), hexc("#3E434B"), hexc("#C9CED5")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            c = WHITE if d > 7.4 else (grille if (x + y) % 3 == 0 or (x - y) % 3 == 0 else dark)
            if 6.0 < d <= 7.4 and int(math.degrees(math.atan2(y - 7.5, x - 7.5)) // 30) % 2 == 0:
                c = nozzle
            put(img, x, y, c)
    return img


def snow_gun_back() -> object:
    """The fan behind its guard: blades in a dark housing."""
    img = canvas(16, 16)
    dark, blade = hexc("#2A2D33"), hexc("#6B727C")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            ang = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 90
            c = WHITE if d > 7.4 else (blade if ang < 34 and d > 1.5 else dark)
            put(img, x, y, c)
    return img


def metal_plate() -> object:
    img = canvas(16, 16)
    base = hexc("#4A4F57")
    for y in range(16):
        for x in range(16):
            put(img, x, y, shade(base, -0.1) if noise2(x, y, 29) > 0.85 else base)
    return img


def steel_pole() -> object:
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = hexc("#A9B0B8") if x % 4 else hexc("#8A929C")
            if y % 8 == 0:
                c = hexc("#6B727C")
            put(img, x, y, c)
    return img


def sign_back() -> object:
    img = canvas(16, 16)
    metal = hexc("#9AA3AD")
    rect(img, 0, 0, 16, 16, lambda x, y: shade(metal, -0.1) if noise2(x, y, 3) > 0.85 else metal)
    return img


# ------------------------------------------------------------------------------------------- trail signs

WOOD = hexc("#5A3A24")
WOOD_L = hexc("#6E4A30")


def trail_board(symbol: str) -> object:
    img = canvas(64, 64)
    for y in range(40):
        for x in range(64):
            grain = (y + int(3 * math.sin(x * 0.35 + y * 0.1))) % 5 == 0
            put(img, x, y, WOOD_L if grain else WOOD)
    for x in range(64):
        for t in (0, 39):
            put(img, x, t, hexc("#3D2616"))
    for y in range(40):
        for t in (0, 63):
            put(img, t, y, hexc("#3D2616"))
    # white plate behind the symbol, left end of the board (x 3..23)
    for y in range(9, 31):
        for x in range(2, 24):
            corner = min(x - 2, 23 - x, y - 9, 30 - y)
            if corner >= 0 and not (corner == 0 and (x in (2, 23) and y in (9, 30))):
                put(img, x, y, WHITE)
    cx, cy = 12.5, 19.5
    for y in range(9, 31):
        for x in range(2, 24):
            px, py = x + 0.5, y + 0.5
            color = None
            if symbol == "green" and math.hypot(px - cx, py - cy) <= 8:
                color = hexc("#2E9E44")
            elif symbol == "blue" and abs(px - cx) <= 7.5 and abs(py - cy) <= 7.5:
                color = hexc("#1F5FD1")
            elif symbol == "black" and abs(px - cx) + abs(py - cy) <= 9.5:
                color = BLACK
            elif symbol == "double_black" and (abs(px - 7.5) + abs(py - cy) <= 5.4 or abs(px - 17.5) + abs(py - cy) <= 5.4):
                color = BLACK
            elif symbol == "park" and abs(px - cx) <= 8.5 and abs(py - cy) <= 6:
                color = hexc("#F28C28")
            elif symbol == "closed" and abs(px - cx) <= 8.5 and abs(py - cy) <= 8.5:
                color = hexc("#C62828")
                if abs(py - cy) <= 1.6 and abs(px - cx) <= 6:
                    color = WHITE
            elif symbol == "extreme":
                color = hexc("#F07A22")
                if abs(px - 7.5) + abs(py - cy) <= 5.4 or abs(px - 17.5) + abs(py - cy) <= 5.4:
                    color = BLACK
            elif symbol == "lift":
                color = hexc("#1F5FD1")
                cable = 12 + (px - 2) * 0.12
                if abs(py - cable) < 0.8 or (abs(px - cx) < 0.8 and cable < py < 21) or (abs(py - 21.5) < 1 and abs(px - cx) < 5)                         or (abs(px - (cx - 4.5)) < 0.8 and 18 < py < 22) or (abs(px - (cx + 4.5)) < 0.8 and 21 < py < 25):
                    color = WHITE
            if color:
                put(img, x, y, color)
    return img


def main(preview_dir: str | None):
    out = {
        "block/load_here": mat("LOAD", "HERE", hexc("#D9481F")),
        "block/wait_here": mat("WAIT", "HERE", hexc("#2B63CF")),
        "block/load_here_side": mat_side(hexc("#D9481F")),
        "block/wait_here_side": mat_side(hexc("#2B63CF")),
        "block/rope_line_post": bamboo(),
        "block/rope_line_rope": rope(),
        "block/closure_fence": closure_mesh(),
        "block/closure_fence_edge": closure_edge(),
        "block/slow_zone_sign": slow_zone(),
        "block/closed_sign": closed(),
        "block/experts_only_sign": experts_only(),
        "block/singles_sign": singles(),
        "block/resort_sign_back": sign_back(),
    }
    for symbol in ("green", "blue", "black", "double_black", "park", "closed", "extreme", "lift"):
        out[f"block/trail_sign_{symbol}"] = trail_board(symbol)
    out["block/boundary_sign"] = boundary()
    out["block/cliff_sign"] = cliffs()
    out["block/hazard_pole"] = hazard_pole()
    out["block/tower_pad"] = tower_pad()
    out["block/tower_pad_top"] = tower_pad_top()
    out["block/snow_gun_body"] = snow_gun_body()
    out["block/snow_gun_front"] = snow_gun_front()
    out["block/snow_gun_back"] = snow_gun_back()
    out["block/snow_gun_base"] = metal_plate()
    out["block/snow_gun_pole"] = steel_pole()
    for name, img in out.items():
        save(img, f"textures/{name}.png")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet([(k, v) for k, v in out.items() if v.shape[0] > 16], factor=3, cols=5).save(os.path.join(preview_dir, "resort_signs.png"))
        contact_sheet([(k, v) for k, v in out.items() if v.shape[0] <= 16], factor=10, cols=6).save(os.path.join(preview_dir, "resort_small.png"))
    print("resort: wrote", len(out), "textures")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
