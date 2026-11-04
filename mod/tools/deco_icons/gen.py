"""
Generates the catalogue icons and names of every decoration, style by style, so that no two decorations of one style share
an icon or a name.

  python -I tools/deco_icons/gen.py <minecolonies.jar> <minecraft-resources.jar> <1-bit_Pixel_Icons folder>

Per decoration, in this order:
  1. a pixel-art icon from Nikoichu's 1-bit Pixel Icons (CC0) when a word of its name has a meaning we know;
  2. the Minecraft item from catalogue_names.json, when no other decoration of the style uses it;
  3. a picture drawn from the blueprint itself: seen from above, or from the front for thin pieces (facades, gates).
Modular pieces (roads, rails, walls…) always take the picture from above, with their family's icon in a corner.

Writes assets/interregne/deco_icons.json and one atlas per style, textures/gui/deco/<style>.png, cells of 32 × 32.
"""
import collections, hashlib, io, json, os, re, sys, zipfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nbt  # noqa: E402

MOD = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(MOD, "src", "main", "resources", "assets", "interregne")
CELL = 32
INK = (74, 42, 16)
CREAM = (243, 230, 196)

jar_path, res_path, pack_dir = sys.argv[1:4]
jar = zipfile.ZipFile(jar_path)
res = zipfile.ZipFile(res_path)
res_names = set(res.namelist())
SPRITES = {f[:-4] for f in os.listdir(os.path.join(pack_dir, "Sprites")) if f.endswith(".png")}
NAMES = json.load(open(os.path.join(ASSETS, "catalogue_names.json"), encoding="utf-8"))


def sprite(part):
    found = sorted((s for s in SPRITES if part in s), key=len)
    assert found, part
    return found[0]


# --- 1. Words with a meaning ---------------------------------------------------------------------------------------
RULES = [
    (r"windmill|flourmill", ["Windmill_Farms"]),
    (r"lighthouse", ["Building_Lighthouse"]),
    (r"fountain", ["Fountain_Park_Water"]),
    (r"well", ["Tools_Crafting_Bucket", "Food_Drink_Glass_Water"]),
    (r"kongming", ["RPG_Item_Lantern_Lamp_Light"]),
    (r"minimarket|market|stall|shop|agora", ["Building_Shop_Storefront", "Store_Shopping_Bag_1", "Store_Shopping_Bag_2", "Price_Tag_Store",
                                              "Price_Tag_Store_Dollar", "Software_Shopping_Basket"]),
    (r"lantern|lamp|light", ["Lamp_Post_1", "Lamp_Post_2", "Lamp_Post_3", "RPG_Item_Lantern_Lamp_Light", "Weapon_Torch_Light"]),
    (r"grave|mausoleum|tomb", ["Gravestone_Death_1", "Gravestone_Death_2", "Gravestone_Death_3", "Gravestone_Death_4", "Gravestone_Death_5"]),
    (r"gallows|pillory|headsman|cage", ["Cage_Prison_Bars_Fence", "Closed_Bars_Prison", "Judge_Gavel"]),
    (r"norsemen|viking", ["Viking_Raiding"]),
    (r"pirate", ["Ship_Pirate_Flag_Black", "Ship_Pirate_Flag", "Ship_Pirate_Hook"]),
    (r"sunk", ["Ship_Anchor_Navy"]),
    (r"ship|bireme|trireme|cargo|boat", ["Medieval_Caravel", "Medieval_Galleon", "Sailing_Boat", "Fishing_Boat", "Ship_Sailing_Wheel", "Sextant"]),
    (r"jetty|pier|dock|harbor", ["Travel_Ship_Dock", "Ship_Anchor_Navy"]),
    (r"buoy", ["Lifebuoy"]),
    (r"crane|shipwright", ["Construction_Crane_Building", "Construction_Crane_Hook"]),
    (r"encampment|war_", ["Siege_Engine_Tower", "Crossed_Swords_Shield"]),
    (r"camp", ["Tent_Campsite", "Campfire_Camping", "Tent_Yurt"]),
    (r"cart|wagon|carriage", ["Caravan_Carriage_Cart", "Travel_Carriage_Cart", "Wheelbarrow_Hand_Cart_1", "Wheelbarrow_Hand_Cart_2"]),
    (r"stable", ["Travel_Horse_Riding", "Horseback_riding_Saddle"]),
    (r"kennel", ["Pet_Paw_Animal_Handling_Tame_Beast_Mastery"]),
    (r"guard", ["Outpost_Tower_Lookout", "Knight_Helmet_Armor_Medieval", "Soldier_Helm"]),
    (r"tower", ["Building_Tower_Roof", "Building_Tower", "Outpost_Tower_Lookout"]),
    (r"gate", ["Stone_Wall_Gate_Door", "Exit_Open", "Exit_Door"]),
    (r"arch", ["Enterance_Exit_Doorway", "Exit_Open"]),
    (r"buddha", ["Religion_Buddha"]),
    (r"knight|swordshield|wallsword|sword", ["Chess_Piece_Knight_Big", "Crossed_Swords_Shield", "Weapon_Shortsword"]),
    (r"king", ["Chess_Piece_King_Big", "Crown_King"]),
    (r"poseidon", ["Chess_Piece_Queen_Big", "Water_Sea_Ocean_Waves"]),
    (r"statue|lion|pedestal|pedastal|pillar|column", ["Greek_Ruins_Column", "Chess_Piece_Rook_Big", "Chess_Piece_Bishop_Big", "Chess_Piece_Pawn",
                                                       "Chess_Piece_Queen_Small", "Chess_Piece_King_Small", "Chess_Piece_Rook_Small",
                                                       "Chess_Piece_Bishop_Small", "Chess_Piece_Knight_Small"]),
    (r"yinyang", ["Yin_Yang_Balance_Big", "Yin_Yang_Balance_Small"]),
    (r"temple|shrine|purification", ["Building_Church_Faith_Temple", "Bank_Greek_Temple"]),
    (r"waystone|portal|nether_gate", ["RPG_Teleportation_Stone", "Magic_Mana_Hearth_Stone"]),
    (r"apiary", ["RPG_Creature_Archetypes_Insect_Spider"]),
    (r"greenhouse|garden|flower|wisteria", ["Rose_Flower_Decoration", "Weather_Flower_Spring_Season", "Flower_Snowdrop"]),
    (r"tree|bush|hedge|park|vegetation", ["Map_Markers_Tree_Forest_Decidous_1", "Map_Markers_Tree_Forest_Decidous_2", "Tree_Forest_Pine",
                                           "Tree_Forest_Decidous_Branches", "Map_Markers_Tree_Forest", "Tree_Forest_Palm"]),
    (r"oasis|beach", ["Tree_Forest_Palm_Beach", "Beach_Umbrella"]),
    (r"waterfall|pond|aquarium|kelp|sea|water", ["Map_Markers_Waterfall", "Water_Sea_Ocean_Waves", "Water_Droplet_Liquid_Rain_Element_Big"]),
    (r"lavafall|magma|volcano", ["Map_Markers_Volcano", "Wildfire_Flame"]),
    (r"field|farm|crop|wheat", ["Farmland_Crops_1", "Farmland_Crops_2", "Farming_Plant_Crop", "Food_Grain_Crop_Plant", "Farmland_Barn"]),
    (r"smelter|forge", ["Smithing_Forge_Bellows", "Smithing_Anvil_Hammer"]),
    (r"concrete", ["Trowel_Brick_Laying"]),
    (r"meat|grinder", ["Meat_Chicken_Leg"]),
    (r"cafe|tavern", ["Beer_Mug_Tavern", "Coffee_Cafe_Tea"]),
    (r"concert", ["Instrument_Harp_Lyre", "Instrument_Drum"]),
    (r"playground|playset|volleyball", ["Net_Tennis_Volleyball", "Sports_Volleyball"]),
    (r"icerink", ["Ice_Skating_Skates"]),
    (r"bathhouse", ["Water_Droplet_Liquid_Rain_Element_Small", "Air_Bubble_Breath"]),
    (r"citysquare|towncenter|centertower|plaza", ["Buildings_Houses_Homes_Village_Town", "Map_Markers_Flagpole", "Banner_Heraldry_Hanging"]),
    (r"house|home|mansion|villa|estate|bag_end|hut", ["Building_Home_House", "Buildings_Houses_Homes_Village_Town", "Building_Tribal_Hut"]),
    (r"sewer", ["Sewer_Pipes_Crossed_1"]),
    (r"pharao|pyramid", ["Egypt_Pyramid", "Egypt_Ankh"]),
    (r"dwarf", ["Miner_Mining_Helmet"]),
    (r"end", ["Weather_Moon_Night_Crescent"]),
]
RULES = [(re.compile(rx), [sprite(p) for p in parts]) for rx, parts in RULES]
# Words that name a size, a material or a variant, never what the piece is
GENERIC_ITEMS = re.compile(r"stairs|slab|structure_void|stone_bricks|cobblestone$|^minecraft:stone$|planks|bricks$|_wall$|dirt|gravel|sand$")

COLOURS = {"white": (240, 240, 240), "lightgray": (170, 170, 170), "gray": (110, 110, 110), "black": (40, 40, 40), "brown": (130, 85, 50),
           "red": (190, 50, 40), "orange": (225, 130, 40), "yellow": (230, 200, 50), "lime": (130, 200, 50), "green": (70, 130, 50),
           "cyan": (40, 150, 160), "lightblue": (100, 170, 230), "blue": (50, 80, 180), "purple": (130, 60, 170), "magenta": (190, 70, 170),
           "pink": (230, 140, 170)}

# --- Modular families ------------------------------------------------------------------------------------------------
FAMILIES = [
    ("alley", "Ruelle", "Footsteps_Shoes_Tracks"), ("avenue", "Avenue", "Travel_Roadway_Straight"), ("street", "Rue", "Roadway_Turn_Corner"),
    ("birail", "Double rail", "Rails_Big"), ("monorail", "Monorail", "Rails_Small_Bend"), ("subway", "Métro", "Rails_Tunnel"),
    ("rail", "Rail", "Railroad_Train_Rails_Small"), ("mineshaft", "Puits de mine", "Travel_Mining_Minecart_Small_Full"),
    ("bridge", "Pont", "Travel_Bridge_1"), ("aquaduct", "Aqueduc", "Travel_Bridge_3"), ("canal", "Canal", "Water_Sea_Ocean_Waves"),
    ("jetty", "Jetée", "Travel_Ship_Dock"), ("sewage", "Égout", "Sewer_Pipes_Horizontal"), ("cliffclimb", "Escalade", "Mountains_Hills_Cliffs"),
    ("stairs", "Escalier", "Map_Markers_Stairs"), ("fields", "Champ", "Farmland_Crops_1"), ("market", "Marché", "Price_Tag_Store"),
    ("park", "Parc", "Tree_Forest_Decidous_1"), ("plaza", "Place", "Buildings_Houses_Homes_Village_Town"),
    ("hall", "Halle", "Building_Bank_Greek_Temple"), ("player_spaces", "Terrain libre", "Location_Pin_Big"), ("gates", "Porte", "Stone_Wall_Gate_Door"),
    ("crossroad", "Carrefour", "Signpost_Double"), ("intersection", "Carrefour", "Signpost_Double"), ("connector", "Raccord", "Signpost"),
    ("narrow", "Sentier", "Footstep_Tracks"), ("seawall", "Digue", "Ship_Anchor_Navy"), ("retaining_wall", "Mur de soutènement", "Bricks_Wall_Big_1"),
    ("platforms", "Plateforme", "Bricks_Wall_Big_2"), ("paths", "Quai", "Travel_Ship_Dock"), ("road", "Route", "Map_Markers_Signpost"), ("misc", "Pièce", "Map_Markers_Signpost"),
]
WALL_FAMILIES = [
    ("corner", "Angle de muraille", "Building_Tower"), ("tower", "Tour de muraille", "Building_Tower_Roof"), ("gate", "Porte de muraille", "Stone_Wall_Gate_Door"),
    ("stairs", "Escalier de muraille", "Map_Markers_Stairs"), ("palisade", "Palissade", "Wooden_Palisade"), ("fence", "Clôture", "Animals_Penned_Fence"),
    ("moat", "Douves", "Water_Sea_Ocean_Waves"), ("", "Muraille", "Stone_Wall_Fortification"),
]
QUALIFIERS = {"brick": "en brique", "cobble": "en pavé", "fancy": "ornée", "rural": "rurale", "urban": "urbaine", "basic": "simple", "sturdy": "solide",
              "planks": "en planches", "stone": "en pierre", "wood": "en bois", "deep": "profond", "shoal": "peu profond", "shallow": "peu profond",
              "nice": "soignée", "poor": "pauvre", "plain": "unie", "wide": "large", "path": "chemin", "inland": "intérieure", "waterfront": "du port",
              "grand_processional": "de procession", "lampposts": "éclairée", "simple": "simple", "advanced": "avancé", "premium": "de luxe",
              "overground": "aérien", "tunnel": "en tunnel", "tunnelexit": "sortie de tunnel", "big_walls": "haute", "tall": "haute", "city": "de ville",
              "clay": "en argile", "fortress": "de forteresse", "oak": "en chêne", "original": "", "base": "fondations", "segment": "", "alley": "ruelle",
              "road": "", "streets": "", "rails": "", "walls": "", "wall": "", "misc": "", "roads": "", "mineshafts": "", "markets": "", "parks": "",
              "barracks": "de caserne", "harbor": "", "short": "basse", "sewage": "",
              "moat": "", "aqueduct": "", "aquaducts": "", "underground": "souterraine", "surface": "en surface", "decorations": "",
              "canals": "", "seawall": "", "paths": "", "platforms": "", "retaining_wall": "", "intersection_deco": "de carrefour"}
WORDS = {"cross": "croisement", "tee": "en T", "turn": "virage", "corner": "angle", "long": "longue", "medium": "moyenne", "med": "moyenne",
         "short": "courte", "down": "pente", "up": "montée", "plug": "raccord", "plub": "raccord", "end": "bout", "station": "gare", "trans": "vers",
         "nature": "nature", "diagonal": "diagonale", "tower": "tour", "side": "côté", "double": "double", "single": "simple", "house": "avec maison",
         "stair": "en escalier", "stairs": "en escalier", "straight": "droite", "intersection": "carrefour", "drop": "chute", "high": "haute",
         "low": "basse", "gate": "écluse", "bridge": "pont", "large": "grande", "small": "petite", "big": "grande", "lrg": "grande", "sml": "petite",
         "mid": "moyenne", "slope": "pente", "sloped": "en pente", "curve": "courbe", "left": "gauche", "right": "droite", "top": "haut",
         "lower": "bas", "upper": "haut", "entrance": "entrée", "exit": "sortie", "l": "longue", "m": "moyenne", "s": "courte", "x": "croisement",
         "t": "en T", "minermainshaft": "puits principal", "minerx": "galerie en croix", "minerx2left": "galerie, 2 sorties gauche",
         "minerx2right": "galerie, 2 sorties droite", "minerx2top": "galerie, 2 sorties haut", "minerx3leftright": "galerie, 3 sorties G-D",
         "minerx3topleft": "galerie, 3 sorties H-G", "minerx3topright": "galerie, 3 sorties H-D", "minerx4": "galerie, 4 sorties"}


SEGMENTS = {"athenian": "athénienne", "mycanean": "mycénienne", "spartan": "spartiate", "beigestone": "en pierre beige", "blackstone": "en pierre noire",
            "brownstone": "en pierre brune", "deepslate": "en ardoise", "stone": "en pierre", "deep": "profond", "shallow": "peu profond",
            "decorative": "décorative", "misc": "", "gardens": "", "parks": "de parc", "supplies": "de départ", "small_deco": "", "deco": "",
            "lights": "", "lanterns": "", "statues": "", "vegetation": "", "markets": "de marché", "plaza": "de place"}


def family(folder):
    parts = folder.split("/")
    if parts[0] == "walls":
        sub = "/".join(parts[1:])
        for key, name, icon in WALL_FAMILIES:
            if key in sub:
                return name, sprite(icon), [p for p in parts[1:] if key not in p or not key]
    else:
        sub = "/".join(parts[1:])
        for key, name, icon in FAMILIES:
            if key in sub:
                return name, sprite(icon), [p for p in parts[1:] if key not in p]
        if len(parts) == 1:
            return "Pièce", sprite("Map_Markers_Signpost"), []
    return None


WORDS.update({"fancy": "ornée", "nice": "soignée", "powered": "alimenté", "unpowered": "sans courant", "plain": "unie", "section": "tronçon",
              "segment": "tronçon", "tjunction": "en T", "junction": "jonction", "vertical": "verticale", "inner": "intérieur", "outer": "extérieur",
              "covered": "couverte", "span": "travée", "stepup": "marche", "step": "marche", "pillar": "pilier", "pillars": "piliers", "incline": "pente",
              "poor": "pauvre", "start": "début", "beginning": "début", "stone": "pierre", "support": "appui", "cobble": "pavé", "round": "ronde",
              "square": "carrée", "wooden": "bois", "wood": "bois", "lock": "écluse", "solid": "pleine", "gatehouse": "corps de garde", "field": "champ",
              "bottom": "bas", "middle": "milieu", "barbican": "barbacane", "medal": "médaillon", "basic": "simple", "steep": "raide",
              "connector": "raccord", "deepslate": "ardoise", "narrow": "étroite", "hatch": "trappe", "glowing": "éclairée", "dual": "double",
              "quad": "quadruple", "bumpout": "avancée", "head": "tête", "by": "x", "tree": "arbre", "light": "lumière", "workshop": "atelier",
              "gradual": "douce", "ladder": "échelle", "ladders": "échelles", "stilts": "pilotis", "flat": "plate", "farm": "ferme", "climb": "escalade",
              "rails": "rails", "rail": "rail", "endcap": "bout", "odd": "impaire", "even": "paire", "platform": "plateforme", "footing": "semelle",
              "lamp": "lampe", "panel": "panneau", "floating": "flottante", "ramp": "rampe", "bench": "banc", "bush": "buisson", "path": "chemin",
              "simplequarry": "petite carrière", "mediumquarry": "grande carrière", "shaft": "puits", "simple": "simple", "fence": "", "wall": "",
              "road": "", "roads": "", "palisade": "", "base": "fondations", "brick": "brique", "bricks": "briques", "mossy": "moussue", "half": "demi",
              "full": "entière", "open": "ouverte", "closed": "fermée", "center": "centre", "edge": "bord", "north": "nord", "south": "sud",
              "east": "est", "west": "ouest", "inside": "intérieur", "outside": "extérieur", "stairway": "escalier", "steps": "marches", "farmer": "fermier", "forestry": "foresterie", "geology": "géologie", "hall": "halle", "sand": "sable", "raised": "surélevée", "tjumction": "en T", "underground": "souterraine",
              "walkway": "passerelle", "walk": "passerelle", "safety": "garde-corps", "crane": "grue", "claw": "à grappin", "naval": "naval",
              "surface": "en surface", "green": "verte", "deco": "", "intersect": "carrefour", "increase": "montante", "inverted": "inversée",
              "defense": "défense", "redoubt": "redoute", "level": "niveau", "both": "double", "one": "1", "two": "2", "spiral": "en spirale",
              "track": "voie", "case": "cas", "batpole": "mât", "patrolpoint": "point de patrouille", "waypoint": "balise", "dig": "fouille",
              "egment": "tronçon", "tep": "marche", "insert": "insert", "post": "poste", "magma": "magma", "mini": "mini", "tiny": "minuscule",
              "military": "militaire", "storage": "réserve", "canalwaterfall": "cascade", "starter": "départ", "return": "retour", "hedge": "haie", "maze": "labyrinthe", "ground": "au sol", "crossing": "croisement",
              "perpendicular": "perpendiculaires", "parallel": "parallèles", "way": "voies", "threeway": "à 3 voies", "fourway": "à 4 voies",
              "twoway": "à 2 voies", "jog": "décalage", "foot": "piétonne", "hill": "colline", "curved": "courbe", "tasty": "des cuisines",
              "herder": "des éleveurs", "mystic": "mystique", "scholars": "des savants", "scorched": "brûlée", "town": "de ville",
              "cliff": "falaise", "landing": "palier", "enter": "entrée", "column": "colonne", "alt": "variante", "subway": "", "uturn": "demi-tour",
              "split": "séparation", "block": "bloc", "three": "3", "ushaped": "en U", "leaves": "feuillage", "player": "joueur", "cavern": "",
              "plot": "parcelle", "mushroom": "champignons", "melon": "melons", "hive": "ruche", "bridges": "", "drawbridge": "pont-levis",
              "redconcrete": "béton rouge", "alternative": "variante", "nether": "Nether", "hosp": "hôpital", "batpole": "mât", "rcorner": "angle R",
              "bw": "noir et blanc", "ry": "rouge et jaune", "in": "intérieur", "out": "extérieur", "gate": "porte", "wallgate": "porte",
              "wallstraight": "droite", "wallcorner": "angle", "wallincorner": "angle intérieur", "walloutcorner": "angle extérieur",
              "fields": "", "field": "", "roads": "", "hall": "", "halls": "", "megahalls": "", "rails": "", "climb": "escalade",
              "flatfield": "plat", "largefield": "grand", "smallfield": "petit", "basicfield": "simple", "fancyfield": "orné", "harbor": "", "dock": "quai", "for": "", "corridor": "couloir", "mangrove": "palétuvier", "fill": "remblai"})
VOCAB = sorted(set(WORDS) | {k for k in QUALIFIERS}, key=len, reverse=True)
VOCAB_SET = {v for v in VOCAB if len(v) > 1}


def split_word(w):
    """wallstraight -> wall straight, brickshort -> brick short: the split that knows the most letters, in the fewest pieces."""
    n = len(w)
    best = [(0, 0, [])] + [None] * n
    for i in range(n):
        if best[i] is None:
            continue
        known, pieces, parts = best[i]
        for j in range(i + 1, n + 1):
            piece = w[i:j]
            k = known + (len(piece) if piece in VOCAB_SET else 0)
            cand = (k, pieces + 1, parts + [piece])
            if best[j] is None or (cand[0], -cand[1]) > (best[j][0], -best[j][1]):
                best[j] = cand
    # Glue runs of unknown letters back together
    out = []
    for piece in best[n][2]:
        if out and piece not in VOCAB_SET and out[-1] not in VOCAB_SET:
            out[-1] += piece
        else:
            out.append(piece)
    return out


def shape_name(base):
    b = base.lower()
    fond = b.endswith("_base")
    b = b[:-5] if fond else b
    if b in WORDS:
        text = WORDS[b]
    else:
        bits = []
        for t in re.findall(r"[a-z]+|\d+", b.replace("_", " ")):
            bits += [t] if t in WORDS or t.isdigit() else split_word(t)
        words = []
        for t in bits:
            fr = WORDS.get(t, QUALIFIERS.get(t, t.upper() if len(t) == 1 else t))
            if fr and fr not in words:
                words.append(fr)
        text = " ".join(words)
    return text + (" · fondations" if fond else "")


MODULAR = re.compile(r"^(infrastructure|walls)|canal|aqueduct|harbor/paths|seawall|retaining_wall|platforms")

# --- Pictures -------------------------------------------------------------------------------------------------------
SKIP = ("air", "structurize:", "light_block", "barrier", "torch", "sign", "button", "lever", "pressure_plate", "carpet")
colour_cache = {}


def block_colour(name):
    if name in colour_cache:
        return colour_cache[name]
    base = name.split(":")[-1]
    trimmed = re.sub(r"_(stairs|slab|wall|fence_gate|fence|trapdoor|door|pane|button)$", "", base)
    cands = [base, base + "_top", trimmed, trimmed + "s", trimmed + "_planks", trimmed.replace("brick", "bricks"), trimmed.replace("_log", "_log_top"),
             trimmed.replace("wood", "log"), trimmed + "_block", trimmed.replace("smooth_", "") + "_top", trimmed.replace("smooth_", "")]
    rgb = None
    for c in cands:
        p = "assets/minecraft/textures/block/%s.png" % c
        if p in res_names:
            im = Image.open(io.BytesIO(res.read(p))).convert("RGBA").resize((1, 1), Image.BOX)
            rgb = im.getpixel((0, 0))[:3]
            break
    if "grass_block" in name or "leaves" in name or "vine" in name:
        rgb = (96, 140, 60)
    if "water" in name:
        rgb = (60, 90, 190)
    if "lava" in name:
        rgb = (220, 110, 30)
    if "powered_rail" in name or "redstone" in name:
        rgb = (200, 40, 30)
    elif "rail" in name:
        rgb = (150, 120, 80)
    colour_cache[name] = rgb or (130, 120, 105)
    return colour_cache[name]


def read(data):
    d = nbt.load(data)
    sx, sy, sz = d["size_x"], d["size_y"], d["size_z"]
    shorts = []
    for v in d["blocks"]:
        shorts.append((v >> 16) & 0xFFFF)
        shorts.append(v & 0xFFFF)
    pal = [p["Name"] for p in d["palette"]]
    return d, sx, sy, sz, shorts, pal


def anchor(d, sx, sz, shorts, pal):
    off = d.get("optional_data", {}).get("structurize", {}).get("primary_offset")
    if not off:
        return ""
    i = off["y"] * sx * sz + off["z"] * sx + off["x"]
    return pal[shorts[i]] if 0 <= i < len(shorts) else ""


def solid(name):
    return not any(s in name for s in SKIP)


def from_above(sx, sy, sz, shorts, pal):
    img = Image.new("RGBA", (sx, sz), (0, 0, 0, 0))
    for x in range(sx):
        for z in range(sz):
            for y in range(sy - 1, -1, -1):
                name = pal[shorts[y * sx * sz + z * sx + x]]
                if solid(name):
                    r, g, b = block_colour(name)
                    k = 0.65 + 0.55 * y / max(1, sy - 1)
                    img.putpixel((x, z), (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255))
                    break
    return img


def from_front(sx, sy, sz, shorts, pal):
    along_x = sx >= sz
    w = sx if along_x else sz
    depth = sz if along_x else sx
    img = Image.new("RGBA", (w, sy), (0, 0, 0, 0))
    for a in range(w):
        for y in range(sy):
            for d in range(depth):
                x, z = (a, d) if along_x else (d, a)
                name = pal[shorts[y * sx * sz + z * sx + x]]
                if solid(name):
                    r, g, b = block_colour(name)
                    k = 1.1 - 0.4 * d / max(1, depth - 1)
                    img.putpixel((a, sy - 1 - y), (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255))
                    break
    return img


def tunnels(sx, sy, sz, shorts, pal):
    best = None
    for y in range(1, sy):
        air = sum(1 for x in range(sx) for z in range(sz) if "air" in pal[shorts[y * sx * sz + z * sx + x]])
        if 0.1 * sx * sz < air < 0.6 * sx * sz:
            best = y
            break
    img = Image.new("RGBA", (sx, sz), (0, 0, 0, 0))
    stone = block_colour("minecraft:stone") + (255,)
    for x in range(sx):
        for z in range(sz):
            name = pal[shorts[best * sx * sz + z * sx + x]] if best is not None else "stone"
            img.putpixel((x, z), (60, 40, 25, 255) if "air" in name else stone)
    return img


def cut(sx, sy, sz, shorts, pal, frac):
    """A horizontal cut at this height: walls, rooms and tunnels seen from above."""
    y = max(0, min(sy - 1, int(sy * frac)))
    img = Image.new("RGBA", (sx, sz), (0, 0, 0, 0))
    for x in range(sx):
        for z in range(sz):
            name = pal[shorts[y * sx * sz + z * sx + x]]
            if solid(name):
                img.putpixel((x, z), block_colour(name) + (255,))
            elif "air" in name:
                img.putpixel((x, z), (60, 40, 25, 255))
    return img


def fit(img, size):
    box = img.getbbox()
    if box is None:
        return None
    img = img.crop(box)
    side = max(img.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    scale = max(1, size // side)
    out = square.resize((side * scale, side * scale), Image.NEAREST) if side * scale <= size else square.resize((size, size), Image.NEAREST)
    cell = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cell.paste(out, ((size - out.width) // 2, (size - out.height) // 2))
    return cell


def plot(sx, sz):
    """An empty plot: its outline, to scale."""
    img = Image.new("RGBA", (sx, sz), (0, 0, 0, 0))
    for x in range(sx):
        for z in range(sz):
            if x in (0, sx - 1) or z in (0, sz - 1):
                img.putpixel((x, z), INK + (255,))
    return img


sprite_cache = {}


def pixel(name, size, tint=None):
    key = (name, size, tint)
    if key not in sprite_cache:
        im = Image.open(os.path.join(pack_dir, "Sprites", name + ".png")).convert("RGBA")
        out = Image.new("RGBA", im.size, (0, 0, 0, 0))
        for x in range(im.width):
            for y in range(im.height):
                r, g, b, a = im.getpixel((x, y))
                if a < 128:
                    continue
                dark = r + g + b < 384
                out.putpixel((x, y), (INK if dark else (tint or CREAM)) + (255,))
        sprite_cache[key] = out.resize((size, size), Image.NEAREST)
    return sprite_cache[key]


def badge(cell, name):
    b = Image.new("RGBA", (14, 14), CREAM + (255,))
    for i in range(14):
        for p in ((i, 0), (i, 13), (0, i), (13, i)):
            b.putpixel(p, INK + (255,))
    b.alpha_composite(pixel(name, 12), (1, 1))
    cell.alpha_composite(b, (CELL - 14, CELL - 14))
    return cell


# --- Per style ------------------------------------------------------------------------------------------------------
def styles():
    out = {}
    for n in jar.namelist():
        m = re.match(r"blueprints/minecolonies/([^/]+)/pack\.json$", n)
        if m:
            out[m.group(1)] = json.loads(jar.read(n))["name"]
    return out


def pieces(style):
    """Every decoration of the style at its lowest level: (folder, base, zip name)."""
    found = {}
    for n in sorted(jar.namelist()):
        m = re.match(r"blueprints/minecolonies/%s/(.+)/([^/]+?)(\d*)\.blueprint$" % re.escape(style), n)
        if not m:
            continue
        folder, base, level = m.group(1).lower(), m.group(2).lower(), int(m.group(3) or 1)
        key = folder + "/" + base
        if key not in found or level < found[key][1]:
            found[key] = (n, level)
    return {k: v[0] for k, v in found.items()}


# --- Names in good French ---------------------------------------------------------------------------------------------
FAMILY_GENDER = {"Double rail": "m", "Monorail": "m", "Métro": "m", "Rail": "m", "Puits de mine": "m", "Pont": "m", "Aqueduc": "m", "Canal": "m",
                 "Égout": "m", "Escalier": "m", "Champ": "m", "Marché": "m", "Parc": "m", "Terrain libre": "m", "Carrefour": "m", "Raccord": "m",
                 "Sentier": "m", "Angle de muraille": "m", "Escalier de muraille": "m", "Mur de soutènement": "m", "Quai": "m"}
MASC = {"longue": "long", "courte": "court", "moyenne": "moyen", "ornée": "orné", "rurale": "rural", "urbaine": "urbain", "soignée": "soigné",
        "unie": "uni", "haute": "haut", "basse": "bas", "petite": "petit", "grande": "grand", "droite": "droit", "diagonale": "diagonal",
        "couverte": "couvert", "étroite": "étroit", "intérieure": "intérieur", "ronde": "rond", "carrée": "carré", "pleine": "plein",
        "douce": "doux", "impaire": "impair", "paire": "pair", "éclairée": "éclairé", "verticale": "vertical", "plate": "plat",
        "flottante": "flottant", "entière": "entier", "ouverte": "ouvert", "fermée": "fermé", "moussue": "moussu", "surélevée": "surélevé",
        "souterraine": "souterrain", "aérienne": "aérien", "alimentée": "alimenté", "avancée": "avancé", "profonde": "profond",
        "principale": "principal", "large": "large"}
FEM = {v: k for k, v in MASC.items() if k != v}
FEM.update({"peu profond": "peu profonde"})


STOP = {"en", "de", "du", "des", "à", "au", "aux", "la", "le", "et", "·"}


def agree(name, gender, family_name=""):
    # « Canal canal angle », « Muraille en argile en argile » : each word once
    words, seen = [], set()
    for w in name.split(" "):
        low = w.lower()
        if low in seen and low not in STOP and not re.fullmatch(r"[A-Z0-9]", w):
            if words and words[-1].lower() in STOP:
                words.pop()
            continue
        seen.add(low)
        words.append(w)
    while words and words[-1].lower() in STOP:
        words.pop()
    name = " ".join(words)
    if family_name == "Canal":
        name = name.replace("porte", "écluse")
    table = MASC if gender == "m" else FEM
    out = " ".join(table.get(w, w) for w in name.split(" "))
    return out.replace("peu profond", "peu profonde") if gender == "f" and "peu profonde" not in out else out


COLOUR_FR = {"white": "blanc", "lightgray": "gris clair", "gray": "gris", "black": "noir", "brown": "brun", "red": "rouge", "orange": "orange",
             "yellow": "jaune", "lime": "vert clair", "green": "vert", "cyan": "cyan", "lightblue": "bleu clair", "blue": "bleu",
             "purple": "violet", "magenta": "magenta", "pink": "rose"}
MATERIAL_FR = {"beigestone": "en pierre beige", "blackstone": "en pierre noire", "brownstone": "en pierre brune", "deepslate": "en ardoise",
               "stone": "en pierre"}
# Hand-written names, by blueprint name (or "folder/name" when one name means different things in different folders)
NAMES_FR = {
    "stall_cloth": "Étal du drapier", "stall_dye": "Étal du teinturier", "stall_fish": "Étal du poissonnier", "stall_food": "Étal de vivres",
    "stall_general": "Étal de bric-à-brac", "stall_meat_one": "Étal du boucher", "stall_meat_two": "Grand étal du boucher",
    "stall_pottery_one": "Étal du potier", "stall_pottery_two": "Grand étal du potier",
    "harbor/piers/jetty": "Jetée", "harbor/piers/large": "Grand ponton", "harbor/piers/large_military": "Grand ponton militaire",
    "harbor/piers/medium": "Ponton moyen", "harbor/piers/medium_military": "Ponton militaire moyen", "harbor/piers/small": "Petit ponton",
    "harbor/piers/small_roofed": "Petit ponton couvert",
    "ship_construction": "Chantier naval", "ship_construction_large": "Grand chantier naval", "storage_shed": "Hangar du port",
    "harbor/structures/warehouse": "Entrepôt du port", "warehouse_large": "Grand entrepôt du port", "crane": "Grue du port",
    "shipwright": "Atelier du charpentier de marine", "buoy": "Bouée",
    "grand_lamp_pot": "Grand lampadaire en pot", "garden_of_poseidon": "Jardin de Poséidon", "seating_area_rural": "Coin repos champêtre",
    "seating_area_urban": "Coin repos citadin", "supplycamp": "Camp de ravitaillement", "supply_camp": "Petit camp de ravitaillement",
    "bush_seat": "Banc de verdure", "planning/barracks": "Terrain de la caserne", "planning/small": "Petit terrain à bâtir",
    "planning/medium": "Terrain à bâtir moyen", "planning/large": "Grand terrain à bâtir", "planning/wall": "Tracé de muraille",
    "diorite_lrg": "Grande place en diorite", "diorite_med": "Place moyenne en diorite", "diorite_sml": "Petite place en diorite",
    "lamp_plaza": "Place aux lanternes", "market_plaza": "Place du marché", "sitting_plaza": "Place aux bancs", "garden_tower": "Tour du jardin",
    "corridorbridge": "Couloir avec pont", "corridorgreens": "Couloir de verdure", "corridormelons": "Couloir aux pastèques",
    "corridorpagodas": "Couloir aux pagodes", "corridorstatues": "Couloir aux statues", "corridorswords": "Couloir aux épées",
    "corridorwaterfalls": "Couloir aux cascades", "corridormangrove": "Couloir aux palétuviers",
    "largegateatlas": "Grande porte d'Atlas", "largegateguardian": "Grande porte des gardiens", "largegatelionandesite": "Grande porte aux lions en andésite",
    "largegatelionblkstone": "Grande porte aux lions en pierre noire", "largegateliondiorite": "Grande porte aux lions en diorite",
    "largegateliongranite": "Grande porte aux lions en granite", "largegatesword": "Grande porte aux épées", "smallgatelava": "Petite porte de lave",
    "smallgatewater": "Petite porte d'eau", "smallgatezen": "Petite porte zen",
    "intersection_deco/megatreecrimson": "Arbre géant pourpre de carrefour", "intersection_deco/megatreemagma": "Arbre géant de magma de carrefour",
    "small_deco/megatreecrimson": "Arbre géant pourpre", "small_deco/megatreemagma": "Arbre géant de magma",
    "fullaquarium": "Grand aquarium", "fullkelpfarm": "Grande ferme à varech",
    "maingatecourtyard": "Cour de la grande porte", "maingatedock": "Quai de la grande porte",
    "warpedquarrycap": "Couvercle de carrière en bois biscornu", "wallseahorse": "Hippocampe mural", "wallsword": "Épée murale",
    "zenstatue": "Statue zen", "parkway": "Allée de parc", "cargo_hay": "Navire chargé de foin", "cargo_wood": "Navire chargé de bois",
    "cornerguard": "Poste de garde d'angle", "cornerinside": "Coin intérieur", "netherportal": "Portail du Nether",
    "kelpfarmcombo": "Ferme à varech combinée", "kelpfarmdual": "Double ferme à varech", "housing/jungle": "Maison en bois exotique",
    "universitylibrary": "Bibliothèque universitaire", "townlibrary": "Bibliothèque municipale",
    "playermansion_invisible": "Manoir du seigneur (variante)", "marketplace": "Place du marché", "marketsquare": "Grand-place du marché",
    "big_norsemen_ship": "Grand drakkar", "medium_norsemen_ship": "Drakkar moyen", "small_norsemen_ship": "Petit drakkar",
    "big_sunk_ship": "Grande épave", "medium_sunk_ship": "Épave moyenne", "small_sunk_ship": "Petite épave",
    "big_pirate_ship": "Grand navire pirate", "medium_pirate_ship": "Navire pirate moyen", "small_pirate_ship": "Petit navire pirate",
    "endstatue": "Statue de l'End", "netherstatue": "Statue du Nether", "statuearmsraised": "Statue aux bras levés", "statuechair": "Statue assise",
    "statueflower": "Statue à la fleur", "statueposeidon": "Statue de Poséidon", "statuepresent": "Statue au cadeau", "statuestaff": "Statue au bâton",
    "statueswordshield": "Statue à l'épée et au bouclier", "statueweight": "Statue aux haltères", "stone_lion": "Lion de pierre",
    "stone_lion_b": "Lion de pierre B", "stone_lion_c": "Lion de pierre C", "stone_lion_l": "Lion de pierre L", "stone_lion_p": "Lion de pierre P",
    "old_stone_park": "Vieux parc de pierre", "waterside_park": "Parc au bord de l'eau", "kongming_group": "Groupe de lanternes célestes",
    "lanterns/andesite": "Lanterne en andésite", "lanterns/cobblestone": "Lanterne en pavés", "lanterns/mossy_cobble": "Lanterne en pavés moussus",
    "lanterns/stone": "Lanterne en pierre", "kelp_farm": "Ferme à varech", "nether_gate": "Porte du Nether", "hole_in_the_wall": "Échoppe dans le mur",
    "lightglowstone": "Lampe en pierre lumineuse", "lightprismarine": "Lampe en prismarine", "lightwooden": "Lampe en bois",
    "hedgemaze": "Labyrinthe de haies", "icerink": "Patinoire", "ivycoveredbenches": "Bancs couverts de lierre", "volleyballcourt": "Terrain de volley",
    "composthut": "Cabane à compost", "nethership": "Navire du Nether", "pharao": "Statue de pharaon", "concert": "Scène de concert",
    "big_log_cart": "Grande charrette de rondins", "small_log_cart": "Petite charrette de rondins", "small_wheat_cart": "Petite charrette de blé",
    "noble_guard_post": "Poste de garde des nobles", "poor_guard_post": "Poste de garde simple",
}


# Plantation fields: MineColonies' own names are English or garbled
FIELDS_FR = {
    "bamboo": "Champ de bambou", "cacao": "Champ de cacao", "cocoa": "Champ de cacao", "cactbamboo": "Champ de cactus et bambou",
    "cactus": "Champ de cactus", "cocoa_vine": "Champ de cacao et lianes", "cocoa_vines": "Champ de cacao et lianes",
    "cocoa_and_vines": "Champ de cacao et lianes", "corner_bamboo": "Champ de bambou d'angle", "corner_cactus": "Champ de cactus d'angle",
    "corner_cocoa": "Champ de cacao d'angle", "corner_crimson": "Champ pourpre d'angle", "corner_sugar": "Champ de canne à sucre d'angle",
    "corner_vines": "Champ de lianes d'angle", "corner_warped": "Champ biscornu d'angle", "glowberries": "Champ de baies lumineuses",
    "glowberry": "Champ de baies lumineuses", "illumination_orb_fruit_cultivation_unit": "Serre à fruits lumineux",
    "imperial_aquatic_plant_cultivation_facility": "Grand bassin de plantes aquatiques", "kelp": "Champ de varech",
    "kelp_seapickles_seagrass": "Champ de varech, d'herbes marines et de cornichons de mer",
    "nether_fungal_growth_facility": "Serre à champignons du Nether", "nether_fungi": "Champ de champignons du Nether",
    "nether_fungus": "Champ de champignons du Nether", "nether_vinery_growth_facility": "Serre à lianes du Nether",
    "nether_vines": "Champ de lianes du Nether", "niflheim_crimson_fungus": "Champignons pourpres de Niflheim",
    "niflheim_crimson_vine": "Lianes pourpres de Niflheim", "niflheim_warped_fungus": "Champignons biscornus de Niflheim",
    "niflheim_warped_vines": "Lianes biscornues de Niflheim", "pickle_seagrass": "Champ d'herbes marines et de cornichons de mer",
    "seapickles_seagrass": "Champ d'herbes marines et de cornichons de mer", "sea_pickles_grass": "Champ d'herbes marines et de cornichons de mer",
    "seagrass_and_sea_pickles": "Champ d'herbes marines et de cornichons de mer", "sea_grass": "Champ d'herbes marines",
    "sea_pickles": "Champ de cornichons de mer", "sea_plants": "Champ de plantes marines", "sea": "Champ de plantes marines",
    "sugar": "Champ de canne à sucre", "sugarcane": "Champ de canne à sucre", "sugar_cane": "Champ de canne à sucre",
    "twisted_vines": "Champ de lianes tordues", "vines": "Champ de lianes", "warm_water_vegetation_cultivation_unit": "Bassin de plantes d'eau chaude",
    "warped_crimson_plants": "Champ de plantes pourpres et biscornues", "weeping_twisting": "Champ de lianes pleureuses et tordues",
    "weeping_vines": "Champ de lianes pleureuses", "kelp_and_sea": "Champ de varech et de plantes marines",
    "vine_and_cocoa": "Champ de lianes et cacao", "twisting_vines": "Champ de lianes tordues", "nether": "Champ du Nether", "nether_crops": "Champ de cultures du Nether",
}
NAMES_FR.update({"mineshafts/simplequarry": "Petite carrière", "mineshafts/simplequarryshaft": "Puits de la petite carrière",
                 "mineshafts/mediumquarry": "Grande carrière", "mineshafts/mediumquarryshaft": "Puits de la grande carrière",
                 "marketplace": "Grand marché", "twowide": "Élément double en long", "twosquared": "Élément double carré",
                 "aqueduct_pillar_one": "Pilier d'aqueduc A", "aqueduct_pillar_two": "Pilier d'aqueduc B", "sewage/road_hatch": "Égout, trappe sous la route",
                 "playground": "Aire de jeux", "playset": "Jeux d'enfants", "market": "Marché", "starterjunglehome": "Maison de départ en bois exotique"})


def forced_name(folder, base):
    """A hand-written name, when we have one, for any piece: modular or not."""
    tail = folder.rsplit("/", 1)[-1]
    # Some packs name their fields « bamboo field », « kelp & sea field »
    base = base.replace(" field", "").replace(" & ", "_and_").replace(" ", "_")
    for key in (tail + "/" + base, "/".join(folder.split("/")[-2:]) + "/" + base, base):
        if key in NAMES_FR:
            return NAMES_FR[key]
    if folder.startswith("agriculture/fields") and base in FIELDS_FR:
        return FIELDS_FR[base]
    if base.endswith("minimarket"):
        colour = base[:-len("minimarket")]
        return "Étal de marché " + COLOUR_FR.get(colour, colour)
    return None


def french_name(folder, base, named):
    tail = folder.rsplit("/", 1)[-1]
    forced = forced_name(folder, base)
    if forced:
        return forced
    if tail in MATERIAL_FR and re.fullmatch(r"(single|double)[a-e]", base):
        kind = "Façade simple" if base.startswith("single") else "Double façade"
        return "%s %s %s" % (kind, base[-1].upper(), MATERIAL_FR[tail])
    if named:
        return named["name"]
    return base.replace("_", " ").capitalize()


def colour_word(base):
    for word in sorted(COLOURS, key=len, reverse=True):
        if word in base:
            return word
    return None


report = collections.Counter()
data = {}
os.makedirs(os.path.join(ASSETS, "textures", "gui", "deco"), exist_ok=True)
for style, pack_name in sorted(styles().items()):
    cells, entries, used_sprites, used_items, hashes, used_names = [], {}, set(), set(), {}, collections.Counter()
    todo = []
    for key, zname in pieces(style).items():
        d, sx, sy, sz, shorts, pal = read(jar.read(zname))
        if re.search(r"minecolonies:blockhut(?!.*field)", anchor(d, sx, sz, shorts, pal)):
            continue
        folder, base = key.rsplit("/", 1)
        todo.append((key, folder, base, d, sx, sy, sz, shorts, pal))
    # Modular pieces first: their pictures are what makes them unique
    todo.sort(key=lambda t: (family(t[1]) is None, t[0]))
    for key, folder, base, d, sx, sy, sz, shorts, pal in todo:
        fam = family(folder) if MODULAR.search(folder) else None
        named = NAMES.get(base)
        cell, item, kind = None, None, ""
        if fam:
            fname, ficon, quals = fam
            qual = " ".join(q for q in (QUALIFIERS.get(p, p.replace("_", " ")) for p in quals) if q)
            words = []
            for w in " ".join(x for x in (fname, qual, shape_name(base)) if x).split(" "):
                if not words or w != words[-1] or w in ("A", "B", "C"):
                    words.append(w)
            name = forced_name(folder, base) or agree(" ".join(words), FAMILY_GENDER.get(fname, "f"), fname)
            pic = tunnels(sx, sy, sz, shorts, pal) if "mineshaft" in folder else from_above(sx, sy, sz, shorts, pal)
            cell = fit(pic, CELL)
            if cell is not None:
                cell = badge(cell, ficon)
                kind = "plan"
        else:
            name = french_name(folder, base, named)
            colour = colour_word(base)
            for rx, cands in RULES:
                if rx.search(base):
                    for c in cands:
                        if c not in used_sprites:
                            used_sprites.add(c)
                            cell = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
                            cell.alpha_composite(pixel(c, CELL))
                            kind = "pixel"
                            break
                    if cell is None and colour and (cands[0], colour) not in used_sprites:
                        used_sprites.add((cands[0], colour))
                        cell = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
                        cell.alpha_composite(pixel(cands[0], CELL, COLOURS[colour]))
                        kind = "tinted"
                    break
            if cell is None and named and named.get("icon") and not GENERIC_ITEMS.search(named["icon"]) and named["icon"] not in used_items:
                item = named["icon"] if ":" in named["icon"] else "minecraft:" + named["icon"]
                used_items.add(named["icon"])
                kind = "item"
            if cell is None and item is None:
                thin = min(sx, sz) <= 3 and max(sx, sz) >= 2 * min(sx, sz)
                cell = fit(from_front(sx, sy, sz, shorts, pal) if thin else from_above(sx, sy, sz, shorts, pal), CELL)
                kind = "front" if thin else "plan"
        if cell is None and item is None:
            cell = fit(plot(sx, sz), CELL)
            kind = "plot"
        entry = {"name": name}
        if item:
            entry["item"] = item
        else:
            h = hashlib.md5(cell.tobytes()).hexdigest()
            views = [lambda: from_front(sx, sy, sz, shorts, pal)] + [lambda f=f: cut(sx, sy, sz, shorts, pal, f) for f in (0.15, 0.3, 0.45, 0.6, 0.75)]
            for view in views if h in hashes and kind in ("plan", "front") else []:
                other = fit(view(), CELL)
                if other is None:
                    continue
                if fam:
                    other = badge(other, fam[1])
                oh = hashlib.md5(other.tobytes()).hexdigest()
                if oh not in hashes:
                    cell, h, kind = other, oh, "cut"
                    break
            if h in hashes:
                # Two blueprints that look the same from everywhere: seen from another side
                for turned in (cell.rotate(90), cell.transpose(Image.FLIP_LEFT_RIGHT), cell.rotate(270), cell.transpose(Image.FLIP_TOP_BOTTOM)):
                    th = hashlib.md5(turned.tobytes()).hexdigest()
                    if th not in hashes:
                        cell, h = turned, th
                        break
            if h in hashes:
                report["same picture: %s | %s" % (style, name)] += 1
            hashes[h] = key
            entry["cell"] = len(cells)
            cells.append(cell)
        report[kind] += 1
        entries[key] = entry
    # Two pieces with one name: say where each comes from when their folders differ, then call the others variants
    def same_names():
        groups = collections.defaultdict(list)
        for key, entry in entries.items():
            groups[entry["name"]].append(key)
        return [(name, keys) for name, keys in groups.items() if len(keys) > 1]

    for name, keys in same_names():
        segs = {key: key.rsplit("/", 2)[-2] for key in keys}
        if len(set(segs.values())) == len(keys):
            for key in keys:
                word = SEGMENTS.get(segs[key], QUALIFIERS.get(segs[key], WORDS.get(segs[key], segs[key].replace("_", " "))))
                if word and word.lower() not in name.lower():
                    entries[key]["name"] = name + " " + word
    for name, keys in same_names():
        tops = {key: key.split("/", 1)[0] for key in keys}
        if len(set(tops.values())) > 1:
            for key in keys:
                if tops[key] in ("infrastructure", "walls"):
                    entries[key]["name"] = name + " (aménagement)"
    for name, keys in same_names():
        for i, key in enumerate(sorted(keys)[1:], 1):
            entries[key]["name"] = name + (" (variante)" if i == 1 else " (variante %d)" % i)
            report["variant name"] += 1
    cols = 16
    rows = max(1, (len(cells) + cols - 1) // cols)
    atlas = Image.new("RGBA", (cols * CELL, rows * CELL), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        atlas.alpha_composite(c, ((i % cols) * CELL, (i // cols) * CELL))
    atlas.save(os.path.join(ASSETS, "textures", "gui", "deco", style + ".png"))
    data[pack_name] = {"atlas": style, "width": cols * CELL, "height": rows * CELL, "pieces": entries}

with open(os.path.join(ASSETS, "deco_icons.json"), "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
for k, v in sorted(report.items(), key=lambda kv: (not kv[0].startswith("same"), kv[0])):
    print(v, k)
