"""Plans de principe du dossier « Donjon de la Station » (SVG inline).

Ce sont des plans de CONCEPTION (rien n'est construit) : cotes indicatives en blocs,
dessinés à la main dans ce fichier, pas extraits d'un gabarit. Les codes couleurs
reprennent ceux de Luca (voir §2 du dossier).
"""
import math

BG = "#262b33"
DRY = "#cbbd96"
DRY2 = "#a89b74"
WET = "#3f86b4"
WETL = "#7cc0e8"
DEEP = "#27567a"
PIPE = "#c88a4a"
STONE = "#8f8a7e"
RED = "#e53935"
BLUE = "#1e63e9"
ORANGE = "#f28c28"
YELLOW = "#f5c518"
GREEN = "#2e9e4f"
CYAN = "#4fc3f7"
TARTRE = "#b58a3c"

DEFS = (
    '<defs>'
    '<pattern id="hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">'
    '<line x1="0" y1="0" x2="0" y2="7" stroke="#f28c28" stroke-width="2.2"/></pattern>'
    '<pattern id="hatchT" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(-45)">'
    '<rect width="6" height="6" fill="#7a5a22"/><line x1="0" y1="0" x2="0" y2="6" stroke="#d4a84c" stroke-width="2.4"/></pattern>'
    '<pattern id="hatchW" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">'
    '<line x1="0" y1="0" x2="0" y2="7" stroke="#ffffff" stroke-opacity=".35" stroke-width="1.6"/></pattern>'
    '</defs>'
)


# ---------------------------------------------------------------- symboles (centrés en x,y)
def s_cross(x, y, r=5):
    return (f'<g stroke="{RED}" stroke-width="3" stroke-linecap="round">'
            f'<line x1="{x-r}" y1="{y-r}" x2="{x+r}" y2="{y+r}"/><line x1="{x-r}" y1="{y+r}" x2="{x+r}" y2="{y-r}"/></g>')


def s_mini(x, y):
    return (f'<text x="{x}" y="{y+6}" font-size="19" font-weight="800" fill="#d81b60" stroke="#fff" '
            f'stroke-width="3" paint-order="stroke" text-anchor="middle" font-family="Segoe UI,Arial,sans-serif">M</text>')


def s_anchor(x, y, r=5.5):
    return f'<circle cx="{x}" cy="{y}" r="{r}" fill="{BLUE}" stroke="#fff" stroke-width="1.6"/>'


def s_chest(x, y, r=5.5, fill=YELLOW):
    return f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}" stroke="#4a3b00" stroke-width="1.4"/>'


def s_key(x, y, n, r=7):
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="{GREEN}" stroke="#fff" stroke-width="1.6"/>'
            f'<text x="{x}" y="{y+3.4}" font-size="10" font-weight="700" fill="#fff" text-anchor="middle" '
            f'font-family="Segoe UI,Arial,sans-serif">{n}</text>')


def s_lever(x, y, n, s=7.5):
    return (f'<rect x="{x-s}" y="{y-s}" width="{2*s}" height="{2*s}" rx="1.5" fill="{ORANGE}" stroke="#fff" stroke-width="1.6"/>'
            f'<text x="{x}" y="{y+3.6}" font-size="10" font-weight="700" fill="#2a1500" text-anchor="middle" '
            f'font-family="Segoe UI,Arial,sans-serif">{n}</text>')


def s_boss(x, y, r=11):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append(f"{x+rr*math.cos(a):.1f},{y+rr*math.sin(a):.1f}")
    return (f'<polygon points="{" ".join(pts)}" fill="#8e24aa" stroke="#fff" stroke-width="1.5"/>'
            f'<text x="{x}" y="{y+3.6}" font-size="9" font-weight="800" fill="#fff" text-anchor="middle" '
            f'font-family="Segoe UI,Arial,sans-serif">B</text>')


def s_gear(x, y, r=9, fill="#d9a441", teeth=10):
    pts = []
    for i in range(teeth * 2):
        a = i * math.pi / teeth
        rr = r if i % 2 == 0 else r * 0.74
        pts.append(f"{x+rr*math.cos(a):.1f},{y+rr*math.sin(a):.1f}")
    return (f'<polygon points="{" ".join(pts)}" fill="{fill}" stroke="#3a2a08" stroke-width="1.2"/>'
            f'<circle cx="{x}" cy="{y}" r="{r*0.28:.1f}" fill="#3a2a08"/>')


def s_gate(x, y, length=14, vertical=True, col="#14171c"):
    if vertical:
        return (f'<rect x="{x-2.5}" y="{y-length/2}" width="5" height="{length}" fill="{col}" stroke="#fff" stroke-width="1.2"/>')
    return (f'<rect x="{x-length/2}" y="{y-2.5}" width="{length}" height="5" fill="{col}" stroke="#fff" stroke-width="1.2"/>')


def s_plate(x, y, n, s=7):
    return (f'<rect x="{x-s}" y="{y-s}" width="{2*s}" height="{2*s}" fill="#e0a45e" stroke="#5a3510" stroke-width="1.6"/>'
            f'<text x="{x}" y="{y+3.4}" font-size="9.5" font-weight="700" fill="#3a1f00" text-anchor="middle" '
            f'font-family="Segoe UI,Arial,sans-serif">{n}</text>')


def s_turbine(x, y, r=11):
    out = [f'<circle cx="{x}" cy="{y}" r="{r}" fill="#2f4558" stroke="#9fc3dc" stroke-width="1.4"/>']
    for k in range(4):
        a = k * math.pi / 2 + 0.4
        out.append(f'<line x1="{x}" y1="{y}" x2="{x+r*0.92*math.cos(a):.1f}" y2="{y+r*0.92*math.sin(a):.1f}" '
                   f'stroke="#e6edf2" stroke-width="3" stroke-linecap="round"/>')
    out.append(f'<circle cx="{x}" cy="{y}" r="2.6" fill="#e6edf2"/>')
    return "".join(out)


def s_boiler(x, y, r=8):
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="#b4602d" stroke="#3a1a08" stroke-width="1.5"/>'
            f'<circle cx="{x}" cy="{y}" r="{r*0.45:.1f}" fill="#e8a066" stroke="#3a1a08" stroke-width="1"/>')


def s_vent(x, y):
    return ''.join(f'<circle cx="{x+dx}" cy="{y+dy}" r="{r}" fill="#fff" fill-opacity=".8"/>'
                   for dx, dy, r in ((-3, 2, 3), (2, -1, 3.6), (5, 3, 2.6)))


def s_arrow(x1, y1, x2, y2, col=CYAN, w=3, dash=None):
    ang = math.atan2(y2 - y1, x2 - x1)
    L = max(w * 3.4, 8)
    ex, ey = x2 - 0.6 * L * math.cos(ang), y2 - 0.6 * L * math.sin(ang)
    p1 = (x2 - L * math.cos(ang - 0.45), y2 - L * math.sin(ang - 0.45))
    p2 = (x2 - L * math.cos(ang + 0.45), y2 - L * math.sin(ang + 0.45))
    d = f' stroke-dasharray="{dash}"' if dash else ''
    return (f'<line x1="{x1}" y1="{y1}" x2="{ex:.1f}" y2="{ey:.1f}" stroke="{col}" stroke-width="{w}" stroke-linecap="round"{d}/>'
            f'<polygon points="{x2},{y2} {p1[0]:.1f},{p1[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}" fill="{col}"/>')


def s_north(x, y):
    return (f'<polygon points="{x},{y-12} {x-6},{y+4} {x+6},{y+4}" fill="#fff"/>'
            f'<text x="{x}" y="{y+16}" font-size="9" font-weight="700" fill="#fff" text-anchor="middle" '
            f'font-family="Segoe UI,Arial,sans-serif">N</text>')


# ---------------------------------------------------------------- figure
class Fig:
    def __init__(self, w, h, bg=BG):
        self.w, self.h = w, h
        self.o = [f'<rect width="{w}" height="{h}" fill="{bg}"/>']

    def add(self, s):
        self.o.append(s)
        return self

    def rect(self, x, y, w, h, fill=DRY, stroke="none", sw=1, op=1, rx=0, dash=None):
        d = f' stroke-dasharray="{dash}"' if dash else ''
        return self.add(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" fill="{fill}" '
                        f'fill-opacity="{op}" stroke="{stroke}" stroke-width="{sw}"{d}/>')

    def circ(self, cx, cy, r, fill=DRY, stroke="none", sw=1, op=1, dash=None):
        d = f' stroke-dasharray="{dash}"' if dash else ''
        return self.add(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{fill}" fill-opacity="{op}" '
                        f'stroke="{stroke}" stroke-width="{sw}"{d}/>')

    def line(self, x1, y1, x2, y2, stroke="#fff", sw=2, dash=None, op=1):
        d = f' stroke-dasharray="{dash}"' if dash else ''
        return self.add(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{stroke}" '
                        f'stroke-width="{sw}" stroke-opacity="{op}" stroke-linecap="round"{d}/>')

    def poly(self, pts, fill=DRY, stroke="none", sw=1, op=1, dash=None):
        p = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
        d = f' stroke-dasharray="{dash}"' if dash else ''
        return self.add(f'<polygon points="{p}" fill="{fill}" fill-opacity="{op}" stroke="{stroke}" stroke-width="{sw}"{d}/>')

    def path(self, d, stroke="#fff", sw=2, fill="none", dash=None, op=1):
        da = f' stroke-dasharray="{dash}"' if dash else ''
        return self.add(f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-opacity="{op}" '
                        f'stroke-linecap="round" stroke-linejoin="round"{da}/>')

    def text(self, x, y, t, size=10, fill="#fff", anchor="middle", weight="normal"):
        return self.add(f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}" text-anchor="{anchor}" '
                        f'font-weight="{weight}" font-family="Segoe UI,Arial,sans-serif">{t}</text>')

    def label(self, x, y, t, size=9, anchor="middle"):
        w = len(t) * size * 0.54 + 8
        bx = x - w / 2 if anchor == "middle" else (x if anchor == "start" else x - w)
        tx = x if anchor == "middle" else (x + 4 if anchor == "start" else x - 4)
        self.add(f'<rect x="{bx:.1f}" y="{y-size-1:.1f}" width="{w:.1f}" height="{size+6:.1f}" rx="2.5" fill="#f4f1e8" fill-opacity=".94" '
                 f'stroke="#222" stroke-opacity=".5" stroke-width=".6"/>')
        return self.add(f'<text x="{tx:.1f}" y="{y+1:.1f}" font-size="{size}" font-weight="700" fill="#1c1c1c" text-anchor="{anchor}" '
                        f'font-family="Segoe UI,Arial,sans-serif">{t}</text>')

    def scale(self, x, y, px, blocks=10):
        self.add(f'<rect x="{x}" y="{y}" width="{px*blocks:.1f}" height="4" fill="#fff"/>')
        return self.text(x + px * blocks + 6, y + 5, f"{blocks} blocs", 9, "#cfd6dd", "start")

    def render(self):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="100%">'
                f'{DEFS}{"".join(self.o)}</svg>')


def sym_svg(inner, w=26, h=26):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">{DEFS}{inner}</svg>'


# ================================================================ FIGURE 1 : vue d'ensemble
def fig_ensemble():
    S = 3.0
    f = Fig(720, 735)
    X = lambda x: x * S
    Z = lambda z: z * S

    def region(x0, z0, x1, z1, label, lx, lz, fill=WET, op=.55):
        f.rect(X(x0), Z(z0), X(x1 - x0), Z(z1 - z0), fill, "#9fd0ee", 1.2, op, 6)
        f.label(X(lx), Z(lz), label, 9)

    region(8, 50, 82, 150, "A · Filtration (nager vite)", 45, 56)
    region(78, 4, 162, 70, "C · Turbines (tourbillon)", 120, 10)
    region(182, 55, 236, 130, "B · Chaufferie (se lester)", 209, 61)
    f.circ(X(120), Z(110), X(44), "none", "#9fd0ee", 1.4, 1, "6 5")
    f.label(X(72), Z(146), "Réservoir du boss (sous la Cuve)", 8.5)
    f.circ(X(120), Z(110), X(34), DRY, "#2a2a2a", 1)
    f.circ(X(120), Z(110), X(30), WET, "#9fd0ee", 1.5)
    f.circ(X(120), Z(110), X(6), DEEP, "#fff", 1.2)
    f.text(X(120), Z(110) + 3, "Bonde", 9, "#fff", "middle", "700")
    f.label(X(120), Z(98), "LA GRANDE CUVE", 10.5)
    f.text(X(120), Z(124), "Coursive (sec) autour", 8, "#1c1c1c", "middle", "600")
    f.circ(X(160), Z(78), X(7), WET, "#9fd0ee", 1.4, .9)
    f.label(X(160), Z(66), "Cheminée → Poste", 8.5)
    for (bx, bz, t) in ((90, 110, "A"), (150, 110, "B"), (120, 80, "C"), (141, 89, "D")):
        f.circ(X(bx), Z(bz), 5.5, "#fff", "#222", 1.2)
        f.text(X(bx), Z(bz) + 3.2, t, 8.5, "#111", "middle", "800")
    f.rect(X(112), Z(144), X(16), Z(26), DRY, "#2a2a2a", 1)
    f.label(X(120), Z(158), "Sas", 8.5)
    f.rect(X(60), Z(170), X(120), Z(55), DRY, "#2a2a2a", 1)
    f.rect(X(66), Z(176), X(24), Z(24), DRY2, "#fff", 1)
    f.rect(X(96), Z(200), X(56), Z(14), DRY2, "#fff", 1)
    f.circ(X(160), Z(186), X(8), DRY2, "#fff", 1)
    f.rect(X(170), Z(174), X(8), Z(8), WET, "#9fd0ee", 1.2)
    f.label(X(120), Z(190), "HALL DES BASSINS", 10)
    f.text(X(78), Z(206), "plongeon", 7.5, "#1c1c1c", "middle", "600")
    f.text(X(124), Z(222), "bassin de 50 m", 7.5, "#1c1c1c", "middle", "600")
    f.text(X(160), Z(201), "toboggan", 7.5, "#1c1c1c", "middle", "600")
    f.rect(X(12), Z(178), X(42), Z(44), DRY, "#2a2a2a", 1)
    f.rect(X(54), Z(198), X(6), Z(6), DRY, "#2a2a2a", 1)
    f.label(X(33), Z(200), "VESTIAIRE", 9.5)
    f.add(s_key(X(18), Z(212), 1))
    pts = [(X(174), Z(174)), (X(174), Z(134)), (X(138), Z(134))]
    f.path("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts), "#7cc0e8", 9, op=.95)
    f.add(s_arrow(X(174), Z(140), X(174), Z(166), "#ffffff", 2.4, "4 3"))
    f.label(X(178), Z(150), "Le Drain (contournement)", 8.5, "start")
    f.text(X(182), Z(160), "≈ 75 blocs, contre-courant", 8, "#cfe9f8", "start")
    f.add(s_arrow(X(120), Z(242), X(120), Z(228), GREEN, 5))
    f.label(X(120), Z(236), "ENTRÉE · escalier de 24 marches (du pavillon en surface)", 8.5)
    f.add(s_north(690, 40))
    f.scale(560, 690, S, 20)
    f.text(560, 676, "Vue de dessus, plan de principe", 8.5, "#cfd6dd", "start")
    return f.render()


# ================================================================ FIGURE 2 : coupe dépliée
def fig_coupe():
    f = Fig(720, 505)
    X = lambda x: 14 + 3.3 * x
    Y = lambda y: 478 - 4 * y
    WALL = "#8a8478"
    f.rect(0, Y(108), 720, 505, "#33383f")
    f.rect(0, 0, 720, Y(108), "#1d2a38")
    f.rect(0, Y(108) - 3, 720, 6, "#4e8f3a")
    f.text(700, 20, "coupe dépliée (schématique)", 8.5, "#cfd6dd", "end")
    # pavillon + escalier
    f.rect(X(2), Y(115), X(11), 28, "#d8cdb0", "#222", 1)
    f.label(X(8), Y(121), "Pavillon", 8.5)
    st = [(X(13), Y(108))]
    for i in range(24):
        st += [(X(13 + i), Y(108 - i)), (X(13 + i + 1), Y(108 - i))]
    st.append((X(37), Y(84)))
    f.path("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in st), "#d8cdb0", 2.6)
    f.label(X(30), Y(100), "escalier 24 marches", 8.5)
    # hall + sas
    f.rect(X(37), Y(98), X(61), 4 * 14, "#e9e2cb", "#222", 1)
    f.rect(X(37), Y(84), X(61), 4, "#cbbd96")
    f.label(X(67), Y(93), "HALL DES BASSINS (sec)", 9)
    f.rect(X(44), Y(84), X(14), 4 * 12, "#a89b74", "#222", 1)
    f.text(X(51), Y(79), "creux 12", 8, "#1c1c1c", "middle", "700")
    f.rect(X(98), Y(90), X(20), 4 * 6, "#e9e2cb", "#222", 1)
    f.rect(X(98), Y(84), X(20), 4, "#cbbd96")
    f.label(X(108), Y(87), "Sas", 8.5)
    # réservoir
    f.rect(X(104), Y(24), X(88), 4 * 24, "#1f4666", "#9fd0ee", 1.2)
    pts = [(X(110 + t * 76), Y(12 + 5 * math.sin(t * 9))) for t in [i / 39 for i in range(40)]]
    f.path("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts), "#c7a7e8", 5, op=.55)
    f.label(X(148), Y(5), "RÉSERVOIR DU BOSS · sol 0, plafond 24 (noyé)", 9)
    # cuve : eau et paroi
    f.rect(X(118), Y(96), X(60), 4 * 66, "#0f1a25")
    f.rect(X(118), Y(76), X(60), 4 * 46, WET, "none", 1, .72)
    f.rect(X(118), Y(76), X(60), 4 * 46, "url(#hatchW)")
    f.rect(X(116), Y(98), X(2), 4 * 70, WALL)
    f.rect(X(178), Y(98), X(2), 4 * 70, WALL)
    f.rect(X(116), Y(98), X(64), 4, WALL)
    f.rect(X(116), Y(30), X(26), 5, WALL)
    f.rect(X(154), Y(30), X(26), 5, WALL)
    f.text(X(148), Y(99) - 5, "plafond de la Cuve 96", 8, "#aaa", "middle")
    f.text(X(130), Y(30) + 17, "sol de la Cuve 30", 8, "#cfd6dd", "middle")
    for lvl, name in ((76, "HAUT 76"), (58, "MÉDIAN 58"), (42, "BAS 42")):
        f.line(X(118), Y(lvl), X(178), Y(lvl), "#fff", 1.6, "7 4")
        f.text(X(176), Y(lvl) - 3, name, 8.5, "#fff", "end", "700")
    # coursive
    f.rect(X(118), Y(84), X(7), 4, "#cbbd96")
    f.rect(X(171), Y(84), X(7), 4, "#cbbd96")
    f.text(X(130), Y(84) - 9, "Coursive 84", 8.5, "#cbbd96", "start", "700")
    # bonde
    f.rect(X(142), Y(30), X(12), 5, DEEP, "#fff", 1.2)
    f.add(s_arrow(X(148), Y(33), X(148), Y(20), "#ffffff", 2.6))
    f.label(X(148), Y(40), "bonde", 8.5)
    # bouches (disques)
    def bouche(x, y0, y1, t, dashed=False):
        f.rect(X(x) - 4, Y(y1), 8, 4 * (y1 - y0), "#fff", "#222", 1, .95 if not dashed else .6, 0, "3 2" if dashed else None)
        f.circ(X(x), Y((y0 + y1) / 2), 7, "#fff", "#222", 1.2)
        f.text(X(x), Y((y0 + y1) / 2) + 3.2, t, 8.5, "#111", "middle", "800")
    bouche(118, 64, 70, "A")
    bouche(118, 32, 38, "B")
    bouche(148, 48, 54, "C", True)
    bouche(180, 30, 36, "D")
    # cheminée
    f.rect(X(184), Y(84), X(14), 4 * 54, "#0f1a25")
    f.rect(X(184), Y(76), X(14), 4 * 46, WET, "none", 1, .72)
    f.rect(X(180), Y(36), X(4), 4 * 6, WET, "none", 1, .72)
    f.rect(X(183), Y(84), 2, 4 * 54, WALL)
    f.rect(X(198), Y(84), 2, 4 * 54, WALL)
    f.label(X(191), Y(56), "Cheminée", 8.5)
    f.line(X(184), Y(76), X(198), Y(76), "#fff", 1.4, "6 3")
    # poste + plongeoir
    f.rect(X(163), Y(96), X(43), 4 * 12, "#e9e2cb", "#222", 1)
    f.label(X(185), Y(92), "POSTE", 9)
    f.add(s_lever(X(199), Y(88), "!", 7))
    f.rect(X(153), Y(87) - 2, X(11), 5, "#d8cdb0", "#222", 1)
    f.label(X(150), Y(104), "plongeoir final", 8.5)
    f.add(s_arrow(X(154), Y(85), X(147), Y(36), "#ffffff", 2, "4 3"))
    # clé des bouches (dans la roche)
    f.rect(X(2), Y(66), X(46), 4 * 26, "#262b33", "#9aa3ad", 1, .9, 3)
    f.text(X(4), Y(62), "Bouches de la Cuve", 9, "#fff", "start", "700")
    for k, t in enumerate(("A · 64–70 (ouest, Filtration)", "B · 32–38 (est, Chaufferie)",
                           "C · 48–54 (nord, Turbines)", "D · 30–36 (nord-est, Cheminée)")):
        f.text(X(4), Y(55 - k * 6), t, 8.3, "#e6edf2", "start")
    for y, t in ((108, "108 plaine"), (84, "84 sol du Hall"), (30, "30"), (0, "0")):
        f.line(8, Y(y), 22, Y(y), "#fff", 1)
        f.text(24, Y(y) + 3, t, 8, "#cfd6dd", "start")
    return f.render()


# ================================================================ FIGURE 3 : acte I
def fig_acte1():
    S = 3.85
    f = Fig(720, 460)
    X = lambda x: 8 + S * (x - 8)
    Z = lambda z: 10 + S * (z - 134)
    f.rect(X(112), Z(140), S * 16, S * 30, DRY, "#2a2a2a", 1)
    f.label(X(120), Z(150), "SAS (fermé)", 8.5)
    f.add(s_gate(X(120), Z(170), S * 16, False))
    for i in range(3):
        f.circ(X(114 + i * 6), Z(165), 2.6, "#555", "#fff", 1)
    f.label(X(120), Z(160), "3 témoins", 7.5)
    f.rect(X(60), Z(170), S * 120, S * 55, DRY, "#2a2a2a", 1.2)
    for i in range(11):
        f.rect(X(63 + i * 10.6), Z(171), 4, 4, STONE)
        f.rect(X(63 + i * 10.6), Z(220), 4, 4, STONE)
    f.rect(X(66), Z(176), S * 24, S * 24, DRY2, "#fff", 1.4)
    f.rect(X(66), Z(176), S * 24, S * 24, "url(#hatch)", "none", 1, .5)
    f.rect(X(72), Z(182), S * 12, S * 12, "#e9dcae", "#222", 1)
    f.rect(X(75), Z(185), S * 6, S * 6, "#f4ecc8", "#222", 1)
    f.add(s_lever(X(78), Z(188), 1))
    for (ax, az) in ((70, 178), (88, 178), (70, 198), (88, 198), (78, 180)):
        f.add(s_anchor(X(ax), Z(az)))
    f.label(X(78), Z(208), "bassin du plongeon (creux 12)", 8)
    f.rect(X(96), Z(200), S * 56, S * 14, DRY2, "#fff", 1.4)
    f.rect(X(96), Z(200), S * 56, S * 14, "url(#hatch)", "none", 1, .5)
    for (cx, cz) in ((108, 205), (116, 209), (126, 205), (138, 209)):
        f.add(s_cross(X(cx), Z(cz)))
    f.add(s_lever(X(149), Z(207), 2))
    f.label(X(124), Z(198), "bassin de 50 m · embuscade", 8)
    f.circ(X(160), Z(186), S * 8, DRY2, "#fff", 1.4)
    f.circ(X(160), Z(186), S * 5.2, "#e9dcae", "#222", 1)
    f.circ(X(160), Z(186), S * 2.4, "#f4ecc8", "#222", 1)
    f.add(s_lever(X(160), Z(186), 3))
    for (ax, az) in ((154, 178), (167, 182), (153, 193), (160, 180)):
        f.add(s_anchor(X(ax), Z(az)))
    f.label(X(160), Z(199), "tour du toboggan", 8)
    f.rect(X(170), Z(174), S * 8, S * 8, WET, "#9fd0ee", 1.4)
    f.label(X(167), Z(172), "bassin d'essai", 8, "end")
    f.path(f"M{X(174):.1f},{Z(174):.1f} L{X(174):.1f},{Z(138):.1f}", "#7cc0e8", 9, op=.95)
    f.add(s_arrow(X(174), Z(150), X(174), Z(168), "#ffffff", 2.4, "4 3"))
    f.label(X(170), Z(146), "le Drain → Cuve (≈ 75 blocs)", 8, "end")
    f.rect(X(12), Z(178), S * 42, S * 44, DRY, "#2a2a2a", 1.2)
    f.rect(X(54), Z(198), S * 6, S * 6, DRY, "#2a2a2a", 1)
    for i in range(10):
        f.rect(X(14), Z(181 + i * 4), 5, 11, "#6b7a8c", "#222", .8)
        f.rect(X(48), Z(181 + i * 4), 5, 11, "#6b7a8c", "#222", .8)
    f.add(s_key(X(31), Z(200), 1, 8))
    f.label(X(33), Z(190), "VESTIAIRE · casier de la cape", 8.5)
    f.text(X(33), Z(214), "(apparaît après les 3 volants)", 7.5, "#1c1c1c", "middle", "600")
    f.rect(X(114), Z(225), S * 12, S * 14, "#9b9486", "#222", 1)
    for i in range(12):
        f.line(X(114), Z(226 + i), X(126), Z(226 + i), "#6e6a60", 1)
    f.add(s_arrow(X(120), Z(241), X(120), Z(228), GREEN, 5))
    f.label(X(130), Z(236), "ENTRÉE (surface)", 8.5, "start")
    f.add(s_north(690, 32))
    f.scale(540, 430, S, 10)
    return f.render()


# ================================================================ FIGURE 4 : secteur A
def fig_secteur_a():
    S = 4.3
    f = Fig(720, 400)
    X = lambda x: 14 + S * x
    Z = lambda z: 34 + S * z
    wet = lambda x0, z0, w, h: f.rect(X(x0), Z(z0), S * w, S * h, WET, "#9fd0ee", 1.2, .6)
    wet(148, 30, 12, 14)
    f.label(X(154), Z(27), "écluse A", 8)
    wet(98, 35, 50, 4)
    for gx in (138, 126, 114, 102):
        f.add(s_gate(X(gx), Z(37), S * 5, True))
    f.add(s_lever(X(147), Z(33.2), "▶", 6))
    f.add(s_arrow(X(146), Z(37), X(100), Z(37), "#fff", 2, "5 4"))
    f.label(X(123), Z(30), "A2 · chaîne de 4 vannes : 50 blocs, 5,0 s", 8.5)
    wet(44, 8, 54, 64)
    for (bx, bz, bw, bh) in ((58, 44, 5, 28), (70, 8, 5, 32), (82, 44, 5, 28)):
        f.rect(X(bx), Z(bz), S * bw, S * bh, "#d9c58e", "#4a3b10", 1.3)
    for (gx, gz) in ((64, 16), (76, 62), (88, 24), (52, 54)):
        f.add(s_cross(X(gx), Z(gz), 5.5))
    f.circ(X(76), Z(62), S * 15, "none", "#ff8a80", 1, .7, "4 3")
    f.path(f"M{X(98):.1f},{Z(37):.1f} L{X(78.5):.1f},{Z(37):.1f} L{X(78.5):.1f},{Z(56):.1f} L{X(66.5):.1f},{Z(56):.1f} "
           f"L{X(66.5):.1f},{Z(30):.1f} L{X(52):.1f},{Z(30):.1f} L{X(46):.1f},{Z(37):.1f}", "#ffffff", 2.2, dash="5 4", op=.9)
    f.label(X(71), Z(80), "A3 · salle des lits : 4 gardiens (laser, portée 15) · 2 noyés", 8.5)
    wet(8, 18, 36, 38)
    f.add(s_gear(X(26), Z(37), 20))
    f.add(s_lever(X(26), Z(37), "A", 9))
    f.label(X(26), Z(15), "A4 · salle de la Pompe A", 8.5)
    f.add(s_cross(X(14), Z(26), 5.5))
    f.add(s_cross(X(14), Z(48), 5.5))
    wet(56, 0, 36, 6)
    f.add(s_gate(X(56), Z(3), S * 5, True))
    f.add(s_gate(X(92), Z(3), S * 5, True))
    f.add(s_chest(X(74), Z(3)))
    f.label(X(96), Z(3), "chambre cachée (coffre)", 8, "start")
    f.add(s_north(700, 40))
    f.scale(560, 376, S, 10)
    f.text(14, 392, "vue de dessus, plan de principe · secteur entièrement noyé", 8.5, "#cfd6dd", "start")
    return f.render()


# ================================================================ FIGURE 5 : secteur B
def fig_secteur_b():
    S = 4.0
    f = Fig(720, 420)
    X = lambda x: 12 + S * x
    Z = lambda z: 36 + S * z
    f.text(12 + S * 40, 20, "ÉTAGE BAS — noyé (se lester)", 11, "#fff", "middle", "700")
    f.rect(X(0), Z(34), S * 10, S * 12, WET, "#9fd0ee", 1.2, .6)
    f.label(X(0), Z(31), "écluse B", 8, "start")
    f.rect(X(10), Z(38), S * 26, S * 4, WET, "#9fd0ee", 1.2, .6)
    for i in range(3):
        f.add(s_arrow(X(34 - i * 8), Z(40), X(26 - i * 8), Z(40), CYAN, 3.2))
    f.label(X(4), Z(49), "B1 · collecteur : torrent 10 b/s", 8.5, "start")
    f.rect(X(36), Z(14), S * 40, S * 52, WET, "#9fd0ee", 1.2, .6)
    for (cx, cz) in ((46, 24), (66, 24), (46, 56), (66, 56)):
        f.add(s_boiler(X(cx), Z(cz), 17))
    f.add(s_arrow(X(50), Z(40), X(50), Z(30), CYAN, 3))
    f.add(s_arrow(X(58), Z(40), X(58), Z(50), CYAN, 3))
    f.add(s_arrow(X(40), Z(46), X(46), Z(46), CYAN, 3))
    f.add(s_plate(X(44), Z(18), 1))
    f.add(s_plate(X(70), Z(44), 2))
    f.add(s_plate(X(52), Z(62), 3))
    for (cx, cz) in ((60, 34), (48, 50), (68, 60), (70, 18)):
        f.add(s_cross(X(cx), Z(cz), 5.5))
    f.circ(X(74), Z(40), S * 3, "#9fe0ff", "#fff", 1.4, .9)
    for k in range(6):
        f.circ(X(73 + (k % 3)), Z(37 + k * 1.2), 1.6, "#fff")
    f.label(X(56), Z(70) + 8, "B2 · salle des chaudières", 8.5)
    f.label(X(74), Z(47) + 6, "colonne de bulles ↑", 7.5)
    X2 = lambda x: 372 + S * x
    f.text(372 + S * 40, 20, "ÉTAGE HAUT — sec (grappin)", 11, "#fff", "middle", "700")
    f.rect(X2(0), Z(8), S * 80, S * 60, DRY, "#2a2a2a", 1.2)
    f.rect(X2(20), Z(22), S * 38, S * 30, WET, "#9fd0ee", 1.4, .85)
    f.label(X2(39), Z(37), "bassin de trempe (eau)", 8)
    for (px, pz, pw, ph) in ((0, 36, 20, 6), (24, 14, 12, 6), (42, 14, 10, 6), (62, 22, 12, 6), (62, 42, 12, 6), (24, 52, 14, 6)):
        f.rect(X2(px), Z(pz), S * pw, S * ph, "#dcd0a8", "#222", 1)
    for (ax, az) in ((22, 12), (38, 12), (56, 16), (60, 46), (40, 54), (18, 54)):
        f.add(s_anchor(X2(ax), Z(az)))
    for (vx, vz) in ((30, 18), (66, 26), (66, 46)):
        f.add(s_vent(X2(vx), Z(vz)))
    for (cx, cz) in ((34, 10), (52, 56), (68, 34)):
        f.add(s_cross(X2(cx), Z(cz), 5.5))
    f.add(s_gear(X2(72), Z(60), 17))
    f.add(s_lever(X2(72), Z(60), "B", 8))
    f.add(s_chest(X2(8), Z(52)))
    f.add(s_arrow(X2(2), Z(39), X2(20), Z(39), "#fff", 2, "5 4"))
    f.label(X2(2), Z(33), "arrivée par les bulles", 7.5, "start")
    f.label(X2(72), Z(70) + 8, "Pompe B", 8.5)
    f.label(X2(28), Z(70) + 8, "B3 · halle haute : vapeur, 3 blazes", 8)
    f.add(s_north(700, 380))
    f.scale(12, 400, S, 10)
    return f.render()


# ================================================================ FIGURE 6 : secteur C
def fig_secteur_c():
    SY, SX = 8.6, 7.2
    f = Fig(720, 520)
    Y = lambda y: 500 - SY * (y - 44)
    X = lambda x: 30 + SX * x
    f.rect(X(8), Y(95), SX * 36, SY * 51, "#0f1a25", "#9fd0ee", 1.4)
    f.rect(X(8), Y(82), SX * 36, SY * 38, WET, "none", 1, .6)
    f.rect(X(8), Y(82), SX * 36, SY * 38, "url(#hatchW)")
    f.rect(X(0), Y(54), SX * 8, SY * 6, WET, "#fff", 1.2, .8)
    f.label(X(0), Y(47), "écluse C (48–54)", 8, "start")
    for (ty, name) in ((54, "T1"), (68, "T2"), (82, "T3")):
        f.rect(X(8), Y(ty), SX * 36, 5, "#79808a", "#222", 1)
        f.rect(X(8), Y(ty), SX * 14, 5, "#0f1a25")
        f.add(s_turbine(X(33), Y(ty) - 14, 12))
        f.text(X(39), Y(ty) - 18, name, 10, "#fff", "start", "700")
    f.rect(X(8), Y(68) - 3, SX * 14, 11, "url(#hatchT)", "#d4a84c", 1.2)
    f.rect(X(8), Y(82) - 3, SX * 14, 11, "url(#hatchT)", "#d4a84c", 1.2)
    f.add(s_arrow(X(15), Y(54) + 3, X(15), Y(66) - 3, "#ffffff", 2, "4 3"))
    for (y0, y1) in ((57, 66), (71, 80)):
        for dx in (26, 32, 38):
            f.add(s_arrow(X(dx), Y(y0), X(dx), Y(y1), CYAN, 2.4))
    for (ex, ey) in ((16, 60), (40, 60), (14, 74), (40, 75)):
        f.add(s_cross(X(ex), Y(ey), 5.5))
    f.add(s_mini(X(27), Y(75)))
    f.rect(X(8), Y(95), SX * 36, SY * 9, "#e9e2cb", "#222", 1)
    f.add(s_gear(X(26), Y(90.5), 15))
    f.add(s_lever(X(26), Y(90.5), "C", 8))
    f.line(X(8), Y(82), X(44), Y(82), "#fff", 1.4, "6 3")
    # annotations à droite, alignées sur chaque étage (le 1er texte au-dessus du 2e)
    def note(y, lines):
        for k, t in enumerate(lines):
            f.label(X(46.5), Y(y) + k * 14, t, 8.5, "start")
    note(60, ["T1 · 3 coups de tourbillon (10 s)", "→ courant ascendant"])
    note(72, ["T2 · même geste + grille de tartre", "mini-boss : « Gardien du rotor »"])
    note(84, ["T3 · dernière turbine + grille", "surface du puits : 82"])
    note(93, ["Pompe C · salle sèche (86–95)"])
    f.text(X(26), 516, "Puits des turbines · coupe · Ø 36 · de 48 à 95", 8.5, "#cfd6dd", "middle")
    return f.render()


# ================================================================ FIGURE 7 : Cheminée + Poste
def fig_cheminee():
    S = 8.0
    SY = 7.4
    f = Fig(720, 590)
    X = lambda x: 20 + S * x
    Y = lambda y: 562 - SY * (y - 28)
    f.rect(X(0), Y(96), S * 12, SY * 66, "#0f1a25", "#9fd0ee", 1.2)
    f.rect(X(0), Y(42), S * 12, SY * 14, WET, "none", 1, .65)
    f.line(X(0), Y(42), X(12), Y(42), "#fff", 1.4, "6 3")
    f.text(X(6), Y(40), "Cuve (Bas)", 8.5, "#cfe9f8", "middle", "700")
    f.rect(X(12), Y(36), S * 24, SY * 6, WET, "#9fd0ee", 1.2, .65)
    for gx in (18, 25, 32):
        f.add(s_gate(X(gx), Y(33), S * 5.2, True))
    f.add(s_arrow(X(13), Y(33), X(34), Y(33), "#fff", 2, "5 4"))
    f.label(X(24), Y(28) + 14, "① 24 blocs · 3 vannes · 3,0 s", 8.5)
    f.rect(X(36), Y(84), S * 14, SY * 54, "#0f1a25", "#9fd0ee", 1.2)
    f.rect(X(36), Y(76), S * 14, SY * 46, WET, "none", 1, .65)
    f.line(X(36), Y(76), X(50), Y(76), "#fff", 1.4, "6 3")
    f.text(X(51.5), Y(76) + 3, "surface 76", 8, "#cfe9f8", "start")
    f.path(f"M{X(36):.1f},{Y(36):.1f} L{X(50):.1f},{Y(40):.1f} L{X(36):.1f},{Y(45):.1f} L{X(50):.1f},{Y(49):.1f} L{X(36):.1f},{Y(54):.1f}", "#dcd0a8", 4)
    for yy in (40, 45, 49):
        f.add(s_arrow(X(43), Y(yy) - 8, X(43), Y(yy) + 6, CYAN, 2.4))
    f.label(X(52), Y(46), "② rampe, torrent descendant", 8.5, "start")
    f.label(X(52), Y(46) + 15, "10 b/s : on monte lesté", 8, "start")
    f.rect(X(36), Y(58) - 3, S * 14, 12, "url(#hatchT)", "#d4a84c", 1.2)
    f.add(s_turbine(X(43), Y(63), 13))
    for dx in (39, 43, 47):
        f.add(s_arrow(X(dx), Y(65), X(dx), Y(74), CYAN, 2.4))
    f.label(X(52), Y(66), "③ grille de tartre + turbine :", 8.5, "start")
    f.label(X(52), Y(66) + 15, "tourbillon → courant ascendant", 8, "start")
    f.rect(X(0), Y(96), S * 50, SY * 12, "#e9e2cb", "#222", 1)
    f.rect(X(36), Y(84), S * 14, 6, "#cbbd96")
    f.label(X(52), Y(80), "④ bond hors de l'eau (≈ 8 blocs)", 8.5, "start")
    f.add(s_arrow(X(44), Y(76), X(43), Y(83), "#fff", 2.4, "4 3"))
    for (ax, ay) in ((31, 93), (21, 93), (12, 93)):
        f.add(s_anchor(X(ax), Y(ay)))
    f.rect(X(26), Y(84), S * 10, 6, "#cbbd96")
    f.rect(X(15), Y(84), S * 6, 6, "#cbbd96")
    f.rect(X(0), Y(84), S * 8, 6, "#cbbd96")
    f.add(s_arrow(X(34), Y(90), X(22), Y(90), "#fff", 1.8, "4 3"))
    f.label(X(52), Y(91), "3 ancrages · trous de 6 (chute = eau)", 8, "start")
    f.add(s_lever(X(2), Y(87), "!", 8))
    f.label(X(8), Y(91), "POSTE", 8.5)
    return f.render()


# ================================================================ FIGURE 8 : Réservoir
def fig_reservoir():
    S = 5.2
    f = Fig(720, 580)
    cx, cz = 262, 270
    f.circ(cx, cz, S * 44, DEEP, "#9fd0ee", 2, .95)
    f.circ(cx, cz, S * 44, "url(#hatchW)")
    pil = []
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        pil.append((28 * math.cos(a), 28 * math.sin(a)))
    for k in range(3):
        a = k * 2 * math.pi / 3 + math.pi / 2
        pil.append((11 * math.cos(a), 11 * math.sin(a)))
    for (px, pz) in pil:
        f.circ(cx + S * px, cz + S * pz, S * 2.2, "#b9b3a2", "#222", 1.2)
    for k in range(4):
        a = k * math.pi / 2
        bx, bz = cx + S * 35 * math.cos(a), cz + S * 35 * math.sin(a)
        f.circ(bx, bz, S * 3, "#0b1b2a", "#fff", 1.4)
        f.path(f"M{bx-7:.1f},{bz:.1f} a7,7 0 1,1 7,7", "#9fd0ee", 1.4)
    f.circ(cx, cz, S * 6, "none", "#ffffff", 2, 1, "5 3")
    f.add(s_arrow(cx, cz - S * 18, cx, cz - S * 7, "#ffffff", 3))
    f.label(cx, cz - S * 19, "arrivée du tourbillon (plafond)", 8.5)
    f.circ(cx, cz, S * 14, "none", "#e1bee7", 1.4, 1, "6 4")
    pts = []
    for i in range(60):
        t = i / 59 * 2 * math.pi * 0.85 + 0.3
        r = S * (14 + 1.5 * math.sin(i / 3))
        pts.append((cx + r * math.cos(t), cz + r * math.sin(t)))
    f.path("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts), "#c7a7e8", 8, op=.8)
    f.add(s_boss(pts[-1][0], pts[-1][1], 12))
    f.label(cx, cz + S * 21, "Léviathan : tourne à ~14 blocs du joueur", 8.5)
    f.rect(cx - S * 4, cz + S * 44 - 2, S * 8, S * 8, "#9fd0ee", "#222", 1)
    f.add(s_arrow(cx, cz + S * 51, cx, cz + S * 59, "#ffffff", 3))
    f.label(cx, cz + S * 61 + 4, "Exutoire → surface (lac)", 8.5)
    f.add(s_chest(cx - S * 20, cz + S * 26))
    f.add(s_north(690, 38))
    f.scale(540, 545, S, 10)
    f.text(540, 535, "Ø 88 · hauteur 26 · noyé", 8.5, "#cfd6dd", "start")
    lx = 540
    items = [("pilier en ruine (11) : abri du rayon", lambda x, y: f.circ(x, y, 7, "#b9b3a2", "#222", 1.2)),
             ("bonde de fond (4, phase 2)", lambda x, y: f.circ(x, y, 7, "#0b1b2a", "#fff", 1.4)),
             ("coffre (jaune)", lambda x, y: f.add(s_chest(x, y)))]
    yy = 140
    for t, fn in items:
        fn(lx + 8, yy)
        f.text(lx + 22, yy + 3, t, 8.5, "#e6edf2", "start")
        yy += 24
    return f.render()
