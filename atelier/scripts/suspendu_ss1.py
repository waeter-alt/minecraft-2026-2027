# [claude-build]
"""suspendu_ss1.py - Donjon Suspendu, sous-sol 1 (« salle -1 », derriere la porte 1) : le plan, d'apres le dessin de Luca (GIMP, 940 x 940 px).
Les coordonnees ci-dessous sont celles du dessin, en pixels (SP) ; 1 px = 0.1 bloc (la grande salle fait 50 x 85 blocs, comme sur le dessin). `vers_plan` les place dans le repere du rez-de-chaussee
(le haut est le nord) de sorte que le bas de l'escalier de la porte 1 du rez-de-chaussee (en (44.5, 38) du plan, au niveau du sol du sous-sol) tombe sur le bas de l'escalier du dessin.
Ce module ne calcule que la geometrie (pas de blocs) : il sert au plan en image, puis a la construction."""
import math

ECHELLE = 0.1                                   # blocs par pixel du dessin
ESCALIER_BAS_SP = (815, 575)                    # le bas de l'escalier, sur le dessin
ESCALIER_BAS_PLAN = (44.5, 38.0)                # et dans le plan du rez-de-chaussee : la porte 1 y descend, 27 marches plus bas que son sol
DEC = (ESCALIER_BAS_PLAN[0] - ESCALIER_BAS_SP[0] * ECHELLE, ESCALIER_BAS_PLAN[1] - ESCALIER_BAS_SP[1] * ECHELLE)


def vers_plan(px, py):
    """Pixel du dessin -> (x, z) du plan du rez-de-chaussee, en blocs."""
    return (px * ECHELLE + DEC[0], py * ECHELLE + DEC[1])


def poly_plan(points):
    return [vers_plan(*p) for p in points]


# ---------------------------------------------------------------- les espaces (en pixels du dessin)
CAVERNE = [(20, 15), (285, 24), (400, 165), (468, 470), (535, 805), (65, 885)]           # la grande salle de gauche
BULBE = ((340, 92), 62, 70)                                                                # la salle arrondie en haut de la grande salle : ellipse (centre, rayon x, rayon y)
ESTRADE = ((322, 885), 178, 185)                                                           # le renfoncement en arc tout en bas : une estrade en demi-cercle, ou est le coffre du grappin
HALL_HAUT = [(380, 36), (490, 36), (905, 56), (915, 165), (380, 165)]                      # le haut : un grand couloir/hall avec deux fosses
FOSSES = [((500, 28), (610, 165)), ((745, 28), (810, 165))]                                # rectangles (coin nord-ouest, coin sud-est)
COULOIR_EST = [(840, 140), (916, 140), (916, 360), (840, 360)]                             # le long de l'est de la salle de l'enigme, du hall du haut jusqu'a sa porte est
SALLE_MUSICALE = [(490, 210), (835, 210), (835, 418), (490, 418)]                          # la salle de l'enigme du bloc musical (a droite)
BLOC_MUSICAL = [(512, 245), (585, 245), (585, 390), (512, 390)]                            # la structure de l'enigme (un mur de blocs musicaux)
PORTE_EST_SALLE_MUSICALE = (842, 326)                                                      # le rond en pointille : l'entree de la salle de l'enigme (a confirmer)
ESCALIER_DESSIN = [(770, 485), (860, 485), (860, 580), (775, 572)]                         # l'escalier hachure du dessin (le bas de l'escalier de la porte 1)
COULOIR_MONSTRES = [(815, 585), (700, 630), (592, 632)]                                    # du bas de l'escalier a la petite salle (ligne centrale)
LARGEUR_COULOIR_MONSTRES = 55
PETITE_SALLE = [(535, 565), (640, 565), (640, 685), (535, 685)]                            # la petite salle des deux monstres (la porte rose est son mur nord)
PORTE_ROSE = [(535, 565), (640, 565)]                                                      # s'ouvre quand les deux monstres sont vaincus
PASSAGE_ARC = [(468, 470), (500, 482), (560, 500), (620, 528), (640, 556), (640, 565), (535, 565), (485, 562)]     # derriere la porte : un passage en arc vers la grande salle

# ---------------------------------------------------------------- les points (en pixels du dessin)
ANCRAGES = [(95, 100), (320, 265), (105, 420), (360, 490), (140, 710),                                     # ronds bleus de la grande salle : chacun au dessus d'un pilier (une plateforme tout en haut)
            (410, 92),                                                                                    # AJOUT : l'ancrage de la corniche (a l'entree du hall, en haut), pour monter du dernier pilier sur la zone haute ; le rebord le cache depuis le sol et toute la plateforme de P2 le voit (grappin 3)
            (645, 95), (845, 90)]                                                                         # ronds bleus des deux fosses du hall, DEPLACES au dessus de la rive est de chaque fosse (essai en jeu : le crochet tire en ligne droite, un balancier depuis le bord s'amortit trop et n'atteint pas l'autre rive)
ANCRAGES_PLUS = [(240, 590), (230, 450)]                                                                          # AJOUT (demande de Luca : 1 ou 2 piliers de plus) : deux piliers en marches intermediaires entre l'estrade et les autres piliers
COFFRES = [(195, 195), (65, 255), (350, 385), (115, 510), (415, 645), (548, 45), (770, 48), (727, 308)]    # ronds jaunes : coffres (vides pour l'instant)
COFFRE_GRAPPIN = (320, 800)                                                                                # rond vert 1 : donne le grappin
COFFRE_CLE_2 = (635, 303)                                                                                  # rond vert 2 : donne la cle de la salle 2 (apres l'enigme musicale)
MINI_BOSS = (215, 360)                                                                                     # le gros M (plus tard)
MONSTRES_PETITE_SALLE = [(550, 615), (615, 598)]                                                           # les deux monstres qui gardent la porte rose
MONSTRES_SALLE_MUSICALE = [(715, 255), (800, 355)]
# les lignes courbes de la grande salle : le sol, par bandes (chaque ligne = une marche de plus vers le nord-ouest) ; ici quelques points de chacune
COURBES_SOL = [
    [(25, 150), (140, 100), (280, 50)], [(30, 230), (160, 170), (330, 105)], [(35, 330), (170, 260), (390, 190)], [(45, 470), (200, 380), (430, 280)],
    [(55, 610), (240, 500), (445, 380)], [(60, 720), (270, 610), (460, 480)], [(180, 830), (330, 700), (495, 640)], [(315, 690), (440, 600), (500, 560)],
]
