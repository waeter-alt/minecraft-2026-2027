# [claude-build] Apercu de modeles de bloc JSON (ceux que le jeu charge), sans lancer le jeu : chaque texel de chaque face, rotations des
# cubes, faces cachees retirees, tri par profondeur, eclairage simple, cubes lumineux (neoforge_data) non ombres. Vue 3/4 avant-gauche.
#   python apercu_bloc.py <sortie.png> <modele>[+<modele>...] [...]       (modele : chemin sous models/, ex. block/station/cadran)
# « a+b » dessine plusieurs modeles ensemble (corps de la pompe + son volant) ; chaque argument est une case de la planche.
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[2] / "atelier/donjon/arene/src/main/resources/assets/grappin"
CASE, SUR, FOND = 380, 3, (214, 220, 224)


def charger_modele(nom):
    m = json.loads((ASSETS / "models" / f"{nom}.json").read_text(encoding="utf-8"))
    tex = {}
    for k, v in m.get("textures", {}).items():
        tex[k] = v
    return m["elements"], tex


def image(ref, tex, cache):
    while ref.startswith("#"):
        ref = tex[ref[1:]]
    if ref not in cache:
        img = Image.open(ASSETS / "textures" / (ref.split(":", 1)[1] + ".png")).convert("RGBA")
        if img.height > img.width:
            img = img.crop((0, 0, img.width, img.width))                       # texture animee : premiere image
        cache[ref] = img
    return cache[ref]


def coins(face, de, a):
    x0, y0, z0 = de
    x1, y1, z1 = a
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[face]


def tourner(p, rot):
    if not rot:
        return p
    o, ang, axe = rot["origin"], math.radians(rot["angle"]), rot["axis"]
    x, y, z = (p[i] - o[i] for i in range(3))
    c, s = math.cos(ang), math.sin(ang)
    if axe == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axe == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def vue(p, lacet, tangage):
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    cl, sl = math.cos(math.radians(lacet)), math.sin(math.radians(lacet))
    x, z = x * cl + z * sl, -x * sl + z * cl
    ct, st = math.cos(math.radians(tangage)), math.sin(math.radians(tangage))
    y, z = y * ct - z * st, y * st + z * ct
    return (x, y, z)


def rendu(noms, lacet=-35, tangage=-22):
    cache, quads, pts_tous = {}, [], []
    for nom in noms:
        elements, tex = charger_modele(nom)
        for e in elements:
            lumineux = "neoforge_data" in e
            for face, f in e["faces"].items():
                img = image(f["texture"], tex, cache)
                c = [vue(tourner(p, e.get("rotation")), lacet, tangage) for p in coins(face, e["from"], e["to"])]
                u = [c[1][i] - c[0][i] for i in range(3)]
                v = [c[3][i] - c[0][i] for i in range(3)]
                n = [v[1] * u[2] - v[2] * u[1], v[2] * u[0] - v[0] * u[2], v[0] * u[1] - v[1] * u[0]]
                ln = math.sqrt(sum(k * k for k in n)) or 1
                n = [k / ln for k in n]
                if n[2] > -1e-6:                                                  # la camera regarde vers +z : face de dos
                    continue
                lum = 1.0 if lumineux else 0.6 + 0.4 * max(0.0, n[1] * 0.6 - n[2] * 0.5 + n[0] * 0.2)
                u0, v0, u1, v1 = f["uv"]
                k = img.width / 16
                nw, nh = max(1, round(abs(u1 - u0) * k)), max(1, round(abs(v1 - v0) * k))
                px = img.load()
                for j in range(nh):
                    for i in range(nw):
                        tx = int((u0 + (u1 - u0) * (i + 0.5) / nw) * k)
                        ty = int((v0 + (v1 - v0) * (j + 0.5) / nh) * k)
                        r, g, b, al = px[min(img.width - 1, max(0, tx)), min(img.height - 1, max(0, ty))]
                        if al == 0:
                            continue
                        q = []
                        for s, t in ((i / nw, j / nh), ((i + 1) / nw, j / nh), ((i + 1) / nw, (j + 1) / nh), (i / nw, (j + 1) / nh)):
                            hautp = [c[0][m] + (c[1][m] - c[0][m]) * s for m in range(3)]
                            basp = [c[3][m] + (c[2][m] - c[3][m]) * s for m in range(3)]
                            q.append([hautp[m] + (basp[m] - hautp[m]) * t for m in range(3)])
                        pts_tous.extend(q)
                        quads.append((sum(p[2] for p in q) / 4, q, (int(r * lum), int(g * lum), int(b * lum))))
    xs, ys = [p[0] for p in pts_tous], [p[1] for p in pts_tous]
    echelle = 0.86 * CASE / max(max(xs) - min(xs), max(ys) - min(ys))
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    img = Image.new("RGB", (CASE * SUR, CASE * SUR), FOND)
    d = ImageDraw.Draw(img)
    for _, q, coul in sorted(quads, key=lambda t: -t[0]):
        d.polygon([(SUR * (CASE / 2 + (p[0] - cx) * echelle), SUR * (CASE / 2 - (p[1] - cy) * echelle)) for p in q], fill=coul)
    return img.resize((CASE, CASE), Image.LANCZOS)


def main():
    sortie, cases = sys.argv[1], sys.argv[2:]
    planche = Image.new("RGB", (CASE * len(cases), CASE + 24), FOND)
    for k, c in enumerate(cases):
        planche.paste(rendu(c.split("+")), (k * CASE, 24))
        ImageDraw.Draw(planche).text((k * CASE + 8, 6), c.replace("block/station/", ""), fill=(0, 0, 0))
    planche.save(sortie)
    print("apercu ecrit :", sortie)


if __name__ == "__main__":
    main()
