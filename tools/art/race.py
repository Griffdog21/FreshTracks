"""Map-building blocks: start/finish lines and trail difficulty markers.

The marker plate is drawn in the region x 1..15, y 0..12 of each 16x16 texture (the model maps the plate's face there).
Run: uv run --with pillow --with numpy python tools/art/race.py [preview_dir]
"""
from __future__ import annotations

import math
import os
import sys

from pix import canvas, contact_sheet, hexc, mix, put, rect, save, shade

WHITE = hexc("#F4F5F7")
EDGE = hexc("#9AA0A8")


def checker(a, b, size=4):
    img = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            put(img, x, y, a if ((x // size) + (y // size)) % 2 == 0 else b)
    return img


def plate():
    img = canvas(16, 16)
    rect(img, 1, 0, 15, 12, WHITE)
    for x in range(1, 15):
        put(img, x, 0, EDGE)
        put(img, x, 11, EDGE)
    for y in range(12):
        put(img, 1, y, EDGE)
        put(img, 14, y, EDGE)
    return img


def symbol(kind: str):
    img = plate()
    cx, cy = 8.0, 6.0
    if kind == "closed":
        rect(img, 2, 1, 14, 11, hexc("#C62828"))
        rect(img, 4, 5, 12, 7, WHITE)
        return img
    if kind == "extreme":
        # Aspen Snowmass "extreme terrain": a double black diamond on orange
        rect(img, 2, 1, 14, 11, hexc("#F07A22"))
        for y in range(1, 11):
            for x in range(2, 14):
                px, py = x + 0.5, y + 0.5
                if abs(px - 5) + abs(py - cy) <= 3.2 or abs(px - 11) + abs(py - cy) <= 3.2:
                    put(img, x, y, hexc("#1F1F22"))
        return img
    if kind == "lift":
        # a chairlift: the cable, a hanger and a chair, white on blue
        rect(img, 2, 1, 14, 11, hexc("#1F5FD1"))
        for x in range(2, 14):
            put(img, x, 2 + (x - 2) // 6, WHITE)
        for y in range(3, 8):
            put(img, 8, y, WHITE)
        for x in range(5, 11):
            put(img, x, 8, WHITE)
        put(img, 5, 7, WHITE)
        put(img, 10, 9, WHITE)
        return img
    for y in range(1, 11):
        for x in range(2, 14):
            px, py = x + 0.5, y + 0.5
            inside = False
            color = hexc("#1F1F22")
            if kind == "green":
                inside = math.hypot(px - cx, py - cy) <= 4.3
                color = hexc("#2E9E44")
            elif kind == "blue":
                inside = abs(px - cx) <= 4 and abs(py - cy) <= 4
                color = hexc("#1F5FD1")
            elif kind == "black":
                inside = abs(px - cx) + abs(py - cy) <= 5
            elif kind == "double_black":
                inside = abs(px - 5) + abs(py - cy) <= 3.2 or abs(px - 11) + abs(py - cy) <= 3.2
            elif kind == "park":
                inside = abs(px - cx) <= 5 and abs(py - cy) <= 3.5 and not (abs(px - cx) > 4 and abs(py - cy) > 2.5)
                color = hexc("#F07A22")
            if inside:
                put(img, x, y, color)
    if kind == "park":
        # a little white kicker shape
        for (x, y) in ((5, 8), (6, 8), (7, 8), (8, 7), (9, 6), (10, 5), (6, 7), (7, 7), (8, 6)):
            put(img, x, y, WHITE)
    return img


def back():
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, hexc("#D9DCE1"))
    for x in range(16):
        put(img, x, 0, EDGE)
        put(img, x, 15, EDGE)
    return img


def main(preview_dir: str | None):
    out = {
        "block/start_line": checker(hexc("#2E9E44"), WHITE),
        "block/finish_line": checker(hexc("#1F1F22"), WHITE),
        "block/trail_marker_back": back(),
    }
    for kind in ("green", "blue", "black", "double_black", "park", "closed", "extreme", "lift"):
        out[f"block/trail_marker_{kind}"] = symbol(kind)
    for name, img in out.items():
        save(img, f"textures/{name}.png")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet(list(out.items()), factor=8, cols=5).save(os.path.join(preview_dir, "race.png"))
    print("race: wrote", len(out), "textures")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
