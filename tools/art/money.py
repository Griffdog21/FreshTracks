"""Ski Bucks notes, the Golden Ticket and collector pins (16x16 item icons).

  uv run --with pillow --with numpy python tools/art/money.py
"""
from __future__ import annotations

import os

from pix import ASSETS, canvas, hexc, put, rect, save, shade

OUT = os.path.join(ASSETS, "textures", "item")


def note(paper: str, ink: str, mark: str, stripe: str | None = None):
    """A folded banknote seen slightly from above: paper, a darker border, a round seal and a corner value mark."""
    img = canvas(16, 16)
    p = hexc(paper)
    k = hexc(ink)
    # the note sits at a slant: rows 4..12, a little offset each row for a fanned stack look
    for y in range(4, 13):
        off = (12 - y) // 4
        rect(img, 1 + off, y, 15 - (2 - off), y + 1, p)
    # border
    for y in range(4, 13):
        off = (12 - y) // 4
        put(img, 1 + off, y, k)
        put(img, 14 - (2 - off), y, k)
    rect(img, 1 + 2, 4, 15 - 0, 5, k)
    rect(img, 1, 12, 13, 13, k)
    # a second note peeking out underneath
    rect(img, 2, 13, 14, 14, shade(p, -0.35))
    put(img, 1, 13, shade(k, -0.2))
    put(img, 14, 13, shade(k, -0.2))
    # seal in the middle
    cx, cy = 8, 8
    for dx, dy in [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1), (-1, -1), (1, 1), (1, -1), (-1, 1)]:
        put(img, cx + dx, cy + dy, shade(k, 0.15) if (dx, dy) != (0, 0) else hexc(mark))
    # value mark in two corners
    put(img, 3, 6, hexc(mark))
    put(img, 4, 6, hexc(mark))
    put(img, 11, 10, hexc(mark))
    put(img, 12, 10, hexc(mark))
    if stripe:
        for y in range(5, 12):
            put(img, 5, y, hexc(stripe))
    return img


def golden_ticket():
    img = canvas(16, 16)
    gold = hexc("F2C230")
    dark = hexc("A87412")
    light = hexc("FFE89A")
    rect(img, 1, 5, 15, 12, gold)
    rect(img, 1, 5, 15, 6, light)
    rect(img, 1, 11, 15, 12, dark)
    # notched ends like a lift ticket
    for y in (7, 8, 9):
        put(img, 1, y, (0, 0, 0, 0))
        put(img, 14, y, (0, 0, 0, 0))
    # perforation line
    for y in range(6, 11, 2):
        put(img, 11, y, dark)
    # a star
    for x, y in [(6, 7), (5, 8), (6, 8), (7, 8), (6, 9), (4, 8), (8, 8)]:
        put(img, x, y, hexc("FFFFFF"))
    return img


def pin(enamel: str, rim: str = "D9A520"):
    """A round enamel resort pin: gold rim, coloured enamel, a white mountain peak."""
    img = canvas(16, 16)
    r = hexc(rim)
    e = hexc(enamel)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.2:
                put(img, x, y, r)
            if d < 5.0:
                put(img, x, y, e)
    for x, y in [(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (5, 7), (6, 7), (9, 7), (10, 7), (4, 8), (5, 8), (10, 8), (11, 8)]:
        put(img, x, y, hexc("F4F7FB"))
    rect(img, 4, 9, 12, 11, shade(e, -0.3))
    put(img, 5, 4, shade(r, 0.5))
    return img


def main():
    save(note("CDE8C4", "2F6B3A", "1E4A27"), os.path.join(OUT, "ski_buck.png"))
    save(note("C9DDF2", "1F4E8C", "0F2E5C", "D8B04A"), os.path.join(OUT, "ski_buck_hundred.png"))
    save(note("2A2A30", "C9A23A", "F2D16B", "8C1F2A"), os.path.join(OUT, "ski_buck_thousand.png"))
    save(golden_ticket(), os.path.join(OUT, "golden_ticket.png"))
    save(pin("1F5FC4"), os.path.join(OUT, "collector_pin.png"))
    print("money art written")


if __name__ == "__main__":
    main()
