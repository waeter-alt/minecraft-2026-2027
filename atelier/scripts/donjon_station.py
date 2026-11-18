# [claude-build]
"""
donjon_station.py - le Donjon de la Station (le donjon 2), construit d'apres son dossier de conception, un acte a la fois. Les actes I et II (station_acte1.py : le Hall, le Vestiaire, le Sas, la Grande Cuve et sa mise en eau)
et la suite (station_secteurs.py et station_logique.py : le Drain, les secteurs A, B, C, la Cheminee et le Poste, le Reservoir du boss et l'Exutoire).
Ce script cree le monde s'il n'existe pas (superflat profond : la plaine est a y 46), ecrit les structures, les fonctions de datapack (volants, embuscade, casier, Sas, mise en eau,
cinematiques, pose, remise a zero) et, avec --zip, le monde pret a copier.
  test-local/venv/Scripts/python.exe atelier/scripts/donjon_station.py [--monde "Donjon de la Station"] [--zip]
En jeu : la pose se fait a la premiere ouverture. Remise a zero : /function atelier:station_reset ; reposer : /scoreboard players set #station atelier 0 puis /reload.
"""
import argparse
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import monde_atelier  # noqa: E402
import station_acte1 as a1  # noqa: E402
import station_cine  # noqa: E402
import station_create  # noqa: E402
import station_logique  # noqa: E402
import station_secteurs  # noqa: E402
import suspendu_cine as sc  # noqa: E402
from suspendu_outils import ecrire_structure  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
SAVES = RACINE.parent / "mod/run/client/saves"
NOM_MONDE = "Donjon de la Station"
COUCHES = [("minecraft:bedrock", 1), ("minecraft:stone", 107), ("minecraft:dirt", 2), ("minecraft:grass_block", 1)]     # herbe a y 46
HERBE = -64 + 1 + 107 + 2
ORIGINE = (-120, HERBE - a1.PLAINE, -238)                    # monde = gabarit + ORIGINE : l'entree du pavillon est en (-1, 47, 0)
SX, SY, SZ = a1.SX, a1.SY, a1.SZ
Z_COUPURE = 131                                              # deux structures : la Cuve (z <= 130) et le Hall, le Sas, le Vestiaire, l'escalier et le pavillon (z >= 131)
DUREE_EAU = 300                                              # la mise en eau : 15 s, la camera monte avec la surface
DUREE_SAS = 110
CAPE = "donjon:cape_lestee"
CADENCE_DRAIN = 50                                            # le bruit d'eau du bassin d'essai (la bouche du Drain), toutes les 2,5 s


def monde(p):
    return tuple(ORIGINE[i] + p[i] for i in range(3))


def txt(p):
    return " ".join(str(round(v)) if isinstance(v, float) and v == int(v) else str(v) for v in monde(p))


def boite(x0, y0, z0, x1, y1, z1):
    """Le selecteur dx/dy/dz d'une boite, et son coin en coordonnees monde."""
    return txt((x0, y0, z0)), f"dx={x1 - x0},dy={y1 - y0},dz={z1 - z0}"


def tuiles(x0, x1, y0, y1, z0, z1, bloc):
    cmds = []
    for a in range(x0, x1 + 1, 31):
        for b in range(y0, y1 + 1, 31):
            for c in range(z0, z1 + 1, 31):
                cmds.append(f"fill {txt((a, b, c))} {txt((min(a + 30, x1), min(b + 30, y1), min(c + 30, z1)))} {bloc}")
    return cmds


def scene_sas(infos):
    """Dans le Hall, un travelling avant vers la vanne du Sas (on regarde vers le nord-nord-ouest, loin de +-180)."""
    cible = (120.5, a1.SOL + 3.0, a1.SAS[3] + 0.5)
    return sc.travelling(cible, (cible[0] + 7.0, cible[1] + 4.5, cible[2] + 24.0))


def scene_eau(infos):
    """Dans la Cuve, la camera monte avec la surface de l'eau (3 blocs au dessus d'elle), en regardant vers le nord : camera et cible montent ensemble, l'angle ne change pas."""
    cx, cz = a1.CUVE
    bas, haut = a1.FOND_CUVE + 1, a1.NIVEAUX["haut"]
    cam = [(1, (cx + 4.0, bas + 3.0, cz + 22.0)), (DUREE_EAU, (cx + 4.0, haut + 3.0, cz + 22.0))]
    vise = [(1, (cx, bas, cz - 14.0)), (DUREE_EAU, (cx, haut, cz - 14.0))]
    return cam, vise


def panneau(p, face, lignes):
    textes = ",".join("'" + '{"text":"' + l.replace("\\", "\\\\").replace("'", "\\'") + '"}' + "'" for l in (list(lignes) + ["", "", "", ""])[:4])
    return f"setblock {txt(p)} minecraft:oak_wall_sign[facing={face}]{{front_text:{{color:\"white\",has_glowing_text:1b,messages:[{textes}]}},is_waxed:1b}}"


def panneau_debout(p, rotation, lignes, eau):
    textes = ",".join("'" + '{"text":"' + l.replace("\\", "\\\\").replace("'", "\\'") + '"}' + "'" for l in (list(lignes) + ["", "", "", ""])[:4])
    return f"setblock {txt(p)} minecraft:oak_sign[rotation={rotation},waterlogged={'true' if eau else 'false'}]{{front_text:{{color:\"white\",has_glowing_text:1b,messages:[{textes}]}},is_waxed:1b}}"


def panneaux(infos):
    """(case du panneau, sens ou il regarde, lignes). Seulement les reperes de niveau de la Cuve : Luca ne veut aucun panneau qui dise quoi faire (18/11)."""
    x0, x1, z0, z1 = a1.HALL
    return [((120, a1.NIVEAUX["haut"] + 1, 124), "north", ("HAUT", "76", "", "")),
            ((120, a1.NIVEAUX["median"] + 1, 124), "north", ("MEDIAN", "58", "", "")),
            ((120, a1.NIVEAUX["bas"] + 1, 124), "north", ("BAS", "42", "", ""))]


def tick_couche(y):
    """Le tick de la mise en eau ou la couche y se remplit : la surface suit la camera (qui demarre et s'arrete en douceur), elle reste 2 blocs sous l'objectif."""
    images = sc.images(*scene_eau(None), DUREE_EAU)
    return next((t for t, im in enumerate(images, start=1) if im[1] >= y + 2.5), DUREE_EAU)


def fonctions(infos, decalages):
    f = {}
    infos2 = station_secteurs.construire()[1]
    f_suite, tick_suite, reset_suite, scenes_suite = station_logique.fonctions(infos2, infos, station_secteurs.tout(), txt)
    f.update(f_suite)
    lev = {k: txt(p) for k, p in infos["leviers"].items()}
    cuve_c = (round(a1.CUVE[0]), a1.SOL + 4, round(a1.CUVE[1]))
    f["station_charger"] = ("# [claude-build] A chaque chargement : les objectifs du donjon et des cinematiques, pas d'apparition de monstres.\n"
                            "scoreboard objectives add station dummy\nscoreboard objectives add cine dummy\nscoreboard objectives add cine_mode dummy\n"
                            "execute unless score #t cine matches 0.. run scoreboard players set #t cine 0\ngamerule doMobSpawning false\n"
                            "bossbar add atelier:station \"Station : pompes 0/3\"\nbossbar set atelier:station max 3\nbossbar set atelier:station color blue\nbossbar set atelier:station style notched_6\n"
                            + "".join(f"execute unless score {v} station matches -2147483648.. run scoreboard players set {v} station 0\n" for v in station_logique.VARIABLES))
    x0, x1, z0, z1 = infos["bassin50"]
    coin50, boite50 = boite(x0, infos["fond50"] + 1, z0, x1, a1.SOL, z1)
    rc = int(a1.R_COURSIVE)
    coin_cuve, boite_cuve = boite(round(a1.CUVE[0]) - rc, a1.FOND_CUVE + 1, round(a1.CUVE[1]) - rc, round(a1.CUVE[0]) + rc, a1.PLAFOND_CUVE - 1, Z_COUPURE - 1)
    ex0, ex1, ez0, ez1 = infos["bassin_essai"]
    tick = ["# [claude-build] Chaque tick : les cinematiques, les volants, l'embuscade, la mise en eau, le bruit du Drain.",
            "execute if score #t cine matches 1.. run function atelier:station_cine_cadre",
            "execute unless score #t cine matches 1.. as @a[tag=cine] run function atelier:station_cine_retour_joueur",
            *station_create.lignes_volant("v1", lev[1], "station_volant_1", "#v1"),
            *station_create.lignes_volant("v3", lev[3], "station_volant_3", "#v3"),
            *station_create.lignes_volant("v2", lev[2], "station_volant_2", "#v2", condition="if score #emb station matches 2", refus="station_volant_2_refus"),
            "scoreboard players add #obj station 1", "execute if score #obj station matches 40.. run function atelier:station_objectif",
            f"execute if score #emb station matches 0 positioned {coin50} if entity @a[gamemode=!spectator,{boite50}] run function atelier:station_embuscade",
            "execute if score #emb station matches 1 unless entity @e[tag=station_emb] run function atelier:station_embuscade_fin",
            f"execute if score #eau station matches 0 positioned {coin_cuve} if entity @a[gamemode=!spectator,{boite_cuve}] run function atelier:station_mise_en_eau",
            "scoreboard players add #tic station 1",
            f"execute if score #tic station matches {CADENCE_DRAIN}.. positioned {txt(((ex0 + ex1) // 2, a1.SOL, (ez0 + ez1) // 2))} run playsound minecraft:block.water.ambient ambient @a[distance=..48] ~ ~ ~ 1.6 0.6",
            f"execute if score #tic station matches {CADENCE_DRAIN}.. run scoreboard players set #tic station 0", *tick_suite]
    f["station_tick"] = "\n".join(tick) + "\n"
    # les volants : un temoin du Sas par volant, dans n'importe quel ordre ; au troisieme, la cinematique du Sas et du casier
    for k in (1, 2, 3):
        f[f"station_volant_{k}"] = "\n".join([
            f"# [claude-build] Le volant {k} est tourne : un temoin du Sas s'allume (l'ordre est libre).",
            f"scoreboard players set #v{k} station 1", "scoreboard players add #temoins station 1",
            f"setblock {txt(infos['temoins_sas'][k - 1])} minecraft:waxed_copper_bulb[lit=true,powered=false]",
            f"execute positioned {txt(infos['temoins_sas'][k - 1])} run playsound minecraft:block.copper_bulb.turn_on master @a[distance=..80] ~ ~ ~ 2 0.8",
            f"execute positioned {lev[k]} run playsound minecraft:block.iron_door.open master @a[distance=..20] ~ ~ ~ 1 0.5",
            f'execute as @a[x={monde(infos["leviers"][k])[0]},y={monde(infos["leviers"][k])[1]},z={monde(infos["leviers"][k])[2]},distance=..12] run title @s actionbar {{"text":"Un témoin du Sas s\'allume","color":"aqua"}}',
            "execute if score #temoins station matches 3 run function atelier:station_trois_volants"]) + "\n"
    f["station_volant_2_refus"] = "\n".join([
        "# [claude-build] Le volant du bassin de 50 m est grippe tant que l'embuscade n'est pas vaincue.",
        f'execute positioned {lev[2]} run title @a[distance=..8] actionbar {{"text":"Le volant est grippé : le bassin n\'est pas sûr","color":"gray"}}']) + "\n"
    f["station_embuscade"] = "\n".join([
        "# [claude-build] Un joueur descend dans le bassin de 50 m : embuscade (une seule fois) : trois zombies et un squelette surgissent du fond.",
        "scoreboard players set #emb station 1",
        *[f"summon minecraft:{'skeleton' if i == 3 else 'zombie'} {txt(p)} {{Tags:[\"station_emb\"],PersistenceRequired:1b}}" for i, p in enumerate(infos["embuscade"])],
        *[f"execute positioned {txt(p)} run particle minecraft:poof ~ ~0.5 ~ 0.4 0.4 0.4 0.05 20" for p in infos["embuscade"]],
        f"execute positioned {coin50} run playsound minecraft:entity.zombie.ambient hostile @a[distance=..60] ~ ~ ~ 2 0.6"]) + "\n"
    f["station_embuscade_fin"] = ("# [claude-build] L'embuscade est vaincue : le volant du bassin repond.\nscoreboard players set #emb station 2\n"
                                  f'execute positioned {lev[2]} run title @a[distance=..70] actionbar {{"text":"Le bassin est libre : le volant répond","color":"aqua"}}\n')
    f["station_trois_volants"] = "\n".join([
        "# [claude-build] Les trois volants sont tournes : la vanne du Sas se leve et un casier s'ouvre au Vestiaire, en cinematique (ou tout de suite si une autre est en cours).",
        "scoreboard players operation #libre cine = #t cine",
        "execute if score #libre cine matches 0 run function atelier:station_cine_debut_sas",
        "execute unless score #libre cine matches 0 run function atelier:station_sas_ouvrir",
        "execute unless score #libre cine matches 0 run function atelier:station_casier"]) + "\n"
    vanne = infos["vanne_sas"]
    va, vb = txt(min(vanne)), txt(max(vanne))
    f["station_sas_ouvrir"] = "\n".join([
        "# [claude-build] La vanne du Sas se leve : le chemin de la Grande Cuve est ouvert.",
        f"fill {va} {vb} minecraft:air", f"playsound minecraft:block.piston.contract master @a {txt(vanne[len(vanne) // 2])} 3 0.5",
        f"playsound minecraft:block.iron_door.open master @a {txt(vanne[len(vanne) // 2])} 3 0.6",
        'tellraw @a {"text":"La vanne du Sas se lève.","color":"gray"}']) + "\n"
    f["station_casier"] = "\n".join([
        "# [claude-build] Le casier orange du Vestiaire recoit la Cape lestee : on le vide puis on le repose plein, il pose lui-meme sa moitie haute (on repart d'une case vide : setblock viderait un coffre deja la).",
        f"setblock {txt((infos['casier'][0], infos['casier'][1] + 1, infos['casier'][2]))} minecraft:air", f"setblock {txt(infos['casier'])} minecraft:air", f"setblock {txt(infos['casier'])} donjon:casier[facing=south,couleur=orange]{{Items:[{{Slot:13b,id:\"{CAPE}\",count:1}}]}}",
        f"execute positioned {txt(infos['casier'])} run particle minecraft:happy_villager ~ ~0.5 ~ 0.4 0.4 0.4 0.1 30",
        f"execute positioned {txt(infos['casier'])} run playsound minecraft:block.chest.open master @a[distance=..60] ~ ~ ~ 2 1",
        'tellraw @a {"text":"Au Vestiaire, un casier s\'est ouvert.","color":"gold"}']) + "\n"
    # la mise en eau : couche par couche, de 31 a 76 (HAUT) ; chaque couche, une fonction
    rangees = infos["cuve"]["rangees"]
    bas, haut = a1.FOND_CUVE + 1, a1.NIVEAUX["haut"]
    for y in range(bas, haut + 1):
        f[f"station_eau_{y}"] = "\n".join([f"# [claude-build] La mise en eau : la couche {y} de la Grande Cuve.",
                                          *[f"fill {txt((xa, y, z))} {txt((xb, y, z))} minecraft:water replace minecraft:air" for (xa, xb, z) in rangees]]) + "\n"
    f["station_eau_tout"] = "\n".join(["# [claude-build] La Cuve se remplit d'un coup (une autre cinematique est en cours).", *[f"function atelier:station_eau_{y}" for y in range(bas, haut + 1)], f"scoreboard players set #niv station {haut}", "function atelier:station_bouches_ouvrir",
                                       'tellraw @a {"text":"La Grande Cuve est pleine.","color":"aqua"}']) + "\n"
    f["station_vider"] = "\n".join(["# [claude-build] Vide la Grande Cuve (remise a zero) : l'eau redevient de l'air, couche par couche.",
                                    *[f"fill {txt((xa, y, z))} {txt((xb, y, z))} minecraft:air replace minecraft:water" for y in range(bas, a1.SOL) for (xa, xb, z) in rangees]]) + "\n"
    f["station_mise_en_eau"] = "\n".join([
        "# [claude-build] Un joueur entre dans la Grande Cuve (par le Sas) : la station se met en route et la Cuve se remplit, en cinematique (ou d'un coup si une autre est en cours). Une seule fois.",
        "scoreboard players set #eau station 1", "scoreboard players operation #libre cine = #t cine",
        "execute if score #libre cine matches 0 run function atelier:station_cine_debut_eau",
        "execute unless score #libre cine matches 0 run function atelier:station_eau_tout"]) + "\n"
    # les deux cinematiques
    ev_sas = {2: ['title @a[tag=cine] actionbar {"text":"…","color":"gray"}'],
              12: ["execute as @a[tag=cine] at @s run playsound minecraft:block.copper_grate.step master @s ~ ~ ~ 1 0.5", "execute as @a[tag=cine] at @s run playsound minecraft:block.chain.place master @s ~ ~ ~ 1 0.5"],
              25: ["function atelier:station_sas_ouvrir", "function atelier:station_casier", "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1.2"],
              34: [station_cine.son("chime", 0.75)], 38: [station_cine.son("chime", 0.94)], 42: [station_cine.son("chime", 1.12)], 47: [station_cine.son("chime", 1.5)],
              62: ['title @a[tag=cine] actionbar {"text":"Au Vestiaire, un casier s\'est ouvert","color":"gold"}']}
    ev_eau = {2: ['title @a[tag=cine] actionbar {"text":"La station se met en route…","color":"gray"}', "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 0.5",
                  "execute as @a[tag=cine] at @s run playsound minecraft:block.piston.extend master @s ~ ~ ~ 1 0.4"]}
    for y in range(bas, haut + 1):
        ev_eau.setdefault(tick_couche(y), []).append(f"function atelier:station_eau_{y}")
    for t in range(10, DUREE_EAU - 10, 30):
        ev_eau.setdefault(t, []).append("execute as @a[tag=cine] at @s run playsound minecraft:block.water.ambient master @s ~ ~ ~ 1 0.7")
        ev_eau.setdefault(t + 5, []).append(f"execute positioned {txt(cuve_c)} run particle minecraft:bubble_column_up ~ ~-40 ~ 12 2 12 0.1 200 force")
    ev_eau.setdefault(DUREE_EAU - 14, []).extend([station_cine.son("chime", 1.0), 'title @a[tag=cine] actionbar {"text":"La Grande Cuve est pleine. Plonge !","color":"aqua"}'])
    ev_eau.setdefault(DUREE_EAU - 10, []).append(station_cine.son("chime", 1.26))
    ev_eau.setdefault(DUREE_EAU - 6, []).append(station_cine.son("chime", 1.5))
    ev_eau.setdefault(DUREE_EAU - 4, []).extend([f"scoreboard players set #niv station {haut}", "function atelier:station_bouches_ouvrir"])
    scenes = {"sas": {"cles": scene_sas(infos), "duree": DUREE_SAS, "centre": txt((120, a1.SOL + 1, 190)), "rayon": 90, "evenements": ev_sas},
              "eau": {"cles": scene_eau(infos), "duree": DUREE_EAU, "centre": txt(cuve_c), "rayon": 50, "evenements": ev_eau}}
    scenes.update(scenes_suite)
    f.update(station_cine.fonctions(scenes, ORIGINE))
    # l'objectif du moment (toutes les 2 s, en barre d'action) et la barre de progression des pompes, pour qui est dans la station
    zone = f"x={ORIGINE[0]},y={ORIGINE[1]},z={ORIGINE[2]},dx={SX - 1},dy={SY + 4},dz={SZ - 1}"
    obj = ["# [claude-build] L'objectif du moment, en barre d'action, et la barre des pompes, pour qui est dans la station (toutes les 2 s).",
           "scoreboard players set #obj station 0",
           f"bossbar set atelier:station players @a[{zone}]",
           f'execute if score #temoins station matches ..2 run title @a[{zone}] actionbar [{{"text":"Hall : tourne les 3 volants (","color":"aqua"}},{{"score":{{"name":"#temoins","objective":"station"}},"color":"white"}},{{"text":"/3) — clic droit maintenu","color":"aqua"}}]',
           f'execute if score #temoins station matches 3 if score #eau station matches 0 run title @a[{zone}] actionbar {{"text":"Prends la Cape lestée au Vestiaire (à l\'ouest), puis passe le Sas (au nord)","color":"aqua"}}',
           f'execute if score #eau station matches 1 unless score #niv station matches 1.. run title @a[{zone}] actionbar {{"text":"La Grande Cuve se remplit…","color":"aqua"}}',
           f'execute if score #niv station matches 1.. if score #pompes station matches ..2 run title @a[{zone}] actionbar [{{"text":"Plonge : bouches A (ouest), B (est), C (nord). Pompes ","color":"aqua"}},{{"score":{{"name":"#pompes","objective":"station"}},"color":"white"}},{{"text":"/3","color":"aqua"}}]',
           f'execute if score #pompes station matches 3 if score #lg station matches 0 run title @a[{zone}] actionbar {{"text":"La vanne D est ouverte : la Cheminée, au fond nord-est de la Cuve","color":"gold"}}',
           f'execute if score #lg station matches 1 if score #boss station matches 0 run title @a[{zone}] actionbar {{"text":"Saute du plongeoir dans le tourbillon","color":"gold"}}',
           f'execute if score #boss station matches 1 run title @a[{zone}] actionbar {{"text":"Abats le Léviathan","color":"red"}}',
           f'execute if score #boss station matches 2 run title @a[{zone}] actionbar {{"text":"L\'Exutoire est ouvert, au sud du Réservoir","color":"gold"}}']
    f["station_objectif"] = "\n".join(obj) + "\n"
    # les panneaux : ou l'on est, ou l'on va (poses a la pose et a chaque remise a zero)
    f["station_panneaux"] = "\n".join(["# [claude-build] Les panneaux de la station (une seule rangee de texte par ligne).",
                                       *[panneau(p, face, lignes) for (p, face, lignes) in panneaux(infos)]]) + "\n"
    # la remise a zero
    f["station_reset"] = "\n".join([
        "# [claude-build] Remise a zero : cinematique arretee, monstres retires, volants baisses, temoins eteints, Sas referme, casier referme, Cuve videe, scores a zero.",
        "function atelier:station_cine_fin", "kill @e[tag=station_emb]",
        "function atelier:station_panneaux", "bossbar set atelier:station value 0", "bossbar set atelier:station name \"Station : pompes 0/3\"",
        *[f"setblock {txt(p)} minecraft:waxed_copper_bulb[lit=false,powered=false]" for p in infos["temoins_sas"] + infos["temoins_pompes"]],
        f"fill {va} {vb} minecraft:iron_bars", f"setblock {txt((infos['casier'][0], infos['casier'][1] + 1, infos['casier'][2]))} minecraft:air", f"setblock {txt(infos['casier'])} minecraft:air", f"setblock {txt(infos['casier'])} donjon:casier[facing=south,couleur=orange]",
        "function atelier:station_vider", *reset_suite,
        *[f"scoreboard players set {v} station 0" for v in ("#v1", "#v2", "#v3", "#cv1", "#cv2", "#cv3", "#temoins", "#emb", "#eau", "#tic", "#obj")],
        'tellraw @a {"text":"Station remise à zéro.","color":"gray"}']) + "\n"
    # la pose : le gabarit tient en 225 chunks (moins des 256 d'un forceload)
    a, b = monde((0, 0, 0)), monde((SX - 1, 0, SZ - 1))
    f["station_pose"] = "\n".join([
        "# [claude-build] Pose la station (sans pause).", f"forceload add {a[0]} {a[2]} {b[0]} {b[2]}",
        'tellraw @a {"text":"Station : 30 secondes de chargement, puis la pose par morceaux (le jeu peut ralentir). Ne mets pas en pause.","color":"gold"}',
        "schedule function atelier:station_pose_2 600t"]) + "\n"
    haut_pierre = a1.PLAINE - 3
    terrain = [*tuiles(0, SX - 1, 0, haut_pierre, 0, SZ - 1, "minecraft:stone"), *tuiles(0, SX - 1, haut_pierre + 1, a1.PLAINE - 1, 0, SZ - 1, "minecraft:dirt"),
               *tuiles(0, SX - 1, a1.PLAINE, a1.PLAINE, 0, SZ - 1, "minecraft:grass_block"), *tuiles(0, SX - 1, a1.PLAINE + 1, SY + 9, 0, SZ - 1, "minecraft:air")]     # 10 de plus au-dessus du gabarit : efface les debris d'une ancienne pose
    places = [f"place template atelier:{nom} {txt(decalages[nom])}" for nom in ("station_cuve", "station_hall") + station_secteurs.ORDRE + ("station_retouches",) if nom in decalages]
    etapes = [terrain[i:i + 40] for i in range(0, len(terrain), 40)] + [[ligne] for ligne in places]
    for i, cmds in enumerate(etapes):
        suite = [f"schedule function atelier:station_pose_2_{i + 1} 2t"] if i + 1 < len(etapes) else ["kill @e[type=item]", "function atelier:station_reset", "schedule function atelier:station_pose_3 100t"]
        f["station_pose_2" if i == 0 else f"station_pose_2_{i}"] = "\n".join([f"# [claude-build] Pose de la station, etape {i + 1} sur {len(etapes)}.", *cmds, *suite]) + "\n"
    f["station_pose_3"] = "\n".join(["# [claude-build] Decharge le gabarit et annonce.", f"forceload remove {a[0]} {a[2]} {b[0]} {b[2]}",
                                     monde_atelier.message_entree("La Station est prête.", txt(infos["entree"]))]) + "\n"
    return f


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--monde", default=NOM_MONDE)
    ap.add_argument("--zip", action="store_true")
    args = ap.parse_args()
    options = {"couches": COUCHES, "difficulte": 2, "apparition": (-1, HERBE + 1, 3), "mode_jeu": 0}
    save = monde_atelier.trouver_ou_creer_monde(args.monde, SAVES, **options)
    print(f"[0] monde « {args.monde} » -> dossier {save.name}", flush=True)
    exports = RACINE / "exports"
    dp = monde_atelier.datapack(save)
    blocs, infos = a1.construire()
    decalages = {"station_cuve": ecrire_structure({p: s for p, s in blocs.items() if p[2] < Z_COUPURE}, exports / "station_cuve.nbt"),
                 "station_hall": ecrire_structure({p: s for p, s in blocs.items() if p[2] >= Z_COUPURE}, exports / "station_hall.nbt")}
    parts, _ = station_secteurs.construire()
    for nom in station_secteurs.ORDRE:
        decalages[nom] = ecrire_structure(parts[nom], exports / f"{nom}.nbt")
    import station_retouches
    retouches = station_retouches.charger()                                 # les retouches a la main de Luca : posees en dernier, par-dessus tout
    if retouches:
        decalages["station_retouches"] = ecrire_structure(retouches, exports / "station_retouches.nbt")
        print(f"{len(retouches)} retouche(s) a la main reposee(s)")
    elif (dp / "data/atelier/structure/station_retouches.nbt").exists():
        (dp / "data/atelier/structure/station_retouches.nbt").unlink()
    (dp / "data/atelier/predicate").mkdir(parents=True, exist_ok=True)
    (dp / "data/atelier/predicate/nage.json").write_text(json.dumps({"condition": "minecraft:entity_properties", "entity": "this",
                                                                    "predicate": {"flags": {"is_swimming": True}}}, indent=2), encoding="utf-8")
    for nom in decalages:
        (dp / "data/atelier/structure" / f"{nom}.nbt").write_bytes((exports / f"{nom}.nbt").read_bytes())
    textes = fonctions(infos, decalages)
    fn = dp / "data/atelier/function"
    for ancien in fn.glob("station_*.mcfunction"):                  # le menage d'abord : Windows ignore la casse, un nettoyage apres coup effacerait des fichiers tout juste ecrits
        ancien.unlink()
    for nom, texte in textes.items():
        (fn / f"{nom}.mcfunction").write_text(texte, encoding="utf-8")
    for etiquette, valeurs in (("tick", ["atelier:station_tick"]), ("load", ["atelier:station_charger"])):
        chemin = dp / f"data/minecraft/tags/function/{etiquette}.json"
        chemin.parent.mkdir(parents=True, exist_ok=True)
        existantes = json.loads(chemin.read_text(encoding="utf-8"))["values"] if chemin.exists() else []
        chemin.write_text(json.dumps({"values": existantes + [v for v in valeurs if v not in existantes]}, indent=2), encoding="utf-8")
    monde_atelier.installer_pose_auto(save, "station_pose", "station")
    print(f"Ecrit : {len(blocs) + sum(len(v) for v in parts.values())} blocs, {len(textes)} fonctions ; entree en {txt(infos['entree'])}")
    if args.zip:
        monde_atelier.zipper_monde(save, args.monde, RACINE / "mondes", **options)


if __name__ == "__main__":
    main()
