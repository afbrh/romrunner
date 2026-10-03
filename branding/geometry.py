"""RomRunner mark geometry: an upright Game Boy-style cartridge.
Single source for both the SVG logos (build.py) and the Android vector icon (android_icon.py).
Everything is emitted as plain M/L/C/Z paths, because Android VectorDrawable supports neither
transforms like skew nor masks. Cutouts rely on even-odd filling."""
import math
from pathlib import Path

from fontTools.pens.basePen import BasePen
from fontTools.pens.boundsPen import BoundsPen
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

# The app's own UI font (Silkscreen, a 5x5-pixel font: one font "pixel" = 125 units).
_FONT = TTFont(Path(__file__).with_name("silkscreen_regular.ttf"))
_GLYPHS, _CMAP = _FONT.getGlyphSet(), _FONT.getBestCmap()
_CAP_UNITS = 625   # glyph height: 5 pixels

class _GlyphOutline(BasePen):
    """Replays a glyph's outline into a Pen, scaled/flipped into mark units (font y points up)."""
    def __init__(self, pen, ox, baseline, sx, sy):
        super().__init__(_GLYPHS)
        self.pen, self.ox, self.base, self.sx, self.sy = pen, ox, baseline, sx, sy
    def _p(self, p): return (self.ox + p[0] * self.sx, self.base - p[1] * self.sy)
    def _moveTo(self, p): self.pen.move(self._p(p))
    def _lineTo(self, p): self.pen.line(self._p(p))
    def _qCurveToOne(self, c, p): self.pen.quad(self._p(c), self._p(p))
    def _curveToOne(self, c1, c2, p):
        self.pen.d += f"C{_fmt(self.pen.T(*self._p(c1)))} {_fmt(self.pen.T(*self._p(c2)))} {_fmt(self.pen.T(*self._p(p)))}"
    def _closePath(self): self.pen.close()

def _ink(ch):
    bp = BoundsPen(_GLYPHS); _GLYPHS[_CMAP[ord(ch)]].draw(bp)
    return bp.bounds[0], bp.bounds[2]

def text_cutout(pen, text, x_left, x_right, cy, cap_height, stretch=1.0):
    """Outlines `text` into `pen` as cutouts (the body is filled even-odd, so letter counters fill
    back in): cap_height tall, centred vertically on cy, with the first letter's left edge on x_left
    and the last letter's right edge on x_right. `stretch` widens the pixels; whatever width is still
    missing after that is added as even letter-spacing."""
    sy = cap_height / _CAP_UNITS
    sx = sy * stretch
    adv = [_GLYPHS[_CMAP[ord(c)]].width * sx for c in text]
    first_l, last_r = _ink(text[0])[0] * sx, _ink(text[-1])[1] * sx
    natural = sum(adv[:-1]) - first_l + last_r
    gap = (x_right - x_left - natural) / max(1, len(text) - 1)
    x = x_left - first_l
    baseline = cy + cap_height / 2
    for c, a in zip(text, adv):
        _GLYPHS[_CMAP[ord(c)]].draw(_GlyphOutline(pen, x, baseline, sx, sy))
        x += a + gap

# Cartridge in unskewed units (roughly Game Boy proportions, 280 x 320).
X0, Y0, X1, Y1 = 150, 96, 430, 416
NOTCH, R_BOTTOM, R_TOP = 34, 8, 8
# Label window spans x 182..398. RUNNER fills it exactly. ROM starts on the same left edge but ends at
# 376: the top-right notch (x >= 396, y <= 130) would swallow the M's right stem at 398, so it stops
# well short of the notch (20 units, tuned by eye).
WIN_X0, WIN_X1 = 182, 398
ROM_X0, ROM_X1 = WIN_X0, 376
ROM_CAP, ROM_STRETCH = 44, 1.35    # ROM_STRETCH trades pixel width for letter-spacing
RUNNER_CAP = 30.9                  # = 216 / 35 pixels, so RUNNER sits on the window edges at natural spacing

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
    text_cutout(b, "ROM", ROM_X0, ROM_X1, 134, ROM_CAP, ROM_STRETCH)
    text_cutout(b, "RUNNER", WIN_X0, WIN_X1, 382, RUNNER_CAP)
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
