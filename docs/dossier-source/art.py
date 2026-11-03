"""Original vector artwork for the Kodistan dossier: gears, rune rings, isometric pixel blocks."""
import math
import random
import re
from pathlib import Path

ICON_DIR = Path(__file__).parent / "icons"


def icon(name, color="currentColor", size=None, cls=""):
    """Inline a game-icons.net SVG, recolored."""
    svg = (ICON_DIR / f"{name}.svg").read_text(encoding="utf-8")
    svg = re.sub(r'fill="#000"', f'fill="{color}"', svg)
    svg = svg.replace('fill="#fff"', 'fill="none"')
    attrs = f' class="ico {cls}"'
    if size:
        attrs += f' width="{size}" height="{size}"'
    return svg.replace("<svg ", f"<svg{attrs} ", 1)


def gear_path(cx, cy, r_out, r_in, teeth, hole=0.0, tooth_ratio=0.5):
    pts = []
    step = 2 * math.pi / teeth
    for i in range(teeth):
        a = i * step
        half = step * tooth_ratio / 2
        for ang, r in ((a - half * 1.25, r_in), (a - half * 0.8, r_out),
                       (a + half * 0.8, r_out), (a + half * 1.25, r_in)):
            pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    d = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"
    if hole:
        d += (f" M{cx + hole:.2f},{cy:.2f} A{hole:.2f},{hole:.2f} 0 1,0 {cx - hole:.2f},{cy:.2f}"
              f" A{hole:.2f},{hole:.2f} 0 1,0 {cx + hole:.2f},{cy:.2f} Z")
    return d


def gear(cx, cy, r, teeth=12, fill="#d9a93f", opacity=1.0, spokes=5, stroke=None):
    r_in = r * 0.86
    d = gear_path(cx, cy, r, r_in, teeth, hole=r * 0.62)
    out = [f'<path d="{d}" fill="{fill}" fill-rule="evenodd" opacity="{opacity}"'
           + (f' stroke="{stroke}" stroke-width="{r*0.02:.2f}"' if stroke else "") + "/>"]
    # spokes + hub
    for i in range(spokes):
        a = i * 2 * math.pi / spokes
        x2, y2 = cx + r * 0.64 * math.cos(a), cy + r * 0.64 * math.sin(a)
        out.append(f'<line x1="{cx:.1f}" y1="{cy:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
                   f'stroke="{fill}" stroke-width="{r*0.11:.1f}" stroke-linecap="round" opacity="{opacity}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{r*0.2:.1f}" fill="{fill}" opacity="{opacity}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{r*0.08:.1f}" fill="#120e0a" opacity="{opacity}"/>')
    return "\n".join(out)


# Simplified Elder-Futhark-like glyphs, strokes in a 0..1 box (x, y pairs)
RUNES = [
    [(0.3, 0, 0.3, 1), (0.3, 0.15, 0.75, 0), (0.3, 0.45, 0.75, 0.3)],       # fehu
    [(0.25, 1, 0.25, 0), (0.25, 0, 0.75, 0.35), (0.75, 0.35, 0.75, 1)],     # uruz
    [(0.3, 0, 0.3, 1), (0.3, 0.25, 0.7, 0.5), (0.7, 0.5, 0.3, 0.75)],       # thurisaz
    [(0.3, 0, 0.3, 1), (0.3, 0, 0.7, 0.25), (0.3, 0.3, 0.7, 0.55)],         # ansuz
    [(0.5, 0, 0.5, 1), (0.2, 0.3, 0.8, 0.7)],                               # nauthiz-ish
    [(0.5, 0, 0.5, 1)],                                                     # isa
    [(0.3, 0, 0.7, 0.5), (0.7, 0.5, 0.3, 1)],                               # kenaz-ish
    [(0.2, 0, 0.2, 1), (0.8, 0, 0.8, 1), (0.2, 0, 0.8, 0.5), (0.8, 0, 0.2, 0.5)],  # mannaz-ish
    [(0.5, 0, 0.5, 1), (0.5, 0.35, 0.15, 0), (0.5, 0.35, 0.85, 0)],         # algiz
    [(0.5, 0, 0.5, 1), (0.5, 0, 0.8, 0.3), (0.5, 0.5, 0.8, 0.3), (0.5, 0.5, 0.8, 0.8), (0.5, 1, 0.8, 0.8)],  # berkano
    [(0.2, 0.2, 0.8, 0.8), (0.8, 0.2, 0.2, 0.8), (0.2, 0.2, 0.2, 0.8), (0.8, 0.2, 0.8, 0.8)],  # dagaz-ish
    [(0.5, 0, 0.5, 1), (0.5, 0.2, 0.8, 0.45), (0.8, 0.45, 0.5, 0.7)],       # wunjo-ish
]


def rune_ring(cx, cy, r, color="#54d6c8", count=24, glyph=None, width=None, seed=3, opacity=1.0):
    rnd = random.Random(seed)
    glyph = glyph or r * 0.16
    width = width or max(1.0, r * 0.012)
    out = [f'<g opacity="{opacity}">',
           f'<circle cx="{cx}" cy="{cy}" r="{r + glyph*0.75:.1f}" fill="none" stroke="{color}" stroke-width="{width:.2f}"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{r - glyph*0.75:.1f}" fill="none" stroke="{color}" stroke-width="{width:.2f}"/>']
    for i in range(count):
        a = i * 2 * math.pi / count - math.pi / 2
        gx, gy = cx + r * math.cos(a), cy + r * math.sin(a)
        rot = math.degrees(a) + 90
        strokes = rnd.choice(RUNES)
        segs = []
        for x1, y1, x2, y2 in strokes:
            segs.append(f'M{(x1-0.5)*glyph*0.7:.2f},{(y1-0.5)*glyph:.2f} L{(x2-0.5)*glyph*0.7:.2f},{(y2-0.5)*glyph:.2f}')
        out.append(f'<path transform="translate({gx:.2f},{gy:.2f}) rotate({rot:.1f})" d="{" ".join(segs)}" '
                   f'stroke="{color}" stroke-width="{width*1.4:.2f}" fill="none" stroke-linecap="square"/>')
    out.append("</g>")
    return "\n".join(out)


def _shade(hex_color, f):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i+2], 16) for i in (0, 2, 4))
    r, g, b = (max(0, min(255, int(c * f))) for c in (r, g, b))
    return f"#{r:02x}{g:02x}{b:02x}"


BLOCKS = {
    "grass": {"top": ["#6aa84f", "#5d9a44", "#78b85a", "#4f8a3a"],
              "side": ["#8b5e3c", "#7a5134", "#9a6a45", "#6b4630"], "fringe": ["#6aa84f", "#5d9a44"]},
    "stone": {"top": ["#8a8a8a", "#7b7b7b", "#999999", "#6f6f6f"], "side": ["#8a8a8a", "#7b7b7b", "#999999", "#6f6f6f"]},
    "gold": {"top": ["#f7d64a", "#e8bf2b", "#fde68a", "#d4a520"], "side": ["#f7d64a", "#e8bf2b", "#fde68a", "#d4a520"]},
    "copper": {"top": ["#c87d4a", "#b56d3c", "#d98f5a", "#9f5e33", "#5fa58f"],
               "side": ["#c87d4a", "#b56d3c", "#d98f5a", "#9f5e33", "#5fa58f"]},
    "rune": {"top": ["#2b2540", "#231e36", "#332c4b", "#1d1830"], "side": ["#2b2540", "#231e36", "#332c4b", "#1d1830"],
             "glow": "#54d6c8"},
    "wood": {"top": ["#b58a52", "#a47a45", "#c49a60", "#8f6a3a"], "side": ["#6e4f2c", "#5f4426", "#7d5b33", "#523a20"]},
}

RUNE_PIX = {(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (2, 2), (3, 3), (4, 3), (5, 2), (6, 1),
            (6, 2), (6, 3), (6, 4), (6, 5), (6, 6)}


def iso_block(x, y, s, kind="grass", seed=1, n=8):
    """Isometric pixel cube; (x, y) = top vertex, s = edge length in px."""
    rnd = random.Random(seed)
    spec = BLOCKS[kind]
    cos30, sin30 = math.cos(math.pi / 6), 0.5
    u = s / n
    polys = []

    def quad(p0, du, dv, color):
        pts = [p0, (p0[0] + du[0], p0[1] + du[1]), (p0[0] + du[0] + dv[0], p0[1] + du[1] + dv[1]),
               (p0[0] + dv[0], p0[1] + dv[1])]
        polys.append(f'<polygon points="{" ".join(f"{a:.2f},{b:.2f}" for a, b in pts)}" fill="{color}"/>')

    # unit vectors
    ex = (cos30 * u, sin30 * u)     # right-down
    ey = (-cos30 * u, sin30 * u)    # left-down
    ez = (0, u)                     # down
    # top face
    for i in range(n):
        for j in range(n):
            p = (x + i * ex[0] + j * ey[0], y + i * ex[1] + j * ey[1])
            c = rnd.choice(spec["top"])
            if kind == "rune" and (i, j) in RUNE_PIX:
                c = spec["glow"]
            quad(p, ex, ey, c)
    left_origin = (x + n * ey[0], y + n * ey[1])
    right_origin = (x + n * ex[0], y + n * ex[1])
    # left face (darker)
    for i in range(n):
        for k in range(n):
            p = (left_origin[0] + i * ex[0] + k * ez[0], left_origin[1] + i * ex[1] + k * ez[1])
            c = rnd.choice(spec["side"])
            if kind == "grass" and k < (1 + (rnd.random() < 0.5)):
                c = rnd.choice(spec["fringe"])
            if kind == "rune" and (i, k) in RUNE_PIX:
                c = spec["glow"]
            quad(p, ex, ez, _shade(c, 0.78))
    # right face (darkest)
    for j in range(n):
        for k in range(n):
            p = (right_origin[0] + j * ey[0] + k * ez[0], right_origin[1] + j * ey[1] + k * ez[1])
            c = rnd.choice(spec["side"])
            if kind == "grass" and k < (1 + (rnd.random() < 0.5)):
                c = rnd.choice(spec["fringe"])
            if kind == "rune" and (n - 1 - j, k) in RUNE_PIX:
                c = spec["glow"]
            quad(p, ey, ez, _shade(c, 0.6))
    return "<g>" + "".join(polys) + "</g>"


def svg(w, h, body, cls="", style=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" class="{cls}" '
            f'style="{style}" preserveAspectRatio="xMidYMid meet">{body}</svg>')


def defs_glow():
    return ('<defs>'
            '<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="6" result="b"/>'
            '<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>'
            '<radialGradient id="halo" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#d9a93f" stop-opacity="0.35"/>'
            '<stop offset="100%" stop-color="#d9a93f" stop-opacity="0"/></radialGradient>'
            '<radialGradient id="halo-rune" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#54d6c8" stop-opacity="0.28"/>'
            '<stop offset="100%" stop-color="#54d6c8" stop-opacity="0"/></radialGradient>'
            '<linearGradient id="goldgrad" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#fbe7a1"/>'
            '<stop offset="50%" stop-color="#d9a93f"/><stop offset="100%" stop-color="#9c6b1e"/></linearGradient>'
            '</defs>')
