#!/usr/bin/env python3
"""Aperçu hors-jeu des visuels générés : planche d'icônes, blocs en vue isométrique, skins de troupes.
Approximation (pas le vrai rendu de Minecraft) : sert à repérer les erreurs grossières avant de lancer le jeu."""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/interregne"
OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
S = 9  # pixels par unité de bloc


def tex_path(ref):
    ns, p = ref.split(":")
    return ROOT / "textures" / f"{p}.png"


def load_model(name):
    m = json.loads((ROOT / "models/block" / f"{name}.json").read_text())
    return m


def iso(x, y, z):
    return ((x - z) * 0.866 * S, ((x + z) * 0.5 - y) * S)


def render_block(name, W=420, H=420):
    m = load_model(name)
    tex = {k: Image.open(tex_path(v)).convert("RGBA") for k, v in m["textures"].items()}
    img = Image.new("RGBA", (W, H), (70, 74, 82, 255))
    ox, oy = W // 2, H // 2
    els = sorted(m["elements"], key=lambda e: sum(e["from"]) + sum(e["to"]))
    for e in els:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        quads = {
            "up": ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2), 1.0),
            "south": ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2), 0.82),
            "east": ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2), 0.68),
        }
        for face, (o, u, v, br) in quads.items():
            f = e["faces"].get(face)
            if not f:
                continue
            t = tex[f["texture"][1:]]
            u0, v0, u1, v1 = f["uv"]
            # coordonnées dans l'image de la face : (u,v) en pixels de texture (16 = tout le bloc)
            po, pu, pv = iso(*o), iso(*u), iso(*v)
            du = (pu[0] - po[0], pu[1] - po[1])
            dv = (pv[0] - po[0], pv[1] - po[1])
            # orientation : south/east lisent la texture de gauche→droite selon l'axe horizontal visible
            if face == "east":
                u_vec, o_uv = (-1, 0), (u1, v0)
            else:
                u_vec, o_uv = (1, 0), (u0, v0)
            w = abs(u1 - u0)
            h = abs(v1 - v0)
            if w == 0 or h == 0:
                continue
            # rasterise le parallélogramme par échantillonnage
            steps_u, steps_v = max(2, int(w * 4)), max(2, int(h * 4))
            for iu in range(steps_u):
                for iv in range(steps_v):
                    a, b = (iu + 0.5) / steps_u, (iv + 0.5) / steps_v
                    tu = u0 + a * (u1 - u0) if face != "east" else u1 - a * (u1 - u0)
                    tv = v0 + b * (v1 - v0)
                    px = t.getpixel((int(tu) % 16 if t.width == 16 else int(tu * t.width / 16) % t.width, int(tv) % 16 if t.height == 16 else int(tv * t.height / 16) % t.height))
                    if px[3] == 0:
                        continue
                    sx = ox + po[0] + a * du[0] + b * dv[0]
                    sy = oy + po[1] + a * du[1] + b * dv[1]
                    c = tuple(int(px[i] * br) for i in range(3))
                    ImageDraw.Draw(img).rectangle([sx - 0.8, sy - 0.8, sx + 0.8, sy + 0.8], fill=c)
    return img


def sheet_icons():
    names = [("item", n) for n in ("couronne", "lingot_imperial", "bourse", "sac_de_couronnes", "coffret_imperial", "tresor_du_dragon")] + \
            [("gui", n) for n in ("hud_coin", "hud_purse", "hud_bank", "hud_region", "hud_influence")]
    z = 10
    im = Image.new("RGBA", (len(names) * (16 * z + 10), 16 * z + 20), (70, 74, 82, 255))
    d = ImageDraw.Draw(im)
    for i, (k, n) in enumerate(names):
        t = Image.open(ROOT / "textures" / k / f"{n}.png").convert("RGBA").resize((16 * z, 16 * z), Image.NEAREST)
        im.paste(t, (i * (16 * z + 10) + 5, 5), t)
        d.text((i * (16 * z + 10) + 5, 16 * z + 8), n, fill=(255, 255, 255))
    im.save(OUT / "icones.png")


if __name__ == "__main__":
    sheet_icons()
    names = ["poste_commercial", "guichet_imperial", "banque_regionale", "depot_regional",
             "caisse_ble", "caisse_bois", "caisse_brique", "caisse_minerai", "caisse_pierre"]
    tiles = [render_block(n, 340, 340) for n in names]
    cols = 3
    sheet = Image.new("RGBA", (cols * 340, 340 * ((len(tiles) + cols - 1) // cols)), (0, 0, 0, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 340, (i // cols) * 340))
    sheet.save(OUT / "blocs.png")
    print("ok", OUT)
