# [claude-build] Le Scaphandrier rouille, mini-boss du Donjon de la Station : ecrit atelier/modeles/station/scaphandrier.bbmodel (os,
# box UV, animations) et sa texture (assets/donjon/textures/entity/scaphandrier.png). Ancien ouvrier de la station devenu une armure de
# plongee vivante : casque de laiton a trois hublots (celui de face lumineux, qui se fissure puis eclate), combinaison de cuivre
# vert-de-grise, tuyaux d'air arraches, semelles de plomb, cle a molette, lance-harpon a chaine. Repere Blockbench : y vers le haut,
# pieds en 0, l'avant vers -z, 16 unites par bloc. Ensuite : bbmodel_vers_java.py <bbmodel> <java> --nom Scaphandrier.
# Une fois retouche dans Blockbench, le .bbmodel est la source : ne plus relancer ce script.
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import donjon_3d as d3  # noqa: E402

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "atelier/modeles/station/scaphandrier.bbmodel"
TEXTURE = RACINE / "atelier/donjon/arene/src/main/resources/assets/donjon/textures/entity/scaphandrier.png"

LAITON = [(118, 84, 34), (160, 118, 48), (200, 158, 70), (236, 204, 120)]
CUIVRE = [(46, 92, 78), (62, 122, 102), (84, 148, 122), (150, 96, 58)]          # vert-de-gris, avec des plaques de cuivre nu
PLOMB = [(46, 48, 56), (62, 64, 74), (80, 82, 92)]
CAOUTCHOUC = [(30, 26, 24), (44, 38, 34), (58, 50, 44)]
VITRE = [(120, 200, 160), (170, 240, 200), (230, 255, 235)]
VITRE_SOMBRE = [(30, 44, 44), (44, 62, 60)]
TRANSPARENT = (1, 2, 3)                                                          # couleur repere, rendue transparente apres coup


def _peintre(matiere, gemme=None):
    """Les matieres du scaphandrier ; le reste (fer, fer_sombre...) vient de donjon_3d."""
    def laiton(x, y, l, h, a, g=None):
        if l >= 5 and x in (1, l - 2) and y % 3 == 1:
            return LAITON[3]                                                     # rivets le long des bords
        k = 2 if y < h / 3 else 1 if y < 2 * h / 3 else 0                        # plus clair en haut : la lumiere vient d'en haut
        if a.random() < 0.08:
            k = max(0, min(2, k + a.choice((-1, 1))))
        return LAITON[k]

    def cuivre(x, y, l, h, a, g=None):
        if y % 8 == 7:
            return CUIVRE[0]                                                     # coutures de la combinaison
        tache = ((x // 3) * 7 + (y // 3) * 13) % 9
        if tache == 0:
            return CUIVRE[3] if a.random() < 0.7 else CUIVRE[1]                  # plaques de cuivre nu sous le vert-de-gris
        k = 2 if tache in (3, 5) else 1
        return CUIVRE[k if a.random() > 0.12 else 0]

    def plomb(x, y, l, h, a, g=None):
        return PLOMB[a.choice((0, 1, 1, 2))]

    def caoutchouc(x, y, l, h, a, g=None):
        return CAOUTCHOUC[0] if y % 3 == 0 else CAOUTCHOUC[a.choice((1, 2))]

    def vitre(x, y, l, h, a, g=None):
        if x + y < 3:
            return VITRE[2]
        return VITRE[a.choice((0, 0, 1))]

    def vitre_sombre(x, y, l, h, a, g=None):
        return VITRE_SOMBRE[a.choice((0, 1))]

    def fissure(x, y, l, h, a, g=None):
        on = abs((x - l / 2) - (y - h / 2) * 0.7) < 0.6 or abs((x - l / 3) + (y - h / 3)) < 0.6
        return (24, 34, 30) if on else TRANSPARENT

    def trou(x, y, l, h, a, g=None):
        return (10, 14, 14) if a.random() < 0.85 else (40, 60, 56)

    propres = {"laiton": laiton, "cuivre": cuivre, "plomb": plomb, "caoutchouc": caoutchouc, "vitre": vitre,
               "vitre_sombre": vitre_sombre, "fissure": fissure, "trou": trou}
    return propres[matiere] if matiere in propres else _peintre_base(matiere, gemme)


_peintre_base = d3.peintre
d3.peintre = _peintre


def scaphandrier():
    m = d3.Modele("scaphandrier", 128)
    os_ = m.os_
    os_("racine", None, (0, 0, 0))
    os_("corps", "racine", (0, 14, 0))
    os_("casque", "corps", (0, 29, 0))
    for n in ("fissure1", "fissure2", "hublot_brise"):
        os_(n, "casque", (0, 29, 0))                                             # os a part : le rendu les montre selon la vie
    os_("bras_g", "corps", (8, 27, 0))
    os_("cle", "bras_g", (9, 12, 0))
    os_("bras_d", "corps", (-8, 27, 0))
    os_("harpon", "bras_d", (-11.75, 9, 0))
    os_("tuyaux", "corps", (0, 27, 5))
    os_("jambe_g", "racine", (3.5, 14, 0))
    os_("jambe_d", "racine", (-3.5, 14, 0))
    c = m.cube
    for s, j in ((1, "jambe_g"), (-1, "jambe_d")):
        x0, x1 = (1, 6) if s > 0 else (-6, -1)
        c(j, f"cuisse_{j}", "cuivre", (x0, 6, -2.5), (x1, 14, 2.5))
        c(j, f"botte_{j}", "plomb", (x0 - 0.5, 1.5, -3.5), (x1 + 0.5, 6, 3))
        c(j, f"semelle_{j}", "plomb", (x0 - 1, 0, -4.5), (x1 + 1, 1.5, 3.5))
    c("corps", "torse", "cuivre", (-7, 14, -4.5), (7, 28, 4.5))
    c("corps", "ceinture", "plomb", (-7.5, 14, -5), (7.5, 17, 5))
    c("corps", "plastron", "laiton", (-5, 18, -5.5), (5, 26, -4.5))
    for k, (x, y) in enumerate(((-3.5, 24.5), (3.5, 24.5), (-3.5, 19.5), (3.5, 19.5))):
        c("corps", f"poids{k}", "plomb", (x - 1, y - 1, -6.5), (x + 1, y + 1, -5.5))
    c("corps", "collerette", "laiton", (-8, 27, -6), (8, 29.5, 6))
    for k, x in enumerate((-6.5, -2, 2, 6.5)):
        c("corps", f"boulon{k}", "fer", (x - 0.75, 27.5, -6.75), (x + 0.75, 29, -6))
    c("corps", "compresseur", "cuivre", (-4, 17, 4.5), (4, 27, 8.5))
    c("tuyaux", "tuyau_g", "caoutchouc", (2, 13, 7), (3.5, 27, 8.5), ("z", -22.5))
    c("tuyaux", "tuyau_d", "caoutchouc", (-3.5, 11, 7), (-2, 26, 8.5), ("z", 22.5))
    c("tuyaux", "tuyau_bout", "laiton", (-4, 10, 6.5), (-1.5, 12, 9), ("z", 22.5))
    # le casque : une boule de laiton, trois hublots ; le hublot de face est lumineux, se fissure puis eclate (GolemModele-like : par le code)
    c("casque", "dome", "laiton", (-5, 30, -5), (5, 40.5, 5))
    c("casque", "calotte", "laiton", (-3.5, 40.5, -3.5), (3.5, 42, 3.5))
    c("casque", "flanc_g", "laiton", (5, 31, -3.5), (6.5, 39.5, 3.5))
    c("casque", "flanc_d", "laiton", (-6.5, 31, -3.5), (-5, 39.5, 3.5))
    c("casque", "flanc_dos", "laiton", (-3.5, 31, 5), (3.5, 39.5, 6.5))
    c("casque", "flanc_avant", "laiton", (-4.5, 30.5, -6), (4.5, 39.5, -5))
    c("casque", "valve", "fer", (-1, 41.5, -1), (1, 43, 1))
    c("casque", "cadre_haut", "laiton", (-4.5, 38.5, -7), (4.5, 39.5, -6))
    c("casque", "cadre_bas", "laiton", (-4.5, 30.5, -7), (4.5, 31.5, -6))
    c("casque", "cadre_g", "laiton", (3.5, 31.5, -7), (4.5, 38.5, -6))
    c("casque", "cadre_d", "laiton", (-4.5, 31.5, -7), (-3.5, 38.5, -6))
    c("casque", "grille_v", "fer_sombre", (-0.4, 31.5, -6.9), (0.4, 38.5, -6.6))
    c("casque", "lueur_hublot", "vitre", (-3.5, 31.5, -6.5), (3.5, 38.5, -6))
    c("fissure1", "fissure1", "fissure", (-3.4, 31.6, -6.6), (3.4, 38.4, -6.55))
    c("fissure2", "fissure2", "fissure", (-3.4, 31.6, -6.65), (3.4, 38.4, -6.6))
    c("hublot_brise", "hublot_brise", "trou", (-3.5, 31.5, -6.55), (3.5, 38.5, -6.1))
    c("casque", "hublot_g", "vitre_sombre", (6.5, 33, -2), (7, 37, 2))
    c("casque", "hublot_d", "vitre_sombre", (-7, 33, -2), (-6.5, 37, 2))
    for s, b in ((1, "bras_g"), (-1, "bras_d")):
        x0, x1 = (7, 11.5) if s > 0 else (-11.5, -7)
        c(b, f"epaule_{b}", "laiton", (x0, 22, -3.5), (x1, 29, 3.5))
        c(b, f"bras_{b}", "cuivre", (x0 + 0.5, 14, -2.5), (x1 - 0.5, 22, 2.5))
        c(b, f"gant_{b}", "caoutchouc", (x0, 10, -3), (x1, 14, 3))
    c("cle", "cle_manche", "fer", (8.25, 11, -12), (9.75, 12.5, -1))
    c("cle", "cle_base", "fer", (7, 10, -15), (11, 11.5, -12))
    c("cle", "cle_mors_g", "fer", (9.5, 11.5, -15), (11, 14, -12))
    c("cle", "cle_mors_d", "fer", (7, 11.5, -15), (8.5, 14, -12))
    # lance-harpon fixe sur l'avant-bras, dans l'axe du bras : bras tendu (viser), il pointe droit devant
    c("harpon", "canon", "fer_sombre", (-13, 9, -1.5), (-10.5, 21, 1.5))
    c("harpon", "bobine", "fer", (-12.5, 15, 1.5), (-9.5, 19, 4))
    c("harpon", "pointe", "fer", (-12.25, 5.5, -0.5), (-11.25, 9, 0.5))
    c("harpon", "ailette_g", "fer", (-11.25, 7.5, -0.25), (-10.25, 9, 0.25))
    c("harpon", "ailette_d", "fer", (-13.25, 7.5, -0.25), (-12.25, 9, 0.25))
    Z = (0, 0, 0)
    anims = {
        "repos": (3.0, "loop", {"corps": {"position": [(0, Z), (1.5, (0, -0.3, 0)), (3.0, Z)]},
                                "casque": {"rotation": [(0, Z), (1.0, (0, 5, 0)), (2.0, (0, -5, 0)), (3.0, Z)]},
                                "tuyaux": {"rotation": [(0, Z), (1.5, (6, 0, 0)), (3.0, Z)]}}),
        "marche": (1.6, "loop", {"jambe_g": {"rotation": [(0, Z), (0.4, (20, 0, 0)), (1.2, (-20, 0, 0)), (1.6, Z)]},
                                 "jambe_d": {"rotation": [(0, Z), (0.4, (-20, 0, 0)), (1.2, (20, 0, 0)), (1.6, Z)]},
                                 "corps": {"rotation": [(0, Z), (0.4, (0, 0, 3)), (1.2, (0, 0, -3)), (1.6, Z)]},
                                 "bras_g": {"rotation": [(0, Z), (0.4, (-10, 0, 0)), (1.2, (10, 0, 0)), (1.6, Z)]}}),
        "coup": (0.9, "once", {"bras_g": {"rotation": [(0, Z), (0.4, (150, 0, 0)), (0.6, (40, 0, 0)), (0.9, Z)]},
                               "corps": {"rotation": [(0, Z), (0.4, (0, 15, 0)), (0.6, (0, -20, 0)), (0.9, Z)]}}),
        "viser": (1.0, "hold", {"bras_d": {"rotation": [(0, Z), (0.4, (85, 0, 0)), (1.0, (85, 0, 0))]},
                                "casque": {"rotation": [(0, Z), (0.4, (-5, 0, 0)), (1.0, (-5, 0, 0))]}}),
        "tirer": (0.4, "hold", {"bras_d": {"rotation": [(0, (85, 0, 0)), (0.1, (65, 0, 0)), (0.4, (85, 0, 0))]},
                                "corps": {"position": [(0, Z), (0.1, (0, 0, 1)), (0.4, Z)]}}),
        "coince": (1.5, "loop", {"bras_d": {"rotation": [(0, (85, 0, 0)), (0.4, (70, 0, 0)), (0.8, (95, 0, 0)), (1.2, (72, 0, 0)), (1.5, (85, 0, 0))]},
                                 "corps": {"rotation": [(0, (-10, 0, 0)), (0.75, (-14, 0, 4)), (1.5, (-10, 0, 0))],
                                           "position": [(0, (0, 0, 1.5)), (1.5, (0, 0, 1.5))]},
                                 "jambe_g": {"rotation": [(0, (-12, 0, 0)), (1.5, (-12, 0, 0))]},
                                 "jambe_d": {"rotation": [(0, (10, 0, 0)), (1.5, (10, 0, 0))]}}),
        "mort": (2.0, "once", {"jambe_g": {"rotation": [(0, Z), (0.8, (70, 0, 0)), (2.0, (70, 0, 0))]},
                               "jambe_d": {"rotation": [(0, Z), (0.8, (70, 0, 0)), (2.0, (70, 0, 0))]},
                               "corps": {"position": [(0, Z), (0.8, (0, -6, 0)), (2.0, (0, -12, 0))],
                                         "rotation": [(0, Z), (0.8, (-8, 0, 0)), (2.0, (70, 0, 0))]},
                               "bras_g": {"rotation": [(0, Z), (2.0, (0, 0, 25))]},
                               "bras_d": {"rotation": [(0, Z), (2.0, (0, 0, -25))]}}),
    }
    return m, anims


def main():
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    m, anims = scaphandrier()
    places = m.ranger()
    tex = m.texture(places)
    px = tex.load()
    for x in range(tex.width):
        for y in range(tex.height):
            if px[x, y][:3] == TRANSPARENT:
                px[x, y] = (0, 0, 0, 0)
    tex.save(TEXTURE)
    SORTIE.write_text(json.dumps(d3.bbmodel(m, places, tex, anims), indent=1) + "\n", encoding="utf-8")
    print(f"scaphandrier.bbmodel ecrit : {len(m.cubes)} cubes, {len(anims)} animations")


if __name__ == "__main__":
    main()
