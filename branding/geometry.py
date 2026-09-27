"""RomRunner mark geometry: a flying Game Boy-style cartridge.
Single source for both the SVG logos (build.py) and the Android vector icon (android_icon.py).
Everything is emitted as plain M/L/C/Z paths with the italic skew baked in, because Android
VectorDrawable supports neither skew transforms nor masks. Cutouts rely on even-odd filling."""
import math

SKEW = math.tan(math.radians(-10))

def _fmt(p): return f"{p[0]:.2f},{p[1]:.2f}"

class Pen:
    def __init__(self, T): self.T, self.d = T, ""
    def move(self, p): self.d += "M" + _fmt(self.T(*p))
    def line(self, p): self.d += "L" + _fmt(self.T(*p))
    def close(self): self.d += "Z"
    def arc(self, c, r, a0, a1, start=False):
        """Circular arc as cubics (exact under the affine skew), split into <=90 degree pieces."""
        n = max(1, math.ceil(abs(a1 - a0) / 90))
        for i in range(n):
            b0 = math.radians(a0 + (a1 - a0) * i / n)
            b1 = math.radians(a0 + (a1 - a0) * (i + 1) / n)
            k = 4 / 3 * math.tan((b1 - b0) / 4)
            p0 = (c[0] + r * math.cos(b0), c[1] + r * math.sin(b0))
            p3 = (c[0] + r * math.cos(b1), c[1] + r * math.sin(b1))
            p1 = (p0[0] - k * r * math.sin(b0), p0[1] + k * r * math.cos(b0))
            p2 = (p3[0] + k * r * math.sin(b1), p3[1] - k * r * math.cos(b1))
            if i == 0: (self.move if start else self.line)(p0)
            self.d += f"C{_fmt(self.T(*p1))} {_fmt(self.T(*p2))} {_fmt(self.T(*p3))}"

    def rrect(self, x, y, w, h, r):
        for i, (c, a0) in enumerate([((x + w - r, y + r), -90), ((x + w - r, y + h - r), 0),
                                     ((x + r, y + h - r), 90), ((x + r, y + r), 180)]):
            self.arc(c, r, a0, a0 + 90, start=(i == 0))
        self.close()

    def round_poly(self, pts, r):
        """Polygon grown by r with round corners (same as stroke-linejoin=round, width 2r)."""
        n = len(pts)
        cx, cy = sum(p[0] for p in pts) / n, sum(p[1] for p in pts) / n
        def normal(a, b):
            nx, ny = b[1] - a[1], a[0] - b[0]
            if nx * ((a[0] + b[0]) / 2 - cx) + ny * ((a[1] + b[1]) / 2 - cy) < 0: nx, ny = -nx, -ny
            return math.degrees(math.atan2(ny, nx))
        for i in range(n):
            a0, a1 = normal(pts[i - 1], pts[i]), normal(pts[i], pts[(i + 1) % n])
            self.arc(pts[i], r, a0, a0 + (a1 - a0 + 180) % 360 - 180, start=(i == 0))
        self.close()

# Cartridge in unskewed units (roughly Game Boy proportions, 280 x 320).
X0, Y0, X1, Y1 = 150, 96, 430, 416
NOTCH, R_BOTTOM, R_TOP = 34, 8, 8

def paths(T):
    """Returns (body, bars): body is filled even-odd (cutouts + the arrow inside the label)."""
    b = Pen(T)
    # shell, clockwise from top-left
    b.arc((X0 + R_TOP, Y0 + R_TOP), R_TOP, 180, 270, start=True)
    # square notch cut out of the top-right corner
    b.line((X1 - NOTCH, Y0)); b.line((X1 - NOTCH, Y0 + NOTCH)); b.line((X1, Y0 + NOTCH))
    b.arc((X1 - R_BOTTOM, Y1 - R_BOTTOM), R_BOTTOM, 0, 90)
    b.arc((X0 + R_BOTTOM, Y1 - R_BOTTOM), R_BOTTOM, 90, 180)
    b.close()
    # grip ridges across the top
    b.rrect(186, 114, 172, 12, 6)
    b.rrect(186, 136, 172, 12, 6)
    # recessed label window, with the play arrow standing solid inside it
    b.rrect(182, 172, 216, 176, 18)
    b.round_poly([(264, 216), (334, 260), (264, 304)], 9)
    # embossed insert arrow near the bottom
    b.round_poly([(274, 374), (306, 374), (290, 392)], 4)

    s = Pen(T)
    s.rrect(30, 214, 92, 26, 13)
    s.rrect(72, 283, 50, 26, 13)
    s.rrect(4, 352, 118, 26, 13)
    return b.d, s.d

def bbox():
    pts = []
    for x, y in [(X0, Y0), (X1, Y0), (X0, Y1), (X1, Y1), (4, 352), (4, 378), (30, 214)]:
        pts.append((x + SKEW * y, y))
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)

def fitted(size, extent):
    """Transform mapping the skewed mark so its bounding box is centred in a size x size box,
    with the longer side spanning `extent`."""
    x0, y0, x1, y1 = bbox()
    s = extent / max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return lambda x, y: (size / 2 + (x + SKEW * y - cx) * s, size / 2 + (y - cy) * s)
