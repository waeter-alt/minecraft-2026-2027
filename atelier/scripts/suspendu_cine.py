# [claude-build]
"""suspendu_cine.py - les cinematiques du Donjon Suspendu, a la Zelda : la camera quitte les joueurs de la salle pour montrer quelque chose (le coffre qui apparait, une porte qui se leve), puis les rend.
Principe (datapack seul, sans mod) : les joueurs presents passent en spectateur et regardent par les yeux d'un porte-armure invisible (la camera) qu'une fonction deplace a chaque tick le long d'un
chemin calcule ici (spline de Hermite, tangentes de Catmull-Rom, camera et cible lissees separement) ; a la fin, chacun est ramene a sa place, dans son mode de jeu. Maj quitte la camera : le joueur
est ramene tout de suite. Pendant la scene les joueurs sont en spectateur : figes, invulnerables.
Pur calcul : renvoie le texte des fonctions ; l'ecriture et la pose sont dans donjon_suspendu.py."""
import math

DUREE = 110                              # ticks (5,5 s)
LEVER = 20                               # tick ou la dalle d'une porte commence a se lever
SEUIL_SKIP = 1.5                         # un joueur a plus de 1,5 bloc de la camera ne la regarde plus (Maj) : on le ramene
TICKS_CHAMBRE = 6                        # on ne cherche pas ceux qui ont quitte la camera avant ce tick (le temps que le serveur les y amene)

CAMERA = "@e[type=minecraft:armor_stand,tag=cine_cam]"
CAMERA_UNE = "@e[type=minecraft:armor_stand,tag=cine_cam,limit=1]"
CAMERA_PROCHE = f"@e[type=minecraft:armor_stand,tag=cine_cam,distance=..{SEUIL_SKIP}]"


# ---------------------------------------------------------------- calcul du chemin
def chemin(cles):
    """Spline de Hermite a tangentes de Catmull-Rom (non uniformes) par les cles [(tick, (x, y, z))] ; renvoie f(tick) -> (x, y, z). Les extremites sont a l'arret."""
    ts = [c[0] for c in cles]
    ps = [c[1] for c in cles]
    n = len(cles)
    ms = [(0.0, 0.0, 0.0)]
    for i in range(1, n - 1):
        ms.append(tuple((ps[i + 1][k] - ps[i - 1][k]) / (ts[i + 1] - ts[i - 1]) for k in range(3)))
    ms.append((0.0, 0.0, 0.0))

    def position(t):
        t = min(max(t, ts[0]), ts[-1])
        i = max(j for j in range(n - 1) if ts[j] <= t) if t < ts[-1] else n - 2
        h = ts[i + 1] - ts[i]
        s = (t - ts[i]) / h
        h00, h10, h01, h11 = 2 * s ** 3 - 3 * s ** 2 + 1, s ** 3 - 2 * s ** 2 + s, -2 * s ** 3 + 3 * s ** 2, s ** 3 - s ** 2
        return tuple(h00 * ps[i][k] + h10 * h * ms[i][k] + h01 * ps[i + 1][k] + h11 * h * ms[i + 1][k] for k in range(3))

    return position


def angles(camera, cible):
    """(yaw, pitch) en degres d'une camera en `camera` qui regarde `cible` (yaw 0 vers le sud (+Z), 90 vers l'ouest ; pitch positif vers le bas)."""
    dx, dy, dz = cible[0] - camera[0], cible[1] - camera[1], cible[2] - camera[2]
    return math.degrees(math.atan2(-dx, dz)), -math.degrees(math.atan2(dy, math.hypot(dx, dz)))


def images(cles_camera, cles_cible, duree=DUREE):
    """Les images 1..duree : [(x, y, z, yaw, pitch)], en coordonnees du gabarit."""
    pos, vise = chemin(cles_camera), chemin(cles_cible)
    sortie = []
    for t in range(1, duree + 1):
        p, c = pos(t), vise(t)
        yaw, pitch = angles(p, c)
        sortie.append((p[0], p[1], p[2], yaw, pitch))
    return sortie


# ---------------------------------------------------------------- les deux scenes
ECHELLES = [(1, 1.0), (35, 0.8), (70, 0.5), (DUREE, 0.3)]               # de combien la camera est eloignee de sa cible a chaque cle (1 = au depart, 0 = sur la cible)


def travelling(cible, depart, echelles=ECHELLES):
    """Un travelling avant : la camera avance en ligne droite vers `cible`, depuis `depart`, et regarde toujours `cible` : l'orientation ne change donc JAMAIS pendant la scene. C'est ce qui rend
    le mouvement fluide : le serveur n'envoie l'orientation d'une entite que par pas de 1,4 degre, une rotation lente se verrait par a-coups ; une position, elle, est precise au 4096e de bloc."""
    dx, dy, dz = depart[0] - cible[0], depart[1] - cible[1], depart[2] - cible[2]
    cam = [(t, (cible[0] + dx * s, cible[1] + dy * s, cible[2] + dz * s)) for t, s in echelles]
    vise = [(1, cible), (DUREE, cible)]
    return cam, vise


def scene_coffre(infos, y0):
    """Le coffre qui apparait : un travelling avant depuis l'est de la salle, en plongee legere (on regarde vers l'ouest : le regard ne passe jamais par le nord, ou l'angle saute de 180 a -180)."""
    cx, _, cz = infos["coffre"]
    coffre = (cx + 0.5, y0 + 1.5, cz + 0.5)
    return travelling(coffre, (coffre[0] + 12.0, coffre[1] + 4.5, coffre[2] + 3.0))


def scene_coffre_generique(coffre, derriere=16.0, haut=9.0, lateral=4.0):
    """Un coffre quelconque qui apparait (celui de l'estrade du sous-sol 1) : un travelling avant depuis le nord, en plongee, un peu de cote ; on regarde vers le sud (yaw proche de 0, loin de +-180 ou l'angle saute)."""
    cx, cy, cz = coffre
    c = (cx + 0.5, cy + 0.5, cz + 0.5)
    return travelling(c, (c[0] + lateral, c[1] + haut, c[2] - derriere))


def scene_porte_generique(porte_xz, interieur, y0, distance=11.0, lateral=3.5):
    """Un travelling avant vers une porte, depuis l'interieur de la salle (`interieur` : vecteur unitaire de la porte vers la salle), un peu de cote (pas pile de face ; pour une porte qui regarde
    le nord, cela evite aussi de viser exactement le nord) ; la camera finit presque devant la porte et, comme elle ne tourne pas, regarde au bout du couloir une fois la dalle partie."""
    nx, nz = interieur
    lx, lz = -nz, nx
    porte = (porte_xz[0], y0 + 4.0, porte_xz[1])
    depart = (porte[0] + nx * distance + lx * lateral, y0 + 5.5, porte[2] + nz * distance + lz * lateral)
    return travelling(porte, depart, [(1, 1.0), (35, 0.8), (70, 0.5), (DUREE, 0.27)])


# ---------------------------------------------------------------- les fonctions de datapack
def _f(x):
    return f"{x:.3f}"


def _cadres(nom, cles, evenements, orig, duree=DUREE):
    """Fonction `suspendu_cine_<nom>` : a chaque tick, place la camera ; evenements {tick: [commandes]}."""
    cam, vise = cles
    lignes = [f"# [claude-build] Cinematique « {nom} » : une image par tick (la camera), et ses evenements (sons, effets)."]
    for t, (x, y, z, yaw, pitch) in enumerate(images(cam, vise, duree), start=1):
        lignes.append(f"execute if score #t cine matches {t} run tp {CAMERA} {_f(x + orig[0])} {_f(y + orig[1])} {_f(z + orig[2])} {_f(yaw)} {_f(pitch)}")
    for t in sorted(evenements):
        for c in evenements[t]:
            lignes.append(f"execute if score #t cine matches {t} run {c}")
    return "\n".join(lignes) + "\n"


def son(note, hauteur, volume=1):
    return f"execute as @a[tag=cine] at @s run playsound minecraft:block.note_block.{note} master @s ~ ~ ~ {volume} {hauteur}"


def fonctions(infos, salle1, orig, centre, couleurs, extras=None, coffres=None, perso=None):
    """Toutes les fonctions des cinematiques : {nom: texte}. `centre` : coordonnees monde (texte) du centre de la salle ; `couleurs` : {numero de porte: nom de la couleur} ; `extras` : des portes
    en plus {nom: (scene, centre monde en texte)} (la porte du sous-sol 1) ; `coffres` : des coffres en plus {nom: (scene, centre monde en texte, fonction qui pose le coffre, texte final)} (le coffre de l'estrade) ;
    `perso` : des scenes sur mesure {nom: {"scene": (cles de la camera, cles de la cible), "duree": nombre de ticks, "centre": centre monde en texte, "rayon": blocs, "evenements": {tick: [commandes]}}} (la cle du sous-sol 2 :
    un long vol au dessus de la salle, qui dure plus que les 110 ticks des autres scenes)."""
    f = {}
    extras = extras or {}
    coffres = coffres or {}
    perso = perso or {}
    ids = {"coffre": 10, **{f"porte{n}": 24 + k for k, n in enumerate(sorted(extras))}, **{n: 40 + k for k, n in enumerate(sorted(coffres))},     # les portes a cle 1, 2, 3 n'ont plus de cinematique (demande de Luca)
           **{n: 60 + k for k, n in enumerate(sorted(perso))}}
    durees = {n: spec["duree"] for n, spec in perso.items()}
    rayons = {nom: 45 for nom in ids}
    scenes = {"coffre": scene_coffre(infos, salle1.Y0)}
    centres = {nom: centre for nom in ids}
    for n, (scene, centre_n) in extras.items():
        scenes[f"porte{n}"] = scene
        centres[f"porte{n}"] = centre_n
    for n, (scene, centre_n, _, _) in coffres.items():
        scenes[n] = scene
        centres[n] = centre_n
        rayons[n] = 70                                                    # la grande salle est vaste : tous ceux qui s'y battent voient le coffre apparaitre
    for n, spec in perso.items():
        scenes[n] = spec["scene"]
        centres[n] = spec["centre"]
        rayons[n] = spec["rayon"]
    evenements = {
        "coffre": {2: ['title @a[tag=cine] actionbar {"text":"…","color":"gray"}'], 25: ["function atelier:suspendu_s1_coffre", "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1.2"],
                   34: [son("chime", 0.75)], 38: [son("chime", 0.94)], 42: [son("chime", 1.12)], 47: [son("chime", 1.5), "execute as @a[tag=cine] at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.6 1"]}}
    for n, (_, _, pose, texte) in coffres.items():
        evenements[n] = {2: ['title @a[tag=cine] actionbar {"text":"…","color":"gray"}'], 25: [f"function atelier:{pose}", "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1.2"],
                         34: [son("chime", 0.75)], 38: [son("chime", 0.94)], 42: [son("chime", 1.12)], 47: [son("chime", 1.5), "execute as @a[tag=cine] at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.6 1"],
                         62: [f'title @a[tag=cine] actionbar {{"text":"{texte}","color":"gold"}}']}
    for n, spec in perso.items():
        evenements[n] = spec["evenements"]
    for n in sorted(extras):
        evenements[f"porte{n}"] = {2: ['title @a[tag=cine] actionbar {"text":"…","color":"gray"}'], LEVER: [f"function atelier:suspendu_p{n}_lever"],
                                   56: [son("chime", 1.0)], 60: [son("chime", 1.26)], 64: [son("chime", 1.5), "execute as @a[tag=cine] at @s run playsound minecraft:block.amethyst_block.chime master @s ~ ~ ~ 1 1"],
                                   66: [f'title @a[tag=cine] actionbar {{"text":"La porte {couleurs[n]} est ouverte","color":"aqua"}}']}
    for nom, cles in scenes.items():
        f[f"suspendu_cine_{nom}"] = _cadres(nom, cles, evenements[nom], orig, durees.get(nom, DUREE))
        x, y, z, yaw, pitch = images(*cles)[0]
        f[f"suspendu_cine_debut_{nom}"] = "\n".join([
            f"# [claude-build] Lance la cinematique « {nom} » : la camera, puis les joueurs de la salle (en spectateur, tournes vers la camera).",
            f"scoreboard players set #id cine {ids[nom]}", "scoreboard players set #t cine 1", "scoreboard players set #nb cine 0",
            f"summon minecraft:armor_stand {_f(x + orig[0])} {_f(y + orig[1])} {_f(z + orig[2])} {{Invisible:1b,Marker:1b,NoGravity:1b,Silent:1b,Invulnerable:1b,Tags:[\"cine_cam\"],Rotation:[{_f(yaw)}f,{_f(pitch)}f]}}",
            f"execute positioned {centres[nom]} as @a[distance=..{rayons[nom]},gamemode=!spectator] at @s run function atelier:suspendu_cine_joueur"]) + "\n"
    f["suspendu_cine_joueur"] = "\n".join([
        "# [claude-build] Un joueur entre dans la cinematique : sa place et son mode de jeu sont gardes, puis il regarde par la camera. Execute en tant que ce joueur.",
        "tag @s add cine", "scoreboard players add #nb cine 1", "scoreboard players operation @s cine = #nb cine",
        "execute store result score @s cine_mode run data get entity @s playerGameType",
        "summon minecraft:marker ~ ~ ~ {Tags:[\"cine_retour\",\"cine_neuf\"]}", "tp @e[type=minecraft:marker,tag=cine_neuf] @s",
        "scoreboard players operation @e[type=minecraft:marker,tag=cine_neuf] cine = @s cine", "tag @e[type=minecraft:marker,tag=cine_neuf] remove cine_neuf",
        "gamemode spectator @s", f"spectate {CAMERA_UNE}"]) + "\n"
    f["suspendu_cine_retour_joueur"] = "\n".join([
        "# [claude-build] Rend un joueur : il quitte la camera, retrouve sa place (et son regard) et son mode de jeu. Execute en tant que ce joueur.",
        "spectate", "scoreboard players operation #cherche cine = @s cine", "tag @s add cine_cible",
        "execute as @e[type=minecraft:marker,tag=cine_retour] if score @s cine = #cherche cine run tp @a[tag=cine_cible,limit=1] @s",
        "execute as @e[type=minecraft:marker,tag=cine_retour] if score @s cine = #cherche cine run kill @s",
        "execute if score @s cine_mode matches 0 run gamemode survival @s", "execute if score @s cine_mode matches 1 run gamemode creative @s",
        "execute if score @s cine_mode matches 2 run gamemode adventure @s", "tag @s remove cine_cible", "tag @s remove cine"]) + "\n"
    f["suspendu_cine_fin"] = "\n".join([
        "# [claude-build] Fin de la cinematique (ou arret force) : tous les joueurs sont rendus, la camera disparait.",
        "execute as @a[tag=cine] run function atelier:suspendu_cine_retour_joueur", f"kill {CAMERA}", "kill @e[type=minecraft:marker,tag=cine_retour]",
        "scoreboard players set #t cine 0", "scoreboard players set #id cine 0"]) + "\n"
    f["suspendu_cine_cadre"] = "\n".join([
        "# [claude-build] Un tick de cinematique (appelee quand #t cine est au moins 1).",
        "scoreboard players add #t cine 1",
        *[f"execute if score #id cine matches {i} run function atelier:suspendu_cine_{nom}" for nom, i in ids.items()],
        f"execute if score #t cine matches {TICKS_CHAMBRE}.. as @a[tag=cine] at @s unless entity {CAMERA_PROCHE} run function atelier:suspendu_cine_retour_joueur",
        f"execute {''.join(f'unless score #id cine matches {ids[n]} ' for n in sorted(perso))}if score #t cine matches {DUREE + 2}.. run function atelier:suspendu_cine_fin",      # les scenes de 110 ticks s'arretent d'elles-memes
        *[f"execute if score #id cine matches {ids[n]} if score #t cine matches {durees[n] + 2}.. run function atelier:suspendu_cine_fin" for n in sorted(perso)]]) + "\n"
    return f
