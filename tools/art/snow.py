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


def snowy_road():
    """A service road under a skin of packed snow: gravel showing through in patches, clearest in the two tyre tracks.
    The tracks run along the texture's y (north-south); the blockstate turns it for east-west roads."""
    img = canvas(16, 16)
    snow, snow_d = hexc("#E9ECEF"), hexc("#D5DADF")
    gravels = (hexc("#8E8679"), hexc("#6F685F"), hexc("#A59C8F"), hexc("#7E776C"))
    for y in range(16):
        for x in range(16):
            track = 3 <= x <= 5 or 10 <= x <= 12
            # broad soft patches of snow (coarse noise, tiling every 16) over the gravel, thinner in the tracks
            n = noise2((x // 2) % 8, (y // 2) % 8, 95) * 0.65 + noise2(x, y, 96) * 0.35
            cover = n > (0.62 if track else 0.38)
            g = gravels[int(noise2(x, y, 92) * 4) % 4]
            if cover:
                c = snow_d if noise2(x, y, 97) > 0.7 else snow
            else:
                c = shade(g, -0.08) if track and y % 4 == 0 else g
            put(img, x, y, c)
    return img


def snowy_road_side():
    """The edge of the road: gravel with snow pressed into it."""
    img = canvas(16, 16)
    snow = hexc("#E3E7EB")
    gravels = (hexc("#8E8679"), hexc("#6F685F"), hexc("#A59C8F"), hexc("#7E776C"))
    for y in range(16):
        for x in range(16):
            c = gravels[int(noise2(x, y, 93) * 4) % 4]
            if noise2(x, y, 94) > 0.55:
                c = mix(c, snow, 0.75)
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
    out = {"block/fresh_powder": fresh_powder(), "block/groomed_snow": corduroy(), "block/snowy_road": snowy_road(),
           "block/snowy_road_side": snowy_road_side()}
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
