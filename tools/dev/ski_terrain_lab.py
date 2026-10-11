"""Offline lab for the Ski Mountains terrain (mirrors SkiTerrain.java's math with an equivalent 2D simplex noise), to
tune the shape quickly: slope mix by trail rating and fall-line run lengths, plus a shaded map.

  uv run --with numpy --with pillow python tools/dev/ski_terrain_lab.py [seed] [out.png]
"""
from __future__ import annotations

import math
import sys

import numpy as np
from PIL import Image

# Minecraft's SimplexNoise.get(x, y) dots with the 3D gradient table at z = 0, so these are those gradients' (x, y)
GRAD = np.array([[1, 1], [-1, 1], [1, -1], [-1, -1], [1, 0], [-1, 0], [1, 0], [-1, 0],
                 [0, 1], [0, -1], [0, 1], [0, -1]], dtype=np.float64)
F2 = 0.5 * (math.sqrt(3) - 1)
G2 = (3 - math.sqrt(3)) / 6


class Simplex:
    def __init__(self, rng: np.random.Generator):
        p = np.arange(256)
        rng.shuffle(p)
        self.perm = np.concatenate([p, p])
        self.xo = rng.random() * 256
        self.yo = rng.random() * 256

    def get(self, x, y):
        x = np.asarray(x, dtype=np.float64) + self.xo
        y = np.asarray(y, dtype=np.float64) + self.yo
        s = (x + y) * F2
        i = np.floor(x + s)
        j = np.floor(y + s)
        t = (i + j) * G2
        x0 = x - (i - t)
        y0 = y - (j - t)
        i1 = (x0 > y0).astype(np.int64)
        j1 = 1 - i1
        x1 = x0 - i1 + G2
        y1 = y0 - j1 + G2
        x2 = x0 - 1 + 2 * G2
        y2 = y0 - 1 + 2 * G2
        ii = (i.astype(np.int64)) & 255
        jj = (j.astype(np.int64)) & 255
        perm = self.perm
        gi0 = perm[ii + perm[jj]] % 12
        gi1 = perm[ii + i1 + perm[jj + j1]] % 12
        gi2 = perm[ii + 1 + perm[jj + 1]] % 12

        def corner(gi, cx, cy):
            tt = 0.5 - cx * cx - cy * cy
            tt = np.where(tt < 0, 0, tt)
            return tt ** 4 * (GRAD[gi, 0] * cx + GRAD[gi, 1] * cy)
        return 70.0 * (corner(gi0, x0, y0) + corner(gi1, x1, y1) + corner(gi2, x2, y2))


P = dict(
    valley=72.0, massif_relief=200.0, ridge_relief=230.0, max_height=440.0,
    warp=220.0, warp_l=1400.0, warp_fine=30.0, warp_fine_l=300.0,
    massif_l=4200.0, massif_fine_l=1700.0, massif_fine_w=0.25,
    lift_lo=0.30, lift_hi=0.85, power=1.5, cap=0.35,
    ridge_l=2600.0, ridge_side_l=900.0, ridge_pow=1.3, ridge_lift_pow=1.0,
)


def heightfield(seed: int, xs, zs, p=P):
    rng = np.random.default_rng(seed)
    n = {k: Simplex(rng) for k in ("wx", "wz", "wfx", "wfz", "massif", "massif_fine", "ridge", "ridge_side", "rolls", "bumps")}
    x, z = np.meshgrid(xs, zs, indexing="ij")
    wx = x + p["warp"] * n["wx"].get(x / p["warp_l"], z / p["warp_l"]) + p["warp_fine"] * n["wfx"].get(x / p["warp_fine_l"], z / p["warp_fine_l"])
    wz = z + p["warp"] * n["wz"].get(x / p["warp_l"], z / p["warp_l"]) + p["warp_fine"] * n["wfz"].get(x / p["warp_fine_l"], z / p["warp_fine_l"])
    m = 0.5 + 0.5 * ((1 - p["massif_fine_w"]) * n["massif"].get(wx / p["massif_l"], wz / p["massif_l"])
                     + p["massif_fine_w"] * n["massif_fine"].get(wx / p["massif_fine_l"], wz / p["massif_fine_l"]))
    raw = np.maximum((m - p["lift_lo"]) / (p["lift_hi"] - p["lift_lo"]), 0)
    # past the top of the ramp keep climbing, ever more slowly (no flat mesa tops)
    lift = np.where(raw < 1, raw, 1 + p.get("cap", 0.35) * np.tanh((raw - 1) / p.get("cap", 0.35)))
    h = p["valley"] + p["massif_relief"] * lift ** p["power"]
    r1 = 1 - np.abs(n["ridge"].get(wx / p["ridge_l"], wz / p["ridge_l"]))
    r2 = 1 - np.abs(n["ridge_side"].get(wx / p["ridge_side_l"], wz / p["ridge_side_l"]))
    ridge = 0.75 * r1 ** p["ridge_pow"] + 0.25 * r2 ** p["ridge_pow"]
    h = h + p["ridge_relief"] * lift ** p.get("ridge_lift_pow", 2.0) * ridge
    h = h + (2.5 + 4.5 * lift) * n["rolls"].get(x / 230.0, z / 230.0) + 7 * (1 - lift) * n["rolls"].get(x / 700.0 + 91.7, z / 700.0 - 13.1) \
        + 1.1 * n["bumps"].get(x / 55.0, z / 55.0)
    knee = p["max_height"] - 70
    return np.where(h < knee, h, knee + 70 * np.tanh((h - knee) / 70))


def analyse(h, step):
    gx = np.gradient(h, step, axis=0)
    gz = np.gradient(h, step, axis=1)
    deg = np.degrees(np.arctan(np.hypot(gx, gz)))
    bands = [np.mean(deg < 6), np.mean((deg >= 6) & (deg < 15)), np.mean((deg >= 15) & (deg < 25)), np.mean((deg >= 25) & (deg < 35)),
             np.mean((deg >= 35) & (deg < 45)), np.mean(deg >= 45)]
    # Fall lines from high ground, the way a skier picks a line: follow the big-picture fall line (the field blurred over
    # ~40 blocks, so small bumps don't trap it), carry on through short flats, stop when it stops dropping (less than
    # 4 blocks of drop over 80 blocks of travel) or cliffs out (over 42 degrees).
    n = h.shape[0]
    k = max(1, int(round(20 / step)))
    blur = h.copy()
    for axis in (0, 1):
        acc = np.zeros_like(blur)
        for o in range(-k, k + 1):
            acc += np.roll(blur, o, axis=axis)
        blur = acc / (2 * k + 1)
    bx = np.gradient(blur, step, axis=0)
    bz = np.gradient(blur, step, axis=1)
    hi = np.percentile(h, 85)
    runs = []
    drops = []
    rng = np.random.default_rng(1)
    for _ in range(600):
        i, j = rng.integers(k + 2, n - k - 2, size=2)
        if h[i, j] < hi:
            continue
        pi, pj = float(i), float(j)
        length = 0.0
        trail = [h[i, j]]
        start_h = h[i, j]
        end_len, end_h = 0.0, start_h
        for _k in range(6000):
            ii, jj = int(pi), int(pj)
            if not (k + 1 <= ii < n - k - 1 and k + 1 <= jj < n - k - 1):
                break
            g = math.hypot(bx[ii, jj], bz[ii, jj])
            if math.degrees(math.atan(g)) > 42 or g < 1e-6:
                break
            pi -= bx[ii, jj] / g * (2.0 / step)
            pj -= bz[ii, jj] / g * (2.0 / step)
            length += 2.0
            trail.append(h[int(pi), int(pj)])
            if len(trail) > 40:
                if trail[-41] - trail[-1] < 4:
                    break
            if trail[-1] < end_h:
                end_len, end_h = length, trail[-1]
        if end_len > 0:
            runs.append(end_len)
            drops.append(start_h - end_h)
    order = np.argsort(runs)
    runs = [runs[o] for o in order]
    drops = [drops[o] for o in order]
    return deg, bands, runs, drops


def render(h, deg, path):
    tint = np.zeros(h.shape + (3,))
    colors = [(170, 220, 170), (120, 200, 120), (110, 150, 235), (80, 80, 90), (40, 40, 45), (220, 70, 60)]
    edges = [0, 6, 15, 25, 35, 45, 1e9]
    for k in range(6):
        mask = (deg >= edges[k]) & (deg < edges[k + 1])
        tint[mask] = colors[k]
    gx = np.gradient(h, axis=0)
    gz = np.gradient(h, axis=1)
    shade = np.clip(0.8 + (-gx - gz) * 0.06, 0.35, 1.2)[..., None]
    white = ((h - h.min()) / max(1, h.max() - h.min()) * 0.35)[..., None]
    img = np.clip((tint * (1 - white) + 255 * white) * shade, 0, 255).astype(np.uint8)
    Image.fromarray(np.transpose(img, (1, 0, 2))).save(path)


def main():
    args = [a for a in sys.argv[1:] if "=" not in a]
    for a in sys.argv[1:]:
        if "=" in a:
            k, v = a.split("=", 1)
            P[k] = float(v)
    seed = int(args[0]) if args else 7
    out = args[1] if len(args) > 1 else "ski_lab.png"
    step = 8
    xs = np.arange(-4096, 4096, step, dtype=np.float64)
    h = heightfield(seed, xs, xs)
    deg, bands, runs, drops = analyse(h, step)
    names = ["flat<6", "green<15", "blue<25", "black<35", "double<45", "cliff"]
    print("mountain fraction (above valley+20): %.0f%%, top 5%% height %.0f" % (np.mean(h > P["valley"] + 20) * 100, np.percentile(h, 95)))
    print("heights %.0f..%.0f" % (h.min(), h.max()))
    print("  ".join(f"{nm} {b * 100:.0f}%" for nm, b in zip(names, bands)))
    if runs:
        print(f"runs from {len(runs)} high starts: median {runs[len(runs) // 2]:.0f}, p75 {runs[len(runs) * 3 // 4]:.0f}, "
              f"p90 {runs[len(runs) * 9 // 10]:.0f}, max {runs[-1]:.0f} blocks; median drop {sorted(drops)[len(drops) // 2]:.0f}")
    render(h, deg, out)


if __name__ == "__main__":
    main()
