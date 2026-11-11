# [claude-build]
"""donjon_aqua_fin.py - blocs de commande (voile, pression des profondeurs, portes, enigmes, ecluse), controles (eau confinee) et ecriture. Execute par donjon_aqua.py."""

CHAINES = [(14, 61, 50), (14, 61, 54), (14, 61, 58), (14, 61, 62)]
CMD = []
VARS = ("#s4", "#g3", "#g6", "#ec", "#cle", "#g5", "#t", "#g1")


def rel(x, y, z):
    i = len(CMD)
    c = CHAINES[i // 32]
    return f"~{x - (c[0] + i % 32)} ~{y - c[1]} ~{z - c[2]}"


def plage(cases):
    xs, ys, zs = zip(*cases)
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def fill(cases, bloc_):
    (a, b, c), (d_, e, f) = plage(cases)
    return f"fill {rel(a, b, c)} {rel(d_, e, f)} {bloc_}"


def ouvrir(nom_):
    p = PORTES[nom_]
    return fill([tuple(c) for c in p["cases"]], "minecraft:water" if p.get("eau") else "minecraft:air")


def sequence(var, boutons, porte_nom):
    for k, b in enumerate(boutons, 1):
        on = f"execute if block {rel(*b)} minecraft:stone_button[powered=true]"
        if k >= 2:
            CMD.append(f"{on} if score {var} aqua matches ..{k - 2} positioned {rel(*b)} run playsound minecraft:block.note_block.bass master @a[distance=..16] ~ ~ ~ 1 0.6")
            CMD.append(f"execute if block {rel(*b)} minecraft:stone_button[powered=true] if score {var} aqua matches ..{k - 2} run scoreboard players set {var} aqua 0")
        CMD.append(f"execute if block {rel(*b)} minecraft:stone_button[powered=true] if score {var} aqua matches {k - 1} run scoreboard players set {var} aqua {k}")
    n = len(boutons)
    CMD.append(f"execute if score {var} aqua matches {n} positioned {rel(*boutons[0])} run playsound minecraft:block.note_block.chime master @a[distance=..30] ~ ~ ~ 1 1")
    CMD.append(f"execute if score {var} aqua matches {n} run " + ouvrir(porte_nom))
    CMD.append(f"execute if score {var} aqua matches {n} run scoreboard players set {var} aqua {n + 1}")


VOILE = "grappin:voile"
CLE = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé du Récif\"']"
CLE_DON = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé du Récif\"',minecraft:lore=['\"Ouvre la porte du Cœur du Temple\"']]"
CMD.append("gamerule commandBlockOutput false")
CMD.append("gamerule doMobSpawning false")
CMD.append("scoreboard objectives add aqua dummy")
for v_ in VARS:
    CMD.append(f"scoreboard players add {v_} aqua 0")
# le voile est remis a l'entree (une seule fois, si on ne l'a pas deja sur soi)
CMD.append(f"execute positioned {rel(10, SOL + 1, 6)} as @a[dx=35,dy=8,dz=32] unless items entity @s armor.head {VOILE} unless items entity @s container.* {VOILE} run give @s {VOILE}")
# aucune autre source de respiration : on efface Respiration aquatique et le conduit dans tout le donjon
CMD.append(f"execute positioned {rel(0, 0, 0)} run effect clear @a[dx={SX - 1},dy={SY - 1},dz={SZ - 1}] minecraft:water_breathing")
CMD.append(f"execute positioned {rel(0, 0, 0)} run effect clear @a[dx={SX - 1},dy={SY - 1},dz={SZ - 1}] minecraft:conduit_power")
# pression des profondeurs : dans les tunnels submerges, qui ne porte pas le voile se noie (toutes les demi-secondes)
CMD.append("scoreboard players add #t aqua 1")
for (x0, x1, y0, y1, z0, z1) in PRESSION:
    CMD.append(f"execute if score #t aqua matches 10.. positioned {rel(x0, y0, z0)} as @a[dx={x1 - x0},dy={y1 - y0},dz={z1 - z0}] unless items entity @s armor.head {VOILE} run damage @s 3 minecraft:drown")
CMD.append("execute if score #t aqua matches 10.. run scoreboard players set #t aqua 0")
# portes et enigmes
ZB = f"if block {rel(*VANNE_G1)} minecraft:oak_trapdoor[open=true] if score #g1 aqua matches 0"          # pas de « positioned » : il decalerait les coordonnees relatives du fill
CMD.append(f"execute {ZB} positioned {rel(*VANNE_G1)} run playsound minecraft:block.chain.break master @a[distance=..40] ~ ~ ~ 1 0.6")
CMD.append(f"execute {ZB} run " + ouvrir("G1"))
CMD.append(f"execute {ZB} run scoreboard players set #g1 aqua 1")
CMD.append(f"execute if block {rel(*BOUTONS_G2)} minecraft:stone_button[powered=true] run " + ouvrir("G2"))
CMD.append(f"execute if block {rel(*LEVIERS['L3a'])} minecraft:lever[powered=true] if block {rel(*LEVIERS['L3b'])} minecraft:lever[powered=true] run " + ouvrir("G3"))
sequence("#s4", [BOUTONS_G4[n] for n in ("poulpe", "raie", "crabe", "etoile")], "G4")
# ecluse : levier en haut = on vide le bassin A, en bas = on le remplit ; le bouton au fond ouvre la grille de T2
bx0, bx1, by0, by1, bz0, bz1 = BASSIN_A
BA = [(bx0, by0, bz0), (bx1, by1, bz1)]
CMD.append(f"execute if block {rel(*LEVIERS['LE'])} minecraft:lever[powered=true] if score #ec aqua matches 0 run " + fill(BA, "minecraft:air replace minecraft:water"))
CMD.append(f"execute if block {rel(*LEVIERS['LE'])} minecraft:lever[powered=true] if score #ec aqua matches 0 run " + ouvrir("G5"))
CMD.append(f"execute if block {rel(*LEVIERS['LE'])} minecraft:lever[powered=true] if score #ec aqua matches 0 run scoreboard players set #ec aqua 1")
CMD.append(f"execute if block {rel(*LEVIERS['LE'])} minecraft:lever[powered=false] if score #ec aqua matches 1 run " + fill(BA, "minecraft:water replace minecraft:air"))
CMD.append(f"execute if block {rel(*LEVIERS['LE'])} minecraft:lever[powered=false] if score #ec aqua matches 1 run scoreboard players set #ec aqua 0")
cond = " ".join(f"if block {rel(*LEVIERS[f'P{x}'])} minecraft:lever[powered=true]" for x in LEV_X)
CMD.append(f"execute {cond} run " + ouvrir("G6"))
# la cle du Recif : la plaque au fond du bassin A la donne, la porte du Coeur la prend
CMD.append(f"execute positioned {rel(*PLAQUE_CLE)} as @a[distance=..1.5] unless items entity @s container.* {CLE} run give @s {CLE_DON} 1")
CMD.append(f"execute if score #cle aqua matches 0 positioned {rel(46, SOL + 1, 135)} as @a[dx=7,dy=3,dz=2] if items entity @s weapon.mainhand {CLE} run scoreboard players set #cle aqua 1")
CMD.append("execute if score #cle aqua matches 1 run " + ouvrir("G7"))
CMD.append(f"execute if score #cle aqua matches 1 positioned {rel(46, SOL + 1, 135)} as @a[dx=7,dy=3,dz=2] run clear @s {CLE} 1")
CMD.append("execute if score #cle aqua matches 1 run scoreboard players set #cle aqua 2")
CMD.append(f"execute if score #cle aqua matches 0 positioned {rel(46, SOL + 1, 135)} as @a[dx=7,dy=3,dz=2] unless items entity @s weapon.mainhand {CLE} run title @s actionbar "
           "{\"text\":\"Porte fermée à clé (Clé du Récif en main)\",\"color\":\"gold\"}")
for n_, c in CACHEES.items():
    if not c["voile"]:
        CMD.append(f"execute if block {rel(*c['bouton'])} minecraft:stone_button[powered=true] run " + ouvrir(f"H{n_}"))
assert len(CMD) <= 32 * len(CHAINES), len(CMD)
for i, c in enumerate(CMD):
    ch = CHAINES[i // 32]
    x, y, z = ch[0] + i % 32, ch[1], ch[2]
    assert not C[x, y, z] and not C[x, y + 1, z] and (x, y, z) in blocs, ("chaine hors de la roche", x, y, z)
    put(x, y, z, S("repeating_command_block" if i % 32 == 0 else "chain_command_block", conditional=False, facing="east"))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(c), "auto": Byte(1), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})
print("[5] commandes :", len(CMD), "/", 32 * len(CHAINES), "| zones de pression :", len(PRESSION), flush=True)

# ====================================================================== controles : eau confinee, rien d'interdit
err = []


def est_eau(s):
    return s[0].split(":")[-1] in ("water", "kelp", "kelp_plant", "seagrass", "tall_seagrass") or dict(s[1]).get("waterlogged") == "true"


for (x, y, z), s in blocs.items():
    n = s[0].split(":")[-1]
    if est_eau(s):
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if q not in blocs and 0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ and C[q]:
                err.append(f"eau qui fuit {(x, y, z)} -> {q}")
            elif q not in blocs and not (0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ):
                err.append(f"eau au bord du gabarit {(x, y, z)}")
        if n in ("soul_sand", "magma_block"):
            err.append(f"colonne de bulles {n} {(x, y, z)}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "trapped_chest", "decorated_pot", "chiseled_bookshelf", "furnace", "conduit", "lava",
             "lectern", "brewing_stand", "campfire", "shulker_box", "bubble_column", "ancrage"):
        err.append(f"interdit {n} {(x, y, z)}")
    if n in ("soul_sand", "magma_block") and blocs.get((x, y + 1, z)) is not None and est_eau(blocs[(x, y + 1, z)]):
        err.append(f"colonne de bulles {n} {(x, y, z)}")
DIRS = {"east": (1, 0, 0), "west": (-1, 0, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
PASSE_ = {"air", "light", "water", "lever", "stone_button", "stone_pressure_plate", "oak_wall_sign", "ladder", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "vine",
          "sea_pickle", "glow_lichen", "iron_bars", "moss_carpet"}


def solide_(q):
    return q in blocs and blocs[q][0].split(":")[-1] not in PASSE_ and not est_eau(blocs[q])


for (x, y, z), s in blocs.items():
    n, pr = s[0].split(":")[-1], dict(s[1])
    if n in ("lever", "stone_button"):
        if pr["face"] == "floor":
            sup = (0, -1, 0)
        else:
            dx, dy, dz = DIRS[pr["facing"]]
            sup = (-dx, 0, -dz)
        if not solide_((x + sup[0], y + sup[1], z + sup[2])):
            err.append(f"{n} sans appui {(x, y, z)}")
    elif n == "oak_wall_sign":
        dx, dy, dz = DIRS[pr["facing"]]
        if not solide_((x - dx, y, z - dz)):
            err.append(f"panneau sans appui {(x, y, z)}")
    elif n == "stone_pressure_plate" and not solide_((x, y - 1, z)):
        err.append(f"plaque sans appui {(x, y, z)}")
    if n in ("lever", "stone_button", "stone_pressure_plate"):             # l'eau qui coule dans la case d'un interrupteur le detruit : aucune eau a cote ni au dessus
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y + 1, z)):
            if q in blocs and est_eau(blocs[q]):
                err.append(f"{n} au contact de l'eau {(x, y, z)}")
    if n == "oak_wall_sign":
        for l_ in NBT[(x, y, z)]["front_text"]["messages"]:
            if len(str(l_).split('"text":"')[1].rsplit('"', 1)[0]) > 14:
                err.append(f"panneau trop large {(x, y, z)}")
for n_, c in CACHEES.items():
    x0, x1, y0, y1, z0, z1 = c["boite"]
    if min(x1 - x0 + 1, z1 - z0 + 1) < 7 or y1 - y0 + 1 < 4:
        err.append(f"salle cachee {n_} trop petite")
print("[controle] erreurs", len(err), Counter(e.split(" (")[0] for e in err).most_common(6), [e for e in err if "fuit" not in e][:10] + [e for e in err if "fuit" in e][:3], flush=True)
if err:
    raise SystemExit(1)


# ====================================================================== ecriture
def ecrire(cases, nbt_, nom_, taille):
    palette, index, liste = [], {}, []
    for p in sorted(cases, key=lambda q: (q[1], q[2], q[0])):
        s = cases[p]
        if s not in index:
            index[s] = len(palette)
            c = Compound({"Name": String(s[0])})
            if s[1]:
                c["Properties"] = Compound({k: String(v) for k, v in s[1]})
            palette.append(c)
        e = Compound({"pos": List[Int]([Int(v) for v in p]), "state": Int(index[s])})
        if p in nbt_:
            e["nbt"] = nbt_[p]
        liste.append(e)
    f = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette),
                     "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
    (RACINE / "exports").mkdir(exist_ok=True)
    f.save(RACINE / "exports" / f"{nom_}.nbt")
    D = SAVE / "datapacks/atelier/data/atelier/structure"
    if D.exists():
        f.save(D / f"{nom_}.nbt")
    return len(liste)


# le script cree lui-meme le datapack du monde vise (dossiers et pack.mcmeta) : il ne suppose aucun etat local deja en place
DP = SAVE / "datapacks/atelier"
if SAVE.exists():
    (DP / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    (DP / "data/atelier/function").mkdir(parents=True, exist_ok=True)
    if not (DP / "pack.mcmeta").exists():
        (DP / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Atelier - structures generees"}}, indent=2), encoding="utf-8")
else:
    print(f"ATTENTION : le monde « {args.monde} » n'existe pas dans {SAVE.parent} ; seuls les fichiers de atelier/exports sont ecrits. "
          "Cree le monde dans le client de test (ou passe --monde) puis relance.", flush=True)
n_total = ecrire(blocs, NBT, NOM, (SX, SY, SZ))


def w(p):
    return " ".join(str(v) for v in (ORIGINE[0] + p[0], ORIGINE[1] + p[1], ORIGINE[2] + p[2]))


FN = SAVE / "datapacks/atelier/data/atelier/function"
if FN.exists():
    fl = f"forceload add {ORIGINE[0]} {ORIGINE[2]} {ORIGINE[0] + SX - 1} {ORIGINE[2] + SZ - 1}"
    (FN / "aqua_pose.mcfunction").write_text("\n".join([
        "# [claude-build] Pose le Sanctuaire des Marees (sans pause).", fl,
        'tellraw @a {"text":"Sanctuaire : 15 secondes de chargement, puis le jeu va se figer un moment. Ne mets pas en pause.","color":"gold"}',
        "schedule function atelier:aqua_pose_2 300t"]) + "\n", encoding="utf-8")
    fills = [f"fill {ORIGINE[0] + a} {ORIGINE[1] + b} {ORIGINE[2] + c} {ORIGINE[0] + min(a + 30, SX - 1)} {ORIGINE[1] + min(b + 30, SY - 1)} "
             f"{ORIGINE[2] + min(c + 30, SZ - 1)} minecraft:air" for a in range(0, SX, 31) for b in range(1, SY, 31) for c in range(0, SZ, 31)]
    (FN / "aqua_pose_2.mcfunction").write_text("\n".join(["# [claude-build] Efface la zone au dessus du sol, pose la structure, remet a zero."] + fills + [
        f"place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}", "kill @e[type=item]", "function atelier:aqua_reset",
        "schedule function atelier:aqua_pose_3 100t"]) + "\n", encoding="utf-8")
    (FN / "aqua_pose_3.mcfunction").write_text("\n".join([
        "# [claude-build] Nettoie et decharge.", "kill @e[type=item]", "forceload remove all",
        f'tellraw @a {{"text":"Le sanctuaire est pret. Entree : /tp @s {w(POINTS["S1"])} 0 0","color":"green"}}']) + "\n", encoding="utf-8")
    pc_ = [tuple(c) for c in PORTES["SORTIE"]["cases"]]
    (a, b, c), (d_, e, f_) = plage(pc_)
    (FN / "aqua_ouvrir_sortie_boss.mcfunction").write_text(
        f"# [claude-build] Ouvre la sortie du boss (a relier a la mort du boss).\nfill {w((a, b, c))} {w((d_, e, f_))} minecraft:air\n"
        f"playsound minecraft:block.iron_door.open master @a {w((a, b, c))} 2 0.6\n", encoding="utf-8")
    (FN / "aqua_fermer_sortie_boss.mcfunction").write_text(
        f"# [claude-build] Referme la sortie du boss.\nfill {w((a, b, c))} {w((d_, e, f_))} minecraft:iron_bars[waterlogged=false]\n", encoding="utf-8")
    reset = ["# [claude-build] Remet le sanctuaire a zero : portes fermees, leviers baisses, ecluse et cle remises."]
    for n_, p in PORTES.items():
        props = ",".join(f"{k}={v}" for k, v in p["props"].items())
        (a, b, c), (d_, e, f_) = plage([tuple(x) for x in p["cases"]])
        reset.append(f"fill {w((a, b, c))} {w((d_, e, f_))} {p['bloc']}{'[' + props + ']' if props else ''}")
    for n_, p in LEVIERS.items():
        face = "east" if n_ in ("L3a", "L3b", "LE", "L1") else "south"
        reset.append(f"setblock {w(p)} minecraft:lever[face=wall,facing={face},powered=false]")
    reset.append(f"fill {w((bx0, by0, bz0))} {w((bx1, by1, bz1))} minecraft:water replace minecraft:air")
    reset.append(f"setblock {w(VANNE_G1)} minecraft:oak_trapdoor[half=bottom,facing=north,open=false,powered=false,waterlogged=true]")
    reset += ["scoreboard objectives add aqua dummy"] + [f"scoreboard players set {v_} aqua 0" for v_ in VARS]
    reset += [f"clear @a {CLE}", 'tellraw @a {"text":"Sanctuaire remis a zero.","color":"gray"}']
    (FN / "aqua_reset.mcfunction").write_text("\n".join(reset) + "\n", encoding="utf-8")
conf = {"_marque": "[claude-build]", "taille": [SX, SY, SZ], "origine": ORIGINE, "portes": PORTES, "points": POINTS, "entree": list(POINTS["S1"]), "tunnels": {k: v for k, v in TUNNELS.items()},
        "longueurs": LONG, "pression": PRESSION, "cachees_detail": {str(k): v for k, v in CACHEES.items()}, "boss": BOSS, "niveau_eau": SOL, "bassin_a": list(BASSIN_A),
        "cibles": {"S1": list(POINTS["S1"]), "vanne_G1": list(VANNE_G1), "bouton_G2": list(BOUTONS_G2), "levier_3a": list(LEVIERS["L3a"]), "levier_3b": list(LEVIERS["L3b"]),
                   "glyphe_poulpe": list(BOUTONS_G4["poulpe"]), "glyphe_raie": list(BOUTONS_G4["raie"]), "glyphe_crabe": list(BOUTONS_G4["crabe"]),
                   "glyphe_etoile": list(BOUTONS_G4["etoile"]), "levier_ecluse": list(LEVIERS["LE"]), "plaque_cle": list(PLAQUE_CLE),
                   "levier_p20": list(LEVIERS["P20"]), "levier_p24": list(LEVIERS["P24"]), "levier_p28": list(LEVIERS["P28"]),
                   "arrivee_T1": list(POINTS["arrivee_T1"]), "arrivee_T2": list(POINTS["arrivee_T2"]), "arrivee_T3": list(POINTS["arrivee_T3"]), "Coeur": list(POINTS["Coeur"]),
                   "sortie": list(POINTS["sortie"]), **{k: list(v) for k, v in BOSS.items()},
                   **{f"H{k}": list(v["coffre"]) for k, v in CACHEES.items()},
                   **{f"bouton_H{k}": list(v["bouton"]) for k, v in CACHEES.items() if not v["voile"]}}}
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps(conf, ensure_ascii=False), encoding="utf-8")
np.savez_compressed(RACINE / "exports" / f"{NOM}_vide.npz", C=np.packbits(C.ravel()), W=np.packbits(W.ravel()), taille=np.array([SX, SY, SZ]))
print(f"Ecrit : {n_total} blocs | commandes {len(CMD)} | portes {list(PORTES)}")
