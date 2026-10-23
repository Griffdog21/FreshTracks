"""Trail map board art for the main mod: the timber frame, the "you are here" dot, the item icon, and a plain resort
map shown on boards that have no map from the server (a painted mountain with runs and a lift, no real place).

A server with a real map (the Snowmass add-on) sends its own picture to players; see TrailMapPayload.
Run: uv run --with pillow --with numpy python tools/art/trail_map.py
"""
from __future__ import annotations

import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "freshtracks", "textures")
GREEN, BLUE, BLACK, RED = (34, 150, 64), (30, 90, 210), (20, 20, 22), (220, 40, 30)


def frame() -> Image.Image:
    """Rough-sawn timber for the board's frame and posts."""
    img = Image.new("RGB", (128, 128))
    px = img.load()
    for y in range(128):
        for x in range(128):
            grain = (x * 3 + int(4 * math.sin(y * 0.21 + x * 0.05))) % 9 == 0
            px[x, y] = (74, 48, 30) if grain else (96, 64, 40)
    return img


def dot() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = img.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if r <= 5.5:
                d[x, y] = (226, 32, 28, 255)
            elif r <= 7.5:
                d[x, y] = (255, 255, 255, 255)
    return img


def blank_map(size: int = 512) -> Image.Image:
    """A painted mountain, the way resort maps look: blue sky, snowy peaks, forest low down, coloured runs, a lift."""
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):  # sky
        t = y / size
        d.line([(0, y), (size, y)], fill=(int(120 + 80 * t), int(170 + 60 * t), int(235 + 15 * t)))
    s = size / 512
    peaks = [(0, 330), (60, 250), (130, 150), (205, 80), (260, 60), (320, 95), (390, 170), (450, 230), (512, 300), (512, 512), (0, 512)]
    d.polygon([(x * s, y * s) for x, y in peaks], fill=(236, 242, 250))
    # shading on the left faces
    for (x0, y0), (x1, y1) in zip(peaks[:4], peaks[1:5]):
        d.polygon([(x0 * s, y0 * s), (x1 * s, y1 * s), (x1 * s, (y1 + 120) * s), (x0 * s, (y0 + 140) * s)], fill=(205, 218, 235))
    # forest band low on the mountain, with gaps for the runs
    forest = Image.new("L", (size, size), 0)
    fd = ImageDraw.Draw(forest)
    for i in range(900):
        x = (i * 97 % 512) * s
        y = (300 + (i * 53 % 190)) * s
        r = (5 + i % 4) * s
        fd.polygon([(x, y - 2.4 * r), (x - r, y + r), (x + r, y + r)], fill=255)
    runs = {GREEN: [(250, 75), (160, 210), (110, 330), (150, 470)], BLUE: [(262, 70), (300, 200), (250, 330), (270, 470)],
            BLACK: [(272, 72), (380, 190), (420, 330), (390, 470)]}
    for pts in runs.values():
        fd.line([(x * s, y * s) for x, y in pts], fill=0, width=int(34 * s), joint="curve")
    tree = Image.new("RGB", (size, size), (46, 82, 60))
    img.paste(tree, (0, 0), forest.filter(ImageFilter.GaussianBlur(0.6)))
    for col, pts in runs.items():
        d.line([(x * s, y * s) for x, y in pts], fill=(255, 255, 255), width=int(9 * s), joint="curve")
        d.line([(x * s, y * s) for x, y in pts], fill=col, width=int(5 * s), joint="curve")
    # the lift and the lodge
    d.line([(200 * s, 470 * s), (300 * s, 105 * s)], fill=(30, 30, 30), width=int(5 * s))
    d.line([(200 * s, 470 * s), (300 * s, 105 * s)], fill=RED, width=int(3 * s))
    d.rectangle([180 * s, 466 * s, 236 * s, 492 * s], fill=(150, 100, 60))
    d.polygon([(174 * s, 468 * s), (208 * s, 446 * s), (242 * s, 468 * s)], fill=(196, 70, 58))
    try:
        title = ImageFont.truetype("arialbd.ttf", int(36 * s))
    except OSError:
        title = ImageFont.load_default()
    d.rectangle([12 * s, 12 * s, 236 * s, 64 * s], fill=(18, 40, 78))
    d.text((24 * s, 18 * s), "TRAIL MAP", fill=(255, 255, 255), font=title)
    return img


def icon(blank: Image.Image) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(1, 13):
        for x in range(1, 15):
            px[x, y] = (96, 64, 40, 255)
    img.paste(blank.resize((12, 10)), (2, 2))
    for y in range(13, 16):
        px[4, y] = (74, 48, 30, 255)
        px[11, y] = (74, 48, 30, 255)
    return img


def pocket_icon(blank: Image.Image) -> Image.Image:
    """The pocket trail map: a folded paper map, three panels, the middle one catching the light."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    sheet = blank.resize((14, 10))
    img.paste(sheet, (1, 3))
    px = img.load()
    for y in range(3, 13):
        for x in range(1, 15):
            r, g, b, a = px[x, y]
            panel = 0 if x < 6 else 1 if x < 11 else 2
            k = (0.82, 1.0, 0.9)[panel]
            px[x, y] = (int(r * k), int(g * k), int(b * k), 255)
    # the folds, a paper edge round it, and the red "you are here" dot
    for y in range(3, 13):
        px[6, y] = (150, 158, 170, 255)
        px[11, y] = (170, 176, 186, 255)
    for x in range(1, 15):
        px[x, 2] = (238, 240, 242, 255)
        px[x, 13] = (120, 126, 138, 255)
    for y in range(2, 14):
        px[0, y] = (120, 126, 138, 255)
        px[15, y] = (120, 126, 138, 255)
    px[8, 7] = (226, 32, 28, 255)
    px[9, 7] = (226, 32, 28, 255)
    px[8, 8] = (226, 32, 28, 255)
    px[9, 8] = (180, 24, 20, 255)
    return img


def main() -> None:
    os.makedirs(os.path.join(OUT, "entity"), exist_ok=True)
    frame().save(os.path.join(OUT, "entity", "trail_map_frame.png"))
    dot().save(os.path.join(OUT, "entity", "trail_map_dot.png"))
    blank = blank_map()
    blank.save(os.path.join(OUT, "entity", "trail_map_blank.png"))
    icon(blank).save(os.path.join(OUT, "item", "trail_map.png"))
    pocket_icon(blank).save(os.path.join(OUT, "item", "pocket_trail_map.png"))
    print("trail map art: frame, dot, blank map, icon")


if __name__ == "__main__":
    main()
