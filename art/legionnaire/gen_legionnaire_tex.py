"""Atlas 64x128 du légionnaire pour ses 5 niveaux (box UV Blockbench).

    python -I art/legionnaire/gen_legionnaire_tex.py art/legionnaire/out

Écrit legionnaire_1.png … legionnaire_5.png (même mise en page, seule la peinture change) et uv.json (lu par le
script Blockbench pour poser les cubes). Ne sert qu'à amorcer la peinture : la source de vérité reste
art/legionnaire/legionnaire.bbmodel.
"""
import json
import os
import sys
from PIL import Image

OUT = sys.argv[1]
W, H = 64, 128


def c(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


NONE = (0, 0, 0, 0)
SKIN, SKIN_D, SKIN_L = c("e4b88e"), c("c99a72"), c("f0cca6")
HAIR, HAIR_D, HAIR_L = c("4a3022"), c("33200f"), c("684630")
RED, RED_D, RED_L = c("a02a28"), c("6f1a1a"), c("c4403a")
LEATH, LEATH_D, LEATH_L = c("7a5230"), c("523520"), c("9c7248")
WOOD, WOOD_D, WOOD_L = c("8a6238"), c("5f4224"), c("a8804e")
STEEL, STEEL_D, STEEL_L = c("a9b0b8"), c("6e757e"), c("d3d9de")
MAIL, MAIL_D = c("8a919a"), c("5d636b")
GOLD, GOLD_D, GOLD_L = c("d4b24a"), c("9a7b26"), c("ecd27a")
CREAM = c("e8dcc0")

# nom: (w, h, d)
PARTS = {
    "head": (8, 8, 8), "hair": (8, 8, 8),
    "chest": (8, 6, 4), "waist": (8, 6, 4), "skirt": (9, 4, 5),
    "r_arm": (4, 6, 4), "r_fore": (4, 6, 4), "l_arm": (4, 6, 4), "l_fore": (4, 6, 4),
    "r_thigh": (4, 6, 4), "r_shin": (4, 6, 4), "l_thigh": (4, 6, 4), "l_shin": (4, 6, 4),
    "shield_c": (3, 14, 1), "shield_w": (3, 14, 1), "boss": (3, 3, 2),
    "pommel": (2, 2, 2), "grip": (1, 3, 1), "guard": (5, 2, 2), "blade": (2, 12, 1), "tip": (1, 2, 1),
    "helm": (8, 4, 8), "neck": (9, 3, 1), "cheek": (1, 3, 5),
    "crest_l": (1, 3, 8), "crest_t": (10, 4, 3),
    "cape": (9, 8, 1), "cape_2": (9, 8, 1), "paul": (5, 2, 5),
    "plate": (8, 6, 4), "plate_w": (8, 6, 4),
}


def pack():
    """Les pièces déjà placées (uv_v1.json) gardent leur offset ; les nouvelles prennent la première place libre."""
    import json as _json
    here = os.path.dirname(os.path.abspath(__file__))
    old = {}
    pth = os.path.join(here, "uv_v1.json")
    if os.path.exists(pth):
        old = _json.load(open(pth))
    uv = {}
    occ = [[False] * W for _ in range(H)]

    def mark(x, y, pw, ph):
        for j in range(y, y + ph):
            for i in range(x, x + pw):
                occ[j][i] = True

    def free(x, y, pw, ph):
        if x + pw > W or y + ph > H:
            return False
        return not any(occ[j][i] for j in range(y, y + ph) for i in range(x, x + pw))

    for n, (w, h, d) in PARTS.items():
        if n in old and tuple(old[n]["size"]) == (w, h, d):
            x, y = old[n]["uv"]
            uv[n] = (x, y)
            mark(x, y, 2 * d + 2 * w, d + h)
    for n, (w, h, d) in sorted(PARTS.items(), key=lambda kv: (-(kv[1][2] + kv[1][1]), kv[0])):
        if n in uv:
            continue
        pw, ph = 2 * d + 2 * w, d + h
        for y in range(H):
            for x in range(W):
                if free(x, y, pw, ph):
                    uv[n] = (x, y)
                    mark(x, y, pw, ph)
                    break
            if n in uv:
                break
        assert n in uv, n
    return uv


UV = pack()


class Painter:
    def __init__(self):
        self.img = Image.new("RGBA", (W, H), NONE)
        self.px = self.img.load()

    def faces(self, n):
        w, h, d = PARTS[n]
        u, v = UV[n]
        return {
            "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
            "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        }

    def rect(self, x, y, w, h, col):
        for i in range(x, x + w):
            for j in range(y, y + h):
                self.px[i, j] = col

    def fill(self, n, col, which=None):
        for k, (x, y, w, h) in self.faces(n).items():
            if which is None or k in which:
                self.rect(x, y, w, h, col)

    def put(self, n, face, dx, dy, col):
        x, y, w, h = self.faces(n)[face]
        if 0 <= dx < w and 0 <= dy < h:
            self.px[x + dx, y + dy] = col

    def band(self, n, face, y0, y1, col):
        x, y, w, h = self.faces(n)[face]
        self.rect(x, y + y0, w, y1 - y0, col)

    def sides(self, n, y0, y1, col):
        for f in ("north", "south", "east", "west"):
            self.band(n, f, y0, y1, col)

    def tex(self, n, col_b, step=3):
        for k, (x, y, w, h) in self.faces(n).items():
            for i in range(w):
                for j in range(h):
                    base = self.px[x + i, y + j]
                    if base[3] and (i * 2 + j * 3) % (step + 2) == 0:
                        self.px[x + i, y + j] = tuple(int(base[m] * 0.6 + col_b[m] * 0.4) for m in range(3)) + (255,)

    def hlines(self, n, faces, step, col, y0=0):
        """Lignes horizontales (bandes d'armure, rangs de mailles) toutes les `step` lignes."""
        for f in faces:
            x, y, w, h = self.faces(n)[f]
            for j in range(y0, h, step):
                self.rect(x, y + j, w, 1, col)


def paint(level):
    P = Painter()

    # ---- tête : même homme à tous les niveaux ---------------------------------------------------------------
    P.fill("head", SKIN)
    P.fill("head", SKIN_D, ("down",))
    for dx, dy, col in [(1, 4, c("ffffff")), (2, 4, c("4a2f1c")), (5, 4, c("4a2f1c")), (6, 4, c("ffffff")),
                        (1, 3, HAIR_D), (2, 3, HAIR_D), (3, 3, HAIR_D), (4, 3, HAIR_D), (5, 3, HAIR_D), (6, 3, HAIR_D),
                        (3, 6, c("a8605a")), (4, 6, c("a8605a")), (3, 5, SKIN_D), (4, 5, SKIN_D),
                        (2, 7, SKIN_D), (3, 7, SKIN_D), (4, 7, SKIN_D), (5, 7, SKIN_D), (1, 6, SKIN_D), (6, 6, SKIN_D)]:
        P.put("head", "north", dx, dy, col)
    # barbe de trois jours : mâchoire un ton plus sombre
    for f in ("east", "west"):
        x, y, w, h = P.faces("head")[f]
        for i in range(w):
            for j in range(6, 8):
                if (i + j) % 2 == 0:
                    P.px[x + i, y + j] = SKIN_D

    # ---- cheveux courts : calotte, front dégagé --------------------------------------------------------------
    P.fill("hair", NONE)
    fx, fy, fw, fh = P.faces("hair")["up"]
    P.rect(fx, fy, fw, fh, HAIR)
    bx, by, bw, bh = P.faces("hair")["south"]
    P.rect(bx, by, bw, 6, HAIR)
    P.rect(bx, by + 6, bw, 2, HAIR_D)
    nx, ny, nw, nh = P.faces("hair")["north"]
    P.rect(nx, ny, nw, 2, HAIR)
    P.put("hair", "north", 0, 2, HAIR)
    P.put("hair", "north", 7, 2, HAIR)
    for side in ("east", "west"):
        sx, sy, sw, sh = P.faces("hair")[side]
        P.rect(sx, sy, sw, 3, HAIR)
        P.rect(sx, sy + 3, sw, 1, HAIR_D)
    for f in ("up", "south"):
        x, y, w, h = P.faces("hair")[f]
        for i in range(w):
            for j in range(h):
                if (i * 3 + j * 5) % 7 == 0:
                    P.px[x + i, y + j] = HAIR_L

    # ---- palettes par niveau ----------------------------------------------------------------------------------
    tun = {1: (RED, RED_D, RED_L), 2: (RED, RED_D, RED_L), 3: (RED, RED_D, RED_L), 4: (RED, RED_D, RED_L),
           5: (c("8c1f26"), c("5a1219"), c("b32f33"))}[level]
    T, TD, TL = tun
    trim = GOLD if level == 5 else (STEEL if level >= 3 else LEATH)
    trim_d = GOLD_D if level == 5 else (STEEL_D if level >= 3 else LEATH_D)

    # ---- torse -------------------------------------------------------------------------------------------------
    P.fill("chest", T)
    P.fill("chest", TD, ("down",))
    P.fill("chest", TL, ("up",))
    P.fill("waist", T)
    P.fill("waist", LEATH, ("down",))
    if level == 1:
        # tunique rouge, harnais de cuir croisé sur la poitrine
        for i in range(6):
            P.put("chest", "north", 1 + i, i, LEATH_D)
            P.put("chest", "north", 6 - i, i, LEATH_D)
            P.put("chest", "south", 1 + i, i, LEATH_D)
            P.put("chest", "south", 6 - i, i, LEATH_D)
    elif level == 2:
        # cuirasse de cuir clouté
        for f in ("north", "south"):
            x, y, w, h = P.faces("chest")[f]
            P.rect(x, y, w, h, LEATH)
        P.hlines("chest", ("north", "south"), 2, LEATH_D, 1)
        for dx in (1, 6):
            for dy in (1, 3, 5):
                P.put("chest", "north", dx, dy, STEEL_L)
        P.sides("chest", 0, 1, TD)
    elif level == 3:
        # cotte de mailles : trame grise serrée
        P.fill("chest", MAIL)
        P.fill("chest", MAIL_D, ("down",))
        P.fill("waist", MAIL)
        P.hlines("chest", ("north", "south", "east", "west"), 2, MAIL_D)
        P.hlines("waist", ("north", "south", "east", "west"), 2, MAIL_D)
        P.band("chest", "north", 0, 1, RED_D)
    else:
        # segmentata : la tunique reste visible dessous, les bandes d'acier sont des pièces posées par-dessus
        P.fill("waist", TD)
    P.tex("chest", TD, 4)
    P.tex("waist", TD, 4)
    belt = GOLD if level == 5 else LEATH_D
    P.sides("waist", 4, 6, belt if level != 5 else GOLD_D)
    P.sides("waist", 4, 5, LEATH_D if level != 5 else GOLD)
    for dx in (3, 4):
        P.put("waist", "north", dx, 4, GOLD_L if level >= 2 else STEEL_L)
        P.put("waist", "north", dx, 5, GOLD_L if level >= 2 else STEEL_L)
    # jupe de lanières (pteruges) : bande rouge puis lanières de cuir
    P.fill("skirt", T)
    P.fill("skirt", TD, ("up", "down"))
    for f in ("north", "south", "east", "west"):
        P.band("skirt", f, 1, 4, LEATH if level < 5 else LEATH_D)
        x, y, w, h = P.faces("skirt")[f]
        for i in range(0, w, 2):
            P.rect(x + i, y + 1, 1, 3, LEATH_D)
        P.band("skirt", f, 3, 4, GOLD_D if level == 5 else LEATH_D)

    # ---- bras : manches (haut), avant-bras (bas) ---------------------------------------------------------------------
    for n in ("r_arm", "l_arm"):
        P.fill(n, T)
        P.fill(n, TL, ("up",))
        P.fill(n, TD, ("down",))
        P.sides(n, 5, 6, TD)
        if level == 3:
            P.fill(n, MAIL)
            P.hlines(n, ("north", "south", "east", "west"), 2, MAIL_D)
            P.sides(n, 5, 6, RED_D)
    for n in ("r_fore", "l_fore"):
        P.fill(n, SKIN)
        P.fill(n, SKIN_D, ("down",))
        P.sides(n, 0, 3, trim if level >= 3 else LEATH)
        P.sides(n, 2, 3, trim_d if level >= 3 else LEATH_D)
        if level >= 4:
            P.sides(n, 0, 4, trim)
            P.sides(n, 3, 4, trim_d)
        P.fill(n, trim if level >= 3 else LEATH, ("up",))

    # ---- jambes : cuisses nues, sandales cloutées (n1-n3), grèves d'acier (n4) ou dorées (n5) ----------------------------------
    for n in ("r_thigh", "l_thigh"):
        P.fill(n, SKIN)
        P.fill(n, SKIN_D, ("up", "down"))
        P.sides(n, 0, 1, SKIN_D)
        for f in ("north", "south", "east", "west"):
            x, y, w, h = P.faces(n)[f]
            for i in range(w):
                if i % 2 == 0:
                    P.px[x + i, y + 4] = SKIN_D
    for n in ("r_shin", "l_shin"):
        P.fill(n, SKIN)
        P.fill(n, LEATH_D, ("down",))
        P.fill(n, SKIN_D, ("up",))
        P.sides(n, 5, 6, LEATH_D)
        for dy in (1, 3):
            P.sides(n, dy, dy + 1, LEATH)  # lanières de sandale
        if level >= 4:
            P.sides(n, 0, 5, trim)
            P.sides(n, 0, 1, trim_d)
            P.sides(n, 4, 5, trim_d)
            if level == 5:
                P.sides(n, 2, 3, GOLD_L)
            P.sides(n, 5, 6, LEATH_D)

    # ---- bouclier (scutum) : trois panneaux courbés, ombon, épée (gladius) ----------------------------------------------------
    sh_base = {1: WOOD, 2: RED, 3: RED, 4: c("a8302c"), 5: c("8c1f26")}[level]
    sh_dark = {1: WOOD_D, 2: RED_D, 3: RED_D, 4: RED_D, 5: c("5a1219")}[level]
    rim = {1: WOOD_D, 2: STEEL, 3: STEEL, 4: STEEL_L, 5: GOLD}[level]
    rim_d = {1: LEATH_D, 2: STEEL_D, 3: STEEL_D, 4: STEEL_D, 5: GOLD_D}[level]
    accent = {1: RED, 2: c("e6d9a8"), 3: c("e6d9a8"), 4: GOLD, 5: GOLD_L}[level]
    for n in ("shield_c", "shield_w"):
        P.fill(n, sh_base, ("north",))
        P.fill(n, LEATH_D, ("south",))
        for f in ("east", "west", "up", "down"):
            P.fill(n, rim, (f,))
        x, y, w, h = P.faces(n)["north"]
        P.rect(x, y, w, 1, rim)
        P.rect(x, y + h - 1, w, 1, rim)
        P.rect(x, y + 1, w, 1, rim_d)
        P.rect(x, y + h - 2, w, 1, rim_d)
        if level == 1:
            for j in range(3, h - 2, 3):          # joints entre les planches
                P.rect(x, y + j, w, 1, sh_dark)
    # panneau central : bande verticale d'accent, deux traits (ailes) de part et d'autre de l'ombon
    x, y, w, h = P.faces("shield_c")["north"]
    P.rect(x + 1, y + 2, 1, h - 4, accent)
    if level >= 2:
        P.rect(x, y + 3, w, 1, accent)
        P.rect(x, y + h - 4, w, 1, accent)
    # panneaux latéraux : champ uni et liseré sur le bord extérieur seulement (pas de motif qui traverse les jointures)
    x, y, w, h = P.faces("shield_w")["north"]
    P.rect(x + 2, y + 1, 1, h - 2, rim)
    P.fill("boss", STEEL_L if level < 5 else GOLD_L)
    P.fill("boss", STEEL_D if level < 5 else GOLD_D, ("down", "east"))
    P.put("boss", "north", 1, 1, c("ffffff") if level < 5 else c("fff3a8"))
    # épée : lame d'acier à gouttière centrale, pointe, garde, poignée de cuir, pommeau
    blade_c = {1: STEEL, 2: STEEL, 3: STEEL_L, 4: STEEL_L, 5: STEEL_L}[level]
    P.fill("blade", blade_c)
    for f in ("north", "south"):
        x, y, w, h = P.faces("blade")[f]
        P.rect(x, y, 1, h, STEEL_L if level >= 3 else STEEL)       # tranchant clair
        P.rect(x + 1, y, 1, h, STEEL_D)                             # gouttière / côté dans l'ombre
        P.rect(x + 1, y + 1, 1, h - 2, STEEL)                       # fond de la gouttière
    for f in ("east", "west", "up", "down"):
        P.fill("blade", STEEL_D, (f,))
    P.fill("tip", STEEL_L)
    P.fill("tip", STEEL, ("east", "west"))
    guard_c = {1: STEEL_D, 2: STEEL, 3: STEEL, 4: GOLD_D, 5: GOLD}[level]
    guard_d = {1: c("4a4f56"), 2: STEEL_D, 3: STEEL_D, 4: c("6e5518"), 5: GOLD_D}[level]
    P.fill("guard", guard_c)
    P.fill("guard", guard_d, ("down", "up"))
    for f in ("east", "west"):
        P.fill("guard", guard_d, (f,))
    P.put("guard", "north", 0, 0, guard_d)
    P.put("guard", "north", 4, 0, guard_d)
    P.fill("grip", LEATH_D)
    for f in ("north", "south", "east", "west"):
        P.band("grip", f, 0, 1, LEATH)
        P.band("grip", f, 2, 3, LEATH)
    pom_c = {1: LEATH_D, 2: STEEL, 3: STEEL, 4: GOLD_D, 5: GOLD}[level]
    P.fill("pommel", pom_c)
    P.fill("pommel", STEEL_D if level < 4 else GOLD_D, ("down", "east", "west"))
    P.put("pommel", "north", 0, 0, STEEL_L if level < 4 else GOLD_L)

    # ---- pièces d'équipement des niveaux 2 à 5 -----------------------------------------------------------------------------------
    if level >= 2:
        P.fill("helm", STEEL if level < 5 else STEEL_L)
        P.fill("helm", STEEL_L, ("up",))
        P.fill("helm", NONE, ("down",))
        P.band("helm", "north", 3, 4, trim_d if level >= 3 else STEEL_D)
        P.sides("helm", 3, 4, trim_d if level >= 3 else STEEL_D)
        if level >= 4:
            P.sides("helm", 3, 4, trim)
            P.put("helm", "north", 1, 1, STEEL_D)
            P.put("helm", "north", 6, 1, STEEL_D)
        P.tex("helm", STEEL_D, 4)
    if level >= 3:
        P.fill("neck", STEEL_D)
        P.sides("neck", 2, 3, trim_d)
        P.fill("cheek", STEEL)
        P.fill("cheek", STEEL_D, ("up", "down"))
        P.put("cheek", "east", 2, 1, STEEL_L)
        P.put("cheek", "west", 2, 1, STEEL_L)
    if level == 4:
        P.fill("crest_l", RED_L)
        P.fill("crest_l", RED_D, ("down", "east"))
        for f in ("north", "south", "east", "west"):
            x, y, w, h = P.faces("crest_l")[f]
            for i in range(w):
                for j in range(h):
                    if (i + j) % 3 == 0:
                        P.px[x + i, y + j] = RED_D
    if level == 5:
        P.fill("crest_t", RED_L)
        P.fill("crest_t", RED_D, ("down",))
        for f in ("north", "south", "east", "west", "up"):
            x, y, w, h = P.faces("crest_t")[f]
            for i in range(w):
                for j in range(h):
                    if (i + j) % 3 == 0:
                        P.px[x + i, y + j] = RED_D
        P.band("crest_t", "north", 3, 4, GOLD_D)
        P.band("crest_t", "south", 3, 4, GOLD_D)
    if level >= 4:
        ctrl = {4: STEEL, 5: STEEL_L}[level]
        for n in ("plate", "plate_w"):
            P.fill(n, ctrl)
            P.fill(n, STEEL_D, ("down",))
            P.fill(n, STEEL_L, ("up",))
            P.hlines(n, ("north", "south", "east", "west"), 2, STEEL_D, 1)
            if level == 5:
                P.band(n, "north", 0, 1, GOLD)
                P.band(n, "south", 0, 1, GOLD)
        for j in range(0, 6, 2):
            P.put("plate", "north", 0, j, GOLD if level == 5 else STEEL_D)
            P.put("plate", "north", 7, j, GOLD if level == 5 else STEEL_D)
        P.fill("paul", STEEL if level == 4 else STEEL_L)
        P.fill("paul", STEEL_L, ("up",))
        P.sides("paul", 1, 2, STEEL_D if level == 4 else GOLD)
        P.hlines("paul", ("north", "south", "east", "west"), 1, STEEL_D, 0) if level == 4 else None
        P.sides("paul", 1, 2, STEEL_D if level == 4 else GOLD)
        cape_c, cape_d = (c("a02a28"), c("6f1a1a")) if level == 4 else (c("8c1f26"), c("5a1219"))
        for n in ("cape", "cape_2"):
            P.fill(n, cape_c)
            P.tex(n, cape_d, 4)
        P.band("cape", "south", 0, 1, STEEL if level == 4 else GOLD)
        P.band("cape_2", "south", 7, 8, STEEL_D if level == 4 else GOLD_D)
        P.sides("cape_2", 7, 8, STEEL_D if level == 4 else GOLD_D)
    return P.img


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for lvl in range(1, 6):
        paint(lvl).save(os.path.join(OUT, "legionnaire_%d.png" % lvl))
    json.dump({n: {"uv": list(UV[n]), "size": list(PARTS[n])} for n in PARTS}, open(os.path.join(OUT, "uv.json"), "w"), indent=1)
    print("ok", W, H, UV)
