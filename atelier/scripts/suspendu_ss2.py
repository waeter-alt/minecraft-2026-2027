# [claude-build]
"""suspendu_ss2.py - Donjon Suspendu, sous-sol 2 (« salle -2 », derriere la porte jaune) : le plan, d'apres le croquis de Luca (2026-11-16, environ 950 x 960 px).
Les coordonnees ci-dessous sont celles du dessin, en pixels (SP) ; 1 px = 0,2 bloc depuis que Luca a demande une salle deux fois plus grande (elle fait environ 148 x 176 blocs ; avant : 0,1 et 74 x 88). `vers_local` les place dans le gabarit du donjon.
Lecture du croquis (confirmee par Luca) : tout le sol hachure est un ABIME mortel de 30 blocs entre des iles ; les traits epais etaient des garde-corps (retires depuis) ; les ronds bleus sont des ancrages toujours actifs sur
un pilier (plateforme) ; les ronds cyan numerotes sont des ancrages INVISIBLES jusqu'a ce qu'on tire le levier (carre orange) du meme numero ; les croix rouges sont des ennemis ; les ronds jaunes des coffres
(vides) ; le grand rond vert, la cle 3. L'ordre 1 a 5 est celui de l'enigme : chaque levier fait apparaitre l'ancrage qui mene a l'ile du levier suivant.
Ce module ne calcule que la geometrie (pas de blocs)."""
import math

ECHELLE = 0.2                                   # blocs par pixel du dessin : la salle est deux fois plus grande que sa premiere version (0,1), a la demande de Luca ; les hauteurs ne changent pas
PX0, PY0 = 168, 45                              # le coin nord-ouest de la salle sur le dessin
PX1, PY1 = 906, 926                             # et son coin sud-est
X0_SALLE, Z0_SALLE = 86, 23                     # ou ce coin tombe dans le gabarit (la salle y occupe x 86..233, z 23..200) ; z 23 met l'ile d'entree a z 117, dans l'axe du couloir
LARGEUR_CELLULES = 148
PROFONDEUR_CELLULES = 178


def vers_local(px, py):
    """Pixel du dessin -> (x, z) du gabarit, en blocs (flottants)."""
    return (X0_SALLE + (px - PX0) * ECHELLE, Z0_SALLE + (py - PY0) * ECHELLE)


def vers_cellule(px, py):
    x, z = vers_local(px, py)
    return (math.floor(x), math.floor(z))


def vers_px(x, z):
    """Centre d'une cellule du gabarit -> pixel du dessin."""
    return (PX0 + (x + 0.5 - X0_SALLE) / ECHELLE, PY0 + (z + 0.5 - Z0_SALLE) / ECHELLE)


# ---------------------------------------------------------------- les iles (en pixels du dessin)
# ellipse : (centre, rayon x, rayon y) ; polygone : liste de points ; chemin : (ligne, largeur en pixels)
ILES = {
    "E": {"nom": "l'entree", "ellipse": ((168, 516), 48, 37)},                                                       # le bout du couloir d'arrivee : un demi-disque a l'est du mur ouest
    "B": {"nom": "l'ile du levier 1", "ellipse": ((712, 178), 78, 62)},
    "C": {"nom": "la grande ile du levier 2", "ellipse": ((432, 292), 108, 98)},
    "A": {"nom": "l'ile du levier 3", "polygone": [(483, 47), (570, 47), (600, 62), (598, 82), (572, 102), (540, 108), (520, 100), (520, 75), (483, 72)]},
    "F": {"nom": "l'ile du levier 4", "ellipse": ((402, 662), 98, 72)},
    "G": {"nom": "l'ile du coffre", "ellipse": ((748, 585), 93, 66)},
    "H": {"nom": "l'ile du levier 5", "polygone": [(178, 925), (180, 862), (200, 822), (245, 798), (300, 790), (348, 800), (350, 836), (315, 856), (288, 880), (372, 886), (376, 925)]},
    "I": {"nom": "l'ile de la cle 3", "ellipse": ((908, 926), 190, 190)},                                              # le quart de disque du coin sud-est
}
PONT = ([(492, 640), (540, 645), (620, 634), (668, 630)], 26)               # le chemin etroit de l'ile du levier 4 a l'ile du coffre (largeur en pixels, soit 5 blocs) : un pont de bois delabre depuis la demande de Luca (34 px avant : un tablier de 7 blocs paraissait trop large pour un pont)
# Les iles des leviers 2 (C) et 5 (H), entourees en rouge par Luca : ce sont des MONTAGNES qui montent jusqu'au plafond, avec une grotte creusee au coeur ou se trouve le levier.
# Une bordure de `anneau` blocs reste a plat (le sol de l'ile : on y arrive en grappin et c'est la que sont les ennemis) ; le reste est un massif de roche, plein jusqu'au plafond.
# La chambre est une ellipse (centre en pixels du dessin, rayons en blocs) dont le plafond forme un dome de `haut_bord` a `haut_centre` blocs ; un tunnel de 3 de large et 4 de haut la relie a la bordure, en direction de `vers` (pixels : l'ancrage qui amene devant l'entree).
MONTAGNES = {
    "C": {"anneau": 6, "chambre": ((435, 288), 10.5, 8.0), "vers": (518, 252), "levier": 2, "coffres": [1],
          "lobes": [(155, 0.95, 4.2), (230, 0.9, 3.6), (95, 0.95, 3.8), (300, 0.9, 3.2)]},
    "H": {"anneau": 5, "chambre": ((245, 862), 5.5, 5.0), "vers": (333, 820), "levier": 5, "coffres": [],
          "lobes": [(200, 0.9, 3.0), (90, 0.95, 3.2), (320, 0.9, 2.8)]},
}
# La grotte est organique (voir suspendu_ss2_deco.creuser_grotte) : `chambre` = (centre en pixels, rayons en blocs) du coeur ; `lobes` = (angle en degres, distance au centre en fraction du rayon, rayon en blocs) des
# poches qui s'y ajoutent ; `vers` : l'ancrage devant l'entree, la direction du tunnel (qui serpente) ; `levier` et `coffres` : ce qu'on doit pouvoir atteindre dedans.
# Les rivieres de lave au fond de la fosse (points du dessin en pixels, largeur moyenne en blocs) : elles serpentent, ne coulent que sur le fond (jamais sous un pilier qui apparait avec un levier).
RIVIERES = [
    ([(170, 130), (260, 250), (330, 380), (300, 520), (380, 610), (330, 760), (260, 900)], 6),     # a l'ouest, du nord au sud
    ([(330, 380), (500, 350), (640, 450), (780, 420), (900, 500)], 5),                                 # un affluent vers l'est
    ([(380, 610), (560, 760), (700, 700), (820, 760), (900, 860)], 6),                                 # vers le sud-est
    ([(580, 40), (620, 200), (650, 330)], 4),                                                          # au nord
]

# ---------------------------------------------------------------- les points (en pixels du dessin)
PILIERS = [(302, 498), (453, 472), (573, 396), (662, 308)]                  # ronds bleus dans le vide : un pilier (plateforme) et son ancrage, toujours actifs ; du plus proche de l'entree au plus loin
ANCRAGE_BLEU_ILE = (762, 205)                                               # rond bleu sur l'ile du levier 1 : un ancrage de plus (sans pilier, il est sur l'ile)
CYAN = {1: [(559, 220), (518, 252)], 2: [(548, 82)], 3: [(668, 433), (705, 568)], 4: [(333, 820)], 5: [(483, 825), (593, 815), (703, 805), (815, 790)]}   # ancrages invisibles jusqu'au levier du meme numero
# SALLE x2 (demande de Luca) : le crochet porte toujours 30 blocs, alors que les distances ont doubles. Pour garder l'enigme jouable (le verificateur le controle), des points ont ete ajoutes ou decales :
# - levier 1 : (559, 220) est un point intermediaire dans le vide (il arrive avec un pilier), et l'ancrage (515, 233) devient (480, 255), a 10 blocs a l'interieur de la grande ile (au bord, on arrivait dans le vide) ;
# - levier 2 : l'ancrage (527, 92) devient (548, 82), a l'interieur de l'ile du levier 3 ;
# - levier 5 : le point du milieu (657, 812) est remplace par deux, (593, 815) et (703, 805), pour que chaque saut reste sous 25 blocs.
# DECALAGE : l'ancrage 4 passe de (295, 800) a (295, 830), 3 blocs plus a l'interieur de l'ile du levier 5 (au bout de la pointe, on arrivait avant l'ile et on tombait).
# AJOUT : le deuxieme ancrage du levier 3, (705, 568), au dessus de l'ile du coffre : l'ancrage 3 du dessin est au milieu du vide, a 9 blocs au nord de cette ile, et ne mene a rien tout seul (le verificateur
# le montre) ; on y arrive depuis le dernier pilier, puis on enchaine sur le second, comme pour les trois ancrages du levier 5
LEVIERS = {1: (718, 140), 2: (422, 280), 3: (545, 60), 4: (345, 668), 5: (245, 862)}
COFFRES = [(585, 63), (447, 297), (743, 625)]                               # ronds jaunes : coffres (vides pour l'instant)
COFFRE_CLE_3 = (874, 862)                                                   # rond vert : la cle 3 (la porte rouge)
ENNEMIS = {                                                                 # croix rouges, par ile
    "B": [(668, 170), (743, 175)], "C": [(527, 267)], "A": [(562, 88)],
    "F": [(320, 640), (365, 637), (425, 625), (402, 665), (445, 687)], "PONT": [(535, 645), (620, 632)],
    "G": [(700, 553), (775, 590), (708, 606)], "H": [(325, 810)], "I": [(872, 753), (790, 865)]}
# Les garde-corps (les traits epais du croquis) ont ete retires a la demande de Luca : le bord des iles donne directement sur l'abime.

# ---------------------------------------------------------------- le couloir d'arrivee (en blocs du gabarit)
# de la fin de l'escalier de la porte 2 (x 11 a 17, arrivee en z 97), puis tout droit vers l'est, le long de la grande salle du sous-sol 1 (qui s'arrete en z 109), jusqu'a l'ile d'entree de la salle -2 (en z 117)
COULOIR = [(14, 97.5), (14, 117), (85.5, 117)]
LARGEUR_COULOIR = 7
