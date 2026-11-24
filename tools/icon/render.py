"""Renders an Android adaptive icon (background + foreground vector XML) to a PNG, with a launcher-style
circle mask, plus guides: the 66dp safe zone and crosshairs through the center. Pure Python, no libraries.

Usage: python render.py <background.xml> <foreground.xml> <out.png> [size] [--guides]
"""
import math
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zlib

A = "{http://schemas.android.com/apk/res/android}"


def color(text, alpha=1.0):
    text = text.lstrip("#")
    if len(text) == 8:
        a, text = int(text[:2], 16) / 255, text[2:]
    else:
        a = 1.0
    return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16), a * alpha)


def tokens(d):
    for m in re.finditer(r"[MmLlHhVvCcAaZz]|-?\d*\.?\d+(?:e-?\d+)?", d):
        yield m.group(0)


def arc_points(x1, y1, rx, ry, phi, large, sweep, x2, y2, n=64):
    # SVG endpoint-to-center conversion (rx == ry, phi == 0 is all these icons use, but keep it general).
    cp, sp = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = x1p**2 / rx**2 + y1p**2 / ry**2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx**2 * ry**2 - rx**2 * y1p**2 - ry**2 * x1p**2
    den = rx**2 * y1p**2 + ry**2 * x1p**2
    coef = math.sqrt(max(0, num / den)) * (-1 if large == sweep else 1)
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx, cy = cp * cxp - sp * cyp + (x1 + x2) / 2, sp * cxp + cp * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    pts = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n
        x, y = rx * math.cos(t), ry * math.sin(t)
        pts.append((cp * x - sp * y + cx, sp * x + cp * y + cy))
    return pts


def flatten(d):
    """Path data -> list of (points, closed) subpaths."""
    toks = list(tokens(d))
    i, cmd, x, y, sx, sy = 0, None, 0.0, 0.0, 0.0, 0.0
    subs, cur = [], []

    def num():
        nonlocal i
        i += 1
        return float(toks[i - 1])

    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]
            i += 1
        rel = cmd.islower()
        c = cmd.upper()
        if c == "Z":
            if cur:
                subs.append((cur, True))
            cur, x, y = [], sx, sy
            continue
        if c == "M":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            if cur:
                subs.append((cur, False))
            cur, sx, sy = [(x, y)], x, y
            cmd = "l" if rel else "L"
        elif c == "L":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            cur.append((x, y))
        elif c == "H":
            nx = num()
            x = x + nx if rel else nx
            cur.append((x, y))
        elif c == "V":
            ny = num()
            y = y + ny if rel else ny
            cur.append((x, y))
        elif c == "C":
            p = [num() for _ in range(6)]
            if rel:
                p = [p[0] + x, p[1] + y, p[2] + x, p[3] + y, p[4] + x, p[5] + y]
            for k in range(1, 33):
                t = k / 32
                mt = 1 - t
                cur.append((
                    mt**3 * x + 3 * mt**2 * t * p[0] + 3 * mt * t**2 * p[2] + t**3 * p[4],
                    mt**3 * y + 3 * mt**2 * t * p[1] + 3 * mt * t**2 * p[3] + t**3 * p[5],
                ))
            x, y = p[4], p[5]
        elif c == "A":
            rx, ry, phi, large, sweep, nx, ny = (num() for _ in range(7))
            if rel:
                nx, ny = x + nx, y + ny
            cur.extend(arc_points(x, y, rx, ry, phi, int(large), int(sweep), nx, ny))
            x, y = nx, ny
    if cur:
        subs.append((cur, False))
    return subs


def shapes(path):
    out = []
    for p in ET.parse(path).getroot().iter("path"):
        d = p.get(A + "pathData")
        subs = flatten(d)
        if p.get(A + "fillColor"):
            out.append(("fill", subs, color(p.get(A + "fillColor"), float(p.get(A + "fillAlpha", 1))),
                        p.get(A + "fillType", "nonZero")))
        if p.get(A + "strokeColor"):
            out.append((
                "stroke",
                subs,
                color(p.get(A + "strokeColor"), float(p.get(A + "strokeAlpha", 1))),
                float(p.get(A + "strokeWidth", 1)),
                p.get(A + "strokeLineCap", "butt"),
            ))
    return out


def inside(px, py, subs, rule="nonZero"):
    wn = crossings = 0
    for pts, _ in subs:
        for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
            if y1 <= py < y2 and (x2 - x1) * (py - y1) - (px - x1) * (y2 - y1) > 0:
                wn += 1
                crossings += 1
            elif y2 <= py < y1 and (x2 - x1) * (py - y1) - (px - x1) * (y2 - y1) < 0:
                wn -= 1
                crossings += 1
    return crossings % 2 == 1 if rule == "evenOdd" else wn != 0


def near(px, py, subs, half, cap):
    for pts, closed in subs:
        segs = list(zip(pts, pts[1:] + (pts[:1] if closed else [])))
        for j, ((x1, y1), (x2, y2)) in enumerate(segs):
            dx, dy = x2 - x1, y2 - y1
            ll = dx * dx + dy * dy
            t = 0 if ll == 0 else ((px - x1) * dx + (py - y1) * dy) / ll
            if cap != "round" and not closed and ((j == 0 and t < 0) or (j == len(segs) - 1 and t > 1)):
                continue
            t = max(0, min(1, t))
            if (px - x1 - t * dx) ** 2 + (py - y1 - t * dy) ** 2 <= half * half:
                return True
    return False


def bbox(subs, pad):
    xs = [p[0] for s, _ in subs for p in s]
    ys = [p[1] for s, _ in subs for p in s]
    return min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad


def render(bg, fg, out, size=432, guides=False, ss=2):
    layers = shapes(bg) + shapes(fg)
    boxes = [bbox(l[1], (l[3] / 2 if l[0] == "stroke" else 0) + 1) for l in layers]
    rows = []
    n = size * ss
    acc = [[0.0, 0.0, 0.0] for _ in range(size * size)]
    for sy in range(n):
        for sx in range(n):
            x, y = (sx + 0.5) * 108 / n, (sy + 0.5) * 108 / n
            r = g = b = 255.0  # page white outside the mask
            if (x - 54) ** 2 + (y - 54) ** 2 <= 36**2:  # launcher circle mask: the visible 72dp
                r = g = b = 0.0
                for l, (x0, y0, x1, y1) in zip(layers, boxes):
                    if not (x0 <= x <= x1 and y0 <= y <= y1):
                        continue
                    hit = inside(x, y, l[1], l[3]) if l[0] == "fill" else near(x, y, l[1], l[3] / 2, l[4])
                    if hit:
                        cr, cg, cb, ca = l[2]
                        r, g, b = r + (cr - r) * ca, g + (cg - g) * ca, b + (cb - b) * ca
            if guides:
                d = math.hypot(x - 54, y - 54)
                if abs(d - 33) < 0.18 or abs(x - 54) < 0.12 or abs(y - 54) < 0.12:
                    r, g, b = 255, 0, 255
            a = acc[(sy // ss) * size + sx // ss]
            a[0] += r
            a[1] += g
            a[2] += b
    k = ss * ss
    for row in range(size):
        line = bytearray([0])
        for col in range(size):
            a = acc[row * size + col]
            line += bytes(int(v / k) for v in a)
        rows.append(bytes(line))
    raw = b"".join(rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(out, "wb").write(png)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    render(args[0], args[1], args[2], int(args[3]) if len(args) > 3 else 432, guides="--guides" in sys.argv)
