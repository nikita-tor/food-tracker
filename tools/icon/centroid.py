"""Visual center of an icon's foreground: each spot weighted by how much it stands out from the background
(color distance times alpha). Prints the offset from the true center (54, 54) in icon units and as % of the
visible 72-unit circle.

Usage: python centroid.py <background.xml> <foreground.xml>
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render  # noqa: E402


def centroid(bg, fg, step=0.25):
    back = render.shapes(bg)[0][2]
    layers = render.shapes(fg)
    sw = sx = sy = 0.0
    y = step / 2
    while y < 108:
        x = step / 2
        while x < 108:
            if (x - 54) ** 2 + (y - 54) ** 2 <= 36 ** 2:
                r, g, b = back[:3]
                for l in layers:
                    hit = render.inside(x, y, l[1], l[3]) if l[0] == "fill" else render.near(x, y, l[1], l[3] / 2, l[4])
                    if hit:
                        cr, cg, cb, ca = l[2]
                        r, g, b = r + (cr - r) * ca, g + (cg - g) * ca, b + (cb - b) * ca
                w = math.dist((r, g, b), back[:3])
                sw += w; sx += w * x; sy += w * y
            x += step
        y += step
    return sx / sw, sy / sw


if __name__ == "__main__":
    cx, cy = centroid(sys.argv[1], sys.argv[2])
    print(f"visual center ({cx:.2f}, {cy:.2f}); off by dx={cx-54:+.2f}, dy={cy-54:+.2f} "
          f"({math.hypot(cx-54, cy-54)/72*100:.1f}% of the icon's width)")
