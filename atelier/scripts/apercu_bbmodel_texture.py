# [claude-build] Apercu TEXTURE d'un petit modele Blockbench d'entite (.bbmodel, os + box UV), sans lancer le jeu : chaque texel de chaque
# face est dessine (apercu_bbmodel.py ne prend que la couleur moyenne d'une face, trop grossier pour un casier de 16 x 32).
# Faces cachees retirees, tri par profondeur, eclairage simple par orientation, rendu sur-echantillonne.
#   python apercu_bbmodel_texture.py <modele.bbmodel> <sortie.png> [--anim <nom> --temps <s> [<s> ...]] [--textures a.png b.png ...]
# Une colonne par vue (3/4 avant, face), une ligne par instant ; avec --textures : une ligne de plus, le modele avec chaque texture.
import base64
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from apercu_bbmodel import FACES, app, mul, rot_mat, transformations  # noqa: E402

CASE = 420
SUR = 3                                                   # sur-echantillonnage
FOND = (214, 220, 224)
COINS_UV = {"north": "lr", "south": "lr", "east": "lr", "west": "lr", "up": "lr", "down": "lr"}


def texture(modele, chemin=None):
    if chemin:
        return Image.open(chemin).convert("RGBA")
    src = modele["textures"][0]["source"].split(",", 1)[1]
    return Image.open(io.BytesIO(base64.b64decode(src))).convert("RGBA")


def rendu(modele, tex, anim, t, lacet, tangage, echelle):
    trans = transformations(modele, anim, t)
    vue = mul(rot_mat(tangage, 0, 0), rot_mat(0, lacet, 0))
    px = tex.load()
    quads = []
    for e in modele["elements"]:
        de, a = e["from"], e["to"]
        rc = rot_mat(*e.get("rotation", [0, 0, 0]))
        o = e.get("origin", [0, 0, 0])
        f = trans[e["uuid"]]
        centre_y = 16
        for nom, coins in FACES.items():
            pts = []
            for c in coins:
                p = [de[i] + (a[i] - de[i]) * c[i] for i in range(3)]
                p = app(rc, [p[i] - o[i] for i in range(3)])
                p = f([p[i] + o[i] for i in range(3)])
                pts.append(app(vue, [p[0], p[1] - centre_y, p[2]]))
            u = [pts[1][i] - pts[0][i] for i in range(3)]
            v = [pts[3][i] - pts[0][i] for i in range(3)]
            n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
            ln = math.sqrt(sum(x * x for x in n)) or 1
            n = [x / ln for x in n]
            centre = [sum(p[i] for p in pts) / 4 for i in range(3)]
            # Normale sortante : comparee a la direction centre de la boite -> centre de la face.
            cb = [(de[i] + a[i]) / 2 for i in range(3)]
            cb = app(rc, [cb[i] - o[i] for i in range(3)])
            cb = f([cb[i] + o[i] for i in range(3)])
            cb = app(vue, [cb[0], cb[1] - centre_y, cb[2]])
            sortante = [centre[i] - cb[i] for i in range(3)]
            if sum(n[i] * sortante[i] for i in range(3)) < 0:
                n = [-x for x in n]
            if n[2] <= 1e-6:
                continue                                                      # face tournee vers l'arriere : cachee
            lum = 0.62 + 0.38 * max(0.0, n[1] * 0.55 + n[2] * 0.6 - n[0] * 0.25)
            u0, v0, u1, v1 = e["faces"][nom]["uv"]
            nw, nh = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
            for j in range(nh):
                for i in range(nw):
                    tx = int(min(u0, u1) + (i if u1 >= u0 else nw - 1 - i))
                    ty = int(min(v0, v1) + (j if v1 >= v0 else nh - 1 - j))
                    r, g, b, al = px[min(tex.width - 1, tx), min(tex.height - 1, ty)]
                    if al == 0:
                        continue
                    sq = []
                    for (s, tt) in ((i / nw, j / nh), ((i + 1) / nw, j / nh), ((i + 1) / nw, (j + 1) / nh), (i / nw, (j + 1) / nh)):
                        haut = [pts[0][k] + (pts[1][k] - pts[0][k]) * s for k in range(3)]
                        bas = [pts[3][k] + (pts[2][k] - pts[3][k]) * s for k in range(3)]
                        sq.append([haut[k] + (bas[k] - haut[k]) * tt for k in range(3)])
                    prof = sum(p[2] for p in sq) / 4
                    quads.append((prof, [(CASE * SUR / 2 + p[0] * echelle * SUR, CASE * SUR / 2 - p[1] * echelle * SUR) for p in sq],
                                  (int(r * lum), int(g * lum), int(b * lum))))
    img = Image.new("RGB", (CASE * SUR, CASE * SUR), FOND)
    d = ImageDraw.Draw(img)
    for _, pts, c in sorted(quads, key=lambda q: q[0]):
        d.polygon(pts, fill=c)
    return img.resize((CASE, CASE), Image.LANCZOS)


def main():
    args = sys.argv[1:]
    modele = json.load(open(args[0], encoding="utf-8"))
    sortie = args[1]
    anim = args[args.index("--anim") + 1] if "--anim" in args else None
    options = ("--anim", "--temps", "--textures")

    def liste(opt):
        if opt not in args:
            return []
        r = []
        for v in args[args.index(opt) + 1:]:
            if v in options:
                break
            r.append(v)
        return r
    temps = [float(v) for v in liste("--temps")] or [0.0]
    autres = liste("--textures")
    vues = [("3/4 avant", -32, 16), ("face", 0, 0), ("3/4 interieur", 28, 10)]
    echelle = CASE / 44
    lignes = len(temps) + (1 if autres else 0)
    larg = max(len(vues), len(autres))
    planche = Image.new("RGB", (CASE * larg, CASE * lignes), FOND)
    tex = texture(modele)
    for ligne, t in enumerate(temps):
        for col, (nom, lacet, tangage) in enumerate(vues):
            planche.paste(rendu(modele, tex, anim, t, lacet, tangage, echelle), (col * CASE, ligne * CASE))
            ImageDraw.Draw(planche).text((col * CASE + 8, ligne * CASE + 8), f"{nom}  {anim or 'repos'}  t = {t:.2f} s", fill=(0, 0, 0))
    for col, chemin in enumerate(autres):
        planche.paste(rendu(modele, texture(modele, chemin), None, 0, -32, 16, echelle), (col * CASE, len(temps) * CASE))
        ImageDraw.Draw(planche).text((col * CASE + 8, len(temps) * CASE + 8), Path(chemin).stem, fill=(0, 0, 0))
    planche.save(sortie)
    print("apercu ecrit :", sortie)


if __name__ == "__main__":
    main()
