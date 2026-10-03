"""Writes the RomRunner mark SVGs. The cartridge carries the name itself (ROM / RUNNER cut out of the
shell in the app's own font, see geometry.py), so there are no separate wordmark logos."""
from geometry import paths, fitted

BG, FG = "#0E0A22", "#FF6B4A"

def mark(color):
    body = paths(fitted(512, 440))
    return f'<g fill="{color}"><path fill-rule="evenodd" d="{body}"/></g>'

def svg(vb, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">{body}</svg>\n'

def nested(x, y, s, color):
    return f'<svg x="{x}" y="{y}" width="{s}" height="{s}" viewBox="0 0 512 512">{mark(color)}</svg>'

def write(name, body): open(name, "w").write(body)

write("mark.svg", svg("0 0 512 512", mark(FG)))
write("mark-reversed.svg", svg("0 0 512 512", mark(BG)))  # for use on FG-colored backgrounds
write("app-icon.svg", svg("0 0 512 512", f'<rect width="512" height="512" rx="112" fill="{BG}"/>{nested(66, 66, 380, FG)}'))

# preview page (SVGs inlined so it renders anywhere)
def inl(f, w): return open(f).read().replace("<svg ", f'<svg width="{w}" ', 1)
write("preview.html", f'''<!doctype html><html><head><meta charset="utf-8"><title>RomRunner Logo</title>
<style>body{{margin:0;background:{BG}}}.row{{display:flex;gap:40px;align-items:center;justify-content:center;padding:40px;flex-wrap:wrap}}.fg{{background:{FG}}}</style></head><body>
<div class="row">{inl("mark.svg", 360)}</div>
<div class="row">{inl("app-icon.svg", 220)}{inl("app-icon.svg", 96)}{inl("app-icon.svg", 48)}{inl("app-icon.svg", 24)}</div>
<div class="row fg">{inl("mark-reversed.svg", 200)}</div>
</body></html>''')
