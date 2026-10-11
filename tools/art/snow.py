"""Snow textures: fresh powder block and the snow particles.

Run: uv run --with pillow --with numpy python tools/art/snow.py [preview_dir]
"""
from __future__ import annotations

import math
import os
import sys

from pix import canvas, contact_sheet, hexc, mix, noise2, put, save, shade


def fresh_powder():
    """Soft and bright: a whisper of blue shading in broad patches and a few glints, so it reads as fluffier than snow."""
    img = canvas(16, 16)
    white = hexc("#FCFDFF")
    shadow = hexc("#E6EEF9")
    for y in range(16):
        for x in range(16):
            # broad, tileable soft patches (wrap the coarse noise grid at 4)
            n = (noise2((x // 4) % 4, (y // 4) % 4, 70) * 0.7 + noise2((x // 2) % 8, (y // 2) % 8, 71) * 0.3)
            c = mix(white, shadow, max(0.0, n - 0.45) * 1.6)
            put(img, x, y, c)
    for (gx, gy) in ((3, 4), (11, 9), (7, 13), (14, 2)):
        put(img, gx, gy, (255, 255, 255, 255))
    for (gx, gy) in ((4, 4), (12, 9)):
        put(img, gx, gy, hexc("#EAF6FF"))
    return img


def corduroy():
    """Fresh corduroy: fine ridges left by the groomer's tiller, lit on one side and shadowed on the other."""
    img = canvas(16, 16)
    white, lit, groove = hexc("#FBFCFE"), hexc("#FFFFFF"), hexc("#DCE5F1")
    for y in range(16):
        for x in range(16):
            k = x % 4
            c = groove if k == 0 else lit if k == 1 else white
            if noise2(x, y, 83) > 0.9:
                c = mix(c, groove, 0.35)
            put(img, x, y, c)
    return img


def spray(i):
    """Soft snow puffs, four sizes."""
    img = canvas(8, 8)
    r = 1.2 + i * 0.8
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if d <= r:
                a = int(255 * min(1.0, (r - d) / 1.2 + 0.2))
                c = mix(hexc("#FFFFFF"), hexc("#DDE9F7"), d / (r + 0.01))
                put(img, x, y, (c[0], c[1], c[2], a))
    return img


def wind_snow(i):
    """A tiny streak of blowing snow."""
    img = canvas(8, 8)
    length = 2 + i
    for k in range(length):
        a = int(255 * (1 - k / (length + 1)))
        put(img, 3 + k - length // 2, 4, (255, 255, 255, a))
    put(img, 3, 3, (255, 255, 255, 120))
    return img


def sparkle(i):
    """Four-point star glint at a few sizes."""
    img = canvas(8, 8)
    arm = [1, 2, 3, 2][i]
    core = (255, 255, 255, 255)
    put(img, 3, 3, core)
    put(img, 4, 3, core)
    put(img, 3, 4, core)
    put(img, 4, 4, core)
    for k in range(1, arm + 1):
        a = int(255 * (1 - k / (arm + 1)))
        for (dx, dy) in ((0, -k), (0, k + 1), (-k, 0), (k + 1, 0)):
            put(img, 3 + dx, 3 + dy, (210, 240, 255, a))
    return img


def main(preview_dir: str | None):
    out = {"block/fresh_powder": fresh_powder(), "block/groomed_snow": corduroy()}
    for i in range(4):
        out[f"particle/spray_{i}"] = spray(i)
        out[f"particle/sparkle_{i}"] = sparkle(i)
    for i in range(3):
        out[f"particle/wind_snow_{i}"] = wind_snow(i)
    for name, img in out.items():
        save(img, f"textures/{name}.png")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        contact_sheet(list(out.items()), factor=10, cols=6).save(os.path.join(preview_dir, "snow.png"))
    print("snow: wrote", len(out), "textures")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
