# [claude-build]
"""station_logique.py - les fonctions de datapack de la suite du Donjon de la Station (station_secteurs.py) : ecluses, Drain, secteurs A, B, C, pompes et niveaux de la Cuve,
Cheminee, Poste et levier general, Reservoir, boss et Exutoire, et leur remise a zero.
fonctions(infos, infos1, blocs, txt) -> ({nom: texte}, lignes du tick, lignes de la remise a zero, scenes de cinematique)."""
import math

import station_acte1 as a1
import station_cine
import station_create
import suspendu_cine as sc

DUREE_BAISSE = 170
DUREE_VANNE_D = 180
DUREE_BONDE = 170                       # le levier general : la bonde s'ouvre au fond de la Cuve, le tourbillon se forme                     # la troisieme pompe : la camera plonge du haut de la Cuve jusqu'a la vanne D (9 s)
NAGE = "predicate=atelier:nage"
NIVEAUX = (a1.NIVEAUX["haut"], a1.NIVEAUX["median"], a1.NIVEAUX["bas"])


def etat(b):
    nom, props = b
    return nom + ("[" + ",".join(f"{k}={v}" for k, v in props) + "]" if props else "")


def _decalage(txt):
    return [int(v) for v in txt((0, 0, 0)).split()]


def monde_dx(txt):
    return _decalage(txt)[0]


def monde_dy(txt):
    return _decalage(txt)[1]


def monde_dz(txt):
    return _decalage(txt)[2]


def fonctions(infos, infos1, blocs, txt):
    f, tick, reset = {}, [], []

    def boite(b):
        """(x0, x1, y0, y1, z0, z1) -> (coin, selecteur dx/dy/dz)."""
        x0, x1, y0, y1, z0, z1 = b
        return txt((x0, y0, z0)), f"dx={x1 - x0},dy={y1 - y0},dz={z1 - z0}"

    def dans(b, extra=""):
        coin, sel = boite(b)
        return coin, f"@a[{sel},gamemode=!spectator{extra}]"

    def poser_cases(cases, bloc):
        return [f"setblock {txt(p)} {bloc}" for p in cases]

    def remettre(cases):
        return [f"setblock {txt(p)} {etat(blocs.get(p, ('minecraft:stone', ())))}" for p in cases]

    def pompe_bb(pompe, marche):
        """La pompe Blockbench de la salle : ses voyants s'allument (en_marche) quand la pompe repart."""
        if "pompe_bb" not in pompe:
            return []
        p, face, eau = pompe["pompe_bb"]
        return [f"setblock {txt(p)} donjon:pompe_station[facing={face},en_marche={'true' if marche else 'false'},waterlogged={'true' if eau else 'false'}]"]

    def pousser(b, v, nom):
        """Un courant : pousse de v (blocs par tick) tout joueur qui flotte (de l'eau sous les pieds), si la case d'arrivee est de l'eau ; qui marche au fond (ou tient une echelle) n'est pas pousse."""
        coin, sel = boite(b)
        dx, dy, dz = v
        tick.append(f"execute positioned {coin} as @a[{sel},gamemode=!spectator] at @s if block ~ ~-0.6 ~ minecraft:water if block ~{dx} ~{dy} ~{dz} minecraft:water run tp @s ~{dx} ~{dy} ~{dz}")

    def monstres(nom, liste, condition_boite, drapeau):
        """Une seule fois, quand un joueur entre dans la boite : [(type, position, nbt en plus)]."""
        coin, sel = boite(condition_boite)
        lignes = [f"# [claude-build] Un joueur entre : les monstres de « {nom} » apparaissent (une seule fois).", f"scoreboard players set {drapeau} station 1"]
        for (typ, p, plus) in liste:
            lignes.append(f"summon minecraft:{typ} {txt(p)} {{Tags:[\"station_emb\"],PersistenceRequired:1b{plus}}}")
            lignes.append(f"execute positioned {txt(p)} run particle minecraft:bubble_pop ~ ~0.5 ~ 0.5 0.5 0.5 0.05 20")
        f[f"station_{nom}_monstres"] = "\n".join(lignes) + "\n"
        tick.append(f"execute if score {drapeau} station matches 0 positioned {coin} if entity @a[{sel},gamemode=!spectator] run function atelier:station_{nom}_monstres")
        reset.append(f"scoreboard players set {drapeau} station 0")

    # ================================================================= le Drain
    for (b, v) in infos["drain"]["poussees"]:
        pousser(b, v, "drain")

    # ================================================================= les bouches A, B, C : scellees tant que la Cuve est seche, grandes ouvertes ensuite (plus de sas a double vanne)
    ouvrir, fermer = ["# [claude-build] La Cuve est pleine : les bouches des secteurs A, B, C s'ouvrent pour de bon (leurs barreaux et ceux du fond du couloir disparaissent)."], \
        ["# [claude-build] Rescelle les bouches A, B, C (remise a zero) : barreaux en place, couloirs pleins."]
    for s_, e in infos["ecluses"].items():
        sas = e["sas"]
        ouvrir += poser_cases(e["externe"] + e["interne"], "minecraft:water")
        fermer += [*poser_cases(e["externe"], "minecraft:iron_bars[waterlogged=false]"), *poser_cases(e["interne"], "minecraft:iron_bars[waterlogged=true]"),
                   f"fill {txt(min(sas))} {txt(max(sas))} minecraft:water"]
    ouvrir.append('tellraw @a {"text":"Les bouches des secteurs A, B et C sont ouvertes.","color":"aqua"}')
    f["station_bouches_ouvrir"] = "\n".join(ouvrir) + "\n"
    f["station_ecluses_fermer"] = "\n".join(fermer) + "\n"
    reset.append("function atelier:station_ecluses_fermer")

    # ================================================================= les pompes : une balise (colonne de particules) tant qu'elles ne sont pas faites ; leur moteur Create au retour
    P = infos["pompes"]
    for k, drapeau in (("A", "#pA"), ("B", "#pB"), ("C", "#pC")):
        v = txt(P[k]["volant"])
        tick.append(f"execute if score #niv station matches 1.. if score {drapeau} station matches 0 positioned {v} run particle minecraft:end_rod ~0.5 ~1.5 ~0.5 0.15 2 0.15 0.01 3 force @a[distance=..96]")
        reset.extend(remettre([P[k]["moteur"]]))
        reset.extend(pompe_bb(P[k], False))
    reset += [f"setblock {txt(p)} donjon:pompe_station[facing={face},en_marche=false,waterlogged=true]" for p, face in infos["blockbench"]["pompes_reservoir"]]
    # ================================================================= secteur A
    A = infos["a"]
    monstres("a3", [("guardian", p, "") for p in A["gardiens_a3"]] + [("drowned", p, "") for p in A["noyes_a3"]], (A["a3"][0], A["a3"][1], A["a3"][4], A["a3"][5], A["a3"][2], A["a3"][3]), "#a3")
    monstres("a4", [("guardian", p, "") for p in A["gardiens_a4"]], (A["a4"][0], A["a4"][1], A["a4"][4], A["a4"][5], A["a4"][2], A["a4"][3]), "#a4")
    tick += station_create.lignes_volant("pA", txt(A["volant_pos"]), "station_pompe_a", "#pA")
    f["station_pompe_a"] = "\n".join([
        "# [claude-build] Le volant A est tourne : la pompe A repart.",
        "scoreboard players set #pA station 1",
        f"execute positioned {txt(A['pompe'][0])} run particle minecraft:bubble_column_up ~ ~2 ~ 1 3 1 0.2 200",
        f"execute positioned {txt(A['pompe'][0])} run playsound minecraft:block.beacon.power_select master @a[distance=..80] ~ ~ ~ 2 0.6",
        'tellraw @a {"text":"La pompe A (Filtration) tourne.","color":"aqua"}', f"setblock {txt(P['A']['moteur'])} create:creative_motor[facing={P['A']['facing']}]", *pompe_bb(P['A'], True), *[f"setblock {txt(p)} donjon:pompe_station[facing={face},en_marche=true,waterlogged=true]" for p, face in infos["salle_lits"]["pompes"]], "function atelier:station_pompe_faite"]) + "\n"
    reset += [f"setblock {txt(p)} donjon:pompe_station[facing={face},en_marche=false,waterlogged=true]" for p, face in infos["salle_lits"]["pompes"]]
    reset += ["scoreboard players set #pA station 0", "scoreboard players set #cpA station 0", "scoreboard players set #cpB station 0", "scoreboard players set #cpC station 0"]

    # ================================================================= secteur B
    B = infos["b"]
    pousser(*B["collecteur"], "collecteur")
    monstres("b2", [("drowned", p, "") for p in B["noyes"]], (B["b2"][0], B["b2"][1], B["b2"][4], B["b2"][5], B["b2"][2], B["b2"][3]), "#b2")
    monstres("b3", [("blaze", p, "") for p in B["blazes"]], (B["b3"][0], B["b3"][1], B["b3"][4], B["b3"][5], B["b3"][2], B["b3"][3]), "#b3")
    effets = {1: ["# ouvre le jet qui garde la plaque 2", *poser_cases(B["jets"][2], "minecraft:water")],
              2: ["# ouvre le jet qui garde la plaque 3", *poser_cases(B["jets"][3], "minecraft:water")],
              3: ["# ouvre la colonne de bulles vers la halle haute", *poser_cases(B["vanne_colonne"], "minecraft:water"), *poser_cases(B["sable"], "minecraft:soul_sand"),
                  'tellraw @a {"text":"Une colonne de bulles monte vers la halle haute.","color":"gold"}']}
    for k, cases in enumerate(B["plaques"], start=1):
        x0 = min(p[0] for p in cases); x1 = max(p[0] for p in cases); z0 = min(p[2] for p in cases); z1 = max(p[2] for p in cases)
        coin, sel = boite((x0, x1, cases[0][1], cases[0][1], z0, z1))
        tick.append(f"execute if score #pl{k} station matches 0 positioned {coin} as @a[{sel},gamemode=!spectator] at @s unless block ~ ~-0.6 ~ minecraft:water run function atelier:station_b_plaque_{k}")
        f[f"station_b_plaque_{k}"] = "\n".join([
            f"# [claude-build] Plaque lestee {k} : un joueur se tient debout dessus (au fond, pas en nageant).", f"scoreboard players set #pl{k} station 1", *effets[k],
            f"execute positioned {txt(cases[4])} run particle minecraft:wax_on ~ ~0.2 ~ 1 0.1 1 0.1 30",
            f"execute positioned {txt(cases[4])} run playsound minecraft:block.copper_bulb.turn_on master @a[distance=..40] ~ ~ ~ 2 0.7"]) + "\n"
        reset.append(f"scoreboard players set #pl{k} station 0")
    for k, porte in B["jets"].items():
        reset += poser_cases(porte, "minecraft:iron_bars[waterlogged=true]")
        tick.append(f"execute if score #pl{k - 1} station matches 0 positioned {txt(porte[len(porte) // 2])} if entity @a[distance=..20] run particle minecraft:bubble_column_up ~ ~ ~ 1 2 0.2 0.3 6")
    reset += poser_cases(B["vanne_colonne"], "minecraft:iron_bars[waterlogged=true]") + remettre(B["sable"])
    tick += ["execute if score #b3 station matches 1 run scoreboard players add #vap station 1", "execute if score #vap station matches 80.. run scoreboard players set #vap station 0"]
    for b in B["vapeur"]:
        coin, sel = boite(b)
        x0, x1, y0, y1, z0, z1 = b
        milieu = txt(((x0 + x1) // 2, y0, (z0 + z1) // 2))
        tick += [f"execute if score #b3 station matches 1 if score #vap station matches 0..19 positioned {milieu} run particle minecraft:cloud ~ ~0.5 ~ {(x1 - x0) / 2 + 0.3} 1 1 0.02 12",
                 f"execute if score #vap station matches 0 positioned {milieu} run playsound minecraft:block.fire.extinguish master @a[distance=..30] ~ ~ ~ 1.5 0.6",
                 f"execute if score #vap station matches 5 positioned {coin} as @a[{sel},gamemode=!spectator] run damage @s 2 minecraft:hot_floor",
                 f"execute if score #vap station matches 15 positioned {coin} as @a[{sel},gamemode=!spectator] run damage @s 2 minecraft:hot_floor"]
    lev = txt(B["levier"])
    tick += station_create.lignes_volant("pB", txt(B["volant_pos"]), "station_pompe_b", "#pB")
    f["station_pompe_b"] = "\n".join([
        "# [claude-build] Le volant B est tourne : la pompe B repart ; l'echelle de sortie vers les chaudieres s'ouvre.",
        "scoreboard players set #pB station 1", *poser_cases(B["vanne_sortie"], "minecraft:air"),
        f"execute positioned {txt(B['pompe'][0])} run particle minecraft:large_smoke ~ ~2 ~ 0.5 2 0.5 0.05 60",
        f"execute positioned {txt(B['pompe'][0])} run playsound minecraft:block.beacon.power_select master @a[distance=..80] ~ ~ ~ 2 0.6",
        'tellraw @a {"text":"La pompe B (Chaufferie) tourne. Une echelle redescend vers les chaudieres.","color":"aqua"}', f"setblock {txt(P['B']['moteur'])} create:creative_motor[facing={P['B']['facing']}]", *pompe_bb(P['B'], True), "function atelier:station_pompe_faite"]) + "\n"
    reset += [*poser_cases(B["vanne_sortie"], "minecraft:iron_bars[waterlogged=false]"),
              "scoreboard players set #pB station 0", "scoreboard players set #vap station 0"]

    # ================================================================= secteur C : turbines (nager contre elles 2 s), tartre (nager contre lui, turbine lancee), gardien du rotor
    C = infos["c"]
    for k, pale in C["pales"].items():
        centre = txt(pale["centre"])
        tick += [f"execute if score #t{k} station matches ..39 positioned {centre} if entity @a[distance=..4.5,{NAGE}] run scoreboard players add #t{k} station 1",
                 f"execute if score #t{k} station matches 1..39 positioned {centre} unless entity @a[distance=..4.5,{NAGE}] run scoreboard players remove #t{k} station 1",
                 f"execute if score #t{k} station matches 1..39 positioned {centre} run particle minecraft:bubble ~ ~ ~ 2 0.3 2 0.1 4",
                 f"execute if score #t{k} station matches 40 run function atelier:station_c_turbine_{k}",
                 f"execute if score #t{k} station matches 1000 positioned {centre} run particle minecraft:bubble_column_up ~ ~ ~ 3 0.4 3 0.2 6"]
        vague = [("drowned", p, "") for p in C["vagues"][k][:3]] + [("guardian", C["vagues"][k][3], "")]
        lignes = [f"# [claude-build] La turbine T{k} est lancee : elle tourne et pousse l'eau vers le haut ; une vague de monstres arrive.", f"scoreboard players set #t{k} station 1000",
                  f"execute positioned {centre} run playsound minecraft:block.beacon.activate master @a[distance=..60] ~ ~ ~ 2 0.6",
                  f'tellraw @a {{"text":"La turbine T{k} tourne.","color":"aqua"}}']
        lignes += [f"summon minecraft:{t} {txt(p)} {{Tags:[\"station_emb\"],PersistenceRequired:1b}}" for (t, p, _) in vague]
        lignes.append(f"setblock {txt(C['moteurs'][k])} create:creative_motor[facing=up]")
        reset.append(f"setblock {txt(C['moteurs'][k])} create:shaft[axis=y]")
        if k == 1:
            lignes += poser_cases(C["sable"], "minecraft:soul_sand")
        if k == 3:
            lignes += poser_cases(C["grille_salle"], "minecraft:air") + ['tellraw @a {"text":"La grille de la salle de la pompe C se leve.","color":"gold"}']
        f[f"station_c_turbine_{k}"] = "\n".join(lignes) + "\n"
        reset.append(f"scoreboard players set #t{k} station 0")
    tx, _, tz = C["centre_tartre"]
    for k, y in enumerate(C["grilles"], start=1):
        cases = C["tartres"][k]
        cond = f"if score #t{k} station matches 1000" + (" unless entity @e[tag=station_rotor]" if k == 2 else "")
        tick.append(f"execute if score #tar{k} station matches 0 {cond} positioned {txt((tx, y - 1, tz))} if entity @a[distance=..4,{NAGE}] run function atelier:station_c_tartre_{k}")
        f[f"station_c_tartre_{k}"] = "\n".join([f"# [claude-build] Le tartre de la grille {k} se brise.", f"scoreboard players set #tar{k} station 1", *poser_cases(cases, "minecraft:water"),
                                                f"execute positioned {txt((tx, y, tz))} run particle minecraft:block{{block_state:{{Name:\"minecraft:dripstone_block\"}}}} ~ ~ ~ 2 0.5 2 0.1 80",
                                                f"execute positioned {txt((tx, y, tz))} run playsound minecraft:block.dripstone_block.break master @a[distance=..40] ~ ~ ~ 2 0.6"]) + "\n"
        reset += poser_cases(cases, "minecraft:dripstone_block") + [f"scoreboard players set #tar{k} station 0"]
    reset += remettre(C["sable"]) + poser_cases(C["grille_salle"], "minecraft:iron_bars[waterlogged=false]")
    cx, cz = C["puits"]
    r = int(C["rayon"])
    monstres("rotor", [("elder_guardian", C["rotor"], ',Tags:["station_emb","station_rotor"],CustomName:\'"Gardien du rotor"\'')],
             (int(cx) - r, int(cx) + r, C["grilles"][0] + 1, C["grilles"][1] - 1, int(cz) - r, int(cz) + r), "#rotor")
    f["station_rotor_monstres"] = f["station_rotor_monstres"].replace('{Tags:["station_emb"],PersistenceRequired:1b,Tags:["station_emb","station_rotor"]', '{PersistenceRequired:1b,Tags:["station_emb","station_rotor"]')
    lev = txt(C["levier"])
    tick += station_create.lignes_volant("pC", txt(C["volant_pos"]), "station_pompe_c", "#pC")
    f["station_pompe_c"] = "\n".join([
        "# [claude-build] Le volant C est tourne : la pompe C repart.", "scoreboard players set #pC station 1",
        f"execute positioned {txt(C['pompe'][0])} run particle minecraft:bubble_column_up ~ ~2 ~ 1 2 1 0.2 100",
        f"execute positioned {txt(C['pompe'][0])} run playsound minecraft:block.beacon.power_select master @a[distance=..80] ~ ~ ~ 2 0.6",
        'tellraw @a {"text":"La pompe C (Turbines) tourne.","color":"aqua"}', f"setblock {txt(P['C']['moteur'])} create:creative_motor[facing={P['C']['facing']}]", *pompe_bb(P['C'], True), "function atelier:station_pompe_faite"]) + "\n"
    reset += ["scoreboard players set #pC station 0"]

    # ================================================================= les pompes : un temoin, la Cuve baisse d'un cran (Haut -> Median -> Bas), a la troisieme la vanne D se leve
    cuve = infos1["cuve"]
    temoins = infos1["temoins_pompes"]
    f["station_pompe_faite"] = "\n".join([
        "# [claude-build] Une pompe de plus : son temoin s'allume sur la Coursive ; la Cuve baisse d'un cran (cinematique, ou tout de suite si une autre est en cours) ; a la troisieme, la vanne D se leve.",
        "scoreboard players add #pompes station 1",
        "execute store result bossbar atelier:station value run scoreboard players get #pompes station",
        *[f'execute if score #pompes station matches {k} run bossbar set atelier:station name "Station : pompes {k}/3"' for k in (1, 2, 3)],
        *[f"execute if score #pompes station matches {k + 1} run setblock {txt(p)} minecraft:waxed_copper_bulb[lit=true,powered=false]" for k, p in enumerate(temoins)],
        "scoreboard players operation #libre cine = #t cine",
        "execute if score #pompes station matches 1 if score #libre cine matches 0 run function atelier:station_cine_debut_median",
        "execute if score #pompes station matches 1 unless score #libre cine matches 0 run function atelier:station_baisse_median",
        "execute if score #pompes station matches 2 if score #libre cine matches 0 run function atelier:station_cine_debut_bas",
        "execute if score #pompes station matches 2 unless score #libre cine matches 0 run function atelier:station_baisse_bas",
        *[f"execute if score #pompes station matches 3 run setblock {txt(p)} donjon:pompe_station[facing={face},en_marche=true,waterlogged=true]"
          for p, face in infos["blockbench"]["pompes_reservoir"]],                    # les pompes du Reservoir repartent avec la troisieme
        "execute if score #pompes station matches 3 if score #libre cine matches 0 run function atelier:station_cine_debut_vanne_d",
        "execute if score #pompes station matches 3 unless score #libre cine matches 0 run function atelier:station_vanne_d"]) + "\n"
    rangees = cuve["rangees"]
    for y in range(NIVEAUX[2] + 1, NIVEAUX[0] + 1):
        f[f"station_vide_{y}"] = "\n".join([f"# [claude-build] La Cuve baisse : la couche {y} se vide.", *[f"fill {txt((xa, y, z))} {txt((xb, y, z))} minecraft:air replace minecraft:water" for (xa, xb, z) in rangees]]) + "\n"
    for nom, (haut, bas) in (("median", (NIVEAUX[0], NIVEAUX[1])), ("bas", (NIVEAUX[1], NIVEAUX[2]))):
        f[f"station_baisse_{nom}"] = "\n".join([f"# [claude-build] La Cuve passe d'un coup au niveau {nom} ({bas}).", *[f"function atelier:station_vide_{y}" for y in range(haut, bas, -1)],
                                                 f"scoreboard players set #niv station {bas}", f'tellraw @a {{"text":"La Grande Cuve baisse : niveau {nom.upper()}.","color":"aqua"}}']) + "\n"
    reset += [f"setblock {txt(p)} minecraft:waxed_copper_bulb[lit=false,powered=false]" for p in temoins] + ["scoreboard players set #pompes station 0", "scoreboard players set #niv station 0"]
    D = infos["d"]
    rangs_d = sorted({p[1] for p in D["vanne_d"]})
    for y in rangs_d:
        cases = [p for p in D["vanne_d"] if p[1] == y]
        f[f"station_vanne_d_rang_{y}"] = "\n".join([f"# [claude-build] La vanne D se leve : sa rangee {y} disparait (de bas en haut).", *poser_cases(cases, "minecraft:water"),
                                                    f"execute positioned {txt(cases[len(cases) // 2])} run particle minecraft:bubble_column_up ~0.5 ~ ~0.5 1.2 0.3 1.2 0.2 40 force",
                                                    f"execute positioned {txt(cases[len(cases) // 2])} run playsound minecraft:block.piston.contract master @a[distance=..120] ~ ~ ~ 2 {0.5 + 0.08 * (y - rangs_d[0]):.2f}"]) + "\n"
    f["station_vanne_d_ouverte"] = "\n".join(["# [claude-build] La vanne D est levee : la Cheminee est ouverte (fin de la cinematique).", "scoreboard players set #d station 1",
                                               'tellraw @a {"text":"Au fond de la Cuve, au nord-est, la vanne D est ouverte.","color":"gold"}']) + "\n"
    f["station_vanne_d"] = "\n".join(["# [claude-build] La vanne D se leve : la Cheminee est ouverte.", "scoreboard players set #d station 1", *poser_cases(D["vanne_d"], "minecraft:water"),
                                      f"execute positioned {txt(D['vanne_d'][0])} run playsound minecraft:block.piston.contract master @a[distance=..80] ~ ~ ~ 3 0.5",
                                      'tellraw @a {"text":"Au fond de la Cuve, au nord-est, la vanne D se leve.","color":"gold"}',
                                      'title @a title {"text":"Va au fond de la Cuve","color":"gold"}',
                                      'title @a subtitle {"text":"la vanne D est ouverte, au nord-est : la Cheminée","color":"aqua"}']) + "\n"
    reset += poser_cases(D["vanne_d"], "minecraft:iron_bars[waterlogged=false]") + ["scoreboard players set #d station 0"]

    # les cinematiques : la camera suit la surface qui descend (elle reste 3 blocs au dessus) ; la vanne D vue de la Cuve
    cxu, czu = a1.CUVE
    scenes = {}
    for nom, (haut, bas) in (("median", (NIVEAUX[0], NIVEAUX[1])), ("bas", (NIVEAUX[1], NIVEAUX[2]))):
        cam = [(1, (cxu - 4.0, haut + 3.0, czu - 22.0)), (DUREE_BAISSE, (cxu - 4.0, bas + 3.0, czu - 22.0))]
        vise = [(1, (cxu, haut, czu + 14.0)), (DUREE_BAISSE, (cxu, bas, czu + 14.0))]
        images = sc.images(cam, vise, DUREE_BAISSE)
        ev = {2: ['title @a[tag=cine] actionbar {"text":"La pompe tourne : la Cuve baisse…","color":"gray"}', "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.power_select master @s ~ ~ ~ 1 0.5"]}
        for y in range(haut, bas, -1):
            t = next((t for t, im in enumerate(images, start=1) if im[1] <= y + 2.0), DUREE_BAISSE)
            ev.setdefault(t, []).append(f"function atelier:station_vide_{y}")
        for t in range(10, DUREE_BAISSE - 10, 25):
            ev.setdefault(t, []).append("execute as @a[tag=cine] at @s run playsound minecraft:block.water.ambient master @s ~ ~ ~ 1 0.5")
        ev.setdefault(DUREE_BAISSE - 4, []).extend([f"scoreboard players set #niv station {bas}", station_cine.son("chime", 0.8),
                                                    f'title @a[tag=cine] actionbar {{"text":"Niveau {nom.upper()}","color":"aqua"}}'])
        scenes[nom] = {"cles": (cam, vise), "duree": DUREE_BAISSE, "centre": txt((int(cxu), a1.SOL, int(czu))), "rayon": 400, "evenements": ev}
    d0 = D["vanne_d"][len(D["vanne_d"]) // 2]
    cible = (d0[0] + 0.5, d0[1] + 0.5, d0[2] + 0.5)
    # la camera part du haut de la Cuve, cote oppose (sud-ouest), et plonge en ligne droite vers la vanne D, au fond : elle ne tourne jamais (fluidite) ;
    # la vanne se leve quand on la voit de pres, puis le message dit ou aller
    a_dep = math.radians(220)
    depart = (cxu + 18 * math.sin(a_dep), 82.0, czu - 18 * math.cos(a_dep))
    point = lambda k: tuple(depart[i] + (cible[i] - depart[i]) * k for i in range(3))                     # noqa: E731
    cam_d = [(1, depart), (55, point(0.6)), (85, point(0.86)), (DUREE_VANNE_D, point(0.9))]          # elle ralentit et s'arrete devant la vanne pour la regarder s'ouvrir
    vise_d = [(1, cible), (DUREE_VANNE_D, cible)]
    ev_d = {2: ['title @a[tag=cine] actionbar {"text":"Troisième pompe : la station est prête…","color":"gray"}',
                "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.power_select master @s ~ ~ ~ 1 0.6"],
            30: ['title @a[tag=cine] title {"text":"Va au fond de la Cuve","color":"gold"}', 'title @a[tag=cine] subtitle {"text":"au nord-est, la vanne D","color":"aqua"}'],
            80: ["execute as @a[tag=cine] at @s run playsound minecraft:block.iron_door.open master @s ~ ~ ~ 1 0.5"],
            130: ["function atelier:station_vanne_d_ouverte", f"execute positioned {txt(d0)} run particle minecraft:bubble_column_up ~0.5 ~ ~0.5 1.5 2 1.5 0.2 120 force"],
            134: [station_cine.son("chime", 0.75)], 140: [station_cine.son("chime", 1.0)], 146: [station_cine.son("chime", 1.5)],
            150: ['title @a[tag=cine] actionbar {"text":"Plonge jusqu\'au fond de la Cuve : la Cheminée t\'attend","color":"gold"}']}
    for k, y in enumerate(rangs_d):                                                             # la grille monte, une rangee toutes les 8 ticks
        ev_d.setdefault(88 + 8 * k, []).append(f"function atelier:station_vanne_d_rang_{y}")
    scenes["vanne_d"] = {"cles": (cam_d, vise_d), "duree": DUREE_VANNE_D, "centre": txt((int(cxu), a1.SOL, int(czu))), "rayon": 400, "evenements": ev_d}
    # la bonde : la camera, au-dessus de l'eau cote plongeoir, plonge vers le centre du fond ; la grille s'ouvre et le tourbillon se forme, sans texte
    centre_fond = (cxu, a1.FOND_CUVE + 0.5, czu)
    a_b = math.radians(40)
    dep_b = (cxu + 20 * math.sin(a_b), 74.0, czu - 20 * math.cos(a_b))
    pt_b = lambda k: tuple(dep_b[i] + (centre_fond[i] - dep_b[i]) * k for i in range(3))                    # noqa: E731
    surface = (cxu, a1.NIVEAUX["bas"], czu)
    ev_b = {2: ["execute as @a[tag=cine] at @s run playsound minecraft:block.lever.click master @s ~ ~ ~ 1 0.6",
                "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.power_select master @s ~ ~ ~ 1 0.5"],
            40: ["function atelier:station_bonde_ouvrir", f"execute positioned {txt(centre_fond)} run particle minecraft:bubble_column_up ~ ~1 ~ 2 3 2 0.3 200 force"]}
    for t in range(48, DUREE_BONDE - 5, 8):                                                     # le tourbillon : un anneau de remous qui tourne et se resserre
        k = (t - 48) / 8
        r = max(1.5, 6.0 - 0.35 * k)
        a = math.radians(k * 47)
        x, z = surface[0] + r * math.sin(a), surface[2] - r * math.cos(a)
        ev_b.setdefault(t, []).extend([f"particle minecraft:splash {x + monde_dx(txt):.2f} {surface[1] + 1 + monde_dy(txt):.2f} {z + monde_dz(txt):.2f} 1 0.2 1 0.3 40 force",
                                       f"particle minecraft:bubble_pop {surface[0] + monde_dx(txt):.2f} {surface[1] + monde_dy(txt):.2f} {surface[2] + monde_dz(txt):.2f} {r:.1f} 0.3 {r:.1f} 0.05 30 force"])
        if int(k) % 3 == 0:
            ev_b[t].append("execute as @a[tag=cine] at @s run playsound minecraft:block.bubble_column.whirlpool_ambient master @s ~ ~ ~ 1 0.7")
    ev_b.setdefault(DUREE_BONDE - 20, []).append(station_cine.son("chime", 0.6))
    scenes["bonde"] = {"cles": ([(1, dep_b), (70, pt_b(0.45)), (DUREE_BONDE, pt_b(0.58))], [(1, centre_fond), (DUREE_BONDE, centre_fond)]), "duree": DUREE_BONDE,
                       "centre": txt((int(cxu), a1.SOL, int(czu))), "rayon": 400, "evenements": ev_b}
    # une balise sur la vanne D tant que personne n'y est alle
    tick += [f"execute if score #d station matches 1 if score #dvu station matches 0 positioned {txt(d0)} run particle minecraft:end_rod ~0.5 ~1 ~0.5 0.3 5 0.3 0.02 6 force @a[distance=..120]",
             f"execute if score #d station matches 1 if score #dvu station matches 0 positioned {txt(d0)} if entity @a[distance=..5,gamemode=!spectator] run scoreboard players set #dvu station 1"]
    reset.append("scoreboard players set #dvu station 0")

    # ================================================================= la Cheminee
    pousser(D["rampe_boite"], (0, -0.3, 0), "rampe")
    ct = txt(D["centre_tartre"])
    tick.append(f"execute if score #tard station matches 0 positioned {ct} if entity @a[distance=..4,{NAGE}] run function atelier:station_d_tartre")
    f["station_d_tartre"] = "\n".join(["# [claude-build] Le tartre de la Cheminee se brise.", "scoreboard players set #tard station 1", *poser_cases(D["tartre"], "minecraft:water"),
                                       f"execute positioned {ct} run particle minecraft:block{{block_state:{{Name:\"minecraft:dripstone_block\"}}}} ~ ~2 ~ 3 0.5 3 0.1 80",
                                       f"execute positioned {ct} run playsound minecraft:block.dripstone_block.break master @a[distance=..40] ~ ~ ~ 2 0.6"]) + "\n"
    reset += poser_cases(D["tartre"], "minecraft:dripstone_block") + ["scoreboard players set #tard station 0"]
    tc = txt(D["turbine"]["centre"])
    tick += [f"execute if score #tard station matches 1 if score #td station matches ..39 positioned {tc} if entity @a[distance=..4,{NAGE}] run scoreboard players add #td station 1",
             f"execute if score #td station matches 1..39 positioned {tc} unless entity @a[distance=..4,{NAGE}] run scoreboard players remove #td station 1",
             "execute if score #td station matches 40 run function atelier:station_d_turbine",
             f"execute if score #td station matches 1000 positioned {tc} run particle minecraft:bubble_column_up ~ ~ ~ 2 0.4 2 0.2 6"]
    f["station_d_turbine"] = "\n".join(["# [claude-build] La turbine de la Cheminee tourne : un courant ascendant (colonne de bulles) monte jusqu'au bassin du haut.", "scoreboard players set #td station 1000",
                                        *poser_cases(D["sable"], "minecraft:soul_sand"), f"setblock {txt(D['moteur'])} create:creative_motor[facing=up]",
                                        f"execute positioned {tc} run playsound minecraft:block.beacon.activate master @a[distance=..60] ~ ~ ~ 2 0.6"]) + "\n"
    reset += poser_cases(D["sable"], "minecraft:dripstone_block") + [f"setblock {txt(D['moteur'])} create:shaft[axis=y]", "scoreboard players set #td station 0"]
    lev = txt(D["levier"])
    tick.append(f"execute if score #lg station matches 0 if block {lev} minecraft:lever[powered=true] run function atelier:station_levier_general")
    f["station_levier_general"] = "\n".join([
        "# [claude-build] Le levier general : la porte du Poste s'ouvre sur la Coursive ; la bonde de la Cuve s'ouvre en cinematique (ou tout de suite si une autre est en cours).",
        "scoreboard players set #lg station 1", *poser_cases(D["porte_poste"], "minecraft:air"), "scoreboard players operation #libre cine = #t cine",
        "execute if score #libre cine matches 0 run function atelier:station_cine_debut_bonde",
        "execute unless score #libre cine matches 0 run function atelier:station_bonde_ouvrir"]) + "\n"
    f["station_bonde_ouvrir"] = "\n".join([
        "# [claude-build] La bonde s'ouvre : la grille du fond de la Cuve disparait, le puits se remplit, un tourbillon (colonne de bulles descendante) tire vers le Reservoir.",
        *poser_cases(D["bonde"] + D["puits_bonde"], "minecraft:water"), *poser_cases(D["magma"], "minecraft:magma_block"),
        f"playsound minecraft:block.beacon.deactivate master @a {txt((int(cxu), a1.SOL, int(czu)))} 4 0.5",
        f"playsound minecraft:block.bubble_column.whirlpool_inside master @a {txt((int(cxu), a1.NIVEAUX['bas'], int(czu)))} 4 0.6"]) + "\n"
    tick.append(f"execute if score #lg station matches 1 positioned {txt((int(cxu), a1.NIVEAUX['bas'], int(czu)))} run particle minecraft:bubble_column_up ~ ~-4 ~ 4 4 4 0.3 20")
    reset += [f"setblock {lev} minecraft:lever[face=floor,facing=east,powered=false]", *poser_cases(D["porte_poste"], "minecraft:iron_bars[waterlogged=false]"),
              *remettre(D["bonde"] + D["puits_bonde"] + D["magma"]), "scoreboard players set #lg station 0"]

    # ================================================================= le Reservoir, le boss, l'Exutoire
    R = infos["r"]
    rr = int(R["rayon"])
    coin, sel = boite((int(cxu) - rr, int(cxu) + rr, R["y"][0], R["y"][1], int(czu) - rr, int(czu) + rr))
    tick.append(f"execute if score #boss station matches 0 positioned {coin} if entity @a[{sel},gamemode=!spectator] run function atelier:station_boss_debut")
    f["station_boss_debut"] = "\n".join([
        "# [claude-build] Un joueur arrive dans le Reservoir : le Leviathan surgit.", "scoreboard players set #boss station 1", "function atelier:station_boss_apparition",
        'title @a title {"text":"Le Léviathan","color":"dark_aqua"}', "execute unless entity @e[tag=station_boss] run tellraw @a {\"text\":\"(Le Léviathan n'existe pas dans ce jar du grappin : l'Exutoire s'ouvre.)\",\"color\":\"gray\"}"]) + "\n"
    f["station_boss_apparition"] = ("# [claude-build] Le Leviathan (mod grappin). A part : si l'entite manque, seule cette fonction est refusee au chargement.\n"
                                    f"summon donjon:leviathan {txt(R['boss'])} {{Tags:[\"station_boss\",\"station_emb\"],PersistenceRequired:1b}}\n")
    tick.append("execute if score #boss station matches 1 unless entity @e[tag=station_boss] run function atelier:station_boss_vaincu")
    f["station_boss_vaincu"] = "\n".join([
        "# [claude-build] Le Leviathan est vaincu : l'Exutoire s'ouvre, un courant ascendant ramene a la surface.", "scoreboard players set #boss station 2",
        *poser_cases(R["exutoire"] + R["grille_puits"], "minecraft:water"), *poser_cases(R["sable"], "minecraft:soul_sand"),
        'title @a title {"text":"Le Léviathan est vaincu","color":"gold"}', 'title @a subtitle {"text":"L\'Exutoire est ouvert, au sud","color":"gray"}']) + "\n"
    lac = R["lac"]
    tick.append(f"execute if score #boss station matches 2 positioned {txt(lac)} if entity @a[distance=..8] run function atelier:station_victoire")
    f["station_victoire"] = "\n".join(["# [claude-build] Retour a la lumiere.", "scoreboard players set #boss station 3", 'title @a title {"text":"La Station est vaincue","color":"gold"}',
                                       f"execute positioned {txt(lac)} run particle minecraft:firework ~ ~2 ~ 3 2 3 0.1 120", f"playsound minecraft:ui.toast.challenge_complete master @a {txt(lac)} 2 1"]) + "\n"
    reset += ["kill @e[tag=station_boss]", *poser_cases(R["exutoire"] + R["grille_puits"], "minecraft:iron_bars[waterlogged=true]"), *remettre(R["sable"]), "scoreboard players set #boss station 0"]
    for drapeau in ("#a3", "#a4", "#b2", "#b3", "#rotor"):
        reset.append(f"scoreboard players set {drapeau} station 0")
    return f, tick, reset, scenes


VARIABLES = ("#niv", "#pompes", "#pA", "#pB", "#pC", "#a3", "#a4", "#b2", "#b3", "#rotor", "#pl1", "#pl2", "#pl3", "#vap",
             "#cpA", "#cpB", "#cpC", "#t1", "#t2", "#t3", "#tar1", "#tar2", "#d", "#dvu", "#tard", "#td", "#lg", "#boss")

