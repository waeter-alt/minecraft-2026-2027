# [claude-build]
"""station_vannes.py - les portes de la Station : plus AUCUNE grille de bloc vanilla (demande de Luca, 25/11). Ce module dit quel bloc du mod pose chaque porte :
- les vannes d'eau (bouches de la Cuve, ecluses, cages et jets, colonne de bulles, grille de la salle C, porte du Poste, Exutoire) : `donjon:vanne_station`, un bloc mince
  (grille a lames dans un cadre de cuivre) dans le plan x, y ou z selon la porte, que l'eau traverse (waterlogged) ;
- les barrages de planches de la salle des lits : `donjon:planche_station`, que la Cape lestee brise d'un coup de tourbillon ;
- les cloisons du Vestiaire et du Sas : une entite `donjon:porte_station` (elle monte dans le linteau), derriere laquelle de la barriere invisible ferme le passage.
Pur calcul, sans dependance a la Station."""
from suspendu_outils import S

CENTRE_CUVE = (120, 95)             # station_acte1.CUVE, en cases : les bouches de la Cuve sont des portions de mur circulaire
VANNE = "donjon:vanne_station"
COURANT = "donjon:courant_station"
PLANCHE = "donjon:planche_station"
BARRIERE = S("barrier")


def axe(p, cases):
    """L'axe du plan d'une vanne : x (mince en z), y (a plat) ou z (mince en x). `cases` : toutes les cases de la porte (p en fait partie)."""
    xs, ys, zs = {c[0] for c in cases}, {c[1] for c in cases}, {c[2] for c in cases}
    if len(cases) == 1:
        return "y"
    if len(xs) == 1 and len(zs) > 1:
        return "z"
    if len(zs) == 1 and len(xs) > 1:
        return "x"
    if len(ys) == 1:
        return "y"
    return "z" if abs(p[0] - CENTRE_CUVE[0]) > abs(p[2] - CENTRE_CUVE[1]) else "x"          # un pan de mur courbe : mince dans le sens du rayon


def vanne(p, cases, eau):
    return S(VANNE, axis=axe(p, cases), waterlogged=eau)


def planche(axe_plan, eau):
    return S(PLANCHE, axis=axe_plan, waterlogged=eau)


def etat_txt(s):
    nom, props = s
    return nom + ("[" + ",".join(f"{k}={v}" for k, v in props) + "]" if props else "")


def commande_vanne(p, cases, eau):
    """Le bloc (texte d'une commande setblock) de la vanne en p."""
    return etat_txt(vanne(p, cases, eau))


def porte_station(centre_xz, y_bas, largeur, hauteur, yaw, cases):
    """La description d'une porte-cloison : l'entite (centre au sol, taille, orientation) et les cases de barriere qui ferment le passage."""
    return {"x": centre_xz[0], "y": y_bas, "z": centre_xz[1], "largeur": largeur, "hauteur": hauteur, "yaw": yaw, "cases": cases}
