# [claude-build]
"""donjon_ciel_fin.py - lumiere, blocs de commande (paravoile, points de passage, retour du vide, portes, cle), controles et ecriture. Execute par donjon_ciel.py."""

# ====================================================================== lumiere : aucune case interieure sous le niveau 5 (pas d'apparition de monstres)
TRANSP = {"light", "lever", "stone_button", "oak_wall_sign", "ladder", "iron_bars", "short_grass", "lantern", "stone_brick_wall", "chain", "candle"}
SEUIL = 5
lum = {}


def opaque(p):
    n_ = nom(p)
    return (n_ is not None and n_ not in TRANSP and not n_.endswith(("_carpet", "_banner", "_wall_banner", "_pane")) and not n_.startswith("potted_")
            and n_ not in ("fern", "large_fern", "rose_bush", "lilac", "peony", "poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                           "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "end_rod", "lightning_rod"))


def propage(src, niveau):
    file_ = deque([(src, niveau)])
    while file_:
        p, l_ = file_.popleft()
        if lum.get(p, -1) >= l_:
            continue
        lum[p] = l_
        if l_ <= 1:
            continue
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if 0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ and not opaque(q):
                file_.append((q, l_ - 1))


for p_, s_ in list(blocs.items()):
    if nom(p_) == "lantern":
        propage(p_, 15)
    elif nom(p_) == "light":
        propage(p_, int(dict(s_[1])["level"]))
nl = 0
for p_ in sorted(INTERIEUR, key=lambda q: (q[1], q[0], q[2])):
    if p_ not in blocs and lum.get(p_, 0) < SEUIL:
        lumiere(*p_, 14)
        propage(p_, 14)
        nl += 1
noirs = [p_ for p_ in INTERIEUR if p_ not in blocs and lum.get(p_, 0) < SEUIL]
print(f"[4] lumiere : {nl} blocs de lumiere ajoutes | cases sombres {len(noirs)}", flush=True)

# ====================================================================== blocs de commande
CLE = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé des Nuées\"']"
CLE_DON = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé des Nuées\"',minecraft:lore=['\"Ouvre la porte de la Citadelle haute\"']]"
PARAVOILE = "grappin:paravoile"
VARS = ("#l1", "#l2", "#l3", "#kp", "#cle", "#gl", "#an", "#sm", "#sq", "#e1", "#e2", "#e3", "#ec")


class Ligne:
    def __init__(self, x0, y, z, n):
        self.x0, self.y, self.z, self.n, self.cmds = x0, y, z, n, []

    def rel(self, x, y, z):
        """Coordonnees relatives au bloc de commande qui va porter cette commande (elle est ajoutee juste apres) ; jamais derriere un « positioned »."""
        return f"~{x - (self.x0 + len(self.cmds))} ~{y - self.y} ~{z - self.z}"


class Rel:
    """Fabrique de coordonnees relatives pour le bloc de commande qui va porter la commande : r(x, y, z) en cases, r.c(x, y, z) au centre de la case (pour un tp)."""

    def __init__(self, ligne):
        self.l = ligne

    def __call__(self, x, y, z):
        return self.l.rel(x, y, z)

    def c(self, x, y, z):
        i = len(self.l.cmds)
        return f"~{x - (self.l.x0 + i)}.5 ~{y - self.l.y} ~{z - self.l.z}.5"


class Chaine:
    def __init__(self, nom_, lignes, cp_ok=True):
        self.nom, self.lignes, self.cp_ok = nom_, lignes, cp_ok

    def libre(self):
        return sum(l_.n - len(l_.cmds) for l_ in self.lignes)

    def ajoute(self, f):
        for l_ in self.lignes:
            if len(l_.cmds) < l_.n:
                c = f(Rel(l_))
                l_.cmds.append(c)
                return
        raise AssertionError(("chaine pleine", self.nom))


CHAINES = {}
for n_ in ("D", "AI", "MV", "CI", "BO"):
    i_ = ILES[n_]
    y_ = i_["T"] - 9
    CHAINES[n_] = Chaine(n_, [Ligne(i_["cx"] - 8, y_, i_["cz"], 17), Ligne(i_["cx"] - 8, y_, i_["cz"] + 3, 17)])
for g_ in ("G1", "G2", "G3"):
    i_ = ILES[g_]
    CHAINES[g_] = Chaine(g_, [Ligne(i_["cx"] - 3, i_["T"] - 5, i_["cz"], 7)])
i_ = ILES["CP"]                                                    # les anneaux de vol : sous la plateforme d'atterrissage
CHAINES["CP"] = Chaine("CP", [Ligne(i_["cx"] - 6, i_["T"] - 4, i_["cz"], 13), Ligne(i_["cx"] - 6, i_["T"] - 4, i_["cz"] + 3, 13)], cp_ok=False)
i_ = ILES["CI"]                                                    # le jeu des lampes : sous la crypte
CHAINES["CR"] = Chaine("CR", [Ligne(i_["cx"] - 6, i_["T"] - 12, i_["cz"] + dz_, 13) for dz_ in (0, 3, -3)], cp_ok=False)

LEV_NIV = [(k_, tuple(v_["leviers"][0])) for k_, v_ in PORTES.items() if v_.get("tour") == "AI"]


def fill_(r, cases, bloc_):
    xs, ys, zs = zip(*[tuple(c) for c in cases])
    return f"fill {r(min(xs), min(ys), min(zs))} {r(max(xs), max(ys), max(zs))} {bloc_}"


def ouvre(r, nom_p):
    return fill_(r, PORTES[nom_p]["cases"], "minecraft:air")


# -- chaine du Debarcadere : reglages du jeu, variables, don de la paravoile
C_ = CHAINES["D"]
C_.ajoute(lambda r: "gamerule commandBlockOutput false")
C_.ajoute(lambda r: "gamerule doMobSpawning false")
C_.ajoute(lambda r: "scoreboard objectives add ciel dummy")
for v_ in VARS:
    C_.ajoute(lambda r, v_=v_: f"scoreboard players add {v_} ciel 0")
C_.ajoute(lambda r: f"execute positioned {r(cxd - 11, Td + 1, czd - 11)} as @a[dx=22,dy=10,dz=22] unless items entity @s hotbar.* {PARAVOILE} "
                    f"unless items entity @s inventory.* {PARAVOILE} unless items entity @s weapon.offhand {PARAVOILE} run give @s {PARAVOILE}")
# -- aucune autre facon de voler ou de ralentir sa chute : dans chaque chaine, on retire elytres, perles, charges de vent, fruits de chorus, trident et les effets de chute lente
INTERDITS = ("minecraft:elytra", "minecraft:ender_pearl", "minecraft:wind_charge", "minecraft:chorus_fruit", "minecraft:trident")
for n_ in ("D", "AI", "MV", "CI", "BO"):
    for it in INTERDITS:
        CHAINES[n_].ajoute(lambda r, it=it: f"execute positioned {r(0, 0, 0)} run clear @a[dx={SX - 1},dy={SY - 1},dz={SZ - 1}] {it}")
    for ef in ("minecraft:slow_falling", "minecraft:levitation"):
        CHAINES[n_].ajoute(lambda r, ef=ef: f"execute positioned {r(0, 0, 0)} run effect clear @a[dx={SX - 1},dy={SY - 1},dz={SZ - 1}] {ef}")

# -- levers des trois girouettes : memorises dans des variables (les trois ilots sont loin du moulin)
for k_, g_ in enumerate(("G1", "G2", "G3"), 1):
    CHAINES[g_].ajoute(lambda r, g_=g_, k_=k_: f"execute if block {r(*LEV_G[g_])} minecraft:lever[powered=true] run scoreboard players set #l{k_} ciel 1")
# -- portes
for k_, lev in LEV_NIV:
    CHAINES["AI"].ajoute(lambda r, k_=k_, lev=lev: f"execute if block {r(*lev)} minecraft:lever[powered=true] run " + ouvre(r, k_))
CHAINES["MV"].ajoute(lambda r: "execute if score #l1 ciel matches 1 if score #l2 ciel matches 1 if score #l3 ciel matches 1 run " + ouvre(r, "G4"))
CHAINES["CI"].ajoute(lambda r: f"execute if block {r(*LEV_LW)} minecraft:lever[powered=true] run " + ouvre(r, "GC1"))
ZONE_CLE = (cxc - 1, Tc + 1, czc - 10)                             # devant la porte de la tour : 3 de large, 4 de profond (cote nord)
CHAINES["CI"].ajoute(lambda r: f"execute if score #cle ciel matches 0 positioned {r(*ZONE_CLE)} as @a[dx=2,dy=3,dz=3] if items entity @s weapon.mainhand {CLE} "
                               "run scoreboard players set #cle ciel 1")
CHAINES["CI"].ajoute(lambda r: "execute if score #cle ciel matches 1 run " + ouvre(r, "GK"))
CHAINES["CI"].ajoute(lambda r: f"execute if score #cle ciel matches 1 positioned {r(*ZONE_CLE)} as @a[dx=2,dy=3,dz=3] run clear @s {CLE} 1")
CHAINES["CI"].ajoute(lambda r: "execute if score #cle ciel matches 1 run scoreboard players set #cle ciel 2")
CHAINES["CI"].ajoute(lambda r: f"execute if score #cle ciel matches 0 positioned {r(*ZONE_CLE)} as @a[dx=2,dy=3,dz=3] unless items entity @s weapon.mainhand {CLE} "
                               "run title @s actionbar {\"text\":\"Porte fermée à clé (Clé des Nuées en main)\",\"color\":\"gold\"}")
REG_CACHEE = {1: "D", 2: "AI", 3: "CI"}
for num_, n_ in REG_CACHEE.items():
    CHAINES[n_].ajoute(lambda r, num_=num_: f"execute if block {r(*CACHEES[num_]['bouton'])} minecraft:stone_button[powered=true] run " + ouvre(r, f"H{num_}"))

# ====================================================================== les enigmes
BTN = "minecraft:stone_button[powered=true]"


def sequence_boutons(chaine, var, boutons, garde=""):
    """Boutons a presser dans l'ordre : presser trop tot remet la variable a zero (comme la sequence des glyphes du Sanctuaire) ; presser dans l'ordre l'avance d'un cran."""
    for k, b in enumerate(boutons, 1):
        if k >= 2:
            chaine.ajoute(lambda r, b=b, k=k: f"execute if block {r(*b)} {BTN} {garde}if score {var} ciel matches ..{k - 2} run scoreboard players set {var} ciel 0")
        chaine.ajoute(lambda r, b=b, k=k: f"execute if block {r(*b)} {BTN} {garde}if score {var} ciel matches {k - 1} run scoreboard players set {var} ciel {k}")


# -- 1. les glyphes de l'Aiguille : l'ordre est affiche sur la stele du Debarcadere
sequence_boutons(CHAINES["AI"], "#gl", [BOUTONS_G[n_] for n_ in ORDRE_GLYPHES])
CHAINES["AI"].ajoute(lambda r: f"execute if score #gl ciel matches 4 positioned {r(*BOUTONS_G[ORDRE_GLYPHES[0]])} run playsound minecraft:block.note_block.chime master @a[distance=..30] ~ ~ ~ 1 1")
CHAINES["AI"].ajoute(lambda r: "execute if score #gl ciel matches 4 run " + ouvre(r, "GG"))
CHAINES["AI"].ajoute(lambda r: "execute if score #gl ciel matches 4 run scoreboard players set #gl ciel 5")

# -- 2. les anneaux de vol : dans l'ordre, un anneau passe devient vert ; au troisieme la herse de la tour de la plateforme cede ; le levier de rappel ramene au Relais
C2 = CHAINES["CP"]
for i_, (ax_, az_, ay_) in enumerate(ANNEAUX):
    C2.ajoute(lambda r, i_=i_, ax_=ax_, az_=az_, ay_=ay_: f"execute if score #an ciel matches {i_} positioned {r(ax_, ay_ - DEMI_ANNEAU, az_ - DEMI_ANNEAU)} if entity @a[dx=0,dy={2 * DEMI_ANNEAU},dz={2 * DEMI_ANNEAU}] "
                                                          f"run scoreboard players set #an ciel {i_ + 1}")
for i_, cases_ in enumerate(ANNEAUX_CASES):
    C2.ajoute(lambda r, i_=i_, cases_=cases_: f"execute if score #an ciel matches {i_ + 1}.. run " + fill_(r, cases_, "minecraft:verdant_froglight replace minecraft:ochre_froglight"))
    C2.ajoute(lambda r, i_=i_, cases_=cases_: f"execute if score #an ciel matches ..{i_} run " + fill_(r, cases_, "minecraft:ochre_froglight replace minecraft:verdant_froglight"))
C2.ajoute(lambda r: "execute if score #an ciel matches 3.. run " + ouvre(r, "GR"))
C2.ajoute(lambda r: f"execute positioned {r(114, Tp, 244)} run tag @a[dx=10,dy=4,dz=6] add rappel")
C2.ajoute(lambda r: f"execute if block {r(*LEV_RAPPEL)} minecraft:lever[powered=true] as @a[tag=rappel] run tp @s {r.c(*POINTS['RL'])}")
C2.ajoute(lambda r: f"execute if block {r(*LEV_RAPPEL)} minecraft:lever[powered=true] run scoreboard players set #an ciel 0")
C2.ajoute(lambda r: f"execute if block {r(*LEV_RAPPEL)} minecraft:lever[powered=true] run setblock {r(*LEV_RAPPEL)} minecraft:lever[face=wall,facing=west,powered=false]")
C2.ajoute(lambda r: "tag @a remove rappel")

# -- 3. la crypte : on regarde les lampes s'allumer, puis on les rallume dans l'ordre (jeu du Simon) ; la cle est la recompense
C3 = CHAINES["CR"]
BOITE_LAMPES = list(LAMPES.values())
C3.ajoute(lambda r: f"execute if block {r(*START_L)} {BTN} if score #sm ciel matches 0 run scoreboard players set #sq ciel 0")
C3.ajoute(lambda r: f"execute if block {r(*START_L)} {BTN} if score #sm ciel matches 0 run scoreboard players set #sm ciel 1")
C3.ajoute(lambda r: "execute if score #sm ciel matches 1.. run scoreboard players add #sm ciel 1")
for i_, lampe_ in enumerate(SEQUENCE):
    t_on = 20 + 25 * i_
    C3.ajoute(lambda r, t_on=t_on, lampe_=lampe_: f"execute if score #sm ciel matches {t_on} run setblock {r(*LAMPES[lampe_])} minecraft:sea_lantern")
    C3.ajoute(lambda r, t_on=t_on, lampe_=lampe_: f"execute if score #sm ciel matches {t_on} positioned {r(*LAMPES[lampe_])} run playsound minecraft:block.note_block.bell master "
                                                  f"@a[distance=..16] ~ ~ ~ 1 {[0.7, 0.85, 1.0, 1.2, 1.4][lampe_ - 1]}")
    C3.ajoute(lambda r, t_on=t_on: f"execute if score #sm ciel matches {t_on + 15} run " + fill_(r, BOITE_LAMPES, "minecraft:gray_concrete"))
C3.ajoute(lambda r: "execute if score #sm ciel matches 150 run scoreboard players set #sm ciel 0")
sequence_boutons(C3, "#sq", [BOUTONS_L[k_] for k_ in SEQUENCE], garde="if score #sm ciel matches 0 ")
C3.ajoute(lambda r: f"execute if score #sq ciel matches 5 if score #kp ciel matches 0 positioned {r(*START_L)} run give @a[distance=..8,limit=1,sort=nearest] {CLE_DON} 1")
C3.ajoute(lambda r: f"execute if score #sq ciel matches 5 positioned {r(*START_L)} run playsound minecraft:block.note_block.chime master @a[distance=..30] ~ ~ ~ 1 1.2")
C3.ajoute(lambda r: "execute if score #sq ciel matches 5 run scoreboard players set #kp ciel 1")
C3.ajoute(lambda r: "execute if score #sq ciel matches 5 run scoreboard players set #sq ciel 6")


def ch_proche(x, z, n):
    cand_ = sorted([c for c in CHAINES.values() if c.cp_ok and c.libre() >= n], key=lambda c: (ILES[c.nom]["cx"] - x) ** 2 + (ILES[c.nom]["cz"] - z) ** 2)
    return cand_[0]


# -- 4. les trois eclats : un levier donne l'eclat (une seule fois) ; sur l'autel de l'arene, les trois ensemble ouvrent la sortie du boss
def item_eclat(nom_, lore=False):
    return "minecraft:amethyst_shard[minecraft:custom_name='\"" + nom_ + "\"'" + (",minecraft:lore=['\"Un morceau du ciel brise\"']" if lore else "") + "]"


TAGS_E = {k_: f"ciel_e{i_}" for i_, k_ in enumerate(ECLATS, 1)}                 # un joueur qui a pris un eclat porte l'etiquette : il ne le reprend pas, mais chacun peut prendre les siens
for k_, e_ in ECLATS.items():
    chp = ch_proche(e_["levier"][0], e_["levier"][2], 3)
    tg_ = TAGS_E[k_]
    chp.ajoute(lambda r, e_=e_, tg_=tg_: f"execute if block {r(*e_['levier'])} minecraft:lever[powered=true] positioned {r(*e_['levier'])} run "
                                         f"give @a[distance=..6,limit=1,sort=nearest,tag=!{tg_}] {item_eclat(e_['nom'], True)} 1")
    chp.ajoute(lambda r, e_=e_, tg_=tg_: f"execute if block {r(*e_['levier'])} minecraft:lever[powered=true] positioned {r(*e_['levier'])} run "
                                         f"tag @a[distance=..6,limit=1,sort=nearest,tag=!{tg_}] add {tg_}")
    chp.ajoute(lambda r, e_=e_: f"execute if block {r(*e_['levier'])} minecraft:lever[powered=true] run setblock {r(*e_['levier'])} minecraft:lever[face=wall,facing={e_['facing']},powered=false]")
C4 = CHAINES["BO"]
ZONE_AUTEL = (ALTAR[0] - 3, ALTAR[1] - 1, ALTAR[2] - 3)
C4.ajoute(lambda r: f"execute if score #ec ciel matches 0 positioned {r(*ZONE_AUTEL)} as @a[dx=6,dy=4,dz=6] " + " ".join(f"if items entity @s container.* {item_eclat(e_['nom'])}" for e_ in ECLATS.values())
                    + " run scoreboard players set #ec ciel 1")
for k_, e_ in ECLATS.items():
    C4.ajoute(lambda r, e_=e_: f"execute if score #ec ciel matches 1 positioned {r(*ZONE_AUTEL)} as @a[dx=6,dy=4,dz=6] run clear @s {item_eclat(e_['nom'])} 1")
    C4.ajoute(lambda r, k_=k_: f"execute if score #ec ciel matches 1 positioned {r(*ZONE_AUTEL)} as @a[dx=6,dy=4,dz=6] run tag @s remove {TAGS_E[k_]}")
C4.ajoute(lambda r: f"execute if score #ec ciel matches 1 positioned {r(*ALTAR)} run playsound minecraft:block.beacon.activate master @a[distance=..40] ~ ~ ~ 1 1")
C4.ajoute(lambda r: "execute if score #ec ciel matches 1 run " + ouvre(r, "SORTIE"))
C4.ajoute(lambda r: "execute if score #ec ciel matches 1 run scoreboard players set #ec ciel 2")

# -- le retour au Debarcadere : le levier de l'ile du Coeur ramene les joueurs proches au depart, sans leurs eclats (ils devront les reprendre)
C4.ajoute(lambda r: f"execute positioned {r(LEV_RETOUR[0] - 4, LEV_RETOUR[1] - 1, LEV_RETOUR[2] - 4)} run tag @a[dx=8,dy=5,dz=8] add retour")
C4.ajoute(lambda r: f"execute if block {r(*LEV_RETOUR)} minecraft:lever[powered=true] as @a[tag=retour] run tp @s {r.c(*POINTS['D'])}")
C4.ajoute(lambda r: f"execute if block {r(*LEV_RETOUR)} minecraft:lever[powered=true] as @a[tag=retour] run clear @s minecraft:amethyst_shard")
for k_ in ECLATS:
    C4.ajoute(lambda r, k_=k_: f"execute if block {r(*LEV_RETOUR)} minecraft:lever[powered=true] as @a[tag=retour] run tag @s remove {TAGS_E[k_]}")
C4.ajoute(lambda r: f"execute if block {r(*LEV_RETOUR)} minecraft:lever[powered=true] run setblock {r(*LEV_RETOUR)} minecraft:lever[face=wall,facing=west,powered=false]")
C4.ajoute(lambda r: "tag @a remove retour")

# -- points de passage (un numero par ile, memorise par joueur) et retour du vide
NUM = {n_: i_ for i_, n_ in enumerate(ORDRE + ["HA", "HB"], 1)}
CP = {}
for n_ in ORDRE + ["HA", "HB"]:
    CP[n_] = POINTS[n_]
for n_ in ORDRE + ["HA", "HB"]:
    i_ = ILES[n_]
    ax, bz = i_["rect"] if i_["rect"] else (i_["r"], i_["r"])
    ht = max([t_["top"] for t_ in i_["tours"]] + [i_["T"]]) - i_["T"] + 8
    if n_ in ("HA", "HB"):
        ht = 12
    cand = sorted([c for c in CHAINES.values() if c.cp_ok and c.libre() >= 3], key=lambda c: (ILES[c.nom]["cx"] - i_["cx"]) ** 2 + (ILES[c.nom]["cz"] - i_["cz"]) ** 2)
    ch_ = cand[0]
    bx0, by0, bz0 = i_["cx"] - ax - 1, i_["T"] - 1, i_["cz"] - bz - 1
    # le point de reapparition suit les iles : a l'arrivee sur une ile (et une seule fois par arrivee), il est reporte au point de passage ; une mort y ramene
    ch_.ajoute(lambda r, i_=i_, ax=ax, bz=bz, ht=ht, n_=n_, bx0=bx0, by0=by0, bz0=bz0: f"execute positioned {r(bx0, by0, bz0)} as @a[dx={2 * ax + 2},dy={ht},dz={2 * bz + 2}] "
                                                              f"unless score @s ciel matches {NUM[n_]} run spawnpoint @s ~{CP[n_][0] - bx0} ~{CP[n_][1] - by0} ~{CP[n_][2] - bz0}")
    ch_.ajoute(lambda r, i_=i_, ax=ax, bz=bz, ht=ht, n_=n_: f"execute positioned {r(i_['cx'] - ax - 1, i_['T'] - 1, i_['cz'] - bz - 1)} as @a[dx={2 * ax + 2},dy={ht},dz={2 * bz + 2}] "
                                                              f"run scoreboard players set @s ciel {NUM[n_]}")
    px_, py_, pz_ = CP[n_]
    ch_.ajoute(lambda r, n_=n_, px_=px_, py_=py_, pz_=pz_: f"execute positioned {r(0, 0, 0)} as @a[scores={{ciel={NUM[n_]}}},dx={SX - 1},dy={VIDE_Y - 1},dz={SZ - 1}] "
                                                           f"run tp @s ~{px_}.5 ~{py_} ~{pz_}.5")

assert all(len(l_.cmds) <= l_.n for c_ in CHAINES.values() for l_ in c_.lignes)
nb_cmd = 0
CMD_BLOCS = []
for c_ in CHAINES.values():
    for l_ in c_.lignes:
        for i_, c in enumerate(l_.cmds):
            x, y, z = l_.x0 + i_, l_.y, l_.z
            CMD_BLOCS.append((x, y, z))
            assert (x, y, z) in blocs and nom((x, y, z)) in ROCHES, ("chaine hors de la roche", c_.nom, (x, y, z), nom((x, y, z)))
            put(x, y, z, S("repeating_command_block" if i_ == 0 else "chain_command_block", conditional=False, facing="east"))
            NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(c), "auto": Byte(1), "TrackOutput": Byte(0),
                                       "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})
            nb_cmd += 1
print(f"[5] commandes : {nb_cmd} | chaines {[(c_.nom, [len(l_.cmds) for l_ in c_.lignes]) for c_ in CHAINES.values()]}", flush=True)

# ====================================================================== controles
err = []
INTERDIT_NOMS = {"chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "trapped_chest", "furnace", "conduit", "lava", "water", "lectern", "brewing_stand", "campfire",
                 "shulker_box", "bubble_column", "ancrage", "ender_chest", "crafting_table", "tnt", "end_portal_frame", "respawn_anchor"}
DIRS = {"east": (1, 0, 0), "west": (-1, 0, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
PASSE_ = {"air", "light", "lever", "stone_button", "oak_wall_sign", "ladder", "short_grass", "lantern", "iron_bars"}


def solide_(q):
    return q in blocs and nom(q) not in PASSE_


for (x, y, z), s in blocs.items():
    n, pr = nom((x, y, z)), dict(s[1])
    if n in INTERDIT_NOMS:
        err.append(f"interdit {n} {(x, y, z)}")
    if y < VIDE_Y + 2:
        err.append(f"bloc sous le niveau du vide {(x, y, z)}")
    if n in ("lever", "stone_button"):
        dx, dy, dz = DIRS[pr["facing"]]
        if not solide_((x - dx, y, z - dz)):
            err.append(f"{n} sans appui {(x, y, z)}")
    elif n == "oak_wall_sign":
        dx, dy, dz = DIRS[pr["facing"]]
        if not solide_((x - dx, y, z - dz)):
            err.append(f"panneau sans appui {(x, y, z)}")
        for l_ in NBT[(x, y, z)]["front_text"]["messages"]:
            if len(json.loads(str(l_))["text"]) > 14:
                err.append(f"panneau trop large {(x, y, z)}")
    elif n == "ladder":
        dx, dy, dz = DIRS[pr["facing"]]
        if not solide_((x - dx, y, z - dz)):
            err.append(f"echelle sans appui {(x, y, z)}")
for n_, p_ in PORTES.items():
    for c in p_["cases"]:
        if tuple(c) not in blocs:
            err.append(f"porte {n_} : case vide {c}")
for n_, p_ in PORTES.items():
    for lv in ([] if p_.get("atteindre") else p_["leviers"]):
        if nom(tuple(lv)) not in ("lever", "stone_button"):
            err.append(f"porte {n_} : pas de levier en {lv}")
for p_ in INTERIEUR:
    if p_ not in blocs and lum.get(p_, 0) < SEUIL:
        err.append(f"case sombre {p_}")
print("[controle] erreurs", len(err), Counter(e.split(" (")[0].split(" ")[0] for e in err).most_common(6), err[:8], flush=True)
if err:
    raise SystemExit(1)


# ====================================================================== ecriture
def entite_anneau(px, py, pz, sx, sy, sz, i):
    nul = [Float(0), Float(0), Float(0)]
    nbt = Compound({"id": String("minecraft:block_display"),
                    "block_state": Compound({"Name": String("minecraft:ochre_froglight"), "Properties": Compound({"axis": String("y")})}),
                    "transformation": Compound({"left_translation": List[Float](nul), "left_rotation": List[Float]([Float(0), Float(0), Float(0), Float(1)]),
                                                "scale": List[Float]([Float(sx), Float(sy), Float(sz)]), "right_rotation": List[Float]([Float(0), Float(0), Float(0), Float(1)])}),
                    "brightness": Compound({"sky": Int(15), "block": Int(15)}), "view_range": Float(4.0),
                    "Tags": List[String]([String("ciel_anneau"), String(f"ciel_anneau_{i}")])})
    return Compound({"pos": List[Double]([Double(px), Double(py), Double(pz)]), "blockPos": List[Int]([Int(px), Int(py), Int(pz)]), "nbt": nbt})


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
                     "blocks": List[Compound](liste), "entities": List[Compound]([entite_anneau(*e) for e in ENTITES])}, gzipped=True)
    (RACINE / "exports").mkdir(exist_ok=True)
    f.save(RACINE / "exports" / f"{nom_}.nbt")
    f.save(monde_atelier.datapack(SAVE) / "data/atelier/structure" / f"{nom_}.nbt")
    return len(liste)


DP = monde_atelier.datapack(SAVE)
n_total = ecrire(blocs, NBT, NOM, (SX, SY, SZ))


def w(p):
    return " ".join(str(v) for v in (ORIGINE[0] + p[0], ORIGINE[1] + p[1], ORIGINE[2] + p[2]))


def plage(cases):
    xs, ys, zs = zip(*[tuple(c) for c in cases])
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


FN = DP / "data/atelier/function"
monde_atelier.installer_pose_auto(SAVE, "ciel_pose", "ciel")
if True:
    moitie = SZ // 2
    fl = [f"forceload add {ORIGINE[0]} {ORIGINE[2]} {ORIGINE[0] + SX - 1} {ORIGINE[2] + moitie - 1}",
          f"forceload add {ORIGINE[0]} {ORIGINE[2] + moitie} {ORIGINE[0] + SX - 1} {ORIGINE[2] + SZ - 1}"]
    (FN / "ciel_pose.mcfunction").write_text("\n".join([
        "# [claude-build] Pose l'Archipel des Nuees (sans pause).", *fl,
        'tellraw @a {"text":"Archipel : 30 secondes de chargement, puis le jeu va se figer un moment. Ne mets pas en pause.","color":"gold"}',
        "schedule function atelier:ciel_pose_2 600t"]) + "\n", encoding="utf-8")
    cases_g = [(a_, b_, c_) for a_ in range((SX + 30) // 31) for b_ in range((SY + 30) // 31) for c_ in range((SZ + 30) // 31)]      # tout le gabarit : aucun reste d'une version precedente
    fills = [f"fill {ORIGINE[0] + a * 31} {ORIGINE[1] + b * 31} {ORIGINE[2] + c * 31} {ORIGINE[0] + min(a * 31 + 30, SX - 1)} {ORIGINE[1] + min(b * 31 + 30, SY - 1)} "
             f"{ORIGINE[2] + min(c * 31 + 30, SZ - 1)} minecraft:air" for (a, b, c) in cases_g]
    (FN / "ciel_pose_2.mcfunction").write_text("\n".join(["# [claude-build] Efface les zones deja occupees, pose la structure, remet a zero."] + fills + [
        "kill @e[type=minecraft:block_display,tag=ciel_anneau]", f"place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}", "kill @e[type=item]", "function atelier:ciel_reset",
        "schedule function atelier:ciel_pose_3 200t"]) + "\n", encoding="utf-8")
    (FN / "ciel_pose_3.mcfunction").write_text("\n".join([
        "# [claude-build] Nettoie et decharge.", "kill @e[type=item]", "forceload remove all",
        monde_atelier.message_entree("L'archipel est prêt.", w(POINTS["D"]))]) + "\n", encoding="utf-8")
    (a, b, c), (d_, e, f_) = plage(PORTES["SORTIE"]["cases"])
    (FN / "ciel_ouvrir_sortie_boss.mcfunction").write_text(
        f"# [claude-build] Ouvre la sortie du boss (a relier a la mort du boss).\nfill {w((a, b, c))} {w((d_, e, f_))} minecraft:air\n"
        f"playsound minecraft:block.iron_door.open master @a {w((a, b, c))} 2 0.6\n", encoding="utf-8")
    (FN / "ciel_fermer_sortie_boss.mcfunction").write_text(
        f"# [claude-build] Referme la sortie du boss.\nfill {w((a, b, c))} {w((d_, e, f_))} minecraft:iron_bars\n", encoding="utf-8")
    reset = ["# [claude-build] Remet l'archipel a zero : herses refermees, leviers rabaisses, dalles des salles cachees remises, cle et variables remises."]
    for n_, p in PORTES.items():
        (a, b, c), (d_, e, f_) = plage(p["cases"])
        reset.append(f"fill {w((a, b, c))} {w((d_, e, f_))} " + ("minecraft:polished_andesite" if p.get("cachee") else "minecraft:iron_bars"))
    for (x, y, z), s in blocs.items():
        if nom((x, y, z)) == "lever":
            reset.append(f"setblock {w((x, y, z))} minecraft:lever[face=wall,facing={dict(s[1])['facing']},powered=false]")
    reset += ["scoreboard objectives add ciel dummy"] + [f"scoreboard players set {v_} ciel 0" for v_ in VARS]
    (a, b, c), (d_, e, f_) = plage(BOITE_LAMPES)
    reset.append(f"fill {w((a, b, c))} {w((d_, e, f_))} minecraft:gray_concrete")
    for cases_ in ANNEAUX_CASES:
        (a, b, c), (d_, e, f_) = plage(cases_)
        reset.append(f"fill {w((a, b, c))} {w((d_, e, f_))} minecraft:ochre_froglight replace minecraft:verdant_froglight")
    reset += [f"tag @a remove {tg_}" for tg_ in TAGS_E.values()]
    reset += [f"clear @a {CLE}", "clear @a minecraft:amethyst_shard", 'tellraw @a {"text":"Archipel remis a zero.","color":"gray"}']
    (FN / "ciel_reset.mcfunction").write_text("\n".join(reset) + "\n", encoding="utf-8")
conf = {"_marque": "[claude-build]", "taille": [SX, SY, SZ], "origine": ORIGINE, "vide_y": VIDE_Y, "ordre": ORDRE, "portes": PORTES,
        "points": {k_: list(v_) for k_, v_ in POINTS.items()}, "depart": list(POINTS["D"]),
        "iles": {k_: {kk: vv for kk, vv in v_.items()} for k_, v_ in ILES.items()},
        "vols_chemins": VOLS_CHEMINS, "piliers": PILIERS, "boss": {k_: list(v_) for k_, v_ in BOSS.items()},
        "cachees": {str(k_): {kk: (list(vv) if isinstance(vv, (tuple, list)) else vv) for kk, vv in v_.items()} for k_, v_ in CACHEES.items()},
        "cabanes": {k_: {kk: list(vv) for kk, vv in v_.items()} for k_, v_ in CABANES.items()},
        "leviers_girouettes": {k_: list(v_) for k_, v_ in LEV_G.items()}, "levier_lw": list(LEV_LW), "levier_rappel": list(LEV_RAPPEL), "anneaux": ANNEAUX, "demi_anneau": DEMI_ANNEAU, "sequence_lampes": SEQUENCE, "ordre_glyphes": ORDRE_GLYPHES,
        "boutons_glyphes": {k_: list(v_) for k_, v_ in BOUTONS_G.items()}, "boutons_lampes": {str(k_): list(v_) for k_, v_ in BOUTONS_L.items()}, "autel": list(ALTAR), "start_lampes": list(START_L), "levier_retour": list(LEV_RETOUR),
        "eclats": {k_: {"nom": e_["nom"], "levier": list(e_["levier"]), "ile": e_["ile"]} for k_, e_ in ECLATS.items()}, "cp": {k_: list(v_) for k_, v_ in CP.items()}}
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps(conf, ensure_ascii=False), encoding="utf-8")
print(f"Ecrit : {n_total} blocs | commandes {nb_cmd} | portes {list(PORTES)}")
