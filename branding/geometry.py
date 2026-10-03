"""RomRunner mark geometry: an upright Game Boy-style cartridge.
Single source for both the SVG logos (build.py) and the Android vector icon (android_icon.py).
Everything is emitted as plain M/L/C/Z paths, because Android VectorDrawable supports neither
transforms like skew nor masks. Cutouts rely on even-odd filling."""
import math
from pathlib import Path

from fontTools.pens.basePen import BasePen
from fontTools.ttLib import TTFont

def _fmt(p): return f"{p[0]:.2f},{p[1]:.2f}"

class Pen:
    def __init__(self, T): self.T, self.d = T, ""
    def move(self, p): self.d += "M" + _fmt(self.T(*p))
    def line(self, p): self.d += "L" + _fmt(self.T(*p))
    def close(self): self.d += "Z"
    def arc(self, c, r, a0, a1, start=False):
        """Circular arc as cubics (exact under affine transforms), split into <=90 degree pieces."""
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

    def quad(self, c, p):
        self.d += f"Q{_fmt(self.T(*c))} {_fmt(self.T(*p))}"

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

_FONT = TTFont(next(Path(__file__).parent.glob("*.ttf")))
_GLYPHS, _CMAP, _UPM = _FONT.getGlyphSet(), _FONT.getBestCmap(), _FONT["head"].unitsPerEm
_CAP = _FONT["OS/2"].sCapHeight / _UPM

class _GlyphOutline(BasePen):
    """Replays a glyph's outline into a Pen, scaled/flipped into mark units (font y points up)."""
    def __init__(self, pen, ox, baseline, scale):
        super().__init__(_GLYPHS)
        self.pen, self.ox, self.base, self.k = pen, ox, baseline, scale
    def _p(self, p): return (self.ox + p[0] * self.k, self.base - p[1] * self.k)
    def _moveTo(self, p): self.pen.move(self._p(p))
    def _lineTo(self, p): self.pen.line(self._p(p))
    def _qCurveToOne(self, c, p): self.pen.quad(self._p(c), self._p(p))
    def _curveToOne(self, c1, c2, p):
        self.pen.d += f"C{_fmt(self.pen.T(*self._p(c1)))} {_fmt(self.pen.T(*self._p(c2)))} {_fmt(self.pen.T(*self._p(p)))}"
    def _closePath(self): self.pen.close()

def text_cutout(pen, text, cx, cy, cap_height, tracking=0.03):
    """Outlines `text` (cap-height tall, centred on cx/cy) into `pen`. The shapes become cutouts
    because the body is filled even-odd; letter counters (the hole in an O or R) fill back in."""
    scale = cap_height / (_CAP * _UPM)
    adv = [_GLYPHS[_CMAP[ord(c)]].width * scale for c in text]
    gap = tracking * cap_height / _CAP
    x = cx - (sum(adv) + gap * (len(text) - 1)) / 2
    baseline = cy + cap_height / 2
    for c, a in zip(text, adv):
        _GLYPHS[_CMAP[ord(c)]].draw(_GlyphOutline(pen, x, baseline, scale))
        x += a + gap

# Cartridge in unskewed units (roughly Game Boy proportions, 280 x 320).
X0, Y0, X1, Y1 = 150, 96, 430, 416
NOTCH, R_BOTTOM, R_TOP = 34, 8, 8
TOP_CAP, BOTTOM_CAP = 46, 36   # cap heights of "ROM" and "RUNNER"

def paths(T):
    """Returns the mark's path, filled even-odd (cutouts + the arrow inside the label)."""
    b = Pen(T)
    # shell, clockwise from top-left
    b.arc((X0 + R_TOP, Y0 + R_TOP), R_TOP, 180, 270, start=True)
    # square notch cut out of the top-right corner
    b.line((X1 - NOTCH, Y0)); b.line((X1 - NOTCH, Y0 + NOTCH)); b.line((X1, Y0 + NOTCH))
    b.arc((X1 - R_BOTTOM, Y1 - R_BOTTOM), R_BOTTOM, 0, 90)
    b.arc((X0 + R_BOTTOM, Y1 - R_BOTTOM), R_BOTTOM, 90, 180)
    b.close()
    # the name, cut out of the shell: ROM in the top band, RUNNER in the bottom one
    cx = (X0 + X1) / 2
    text_cutout(b, "ROM", cx, 134, TOP_CAP)
    text_cutout(b, "RUNNER", cx, 382, BOTTOM_CAP)
    # recessed label window, with the play arrow standing solid inside it
    b.rrect(182, 172, 216, 176, 18)
    b.round_poly([(264, 216), (334, 260), (264, 304)], 9)

    return b.d

def bbox():
    return X0, Y0, X1, Y1

def fitted(size, extent):
    """Transform mapping the mark so its bounding box is centred in a size x size box,
    with the longer side spanning `extent`."""
    x0, y0, x1, y1 = bbox()
    s = extent / max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return lambda x, y: (size / 2 + (x - cx) * s, size / 2 + (y - cy) * s)
