# [claude-build]
"""plan_pdf_donjon_suspendu.py - le dossier de travail du Donjon Suspendu (le donjon 1 actuel) en PDF : un plan par niveau avec les codes couleurs de Luca, la logique de
level design et de game design, les enigmes et leurs solutions, le scenario attendu du joueur, le boss, les cinematiques, les controles et les points ouverts.
Pur calcul a partir des generateurs (suspendu_salle1, suspendu_ss1_blocs, suspendu_ss2_blocs, suspendu_ss3_blocs) ; ecrit atelier/docs/donjon_suspendu/dossier/ (un PNG par
niveau, dossier.html) puis le PDF par Microsoft Edge en mode sans fenetre.
  test-local/venv/Scripts/python.exe atelier/scripts/plan_pdf_donjon_suspendu.py"""
import math
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import donjon_suspendu as dj  # noqa: E402
import suspendu_salle1 as rdc  # noqa: E402
import suspendu_ss1_blocs as ss1  # noqa: E402
import suspendu_ss2_blocs as ss2  # noqa: E402
import suspendu_ss3_blocs as ss3  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
SORTIE = RACINE / "docs" / "donjon_suspendu" / "dossier"
EDGE = [Path("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"), Path("C:/Program Files/Microsoft/Edge/Application/msedge.exe")]
POLICE, POLICE_G = "C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/arialbd.ttf"
OX, OY, OZ = dj.ORIGINE

ORANGE, JAUNE, VERT, ROUGE, BLEU, CYAN, VIOLET, BRUN = (245, 140, 20), (250, 215, 40), (40, 170, 60), (215, 30, 30), (40, 90, 220), (20, 200, 220), (140, 60, 190), (130, 85, 40)
PASSE = ("air", "cave_air", "light", "chain", "lantern", "cave_vines", "cave_vines_plant", "hanging_roots", "pointed_dripstone", "ladder", "stone_button", "lever", "torch", "wall_torch", "vine", "glow_lichen")


def police(t, gras=False):
    return ImageFont.truetype(POLICE_G if gras else POLICE, t)


class Plan:
    """Une vue de dessus d'une zone du gabarit (x0..x1, z0..z1), k pixels par bloc ; les symboles et les etiquettes s'ajoutent ensuite, les etiquettes sont dessinees en dernier."""

    def __init__(self, titre, sous_titre, x0, x1, z0, z1, k):
        self.x0, self.x1, self.z0, self.z1, self.k = x0, x1, z0, z1, k
        largeur = (x1 - x0 + 1) * k + 2 * 60
        f = police(11)
        lignes, ligne = [], ""
        mesure = ImageDraw.Draw(Image.new("RGB", (1, 1)))
        for mot in sous_titre.split():
            essai = (ligne + " " + mot).strip()
            if mesure.textlength(essai, font=f) > largeur - 120:
                lignes.append(ligne)
                ligne = mot
            else:
                ligne = essai
        lignes.append(ligne)
        self.m = 60 + 15 * (len(lignes) - 1)
        self.mx = 60
        self.img = Image.new("RGB", (largeur, (z1 - z0 + 1) * k + self.m + 80), (255, 255, 255))
        self.d = ImageDraw.Draw(self.img, "RGBA")
        self.d.rectangle([self.mx, self.m, self.mx + (x1 - x0 + 1) * k, self.m + (z1 - z0 + 1) * k], fill=(52, 54, 62))      # la roche
        self.etiquettes = []
        self.d.text((self.mx, 8), titre, font=police(17, True), fill=(10, 10, 30))
        for n, l in enumerate(lignes):
            self.d.text((self.mx, 32 + 15 * n), l, font=f, fill=(50, 50, 70))

    def c(self, x, z):
        return self.mx + (x - self.x0) * self.k + self.k / 2, self.m + (z - self.z0) * self.k + self.k / 2

    def dedans(self, x, z):
        return self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1

    def case(self, x, z, couleur):
        if self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1:
            a = (self.mx + (x - self.x0) * self.k, self.m + (z - self.z0) * self.k)
            self.d.rectangle([a[0], a[1], a[0] + self.k - 1, a[1] + self.k - 1], fill=couleur)

    def hachure(self, x, z):
        if (x + z) % 3 == 0 and self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1:
            a = (self.mx + (x - self.x0) * self.k, self.m + (z - self.z0) * self.k)
            self.d.line([a[0], a[1] + self.k, a[0] + self.k, a[1]], fill=(150, 150, 165), width=1)

    def etiquette(self, xy, texte, taille=12, fond=(255, 255, 255, 230), couleur=(20, 20, 30)):
        self.etiquettes.append((xy, texte, taille, fond, couleur))

    def _etiquette(self, xy, texte, taille, fond, couleur):
        f = police(taille, True)
        b = self.d.textbbox((0, 0), texte, font=f)
        w, h = b[2] - b[0], b[3] - b[1]
        x, y = xy[0] - w / 2, xy[1] - h / 2
        self.d.rectangle([x - 4, y - 3, x + w + 4, y + h + 5], fill=fond, outline=(80, 80, 90))
        self.d.text((x, y - b[1]), texte, font=f, fill=couleur)

    # -- les symboles (codes couleurs de Luca)
    def texte_centre(self, c, texte, taille, couleur):
        f = police(taille, True)
        b = self.d.textbbox((0, 0), texte, font=f)
        self.d.text((c[0] - (b[2] - b[0]) / 2, c[1] - (b[3] - b[1]) / 2 - b[1]), texte, font=f, fill=couleur)

    def carre_orange(self, x, z, texte, cote=16):
        if not self.dedans(x, z):
            return
        c = self.c(x, z)
        self.d.rectangle([c[0] - cote / 2, c[1] - cote / 2, c[0] + cote / 2, c[1] + cote / 2], fill=ORANGE, outline=(90, 40, 0), width=2)
        self.texte_centre(c, texte, 11, (20, 10, 0))

    def rond(self, x, z, couleur, texte="", r=8):
        if not self.dedans(x, z):
            return
        c = self.c(x, z)
        self.d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=couleur, outline=(20, 20, 20), width=2)
        if texte:
            self.texte_centre(c, texte, 10 if len(texte) > 1 else 11, (0, 0, 0) if couleur in (JAUNE, CYAN) else (255, 255, 255))

    def croix(self, x, z, r=6):
        if not self.dedans(x, z):
            return
        c = self.c(x, z)
        self.d.line([(c[0] - r, c[1] - r), (c[0] + r, c[1] + r)], fill=ROUGE, width=4)
        self.d.line([(c[0] - r, c[1] + r), (c[0] + r, c[1] - r)], fill=ROUGE, width=4)

    def mini_boss(self, x, z):
        if not self.dedans(x, z):
            return
        c = self.c(x, z)
        self.texte_centre((c[0] + 1, c[1] + 1), "M", 30, (0, 0, 0))
        self.texte_centre(c, "M", 30, ROUGE)

    def boss(self, x, z, r=18):
        cx, cy = self.c(x, z)
        pts = []
        for i in range(10):
            a = math.pi / 2 + i * math.pi / 5
            rr = r if i % 2 == 0 else r * 0.45
            pts.append((cx + rr * math.cos(a), cy - rr * math.sin(a)))
        self.d.polygon(pts, fill=VIOLET, outline=(40, 0, 60))
        self.texte_centre((cx, cy + 1), "B", 11, (255, 255, 255))

    def note(self, x, z, texte):
        if not self.dedans(x, z):
            return
        c = self.c(x, z)
        self.d.rectangle([c[0] - 8, c[1] - 8, c[0] + 8, c[1] + 8], fill=(150, 90, 210), outline=(50, 0, 80), width=2)
        self.texte_centre(c, texte, 11, (255, 255, 255))

    def barre(self, cases, couleur, contour=(0, 0, 0)):
        xs, zs = [c[0] for c in cases], [c[1] for c in cases]
        a = (self.mx + (min(xs) - self.x0) * self.k, self.m + (min(zs) - self.z0) * self.k)
        b = (self.mx + (max(xs) - self.x0 + 1) * self.k, self.m + (max(zs) - self.z0 + 1) * self.k)
        self.d.rectangle([a[0] - 2, a[1] - 2, b[0] + 2, b[1] + 2], fill=couleur, outline=contour, width=2)
        return ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)

    def contour_rouge(self, cases, marge=1.5):
        """La « zone entouree de rouge » de Luca : une ellipse autour d'un groupe de cases."""
        xs, zs = [c[0] for c in cases], [c[1] for c in cases]
        a = self.c(min(xs) - marge, min(zs) - marge)
        b = self.c(max(xs) + marge, max(zs) + marge)
        self.d.ellipse([a[0], a[1], b[0], b[1]], outline=ROUGE, width=3)

    def fleche(self, x, z, texte, vers="bas"):
        c = self.c(x, z)
        if vers == "bas":
            self.d.polygon([(c[0], c[1] - 6), (c[0] - 9, c[1] - 22), (c[0] + 9, c[1] - 22)], fill=(0, 140, 0))
            self.etiquette((c[0], c[1] - 34), texte, 11, (220, 255, 220, 240))
        else:
            self.d.polygon([(c[0] + 6, c[1]), (c[0] + 22, c[1] - 9), (c[0] + 22, c[1] + 9)], fill=(0, 140, 0))
            self.etiquette((c[0] + 60, c[1]), texte, 11, (220, 255, 220, 240))

    def finir(self, chemin, echelle=10):
        for e in self.etiquettes:
            self._etiquette(*e)
        y0 = self.img.height - 40
        self.d.rectangle([self.mx, y0, self.mx + echelle * self.k, y0 + 6], fill=(20, 20, 20))
        self.d.text((self.mx + echelle * self.k + 8, y0 - 4), f"{echelle} blocs", font=police(11), fill=(20, 20, 20))
        nx, ny = self.img.width - self.mx - 20, self.m + 26
        self.d.polygon([(nx, ny - 20), (nx - 8, ny), (nx + 8, ny)], fill=(255, 255, 255), outline=(0, 0, 0))
        self.d.text((nx - 5, ny + 2), "N", font=police(13, True), fill=(255, 255, 255))
        self.img.save(chemin)


# --------------------------------------------------------------- le fond d'un plan : la vue de dessus des blocs (sol, vide, lave, escalier, bois) avec la hauteur du sol
def nom(v):
    return v[0].split(":")[-1]


def fond(p, blocs, y_haut, y_bas, ref, sols_speciaux=None, lave_vide=True):
    """Pour chaque colonne : de haut en bas, la premiere case libre, puis le premier bloc plein en dessous ; sol (teinte selon sa hauteur par rapport a `ref`), lave, escalier, bois, ou vide."""
    hauteurs = {}
    for x in range(p.x0, p.x1 + 1):
        for z in range(p.z0, p.z1 + 1):
            libre = False
            for y in range(y_haut, y_bas - 1, -1):
                v = blocs.get((x, y, z))
                if v is None:
                    if libre:
                        break
                    continue
                n = nom(v)
                if n in PASSE or n == "water":
                    libre = True
                    continue
                if not libre:
                    continue
                if n == "lava":
                    p.case(x, z, (235, 110, 30))
                    if lave_vide:
                        p.hachure(x, z)
                elif n.endswith("_stairs"):
                    p.case(x, z, (175, 165, 140))
                elif "planks" in n or n.endswith("_log") or "wood" in n:
                    p.case(x, z, (150, 105, 55))
                else:
                    t = max(0.0, min(1.0, 0.5 + (y - ref) / 60))
                    p.case(x, z, (int(110 + 130 * t), int(105 + 125 * t), int(95 + 105 * t)))
                    hauteurs[(x, z)] = y
                break
            else:
                if libre:
                    p.case(x, z, (28, 28, 34))
                    p.hachure(x, z)
    return hauteurs


# --------------------------------------------------------------- les quatre niveaux
def plan_rdc():
    blocs, i = rdc.construire()
    p = Plan("Niveau 0 — Salle 1 (rez-de-chaussée)", f"Vue de dessus. Coordonnées du gabarit ; monde = ({OX} + x, {OY} + y, {OZ} + z). Sol à y 85 (monde y 29). Plus clair = plus haut.", 8, 112, 18, 92, 7)
    fond(p, blocs, 99, 20, 85)
    for n, cases in i["cases_portes"].items():
        couleur = {"1": (40, 90, 230), "2": (240, 210, 30), "3": (200, 25, 25)}[n]
        c = p.barre(list(cases), couleur)
        p.etiquette((c[0], c[1] - 22), f"Porte {n} ({rdc.COULEURS_PORTES[n][2]})", 11)
    for (x, y, z) in i["zombies"]:
        p.croix(x, z)
    cx, _, cz = i["coffre"]
    p.rond(cx, cz, VERT, "1", 10)
    ex, _, ez = i["entree"]
    p.fleche(ex, ez - 2, "ENTRÉE (0 30 0)")
    for nom_, (x, z, txt) in {"c1": (104, 54, "Escalier 27 marches → sous-sol 1"), "c2": (14, 54, "Escalier 46 marches → sous-sol 2")}.items():
        if p.x0 <= x <= p.x1:
            p.etiquette(p.c(x, z), txt, 11, (240, 235, 220, 235))
    p.etiquette(p.c(73, 24), "Couloir 3 → palier → tunnel de la salle 3", 11, (255, 225, 225, 235))
    p.etiquette(p.c(74, 64), "Salle octogonale", 13)
    p.finir(SORTIE / "plan_0_rdc.png")


def plan_ss1():
    blocs, i = ss1.construire()
    p = Plan("Niveau -1 — Sous-sol 1 (derrière la porte bleue)", f"Vue de dessus (on voit le sol le plus haut de chaque colonne : plateformes des piliers, zone haute). Arrivée à y 57 (monde y 1). Plus clair = plus haut.", 24, 115, 20, 110, 7)
    hauteurs = fond(p, blocs, 82, 22, 57, lave_vide=False)
    haute = [c for c, y in hauteurs.items() if y >= i["sol_haut"] - 1 and c[0] >= 60 and c[1] <= 60]
    if haute:
        p.contour_rouge(haute, 1)
        xs = [c[0] for c in haute]
        p.etiquette(p.c(sum(xs) / len(xs), min(c[1] for c in haute) - 2), "Zone haute (+11) : au grappin seulement", 11, (255, 230, 230, 235))
    p.barre(list(i["cases_porte"]), (40, 90, 230))
    p.etiquette(p.c(81, 74), "Porte bleue (s'ouvre aux 2 monstres vaincus)", 10)
    for (x, y, z) in i["monstres_petite"] + i["monstres_musicale"]:
        p.croix(x, z)
    mx, _, mz = i["mini_boss"]
    p.mini_boss(mx, mz)
    noms = ["P1", "P2", "P3", "P4", "P5", "P6", "P7"]
    for k, (x, y, z) in enumerate(i["ancrages"]):
        lib = noms[k] if k < 5 else ("C" if (x, y, z) == (64, 71, 29) else ("F1" if x == 87 else ("F2" if x == 107 else noms[k - 3 if k >= 8 else k])))
        p.rond(x, z, BLEU, "", 8)
        p.etiquette((p.c(x, z)[0] + 16, p.c(x, z)[1] - 12), lib, 10, (225, 235, 255, 235))
    for (x, y, z) in i["coffres"]:
        p.rond(x, z, JAUNE)
    gx, _, gz = i["coffre_grappin"]
    p.rond(gx, gz, VERT, "1", 10)
    kx, _, kz = i["coffre_cle"]
    p.rond(kx, kz, VERT, "2", 10)
    seq = i["enigme"]["sequence"]
    for n, (x, y, z) in enumerate(i["enigme"]["pads"], 1):
        p.note(x, z, str(n))
    dx_, _, dz_ = i["enigme"]["depart"]
    p.carre_orange(dx_, dz_, "D", 14)
    p.etiquette(p.c(84, 60), "Énigme musicale : rejouer " + "-".join(str(s) for s in seq), 10, (240, 225, 255, 235))
    ax, _, az = i["arrivee"]
    p.fleche(ax - 4, az + 4, "ARRIVÉE (escalier de la porte 1)")
    p.etiquette(p.c(55, 104), "Estrade (+26)", 10)
    p.etiquette(p.c(46, 40), "Grande salle (sol -34 à -24)", 12)
    p.etiquette(p.c(66, 82), "Balcon (échelle 30)", 10)
    for (a, b) in i["fosses"]:
        p.etiquette(p.c((a[0] + b[0]) / 2, b[1] + 2), "fosse (12)", 9, (230, 230, 230, 230))
    p.finir(SORTIE / "plan_1_ss1.png")


def plan_ss2():
    blocs, i = ss2.construire()
    p = Plan("Niveau -2 — Sous-sol 2 (derrière la porte jaune)", "Vue de dessus. Sol des îles à y 38 (monde y -18) ; hachuré = la fosse de 45 blocs (vide mortel), orange = lave au fond, brun = pont de bois.", 4, 234, 22, 202, 4)
    lave = set(i["lave_fond"])
    for (x, z), (sol, haut, genre) in i["cases"].items():
        if genre == "abime":
            p.case(x, z, (235, 110, 30) if (x, z) in lave else (28, 28, 34))
            p.hachure(x, z)
        elif genre == "pont":
            p.case(x, z, (150, 105, 55))
        elif genre == "massif":
            p.case(x, z, (90, 88, 84))
        elif genre == "grotte":
            p.case(x, z, (120, 100, 80))
        elif genre == "pilier":
            p.case(x, z, (150, 170, 210))
        else:
            p.case(x, z, (200, 195, 180))
    for nom_, m in i["montagnes"].items():
        p.contour_rouge(m["noyau"], 2)
    for k, liste in i["piliers_cyan"].items():
        for pil in liste:
            for (x, z) in pil["cases"]:
                p.case(x, z, (120, 210, 220, 140))
    for (x, y, z) in i["ancrages_bleus"]:
        p.rond(x, z, BLEU)
    for k, liste in i["ancrages_cyan"].items():
        for (x, y, z) in liste:
            p.rond(x, z, CYAN, str(k))
    for k, (x, y, z) in i["leviers"].items():
        p.carre_orange(x, z, str(k))
    for nom_, liste in i["ennemis"].items():
        for (x, y, z) in liste:
            p.croix(x, z, 5)
    for (x, y, z) in i["coffres"]:
        p.rond(x, z, JAUNE)
    cx, _, cz = i["coffre_cle"]
    p.rond(cx, cz, VERT, "3", 10)
    for nom_, ile in i["iles"].items():
        if ile.get("centre") and nom_ != "PONT":
            x, _, z = ile["centre"]
            p.etiquette((p.c(x, z)[0], p.c(x, z)[1] + 22), f"île {nom_}", 10)
    ax, _, az = i["arrivee"]
    p.fleche(ax, az + 2, "ARRIVÉE (escalier de la porte 2)")
    p.etiquette(p.c(45, 112), "Couloir-grotte", 10)
    p.finir(SORTIE / "plan_2_ss2.png", 20)


def plan_ss3(zoom=False):
    blocs, i = ss3.construire()
    if zoom:
        p = Plan("Niveau -3 — Salle 3 : le gouffre, le pont et l'arène du boss", "Vue de dessus (on voit le tour le plus haut de l'anneau en spirale). Lave = vide mortel (retour au bord) ; ronds bleus = ancrages du grappin.", 246, 316, 14, 98, 8)
    else:
        p = Plan("Niveau -3 — Salle 3 (derrière la porte rouge) : le tunnel organique et l'arène", "Vue de dessus, de la porte rouge (à gauche) à l'arène (à droite). Sol du tunnel à y 31 (monde y -25). Orange hachuré = fosse de lave.", 66, 316, 2, 98, 4)
    fond(p, blocs, 90, -8, 31)
    for (nom_, x, y, z) in i["ancrages_boss"]:
        p.rond(x, z, BLEU, "", 6 if not zoom else 7)
    for c in i["tunnel"]["coffres"]:
        x, y, z = c["pos"]
        p.rond(x, z, JAUNE)
    ax, _, az = i["coffre"]
    p.rond(ax, az, JAUNE)
    gx, _, gz = i["golem"]["centre"]
    p.boss(gx, gz, 20 if zoom else 14)
    p.barre([(c[0], c[2]) for c in i["sortie"]], (110, 20, 20))
    if zoom:
        p.etiquette(p.c(279, 22), "Gouffre : pont en 4 tronçons, 3 trous de 6", 11)
        p.etiquette(p.c(266, 51), "Tête de pont (réveil du boss)", 10, (255, 235, 235, 235))
        p.etiquette(p.c(279, 75), "Lac de lave", 11, (255, 230, 210, 235))
        p.etiquette(p.c(302, 64), "Balcon + sortie (au grappin)", 10)
        p.etiquette(p.c(254, 81), "Alcove à coffre", 10)
        for n, (x, y, z) in i["corniches"].items():
            p.etiquette((p.c(x, z)[0], p.c(x, z)[1] + 18), f"corniche {n.upper()}", 9, (230, 240, 255, 230))
        p.etiquette(p.c(279, 92), "Anneau en spirale (rampe) : 6 trous, 12 ancrages", 11)
        p.finir(SORTIE / "plan_3b_arene.png", 10)
    else:
        p.fleche(73, 12, "depuis la porte rouge (couloir 3)", "droite")
        p.etiquette(p.c(103, 18), "Escalier 53 marches (niche à coffre)", 10)
        for n, (a, b) in enumerate(ss3.TROUS_TUNNEL, 1):
            p.etiquette(p.c((a + b) / 2, 21), f"trou {n}", 9, (255, 225, 200, 235))
        p.etiquette(p.c(141, 1), "Chambre 1 (corniche à coffre)", 9)
        p.etiquette(p.c(213, 1), "îlot à coffre", 9)
        p.etiquette(p.c(264, 1), "Chambre 3 (coffre)", 9)
        p.etiquette(p.c(279, 60), "Arène", 12)
        p.finir(SORTIE / "plan_3a_tunnel.png", 20)


# --------------------------------------------------------------- le texte
SVG = {
    "croix": '<svg width="22" height="22"><line x1="4" y1="4" x2="18" y2="18" stroke="#d71e1e" stroke-width="4"/><line x1="4" y1="18" x2="18" y2="4" stroke="#d71e1e" stroke-width="4"/></svg>',
    "M": '<svg width="22" height="22"><text x="2" y="19" font-size="21" font-weight="bold" fill="#d71e1e">M</text></svg>',
    "bleu": '<svg width="22" height="22"><circle cx="11" cy="11" r="8" fill="#285adc" stroke="#222" stroke-width="2"/></svg>',
    "cyan": '<svg width="22" height="22"><circle cx="11" cy="11" r="8" fill="#14c8dc" stroke="#222" stroke-width="2"/><text x="8" y="15" font-size="10" font-weight="bold">1</text></svg>',
    "orange": '<svg width="22" height="22"><rect x="3" y="3" width="16" height="16" fill="#f58c14" stroke="#5a2800" stroke-width="2"/><text x="8" y="16" font-size="11" font-weight="bold">1</text></svg>',
    "jaune": '<svg width="22" height="22"><circle cx="11" cy="11" r="8" fill="#fad728" stroke="#222" stroke-width="2"/></svg>',
    "vert": '<svg width="22" height="22"><circle cx="11" cy="11" r="8" fill="#28aa3c" stroke="#222" stroke-width="2"/><text x="8" y="15" font-size="10" font-weight="bold" fill="#fff">1</text></svg>',
    "rouge": '<svg width="22" height="22"><ellipse cx="11" cy="11" rx="9" ry="7" fill="none" stroke="#d71e1e" stroke-width="2"/></svg>',
    "hachure": '<svg width="22" height="22"><rect x="2" y="2" width="18" height="18" fill="#1c1c22" stroke="#222"/><line x1="2" y1="12" x2="12" y2="2" stroke="#9696a5"/><line x1="2" y1="20" x2="20" y2="2" stroke="#9696a5"/><line x1="10" y1="20" x2="20" y2="10" stroke="#9696a5"/></svg>',
    "lave": '<svg width="22" height="22"><rect x="2" y="2" width="18" height="18" fill="#eb6e1e"/><line x1="2" y1="20" x2="20" y2="2" stroke="#9696a5"/></svg>',
    "boss": '<svg width="22" height="22"><polygon points="11,1 13.5,8 21,8 15,12.5 17,20 11,15.5 5,20 7,12.5 1,8 8.5,8" fill="#8c3cbe"/></svg>',
    "note": '<svg width="22" height="22"><rect x="3" y="3" width="16" height="16" fill="#965ad2" stroke="#320050" stroke-width="2"/><text x="8" y="16" font-size="11" font-weight="bold" fill="#fff">1</text></svg>',
    "porte": '<svg width="22" height="22"><rect x="2" y="7" width="18" height="8" fill="#285ae6" stroke="#000" stroke-width="2"/></svg>',
    "sortie": '<svg width="22" height="22"><rect x="2" y="7" width="18" height="8" fill="#6e1414" stroke="#000" stroke-width="2"/></svg>',
    "fleche": '<svg width="22" height="22"><polygon points="11,20 3,4 19,4" fill="#008c00"/></svg>',
}


def pastille(rgb):
    return '<span class="pastille" style="background:rgb(%d,%d,%d)"></span>' % rgb


def html():
    return f"""<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Donjon Suspendu — dossier de travail</title>
<style>
@page {{ size: A4; margin: 13mm 12mm; }}
body {{ font-family: 'Segoe UI', Arial, sans-serif; font-size: 10pt; color: #16161e; line-height: 1.37; }}
h1 {{ font-size: 21pt; margin: 0 0 4px; color: #3a1d5c; }} h2 {{ font-size: 14pt; color: #3a1d5c; border-bottom: 2px solid #3a1d5c; padding-bottom: 2px; margin-top: 16px; }}
h3 {{ font-size: 11.5pt; color: #5a2d8c; margin: 11px 0 4px; }}
table {{ border-collapse: collapse; width: 100%; margin: 6px 0 10px; font-size: 9.1pt; }} th, td {{ border: 1px solid #bdb7cb; padding: 3px 5px; vertical-align: top; text-align: left; }}
th {{ background: #efe8f7; }} .leg td:first-child {{ width: 26px; text-align: center; }} .pastille {{ display:inline-block; width:16px; height:16px; border:1px solid #444; vertical-align: middle; }}
.note {{ background: #fff6dc; border-left: 4px solid #e0a000; padding: 6px 9px; margin: 8px 0; }} .info {{ background: #efe9ff; border-left: 4px solid #6a3ad0; padding: 6px 9px; margin: 8px 0; }}
.page {{ page-break-before: always; }} img.plein {{ width: 100%; }} .etape {{ border: 1px solid #bdb7cb; border-radius: 6px; padding: 5px 8px; margin: 5px 0; page-break-inside: avoid; }}
.etape b.n {{ display:inline-block; background:#3a1d5c; color:#fff; border-radius:10px; padding:0 7px; margin-right:6px; }} code {{ font-family: Consolas, monospace; font-size: 8.8pt; background:#f3f1f6; padding:0 2px; }}
.petit {{ font-size: 8.6pt; color: #444; }}
</style></head><body>

<h1>Donjon Suspendu — dossier de travail</h1>
<div class="petit">Le donjon 1 actuel du projet Kodistan. Dossier généré le 2026-11-17 à partir des générateurs (<code>atelier/scripts/donjon_suspendu.py</code> et <code>suspendu_*.py</code>), de la fiche <code>atelier/docs/donjon_suspendu.md</code> et des demandes de Luca. Régénérer : <code>test-local/venv/Scripts/python.exe atelier/scripts/plan_pdf_donjon_suspendu.py</code>.</div>

<div class="info"><b>À qui s'adresse ce document.</b> À un agent (Opus) qui doit reprendre ce donjon ou le faire évoluer. Il décrit ce qui est <b>construit</b> (branche <code>feature/donjon-suspendu</code>, PR #49 vers <code>main</code>, en attente de Duncan), l'intention de game design derrière chaque choix, ce qui est vérifié par script et ce qui ne l'a pas encore été en jeu, et les règles du projet. Les plans utilisent les <b>codes couleurs de Luca</b> (§ 2).</div>

<h2>1. En bref</h2>
<ul>
<li><b>Principe :</b> un donjon qui se joue <b>de haut en bas</b> : une salle en surface et trois niveaux souterrains taillés dans la pierre. Construit <b>salle par salle</b> d'après les croquis GIMP de Luca : il décrit une salle, on la construit, on la teste, puis on passe à la suivante.</li>
<li><b>Objet du donjon :</b> le <b>grappin</b> (mod du projet, version 3 : on vise un ancrage, on est tiré en ligne droite jusqu'à lui, on tombe dessous). On le gagne au sous-sol 1, on s'en sert ensuite partout.</li>
<li><b>Fil rouge :</b> trois portes colorées au rez-de-chaussée, chacune ouverte par une clé de sa couleur, trouvée un niveau plus bas : <b>clé bleue</b> (rez-de-chaussée) → sous-sol 1 → <b>clé jaune</b> → sous-sol 2 → <b>clé rouge</b> → salle 3 et le boss, le <b>Gardien des Racines</b>.</li>
<li><b>Ingrédients :</b> combats qui ouvrent des portes, coffres qui apparaissent en cinématique, une énigme musicale, une énigme de leviers qui font apparaître des piliers, des traversées de vide au grappin, un boss à points faibles qu'on frappe à l'épée depuis une rampe en spirale.</li>
<li><b>État :</b> tout est généré et vérifié par script (quatre vérificateurs et 107 tests passent). En jeu : le rez-de-chaussée et le début du sous-sol 1 ont été essayés ; <b>le reste n'a pas été vu en jeu</b>.</li>
</ul>

<h2>2. Légende — les codes couleurs de Luca</h2>
<p>Luca dessine ses plans sur GIMP avec ces codes (expliqués le 2026-11-16 pour le sous-sol 1, puis pour le sous-sol 2). Les plans de ce document les reprennent.</p>
<table class="leg"><tr><th></th><th>Code</th><th>Sens</th><th>Où on le trouve ici</th></tr>
<tr><td>{SVG['croix']}</td><td>Croix rouge</td><td>Monstre (zombie / squelette)</td><td>Zombies du rez-de-chaussée ; gardiens et monstres du sous-sol 1 ; ennemis des îles du sous-sol 2</td></tr>
<tr><td>{SVG['M']}</td><td>Gros « M »</td><td>Mini-boss</td><td>Sous-sol 1, grande salle : le Rôdeur des Racines (donjon:rodeur, mod grappin 3.4.0+)</td></tr>
<tr><td>{SVG['bleu']}</td><td>Rond bleu</td><td>Point d'ancrage permanent du grappin (avec plateforme quand il est dans le vide)</td><td>Piliers et corniche du sous-sol 1 ; piliers bleus du sous-sol 2 ; tous les ancrages de la salle 3</td></tr>
<tr><td>{SVG['cyan']}</td><td>Rond cyan (numéroté)</td><td>Point d'ancrage qui apparaît quand on tire le levier du même numéro</td><td>Sous-sol 2</td></tr>
<tr><td>{SVG['orange']}</td><td>Carré orange numéroté</td><td>Levier ; le numéro donne l'<b>ordre</b></td><td>Les 5 leviers du sous-sol 2 ; « D » = bouton de départ de l'énigme musicale</td></tr>
<tr><td>{SVG['jaune']}</td><td>Rond jaune</td><td>Coffre (vide pour l'instant)</td><td>Coffres jaunes des sous-sols 1 et 2 ; alcove de l'arène ; les 4 coffres du tunnel (remplis par une table de butin provisoire)</td></tr>
<tr><td>{SVG['vert']}</td><td>Rond vert numéroté</td><td>Coffre qui donne un objet clé</td><td>Rez-de-chaussée : <b>clé bleue</b> ; sous-sol 1 : <b>1 = grappin</b>, <b>2 = clé jaune</b> ; sous-sol 2 : <b>3 = clé rouge</b>. Ils n'existent pas au départ : ils apparaissent (cinématique) quand on a réussi l'épreuve.</td></tr>
<tr><td>{SVG['rouge']}</td><td>Zone entourée de rouge</td><td>Annotation de Luca : zone à traiter à part (en hauteur, à monter jusqu'au plafond…)</td><td>Sous-sol 1 : la zone haute (+11) ; sous-sol 2 : les deux îles devenues montagnes à grotte</td></tr>
<tr><td>{SVG['hachure']}</td><td>Sol hachuré</td><td>Vide (chute)</td><td>La fosse de 45 blocs du sous-sol 2 (mortelle)</td></tr>
</table>
<h3>Ajouts de ce dossier</h3>
<table class="leg"><tr><th></th><th>Symbole</th><th>Sens</th></tr>
<tr><td>{pastille((200, 190, 165))}</td><td>Beige (plus clair = plus haut)</td><td>Sol où l'on marche ; la teinte donne la hauteur</td></tr>
<tr><td>{SVG['lave']}</td><td>Orange hachuré</td><td>Lave au fond d'un vide (fosses du tunnel, gouffre, arène ; rivières du sous-sol 2)</td></tr>
<tr><td>{pastille((175, 165, 140))}</td><td>Gris-beige</td><td>Escalier</td></tr>
<tr><td>{pastille((150, 105, 55))}</td><td>Brun</td><td>Pont de bois délabré (sous-sol 2)</td></tr>
<tr><td>{pastille((150, 170, 210))}</td><td>Bleu pâle</td><td>Plateforme d'un pilier bleu (sous-sol 2) ; cyan pâle : pilier qui sort du vide avec un levier</td></tr>
<tr><td>{SVG['note']}</td><td>Carré violet numéroté</td><td>Bloc musical de l'énigme (numéroté du nord au sud)</td></tr>
<tr><td>{SVG['porte']}</td><td>Barre de couleur</td><td>Porte (bleue, jaune, rouge) ; barre rouge sombre : sortie scellée de l'arène</td></tr>
<tr><td>{SVG['boss']}</td><td>Étoile violette « B »</td><td>Boss (Gardien des Racines)</td></tr>
<tr><td>{SVG['fleche']}</td><td>Flèche verte</td><td>Entrée ou arrivée d'un escalier</td></tr>
</table>

<h2>3. Le monde et la technique</h2>
<table>
<tr><th>Monde</th><td>« Donjon Suspendu », superflat <b>profond</b> (bedrock à y -64, 90 blocs de pierre, 2 de terre, herbe à y 29) : la place des trois sous-sols. Survie, difficulté normale. Prêt à copier : <code>atelier/mondes/Donjon Suspendu.zip</code>, à dézipper dans <code>mod/run/client/saves/</code> ; la pose est automatique à la première ouverture (≈ 1 min, par étapes, ne pas mettre en pause), puis un <code>/tp</code> cliquable vers l'entrée (0 30 0).</td></tr>
<tr><th>Versions</th><td>Minecraft 1.21.1, NeoForge 21.1.216 (figé : ne jamais le changer), mod du grappin avec le golem (<code>atelier/exports/grappin-3.0.4.jar</code>, copié par <code>test-local/lancer-client-test.sh</code>)</td></tr>
<tr><th>Gabarit</th><td>336 × 120 × 208, posé en {OX} {OY} {OZ} (monde = gabarit + ce décalage), chargé en deux moitiés (forceload ≤ 256 chunks). Structures : <code>suspendu_salle1</code>, <code>suspendu_ss1</code>, <code>suspendu_ss2</code>, <code>suspendu_ss3</code> (+ dalles des portes).</td></tr>
<tr><th>Hauteurs</th><td>Rez-de-chaussée : sol y 85 du gabarit (monde 29). Sous-sol 1 : arrivée à -28 (grande salle de -62 à -6). Sous-sol 2 : sol des îles à -47, fosse jusqu'à -92 (juste au-dessus du socle). Salle 3 : tunnel à -54, lac de lave de l'arène à -89.</td></tr>
<tr><th>Logique</th><td>Fonctions de datapack seulement (aucun bloc de commande) : <code>suspendu_tick</code> (combats, leviers, chutes), <code>suspendu_load</code>, la pose par étapes (<code>suspendu_pose</code>, <code>_2</code>, <code>_2_N</code>), les portes (<code>suspendu_ouvrir_pN</code>), les cinématiques (<code>suspendu_cine_*</code>), la remise à zéro <code>/function atelier:suspendu_reset</code>. Reposer tout : <code>/scoreboard players set #suspendu atelier 0</code> puis <code>/reload</code>.</td></tr>
<tr><th>Fichiers</th><td><code>donjon_suspendu.py</code> (assemblage, fonctions, monde, <code>--zip</code>) ; <code>suspendu_salle1.py</code> ; <code>suspendu_ss1.py</code> / <code>suspendu_ss1_blocs.py</code> ; <code>suspendu_ss2.py</code> / <code>_blocs.py</code> / <code>_deco.py</code> / <code>_fonctions.py</code> ; <code>suspendu_ss3_blocs.py</code> / <code>_fonctions.py</code> ; <code>suspendu_cine.py</code> ; vérificateurs <code>verif_donjon_suspendu.py</code>, <code>verif_ss1.py</code>, <code>verif_ss2.py</code>, <code>verif_ss3.py</code> ; tests <code>atelier/tests/test_donjon_ss*.py</code>, <code>test_suspendu_cine.py</code>.</td></tr>
</table>

<h2>4. Game design</h2>
<h3>4.1 La progression</h3>
<table><tr><th>Niveau</th><th>Ce qu'on y gagne</th><th>Ce que ça ouvre</th><th>Ce qu'on y apprend</th></tr>
<tr><td>0 · Salle 1</td><td>Clé bleue (après les 3 zombies)</td><td>Porte 1 (bleue)</td><td>Les portes à clé, les coffres qui apparaissent</td></tr>
<tr><td>-1 · Sous-sol 1</td><td><b>Grappin</b> (après le mini-boss), puis clé jaune (après l'énigme musicale)</td><td>Porte 2 (jaune)</td><td>Le grappin : monter sur des piliers, traverser des fosses</td></tr>
<tr><td>-2 · Sous-sol 2</td><td>Clé rouge (après les 5 leviers)</td><td>Porte 3 (rouge)</td><td>Enchaîner les tirs au-dessus d'un vide mortel ; des leviers qui créent le chemin</td></tr>
<tr><td>-3 · Salle 3</td><td>La victoire (sortie de l'arène)</td><td>— (la suite reste à définir)</td><td>Tout à la fois : fosses, pont en ruine, anneau en ruine, combat à l'épée</td></tr></table>
<h3>4.2 Les règles qui tiennent tout</h3>
<ul>
<li><b>Une porte = une clé de sa couleur</b>, présentée en main devant la porte (elle est consommée) ; la dalle se lève, sans cinématique. La seule porte sans clé est la porte bleue du sous-sol 1 : elle s'ouvre quand ses deux gardiens sont vaincus.</li>
<li><b>Les objets clés n'existent pas d'avance</b> : leur coffre apparaît quand l'épreuve est réussie (combat, mini-boss, énigme, levier 5), en cinématique. Rien ne se prend par raccourci.</li>
<li><b>Le grappin 3</b> : portée 30 blocs ; le crochet s'accroche à un ancrage qu'il frôle à moins de 1 bloc ; on est tiré en ligne droite, on s'arrête à 1,2 bloc de l'ancrage et on tombe tout droit. Donc <b>un ancrage se place juste au-dessus de l'endroit où l'on doit arriver</b>, 3 à 4 blocs plus haut, et à 3,5 blocs du bord d'un trou. On ne s'accroche qu'aux blocs d'ancrage, jamais à la roche.</li>
<li><b>Les trous ne se sautent pas</b> : au moins 5 à 6 blocs (un saut en courant en franchit 4 au mieux). Le vérificateur le contrôle partout.</li>
<li><b>Tomber</b> : au sous-sol 1, on survit (12 blocs, échelle de secours) ; au sous-sol 2, la fosse est <b>mortelle</b> ; à la salle 3, la lave renvoie au bord d'où l'on vient (crochet décroché, chute lente, sans dégâts).</li>
<li><b>Lumière ≥ 5 partout où l'on marche</b> : aucun monstre n'apparaît tout seul ; tous les ennemis sont invoqués par le donjon, une seule fois.</li>
<li><b>Cinématiques</b> à la Zelda (datapack seul) : les joueurs proches passent en spectateur et regardent par une caméra qui avance en ligne droite sans jamais tourner (fluidité), puis retrouvent leur place, leur regard et leur mode de jeu. Maj quitte la caméra. Si une autre scène est en cours, l'effet a lieu tout de suite, sans caméra.</li>
</ul>

<div class="page"></div>
<h2>5. Niveau 0 — Salle 1 (rez-de-chaussée)</h2>
<img class="plein" src="plan_0_rdc.png">
<ul>
<li>Un <b>bâtiment octogonal</b> posé sur la plaine (≈ 30 de large, 14 de haut), couloir d'entrée au sud. Au centre, un emblème octogonal ; lumière par 12 feux de sol et un lustre ; 13 pilastres.</li>
<li><b>Trois portes</b> au nord, dalles colorées en arc avec une spirale : <b>1 bleue</b> (nord-est), <b>2 jaune</b> (nord-ouest), <b>3 rouge</b> (milieu). Couloirs : 1 et 2 finissent par un escalier qui descend (27 marches vers le sous-sol 1 ; 46 vers le sous-sol 2), 3 est droit et mène au palier de la salle 3.</li>
<li><b>Combat :</b> un joueur au centre → <b>3 zombies</b> apparaissent ; tous morts → cinématique, un <b>coffre</b> (rond vert) apparaît au milieu avec la <b>clé bleue</b>.</li>
</ul>

<div class="page"></div>
<h2>6. Niveau -1 — Sous-sol 1 (derrière la porte bleue)</h2>
<img class="plein" src="plan_1_ss1.png">
<ul>
<li><b>Petite salle d'arrivée</b> (bas de l'escalier de 27 marches) : un zombie et un squelette apparaissent ; vaincus → la <b>seconde porte bleue</b> s'ouvre seule (cinématique).</li>
<li><b>Grande salle</b> (≈ 50 × 85, 56 de haut) : on arrive sur un <b>balcon</b> (garde-corps, échelle de 30 barreaux vers le sol). Au sol : le <b>mini-boss « M »</b> (se réveille à moins de 22 blocs), des coffres jaunes, et au sud une <b>estrade</b> haute de 26 (échelle de 26). <b>Mini-boss vaincu → cinématique → le coffre du grappin</b> (vert 1) apparaît sur l'estrade.</li>
<li><b>Sept piliers</b> P1–P7 (colonnes de 30 à 46 blocs, plateforme au sommet, ancrage 4 blocs au-dessus tenu au plafond) : on n'y monte qu'au grappin, depuis l'estrade (P4, P5, P6, P7) ou le balcon (P2, P4, P6, P7).</li>
<li><b>La zone haute</b> (entourée de rouge, +11, en falaise) : depuis la plateforme de P2 on tire l'<b>ancrage de la corniche C</b> (caché à qui est au sol) et on tombe dans le <b>hall</b>. Le hall est coupé par <b>deux fosses</b> de 12 (F1, F2) : du bord ouest on tire l'ancrage au-dessus de la rive est. Tomber n'est pas mortel : échelle de secours.</li>
<li><b>La salle de l'énigme musicale</b> (un zombie et un squelette à l'entrée) : 5 blocs musicaux (cloche, carillon, 8 bits, xylophone de fer, xylophone), une lampe de couleur au-dessus de chacun, un bouton de départ « D ». Solution en § 9. Réussite → le <b>coffre de la clé jaune</b> (vert 2) apparaît.</li>
<li>Décor organique : mousse, stalactites, stalagmites, lianes à baies lumineuses.</li>
</ul>

<div class="page"></div>
<h2>7. Niveau -2 — Sous-sol 2 (derrière la porte jaune)</h2>
<img class="plein" src="plan_2_ss2.png">
<ul>
<li><b>Le couloir d'arrivée</b> (bas de l'escalier de 46 marches) est une grotte (largeur et plafond qui ondulent, mousse, champignons lumineux, stalactites, racines, lianes) qui longe le sous-sol 1 jusqu'à l'<b>île d'entrée</b>.</li>
<li><b>La salle</b> (≈ 148 × 178, 12 de haut) : <b>huit îles</b> au-dessus d'une <b>fosse mortelle de 45 blocs</b> (hachurée), au fond de laquelle serpentent 4 rivières de lave. Aucun garde-corps. Îles : entrée E, levier 1 (B), grande île du levier 2 (C, montagne à grotte), levier 3 (A), levier 4 (F), île du coffre (G, reliée à F par un <b>pont de bois délabré</b> de 38 blocs aux planches manquantes), île en pointe du levier 5 (H, montagne à grotte), quart de disque de la clé (I).</li>
<li><b>Les deux montagnes</b> (entourées de rouge par Luca) montent jusqu'au plafond ; une grotte organique y est creusée (ruisseau de lave, stalactites, cristaux d'améthyste), le levier y est.</li>
<li><b>Ancrages bleus</b> (toujours là) : 4 piliers dans le vide et un point sur l'île du levier 1 — de l'entrée on enchaîne les piliers jusqu'à l'île 1.</li>
<li><b>L'énigme des leviers</b> : chaque levier tiré <b>fait apparaître ses ancrages cyan</b> (du même numéro), qui mènent à l'île du levier suivant. Ceux au-dessus du vide arrivent avec un <b>pilier qui sort du fond de la fosse</b> (5 piliers en tout). Chaque levier a sa <b>cinématique</b> : la caméra montre le pilier monter rang par rang (ou la chaîne descendre du plafond), puis l'ancrage. Le <b>levier 5</b> ouvre la route de l'île de la clé et fait apparaître le <b>coffre de la clé rouge</b> (vert 3) au cours d'un long survol de la salle.</li>
<li><b>Ennemis</b> : 17 emplacements, un zombie / squelette alterné, qui apparaissent quand un joueur approche de leur île ; ils ne bloquent rien.</li>
</ul>

<div class="page"></div>
<h2>8. Niveau -3 — Salle 3 (derrière la porte rouge)</h2>
<img class="plein" src="plan_3a_tunnel.png">
<ul>
<li><b>Le tunnel</b> : du bout du couloir 3, un palier de briques, puis un <b>escalier de 53 marches</b> brutes et un <b>tunnel plat de 150 blocs</b>, creusés comme une grotte (largeur 7 à 13, plafond 6 à 9, trois chambres plus hautes, mousse, racines, lianes à baies, champignons lumineux).</li>
<li><b>Trois fosses</b> à franchir au grappin (4 trous de 6 à 7 blocs sur toute la largeur, lave 11 blocs plus bas) ; la deuxième est coupée par un <b>îlot</b>. Deux ancrages par bord ; chacun suffit seul à passer, dans les deux sens. Tomber renvoie au bord d'où l'on vient.</li>
<li><b>Quatre coffres</b> : niche de l'escalier (à pied), corniche haute de la chambre 1 (au grappin), îlot (au grappin), chambre 3 (après la dernière fosse). Butin <b>provisoire</b> (table <code>atelier:suspendu_tunnel</code>).</li>
</ul>
<img class="plein" src="plan_3b_arene.png">
<ul>
<li><b>Le gouffre et le pont en ruine</b> : un gouffre de roche de 15 de large (lave 35 blocs plus bas) ; le pont n'est plus qu'en <b>quatre tronçons</b> séparés par <b>trois trous de 6</b> ; deux ancrages par trou, au bout du tronçon d'en face.</li>
<li><b>L'arène</b> : une caverne ronde (27 de rayon, 80 de haut), un <b>lac de lave</b> au fond d'où le <b>Gardien</b> surgit au centre. Autour, une <b>rampe en spirale</b> de 4 de large (rayon 12,5 à 16,5, hors de portée de ses bras), de 9 à 44 de haut, <b>en ruine</b> : six trous (trois en bas, trois en haut), une colonne brisée portant un ancrage de chaque côté de chaque trou ; la <b>bande du milieu</b> (≈ 1,8 tour) est entière : c'est là qu'on se bat.</li>
<li>Au grappin seulement : le <b>balcon de la sortie</b> (15 au-dessus du sommet de la rampe, sortie scellée par des barreaux), une <b>alcove à coffre</b>, quatre <b>corniches</b> (refuges, ancrage encastré dans le mur). 39 ancrages dans toute la salle 3.</li>
</ul>

<h2>9. Les énigmes et leurs solutions</h2>
<table>
<tr><th>Où</th><th>Épreuve</th><th>Solution</th><th>Récompense / effet</th></tr>
<tr><td>Salle 1</td><td>Combat</td><td>Aller au centre, tuer les 3 zombies</td><td>Cinématique ; coffre de la <b>clé bleue</b></td></tr>
<tr><td>Salle 1</td><td>Porte 1</td><td>Clé bleue en main devant la porte bleue</td><td>La dalle se lève (cinématique) ; escalier de 27 marches</td></tr>
<tr><td>Sous-sol 1</td><td>Petite salle</td><td>Tuer le zombie et le squelette</td><td>La seconde porte bleue s'ouvre seule</td></tr>
<tr><td>Sous-sol 1</td><td>Mini-boss</td><td>Descendre l'échelle du balcon, vaincre le Rôdeur des Racines</td><td>Cinématique ; <b>coffre du grappin</b> sur l'estrade</td></tr>
<tr><td>Sous-sol 1</td><td>Zone haute</td><td>Grappin : sol (ou estrade) → <b>P2</b> → ancrage de la <b>corniche C</b> → hall ; puis F1 et F2 depuis le bord ouest de chaque fosse</td><td>Accès à la salle de l'énigme</td></tr>
<tr><td>Sous-sol 1</td><td>Énigme musicale</td><td>Bouton « D » : la mélodie se joue (lampes) ; la rejouer par clic droit sur les blocs musicaux numérotés du nord au sud dans l'ordre <b>2, 4, 1, 5, 3</b>. Erreur = on recommence ; les clics pendant la mélodie ne comptent pas.</td><td>Toutes les lampes s'allument ; coffre de la <b>clé jaune</b></td></tr>
<tr><td>Salle 1</td><td>Porte 2</td><td>Clé jaune en main</td><td>Escalier de 46 marches vers le sous-sol 2</td></tr>
<tr><td>Sous-sol 2</td><td>Leviers</td><td>Enchaîner les piliers bleus jusqu'à l'île 1 ; tirer les leviers <b>dans l'ordre 1 → 5</b> : chacun fait apparaître (en cinématique) les ancrages cyan qui mènent au suivant</td><td>Levier 5 : coffre de la <b>clé rouge</b> sur l'île I</td></tr>
<tr><td>Salle 1</td><td>Porte 3</td><td>Clé rouge en main</td><td>Palier, escalier de 53 marches, tunnel</td></tr>
<tr><td>Salle 3</td><td>Fosses, pont, anneau</td><td>Tirer l'ancrage au bout du tronçon d'en face, se laisser tomber dessus</td><td>Coffres du tunnel ; tête de pont ; hauts de l'anneau, balcon</td></tr>
<tr><td>Salle 3</td><td>Boss</td><td>Voir § 10</td><td>La sortie s'ouvre</td></tr>
</table>

<h2>10. Le boss : le Gardien des Racines</h2>
<ul>
<li>Un <b>golem d'arbre</b> géant, enfoncé dans le lac de lave au centre de l'arène (entité <code>donjon:golem</code> du mod). Il surgit 5 s après l'annonce, quand un joueur arrive en <b>tête de pont</b> (une seule fois).</li>
<li><b>Il se tourne lentement vers le joueur le plus proche</b> et <b>ouvre un point faible du côté opposé</b> : il faut faire le tour de l'anneau pour aller le frapper. Cinq points faibles (masque, plastron, poing, dos gauche, dos droit), des <b>bourgeons au bout de branches</b> qui sortent du corps : ce qu'on voit est exactement ce qu'on frappe. Un seul ouvert à la fois, pendant 20 s ; un faisceau de particules rouges le signale de loin. Il lance aussi des graines depuis son poing.</li>
<li><b>Frappable à l'épée</b> : pour chaque point faible et chaque orientation du golem (pas de 15°), une case de la rampe est à moins de 3 blocs de sa boîte (au pire 2,8 ; vérifié par script). Les trous de l'anneau ne font perdre aucune orientation.</li>
<li><b>Le grappin</b> sert à franchir les trous du haut de l'anneau, à rejoindre les corniches (refuges) et le balcon de la sortie.</li>
<li><b>Mort</b> : le mod appelle le tag <code>donjon:golem_mort</code> → la sortie s'ouvre, message « Le Gardien est vaincu ». <b>Chute</b> dans la lave : retour en tête de pont (ou au départ du pont si l'on tombait du pont). La remise à zéro tue le golem et referme la sortie.</li>
</ul>

<h2>11. Scénario attendu du joueur</h2>
<div class="etape"><b class="n">1</b> Il arrive par le couloir sud dans la <b>salle octogonale</b>. Au centre, trois zombies surgissent. Il les tue ; la caméra montre un coffre qui apparaît au milieu : la <b>clé bleue</b>.</div>
<div class="etape"><b class="n">2</b> Clé en main devant la <b>porte bleue</b> : la dalle se lève (cinématique). Il descend l'escalier de 27 marches.</div>
<div class="etape"><b class="n">3</b> Dans la <b>petite salle</b>, un zombie et un squelette ; vaincus, la seconde porte bleue s'ouvre. Il débouche sur le <b>balcon</b> de la grande salle, voit les piliers, l'estrade, et descend l'échelle.</div>
<div class="etape"><b class="n">4</b> Le <b>mini-boss</b> se réveille. Vaincu, la caméra montre le coffre du <b>grappin</b> sur l'estrade. Il monte à l'estrade et le prend.</div>
<div class="etape"><b class="n">5</b> Premier usage du grappin : il se hisse sur un <b>pilier</b>, puis sur <b>P2</b>, d'où il aperçoit l'ancrage de la <b>corniche</b> ; il tire, tombe dans le <b>hall</b>, franchit les <b>deux fosses</b> au grappin (s'il tombe, l'échelle de secours le remonte).</div>
<div class="etape"><b class="n">6</b> Dans la <b>salle de l'énigme</b>, deux monstres ; puis il appuie sur le bouton, écoute la mélodie et la rejoue (<b>2, 4, 1, 5, 3</b>) : le coffre de la <b>clé jaune</b> apparaît.</div>
<div class="etape"><b class="n">7</b> Il remonte, ouvre la <b>porte jaune</b>, descend les 46 marches et suit le <b>couloir-grotte</b> jusqu'à l'île d'entrée du sous-sol 2 : devant lui, huit îles au-dessus d'un vide de 45 blocs.</div>
<div class="etape"><b class="n">8</b> Il enchaîne les <b>piliers bleus</b> jusqu'à l'île du <b>levier 1</b> et le tire : la caméra montre un <b>pilier qui sort de la fosse</b> et l'ancrage qui apparaît. Il recommence île après île (leviers 2 à 5, deux dans des grottes au cœur des montagnes, un au bout du pont délabré), en évitant ou combattant les monstres des îles.</div>
<div class="etape"><b class="n">9</b> Le <b>levier 5</b> déclenche un long survol de la salle : les derniers ancrages apparaissent un à un, puis le coffre de la <b>clé rouge</b> sur l'île du coin. Il s'y rend au grappin et prend la clé.</div>
<div class="etape"><b class="n">10</b> Retour au rez-de-chaussée : la <b>porte rouge</b> s'ouvre. Palier, escalier de 53 marches, puis le <b>tunnel</b> : il ramasse le coffre de la niche, franchit les trois <b>fosses</b> au grappin (îlot et corniche à coffres en chemin).</div>
<div class="etape"><b class="n">11</b> Au bout, le <b>gouffre</b> : il traverse le <b>pont en ruine</b> au grappin, trou après trou, jusqu'à la <b>tête de pont</b>. Annonce « Le Gardien se réveille… » ; 5 s plus tard, le golem surgit de la lave.</div>
<div class="etape"><b class="n">12</b> Combat : il court sur la <b>rampe</b> pour atteindre le point faible ouvert du côté opposé, frappe à l'épée, se sert du grappin pour les trous du haut et les corniches. Le Gardien meurt : la <b>sortie</b> du balcon s'ouvre ; il y monte au grappin.</div>

<h2>12. Ce qui est contrôlé automatiquement</h2>
<ul>
<li><b>Vérificateurs</b> (simulation de la marche et du grappin 3, tirs enchaînés en l'air compris) : rez-de-chaussée (portes fermées / ouvertes, escaliers d'une marche à la fois, lumière) ; sous-sol 1 (à pied jusqu'au coffre du grappin mais ni piliers ni zone haute ; au grappin, tout ; au moins 4 piliers depuis l'estrade) ; sous-sol 2 (étape par étape : chaque levier k fait atteindre le levier k+1 et pas plus loin ; la clé seulement après le 5) ; salle 3 (à pied jusqu'à la première fosse et rien au-delà ; au grappin tout ; trous ≥ 5 blocs ; chaque ancrage suffit seul ; portée d'épée ; lumière).</li>
<li><b>Tests</b> (107) : géométrie, absence de recouvrement entre niveaux, fonctions de datapack (noms valides, fonctions appelées qui existent), cinématiques (caméra dans l'air, ≤ 1,2 bloc par tick, jamais de rotation), <b>chaque particule existe dans le jeu</b> (une seule particule fausse fait rejeter toute une fonction).</li>
</ul>

<h2>13. Points ouverts</h2>
<ul>
<li><b>Pas vu en jeu</b> : l'escalier de 27 marches en entier, la salle de l'énigme et la clé jaune, tout le sous-sol 2 (dont les cinématiques des leviers), toute la salle 3 (tunnel, pont, anneau, boss), la pose par étapes.</li>
<li>Contenu à définir par Luca : le contenu des <b>coffres jaunes</b>, le <b>butin</b> du tunnel (table provisoire), d'autres ennemis que zombies et squelettes.</li>
<li>Après le boss : la sortie mène à une petite salle <b>sans issue</b> ; pas de butin du boss ni de cinématique de victoire.</li>
<li>Écarts aux croquis, à valider par Luca : ancrages ajoutés ou déplacés au sous-sol 2 (salle doublée), deuxième ancrage cyan 3, ancrage cyan 4 devant la grotte ; corniche C ajoutée au sous-sol 1.</li>
<li>Le namespace <code>grappin</code> du mod (golem, ancrages) attend la validation de Duncan.</li>
</ul>

<h2>14. Règles du projet à respecter (CLAUDE.md)</h2>
<ul>
<li>Jamais de push sur <code>main</code> ; une branche et une PR par sujet ; <b>le merge est décidé par Duncan</b>.</li>
<li>Ne jamais changer <code>neo_version</code> (NeoForge 21.1.216) ; tout cible cette version.</li>
<li>« Le build passe » n'est pas « ça marche » : tester en jeu avec le client du dépôt (<code>mod/</code>, <code>./gradlew runClient</code>).</li>
<li>Un script ne suppose aucun état local ; livrer le monde prêt à copier (<code>donjon_suspendu.py --zip</code>).</li>
<li>Construire salle par salle d'après les croquis de Luca, avec ses codes couleurs ; signaler chaque écart au croquis.</li>
<li>Commits en français, qui expliquent le pourquoi.</li>
</ul>
</body></html>"""


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    plan_rdc()
    plan_ss1()
    plan_ss2()
    plan_ss3()
    plan_ss3(zoom=True)
    page = SORTIE / "dossier.html"
    page.write_text(html(), encoding="utf-8")
    pdf = SORTIE / "Donjon Suspendu - dossier de travail.pdf"
    edge = next((e for e in EDGE if e.exists()), None)
    if edge is None:
        print("Microsoft Edge introuvable : ouvrir", page, "et l'imprimer en PDF")
        return 1
    subprocess.run([str(edge), "--headless=new", "--disable-gpu", "--no-pdf-header-footer", f"--print-to-pdf={pdf}", page.as_uri()], check=True, timeout=180)
    print("PDF :", pdf, pdf.stat().st_size, "octets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
