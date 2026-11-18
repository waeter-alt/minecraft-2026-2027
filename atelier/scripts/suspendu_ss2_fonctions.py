# [claude-build]
"""suspendu_ss2_fonctions.py - Donjon Suspendu, salle -2 : les fonctions de datapack de l'enigme des leviers, des ennemis et de la cle.
`fonctions(infos, txt, cle_item)` renvoie (fonctions {nom: texte}, lignes ajoutees a suspendu_tick, lignes ajoutees a suspendu_reset, scenes sur mesure pour suspendu_cine.fonctions).
Les leviers : tirer le levier k fait apparaitre ses ancrages cyan (un bloc d'ancrage et sa chaine, avec un pilier s'il est au dessus du vide) ; ils restent jusqu'a la remise a zero.
Chaque levier lance une cinematique (demande de Luca) : la camera s'approche de l'ancrage, le pilier monte du fond de la fosse rang par rang (ou la chaine descend du plafond), puis l'ancrage apparait.
Le levier 5 fait en plus apparaitre le coffre de la cle de la derniere porte, au fond de la salle : une cinematique survole la salle, les ancrages apparaissent un a un, puis le coffre (le meme enchainement,
sans camera, si une autre cinematique est deja en cours). Les ennemis apparaissent quand un joueur s'approche de leur ile."""
import math

import suspendu_cine as cine

RAYON_ILE = 36                       # un joueur a moins de 36 blocs du centre d'une ile reveille ses ennemis (18 quand la salle etait deux fois plus petite)
MOBS = ("zombie", "skeleton")        # en alternance : un zombie, un squelette, un zombie...
DUREE_CLE = 210                      # ticks de la cinematique de la cle (10,5 s) : un long vol au dessus de la salle, plus long que les 110 ticks des autres scenes
ECHELLES_CLE = [(1, 1.0), (60, 0.8), (130, 0.4), (180, 0.22), (DUREE_CLE, 0.17)]      # de combien la camera est eloignee du coffre a chaque cle (1 = au depart) : elle accelere, puis ralentit vers le coffre
T_PREMIER_ANCRAGE, PAS_ANCRAGES, T_COFFRE = 8, 28, 150                                # ticks de la scene : le premier ancrage apparait, un de plus tous les 28 ticks, puis le coffre
DEPART_CLE = 95.0                    # la camera part a 95 blocs a l'ouest du coffre, au dessus du vide, au bord est de la grotte du levier 5
HAUT_CAMERA_CLE = 6.5                # et vole a 6,5 blocs au dessus du sol des iles : sous la pointe des stalactites et au dessus des ancrages
RAYON_CLE = 45                       # les joueurs a moins de 45 blocs du levier 5 (dans la grotte) voient la scene
DUREE_LEVIER = 110                   # les cinematiques des leviers 1 a 4 (5,5 s)
T_MONTEE, DUREE_MONTEE = 12, 40      # le pilier monte du tick 12 au tick 52 ; la chaine d'un ancrage sans pilier descend pendant la fin de ce temps
T_ANCRAGE = 56                       # puis l'ancrage apparait (le suivant 6 ticks plus tard)
DIST_CAM_LEVIER = 24.0               # la camera part a 24 blocs de l'ancrage (a l'horizontale), 6 blocs au dessus du sol des iles, et s'en approche jusqu'a 45 % de cette distance
ECHELLES_LEVIER = [(1, 1.0), (40, 0.78), (80, 0.55), (DUREE_LEVIER, 0.45)]


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
    lignes += [f"fill {txt((x, y + 1, z))} {txt((x, plafond, z))} minecraft:chain[axis=y]", f"setblock {txt((x, y, z))} donjon:ancrage",
               f"execute positioned {txt((x, y, z))} run particle minecraft:end_rod ~ ~ ~ 0.3 0.6 0.3 0.05 30",
               f"execute positioned {txt((x, y, z))} run playsound minecraft:block.chain.place master @a[distance=..60] ~ ~ ~ 2 0.8"]
    return lignes


def txt_decal(txt):
    """Le decalage du gabarit vers le monde, retrouve a partir de `txt` (qui convertit une case du gabarit en coordonnees monde)."""
    return tuple(int(v) for v in txt((0, 0, 0)).split())


def etapes_pilier(p, txt, yf2, y_fond, t0, duree):
    """Le pilier qui monte : {tick: [commandes]}, ses rangs poses du fond de la fosse vers le haut entre t0 et t0 + duree (bandes sculptees comme lignes_pilier), un grondement et de la fumee a son
    sommet. La fonction de l'ancrage (lignes_pilier + lignes_ancrage), appelee ensuite, repose le tout a l'identique, plus le dessus, la lumiere, la chaine et l'ancrage."""
    rangees = list(segments(p["cases"]))
    ys = list(range(y_fond + 2, yf2))
    bandes = set(range(yf2 - 4, y_fond + 1, -4))
    ox, oy, oz = txt_decal(txt)
    cx = sum(c[0] for c in p["cases"]) / len(p["cases"]) + 0.5 + ox
    cz = sum(c[1] for c in p["cases"]) / len(p["cases"]) + 0.5 + oz
    ev = {}
    for i, y in enumerate(ys):
        tick = t0 + (i * duree) // len(ys)
        bloc = "minecraft:chiseled_stone_bricks" if y in bandes else "minecraft:stone_bricks"
        ev.setdefault(tick, []).extend(f"fill {txt((a, y, z))} {txt((b, y, z))} {bloc}" for (a, b, z) in rangees)
    for tick in range(t0, t0 + duree, 3):
        y = ys[min(len(ys) - 1, (tick - t0) * len(ys) // duree)]
        ev.setdefault(tick, []).extend([f"particle minecraft:campfire_cosy_smoke {cine._f(cx)} {cine._f(y + 1 + oy)} {cine._f(cz)} 1.6 0.3 1.6 0.02 10 force",       # (« cosy » : un nom de particule faux fait rejeter toute la fonction par le jeu)
                                        f"particle minecraft:dust_plume {cine._f(cx)} {cine._f(y + 1 + oy)} {cine._f(cz)} 1.8 0.2 1.8 0.05 25 force"])
    for tick in range(t0, t0 + duree, 6):
        ev.setdefault(tick, []).append("execute as @a[tag=cine] at @s run playsound minecraft:block.deepslate.place master @s ~ ~ ~ 1 0.5")
    ev.setdefault(t0, []).append("execute as @a[tag=cine] at @s run playsound minecraft:block.piston.extend master @s ~ ~ ~ 0.8 0.5")
    return ev


def etapes_chaine(a, txt, plafond, t0, duree):
    """La chaine d'un ancrage sans pilier descend du plafond, un maillon a la fois : {tick: [commandes]}."""
    x, y, z = a
    maillons = list(range(plafond, y, -1))
    ev = {}
    for i, yy in enumerate(maillons):
        tick = t0 + (i * duree) // len(maillons)
        ev.setdefault(tick, []).extend([f"setblock {txt((x, yy, z))} minecraft:chain[axis=y]", "execute as @a[tag=cine] at @s run playsound minecraft:block.chain.step master @s ~ ~ ~ 1 0.7"])
    return ev


def _libre(infos, p):
    """Le point p (gabarit) est-il dans l'air de la salle -2 (d'apres les cases : sol et plafond de chaque colonne) ?"""
    v = infos["cases"].get((math.floor(p[0]), math.floor(p[2])))
    return v is not None and v[2] != "massif" and v[0] + 1 <= p[1] <= v[1] + 0.6


def _ligne_libre(infos, a, b):
    n = max(2, int(math.dist(a, b) / 0.4))
    return all(_libre(infos, tuple(a[k] + (b[k] - a[k]) * i / n for k in range(3))) for i in range(n + 1))


def scene_levier(infos, k):
    """La camera d'un levier : un travelling avant vers son premier ancrage au dessus du vide (son pilier), sinon vers le premier, depuis le cote du levier si l'air le permet (sinon un peu a cote) ;
    elle ne tourne jamais. Le point vise est bas pour un pilier (on le voit monter de la fosse), a hauteur de l'ancrage sinon."""
    piliers = [tuple(p["ancrage"]) for p in infos["piliers_cyan"].get(k, [])]
    ax, ay, az = piliers[0] if piliers else tuple(infos["ancrages_cyan"][k][0])
    yf2 = infos["yf2"]
    cible = (ax + 0.5, yf2 - 10.0 if piliers else ay + 1.5, az + 0.5)
    lx, _, lz = infos["leviers"][k]
    base = math.atan2(lx + 0.5 - cible[0], lz + 0.5 - cible[2])
    ancre = (ax + 0.5, ay + 0.5, az + 0.5)
    for ecart in sorted(range(-180, 181, 10), key=abs):
        ang = base + math.radians(ecart)
        depart = (cible[0] + DIST_CAM_LEVIER * math.sin(ang), yf2 + 6.0, cible[2] + DIST_CAM_LEVIER * math.cos(ang))
        s = ECHELLES_LEVIER[-1][1]
        fin = tuple(cible[i] + (depart[i] - cible[i]) * s for i in range(3))
        if _ligne_libre(infos, depart, fin) and _ligne_libre(infos, depart, ancre) and _ligne_libre(infos, fin, ancre):
            return cine.travelling(cible, depart, ECHELLES_LEVIER)
    raise ValueError(f"levier {k} : pas de place pour la camera")


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
    perso_leviers = {}
    for k in sorted(levier):
        pos = txt(levier[k])
        tick.append(f"execute if score #l{k} suspendu matches 0 if block {pos} minecraft:lever[powered=true] run function atelier:suspendu_ss2_levier{k}")
        piliers = {tuple(p["ancrage"]): p for p in infos["piliers_cyan"].get(k, [])}             # les ancrages au dessus du vide arrivent avec leur pilier
        ancrages = [tuple(a) for a in infos["ancrages_cyan"][k]]
        son_levier = [f"execute positioned {pos} run playsound minecraft:block.beacon.activate master @a[distance=..40] ~ ~ ~ 1 1.4"]
        if k != 5:
            # une cinematique : la camera s'approche, le pilier monte (ou la chaine descend), puis l'ancrage ; tout tout de suite si une autre cinematique est deja en cours
            f[f"suspendu_ss2_levier{k}"] = "\n".join([
                f"# [claude-build] Le levier {k} est tire : une cinematique montre son pilier qui monte de la fosse (ou sa chaine qui descend du plafond) puis son ancrage (ou tout apparait tout de suite si une autre cinematique est en cours).",
                f"scoreboard players set #l{k} suspendu 1", *son_levier,
                "scoreboard players operation #libre cine = #t cine",
                f"execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_levier{k}",
                f"execute unless score #libre cine matches 0 run function atelier:suspendu_ss2_l{k}_tout"]) + "\n"
            for i, a in enumerate(ancrages, start=1):
                f[f"suspendu_ss2_l{k}_a{i}"] = "\n".join([f"# [claude-build] Levier {k} : l'ancrage {i} sur {len(ancrages)} apparait (avec son pilier s'il est au dessus du vide)."] + lignes_ancrage(a, piliers.get(a), infos, txt)) + "\n"
            message = f"execute positioned {pos} run title @a[distance=..40] actionbar " + '{"text":"Levier ' + str(k) + ' : un point d\'ancrage est apparu","color":"aqua"}'
            f[f"suspendu_ss2_l{k}_tout"] = "\n".join([f"# [claude-build] Levier {k} : tous ses ancrages apparaissent tout de suite (sans camera).",
                                                    *[f"function atelier:suspendu_ss2_l{k}_a{i}" for i in range(1, len(ancrages) + 1)], message]) + "\n"
            annonce = "Un pilier sort de l'abîme…" if piliers else "Une chaîne descend de la voûte…"
            ev = {2: ['title @a[tag=cine] actionbar {"text":"' + annonce + '","color":"gray"}']}
            for i, a in enumerate(ancrages, start=1):
                decal = 6 * (i - 1)
                if a in piliers:
                    etapes = etapes_pilier(piliers[a], txt, yf2, y_fond, T_MONTEE + decal, DUREE_MONTEE)
                else:
                    etapes = etapes_chaine(a, txt, plafond, T_MONTEE + 16 + decal, DUREE_MONTEE - 16)
                for tk, cmds in etapes.items():
                    ev.setdefault(tk, []).extend(cmds)
                ev.setdefault(T_ANCRAGE + decal, []).extend([f"function atelier:suspendu_ss2_l{k}_a{i}", "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1.4"])
            fin = T_ANCRAGE + 6 * (len(ancrages) - 1)
            ev.setdefault(fin + 8, []).append(cine.son("chime", 1.0))
            ev.setdefault(fin + 12, []).append(cine.son("chime", 1.26))
            ev.setdefault(fin + 16, []).extend([cine.son("chime", 1.5), 'title @a[tag=cine] actionbar {"text":"Levier ' + str(k) + ' : un point d\'ancrage est apparu","color":"aqua"}'])
            perso_leviers[f"levier{k}"] = {"scene": scene_levier(infos, k), "duree": DUREE_LEVIER, "centre": pos, "rayon": RAYON_CLE, "evenements": ev}
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
    piliers5 = {tuple(p["ancrage"]): p for p in infos["piliers_cyan"].get(5, [])}
    for i, a in enumerate(ancrages5, start=1):                                                    # chaque pilier monte de la fosse en 20 ticks, puis son ancrage apparait ; le dernier (sans pilier) : sa chaine descend
        t_i = T_PREMIER_ANCRAGE + PAS_ANCRAGES * (i - 1)
        etapes = etapes_pilier(piliers5[tuple(a)], txt, yf2, y_fond, t_i, 20) if tuple(a) in piliers5 else etapes_chaine(tuple(a), txt, plafond, t_i + 4, 16)
        for tk, cmds in etapes.items():
            evenements.setdefault(tk, []).extend(cmds)
        evenements.setdefault(t_i + 22, []).append(f"function atelier:suspendu_ss2_l5_a{i}")
    evenements[T_COFFRE] = ["function atelier:suspendu_ss2_cle_coffre", "execute as @a[tag=cine] at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1.2"]
    evenements[T_COFFRE + 9] = [cine.son("chime", 0.75)]
    evenements[T_COFFRE + 13] = [cine.son("chime", 0.94)]
    evenements[T_COFFRE + 17] = [cine.son("chime", 1.12)]
    evenements[T_COFFRE + 22] = [cine.son("chime", 1.5), "execute as @a[tag=cine] at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.6 1"]
    perso = {"cle": {"scene": scene_cle(infos), "duree": DUREE_CLE, "centre": txt(levier[5]), "rayon": RAYON_CLE, "evenements": evenements}, **perso_leviers}
    return f, tick, reset, perso
