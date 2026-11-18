# [claude-build]
"""suspendu_ss3_fonctions.py - Donjon Suspendu, sous-sol 3 : les fonctions de datapack du combat contre le Gardien des Racines, reprises de l'ancien donjon 1 (donjon_temple.py, branche feature/boss-golem).
`fonctions(infos, txt)` renvoie (fonctions {nom: texte}, lignes ajoutees a suspendu_tick, lignes ajoutees a suspendu_reset).
Les trois fosses du tunnel (avant le gouffre) ont chacune sa fonction de retour au bord (suspendu_ss3_fosse_N).
Quand un joueur arrive sur la tete du pont (le bout du pont en ruine, ou commence la rampe) : annonce, puis 5 s apres le Gardien surgit de la lave au centre de la salle (une seule fois). Le mod appelle le tag de
fonctions donjon:golem_mort a sa mort : la sortie, scellee par des barreaux, s'ouvre. Un joueur qui tombe dans le gouffre (son crochet decroche, chute lente, pas de degats) revient au depart du pont s'il est tombe du pont,
en tete de pont s'il est tombe de la rampe."""

DELAI_BOSS = 100                       # 5 s entre l'annonce et l'apparition du golem
TAG_MORT = "golem_mort"                # le tag de fonctions que le mod appelle a la mort du golem (data/donjon/tags/function/golem_mort.json)


def fonctions(infos, txt):
    f, tick, reset = {}, [], []
    gx, gy, gz = infos["golem"]["centre"]
    bx, by, bz = (int(v) for v in txt((gx, gy, gz)).split())
    arene = f"execute positioned {bx} {by} {bz} run"
    x0, x1, z0, z1 = infos["plateformes"]["D"]["bbox"]
    top = infos["plateformes"]["D"]["dessus"]
    (zx, zy, zz), (dx, dy, dz) = infos["zone_chute"]
    sx, sy, sz = infos["depart_boss"]
    sortie = infos["sortie"]
    a = (min(c[0] for c in sortie), min(c[1] for c in sortie), min(c[2] for c in sortie))
    b = (max(c[0] for c in sortie), max(c[1] for c in sortie), max(c[2] for c in sortie))
    zone = f"dx={dx},dy={dy},dz={dz}"
    tick += [f"execute if score #boss suspendu matches 0 positioned {txt((x0, top + 1, z0))} if entity @a[gamemode=!spectator,dx={x1 - x0},dy=5,dz={z1 - z0}] run function atelier:suspendu_ss3_annonce",
             f"execute positioned {txt((zx, zy, zz))} as @a[{zone}] run function atelier:suspendu_ss3_chute",
             f"execute positioned {txt((zx, zy, zz))} run kill @e[type=item,{zone}]"]
    f["suspendu_ss3_annonce"] = "\n".join([
        "# [claude-build] Un joueur est arrive sur la plateforme de depart de la salle du boss : annonce, puis le Gardien des Racines apparait apres 5 s (une seule fois).",
        "scoreboard players set #boss suspendu 1",
        f"{arene} title @a[distance=..90] times 10 60 20",
        f'{arene} title @a[distance=..90] subtitle {{"text":"Tenez-vous prets","color":"gray"}}',
        f'{arene} title @a[distance=..90] title {{"text":"Le Gardien se reveille...","color":"gold"}}',
        f"{arene} playsound minecraft:block.beacon.power_select master @a[distance=..90] ~ ~ ~ 3 0.6",
        f"schedule function atelier:suspendu_ss3_invoque {DELAI_BOSS}t"]) + "\n"
    # positioned en premier : une fonction programmee s'execute a l'origine du monde, pas dans la salle ; Rotation 180 : le golem regarde vers la plateforme de depart (au nord de lui)
    f["suspendu_ss3_invoque"] = "\n".join([
        "# [claude-build] Fait surgir le Gardien des Racines de la lave, au centre de la salle (jamais deux a la fois).",
        f"execute positioned {bx} {by} {bz} unless entity @e[type=donjon:golem,distance=..150] run summon donjon:golem {bx + 0.5} {by} {bz + 0.5} {{Rotation:[180f,0f]}}",
        f"execute positioned {bx} {by} {bz} if entity @e[type=donjon:golem,distance=..150] run scoreboard players set #boss suspendu 2",
        f"execute positioned {bx} {by} {bz} unless entity @e[type=donjon:golem,distance=..150] run scoreboard players set #boss suspendu 0",
        f"{arene} playsound minecraft:entity.ender_dragon.growl master @a[distance=..90] ~ ~ ~ 5 0.6"]) + "\n"
    f["suspendu_ss3_mort"] = ("# [claude-build] Appelee par le mod (tag donjon:golem_mort) a la mort du golem : la sortie ne s'ouvre que si le boss a bien ete invoque (pas lors d'une remise a zero).\n"
                              "execute if score #boss suspendu matches 2 run function atelier:suspendu_ss3_vaincu\n")
    f["suspendu_ss3_vaincu"] = "\n".join([
        "# [claude-build] Le Gardien des Racines est mort : la sortie de la salle du boss s'ouvre.",
        "scoreboard players set #boss suspendu 3",
        "function atelier:suspendu_ss3_ouvrir_sortie",
        f'{arene} title @a[distance=..90] title {{"text":"Le Gardien est vaincu","color":"green"}}']) + "\n"
    f["suspendu_ss3_ouvrir_sortie"] = (f"# [claude-build] Ouvre la sortie du boss (a la mort du boss).\nfill {txt(a)} {txt(b)} minecraft:air\n"
                                       f"playsound minecraft:block.iron_door.open master @a {txt(a)} 2 0.6\n")
    f["suspendu_ss3_fermer_sortie"] = f"# [claude-build] Referme la sortie du boss (remise a zero).\nfill {txt(a)} {txt(b)} {infos['sortie_bloc']}[waterlogged=false]\n"
    f["suspendu_ss3_decrocher"] = ("# [claude-build] Supprime le crochet du grappin du joueur dont l'identifiant est passe en parametre.\n"
                                   "$kill @e[type=donjon:crochet,nbt={Owner:$(uuid)}]\n")
    px, py, pz = (float(v) for v in txt((sx, sy, sz)).split())
    rx, ry, rz = (float(v) for v in txt(infos["retour_rampe"]).split())
    dz_pont = infos["chute_pont_dz"]
    f["suspendu_ss3_chute"] = "\n".join([
        "# [claude-build] Un joueur est tombe dans le gouffre du boss : on decroche son grappin, chute lente (pas de degats). Tombe du pont (en amont de la rampe), il revient au depart du pont (a refranchir au grappin) ; tombe de la rampe, il revient en tete de pont.",
        "data modify storage atelier:suspendu chute.uuid set from entity @s UUID",
        "function atelier:suspendu_ss3_decrocher with storage atelier:suspendu chute",
        "effect give @s minecraft:slow_falling 3 0 true",
        f"execute positioned {txt((zx, zy, zz))} if entity @s[dx={dx},dy={dy},dz={dz_pont}] run tag @s add chute_pont",
        f"execute if entity @s[tag=chute_pont] run tp @s {px + 0.5} {py} {pz + 0.5} 0 0",
        f"execute unless entity @s[tag=chute_pont] run tp @s {rx + 0.5} {ry} {rz + 0.5} 0 0",
        "tag @s remove chute_pont",
        'title @s actionbar {"text":"Tu es tombé !","color":"red"}']) + "\n"
    for k, fo in enumerate(infos["tunnel"]["fosses"], 1):                                   # les fosses du tunnel : on tombe, on revient au bord (ou sur l'ilot) : crochet decroche, chute lente, pas de degats
        (fx, fy, fz), (fdx, fdy, fdz) = fo["zone"]
        tx, ty, tz = (float(v) for v in txt(fo["retour"]).split())
        tick.append(f"execute positioned {txt((fx, fy, fz))} as @a[dx={fdx},dy={fdy},dz={fdz}] run function atelier:suspendu_ss3_fosse_{k}")
        f[f"suspendu_ss3_fosse_{k}"] = "\n".join([
            "# [claude-build] Un joueur est tombe dans une fosse du tunnel : on decroche son grappin, chute lente (pas de degats), retour au bord d'ou il est venu.",
            "data modify storage atelier:suspendu chute.uuid set from entity @s UUID",
            "function atelier:suspendu_ss3_decrocher with storage atelier:suspendu chute",
            "effect give @s minecraft:slow_falling 3 0 true",
            f"tp @s {tx + 0.5} {ty} {tz + 0.5} 0 0",
            'title @s actionbar {"text":"Tu es tombé !","color":"red"}']) + "\n"
    reset += ["scoreboard players set #boss suspendu 0", "kill @e[type=donjon:golem]", "kill @e[type=donjon:graine]", "schedule clear atelier:suspendu_ss3_invoque", "function atelier:suspendu_ss3_fermer_sortie"]
    return f, tick, reset
