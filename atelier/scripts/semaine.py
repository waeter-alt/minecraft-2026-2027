# [claude-build]
"""
semaine.py - Monde final "Arene Semaine" : un hub O104 a 7 portes et les 7 arenes de la serie, une par jour.
Les arenes ne sont pas tournees (leurs commandes sont relatives) : chaque porte du hub teleporte devant l'entree de l'arene,
et une arche de retour ramene au hub. Ecrit le hub, les retours, copie les arenes et ecrit les fonctions de pose.
  .venv\\Scripts\\python.exe sources\\semaine.py
En jeu : /reload puis /function atelier:semaine_tout   (une seule fois, sans mettre le jeu en pause)
Controle : sources\\verif_semaine.py
"""
import json
import math
import shutil
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine"
D = SAVE / "datapacks/atelier"
ST, FN = D / "data/atelier/structure", D / "data/atelier/function"
SX = SZ = 104
SOL = 8
SY = SOL + 26
C = 52
CEN = 52.0
R = 52.0
ORIGINE = (-52, 78, -52)
SPAWN = (0, 87, 20)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR, SB, AND, SST, CHB = S("air"), S("stone_bricks"), S("andesite"), S("smooth_stone"), S("chiseled_stone_bricks")
QZ, QS, QC, QP = S("quartz_block"), S("smooth_quartz"), S("chiseled_quartz_block"), S("quartz_pillar", axis="y")
VERRE, LANT, LANTERNE = S("light_blue_stained_glass"), S("sea_lantern"), S("lantern", hanging=False, waterlogged=False)

# jour, nom, fichier, centre, gabarit Y, sol, mur, accent, biome, heure, couleur, danger
ARENES = [
    dict(n=1, nom="Le Croissant", f="croissant", c=(0, -256), y=80, sol=S("snow_block"), mur=S("packed_ice"), acc=S("blue_ice"), biome="snowy_plains", heure=6000, rvb=(.6, .85, 1), danger="le labyrinthe"),
    dict(n=2, nom="Le Feu", f="feu2", c=(256, -256), y=56, sol=S("blackstone"), mur=S("nether_bricks"), acc=S("magma_block"), biome="desert", heure=6000, rvb=(1, .35, .05), danger="la lave"),
    dict(n=3, nom="La Jungle", f="jungle", c=(256, 0), y=226, sol=S("moss_block"), mur=S("jungle_log", axis="y"), acc=S("jungle_planks"), biome="jungle", heure=6000, rvb=(.25, .8, .2), danger="le vide"),
    dict(n=4, nom="Le Piège", f="piege", c=(256, 256), y=68, sol=S("polished_andesite"), mur=SB, acc=S("create:brass_casing"), biome=None, heure=6000, rvb=(.8, .6, .25), danger="les pièges"),
    dict(n=5, nom="Les Abysses", f="abysses", c=(0, 256), y=68, sol=S("prismarine_bricks"), mur=S("dark_prismarine"), acc=LANT, biome="deep_ocean", heure=6000, rvb=(.1, .65, .75), danger="manquer d'air"),
    dict(n=6, nom="Les Ténèbres", f="tenebres", c=(-256, 256), y=68, sol=S("deepslate_tiles"), mur=S("deepslate_bricks"), acc=S("sculk"), biome="deep_dark", heure=18000, rvb=(.1, .35, .5), danger="le bruit"),
    dict(n=7, nom="Les Portes", f="portes", c=(-256, 0), y=68, sol=S("end_stone_bricks"), mur=S("purpur_block"), acc=S("purpur_pillar", axis="y"), biome="plains", heure=6000, rvb=(.7, .35, .85), danger="se perdre"),
]
blocs, NBT = {}, {}


def put(x, y, z, s):
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def dist(x, z):
    return math.hypot(x + .5 - CEN, z + .5 - CEN)


def ang(x, z):
    return math.degrees(math.atan2(z + .5 - CEN, x + .5 - CEN)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


def commande(cible, nbt, p, cmd, premier):
    cible[p] = S("repeating_command_block" if premier else "chain_command_block", conditional=False, facing="east")
    nbt[p] = Compound({"id": String("minecraft:command_block"), "Command": String(cmd), "auto": Byte(1), "TrackOutput": Byte(0),
                       "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})


def ecrire(cible, nbt, taille, nom_):
    palette, index, liste = [], {}, []
    for p in sorted(cible, key=lambda q: (q[1], q[2], q[0])):
        s = cible[p]
        if s not in index:
            index[s] = len(palette)
            c = Compound({"Name": String(s[0])})
            if s[1]:
                c["Properties"] = Compound({k: String(v) for k, v in s[1]})
            palette.append(c)
        e = Compound({"pos": List[Int]([Int(v) for v in p]), "state": Int(index[s])})
        if p in nbt:
            e["nbt"] = nbt[p]
        liste.append(e)
    f = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
    f.save(RACINE / "exports" / f"{nom_}.nbt")
    return len(liste)


def rvb(a):
    r, g, b = a["rvb"]
    return f"minecraft:dust{{color:[{r}f,{g}f,{b}f],scale:1.6f}}"


# ====================================================================== 1. hub : sol, enceinte, colonnes, obelisque
for i, a in enumerate(ARENES):
    a["angle"] = (270 + i * 360 / 7) % 360
for x in range(SX):
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        if d >= R:
            continue
        put(x, SOL - 1, z, SB)
        put(x, SOL, z, CHB if abs(d - 42) < .8 else SST)
        if d >= 50:
            fen = int(a / 5) % 3 == 1
            for y in range(SOL + 1, SOL + 13):
                put(x, y, z, VERRE if fen and SOL + 3 <= y <= SOL + 6 else QC if y == SOL + 12 else SB if y % 3 else AND)
for x in range(SX):                                    # dais et obelisque
    for z in range(SZ):
        d = dist(x, z)
        if d < 14:
            put(x, SOL + 1, z, QS if d < 12.5 else QZ)
        if d < 10:
            put(x, SOL + 2, z, QZ)
for x in range(C - 3, C + 3):
    for z in range(C - 3, C + 3):
        if dist(x, z) < 3.2:
            for y in range(SOL + 3, SOL + 11):
                put(x, y, z, QZ if y % 4 else QC)
for x in range(C - 2, C + 2):
    for z in range(C - 2, C + 2):
        if dist(x, z) < 2.2:
            for y in range(SOL + 11, SOL + 19):
                put(x, y, z, QP)
for x, z in ((C - 1, C - 1), (C, C - 1), (C - 1, C), (C, C)):
    for y in range(SOL + 19, SOL + 23):
        put(x, y, z, QS)
    put(x, SOL + 23, z, LANT)
for k in range(24):                                    # colonnade, interrompue devant les portes
    a = 8 + 15 * k
    if any(adiff(a, g["angle"]) < 13 for g in ARENES):
        continue
    x, z = math.floor(CEN + 47 * math.cos(math.radians(a))), math.floor(CEN + 47 * math.sin(math.radians(a)))
    for u, v in ((0, 0), (1, 0), (0, 1), (1, 1)):
        for y in range(SOL + 1, SOL + 9):
            put(x + u, y, z + v, QC if y in (SOL + 1, SOL + 8) else QP)
    put(x, SOL + 9, z, LANTERNE)


# ====================================================================== 2. les 7 portes, dans le style de leur arene
def panneau(x, y, z, rot, a):
    put(x, y, z, S("oak_sign", rotation=rot, waterlogged=False))
    lignes = [f"Jour {a['n']}", a["nom"], "", ""]
    msg = List[String]([String(json.dumps(l, ensure_ascii=False)) for l in lignes])
    face = Compound({"has_glowing_text": Byte(1), "color": String("white"), "messages": msg})
    NBT[(x, y, z)] = Compound({"id": String("minecraft:sign"), "front_text": face, "back_text": face, "is_waxed": Byte(1)})


for g in ARENES:
    ca, sa = math.cos(math.radians(g["angle"])), math.sin(math.radians(g["angle"]))
    for x in range(SX):
        for z in range(SZ):
            dx, dz = x + .5 - CEN, z + .5 - CEN
            rr, t = dx * ca + dz * sa, -dx * sa + dz * ca
            if dist(x, z) >= R or rr < 0:
                continue
            if 14.5 <= rr < 44 and abs(t) < 3:          # allee au sol, de la couleur de l'arene
                put(x, SOL, z, g["sol"])
            if rr >= 44 and abs(t) < 8:
                ouvert = abs(t) < 3 and rr < 50
                put(x, SOL, z, g["sol"])
                for y in range(SOL + 1, SOL + 15):
                    if ouvert and y < SOL + 7:
                        put(x, y, z, AIR)
                    else:
                        put(x, y, z, g["acc"] if y == SOL + 14 else g["mur"])
    # le monument central porte aussi la couleur de chaque porte
    x, z = math.floor(CEN + 11.5 * ca), math.floor(CEN + 11.5 * sa)
    put(x, SOL + 2, z, g["acc"])
    g["porte"] = (ORIGINE[0] + round(CEN + 47.5 * ca), 87, ORIGINE[2] + round(CEN + 47.5 * sa))
    rot = round((math.degrees(math.atan2(ca, -sa)) % 360) / 22.5) % 16     # panneaux tournes vers le centre
    for t in (-4.5, 4.5):
        px, pz = math.floor(CEN + 42.5 * ca - t * sa), math.floor(CEN + 42.5 * sa + t * ca)
        put(px, SOL + 1, pz, g["mur"])
        panneau(px, SOL + 2, pz, rot, g)
print("[1] hub :", len(blocs), flush=True)

# ====================================================================== 3. arches de retour, devant chaque entree
for a in ARENES:
    cx, cz = a["c"]
    b, nb = {}, {}
    ox, oz = cx - 52, cz - 52
    if a["f"] == "croissant":
        fx, fz, axe, front, yaw = cx + 62, cz - 1, "z", (-1, 0), 90
        a["arrivee"] = (cx + 57.5, 87, cz + .5)
        plate = [(x, 86, z) for x in range(cx + 53, cx + 65) for z in range(cz - 6, cz + 6)]
        yb = 86
    elif a["f"] == "jungle":
        fx, fz, axe, front, yaw = ox + 3, oz + 51, "z", (1, 0), -90
        a["arrivee"] = (ox + 7.5, 257, oz + 52.5)
        plate, yb = [], 256
    else:
        fx, fz, axe, front, yaw = cx - 1, cz - 62, "x", (0, 1), 0
        a["arrivee"] = (cx + .5, 87, cz - 56.5)
        plate = [(x, 86, z) for x in range(cx - 6, cx + 6) for z in range(cz - 64, cz - 52)]
        yb = 86
    a["yaw"] = yaw
    for p in plate:
        b[p] = a["sol"]
    u = (1, 0) if axe == "x" else (0, 1)
    cases = [(fx, fz), (fx + u[0], fz + u[1])]
    bords = [(fx - u[0], fz - u[1]), (fx + 2 * u[0], fz + 2 * u[1])]
    for x, z in cases + bords:
        b[(x, yb, z)] = a["mur"]
        b[(x, yb + 4, z)] = a["acc"]
    for x, z in bords:
        for y in range(yb + 1, yb + 4):
            b[(x, y, z)] = a["mur"]
    for x, z in cases:
        for y in range(yb + 1, yb + 4):
            b[(x, y, z)] = AIR
    centre = (fx + 1.0, yb + 1, fz + .5) if axe == "x" else (fx + .5, yb + 1, fz + 1.0)
    a["retour"] = centre
    sel = "@a[distance=..1.7,gamemode=!spectator]"
    pos = f"{centre[0]} {centre[1]} {centre[2]}"
    cmds = [f"particle {rvb(a)} {centre[0]} {centre[1] + 1} {centre[2]} .5 .8 .5 0 4",
            f"execute positioned {pos} if entity {sel} run time set 6000",
            f"execute positioned {pos} if entity {sel} run playsound minecraft:entity.enderman.teleport master @a[distance=..20] ~ ~ ~ 1 1",
            f"execute positioned {pos} as {sel} run tp @s {SPAWN[0]} {SPAWN[1]} {SPAWN[2]} 180 0"]
    yc = yb - 4 if a["f"] != "jungle" else yb - 2
    x0c = fx - 1 if axe == "x" else fx + 1
    zc = fz if axe == "x" else fz - 1
    for i, c in enumerate(cmds):
        commande(b, nb, (x0c + i, yc, zc), c, i == 0)
    xs, ys, zs = zip(*b)
    o = (min(xs), min(ys), min(zs))
    a["retour_o"] = o
    t = (max(xs) - o[0] + 1, max(ys) - o[1] + 1, max(zs) - o[2] + 1)
    b2 = {(x - o[0], y - o[1], z - o[2]): s for (x, y, z), s in b.items()}
    n2 = {(x - o[0], y - o[1], z - o[2]): s for (x, y, z), s in nb.items()}
    ecrire(b2, n2, t, f"retour_{a['f']}")

# ====================================================================== 4. chaine du hub : les 7 portes
CY, CZ, CX0 = SOL - 5, C, C - 20
CMD = ["gamerule commandBlockOutput false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doFireTick false", "gamerule doWardenSpawning false", "gamerule spawnRadius 0", "difficulty normal"]
for a in ARENES:
    gx, gy, gz = a["porte"]
    sel = "@a[distance=..3,gamemode=!spectator]"
    ax, ay, az = a["arrivee"]
    CMD += [f"particle {rvb(a)} {gx} {gy + 1} {gz} 1.2 1.3 1.2 0 8",
            f"execute positioned {gx} {gy} {gz} if entity {sel} run time set {a['heure']}",
            f"execute positioned {gx} {gy} {gz} if entity {sel} run playsound minecraft:entity.enderman.teleport master @a[distance=..20] ~ ~ ~ 1 1",
            f"execute positioned {gx} {gy} {gz} as {sel} run tp @s {ax} {ay} {az} {a['yaw']} 0"]
for i, c in enumerate(CMD):
    commande(blocs, NBT, (CX0 + i, CY, CZ), c, i == 0)
print("[2] commandes :", len(CMD), flush=True)
n = ecrire(blocs, NBT, (SX, SY, SZ), "hub7")

# ====================================================================== 5. fonctions de pose
if not (SAVE / "level.dat").exists():
    raise SystemExit("monde Arene Semaine introuvable")
ST.mkdir(parents=True, exist_ok=True)
FN.mkdir(parents=True, exist_ok=True)
if not (D / "pack.mcmeta").exists():
    (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
fichiers = ["hub7"] + [a["f"] for a in ARENES] + [f"retour_{a['f']}" for a in ARENES]
for f in fichiers:
    shutil.copyfile(RACINE / "exports" / f"{f}.nbt", ST / f"{f}.nbt")
# 2026-11-08, demande de l'utilisateur : plus de parois invisibles autour des arenes. Les .nbt des arenes les gardent
# (mondes de chaque arene) ; dans le monde final, une structure d'air les retire juste apres la pose de chaque arene.
SANS = {}
for a in ARENES:
    g = nbtlib.load(RACINE / "exports" / f"{a['f']}.nbt")
    pal_ = [str(p["Name"]) for p in g["palette"]]
    bar = [tuple(int(v) for v in b["pos"]) for b in g["blocks"] if pal_[int(b["state"])] == "minecraft:barrier"]
    if not bar:
        continue
    m = tuple(min(p[k] for p in bar) for k in range(3))
    t_ = tuple(max(p[k] for p in bar) - m[k] + 1 for k in range(3))
    nb_bar = ecrire({(x - m[0], y - m[1], z - m[2]): AIR for x, y, z in bar}, {}, t_, f"sans_barrieres_{a['f']}")
    shutil.copyfile(RACINE / "exports" / f"sans_barrieres_{a['f']}.nbt", ST / f"sans_barrieres_{a['f']}.nbt")
    cx, cz = a["c"]
    SANS[a["f"]] = f"place template atelier:sans_barrieres_{a['f']} {cx - 52 + m[0]} {a['y'] + m[1]} {cz - 52 + m[2]}"
    print(f"[5] sans_barrieres_{a['f']} : {nb_bar} barrieres retirees, taille {t_}", flush=True)


def biomes(a):
    """fillbiome refuse plus de 32768 blocs par commande : on decoupe en cubes de 32."""
    cx, cz = a["c"]
    tranches = (224, 256, 288) if a["f"] == "jungle" else (32, 64, 96, 128) if a["f"] == "feu2" else (64, 96, 128)   # feu2 : caverne jusqu'a Y56 (2026-11-08)
    return [f"fillbiome {x} {y} {z} {x + 31} {y + 31} {z + 31} minecraft:{a['biome']}" for y in tranches for x in range(cx - 64, cx + 64, 32) for z in range(cz - 64, cz + 64, 32)]


t0 = ["# [claude-build] Charge les zones du monde final, puis pose le hub et les arenes une par une.", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "weather clear", "time set 6000", "forceload add -56 -56 55 55"]
for a in ARENES:
    cx, cz = a["c"]
    t0.append(f"forceload add {cx - 72} {cz - 72} {cx + 71} {cz + 71}")
t0 += ['tellraw @a {"text":"Chargement : 30 secondes, puis le jeu va se figer plusieurs fois. Ne le mets pas en pause et ne le ferme pas.","color":"gold"}', "schedule function atelier:semaine_0 600t"]
(FN / "semaine_tout.mcfunction").write_text("\n".join(t0) + "\n", encoding="utf-8")
(FN / "semaine_0.mcfunction").write_text("\n".join(["# [claude-build] Pose le hub.", f"place template atelier:hub7 {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}", 'tellraw @a {"text":"Hub pose (1/8)","color":"gray"}', "schedule function atelier:semaine_1 200t"]) + "\n", encoding="utf-8")
for i, a in enumerate(ARENES, 1):
    cx, cz = a["c"]
    l = [f"# [claude-build] Pose {a['f']}."]
    if a["biome"]:
        l += biomes(a)
    l.append(f"place template atelier:{a['f']} {cx - 52} {a['y']} {cz - 52}")
    if a["f"] in SANS:
        l.append(SANS[a["f"]])
    o = a["retour_o"]
    l.append(f"place template atelier:retour_{a['f']} {o[0]} {o[1]} {o[2]}")
    l.append(f'tellraw @a {{"text":"{a["nom"]} posee ({i + 1}/8)","color":"gray"}}')
    l.append(f"schedule function atelier:semaine_{i + 1 if i < 7 else 'fin'} 200t")
    (FN / f"semaine_{i}.mcfunction").write_text("\n".join(l) + "\n", encoding="utf-8")
fin = ["# [claude-build] Termine la pose.", f"setworldspawn {SPAWN[0]} {SPAWN[1]} {SPAWN[2]}", "kill @e[type=item]", f"tp @a {SPAWN[0]} {SPAWN[1]} {SPAWN[2]} 180 0", "forceload remove all", "schedule function atelier:semaine_jour 200t"]
(FN / "semaine_fin.mcfunction").write_text("\n".join(fin) + "\n", encoding="utf-8")
# les chaines des arenes tournent encore un instant apres forceload remove : le jour revient 10 s plus tard
(FN / "semaine_jour.mcfunction").write_text("\n".join(["# [claude-build] Remet le jour une fois les arenes dechargees.", "time set 6000", 'tellraw @a {"text":"Le monde de la semaine est pret !","color":"green"}']) + "\n", encoding="utf-8")
# rattrapage pour un monde deja pose : charge les arenes, applique les biomes, decharge
b0 = ["# [claude-build] Applique les biomes sur un monde deja pose."]
for a in ARENES:
    if a["biome"]:
        cx, cz = a["c"]
        b0.append(f"forceload add {cx - 72} {cz - 72} {cx + 71} {cz + 71}")
b0 += ['tellraw @a {"text":"Biomes : 15 secondes, ne mets pas le jeu en pause.","color":"gold"}', "schedule function atelier:semaine_biomes_2 300t"]
(FN / "semaine_biomes.mcfunction").write_text("\n".join(b0) + "\n", encoding="utf-8")
b1 = ["# [claude-build] Biomes, puis retour au jour."]
for a in ARENES:
    if a["biome"]:
        b1 += biomes(a)
b1 += ["scoreboard players set #biomes2 semaine 1", "forceload remove all", "schedule function atelier:semaine_jour 200t"]
(FN / "semaine_biomes_2.mcfunction").write_text("\n".join(b1) + "\n", encoding="utf-8")
# a l'ouverture du monde : tant que les biomes ne sont pas appliques, la correction se lance seule
(FN / "semaine_auto.mcfunction").write_text("\n".join(["# [claude-build] Lance les biomes a l'ouverture, une seule fois.", "scoreboard objectives add semaine dummy", "execute unless score #biomes2 semaine matches 1 run function atelier:semaine_biomes"]) + "\n", encoding="utf-8")
TAG = D / "data/minecraft/tags/function"
TAG.mkdir(parents=True, exist_ok=True)
(TAG / "load.json").write_text('{"values": ["atelier:semaine_auto"]}\n', encoding="utf-8")

conf = {"_marque": "[claude-build]", "spawn": SPAWN, "arenes": [{k: a[k] for k in ("n", "nom", "f", "c", "y", "porte", "arrivee", "yaw", "retour", "heure", "biome")} for a in ARENES]}
(RACINE / "exports" / "semaine.json").write_text(json.dumps(conf, ensure_ascii=False, indent=1), encoding="utf-8")
gx0 = [C, SOL + 1, C + 20]
ci = {f"porte {a['nom']}": [a["porte"][0] - ORIGINE[0], SOL + 1, a["porte"][2] - ORIGINE[2]] for a in ARENES}
ci["dais"] = [C + 5, SOL + 3, C + 5]
(RACINE / "exports" / "hub7_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": gx0, "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")
md = ["<!-- [claude-build] genere par sources/semaine.py -->", "# Monde final Arene Semaine", "", f"Hub au centre, spawn en {SPAWN[0]} {SPAWN[1]} {SPAWN[2]}. Mourir ramène au hub. Chaque porte du hub téléporte devant l'arène, une arche ramène au hub.", "",
      "| Jour | Arène | Centre (X Z) | Arrivée | Biome |", "|---|---|---|---|---|"]
md += [f"| {a['n']} | {a['nom']} | {a['c'][0]} {a['c'][1]} | {a['arrivee'][0]} {a['arrivee'][1]} {a['arrivee'][2]} | {a['biome'] or 'forêt'} |" for a in ARENES]
md += ["", "Pose : `/reload` puis `/function atelier:semaine_tout`, une seule fois, sans mettre le jeu en pause.", "", "Il faut NeoForge et Create pour ouvrir ce monde. Sur un serveur : `enable-command-block=true`."]
(RACINE / "semaine.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw, ImageFont
COUL = {"smooth_stone": (165, 165, 165), "chiseled_stone_bricks": (135, 135, 135), "stone_bricks": (120, 120, 120), "andesite": (130, 130, 125), "quartz_block": (235, 230, 225), "smooth_quartz": (240, 235, 230), "chiseled_quartz_block": (225, 220, 215),
        "quartz_pillar": (225, 220, 215), "snow_block": (245, 250, 255), "packed_ice": (150, 190, 235), "blue_ice": (110, 165, 240), "blackstone": (50, 45, 55), "nether_bricks": (70, 30, 35), "magma_block": (200, 80, 20), "moss_block": (90, 125, 50),
        "jungle_log": (90, 70, 40), "jungle_planks": (160, 115, 80), "polished_andesite": (150, 150, 145), "brass_casing": (200, 160, 80), "prismarine_bricks": (90, 160, 145), "dark_prismarine": (45, 95, 85), "sea_lantern": (210, 240, 230),
        "deepslate_tiles": (55, 55, 60), "deepslate_bricks": (72, 72, 78), "sculk": (15, 60, 72), "end_stone_bricks": (205, 200, 150), "purpur_block": (165, 115, 170), "purpur_pillar": (175, 125, 180)}
s_ = 8
img = Image.new("RGB", (SX * s_, SZ * s_), (120, 165, 105))
dr = ImageDraw.Draw(img)
police = ImageFont.truetype("arial.ttf", 15)
for x in range(SX):
    for z in range(SZ):
        for y in range(SOL + 14, SOL - 1, -1):
            q = blocs.get((x, y, z))
            if q and q[0].split(":")[1] not in ("air", "oak_sign"):
                c = COUL.get(q[0].split(":")[1], (130, 130, 130))
                dr.rectangle([x * s_, z * s_, x * s_ + s_ - 1, z * s_ + s_ - 1], fill=c)
                break
for a in ARENES:
    px, pz = CEN + 30 * math.cos(math.radians(a["angle"])), CEN + 30 * math.sin(math.radians(a["angle"]))
    dr.text((px * s_, pz * s_), f"{a['n']}. {a['nom']}", fill=(255, 255, 255), font=police, stroke_width=2, stroke_fill=(0, 0, 0), anchor="mm")
img.save(RACINE / "exports" / "hub7_plan.png")
print(f"Ecrit : hub7 {n} blocs, {len(fichiers)} structures copiees, commandes {len(CMD)}")
print("POSE : /reload   puis   /function atelier:semaine_tout")
