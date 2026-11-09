# [claude-build] Dessine le plan du donjon du grappin (vue de dessus, hauteur des iles, trace prevu) et une coupe vue du sud.
# Usage : plan_donjon.py   -> exports/donjon_grappin_plan.png
import json
from pathlib import Path

import nbtlib
from PIL import Image, ImageDraw, ImageFont

R = Path(__file__).resolve().parent.parent
f = nbtlib.load(R / "exports/donjon_grappin.nbt")
conf = json.load(open(R / "exports/donjon_grappin_cibles.json", encoding="utf-8"))
SX, SY, SZ = (int(v) for v in f["size"])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
haut = {}
for b in f["blocks"]:
    x, y, z = (int(v) for v in b["pos"])
    n = pal[int(b["state"])]
    if n in ("air", "light", "water", "cave_vines", "cave_vines_plant", "glow_lichen"):
        continue
    if y < 90:
        haut[(x, z)] = max(haut.get((x, z), 0), y)
s_ = 7
img = Image.new("RGB", (SX * s_ + 260, SZ * s_), (25, 30, 28))
dr = ImageDraw.Draw(img)
pol = ImageFont.truetype("arial.ttf", 13)
for (x, z), y in haut.items():
    v = int(40 + 180 * y / 90)
    dr.rectangle([x * s_, z * s_, x * s_ + s_ - 1, z * s_ + s_ - 1], fill=(v // 2, v, v // 2))
ILES = conf["iles"]
ordre = conf["ordre"][:-1]
centres = {}
for n, d in ILES.items():
    xs = [c[0] for c in d["cases"]]
    zs = [c[1] for c in d["cases"]]
    centres[n] = (sum(xs) / len(xs), sum(zs) / len(zs))
for a, b in zip(ordre, ordre[1:]):
    if a.startswith("C") and b.startswith("H"):
        continue
    (xa, za), (xb, zb) = centres[a], centres["P4"] if b == "H1" else centres[b]
for chemin in (["depart", "P1", "P2", "P3", "P4", "C1", "C2", "C3", "J"], ["P4", "H1", "H2", "H3", "J"], ["J", "RA", "R1", "R2", "A1", "F"]):
    for a, b in zip(chemin, chemin[1:]):
        (xa, za), (xb, zb) = centres[a], centres[b]
        dr.line([(xa * s_ + 3, za * s_ + 3), (xb * s_ + 3, zb * s_ + 3)], fill=(240, 190, 80) if chemin[1] != "H1" else (120, 200, 255), width=3)
for n, (cx, cz) in centres.items():
    dr.text((cx * s_ + 8, cz * s_ - 6), f"{n} (Y{ILES[n]['yf'] - 61 + 1})", fill=(255, 255, 255), font=pol, stroke_width=2, stroke_fill=(0, 0, 0))
leg = ["Donjon du grappin, vu de dessus", "(nord en haut ; plus clair = plus haut)", "", "orange : voie basse (cascade)", "bleu : voie haute (canopee)",
       "Y = hauteur ou l'on se tient", "", "Entree : escalier a l'ouest", "Salle du Gardien : au sud", "(derriere F)"]
for i, l_ in enumerate(leg):
    dr.text((SX * s_ + 12, 12 + 18 * i), l_, fill=(230, 230, 230), font=pol)
img.save(R / "exports/donjon_grappin_plan.png")
print("plan ecrit")
