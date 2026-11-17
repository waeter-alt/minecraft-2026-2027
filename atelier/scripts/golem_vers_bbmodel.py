# [claude-build] Gardien des Racines : ecrit atelier/modeles/golem/golem.bbmodel, le modele du boss decoupe en os pour Blockbench.
# La geometrie est celle de GolemModele.creerCouche() au 2026-11-17 (meme cubes, memes valeurs, memes zones de texture), rangee
# dans des os pour pouvoir l'animer. Les 5 points faibles (bourgeon, couvercle, branche) sont dans l'os « fixe », que rien n'anime :
# leurs boites de collision (Golem.AZIMUTS, RAYONS, HAUTEURS, TAILLES) ne bougent pas.
# Une fois que Luca a retouche le fichier dans Blockbench, c'est lui la source : ne plus relancer ce script (il l'ecraserait).
# Ensuite : atelier/scripts/bbmodel_vers_java.py traduit le .bbmodel en Java.
import base64
import json
import math
import re
import uuid
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "atelier/modeles/golem/golem.bbmodel"
JAVA = RACINE / "atelier/donjon/arene/src/main/java/fr/atelier/grappin"
TEXTURE = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/textures/entity/golem.png"
U = 4.0                                   # unites de modele par bloc (comme GolemModele)

# Zones de texture (texOffs), comme GolemModele
SOMBRE, ROUGE, MOUSSE = (0, 0), (0, 130), (0, 224)
CLAIR, MASQUE, CREME, CERNES, AMBRE = (260, 0), (260, 100), (330, 100), (330, 140), (330, 200)

GRAINE = uuid.UUID("2b9f3c1e-6a41-4d7e-9c55-0d1f00000000")

# os : nom -> (parent, pivot en blocs dans le repere de GolemModele.cube : x a droite, y au-dessus de la lave, z devant)
OS = {
    "racine": (None, (0, 0, 0)),
    "base": ("racine", (0, 0, 0)),
    "fixe": ("racine", (0, 0, 0)),
    "corps": ("racine", (0, 6, 0)),
    "tete": ("corps", (0, 31, 2)),
    "machoire": ("tete", (0, 31, 1)),
    "bras_g": ("corps", (-11, 28, 0)),
    "main_g": ("bras_g", (-12, 10, 2)),
    "bras_d": ("corps", (11, 28, 0)),
    "poing_d": ("bras_d", (10.6, 36, 3)),
    "branches_dos": ("corps", (0, 26, -5)),
    "lianes": ("corps", (0, 24, 5)),
}


def vers_bb(cx, cy, cz):
    """Repere de GolemModele.cube (blocs) -> repere Blockbench (unites, y vers le haut, x_bb = -x_java, z_bb = z_java)."""
    return (-cx * U, cy * U, -cz * U)


class Modele:
    def __init__(self):
        self.cubes = {nom: [] for nom in OS}

    def cube(self, os, nom, zone, cx, cy, cz, w, h, d, rz=0):
        c = vers_bb(cx, cy, cz)
        t = (w * U, h * U, d * U)
        self.cubes[os].append({
            "name": nom, "zone": zone,
            "from": [c[i] - t[i] / 2 for i in range(3)], "to": [c[i] + t[i] / 2 for i in range(3)],
            "origin": list(c), "rz": rz,
        })

    def sombre(self, os, cx, cy, cz, w, h, d, rz=0, nom="ecorce"):
        self.cube(os, nom, SOMBRE, cx, cy, cz, w, h, d, rz)

    def bois(self, os, cx, cy, cz, w, h, d, rz=0, nom="bois"):
        self.cube(os, nom, CLAIR, cx, cy, cz, w, h, d, rz)


def constantes_golem():
    texte = (JAVA / "Golem.java").read_text(encoding="utf-8")

    def tableau(nom):
        return [float(v) for v in re.search(nom + r"\s*=\s*\{([^}]*)\}", texte).group(1).split(",")]
    largeur = float(re.search(r"FACTEUR_LARGEUR\s*=\s*([0-9.]+)F", texte).group(1))
    return tableau("AZIMUTS"), tableau("RAYONS"), tableau("HAUTEURS"), tableau("TAILLES"), largeur


def geometrie():
    m = Modele()
    # ------------------------------------------------ taille de mousse, hanches, racines (base : ne bouge pas, plonge dans la lave)
    m.cube("base", "mousse", MOUSSE, 0, 3, 0, 13, 6, 11)
    m.sombre("base", 0, 10.5, 0, 11.5, 9, 10)
    for s in (-1, 1):
        m.sombre("base", s * 7.4, 1.0, 3.5, 3.5, 2.6, 3.5, nom="racine")
        m.sombre("base", s * 7.0, 1.0, -3.5, 3.5, 2.6, 3.5, nom="racine")
    # ------------------------------------------------ torse, epaulement, dos
    m.sombre("corps", 0, 21.5, 0, 16, 14, 11, nom="torse")
    m.sombre("corps", 0, 29, 0, 18, 3, 10, nom="epaulement")
    for k in range(3):
        m.sombre("corps", 0, 18.5 + k * 3.8, -6.6, 3, 2.4, 2, nom="crete")
    m.cube("corps", "mousse", MOUSSE, 0, 30.8, -1, 8, 1, 6)
    # ------------------------------------------------ cou, tete, machoire
    m.sombre("corps", 0, 30.8, 1.8, 5, 2.4, 5, nom="cou")
    m.bois("tete", 0, 35.2, 2.5, 11, 10, 10, nom="tete")
    m.sombre("tete", 0, 38.6, 7.7, 11.6, 1.5, 1.8, nom="arcade")
    m.bois("tete", -5.9, 34.5, 3.5, 1.4, 5, 6, nom="joue")
    m.bois("tete", 5.9, 34.5, 3.5, 1.4, 5, 6, nom="joue")
    m.sombre("machoire", 0, 30.0, 4.2, 9, 2, 8, nom="machoire")
    for k in range(5):
        m.sombre("machoire", -3 + k * 1.5, 29.4 - (k % 2), 6.2, 0.8, 3 + (k % 2), 0.8, nom="barbe")
    # ------------------------------------------------ cornes de branches
    for s in (-1, 1):
        m.bois("tete", s * 4.6, 41.2, 2.5, 2.6, 3.4, 2.6, nom="corne")
        m.bois("tete", s * 5.5, 44.4, 2.5, 2.4, 4.2, 2.4, s * 14, nom="corne")
        m.bois("tete", s * 6.8, 48.0, 2.5, 2.2, 4.2, 2.2, s * 12, nom="corne")
        m.bois("tete", s * 7.4, 51.6, 2.5, 2.0, 3.8, 2.0, s * 2, nom="corne")
        m.bois("tete", s * 6.7, 54.6, 2.5, 1.8, 3.2, 1.8, -s * 32, nom="corne")
        m.bois("tete", s * 5.2, 56.6, 2.5, 1.6, 2.6, 1.6, -s * 62, nom="corne")
        m.bois("tete", s * 8.6, 50.0, 2.5, 0.9, 2.4, 0.9, s * 38, nom="brindille")
    # ------------------------------------------------ epaules, bras gauche pendant, bras droit leve
    for s in (-1, 1):
        m.sombre("corps", s * 10.5, 28.5, 0, 8, 8, 9, nom="epaule")
        m.cube("corps", "rondin", CERNES, s * 14.8, 28.5, 0, 0.6, 7, 7)
        m.cube("corps", "mousse", MOUSSE, s * 10.5, 32.9, 0, 6, 1, 7)
    m.sombre("bras_g", -11.8, 21.5, 0.3, 5.5, 10, 6.5, nom="bras")
    m.sombre("bras_g", -12.2, 16.3, 0.8, 7, 2, 7.5, nom="coude")
    m.sombre("bras_g", -12.6, 12.5, 1.2, 6, 8, 7, nom="avant_bras")
    m.sombre("main_g", -13.0, 7.2, 2.2, 7.5, 5, 8, nom="main")
    for k in range(4):
        m.sombre("main_g", -15.2 + k * 1.9, 5.4, 6.5, 1.6, 3.2, 1.8, nom="doigt")
    m.sombre("bras_d", 10.6, 31.8, 2.2, 5.5, 6, 5.5, nom="bras")
    m.sombre("poing_d", 9.8, 36.4, 5.0, 5.5, 6, 5.5, nom="poing")
    m.cube("poing_d", "corne_poing", CREME, 9.2, 45.6, 7.7, 1.8, 3.2, 1.8)
    # ------------------------------------------------ details
    for k in range(3):
        m.bois("corps", 0, 20.6 - k * 1.9, 5.8, 12.8, 0.8, 0.5, nom="cote")
    m.bois("corps", 0, 18.6, 5.9, 1.2, 4.4, 0.5, nom="sternum")
    for s in (-1, 1):
        m.bois("corps", s * 6.6, 23.6, 5.6, 2.2, 5.6, 0.8, nom="plaque")
        m.bois("corps", s * 8.2, 17.5, 3.0, 0.8, 3.4, 5.6, nom="plaque")
        for k in range(2):
            m.bois("corps", s * (8.6 + k * 2.6), 33.9, 0.6, 1.4, 1.6, 1.4, s * 18, nom="arete")
        m.bois("tete", s * 6.5, 36.2, 2.6, 1.6, 2.2, 1.6, nom="oreille")
        m.cube("machoire", "croc", CREME, s * 3.4, 29.5, 8.4, 0.9, 2.4, 0.9)
        m.cube("machoire", "croc", CREME, s * 3.2, 31.0, 8.0, 0.6, 1.2, 0.6, -s * 16)
        m.sombre("corps", s * 12.0, 35.0, -1.5, 0.9, 3.6, 0.9, nom="pousse")
        m.cube("corps", "feuille", MOUSSE, s * 12.0, 37.2, -1.5, 2.6, 0.8, 1.6, s * 20)
        m.cube("corps", "feuille", MOUSSE, s * 11.0, 36.2, -1.5, 2.0, 0.7, 1.4, -s * 30)
        m.cube("corps", "feuille", MOUSSE, s * 12.0, 38.0, -1.5, 1.2, 0.9, 2.6)
        m.sombre("branches_dos", s * 5.0, 33.2, -5.6, 1.1, 4.6, 1.1, nom="branche")
        m.sombre("branches_dos", s * 6.6, 36.2, -5.6, 0.9, 3.0, 0.9, nom="branche")
        m.cube("branches_dos", "feuille", MOUSSE, s * 6.0, 38.0, -5.6, 3.0, 0.8, 2.0, s * 25)
        m.cube("branches_dos", "feuille", MOUSSE, s * 4.6, 35.8, -5.6, 2.2, 0.8, 1.6, -s * 30)
        m.cube("tete", "feuille", MOUSSE, s * 5.0, 58.2, 2.5, 2.4, 0.8, 1.8, -s * 20)
        m.cube("tete", "feuille", MOUSSE, s * 7.4, 53.4, 2.5, 2.0, 0.8, 1.6, s * 40)
    m.cube("tete", "mousse", MOUSSE, 0, 40.7, 2.5, 7, 1.2, 7)
    for k in range(4):
        m.cube("tete", "mousse", MOUSSE, -2.6 + k * 1.7, 41.8, 2.5, 1.0, 1.4, 1.0)
    for k in range(6):                                                    # ceinture de racines
        a = k * math.pi / 3
        m.sombre("base", math.cos(a) * 5.9, 6.9, math.sin(a) * 5.2, 2.4, 1.8, 2.4, nom="ceinture")
    m.bois("bras_g", -12.6, 12.6, 4.9, 4.6, 6, 0.6, nom="plaque")
    m.bois("bras_g", -12.2, 21.5, 3.8, 3.6, 7, 0.6, nom="plaque")
    for k in range(4):
        m.sombre("main_g", -15.2 + k * 1.9, 3.6, 6.6, 1.5, 2.4, 1.6, nom="bout_doigt")
        m.sombre("main_g", -15.2 + k * 1.9, 9.8, 4.4, 1.7, 1.2, 1.9, nom="phalange")
    m.sombre("main_g", -9.9, 6.4, 4.2, 2, 2.8, 2, nom="pouce")
    for k in range(3):
        m.sombre("main_g", -15.4 + k * 1.6, 4.0, 1.0 + k * 1.4, 0.7, 5.5 - k, 0.7, nom="liane")
    m.bois("poing_d", 9.8, 36.4, 8.0, 4.6, 4.2, 0.6, nom="plaque")
    m.bois("bras_d", 11.9, 31.8, 2.2, 0.6, 4.4, 4.2, nom="plaque")
    for k in range(4):
        m.sombre("poing_d", 6.2 + k * 2.2, 36.6, 11.9, 1.8, 2.4, 1.8, nom="doigt")
    m.sombre("poing_d", 13.2, 38.6, 9.2, 2.0, 2.0, 2.2, nom="pouce")
    for k in range(3):
        m.sombre("bras_d", 12.8 - k * 0.5, 28.8, 1.4 + k * 1.4, 0.7, 4.0, 0.7, nom="liane")
    for k in range(5):
        m.sombre("lianes", -6.0 + k * 3.0, 14.2 - (k % 2) * 1.2, 5.4, 0.7, 3.6 + (k % 2), 0.7, nom="liane")
    for s in (-1, 1):                                                     # veines de seve (lumineuses)
        m.cube("corps", "veine_", AMBRE, s * 5.6, 20.0, 5.75, 0.5, 5.0, 0.3, s * 12)
        m.cube("corps", "veine_", AMBRE, s * 6.4, 16.8, 5.75, 0.4, 3.0, 0.3, -s * 18)
        m.cube("corps", "veine_", AMBRE, s * 9.0, 30.0, 4.65, 0.4, 3.0, 0.3)
    # ------------------------------------------------ points faibles (os fixe), a l'identique de leurs boites de collision
    az, rayons, hauteurs, tailles, largeur = constantes_golem()
    for i in range(5):
        rm = rayons[i] / largeur
        a = math.radians(az[i])
        cx, cz = -rm * math.sin(a), rm * math.cos(a)
        t = tailles[i]
        w = t / largeur
        cy = hauteurs[i]
        for k in range(1, 5):
            f = 0.18 + 0.17 * k
            m.sombre("fixe", cx * f, cy, cz * f, 2.4, 2.4, 2.4, nom="noeud")
        m.cube("fixe", f"rouge{i}", ROUGE, cx, cy, cz, w * 0.72, t * 0.72, w * 0.72)
        m.cube("fixe", f"couv{i}", SOMBRE, cx, cy, cz, w, t, w)
    m.cube("tete", "lueur_oeil_g", AMBRE, -2.5, 36.0, 9.8, 2.8, 1.0, 0.4, 20)
    m.cube("tete", "lueur_oeil_d", AMBRE, 2.5, 36.0, 9.8, 2.8, 1.0, 0.4, -20)
    return m


def faces_box_uv(zone, de, a):
    """Faces en « box UV » (disposition de Blockbench et des modeles d'entite de Minecraft)."""
    u, v = zone
    w, h, d = (a[i] - de[i] for i in range(3))
    return {
        "north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0},
        "east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0},
        "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
        "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
        "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0},
        "down": {"uv": [u + d + 2 * w, v, u + d + w, v + d], "texture": 0},
    }


def ident(*parties):
    return str(uuid.uuid5(GRAINE, "/".join(str(p) for p in parties)))


def construire(animations=None):
    m = geometrie()
    elements = []
    enfants = {nom: [] for nom in OS}
    for os, cubes in m.cubes.items():
        for n, c in enumerate(cubes):
            e = {
                "name": c["name"], "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                "allow_mirror_modeling": True, "from": c["from"], "to": c["to"], "autouv": 0, "color": n % 8,
                "origin": c["origin"], "rotation": [0, 0, c["rz"]], "uv_offset": list(c["zone"]),
                "faces": faces_box_uv(c["zone"], c["from"], c["to"]), "type": "cube", "uuid": ident(os, n, c["name"]),
            }
            elements.append(e)
            enfants[os].append(e["uuid"])

    def groupe(nom):
        pivot = OS[nom][1]
        return {"name": nom, "origin": list(vers_bb(*pivot)), "color": 0, "uuid": ident("os", nom), "export": True,
                "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
                "children": enfants[nom] + [groupe(f) for f, (p, _) in OS.items() if p == nom]}

    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "golem", "model_identifier": "golem", "modded_entity_entity_class": "Golem",
        "modded_entity_version": "Mojang", "modded_entity_flip_y": True,
        "visible_box": [8, 15, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": 512, "height": 512},
        "elements": elements,
        "outliner": [groupe("racine")],
        "textures": [{
            "path": "", "name": "golem.png", "folder": "entity", "namespace": "grappin", "id": "0",
            "width": 512, "height": 512, "uv_width": 512, "uv_height": 512, "particle": False, "use_as_default": True,
            "layers_enabled": False, "render_mode": "default", "render_sides": "auto", "frame_time": 1,
            "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
            "internal": True, "saved": True, "uuid": ident("texture"), "relative_path": "",
            "source": "data:image/png;base64," + base64.b64encode(TEXTURE.read_bytes()).decode(),
        }],
        "animations": animations or [],
    }


# ------------------------------------------------ animations de depart (a retoucher dans Blockbench, onglet Animer)
# Valeurs dans le repere de Blockbench (y vers le haut, l'avant du Gardien vers -z) :
#   rotation x : negatif = se pencher en avant ; tete positive = tete en arriere ; machoire negative = bouche ouverte ;
#   bras : x positif = vers l'avant ; z positif = bras gauche (bras_g) ecarte, z negatif = bras droit (bras_d) ecarte.
# Lent et lourd : au plus 25 degres pour le corps, 35 pour la tete et la machoire, 60 pour un bras (teste).
Z = (0, 0, 0)
ANIMATIONS = {
    "repos": (4.0, "loop", {
        "corps": {"rotation": [(0, Z), (2, (-2, 0, 0)), (4, Z)], "position": [(0, Z), (2, (0, -0.5, 0)), (4, Z)]},
        "tete": {"rotation": [(0, Z), (1, (0, 3, 0)), (3, (0, -3, 0)), (4, Z)]},
        "machoire": {"rotation": [(0, Z), (2, (-2, 0, 0)), (4, Z)]},
        "branches_dos": {"rotation": [(0, Z), (1, (0, 0, 4)), (3, (0, 0, -4)), (4, Z)]},
        "lianes": {"rotation": [(0, Z), (2, (4, 0, 2)), (4, Z)]},
        "bras_g": {"rotation": [(0, Z), (2, (2, 0, 1)), (4, Z)]},
    }),
    "emergence": (5.0, "once", {
        "corps": {"rotation": [(0, (-20, 0, 0)), (1.5, (-14, 0, 0)), (3.0, Z), (5.0, Z)]},
        "tete": {"rotation": [(0, (-15, 0, 0)), (3.0, Z), (3.2, (20, 0, 0)), (4.2, (20, 0, 0)), (5.0, Z)]},
        "machoire": {"rotation": [(0, Z), (3.0, Z), (3.2, (-25, 0, 0)), (4.2, (-25, 0, 0)), (5.0, Z)]},
        "bras_g": {"rotation": [(0, (25, 0, -10)), (3.0, Z), (3.2, (0, 0, 20)), (4.2, (0, 0, 20)), (5.0, Z)]},
    }),
    "lancer": (1.5, "once", {
        "corps": {"rotation": [(0, Z), (0.6, (-8, 0, 0)), (0.9, (10, 0, 0)), (1.5, Z)]},
        "tete": {"rotation": [(0, Z), (0.6, (-5, 0, 0)), (0.9, (15, 0, 0)), (1.5, Z)]},
        "machoire": {"rotation": [(0, Z), (0.6, Z), (0.9, (-15, 0, 0)), (1.5, Z)]},
        "bras_g": {"rotation": [(0, Z), (0.6, (-30, 0, 0)), (0.9, (40, 0, 0)), (1.5, Z)]},
    }),
    "douleur": (0.75, "once", {
        "corps": {"rotation": [(0, Z), (0.15, (8, 0, 5)), (0.4, (6, 0, -3)), (0.75, Z)]},
        "tete": {"rotation": [(0, Z), (0.15, (25, 0, 0)), (0.75, Z)]},
        "machoire": {"rotation": [(0, Z), (0.15, (-20, 0, 0)), (0.75, Z)]},
        "bras_g": {"rotation": [(0, Z), (0.15, (0, 0, 15)), (0.75, Z)]},
        "bras_d": {"rotation": [(0, Z), (0.15, (0, 0, -15)), (0.75, Z)]},
    }),
    "rage": (2.5, "once", {
        "corps": {"rotation": [(0, Z), (0.6, (15, 0, 0)), (1.8, (15, 0, 0)), (2.5, Z)]},
        "tete": {"rotation": [(0, Z), (0.6, (30, 0, 0)), (1.8, (30, 0, 0)), (2.5, Z)]},
        "machoire": {"rotation": [(0, Z), (0.6, (-30, 0, 0)), (1.8, (-30, 0, 0)), (2.5, Z)]},
        "bras_g": {"rotation": [(0, Z), (0.6, (0, 0, 45)), (1.8, (0, 0, 45)), (2.5, Z)]},
        "bras_d": {"rotation": [(0, Z), (0.6, (0, 0, -30)), (1.8, (0, 0, -30)), (2.5, Z)]},
        "branches_dos": {"rotation": [(0, Z), (0.6, (0, 0, 8)), (1.2, (0, 0, -8)), (1.8, (0, 0, 8)), (2.5, Z)]},
    }),
    "mort": (2.0, "once", {
        "corps": {"rotation": [(0, Z), (0.8, (8, 0, 0)), (2.0, (-25, 0, 0))], "position": [(0, Z), (0.8, Z), (2.0, (0, -48, 0))]},
        "tete": {"rotation": [(0, Z), (0.8, (25, 0, 0)), (2.0, (-30, 0, 0))]},
        "machoire": {"rotation": [(0, Z), (0.8, (-30, 0, 0)), (2.0, (-10, 0, 0))]},
        "bras_g": {"rotation": [(0, Z), (0.8, (0, 0, 20)), (2.0, (0, 0, -20))]},
        "bras_d": {"rotation": [(0, Z), (2.0, (0, 0, 20))]},
    }),
}


def animations_bbmodel():
    resultat = []
    for nom, (duree, boucle, os_anims) in ANIMATIONS.items():
        animateurs = {}
        for os, canaux in os_anims.items():
            keyframes = []
            for canal, images in canaux.items():
                for t, (x, y, z) in images:
                    keyframes.append({"channel": canal, "data_points": [{"x": str(x), "y": str(y), "z": str(z)}],
                                      "uuid": ident("anim", nom, os, canal, t), "time": t, "color": -1, "interpolation": "catmullrom"})
            animateurs[ident("os", os)] = {"name": os, "type": "bone", "keyframes": keyframes}
        resultat.append({"uuid": ident("anim", nom), "name": nom, "loop": boucle, "override": False, "length": duree,
                         "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                         "loop_delay": "", "animators": animateurs})
    return resultat


def main():
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    modele = construire(animations_bbmodel())
    SORTIE.write_text(json.dumps(modele, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"golem.bbmodel ecrit : {len(modele['elements'])} cubes")


if __name__ == "__main__":
    main()
