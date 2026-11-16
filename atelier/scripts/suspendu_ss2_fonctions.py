# [claude-build]
"""suspendu_ss2_fonctions.py - Donjon Suspendu, salle -2 : les fonctions de datapack de l'enigme des leviers, des ennemis et de la cle.
`fonctions(infos, txt, cle_item)` renvoie (fonctions {nom: texte}, lignes ajoutees a suspendu_tick, lignes ajoutees a suspendu_reset, scenes sur mesure pour suspendu_cine.fonctions).
Les leviers : tirer le levier k fait apparaitre ses ancrages cyan (un bloc d'ancrage et sa chaine, avec un pilier s'il est au dessus du vide) ; ils restent jusqu'a la remise a zero.
Le levier 5 fait en plus apparaitre le coffre de la cle de la derniere porte, au fond de la salle : une cinematique survole la salle, les ancrages apparaissent un a un, puis le coffre (le meme enchainement,
sans camera, si une autre cinematique est deja en cours). Les ennemis apparaissent quand un joueur s'approche de leur ile."""
import suspendu_cine as cine

RAYON_ILE = 36                       # un joueur a moins de 36 blocs du centre d'une ile reveille ses ennemis (18 quand la salle etait deux fois plus petite)
MOBS = ("zombie", "skeleton")        # en alternance : un zombie, un squelette, un zombie...
DUREE_CLE = 210                      # ticks de la cinematique de la cle (10,5 s) : un long vol au dessus de la salle, plus long que les 110 ticks des autres scenes
ECHELLES_CLE = [(1, 1.0), (60, 0.8), (130, 0.4), (180, 0.22), (DUREE_CLE, 0.17)]      # de combien la camera est eloignee du coffre a chaque cle (1 = au depart) : elle accelere, puis ralentit vers le coffre
T_PREMIER_ANCRAGE, PAS_ANCRAGES, T_COFFRE = 8, 28, 150                                # ticks de la scene : le premier ancrage apparait, un de plus tous les 28 ticks, puis le coffre
DEPART_CLE = 95.0                    # la camera part a 95 blocs a l'ouest du coffre, au dessus du vide, au bord est de la grotte du levier 5
HAUT_CAMERA_CLE = 6.5                # et vole a 6,5 blocs au dessus du sol des iles : sous la pointe des stalactites et au dessus des ancrages
RAYON_CLE = 45                       # les joueurs a moins de 45 blocs du levier 5 (dans la grotte) voient la scene


def segments(cases):
    """Les cases (x, z) regroupees en rangees d'un seul tenant : (x debut, x fin, z), pour qu'un `fill` couvre toute une rangee."""
    par_z = {}
    for (x, z) in cases:
        par_z.setdefault(z, []).append(x)
    for z, xs in sorted(par_z.items()):
        xs.sort()
        debut = precedent = xs[0]
        for x in xs[1:]:
            if x != precedent + 1:
                yield debut, precedent, z
                debut = x
            precedent = x
        yield debut, precedent, z


def lignes_pilier(p, txt, yf2, y_fond, niveau):
    """Les commandes qui font apparaitre un pilier (du banc de roche au fond de la fosse au niveau des iles) : le corps en briques de pierre, une bande sculptee tous les 4 blocs, le dessus en roche, une lumiere invisible."""
    rangees = list(segments(p["cases"]))
    lignes = [f"fill {txt((a, y_fond + 2, z))} {txt((b, yf2 - 1, z))} minecraft:stone_bricks" for (a, b, z) in rangees]
    y = yf2 - 4
    while y > y_fond + 1:
        lignes += [f"fill {txt((a, y, z))} {txt((b, y, z))} minecraft:chiseled_stone_bricks" for (a, b, z) in rangees]
        y -= 4
    lignes += [f"setblock {txt((x, yf2, z))} {nom}" for (x, z, nom) in p["dessus"]]
    lignes.append(f"setblock {txt(p['lumiere'])} minecraft:light[level={niveau}]")
    return lignes


def lignes_ancrage(a, pilier, infos, txt):
    """Les commandes qui font apparaitre un ancrage cyan a : son pilier s'il est au dessus du vide, sa chaine (du plafond), le bloc d'ancrage, des particules et un son."""
    x, y, z = a
    yf2, y_fond, niveau, plafond = infos["yf2"], infos["y_fond"], infos["lum_niveau"], infos["plafond"]
    lignes = []
    if pilier is not None:
        lignes += lignes_pilier(pilier, txt, yf2, y_fond, niveau)
        lignes.append(f"execute positioned {txt((x, yf2 + 1, z))} run particle minecraft:poof ~ ~ ~ 1.2 0.2 1.2 0.05 40")
        lignes.append(f"execute positioned {txt((x, yf2, z))} run playsound minecraft:block.stone.place master @a[distance=..60] ~ ~ ~ 3 0.6")
    lignes += [f"fill {txt((x, y + 1, z))} {txt((x, plafond, z))} minecraft:chain[axis=y]", f"setblock {txt((x, y, z))} grappin:ancrage",
               f"execute positioned {txt((x, y, z))} run particle minecraft:end_rod ~ ~ ~ 0.3 0.6 0.3 0.05 30",
               f"execute positioned {txt((x, y, z))} run playsound minecraft:block.chain.place master @a[distance=..60] ~ ~ ~ 2 0.8"]
    return lignes


def scene_cle(infos):
    """La camera de la cle : un long travelling avant, en ligne droite vers le coffre, depuis le bord est de la grotte du levier 5 ; elle ne tourne jamais (voir suspendu_cine.travelling)."""
    cx, cy, cz = infos["coffre_cle"]
    cible = (cx + 0.5, cy + 2.0, cz + 0.5)                                    # un peu au dessus du coffre : le rayon de lumiere est dans le cadre
    depart = (cible[0] - DEPART_CLE, infos["yf2"] + HAUT_CAMERA_CLE, cible[2] + 6.0)
    dx, dy, dz = depart[0] - cible[0], depart[1] - cible[1], depart[2] - cible[2]
    cam = [(t, (cible[0] + dx * s, cible[1] + dy * s, cible[2] + dz * s)) for t, s in ECHELLES_CLE]
    return cam, [(1, cible), (DUREE_CLE, cible)]


def fonctions(infos, txt, cle_item):
    f, tick, reset = {}, [], []
    levier = {k: v for k, v in infos["leviers"].items()}
    plafond = infos["plafond"]
    yf2, y_fond = infos["yf2"], infos["y_fond"]
    coffre = txt(infos["coffre_cle"])
    for k in sorted(levier):
        pos = txt(levier[k])
        tick.append(f"execute if score #l{k} suspendu matches 0 if block {pos} minecraft:lever[powered=true] run function atelier:suspendu_ss2_levier{k}")
        piliers = {tuple(p["ancrage"]): p for p in infos["piliers_cyan"].get(k, [])}             # les ancrages au dessus du vide arrivent avec leur pilier
        ancrages = [tuple(a) for a in infos["ancrages_cyan"][k]]
        son_levier = [f"execute positioned {pos} run playsound minecraft:block.beacon.activate master @a[distance=..40] ~ ~ ~ 1 1.4"]
        if k != 5:
            lignes = [f"# [claude-build] Le levier {k} est tire : ses ancrages apparaissent (un bloc d'ancrage et sa chaine, du plafond), avec un son et des particules.", f"scoreboard players set #l{k} suspendu 1"]
            for a in ancrages:
                lignes += lignes_ancrage(a, piliers.get(a), infos, txt)
            lignes += son_levier + [f"execute positioned {pos} run title @a[distance=..40] actionbar {{\"text\":\"Levier {k} : un point d'ancrage est apparu\",\"color\":\"aqua\"}}"]
            f[f"suspendu_ss2_levier{k}"] = "\n".join(lignes) + "\n"
        else:
            # le levier 5 : une cinematique survole la salle, les ancrages apparaissent un a un, puis le coffre de la cle ; sans camera si une autre cinematique est deja en cours
            f["suspendu_ss2_levier5"] = "\n".join([
                "# [claude-build] Le levier 5 est tire : une cinematique montre les ancrages qui apparaissent un a un vers l'ile de la cle, puis le coffre de la cle de la derniere porte (ou tout apparait tout de suite si une autre cinematique est en cours).",
                "scoreboard players set #l5 suspendu 1", *son_levier,
                "scoreboard players operation #libre cine = #t cine",          # on retient si une scene etait deja en cours AVANT de lancer la notre (lancer la notre met #t a 1)
                "execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_cle",
                "execute unless score #libre cine matches 0 run function atelier:suspendu_ss2_cle_tout"]) + "\n"
            for i, a in enumerate(ancrages, start=1):
                f[f"suspendu_ss2_l5_a{i}"] = "\n".join([f"# [claude-build] Levier 5 : l'ancrage {i} sur {len(ancrages)} apparait (avec son pilier s'il est au dessus du vide)."] + lignes_ancrage(a, piliers.get(a), infos, txt)) + "\n"
            f["suspendu_ss2_cle_tout"] = "\n".join(["# [claude-build] Tout apparait tout de suite (une autre cinematique est en cours) : les ancrages du levier 5, puis le coffre de la cle.",
                                                    *[f"function atelier:suspendu_ss2_l5_a{i}" for i in range(1, len(ancrages) + 1)], "function atelier:suspendu_ss2_cle_coffre"]) + "\n"
            f["suspendu_ss2_cle_coffre"] = "\n".join([
                "# [claude-build] Le coffre de la cle de la derniere porte apparait (au milieu de la cinematique, ou tout de suite) ; on repart d'une case vide, setblock viderait un coffre deja la.",
                f"setblock {coffre} minecraft:air", f"setblock {coffre} minecraft:chest[facing=north]{{Items:[{{Slot:13b,id:\"minecraft:tripwire_hook\",count:1,components:{cle_item}}}]}}",
                f"execute positioned {coffre} run particle minecraft:happy_villager ~ ~0.5 ~ 0.4 0.4 0.4 0.1 30",
                f"execute positioned {coffre} run particle minecraft:end_rod ~ ~1 ~ 0.3 0.6 0.3 0.05 25",
                f"execute positioned {coffre} run particle minecraft:end_rod ~ ~4 ~ 0.12 4 0.12 0.01 120 force",                  # un rayon de lumiere, visible de loin
                f"execute positioned {coffre} run playsound minecraft:block.chest.open master @a[distance=..64] ~ ~ ~ 2 1",
                f"execute positioned {coffre} run title @a[distance=..64] title {{\"text\":\"Un coffre est apparu au fond de la salle\",\"color\":\"gold\"}}"]) + "\n"
        reset += [f"scoreboard players set #l{k} suspendu 0", f"setblock {pos} minecraft:lever[face=floor,facing=north,powered=false]"]
        for (x, y, z) in ancrages:
            reset += [f"fill {txt((x, y, z))} {txt((x, plafond, z))} minecraft:air"]
        for p in infos["piliers_cyan"].get(k, []):                                                 # le pilier disparait : l'abime redevient vide (seulement ses cases, jamais celles d'une ile)
            reset += [f"fill {txt((a, y_fond + 2, z))} {txt((b, yf2, z))} minecraft:air" for (a, b, z) in segments(p["cases"])]
            reset.append(f"setblock {txt(p['lumiere'])} minecraft:air")
    for nom, liste in infos["ennemis"].items():
        if nom in infos["iles"] and infos["iles"][nom].get("centre"):
            centre = txt(infos["iles"][nom]["centre"])
        else:
            centre = txt(liste[0])
        fonction = f"suspendu_ss2_ennemis_{nom.lower()}"                      # un identifiant de fonction est en minuscules (« E » ou « PONT » seraient refuses par le jeu)
        tick.append(f"execute if score #e_{nom} suspendu matches 0 positioned {centre} if entity @a[gamemode=!spectator,distance=..{RAYON_ILE}] run function atelier:{fonction}")
        lignes = [f"# [claude-build] Un joueur s'approche de l'ile {nom} : ses {len(liste)} ennemi(s) apparaissent (une seule fois).", f"scoreboard players set #e_{nom} suspendu 1"]
        for i, pos in enumerate(liste):
            lignes.append(f"summon minecraft:{MOBS[i % 2]} {txt(pos)} {{Tags:[\"suspendu_ss2e\"],PersistenceRequired:1b}}")
        f[fonction] = "\n".join(lignes) + "\n"
        reset.append(f"scoreboard players set #e_{nom} suspendu 0")
    reset += ["kill @e[tag=suspendu_ss2e]", f"setblock {coffre} minecraft:air"]               # le coffre de la cle n'existe pas avant le levier 5
    # la cinematique de la cle : les ancrages un a un, puis le coffre avec ses carillons
    ancrages5 = infos["ancrages_cyan"][5]
    evenements = {2: ['title @a[tag=cine] actionbar {"text":"…","color":"gray"}']}
    for i in range(1, len(ancrages5) + 1):
        evenements[T_PREMIER_ANCRAGE + PAS_ANCRAGES * (i - 1)] = [f"function atelier:suspendu_ss2_l5_a{i}"]
    evenements[T_COFFRE] = ["function atelier:suspendu_ss2_cle_coffre", "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1.2"]
    evenements[T_COFFRE + 9] = [cine.son("chime", 0.75)]
    evenements[T_COFFRE + 13] = [cine.son("chime", 0.94)]
    evenements[T_COFFRE + 17] = [cine.son("chime", 1.12)]
    evenements[T_COFFRE + 22] = [cine.son("chime", 1.5), "execute as @a[tag=cine] at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.6 1"]
    perso = {"cle": {"scene": scene_cle(infos), "duree": DUREE_CLE, "centre": txt(levier[5]), "rayon": RAYON_CLE, "evenements": evenements}}
    return f, tick, reset, perso
