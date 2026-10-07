#!/usr/bin/env python3
"""Write the Material Design Icons paths the add-on draws, from @mdi/js.

    npm pack @mdi/js && tar xzf mdi-js-*.tgz
    tools/generate_icons.py package/mdi.js

Keys are normalised (lower case, no dashes) so `mdi:numeric-10-box` and `mdiNumeric10Box` meet at
`numeric10box`; `Icons.kt` normalises a name the same way before it looks one up.

Paths are rewritten to absolute M, L, C and Z only, one command per segment, numbers separated by
spaces. Still valid SVG for the editor, and simple enough for the add-on to read without the Remote
Compose path parser (which knows neither relative commands nor V, T or numbers like `1-2.5.5`).
"""
import json
import math
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "server/src/main/resources/mdi.json"
LINE = re.compile(r'^export var mdi(\w+) = "([^"]+)";$')
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def fmt(v: float) -> str:
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def arc_to_cubics(x1, y1, rx, ry, phi, large, sweep, x2, y2):
    """SVG arc to cubic Béziers (SVG 1.1 appendix F.6)."""
    if rx == 0 or ry == 0 or (x1 == x2 and y1 == y2):
        return [(x1, y1, x2, y2, x2, y2)]
    rx, ry = abs(rx), abs(ry)
    cp, sp = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2

    def angle(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    n = max(1, math.ceil(abs(dt) / (math.pi / 2) - 1e-9))
    step = dt / n
    k = 4 / 3 * math.tan(step / 4)
    out = []
    for i in range(n):
        a1, a2 = t1 + i * step, t1 + (i + 1) * step
        c1, s1, c2, s2 = math.cos(a1), math.sin(a1), math.cos(a2), math.sin(a2)
        p1 = (c1 - k * s1, s1 + k * c1)
        p2 = (c2 + k * s2, s2 - k * c2)
        p3 = (c2, s2)

        def tr(p):
            x, y = p[0] * rx, p[1] * ry
            return cp * x - sp * y + cx, sp * x + cp * y + cy

        a, b, c = tr(p1), tr(p2), tr(p3)
        out.append((a[0], a[1], b[0], b[1], c[0], c[1]))
    return out


def normalise(d: str) -> str:
    tokens = TOKEN.findall(d)
    out = []
    i = 0
    cmd = None
    x = y = sx = sy = 0.0
    last_c = None  # second control point of the last cubic, for S
    last_q = None  # control point of the last quadratic, for T
    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                out.append("Z")
                x, y = sx, sy
                last_c = last_q = None
                continue
        elif cmd is None:
            raise ValueError(f"path starts with a number: {d[:40]}")
        up = cmd.upper()
        rel = cmd != up
        n = ARGS[up]
        a = [float(v) for v in tokens[i:i + n]]
        if len(a) < n:
            break
        i += n
        if up == "M":
            nx, ny = (x + a[0], y + a[1]) if rel else (a[0], a[1])
            out.append(f"M {fmt(nx)} {fmt(ny)}")
            x, y, sx, sy = nx, ny, nx, ny
            cmd = "l" if rel else "L"
            last_c = last_q = None
        elif up in "LHV":
            if up == "L":
                nx, ny = (x + a[0], y + a[1]) if rel else (a[0], a[1])
            elif up == "H":
                nx, ny = (x + a[0] if rel else a[0]), y
            else:
                nx, ny = x, (y + a[0] if rel else a[0])
            out.append(f"L {fmt(nx)} {fmt(ny)}")
            x, y = nx, ny
            last_c = last_q = None
        elif up in "CS":
            if up == "C":
                pts = [(a[j] + (x if rel else 0), a[j + 1] + (y if rel else 0)) for j in (0, 2, 4)]
            else:
                c1 = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y)
                pts = [c1] + [(a[j] + (x if rel else 0), a[j + 1] + (y if rel else 0)) for j in (0, 2)]
            out.append("C " + " ".join(f"{fmt(p[0])} {fmt(p[1])}" for p in pts))
            last_c = pts[1]
            x, y = pts[2]
            last_q = None
        elif up in "QT":
            if up == "Q":
                q = (a[0] + (x if rel else 0), a[1] + (y if rel else 0))
                end = (a[2] + (x if rel else 0), a[3] + (y if rel else 0))
            else:
                q = (2 * x - last_q[0], 2 * y - last_q[1]) if last_q else (x, y)
                end = (a[0] + (x if rel else 0), a[1] + (y if rel else 0))
            c1 = (x + 2 / 3 * (q[0] - x), y + 2 / 3 * (q[1] - y))
            c2 = (end[0] + 2 / 3 * (q[0] - end[0]), end[1] + 2 / 3 * (q[1] - end[1]))
            out.append("C " + " ".join(f"{fmt(p[0])} {fmt(p[1])}" for p in (c1, c2, end)))
            last_q = q
            x, y = end
            last_c = None
        elif up == "A":
            ex, ey = (x + a[5], y + a[6]) if rel else (a[5], a[6])
            for c in arc_to_cubics(x, y, a[0], a[1], a[2], a[3] != 0, a[4] != 0, ex, ey):
                out.append("C " + " ".join(fmt(v) for v in c))
            x, y = ex, ey
            last_c = last_q = None
    return " ".join(out)


def main(src: str) -> None:
    text = pathlib.Path(src).read_text()
    version = re.search(r"Material Design Icons v([\d.]+)", text).group(1)
    icons = {}
    for line in text.splitlines():
        m = LINE.match(line)
        if m:
            icons[m.group(1).lower()] = normalise(m.group(2))
    OUT.write_text(json.dumps({"version": version, "icons": icons}, separators=(",", ":")))
    print(f"{len(icons)} icons from MDI {version} -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/mdi/package/mdi.js")
