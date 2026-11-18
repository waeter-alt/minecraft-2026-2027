# [claude-build] Rodeur des Racines (mini-boss du sous-sol 1 du Donjon Suspendu) : ecrit atelier/modeles/rodeur/rodeur.bbmodel, modele et
# animations pour Blockbench. Bete a quatre pattes de racines tressees, dos de mousse, gueule d'ecorce, yeux ambre, faite des memes matieres
# que le Gardien des Racines (meme texture : textures/entity/golem.png).
# Repere Blockbench : y vers le haut, pieds en y = 0, l'avant de la bete vers -z ; 16 unites = 1 bloc (pas d'echelle au rendu).
# Une fois retouche par Luca dans Blockbench, le .bbmodel est la source : ne plus relancer ce script. Ensuite :
#   python bbmodel_vers_java.py atelier/modeles/rodeur/rodeur.bbmodel <dossier java> --nom Rodeur
import base64
import json
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from golem_vers_bbmodel import faces_box_uv, TEXTURE  # noqa: E402

SORTIE = Path(__file__).resolve().parents[2] / "atelier/modeles/rodeur/rodeur.bbmodel"
GRAINE = uuid.UUID("7d0c5b2a-1e3f-4a6b-8c9d-0e1f00000000")
SOMBRE, ROUGE, MOUSSE = (0, 0), (0, 130), (0, 224)
CLAIR, CREME, CERNES, AMBRE = (260, 0), (330, 100), (330, 140), (330, 200)

# os : nom -> (parent, pivot)
OS = {
    "racine": (None, (0, 0, 0)),
    "corps": ("racine", (0, 20, 0)),
    "cou": ("corps", (0, 24, -16)),
    "tete": ("cou", (0, 24, -23)),
    "machoire": ("tete", (0, 20, -28)),
    "patte_av_g": ("corps", (6, 18, -12)),
    "patte_av_d": ("corps", (-6, 18, -12)),
    "patte_ar_g": ("corps", (6, 18, 10)),
    "patte_ar_d": ("corps", (-6, 18, 10)),
    "queue": ("corps", (0, 22, 16)),
    "crete": ("corps", (0, 29, 0)),
    "lianes": ("corps", (0, 13, 0)),
}

CUBES = {nom: [] for nom in OS}


def cube(os, nom, zone, de, a, rotation=(0, 0, 0)):
    CUBES[os].append({"name": nom, "zone": zone, "from": list(de), "to": list(a), "rotation": list(rotation)})


def miroir(os_g, os_d, nom, zone, de, a, rotation=(0, 0, 0)):
    """Le meme cube a gauche (x > 0) et a droite (x miroir)."""
    cube(os_g, nom + "_g", zone, de, a, rotation)
    cube(os_d, nom + "_d", zone, (-a[0], de[1], de[2]), (-de[0], a[1], a[2]), (rotation[0], -rotation[1], -rotation[2]))


def geometrie():
    # ------------------------------------------------ corps : racines tressees, dos de mousse
    cube("corps", "racine_dos_g", SOMBRE, (1, 20, -15), (9, 28, 13))
    cube("corps", "racine_dos_d", CERNES, (-9, 20, -13), (-1, 28, 15))
    cube("corps", "racine_ventre_g", CERNES, (0, 13, -13), (8, 21, 12))
    cube("corps", "racine_ventre_d", SOMBRE, (-8, 13, -12), (0, 21, 13))
    cube("corps", "coeur", SOMBRE, (-6, 15, -12), (6, 26, 12))
    cube("corps", "poitrail", SOMBRE, (-8, 12, -19), (8, 26, -12))
    cube("corps", "croupe", CERNES, (-7, 14, 11), (7, 26, 17))
    cube("corps", "mousse_dos", MOUSSE, (-8, 28, -13), (8, 31, 11))
    cube("corps", "mousse_epaule", MOUSSE, (-7, 26, -18), (7, 29, -12))
    for k, z in enumerate((-9, 0, 9)):                                      # liens de racines autour du corps
        cube("corps", f"lien{k}", CERNES, (-9.5, 18 + k % 2, z), (9.5, 21 + k % 2, z + 2))
    # ------------------------------------------------ cou, tete d'ecorce, machoire, crocs, yeux ambre
    cube("cou", "cou", SOMBRE, (-5, 18, -25), (5, 28, -15))
    cube("cou", "mousse_cou", MOUSSE, (-4, 28, -24), (4, 30, -16))
    cube("tete", "crane", CLAIR, (-6, 18, -35), (6, 29, -23))
    cube("tete", "museau", CLAIR, (-4, 19, -42), (4, 25, -35))
    cube("tete", "arcade", SOMBRE, (-6.5, 26, -36), (6.5, 28, -32))
    cube("tete", "truffe", SOMBRE, (-2, 23, -42.5), (2, 25, -41.5))
    miroir("tete", "tete", "corne", SOMBRE, (3, 29, -30), (5, 33, -28), (-20, 0, -15))
    miroir("tete", "tete", "corne_bout", CLAIR, (4, 32, -32), (5.5, 35, -30.5), (-35, 0, -25))
    miroir("tete", "tete", "croc_haut", CREME, (2.5, 17, -41.5), (3.5, 20, -40.5))
    cube("tete", "lueur_oeil_g", AMBRE, (2.2, 25, -35.4), (5.2, 26.5, -34.9))
    cube("tete", "lueur_oeil_d", AMBRE, (-5.2, 25, -35.4), (-2.2, 26.5, -34.9))
    cube("machoire", "machoire", SOMBRE, (-4, 15, -42), (4, 19, -28))
    miroir("machoire", "machoire", "croc_bas", CREME, (1.5, 19, -41.5), (2.5, 22, -40.5))
    cube("machoire", "barbe", MOUSSE, (-3, 13.5, -38), (3, 15, -31))
    # ------------------------------------------------ pattes : cuisse, jambe, griffes
    miroir("patte_av_g", "patte_av_d", "epaule", SOMBRE, (3, 8, -15), (9.5, 19, -9))
    miroir("patte_av_g", "patte_av_d", "jambe_av", CERNES, (3.5, 0, -16), (9, 8, -9.5))
    miroir("patte_av_g", "patte_av_d", "griffe_av", CREME, (4, 0, -18), (8.5, 2, -16))
    miroir("patte_ar_g", "patte_ar_d", "cuisse", SOMBRE, (3, 8, 7), (10, 20, 14))
    miroir("patte_ar_g", "patte_ar_d", "jambe_ar", CERNES, (3.5, 0, 8), (9, 8, 14))
    miroir("patte_ar_g", "patte_ar_d", "griffe_ar", CREME, (4, 0, 6), (8.5, 2, 8))
    # ------------------------------------------------ queue de racines, touffe de mousse
    cube("queue", "queue", SOMBRE, (-2, 18, 16), (2, 22, 26))
    cube("queue", "queue_bout", CERNES, (-1.5, 17, 26), (1.5, 20, 30))
    cube("queue", "queue_mousse", MOUSSE, (-2.5, 17.5, 28), (2.5, 21.5, 31))
    # ------------------------------------------------ crete de racines pointues le long de l'echine
    for k, z in enumerate((-12, -6, 0, 6)):
        h = (6, 7, 6, 4)[k]
        cube("crete", f"pointe{k}", SOMBRE if k % 2 else CLAIR, (-1, 29, z), (1, 29 + h, z + 2), (-25, 0, 0))
    # ------------------------------------------------ lianes pendantes et baies lumineuses
    for k, (x, z, lg) in enumerate(((5, -8, 6), (-5, -2, 5), (4, 5, 7), (-4, 9, 4))):
        cube("lianes", f"liane{k}", MOUSSE, (x - 0.5, 13 - lg, z - 0.5), (x + 0.5, 13, z + 0.5))
        if k < 3:
            cube("lianes", f"lueur_baie{k}", AMBRE, (x - 1, 12 - lg, z - 1), (x + 1, 14 - lg, z + 1))


# ------------------------------------------------ animations (degres, repere Blockbench)
# Rotation x positive : patte vers l'avant, museau vers le haut ; negative : museau ou machoire vers le bas (gueule ouverte).
Z = (0, 0, 0)
ANIMATIONS = {
    "repos": (3.0, "loop", {
        "corps": {"position": [(0, Z), (1.5, (0, -0.5, 0)), (3, Z)]},
        "tete": {"rotation": [(0, Z), (1.2, (-6, 4, 0)), (1.6, (-3, -4, 0)), (3, Z)]},
        "machoire": {"rotation": [(0, Z), (1.4, (-4, 0, 0)), (3, Z)]},
        "queue": {"rotation": [(0, Z), (0.75, (0, 10, 0)), (2.25, (0, -10, 0)), (3, Z)]},
        "lianes": {"rotation": [(0, Z), (1.5, (5, 0, 3)), (3, Z)]},
    }),
    "marche": (1.0, "loop", {
        "patte_av_g": {"rotation": [(0, Z), (0.25, (25, 0, 0)), (0.75, (-25, 0, 0)), (1, Z)]},
        "patte_ar_d": {"rotation": [(0, Z), (0.25, (25, 0, 0)), (0.75, (-25, 0, 0)), (1, Z)]},
        "patte_av_d": {"rotation": [(0, Z), (0.25, (-25, 0, 0)), (0.75, (25, 0, 0)), (1, Z)]},
        "patte_ar_g": {"rotation": [(0, Z), (0.25, (-25, 0, 0)), (0.75, (25, 0, 0)), (1, Z)]},
        "tete": {"rotation": [(0, Z), (0.5, (-4, 0, 0)), (1, Z)]},
        "queue": {"rotation": [(0, Z), (0.5, (0, 8, 0)), (1, Z)]},
    }),
    "morsure": (0.5, "once", {
        "tete": {"rotation": [(0, Z), (0.15, (12, 0, 0)), (0.3, (-15, 0, 0)), (0.5, Z)]},
        "machoire": {"rotation": [(0, Z), (0.15, (-35, 0, 0)), (0.3, Z), (0.5, Z)]},
        "corps": {"position": [(0, Z), (0.3, (0, 0, -3)), (0.5, Z)]},
    }),
    "grattage": (1.0, "loop", {
        "patte_av_d": {"rotation": [(0, Z), (0.25, (-35, 0, 0)), (0.5, (20, 0, 0)), (0.75, (-35, 0, 0)), (1, Z)]},
        "tete": {"rotation": [(0, (-20, 0, 0)), (0.5, (-24, 0, 0)), (1, (-20, 0, 0))]},
        "corps": {"rotation": [(0, (-4, 0, 0)), (1, (-4, 0, 0))], "position": [(0, (0, -1, 0)), (1, (0, -1, 0))]},
        "queue": {"rotation": [(0, Z), (0.25, (0, 15, 0)), (0.75, (0, -15, 0)), (1, Z)]},
    }),
    "charge": (0.5, "loop", {
        "patte_av_g": {"rotation": [(0, (40, 0, 0)), (0.25, (-40, 0, 0)), (0.5, (40, 0, 0))]},
        "patte_av_d": {"rotation": [(0, (40, 0, 0)), (0.25, (-40, 0, 0)), (0.5, (40, 0, 0))]},
        "patte_ar_g": {"rotation": [(0, (-40, 0, 0)), (0.25, (40, 0, 0)), (0.5, (-40, 0, 0))]},
        "patte_ar_d": {"rotation": [(0, (-40, 0, 0)), (0.25, (40, 0, 0)), (0.5, (-40, 0, 0))]},
        "corps": {"rotation": [(0, (-5, 0, 0)), (0.25, (3, 0, 0)), (0.5, (-5, 0, 0))], "position": [(0, (0, -2, 0)), (0.25, Z), (0.5, (0, -2, 0))]},
        "tete": {"rotation": [(0, (-15, 0, 0)), (0.5, (-15, 0, 0))]},
        "queue": {"rotation": [(0, (15, 0, 0)), (0.5, (15, 0, 0))]},
    }),
    "sonne": (1.5, "loop", {
        "tete": {"rotation": [(0, (-10, 0, 0)), (0.375, (-10, 0, 15)), (1.125, (-10, 0, -15)), (1.5, (-10, 0, 0))]},
        "corps": {"rotation": [(0, Z), (0.375, (0, 0, 6)), (1.125, (0, 0, -6)), (1.5, Z)]},
        "machoire": {"rotation": [(0, (-12, 0, 0)), (1.5, (-12, 0, 0))]},
        "crete": {"rotation": [(0, Z), (0.75, (-8, 0, 0)), (1.5, Z)]},
    }),
    "reveil": (2.5, "once", {
        "corps": {"position": [(0, (0, -24, 0)), (1.2, Z), (2.5, Z)], "rotation": [(0, (-20, 0, 0)), (1.2, Z), (2.5, Z)]},
        "tete": {"rotation": [(0, (-25, 0, 0)), (1.2, Z), (1.5, (0, 0, 20)), (1.7, (0, 0, -20)), (1.9, (0, 0, 15)), (2.1, Z), (2.5, Z)]},
        "machoire": {"rotation": [(0, Z), (2.1, Z), (2.3, (-30, 0, 0)), (2.5, Z)]},
    }),
    "mort": (1.0, "once", {
        "corps": {"rotation": [(0, Z), (0.8, (0, 0, 80)), (1, (0, 0, 85))], "position": [(0, Z), (1, (0, -10, 0))]},
        "tete": {"rotation": [(0, Z), (1, (-20, 0, 0))]},
        "patte_av_g": {"rotation": [(0, Z), (1, (0, 0, 20))]},
        "patte_ar_g": {"rotation": [(0, Z), (1, (0, 0, 20))]},
        "patte_av_d": {"rotation": [(0, Z), (1, (0, 0, -20))]},
        "patte_ar_d": {"rotation": [(0, Z), (1, (0, 0, -20))]},
        "crete": {"rotation": [(0, Z), (1, (-30, 0, 0))]},
    }),
}


def ident(*p):
    return str(uuid.uuid5(GRAINE, "/".join(str(x) for x in p)))


def construire():
    geometrie()
    elements, enfants = [], {nom: [] for nom in OS}
    for os, cubes in CUBES.items():
        for n, c in enumerate(cubes):
            origine = [(c["from"][k] + c["to"][k]) / 2 for k in range(3)] if any(c["rotation"]) else list(OS[os][1])
            e = {"name": c["name"], "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                 "allow_mirror_modeling": True, "from": c["from"], "to": c["to"], "autouv": 0, "color": n % 8,
                 "origin": origine, "rotation": c["rotation"], "uv_offset": list(c["zone"]),
                 "faces": faces_box_uv(c["zone"], c["from"], c["to"]), "type": "cube", "uuid": ident(os, n, c["name"])}
            elements.append(e)
            enfants[os].append(e["uuid"])

    def groupe(nom):
        return {"name": nom, "origin": list(OS[nom][1]), "color": 0, "uuid": ident("os", nom), "export": True, "mirror_uv": False,
                "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
                "children": enfants[nom] + [groupe(f) for f, (p, _) in OS.items() if p == nom]}
    animations = []
    for nom, (duree, boucle, os_anims) in ANIMATIONS.items():
        animateurs = {}
        for os, canaux in os_anims.items():
            kfs = [{"channel": canal, "data_points": [{"x": str(x), "y": str(y), "z": str(z)}], "uuid": ident("anim", nom, os, canal, t),
                    "time": t, "color": -1, "interpolation": "catmullrom"}
                   for canal, images in canaux.items() for t, (x, y, z) in images]
            animateurs[ident("os", os)] = {"name": os, "type": "bone", "keyframes": kfs}
        animations.append({"uuid": ident("anim", nom), "name": nom, "loop": boucle, "override": False, "length": duree, "snapping": 20,
                           "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                           "animators": animateurs})
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "rodeur", "model_identifier": "rodeur", "modded_entity_entity_class": "Rodeur", "modded_entity_version": "Mojang",
        "modded_entity_flip_y": True, "visible_box": [2, 2.5, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "timeline_setups": [], "unhandled_root_fields": {}, "resolution": {"width": 512, "height": 512},
        "elements": elements, "outliner": [groupe("racine")],
        "textures": [{"path": "", "name": "golem.png", "folder": "entity", "namespace": "grappin", "id": "0", "width": 512, "height": 512,
                      "uv_width": 512, "uv_height": 512, "particle": False, "use_as_default": True, "layers_enabled": False,
                      "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
                      "frame_interpolate": False, "visible": True, "internal": True, "saved": True, "uuid": ident("texture"),
                      "relative_path": "", "source": "data:image/png;base64," + base64.b64encode(TEXTURE.read_bytes()).decode()}],
        "animations": animations,
    }


def main():
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    m = construire()
    SORTIE.write_text(json.dumps(m, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"rodeur.bbmodel ecrit : {len(m['elements'])} cubes, {len(m['animations'])} animations")


if __name__ == "__main__":
    main()
