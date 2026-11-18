# [claude-build] Apercu d'un modele Blockbench d'entite (.bbmodel, os + box UV) sans lancer le jeu, eventuellement pose a un instant
# d'une animation. Une face = un quadrilatere de la couleur moyenne de sa zone de texture (un modele de 170 cubes sur une texture
# 512 x 512 serait trop lourd pixel par pixel). Vues : face, 3/4 avant, dos.
#   python apercu_bbmodel.py <modele.bbmodel> <sortie.png> [--anim <nom> --temps <s> [<s> ...]]
# Avec plusieurs temps : une ligne de vues par instant.
import base64
import io
import json
import math
import sys

from PIL import Image, ImageDraw

VUES = [("face", 0, 8), ("3/4", 35, 15), ("dos", 180, 8)]
CASE = 360


def rot_mat(rx, ry, rz):
    """Matrice de rotation d'Euler (degres), ordre de Blockbench et de Minecraft : X puis Y puis Z."""
    ax, ay, az = (math.radians(v) for v in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(mz, mul(my, mx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def app(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def valeur(keyframes, canal, t):
    """Valeur d'un canal a l'instant t (interpolation lineaire entre images cles), ou None s'il n'est pas anime."""
    ks = sorted((k for k in keyframes if k["channel"] == canal), key=lambda k: k["time"])
    if not ks:
        return None

    def vec(k):
        p = k["data_points"][0]
        return [float(p[a]) for a in "xyz"]
    if t <= ks[0]["time"]:
        return vec(ks[0])
    for a, b in zip(ks, ks[1:]):
        if a["time"] <= t <= b["time"]:
            f = (t - a["time"]) / max(1e-9, b["time"] - a["time"])
            va, vb = vec(a), vec(b)
            return [va[i] + (vb[i] - va[i]) * f for i in range(3)]
    return vec(ks[-1])


def couleurs_faces(modele):
    src = modele["textures"][0]["source"].split(",", 1)[1]
    tex = Image.open(io.BytesIO(base64.b64decode(src))).convert("RGBA")
    cache = {}

    def couleur(uv):
        cle = tuple(round(v, 2) for v in uv)
        if cle not in cache:
            x0, y0, x1, y1 = min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3])
            box = (int(x0), int(y0), max(int(x0) + 1, int(math.ceil(x1))), max(int(y0) + 1, int(math.ceil(y1))))
            pixels = [p for p in tex.crop(box).get_flattened_data() if p[3] > 0]
            if not pixels:
                cache[cle] = None
                return None
            cache[cle] = tuple(sum(p[i] for p in pixels) // len(pixels) for i in range(3))
        return cache[cle]
    return couleur


def transformations(modele, anim, t):
    """{uuid d'element: fonction point -> point monde} en suivant les os et l'animation."""
    animateurs = {}
    if anim:
        for a in modele["animations"]:
            if a["name"] == anim:
                animateurs = {v["name"]: v["keyframes"] for v in a["animators"].values()}
    resultat = {}

    def visiter(groupe, parent):
        piv = groupe["origin"]
        kf = animateurs.get(groupe["name"], [])
        r = valeur(kf, "rotation", t) or [0, 0, 0]
        base = groupe.get("rotation", [0, 0, 0])
        m = rot_mat(base[0] + r[0], base[1] + r[1], base[2] + r[2])
        d = valeur(kf, "position", t) or [0, 0, 0]

        def f(p, m=m, piv=piv, d=d, parent=parent):
            q = app(m, [p[i] - piv[i] for i in range(3)])
            q = [q[i] + piv[i] + d[i] for i in range(3)]
            return parent(q)
        for c in groupe["children"]:
            if isinstance(c, dict):
                visiter(c, f)
            else:
                resultat[c] = f
    for g in modele["outliner"]:
        visiter(g, lambda p: p)
    return resultat


FACES = {
    "north": ((0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    "east": ((1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    "down": ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
}


def rendu(modele, anim, t, lacet, tangage):
    couleur = couleurs_faces(modele)
    trans = transformations(modele, anim, t)
    vue = mul(rot_mat(tangage, 0, 0), rot_mat(0, lacet, 0))
    polys = []
    for e in modele["elements"]:
        de, a = e["from"], e["to"]
        rc = rot_mat(*e.get("rotation", [0, 0, 0]))
        o = e.get("origin", [0, 0, 0])
        f = trans[e["uuid"]]
        for nom, coins in FACES.items():
            pts = []
            for c in coins:
                p = [de[i] + (a[i] - de[i]) * c[i] for i in range(3)]
                p = app(rc, [p[i] - o[i] for i in range(3)])
                p = f([p[i] + o[i] for i in range(3)])
                pts.append(app(vue, [p[0], p[1] - 120, p[2]]))
            u = [pts[1][i] - pts[0][i] for i in range(3)]
            v = [pts[3][i] - pts[0][i] for i in range(3)]
            n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
            ln = math.sqrt(sum(x * x for x in n)) or 1
            k = 0.55 + 0.45 * abs(n[1] / ln * 0.7 + n[0] / ln * 0.3)
            c = couleur(e["faces"][nom]["uv"])
            if c is None:
                continue                                         # zone transparente de la texture : face invisible en jeu aussi
            r, g, b = c
            prof = sum(p[2] for p in pts) / 4
            polys.append((prof, [(CASE / 2 - p[0] * CASE / 300, CASE / 2 - p[1] * CASE / 300) for p in pts],
                          (int(r * k), int(g * k), int(b * k))))
    img = Image.new("RGB", (CASE, CASE), (205, 205, 205))
    d = ImageDraw.Draw(img)
    for _, pts, c in sorted(polys, key=lambda q: -q[0]):
        d.polygon(pts, fill=c)
    return img


def main():
    args = sys.argv[1:]
    modele = json.load(open(args[0], encoding="utf-8"))
    sortie = args[1]
    anim = args[args.index("--anim") + 1] if "--anim" in args else None
    temps = [0.0]
    if "--temps" in args:
        temps = [float(v) for v in args[args.index("--temps") + 1:] if not v.startswith("--")]
    planche = Image.new("RGB", (CASE * len(VUES), CASE * len(temps)), (205, 205, 205))
    for ligne, t in enumerate(temps):
        for col, (_, lacet, tangage) in enumerate(VUES):
            planche.paste(rendu(modele, anim, t, lacet, tangage), (col * CASE, ligne * CASE))
        ImageDraw.Draw(planche).text((6, ligne * CASE + 6), f"{anim or 'repos'}  t = {t:.2f} s", fill=(0, 0, 0))
    planche.save(sortie)
    print("apercu ecrit :", sortie)


if __name__ == "__main__":
    main()
