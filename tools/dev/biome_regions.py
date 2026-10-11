"""Find the biggest patch of each Fresh Tracks biome in run/biomemap.png (made by /ftdev biomemap).

  python tools/dev/biome_regions.py <centre_x> <centre_z> <step>
"""
import sys
from collections import deque

from PIL import Image

COLORS = {
    "powder_bowls": (0x2E, 0x7B, 0xFF), "frosted_glades": (0x19, 0xB7, 0xA8), "alpenglow_peaks": (0xFF, 0x5F, 0xA8),
    "glacier": (0x7F, 0xF0, 0xFF), "larch_basin": (0xFF, 0x9A, 0x1F), "aspen_hollow": (0xFF, 0xE0, 0x3A),
}


def main():
    cx, cz, step = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3])
    im = Image.open("run/biomemap.png").convert("RGB")
    w, h = im.size
    px = im.load()
    for name, col in COLORS.items():
        seen = set()
        best = []
        total = 0
        for i in range(w):
            for j in range(h):
                if (i, j) in seen or px[i, j] != col:
                    continue
                comp = []
                q = deque([(i, j)])
                seen.add((i, j))
                while q:
                    a, b = q.popleft()
                    comp.append((a, b))
                    for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        na, nb = a + da, b + db
                        if 0 <= na < w and 0 <= nb < h and (na, nb) not in seen and px[na, nb] == col:
                            seen.add((na, nb))
                            q.append((na, nb))
                total += len(comp)
                best.append(comp)
        best.sort(key=len, reverse=True)
        out = []
        for comp in best[:3]:
            mi = sum(p[0] for p in comp) / len(comp)
            mj = sum(p[1] for p in comp) / len(comp)
            area = len(comp) * step * step
            out.append(f"{len(comp) * step * step // 1000}k m2 at {int(cx + (mi - w / 2) * step)} {int(cz + (mj - h / 2) * step)}")
        print(f"{name:16s} patches={len(best):4d}  " + " | ".join(out))


if __name__ == "__main__":
    main()
