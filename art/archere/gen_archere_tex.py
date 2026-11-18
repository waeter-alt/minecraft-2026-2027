"""Atlas 64x128 de l'archère pour ses 5 niveaux (box UV Blockbench).

    python -I art/archere/gen_archere_tex.py art/archere/out

Écrit archere_1.png … archere_5.png (même mise en page, seule la peinture change) et uv.json.
Les pièces du corps gardent leurs offsets d'origine (niveau 1) ; les pièces d'équipement ajoutées par les niveaux
suivants (capuche, cape, casque, plastron…) vivent dans la moitié basse de l'atlas. Ce script ne sert qu'à amorcer
la peinture : la source de vérité reste art/archere/archere.bbmodel.
"""
import json
import sys
from PIL import Image

OUT = sys.argv[1]
W, H = 64, 128


def c(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


NONE = (0, 0, 0, 0)
SKIN, SKIN_D = c("f0c8aa"), c("d9a98a")
HAIR, HAIR_D, HAIR_L = c("7a3c22"), c("5c2a18"), c("a05a30")
CREAM = c("d6c8a4")
WOOD, WOOD_D = c("966434"), c("6e4824")
STRING, RED = c("e6e6d7"), c("aa3228")
SHAFT = c("c8aa6e")
GOLD, GOLD_D = c("d4b24a"), c("9a7b26")
STEEL, STEEL_D, STEEL_L = c("a9b0b8"), c("6e757e"), c("d3d9de")
CRIMSON, CRIMSON_D = c("8f2a2a"), c("5f1a1a")

# nom: (w, h, d), uv fixe (niveau 1) ou None = rangé dans la moitié basse
PARTS = {
    "head": ((8, 8, 8), (0, 0)), "hair": ((8, 8, 8), (32, 0)),
    "ponytail_1": ((3, 5, 3), (24, 50)), "ponytail_2": ((2, 6, 2), (36, 50)),
    "chest": ((8, 6, 4), (40, 16)), "waist": ((7, 6, 4), (0, 30)), "skirt": ((9, 5, 5), (12, 16)),
    "r_arm": ((3, 6, 3), (32, 40)), "r_fore": ((3, 6, 3), (44, 40)),
    "l_arm": ((3, 6, 3), (0, 50)), "l_fore": ((3, 6, 3), (12, 50)),
    "r_thigh": ((4, 6, 4), (22, 30)), "r_shin": ((4, 6, 4), (38, 30)),
    "l_thigh": ((4, 6, 4), (0, 40)), "l_shin": ((4, 6, 4), (16, 40)),
    "quiver": ((3, 11, 3), (0, 16)), "fletch": ((2, 3, 2), (54, 50)), "pouch": ((3, 3, 2), (44, 50)),
    "bow": ((1, 4, 1), (0, 59)), "string": ((1, 1, 1), (4, 59)), "arrow": ((1, 1, 1), (8, 59)),
    # équipement des niveaux 2 à 5
    "hood": ((8, 8, 8), (0, 64)), "helm": ((8, 4, 8), (32, 64)),
    "plate": ((8, 6, 4), (0, 80)), "cape": ((9, 10, 1), (24, 80)), "paul": ((4, 2, 4), (44, 80)),
    "cape_2": ((9, 8, 1), (0, 91)), "plume": ((1, 4, 7), (20, 91)), "nose": ((1, 4, 1), (36, 91)),
    "bowdeco": ((1, 1, 1), (40, 91)),
}
SIZE = {n: v[0] for n, v in PARTS.items()}
UV = {n: v[1] for n, v in PARTS.items()}


def check_overlaps():
    boxes = []
    for n, (w, h, d) in SIZE.items():
        u, v = UV[n]
        boxes.append((n, u, v, u + 2 * d + 2 * w, v + d + h))
    for i, a in enumerate(boxes):
        assert a[3] <= W and a[4] <= H, a
        for b in boxes[i + 1:]:
            if a[1] < b[3] and b[1] < a[3] and a[2] < b[4] and b[2] < a[4]:
                raise SystemExit("chevauchement UV : %s / %s" % (a[0], b[0]))


check_overlaps()


class Painter:
    def __init__(self):
        self.img = Image.new("RGBA", (W, H), NONE)
        self.px = self.img.load()

    def faces(self, n):
        w, h, d = SIZE[n]
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
        """Fine trame de tissu/cuir : un pixel sur `step` un peu plus sombre (mélange à 40 %), pour casser les aplats."""
        for k, (x, y, w, h) in self.faces(n).items():
            for i in range(w):
                for j in range(h):
                    base = self.px[x + i, y + j]
                    if base[3] and (i * 2 + j * 3) % (step + 2) == 0:
                        self.px[x + i, y + j] = tuple(int(base[m] * 0.6 + col_b[m] * 0.4) for m in range(3)) + (255,)


# palette par niveau : tunique (3 tons), cuir, braies
LEVELS = {
    1: dict(tun=(c("3e6a3a"), c("2c4e2a"), c("568a4c")), leath=(c("7e5630"), c("583a20"), c("9e7246")), pants=(c("5a5040"), c("463e30"))),
    2: dict(tun=(c("37603a"), c("27452a"), c("4f7d4c")), leath=(c("8a5c32"), c("5e3d21"), c("ab7a48")), pants=(c("56402e"), c("40301f"))),
    3: dict(tun=(c("34553a"), c("243c29"), c("4a6e4c")), leath=(c("6e4c2c"), c("4c3320"), c("8c6640")), pants=(c("3f4838"), c("2d352a"))),
    4: dict(tun=(c("2f5236"), c("203a26"), c("46704a")), leath=(c("5e4128"), c("40291a"), c("7c5a38")), pants=(c("383c3a"), c("272a29"))),
    5: dict(tun=(c("27452f"), c("1a3022"), c("3d6542")), leath=(c("2f241d"), c("1d1511"), c("4d3b2d")), pants=(c("2d302e"), c("1f2120"))),
}


def paint(level):
    P = Painter()
    GREEN, GREEN_D, GREEN_L = LEVELS[level]["tun"]
    LEATH, LEATH_D, LEATH_L = LEVELS[level]["leath"]
    PANT, PANT_D = LEVELS[level]["pants"]
    plated = level >= 4
    mail = level >= 4

    # ---- tête et visage (identique à tous les niveaux : c'est la même femme) ------------------
    P.fill("head", SKIN)
    P.fill("head", SKIN_D, ("down",))
    for dx, dy, col in [(1, 4, c("ffffff")), (2, 4, c("2e6b4a")), (5, 4, c("2e6b4a")), (6, 4, c("ffffff")),
                        (1, 3, c("3a2216")), (2, 3, c("3a2216")), (5, 3, c("3a2216")), (6, 3, c("3a2216")),
                        (3, 6, c("c0605a")), (4, 6, c("c0605a")), (3, 5, SKIN_D), (4, 5, SKIN_D),
                        (0, 5, c("e9a89a")), (7, 5, c("e9a89a"))]:
        P.put("head", "north", dx, dy, col)

    # ---- cheveux : calotte + frange, visage dégagé ----------------------------------------------
    fx, fy, fw, fh = P.faces("hair")["up"]
    P.rect(fx, fy, fw, fh, HAIR)
    for i in range(fw):
        for j in range(fh):
            if (i * 3 + j * 5) % 7 == 0:
                P.px[fx + i, fy + j] = HAIR_L
    bx, by, bw, bh = P.faces("hair")["south"]
    P.rect(bx, by, bw, bh, HAIR)
    for i in range(bw):
        for j in range(bh):
            if (i * 2 + j * 3) % 5 == 0:
                P.px[bx + i, by + j] = HAIR_D
    nx, ny, nw, nh = P.faces("hair")["north"]
    P.rect(nx, ny, nw, 2, HAIR)
    for dx in (0, 1, 6, 7):
        P.rect(nx + dx, ny + 2, 1, 5, HAIR)
    P.px[nx + 2, ny + 2] = HAIR
    P.px[nx + 5, ny + 2] = HAIR
    P.px[nx + 3, ny + 2] = HAIR_L
    for side, back_cols in (("east", range(0, 5)), ("west", range(3, 8))):
        sx, sy, sw, sh = P.faces("hair")[side]
        P.rect(sx, sy, sw, 3, HAIR)
        for i in back_cols:
            P.rect(sx + i, sy + 3, 1, 5, HAIR_D if i % 2 else HAIR)

    # ---- queue de cheval ---------------------------------------------------------------------------
    for n in ("ponytail_1", "ponytail_2"):
        P.fill(n, HAIR)
        for k, (x, y, w, h) in P.faces(n).items():
            for j in range(h):
                if j % 2 == 0:
                    P.rect(x, y + j, w, 1, HAIR_D)
    ribbon = {1: GREEN_L, 2: LEATH_L, 3: LEATH_L, 4: STEEL, 5: GOLD}[level]
    P.sides("ponytail_1", 0, 1, ribbon)

    # ---- torse -----------------------------------------------------------------------------------
    P.fill("chest", GREEN)
    P.fill("chest", GREEN_D, ("down",))
    P.fill("chest", GREEN_L, ("up",))
    P.band("chest", "north", 0, 1, CREAM)
    P.band("chest", "south", 0, 1, GREEN_D)
    if level == 1:
        for j in range(1, 6):
            for dx in (3, 4):
                P.put("chest", "north", dx, j, LEATH_L if j % 2 else LEATH)
        for i in range(6):
            P.put("chest", "north", i + 1 if i < 3 else i, i, LEATH_D)
            P.put("chest", "south", 7 - i, i, LEATH_D)
    elif level == 2:
        # gilet de cuir lacé par-dessus la tunique
        for f in ("north", "south"):
            P.rect(*(lambda x, y, w, h: (x + 1, y + 1, w - 2, h - 1))(*P.faces("chest")[f]), LEATH)
        for j in range(1, 6):
            for dx in (3, 4):
                P.put("chest", "north", dx, j, CREAM if j % 2 else LEATH_D)
        P.sides("chest", 5, 6, LEATH_D)
        for i in range(6):
            P.put("chest", "south", 7 - i, i, LEATH_D)
    else:
        for j in range(1, 6):
            for dx in (3, 4):
                P.put("chest", "north", dx, j, LEATH_L if j % 2 else LEATH)
        for i in range(6):
            P.put("chest", "north", i + 1 if i < 3 else i, i, LEATH_D)
            P.put("chest", "south", 7 - i, i, LEATH_D)
    P.fill("waist", GREEN_D)
    P.fill("waist", LEATH, ("down",))
    belt_col = GOLD_D if level == 5 else LEATH_D
    P.band("waist", "north", 3, 6, GREEN_D)
    P.sides("waist", 4, 6, belt_col)
    buckle = {1: c("c9b04a"), 2: c("c9b04a"), 3: STEEL, 4: STEEL_L, 5: GOLD}[level]
    for dx in (3, 4):
        P.put("waist", "north", dx, 4, buckle)
        P.put("waist", "north", dx, 5, buckle)
    P.fill("skirt", GREEN)
    P.fill("skirt", GREEN_D, ("up", "down"))
    hem = GOLD if level == 5 else CREAM
    for f in ("north", "south", "east", "west"):
        P.band("skirt", f, 4, 5, hem)
        P.band("skirt", f, 3, 4, GREEN_L)
    for dx in (3, 4, 5):
        P.put("skirt", "north", dx, 0, GREEN_D)
    P.tex("chest", GREEN_D)
    P.tex("skirt", GREEN_D, 4)

    # ---- bras : manches vertes (haut), avant-bras nus + brassards ------------------------------------
    for n in ("r_arm", "l_arm"):
        P.fill(n, GREEN)
        P.fill(n, GREEN_D, ("down",))
        P.sides(n, 4, 6, GREEN_D)
        P.fill(n, GREEN_L, ("up",))
    for n in ("r_fore", "l_fore"):
        P.fill(n, SKIN)
        P.fill(n, SKIN_D, ("down",))
        if plated:
            P.sides(n, 0, 4, STEEL)
            P.sides(n, 3, 4, STEEL_D)
            P.sides(n, 0, 1, GOLD if level == 5 else STEEL_L)
            P.fill(n, STEEL, ("up",))
            if level == 5:
                P.sides(n, 2, 3, GOLD)
        else:
            P.sides(n, 0, 3, LEATH)
            P.sides(n, 2, 3, LEATH_D)
            P.fill(n, LEATH, ("up",))
            if level >= 2:
                P.put(n, "north", 1, 1, LEATH_L)  # rivet
        if level >= 3:
            P.sides(n, 4, 6, LEATH)  # gants

    # ---- jambes : braies + bottes -----------------------------------------------------------------------
    for n in ("r_thigh", "l_thigh"):
        P.fill(n, PANT)
        P.fill(n, PANT_D, ("up", "down"))
        P.sides(n, 5, 6, PANT_D)
    for n in ("r_shin", "l_shin"):
        P.fill(n, PANT)
        P.sides(n, 2, 6, LEATH)
        P.sides(n, 2, 3, GOLD_D if level == 5 else LEATH_L)
        P.sides(n, 5, 6, LEATH_D)
        if plated:
            P.sides(n, 0, 2, STEEL)       # genouillère / grève
            P.sides(n, 1, 2, STEEL_D)
        P.fill(n, LEATH_D, ("down",))
        P.fill(n, PANT_D, ("up",))

    # ---- carquois, empennage, bourse ----------------------------------------------------------------------
    P.fill("quiver", LEATH)
    P.fill("quiver", LEATH_D, ("up", "down", "east", "west"))
    for f in ("south", "north"):
        P.band("quiver", f, 0, 1, LEATH_D)
        P.band("quiver", f, 5, 6, LEATH_D)
        if level >= 4:
            P.band("quiver", f, 10, 11, STEEL if level == 4 else GOLD)
    P.fill("fletch", RED if level < 5 else CRIMSON)
    for f in ("east", "west"):
        P.band("fletch", f, 0, 3, c("e8e0d0"))
    P.fill("pouch", LEATH_L)
    P.fill("pouch", LEATH_D, ("up", "down", "east", "west"))
    P.band("pouch", "north", 0, 1, LEATH_D)

    # ---- arc, corde, flèche, ornement --------------------------------------------------------------------------
    P.fill("bow", WOOD if level < 4 else c("6e4a2a"))
    P.fill("bow", WOOD_D, ("up", "down"))
    for i in (1, 2):
        P.put("bow", "north", 0, i, WOOD_D)
    if level == 5:
        P.band("bow", "north", 0, 1, GOLD)
        P.band("bow", "north", 3, 4, GOLD)
    P.fill("string", STRING)
    P.fill("arrow", SHAFT)
    P.fill("bowdeco", STEEL if level == 4 else GOLD)

    # ---- équipement par niveau ------------------------------------------------------------------------------------
    if level >= 2:
        # épaulière de cuir (n2) / d'acier (n4) / dorée (n5)
        col = LEATH if level in (2, 3) else STEEL
        P.fill("paul", col)
        P.fill("paul", col, ("up",))
        P.sides("paul", 1, 2, LEATH_D if level < 4 else STEEL_D)
        if level == 2:
            P.put("paul", "north", 1, 0, LEATH_L)
            P.put("paul", "north", 2, 0, LEATH_L)
        if level == 5:
            P.sides("paul", 0, 1, GOLD)
            P.fill("paul", STEEL_L, ("up",))
    if level == 3:
        moss, moss_d, moss_l = c("4f6a4a"), c("384e36"), c("667f5e")
        # capuche : visage dégagé, tissu mousse
        P.fill("hood", moss)
        P.fill("hood", moss_d, ("down",))
        nx, ny, nw, nh = P.faces("hood")["north"]
        P.rect(nx, ny, nw, nh, NONE)
        P.rect(nx, ny, nw, 2, moss_l)
        for dx in (0, 1, 6, 7):
            P.rect(nx + dx, ny + 2, 1, 6, moss)
        P.rect(nx, ny + 7, nw, 1, moss_d)
        P.fill("hood", NONE, ("down",))
        P.tex("hood", moss_d, 4)
        P.fill("cape", moss)
        P.band("cape", "south", 9, 10, moss_d)
        P.sides("cape", 9, 10, moss_d)
        P.tex("cape", moss_d, 4)
    if level == 4:
        cape, cape_d = c("2c5a38"), c("1b3b24")
        for n in ("cape", "cape_2"):
            P.fill(n, cape)
            P.tex(n, cape_d, 4)
        P.band("cape", "south", 0, 1, STEEL)
        P.band("cape_2", "south", 7, 8, STEEL_D)
        P.sides("cape_2", 7, 8, STEEL_D)
        P.fill("plate", STEEL)
        P.fill("plate", STEEL_D, ("down",))
        P.fill("plate", STEEL_L, ("up",))
        P.band("plate", "north", 0, 1, STEEL_L)
        for j in (1, 2, 3, 4, 5):
            P.put("plate", "north", 3, j, STEEL_D)
            P.put("plate", "north", 4, j, STEEL_D)
        P.band("plate", "north", 4, 5, STEEL_D)
        P.sides("plate", 5, 6, STEEL_D)
        for i in range(8):
            P.put("plate", "south", i, 2, STEEL_D)
    if level == 5:
        for n in ("cape", "cape_2"):
            P.fill(n, CRIMSON)
            P.tex(n, CRIMSON_D, 4)
        P.band("cape", "south", 0, 1, GOLD)
        P.band("cape_2", "south", 6, 8, GOLD_D)
        P.sides("cape_2", 7, 8, GOLD)
        P.sides("cape", 0, 1, GOLD)
        P.fill("plate", STEEL)
        P.fill("plate", STEEL_L, ("up",))
        P.band("plate", "north", 1, 2, STEEL_L)
        P.put("plate", "north", 1, 3, STEEL_L)
        P.put("plate", "north", 6, 3, STEEL_L)
        P.sides("plate", 0, 1, GOLD)
        P.sides("plate", 5, 6, GOLD)
        for j in (1, 2, 3, 4):
            P.put("plate", "north", 3, j, GOLD)
            P.put("plate", "north", 4, j, GOLD)
        P.put("plate", "north", 1, 2, GOLD_D)
        P.put("plate", "north", 6, 2, GOLD_D)
        P.fill("helm", STEEL)
        P.fill("helm", STEEL_L, ("up",))
        for dy in (0, 1):
            P.put("helm", "north", 1, dy, STEEL_D)
            P.put("helm", "north", 6, dy, STEEL_D)
        P.fill("helm", NONE, ("down",))
        P.band("helm", "north", 3, 4, GOLD)
        P.sides("helm", 3, 4, GOLD)
        for i in range(8):
            P.put("helm", "up", i, 3, STEEL_D)
        P.fill("plume", CRIMSON)
        P.fill("plume", CRIMSON_D, ("down",))
        for k, (x, y, w, h) in P.faces("plume").items():
            for i in range(w):
                for j in range(h):
                    if (i + j) % 3 == 0:
                        P.px[x + i, y + j] = CRIMSON_D
        P.fill("nose", STEEL)
    return P.img


if __name__ == "__main__":
    import os
    os.makedirs(OUT, exist_ok=True)
    for lvl in LEVELS:
        paint(lvl).save(os.path.join(OUT, "archere_%d.png" % lvl))
    json.dump({n: list(UV[n]) for n in PARTS}, open(os.path.join(OUT, "uv.json"), "w"), indent=1)
    print("ok", W, H)
