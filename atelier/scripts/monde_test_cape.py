# [claude-build]
"""
monde_test_cape.py - monde de test de la Cape lestee : un bassin pose sur la plaine, avec de quoi essayer chaque capacite (nage rapide, mode leste, tourbillon, grappin sous l'eau).
Le monde est un zip pret a copier : atelier/mondes/Test cape lestee.zip, a dezipper dans mod/run/client/saves/. La pose est automatique a la premiere ouverture (quelques secondes,
ne pas mettre en pause) ; aucune commande a taper. Remise a zero : /function atelier:cape_pose. Se redonner l'equipement : /function atelier:cape_kit.
  python atelier/scripts/monde_test_cape.py
Le bassin (coordonnees du monde ; sol de la plaine a y -61, premier bloc d'air a -60) : x 4..43, z 4..33, eau de y -59 a -48.
  - plage a l'est (x 28..41) : pente douce pour marcher au fond en mode leste et sortir de l'eau ;
  - cinq piliers a l'ouest (slalom, tours rapides) ; tunnel noye de 3x3 (x 21..25, sur toute la largeur) pour sentir la vitesse ;
  - zone de minage au fond (pierre, deepslate, fer) pour comparer la vitesse de minage ; trois noyes immobiles a 3, 4 et 6 blocs d'un repere en or (portee du tourbillon) ;
  - trois points d'ancrage du grappin : sur le rebord, au sommet d'un pilier (sous l'eau), au dessus du tunnel (sous l'eau) ;
  - trois tubes de verre suspendus au dessus de l'eau (vert, jaune, rouge), ancrage au plafond, hors de portee depuis la surface :
    il faut jaillir de l'eau (nage rapide vers le haut) et tirer en plein bond. Vert : bond en sprint ; jaune : tourbillon
    tire en profondeur (plus de 3 blocs sous la surface) en remontant ; rouge : tourbillon pres de la surface en visant a la verticale (projection). Hauteurs estimees d'apres la portee du crochet, pas encore verifiees en jeu.
"""
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import monde_atelier

NOM = "Test cape lestee"
RACINE = HERE.parent

SOL = -60                       # premier niveau au dessus de la plaine : le fond du bassin
EAU_HAUT = -48                  # dernier niveau d'eau
RIM = -46                       # sommet des murs
X0, X1, Z0, Z1 = 4, 43, 4, 33   # emprise exterieure
IX0, IX1, IZ0, IZ1 = 6, 41, 6, 31  # interieur

cmds = []


def c(s):
    cmds.append(s)


def fill(x1, y1, z1, x2, y2, z2, bloc, mode=""):
    c(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {bloc} {mode}".rstrip())


def poser(x, y, z, bloc):
    c(f"setblock {x} {y} {z} {bloc}")


# 1. Nettoyage de la zone (permet de reposer) puis coque du bassin
fill(X0 - 2, SOL, Z0 - 2, X1 + 16, SOL + 12, Z1 + 2, "air")
fill(X0 - 2, SOL + 13, Z0 - 2, X1 + 16, SOL + 25, Z1 + 2, "air")
# (decoupe en tranches : une commande fill est limitee a 32768 blocs)
fill(X0 - 2, SOL + 26, Z0 - 2, X1 + 16, SOL + 40, Z1 + 2, "air")
fill(X0 - 2, SOL + 41, Z0 - 2, X1 + 16, SOL + 52, Z1 + 2, "air")
fill(X0 - 2, SOL + 53, Z0 - 2, X1 + 16, SOL + 60, Z1 + 2, "air")
fill(X0, SOL, Z0, X1, RIM, Z1, "stone_bricks")
fill(IX0, SOL + 1, IZ0, IX1, EAU_HAUT, IZ1, "water")
fill(IX0, EAU_HAUT + 1, IZ0, IX1, RIM, IZ1, "air")
fill(IX0, SOL, IZ0, IX1, SOL, IZ1, "smooth_stone")

# 2. La plage : une marche par bloc, de x=29 (fond) a x=41 (au dessus de l'eau)
for k in range(1, 14):
    fill(28 + k, SOL + 1, IZ0, 28 + k, SOL + k, IZ1, "sandstone")

# 3. Piliers (2x2, du fond jusqu'a 1 bloc sous la surface)
PILIERS = [(9, 9), (9, 21), (12, 15), (15, 9), (15, 21)]
for x, z in PILIERS:
    fill(x, SOL + 1, z, x + 1, EAU_HAUT - 1, z + 1, "stone_bricks")

# 4. Tunnel noye de 3x3 sur toute la largeur du bassin
fill(21, SOL + 1, IZ0, 25, SOL + 5, IZ1, "stone_bricks")
fill(22, SOL + 2, IZ0, 24, SOL + 4, IZ1, "water")

# 5. Zone de minage
fill(6, SOL + 1, 26, 9, SOL + 3, 29, "stone")
fill(6, SOL + 1, 26, 9, SOL + 1, 29, "deepslate")
fill(7, SOL + 2, 27, 8, SOL + 2, 28, "iron_ore")

# 6. Repere de portee et cibles (noyes immobiles, sans IA)
poser(18, SOL, 18, "gold_block")
for dz in (3, 4, 6):
    c(f'summon drowned 18.5 {SOL + 1} {18 + dz}.5 {{NoAI:1b,PersistenceRequired:1b,CustomName:\'"Cible a {dz} blocs"\',Silent:1b}}')

# 7. Points d'ancrage du grappin
poser(23, RIM + 1, Z0, "donjon:ancrage")
poser(12, EAU_HAUT, 15, "donjon:ancrage")
poser(23, SOL + 6, 18, "donjon:ancrage")

# 7b. Ancrages hors de portee depuis la surface, a attraper en plein bond (sortie de l'eau vers le haut) :
#     tubes de verre suspendus au dessus de l'eau, ouverts par le bas, ancrage au plafond. Du plus facile au plus dur.
#     Hauteurs choisies d'apres la portee du crochet (30 blocs mesures depuis les pieds) : estimation, pas encore verifiee en jeu.
# Vert : bond en sprint (environ 3 blocs) ; jaune : elan du tourbillon tire de plus de 3 blocs sous la surface (environ 8) ;
#     rouge : projection du tourbillon pres de la surface en visant a la verticale (environ 8).
TUBES = [(16, 27, -17, "lime_stained_glass"), (27, 27, -12, "yellow_stained_glass"), (27, 11, -10, "red_stained_glass")]
for x, z, haut, verre in TUBES:
    fill(x - 3, RIM + 2, z - 3, x + 3, haut + 1, z + 3, verre)
    fill(x - 2, RIM + 2, z - 2, x + 2, haut, z + 2, "air")
    poser(x, haut, z, "donjon:ancrage")

# 8. Lumiere : lanternes marines encastrees dans le fond
for x in range(8, 40, 8):
    for z in (10, 20, 30):
        if not (21 <= x <= 25):
            poser(x, SOL, z, "sea_lantern")

# 9. Escalier exterieur (a l'est) pour redescendre du rebord
for k in range(0, 15):
    fill(X1 + 1 + k, SOL, 18, X1 + 1 + k, RIM - k, 20, "stone_bricks")

# 10. Regles du monde et accueil
c("gamerule doMobSpawning false")
c("gamerule doDaylightCycle false")
c("gamerule doWeatherCycle false")
c("gamerule commandBlockOutput false")
c("time set day")
c("weather clear")
c("function atelier:cape_kit")
c(monde_atelier.message_entree("Le bassin de test de la cape est pret.", "2 -60 18"))
c('tellraw @a [{"text":"Equipe la cape dans l\'emplacement cape d\'Accessories. Nage rapide : dans l\'eau, avance dans la direction du regard (sprint = elan). Leste : touche V. Tourbillon : clic gauche en nage rapide.","color":"yellow"}]')
c('tellraw @a [{"text":"A essayer : ","color":"gray"},{"text":"plage (marcher leste), slalom, tunnel, minage au fond (nord-ouest), cibles autour de l\'or, grappin sous l\'eau.","color":"white"}]')
c('tellraw @a [{"text":"Bond : ","color":"gray"},{"text":"les tubes de verre au dessus de l\'eau cachent un ancrage hors de portee depuis la surface. Jaillis de l\'eau en nage rapide et tire au grappin en l\'air. Vert : bond en sprint ; jaune : tourbillon en profondeur en remontant ; rouge : tourbillon pres de la surface en visant a la verticale (hauteurs estimees).","color":"white"}]')

KIT = [
    "gamemode survival @s",
    "give @s donjon:cape_lestee",
    "give @s donjon:grappin",
    "give @s minecraft:iron_sword",
    "give @s minecraft:iron_pickaxe",
    "give @s minecraft:cooked_beef 16",
]


def ecrire(save):
    dp = monde_atelier.datapack(save)
    fonctions = dp / "data/atelier/function"
    (fonctions / "cape_pose.mcfunction").write_text("# [claude-build] pose du bassin de test de la cape\n" + "\n".join(cmds) + "\n", encoding="utf-8")
    (fonctions / "cape_kit.mcfunction").write_text("# [claude-build] equipement de test\nexecute as @a run function atelier:cape_kit_un\n", encoding="utf-8")
    (fonctions / "cape_kit_un.mcfunction").write_text("# [claude-build]\n" + "\n".join(KIT) + "\n", encoding="utf-8")
    monde_atelier.installer_pose_auto(save, "cape_pose", "cape")


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as tmp:
        save = monde_atelier.trouver_ou_creer_monde(NOM, tmp)
        ecrire(save)
        print(f"{len(cmds)} commandes de pose")
        monde_atelier.zipper_monde(save, NOM, RACINE / "mondes")
