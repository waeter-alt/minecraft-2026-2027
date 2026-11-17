# [claude-build] Apercu d'un modele d'objet Minecraft (JSON a cubes) sans lancer le jeu : planche de 3 vues.
#   python apercu_modele.py <modele.json> <sortie.png> [--assets <dossier assets/<namespace>>] [--icone <png 16x16>]
# Chaque face est decoupee en un petit quadrilatere par pixel de texture, projete en vue orthographique et trie par
# profondeur. Les conventions d'UV et de rotation sont celles de Minecraft ; le modele est montre brut (sans les
# reglages d'affichage en main).
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

LUMIERE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMALES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def coins_face(face, de, a):
    """Les 4 coins dans l'ordre (haut-gauche, haut-droite, bas-droite, bas-gauche) de la texture, comme Minecraft."""
    x0, y0, z0 = de
    x1, y1, z1 = a
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def tourner(p, rot):
    if not rot:
        return p
    ox, oy, oz = rot["origin"]
    t = math.radians(rot["angle"])
    c, s = math.cos(t), math.sin(t)
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


def vue(p, lacet, tangage):
    """Projection orthographique : renvoie (x ecran, y ecran, profondeur vers l'observateur)."""
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    a, b = math.radians(lacet), math.radians(tangage)
    x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
    y, z = y * math.cos(b) - z * math.sin(b), y * math.sin(b) + z * math.cos(b)
    return x, -y, z


def rendu(modele, textures, lacet, tangage, taille=320):
    echelle = taille / 20.0
    img = Image.new("RGBA", (taille, taille), (200, 200, 200, 255))
    quads = []
    for el in modele["elements"]:
        rot = el.get("rotation")
        for face, info in el["faces"].items():
            tex = textures[info["texture"].lstrip("#")]
            tw, th = tex.size
            u0, v0, u1, v1 = (c * tw / 16 if i % 2 == 0 else c * th / 16 for i, c in enumerate(info["uv"]))
            nu, nv = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
            coins = [tourner(c, rot) for c in coins_face(face, el["from"], el["to"])]
            n = tourner(NORMALES[face], dict(rot, origin=[0, 0, 0]) if rot else None)
            nz = vue((n[0] + 8, n[1] + 8, n[2] + 8), lacet, tangage)[2]
            if nz <= 0:
                continue                                    # face tournee vers l'arriere
            for j in range(nv):
                for i in range(nu):
                    pts = []
                    for di, dj in ((0, 0), (1, 0), (1, 1), (0, 1)):
                        fu, fv = (i + di) / nu, (j + dj) / nv
                        haut = [coins[0][k] + (coins[1][k] - coins[0][k]) * fu for k in range(3)]
                        bas = [coins[3][k] + (coins[2][k] - coins[3][k]) * fu for k in range(3)]
                        pts.append(vue([haut[k] + (bas[k] - haut[k]) * fv for k in range(3)], lacet, tangage))
                    tu = int(min(u0, u1) + (i + 0.5) * abs(u1 - u0) / nu) if u1 >= u0 else int(u0 - (i + 0.5) * abs(u1 - u0) / nu)
                    tv = int(min(v0, v1) + (j + 0.5) * abs(v1 - v0) / nv) if v1 >= v0 else int(v0 - (j + 0.5) * abs(v1 - v0) / nv)
                    r, g, b, al = tex.getpixel((min(tw - 1, tu), min(th - 1, tv)))
                    if al == 0:
                        continue
                    k = LUMIERE[face]
                    prof = sum(p[2] for p in pts) / 4
                    quads.append((prof, [(taille / 2 + p[0] * echelle, taille / 2 + p[1] * echelle) for p in pts],
                                  (int(r * k), int(g * k), int(b * k), 255)))
    d = ImageDraw.Draw(img)
    for _, pts, couleur in sorted(quads, key=lambda q: q[0]):
        d.polygon(pts, fill=couleur, outline=couleur)
    return img


def charger(modele_json, assets):
    modele = json.loads(Path(modele_json).read_text(encoding="utf-8"))
    textures = {}
    for cle, ident in modele.get("textures", {}).items():
        chemin = Path(assets) / "textures" / (ident.split(":", 1)[1] + ".png")
        textures[cle] = Image.open(chemin).convert("RGBA")
    return modele, textures


def planche(modeles, sortie, icone=None):
    vues = [(-35, 25), (-90, 0), (0, 0)]
    largeur = 320 * len(vues) + (288 if icone else 0)
    img = Image.new("RGBA", (largeur, 320 * len(modeles)), (200, 200, 200, 255))
    for ligne, (modele, textures) in enumerate(modeles):
        for col, (lacet, tangage) in enumerate(vues):
            img.paste(rendu(modele, textures, lacet, tangage), (col * 320, ligne * 320))
    if icone:
        ic = Image.open(icone).convert("RGBA").resize((256, 256), Image.NEAREST)
        img.alpha_composite(ic, (320 * len(vues) + 16, 32))
    img.save(sortie)


def main():
    args = sys.argv[1:]
    assets = args[args.index("--assets") + 1] if "--assets" in args else None
    icone = args[args.index("--icone") + 1] if "--icone" in args else None
    fichiers = [a for i, a in enumerate(args) if not a.startswith("--") and (i == 0 or args[i - 1] not in ("--assets", "--icone"))]
    *modeles, sortie = fichiers
    if assets is None:
        assets = Path(modeles[0]).resolve().parents[2]
    planche([charger(m, assets) for m in modeles], sortie, icone)
    print("apercu ecrit :", sortie)


if __name__ == "__main__":
    main()
