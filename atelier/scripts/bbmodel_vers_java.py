# [claude-build] Traduit un modele Blockbench d'entite (.bbmodel : os, box UV, animations) en deux classes Java pour Minecraft 1.21.1 :
#   <Nom>Geometrie : creerCouche() et les chemins des parties dont le rendu a besoin (lueurs, bourgeons, masque et petales des points faibles, tete) ;
#   <Nom>Animations : une AnimationDefinition par animation (systeme d'animation integre a Minecraft, comme le Warden).
#   <Nom>Squelette : le meme modele pour le serveur (classe Squelette), qui y calcule ou sont les points faibles.
# On n'utilise pas l'export Java de Blockbench : il ne garde pas les parties lumineuses ni les points faibles a part.
#   python bbmodel_vers_java.py <modele.bbmodel> <dossier java> [--paquet fr.atelier.grappin] [--nom Golem]
# Reperes : Blockbench a y vers le haut et les pieds en 0 ; Minecraft a y vers le bas et les pieds en 24, x inverse :
#   x_java = -x_bb, y_java = -y_bb (+24 a la racine), z_java = z_bb ; rotations (-rx, -ry, rz) ; posVec(-px, py, pz) (posVec inverse deja y).
# Chaque cube tourne, lumineux ou bourgeon (rouge<i>) devient sa propre partie, pour pouvoir le rendre lumineux ou l'agrandir.
import json
import math
import re
import sys
from pathlib import Path

SOURCE_GOLEM = "atelier/modeles/golem/golem.bbmodel"


def entete(source):
    return f"// Genere par atelier/scripts/bbmodel_vers_java.py depuis {source} : ne pas modifier a la main.\n"
INTERPOLATIONS = {"linear": "LINEAR", "catmullrom": "CATMULLROM", "smooth": "CATMULLROM", "step": "LINEAR", "bezier": "CATMULLROM"}


def f(v):
    return repr(round(float(v), 6) + 0.0) + "F"


def lumineux(nom):
    return nom.startswith("lueur_") or nom.startswith("veine_") or re.fullmatch(r"rouge\d+", nom) is not None


def a_part(e):
    return lumineux(e["name"]) or any(abs(r) > 1e-9 for r in e.get("rotation", [0, 0, 0]))


def nom_part(e, n):
    if re.fullmatch(r"rouge\d+", e["name"]):
        return e["name"]
    return f"{e['name'].rstrip('_')}_{n}"


def normaliser(modele):
    """Remet un fichier de Blockbench 5 sous la forme des fichiers 4.x pour le reste du script : Blockbench 5 range les os dans
    `groups` (l'outliner ne garde que leurs uuid), met `rotation: null` aux cubes droits, et enregistre les rotations animees avec
    x et y de signe oppose (en reenregistrant un fichier 4.10, il les inverse pour garder le meme mouvement a l'ecran)."""
    if str(modele.get("meta", {}).get("format_version", "4")).split(".")[0] < "5" and "groups" not in modele:
        return modele
    groupes = {g["uuid"]: g for g in modele.get("groups", [])}

    def complet(noeud):
        if not isinstance(noeud, dict):
            return noeud
        g = dict(groupes.get(noeud["uuid"], {}), **noeud)
        g["children"] = [complet(c) for c in noeud.get("children", [])]
        return g
    m = dict(modele)
    m["outliner"] = [complet(n) for n in modele["outliner"]]
    m["elements"] = [dict(e, rotation=e.get("rotation") or [0, 0, 0]) for e in modele["elements"]]
    m.pop("groups", None)
    m["meta"] = dict(modele.get("meta", {}), format_version="4.10")         # deja normalise : un second passage ne change rien
    if int(str(modele.get("meta", {}).get("format_version", "4")).split(".")[0]) >= 5:
        m["animations"] = []
        for a in modele.get("animations", []):
            animateurs = {}
            for cle, anim in a.get("animators", {}).items():
                images = []
                for k in anim.get("keyframes", []):
                    if k["channel"] == "rotation":
                        k = dict(k, data_points=[dict(p, x=str(-float(p["x"])), y=str(-float(p["y"]))) for p in k["data_points"]])
                    images.append(k)
                animateurs[cle] = dict(anim, keyframes=images)
            m["animations"].append(dict(a, animators=animateurs))
    return m


def parcourir(modele):
    """Liste (chemin de l'os, groupe, parent, [elements]) dans l'ordre de l'arbre ; le chemin part de la racine (exclue)."""
    modele = normaliser(modele)
    elements = {e["uuid"]: e for e in modele["elements"]}
    resultat = []

    def visiter(g, parent, chemin):
        resultat.append((chemin, g, parent, [elements[c] for c in g["children"] if not isinstance(c, dict)]))
        for c in g["children"]:
            if isinstance(c, dict):
                visiter(c, g, (chemin + "/" if chemin else "") + c["name"])
    for g in modele["outliner"]:
        visiter(g, None, "")
    return resultat


def chemins(modele):
    lueurs, rouges, tete = [], {}, None
    masques, petales = {}, {}
    n = 0
    for chemin, g, _, els in parcourir(modele):
        if g["name"] == "tete":
            tete = chemin
        m = re.fullmatch(r"pf(\d+)_masque", g["name"])
        if m:
            masques[int(m.group(1))] = chemin
        m = re.fullmatch(r"pf(\d+)_petale(\d+)", g["name"])
        if m:
            petales.setdefault(int(m.group(1)), {})[int(m.group(2))] = chemin
        for e in els:
            if a_part(e):
                nom = nom_part(e, n)
                n += 1
                complet = (chemin + "/" if chemin else "") + nom
                if lumineux(e["name"]):
                    lueurs.append(complet)
                m = re.fullmatch(r"rouge(\d+)", e["name"])
                if m:
                    rouges[int(m.group(1))] = complet
    indices = sorted(rouges)
    return {"LUEURS": lueurs, "ROUGES": [rouges[i] for i in indices], "TETE": tete,
            "MASQUES": [masques.get(i, "") for i in indices],
            "PETALES": [[petales.get(i, {})[k] for k in sorted(petales.get(i, {}))] for i in indices]}


def boite(e, ref):
    """addBox(...) d'un cube, relatif au point ref (en repere Blockbench)."""
    de, a = e["from"], e["to"]
    x = -(a[0] - ref[0])
    y = -(a[1] - ref[1])
    z = de[2] - ref[2]
    w, h, d = (a[i] - de[i] for i in range(3))
    u, v = e.get("uv_offset", [0, 0])
    return f".texOffs({int(u)}, {int(v)}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"


def pose(point, ref, rot, racine=False):
    ox = -(point[0] - ref[0])
    oy = (24 - point[1]) if racine else -(point[1] - ref[1])
    oz = point[2] - ref[2]
    rx, ry, rz = (math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2]))
    return f"PartPose.offsetAndRotation({f(ox)}, {f(oy)}, {f(oz)}, {f(rx)}, {f(ry)}, {f(rz)})"


def java_geometrie(modele, paquet, classe, source=SOURCE_GOLEM):
    lignes = []
    variables = {}
    n = 0
    for k, (chemin, g, parent, els) in enumerate(parcourir(modele)):
        var = f"p{k}"
        variables[id(g)] = var
        mere = "maillage.getRoot()" if parent is None else variables[id(parent)]
        ref_parent = [0, 0, 0] if parent is None else parent["origin"]
        cubes = "".join(boite(e, g["origin"]) for e in els if not a_part(e))
        lignes.append(f'        PartDefinition {var} = {mere}.addOrReplaceChild("{g["name"]}", CubeListBuilder.create(){cubes},')
        lignes.append(f"                {pose(g['origin'], ref_parent, g.get('rotation', [0, 0, 0]), parent is None)});")
        for e in els:
            if a_part(e):
                nom = nom_part(e, n)
                n += 1
                o = e.get("origin", g["origin"])
                lignes.append(f'        {var}.addOrReplaceChild("{nom}", CubeListBuilder.create(){boite(e, o)},')
                lignes.append(f"                {pose(o, g['origin'], e.get('rotation', [0, 0, 0]))});")
    ch = chemins(modele)

    def tableau(nom):
        return f"    public static final String[] {nom} = {{\n" + "".join(f'        "{c}",\n' for c in ch[nom]) + "    };\n"
    largeur, hauteur = modele["resolution"]["width"], modele["resolution"]["height"]
    return (entete(source) + f"package {paquet};\n\n"
            "import net.minecraft.client.model.geom.PartPose;\n"
            "import net.minecraft.client.model.geom.builders.CubeListBuilder;\n"
            "import net.minecraft.client.model.geom.builders.LayerDefinition;\n"
            "import net.minecraft.client.model.geom.builders.MeshDefinition;\n"
            "import net.minecraft.client.model.geom.builders.PartDefinition;\n\n"
            f"public final class {classe} {{\n"
            f"    private {classe}() {{\n    }}\n\n"
            + tableau("LUEURS") + tableau("ROUGES") + tableau("MASQUES")
            + "    public static final String[][] PETALES = {\n"
            + "".join("        {" + ", ".join(f'"{c}"' for c in p) + "},\n" for p in ch["PETALES"]) + "    };\n"
            + f'    public static final String TETE = "{ch["TETE"] or ""}";\n\n'
            "    public static LayerDefinition creerCouche() {\n"
            "        MeshDefinition maillage = new MeshDefinition();\n"
            + "\n".join(lignes) + "\n"
            f"        return LayerDefinition.create(maillage, {largeur}, {hauteur});\n"
            "    }\n}\n")


def canal(keyframes, nom, cible, conv):
    ks = sorted((k for k in keyframes if k["channel"] == nom), key=lambda k: k["time"])
    if not ks:
        return None
    images = []
    for k in ks:
        p = k["data_points"][0]
        x, y, z = (float(p[a]) for a in "xyz")
        interp = INTERPOLATIONS.get(k.get("interpolation", "linear"), "LINEAR")
        images.append(f"new Keyframe({f(k['time'])}, {conv(x, y, z)}, AnimationChannel.Interpolations.{interp})")
    return f"new AnimationChannel(AnimationChannel.Targets.{cible},\n                        " + ",\n                        ".join(images) + ")"


def java_animations(modele, paquet, classe, source=SOURCE_GOLEM):
    modele = normaliser(modele)
    champs = []
    for a in modele.get("animations", []):
        lignes = [f"    public static final AnimationDefinition {a['name'].upper()} = AnimationDefinition.Builder.withLength({f(a['length'])})"]
        if a.get("loop") == "loop":
            lignes.append("            .looping()")
        for anim in a.get("animators", {}).values():
            if anim.get("type", "bone") != "bone":
                continue
            for nom, cible, conv in (("rotation", "ROTATION", lambda x, y, z: f"KeyframeAnimations.degreeVec({f(-x)}, {f(-y)}, {f(z)})"),
                                     ("position", "POSITION", lambda x, y, z: f"KeyframeAnimations.posVec({f(-x)}, {f(y)}, {f(z)})")):
                c = canal(anim.get("keyframes", []), nom, cible, conv)
                if c:
                    lignes.append(f'            .addAnimation("{anim["name"]}", {c})')
        lignes[-1] += "\n            .build();"
        champs.append("\n".join(lignes))
    return (entete(source) + f"package {paquet};\n\n"
            "import net.minecraft.client.animation.AnimationChannel;\n"
            "import net.minecraft.client.animation.AnimationDefinition;\n"
            "import net.minecraft.client.animation.Keyframe;\n"
            "import net.minecraft.client.animation.KeyframeAnimations;\n\n"
            f"public final class {classe} {{\n"
            f"    private {classe}() {{\n    }}\n"
            + "".join("\n" + c + "\n" for c in champs) + "}\n")


def java_squelette(modele, paquet, classe, source=SOURCE_GOLEM):
    """Le meme modele pour le serveur (classe Squelette, sans Minecraft) : os, pivots et canaux, valeurs dans le repere de Minecraft."""
    modele = normaliser(modele)
    os_lignes = []
    for chemin, g, parent, _ in parcourir(modele):
        assert all(abs(r) < 1e-9 for r in g.get("rotation", [0, 0, 0])), f"os {g['name']} : rotation de repos non geree par Squelette"
        o = g["origin"]
        ref = [0, 0, 0] if parent is None else parent["origin"]
        y = (24 - o[1]) if parent is None else -(o[1] - ref[1])
        nom_parent = "null" if parent is None else f'"{parent["name"]}"'
        os_lignes.append(f'            new Squelette.Os("{g["name"]}", {nom_parent}, {f(-(o[0] - ref[0]))}, {f(y)}, {f(o[2] - ref[2])})')
    anims = []
    for a in modele.get("animations", []):
        canaux = []
        for anim in a.get("animators", {}).values():
            for nom, rotation in (("rotation", True), ("position", False)):
                ks = sorted((k for k in anim.get("keyframes", []) if k["channel"] == nom), key=lambda k: k["time"])
                if not ks:
                    continue
                vals = []
                for k in ks:
                    x, y, z = (float(k["data_points"][0][c]) for c in "xyz")
                    vals.append((-x, -y, z))
                temps = ", ".join(f(k["time"]) for k in ks)
                valeurs = ", ".join("{" + ", ".join(f(v) for v in t) + "}" for t in vals)
                catmull = ", ".join("true" if INTERPOLATIONS.get(k.get("interpolation", "linear")) == "CATMULLROM" else "false" for k in ks)
                canaux.append(f'                    new Squelette.Canal("{anim["name"]}", {str(rotation).lower()}, new float[] {{{temps}}}, '
                              f'new float[][] {{{valeurs}}}, new boolean[] {{{catmull}}})')
        anims.append(f'            Map.entry("{a["name"]}", new Squelette.Anim({f(a["length"])}, {str(a.get("loop") == "loop").lower()}, List.of(\n'
                     + ",\n".join(canaux) + ")))")
    return (entete(source) + f"package {paquet};\n\n"
            "import java.util.List;\n"
            "import java.util.Map;\n\n"
            f"public final class {classe} {{\n"
            f"    private {classe}() {{\n    }}\n\n"
            "    public static final Squelette SQUELETTE = new Squelette(\n"
            "        List.of(\n" + ",\n".join(os_lignes) + "),\n"
            "        Map.ofEntries(\n" + ",\n".join(anims) + "));\n"
            "}\n")


def main():
    args = sys.argv[1:]
    paquet = args[args.index("--paquet") + 1] if "--paquet" in args else "fr.atelier.grappin"
    nom = args[args.index("--nom") + 1] if "--nom" in args else "Golem"
    modele = json.loads(Path(args[0]).read_text(encoding="utf-8"))
    source = Path(args[0]).as_posix()
    if "atelier/" in source:
        source = source[source.index("atelier/"):]
    dossier = Path(args[1])
    (dossier / f"{nom}Geometrie.java").write_text(java_geometrie(modele, paquet, nom + "Geometrie", source), encoding="utf-8")
    (dossier / f"{nom}Animations.java").write_text(java_animations(modele, paquet, nom + "Animations", source), encoding="utf-8")
    (dossier / f"{nom}Squelette.java").write_text(java_squelette(modele, paquet, nom + "Squelette", source), encoding="utf-8")
    print(f"{nom}Geometrie, {nom}Animations et {nom}Squelette ecrits dans {dossier}")


if __name__ == "__main__":
    main()
