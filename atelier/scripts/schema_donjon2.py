# [claude-build] Schema (vue de dessus) du plan du donjon n°1 refait : sections, sens du parcours, salles cachees. -> exports/donjon_plan_schema.png
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
R = Path(__file__).resolve().parent.parent
s_ = 5
W, H = 150 * s_ + 300, 170 * s_ + 20
img = Image.new("RGB", (W, H), (24, 28, 26))
d = ImageDraw.Draw(img)
f = ImageFont.truetype("arial.ttf", 14)
fp = ImageFont.truetype("arial.ttf", 12)
SECTIONS = [(1, "Seuil des Racines", (0, 72, 22, 92), (110, 150, 95)), (2, "Galerie des Echos", (22, 60, 55, 100), (120, 140, 100)),
            (3, "Gouffre de l'Arbre-Mere", (55, 40, 112, 122), (70, 120, 70)), (4, "La Cascade", (58, 5, 110, 40), (80, 130, 160)),
            (5, "Jardin des Symboles", (110, 8, 146, 52), (150, 130, 80)), (6, "Cle de l'Arbre", (114, 55, 146, 82), (160, 110, 70)),
            (7, "Salle des Lianes", (110, 85, 146, 126), (130, 90, 90)), (8, "Ponts Brises", (60, 126, 110, 162), (120, 100, 110)),
            (9, "Ascension des Racines", (22, 118, 58, 162), (90, 110, 130)), (10, "Salle du Gardien", (2, 100, 22, 165), (170, 150, 90))]
for n, nom, (x0, z0, x1, z1), c in SECTIONS:
    d.rectangle([x0 * s_, z0 * s_ + 10, x1 * s_, z1 * s_ + 10], fill=c, outline=(230, 230, 230))
    d.text((x0 * s_ + 6, z0 * s_ + 16), f"{n}. {nom}", fill=(255, 255, 255), font=f, stroke_width=2, stroke_fill=(0, 0, 0))
ordre = [1, 2, 3, 4, 5, 6, 5, 7, 8, 9, 10]
centres = {n: ((x0 + x1) / 2 * s_, (z0 + z1) / 2 * s_ + 10) for n, _, (x0, z0, x1, z1), _ in SECTIONS}
for a, b in zip(ordre, ordre[1:]):
    (xa, za), (xb, zb) = centres[a], centres[b]
    d.line([(xa, za), (xb, zb)], fill=(255, 220, 90), width=4)
CACHEES = [(8, 76), (30, 64), (60, 52), (80, 8), (140, 14), (140, 70), (128, 92), (26, 150)]
for i, (x, z) in enumerate(CACHEES, 1):
    d.ellipse([x * s_ - 9, z * s_ + 1, x * s_ + 9, z * s_ + 19], fill=(200, 60, 160), outline=(255, 255, 255))
    d.text((x * s_ - 4, z * s_ + 3), str(i), fill=(255, 255, 255), font=fp)
leg = ["Plan du donjon refait (vue de dessus)", "nord en haut, ouest a gauche", "", "jaune : sens du parcours", "(5 -> 6 -> 5 : aller chercher la cle)",
       "rose : salles cachees 1 a 8", "", "Total estime : ~31 min"]
for i, l_ in enumerate(leg):
    d.text((150 * s_ + 14, 14 + 20 * i), l_, fill=(230, 230, 230), font=f)
img.save(R / "exports/donjon_plan_schema.png")
print("schema ecrit")
