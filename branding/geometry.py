"""RomRunner mark geometry: an upright Game Boy-style cartridge with grip ridges, a down arrow and A/B buttons.
Single source for both the SVG logos (build.py) and the Android vector icon (android_icon.py).
Everything is emitted as plain M/L/C/Z paths, because Android VectorDrawable supports neither
transforms like skew nor masks. Cutouts rely on even-odd filling."""
import math
from pathlib import Path

from fontTools.pens.basePen import BasePen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import RecordingPen
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


def _glyph_cells(ch):
    """The glyph as a set of (col, row) pixels (row 0 = top), read from the outline: each pixel is 125
    font units, the glyph starts one pixel in, and a pixel is ink if its centre is inside the shape."""
    rp = RecordingPen(); _GLYPHS[_CMAP[ord(ch)]].draw(rp)
    loops, cur = [], []
    for op, args in rp.value:
        if op in ("moveTo", "lineTo"): cur.append(args[0])
        elif op == "closePath": loops.append(cur); cur = []
        else: raise ValueError(f"{ch}: curved glyph outline, expected a pixel font")
    def inside(x, y):
        hit = False
        for loop in loops:
            for (x0, y0), (x1, y1) in zip(loop, loop[1:] + loop[:1]):
                if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0): hit = not hit
        return hit
    ink_l, ink_r = _ink(ch)
    cols = round((ink_r - ink_l) / 125)
    return cols, {(c, 4 - r) for c in range(cols) for r in range(5)
                  if inside(ink_l + (c + .5) * 125, (r + .5) * 125)}

def _union_loops(rects):
    """Boundary loops of the union of axis-aligned rects [(x0, y0, x1, y1)], so overlapping or touching
    rects merge into one outline (counters come out as separate inner loops)."""
    xs = sorted({v for r in rects for v in (r[0], r[2])}); ys = sorted({v for r in rects for v in (r[1], r[3])})
    filled = {(i, j) for i in range(len(xs) - 1) for j in range(len(ys) - 1)
              if any(r[0] <= xs[i] and xs[i + 1] <= r[2] and r[1] <= ys[j] and ys[j + 1] <= r[3] for r in rects)}
    edges = {}
    for i, j in filled:
        x0, x1, y0, y1 = xs[i], xs[i + 1], ys[j], ys[j + 1]
        if (i, j - 1) not in filled: edges.setdefault((x0, y0), []).append((x1, y0))   # top, left->right
        if (i + 1, j) not in filled: edges.setdefault((x1, y0), []).append((x1, y1))   # right, down
        if (i, j + 1) not in filled: edges.setdefault((x1, y1), []).append((x0, y1))   # bottom, right->left
        if (i - 1, j) not in filled: edges.setdefault((x0, y1), []).append((x0, y0))   # left, up
    loops = []
    while edges:
        start = next(iter(edges)); pts = [start]; p = start
        while True:
            nxt = edges[p].pop()
            if not edges[p]: del edges[p]
            if nxt == start: break
            pts.append(nxt); p = nxt
        # drop collinear points so straight runs are single segments
        n = len(pts)
        loops.append([pts[k] for k in range(n)
                      if (pts[k][0] - pts[k - 1][0]) * (pts[(k + 1) % n][1] - pts[k][1])
                      != (pts[k][1] - pts[k - 1][1]) * (pts[(k + 1) % n][0] - pts[k][0])])
    return loops

def pixel_text_cutout(pen, text, x_left, x_right, cy, cap_height, bold, gap):
    """Pixel-font text cut out like text_cutout, but emboldened: every font pixel is grown outward by
    `bold` on all sides (so strokes get 2*bold thicker), and letters sit exactly `gap` apart. The text
    spans x_left..x_right and is cap_height tall in total, bold included; pixel width/height are
    solved from that, so thicker strokes eat into the letter shapes rather than the overall size."""
    glyphs = [_glyph_cells(c) for c in text]
    cols = sum(g[0] for g in glyphs)
    pw = (x_right - x_left - 2 * bold * len(text) - gap * (len(text) - 1)) / cols
    ph = (cap_height - 2 * bold) / 5
    top = cy - cap_height / 2 + bold
    x = x_left + bold
    for ncols, cells in glyphs:
        rects = [(x + c * pw - bold, top + r * ph - bold, x + (c + 1) * pw + bold, top + (r + 1) * ph + bold)
                 for c, r in cells]
        for loop in _union_loops(rects):
            pen.move(loop[0])
            for p in loop[1:]: pen.line(p)
            pen.close()
        x += ncols * pw + 2 * bold + gap

# ---- Window art: two R's in the app's pixel font, placed where a Game Boy's B (lower-left) and A (upper-right)
# buttons are, on the slight upward slope the real buttons have. (The 9 x 9 cell circles that used to sit behind
# them are gone; their grid still positions the letters.) ----
_CIRCLE_ROWS = [5, 7, 9, 9, 9, 9, 9, 7, 5]     # width of each row of a 9-cell pixel circle (all centred)
_CIRCLE_D = 9
_B_AT, _A_AT = (0, 4), (11, 0)                  # top-left cell of each circle: A is 11 across and 4 up from B
_ART_W, _ART_H = 20, 13                         # cells covered by both circles together

def _circle_cells(ox, oy):
    return {(ox + (_CIRCLE_D - w) // 2 + i, oy + r) for r, w in enumerate(_CIRCLE_ROWS) for i in range(w)}

def ab_buttons(pen, cx, cy, cell):
    """The two R's, laid out where the A and B buttons are (B lower-left, A upper-right) and centred on (cx, cy),
    each pixel `cell` units square. They stand solid in the dark label window, with no circles behind them."""
    x0, y0 = cx - _ART_W * cell / 2, cy - _ART_H * cell / 2
    r_cols, r_cells = _glyph_cells("R")
    r_left = (_CIRCLE_D - r_cols + 1) // 2           # leans the letter a half cell right: its weight is on the left
    r_top = (_CIRCLE_D - 5) // 2
    def rect(c, r): return (x0 + c * cell, y0 + r * cell, x0 + (c + 1) * cell, y0 + (r + 1) * cell)
    for ox, oy in (_B_AT, _A_AT):
        letter = {(ox + r_left + c, oy + r_top + r) for c, r in r_cells}
        for loop in _union_loops([rect(*c) for c in letter]):
            pen.move((loop[0][0], loop[0][1]))
            for pt in loop[1:]: pen.line(pt)
            pen.close()

def down_arrow(pen, cx, cy, cell):
    """The small down-pointing triangle moulded into the bottom of a Game Boy cartridge, as a pixel triangle."""
    widths = [9, 7, 5, 3, 1]
    h = len(widths) * cell
    rects = []
    for r, w in enumerate(widths):
        x = cx - w * cell / 2
        rects.append((x, cy - h / 2 + r * cell, x + w * cell, cy - h / 2 + (r + 1) * cell))
    for loop in _union_loops(rects):
        pen.move(loop[0])
        for pt in loop[1:]: pen.line(pt)
        pen.close()

def grip_ridges(pen, x0, x1, ys, thickness):
    """Horizontal grip grooves across the top of the cartridge, as rounded slots cut into the shell."""
    for y in ys:
        pen.rrect(x0, y - thickness / 2, x1 - x0, thickness, thickness / 2)

# Cartridge in unskewed units (Game Boy proportions: 57 x 65.5 mm).
X0, Y0, X1, Y1 = 150, 96, 430, 418   # 280 x 322: the real cartridge's 57 x 65.5 mm proportions
NOTCH, R_BOTTOM, R_TOP = 34, 10, 10   # R_TOP also rounds the notch's three corners
# Real-world sizes, in mm, mapped onto the cartridge's 280 x 322 units (57 x 65.5 mm), so the label window is the
# real sticker's size and sits where it does on a cartridge: 42 x 37 mm (the real corner radius is 1.5 mm; the window
# uses 15 units, about 3 mm, to taste), centred left to
# right (7.5 mm each side). Its distance from the top edge, 17 mm, is an estimate from photos of the cartridge.
MM = (X1 - X0) / 57
LABEL_W, LABEL_H, LABEL_R, LABEL_TOP = 42 * MM, 37 * MM, 15, 17 * MM
WIN_X0, WIN_X1 = X0 + (57 - 42) / 2 * MM, X1 - (57 - 42) / 2 * MM
WIN_Y0 = Y0 + LABEL_TOP
WIN_Y1 = WIN_Y0 + LABEL_H
# Two lines, centred between the cartridge's left edge and the notch's edge: they start in from the left edge by the
# same distance the label window does (so they line up with it), and stop that far short of the notch.
RIDGE_X0 = WIN_X0
RIDGE_X1 = (X1 - NOTCH) - (WIN_X0 - X0)
RIDGE_MID = (Y0 + WIN_Y0) / 2                       # the grip ridges are centred in the band above the label
RIDGE_YS, RIDGE_T = (RIDGE_MID - 9, RIDGE_MID + 9), 10

def paths(T):
    """Returns the mark's path, filled even-odd (cutouts + the A/B buttons inside the label)."""
    b = Pen(T)
    # shell, clockwise from top-left
    b.arc((X0 + R_TOP, Y0 + R_TOP), R_TOP, 180, 270, start=True)
    # notch cut out of the top-right corner, its three corners rounded to the same radius as the shell's others
    b.arc((X1 - NOTCH - R_TOP, Y0 + R_TOP), R_TOP, -90, 0)               # shell's top edge turning down into the notch
    b.arc((X1 - NOTCH + R_TOP, Y0 + NOTCH - R_TOP), R_TOP, 180, 90)      # inside corner of the notch (concave)
    b.arc((X1 - R_TOP, Y0 + NOTCH + R_TOP), R_TOP, -90, 0)               # notch floor turning down the right edge
    b.arc((X1 - R_BOTTOM, Y1 - R_BOTTOM), R_BOTTOM, 0, 90)
    b.arc((X0 + R_BOTTOM, Y1 - R_BOTTOM), R_BOTTOM, 90, 180)
    b.close()
    # grip ridges along the top, and the little down arrow along the bottom, cut out of the shell
    grip_ridges(b, RIDGE_X0, RIDGE_X1, RIDGE_YS, RIDGE_T)
    down_arrow(b, (WIN_X0 + WIN_X1) / 2, (WIN_Y1 + Y1) / 2, 7)   # centred in the band below the window
    # recessed label window, with the A and B buttons standing solid inside it
    b.rrect(WIN_X0, WIN_Y0, LABEL_W, LABEL_H, LABEL_R)
    ab_buttons(b, (WIN_X0 + WIN_X1) / 2, (WIN_Y0 + WIN_Y1) / 2, 9)

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
