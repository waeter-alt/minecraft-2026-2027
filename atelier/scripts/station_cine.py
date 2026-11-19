# [claude-build]
"""station_cine.py - les cinematiques du Donjon de la Station, sur le principe de celles du Donjon Suspendu (suspendu_cine.py) : les joueurs proches passent en spectateur et regardent par une
camera (un porte-armure invisible) deplacee a chaque tick, puis retrouvent leur place, leur regard et leur mode de jeu ; Maj quitte la camera. La camera ne tourne jamais (fluidite).
Ici chaque scene a sa duree. `fonctions(scenes, orig)` : scenes = {nom: {"cles": (cles de la camera, cles de la cible), "duree": ticks, "centre": texte monde, "rayon": blocs,
"evenements": {tick: [commandes]}}} ; renvoie {nom de fonction: texte}."""
import suspendu_cine as sc

CAMERA, CAMERA_UNE, CAMERA_PROCHE, TICKS_CHAMBRE = sc.CAMERA, sc.CAMERA_UNE, sc.CAMERA_PROCHE, sc.TICKS_CHAMBRE


def son(note, hauteur, volume=1):
    return sc.son(note, hauteur, volume)


def fonctions(scenes, orig):
    f = {}
    ids = {nom: 10 + k for k, nom in enumerate(sorted(scenes))}
    for nom, s in scenes.items():
        f[f"station_cine_{nom}"] = sc._cadres(nom, s["cles"], s["evenements"], orig, s["duree"])
        x, y, z, yaw, pitch = sc.images(*s["cles"], s["duree"])[0]
        f[f"station_cine_debut_{nom}"] = "\n".join([
            f"# [claude-build] Lance la cinematique « {nom} » : la camera, puis les joueurs proches (en spectateur, par les yeux de la camera).",
            f"scoreboard players set #id cine {ids[nom]}", "scoreboard players set #t cine 1", "scoreboard players set #nb cine 0",
            f"summon minecraft:armor_stand {sc._f(x + orig[0])} {sc._f(y + orig[1])} {sc._f(z + orig[2])} {{Invisible:1b,Marker:1b,NoGravity:1b,Silent:1b,Invulnerable:1b,Tags:[\"cine_cam\"],Rotation:[{sc._f(yaw)}f,{sc._f(pitch)}f]}}",
            f"execute positioned {s['centre']} as @a[distance=..{s['rayon']},gamemode=!spectator] at @s run function atelier:station_cine_joueur"]) + "\n"
    f["station_cine_joueur"] = "\n".join([
        "# [claude-build] Un joueur entre dans la cinematique : sa place et son mode de jeu sont gardes, puis il regarde par la camera. Execute en tant que ce joueur.",
        "tag @s add cine", "scoreboard players add #nb cine 1", "scoreboard players operation @s cine = #nb cine",
        "execute store result score @s cine_mode run data get entity @s playerGameType",
        "summon minecraft:marker ~ ~ ~ {Tags:[\"cine_retour\",\"cine_neuf\"]}", "tp @e[type=minecraft:marker,tag=cine_neuf] @s",
        "scoreboard players operation @e[type=minecraft:marker,tag=cine_neuf] cine = @s cine", "tag @e[type=minecraft:marker,tag=cine_neuf] remove cine_neuf",
        "gamemode spectator @s", f"spectate {CAMERA_UNE}"]) + "\n"
    f["station_cine_retour_joueur"] = "\n".join([
        "# [claude-build] Rend un joueur : il quitte la camera, retrouve sa place (et son regard) et son mode de jeu. Execute en tant que ce joueur.",
        "spectate", "scoreboard players operation #cherche cine = @s cine", "tag @s add cine_cible",
        "execute as @e[type=minecraft:marker,tag=cine_retour] if score @s cine = #cherche cine run tp @a[tag=cine_cible,limit=1] @s",
        "execute as @e[type=minecraft:marker,tag=cine_retour] if score @s cine = #cherche cine run kill @s",
        "execute if score @s cine_mode matches 0 run gamemode survival @s", "execute if score @s cine_mode matches 1 run gamemode creative @s",
        "execute if score @s cine_mode matches 2 run gamemode adventure @s", "tag @s remove cine_cible", "tag @s remove cine"]) + "\n"
    f["station_cine_fin"] = "\n".join([
        "# [claude-build] Fin de la cinematique (ou arret force) : tous les joueurs sont rendus, la camera disparait.",
        "execute as @a[tag=cine] run function atelier:station_cine_retour_joueur", f"kill {CAMERA}", "kill @e[type=minecraft:marker,tag=cine_retour]",
        "scoreboard players set #t cine 0", "scoreboard players set #id cine 0"]) + "\n"
    f["station_cine_cadre"] = "\n".join([
        "# [claude-build] Un tick de cinematique (appelee quand #t cine est au moins 1).",
        "scoreboard players add #t cine 1",
        *[f"execute if score #id cine matches {i} run function atelier:station_cine_{nom}" for nom, i in ids.items()],
        f"execute if score #t cine matches {TICKS_CHAMBRE}.. as @a[tag=cine] at @s unless entity {CAMERA_PROCHE} run function atelier:station_cine_retour_joueur",
        *[f"execute if score #id cine matches {ids[nom]} if score #t cine matches {scenes[nom]['duree'] + 2}.. run function atelier:station_cine_fin" for nom in sorted(scenes)]]) + "\n"
    return f
