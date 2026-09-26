from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

BG, FG = "#0E0A22", "#FF6B4A"

font = TTFont("RussoOne.ttf")
gs, cmap = font.getGlyphSet(), font.getBestCmap()
upm = font["head"].unitsPerEm

def text_path(s, x, baseline, size, tracking=0.02, skew=-0.18):
    sc, pen = size / upm, SVGPathPen(gs)
    for ch in s:
        g = cmap[ord(ch)]
        gs[g].draw(TransformPen(pen, (sc, 0, -skew * sc, -sc, x, baseline)))
        x += gs[g].width * sc + tracking * size
    return pen.getCommands(), x

# Flying SD card: one solid shape, details are knocked out (negative space).
def mark(color, uid):
    return f'''<defs><mask id="cut{uid}" maskUnits="userSpaceOnUse" x="-100" y="0" width="700" height="512">
  <rect x="-100" width="700" height="512" fill="#fff"/>
  <g fill="#000"><rect x="222" y="124" width="16" height="44" rx="6"/><rect x="252" y="124" width="16" height="44" rx="6"/>
  <rect x="282" y="124" width="16" height="44" rx="6"/><rect x="312" y="124" width="16" height="44" rx="6"/></g>
  <path d="M262 246 L340 296 L262 346 Z" fill="#000" stroke="#000" stroke-width="18" stroke-linejoin="round"/>
</mask></defs>
<g transform="translate(50 0) skewX(-10)" fill="{color}">
  <path mask="url(#cut{uid})" d="M214 96 H340 L390 146 V392 Q390 416 366 416 H214 Q190 416 190 392 V120 Q190 96 214 96 Z"/>
  <rect x="70" y="214" width="92" height="26" rx="13"/>
  <rect x="112" y="283" width="50" height="26" rx="13"/>
  <rect x="44" y="352" width="118" height="26" rx="13"/>
</g>'''

def svg(vb, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">{body}</svg>\n'

def nested(x, y, s, color, uid):
    return f'<svg x="{x}" y="{y}" width="{s}" height="{s}" viewBox="0 0 512 512">{mark(color, uid)}</svg>'

def write(name, body): open(name, "w").write(body)

write("mark.svg", svg("0 0 512 512", mark(FG, "m")))
write("mark-reversed.svg", svg("0 0 512 512", mark(BG, "r")))  # for use on FG-colored backgrounds
write("app-icon.svg", svg("0 0 512 512", f'<rect width="512" height="512" rx="112" fill="{BG}"/>{nested(16, 8, 480, FG, "a")}'))

d, xend = text_path("ROMRUNNER", 330, 250, 150)
W = int(xend + 40)
for name, bg in [("logo-horizontal.svg", BG), ("logo-horizontal-transparent.svg", None)]:
    back = f'<rect width="{W}" height="400" fill="{bg}"/>' if bg else ""
    write(name, svg(f"0 0 {W} 400", f'{back}{nested(0, 30, 340, FG, "h")}<path d="{d}" fill="{FG}"/>'))

d, xend = text_path("ROMRUNNER", 40, 520, 110)
W = int(xend + 60)
write("logo-stacked.svg", svg(f"0 0 {W} 580", f'<rect width="{W}" height="580" fill="{BG}"/>{nested(W//2 - 200, 20, 400, FG, "s")}<path d="{d}" fill="{FG}"/>'))

# preview page (SVGs inlined so it renders anywhere)
def inl(f, w, style=""): return open(f).read().replace("<svg ", f'<svg width="{w}" style="{style}" ', 1)
write("preview.html", f'''<!doctype html><html><head><meta charset="utf-8"><title>RomRunner Logo</title>
<style>body{{margin:0;background:{BG}}}.row{{display:flex;gap:40px;align-items:center;justify-content:center;padding:40px;flex-wrap:wrap}}.fg{{background:{FG}}}</style></head><body>
<div class="row">{inl("logo-horizontal.svg", 860)}</div>
<div class="row">{inl("app-icon.svg", 220)}{inl("app-icon.svg", 96)}{inl("app-icon.svg", 48)}{inl("app-icon.svg", 24)}{inl("logo-stacked.svg", 300)}</div>
<div class="row fg">{inl("mark-reversed.svg", 200)}</div>
</body></html>''')
