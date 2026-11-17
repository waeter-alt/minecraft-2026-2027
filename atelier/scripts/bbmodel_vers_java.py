# [claude-build] Traduit un modele Blockbench d'entite (.bbmodel : os, box UV, animations) en deux classes Java pour Minecraft 1.21.1 :
#   <Nom>Geometrie : creerCouche() et les chemins des parties dont le rendu a besoin (lueurs, bourgeons, couvercles, tete) ;
#   <Nom>Animations : une AnimationDefinition par animation (systeme d'animation integre a Minecraft, comme le Warden).
# On n'utilise pas l'export Java de Blockbench : il ne garde pas les parties lumineuses ni les points faibles a part.
#   python bbmodel_vers_java.py <modele.bbmodel> <dossier java> [--paquet fr.atelier.grappin] [--nom Golem]
# Reperes : Blockbench a y vers le haut et les pieds en 0 ; Minecraft a y vers le bas et les pieds en 24, x inverse :
#   x_java = -x_bb, y_java = -y_bb (+24 a la racine), z_java = z_bb ; rotations (-rx, -ry, rz) ; posVec(-px, py, pz) (posVec inverse deja y).
# Chaque cube tourne, lumineux, bourgeon (rouge<i>) ou couvercle (couv<i>) devient sa propre partie, pour pouvoir le cacher ou l'agrandir.
import json
import math
import re
import sys
from pathlib import Path

ENTETE = "// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/golem/golem.bbmodel : ne pas modifier a la main.\n"
INTERPOLATIONS = {"linear": "LINEAR", "catmullrom": "CATMULLROM", "smooth": "CATMULLROM", "step": "LINEAR", "bezier": "CATMULLROM"}


def f(v):
    return repr(round(float(v), 6) + 0.0) + "F"


def lumineux(nom):
    return nom.startswith("lueur_") or nom.startswith("veine_") or re.fullmatch(r"rouge\d+", nom) is not None


def a_part(e):
    return lumineux(e["name"]) or re.fullmatch(r"couv\d+", e["name"]) is not None or any(abs(r) > 1e-9 for r in e.get("rotation", [0, 0, 0]))


def nom_part(e, n):
    if re.fullmatch(r"(rouge|couv)\d+", e["name"]):
        return e["name"]
    return f"{e['name'].rstrip('_')}_{n}"


def parcourir(modele):
    """Liste (chemin de l'os, groupe, parent, [elements]) dans l'ordre de l'arbre ; le chemin part de la racine (exclue)."""
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
    lueurs, rouges, couvercles, tete = [], {}, {}, None
    n = 0
    for chemin, g, _, els in parcourir(modele):
        if g["name"] == "tete":
            tete = chemin
        for e in els:
            if a_part(e):
                nom = nom_part(e, n)
                n += 1
                complet = (chemin + "/" if chemin else "") + nom
                if lumineux(e["name"]):
                    lueurs.append(complet)
                m = re.fullmatch(r"(rouge|couv)(\d+)", e["name"])
                if m:
                    (rouges if m.group(1) == "rouge" else couvercles)[int(m.group(2))] = complet
    return {"LUEURS": lueurs, "ROUGES": [rouges[i] for i in sorted(rouges)],
            "COUVERCLES": [couvercles[i] for i in sorted(couvercles)], "TETE": tete}


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


def java_geometrie(modele, paquet, classe):
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
    return (ENTETE + f"package {paquet};\n\n"
            "import net.minecraft.client.model.geom.PartPose;\n"
            "import net.minecraft.client.model.geom.builders.CubeListBuilder;\n"
            "import net.minecraft.client.model.geom.builders.LayerDefinition;\n"
            "import net.minecraft.client.model.geom.builders.MeshDefinition;\n"
            "import net.minecraft.client.model.geom.builders.PartDefinition;\n\n"
            f"public final class {classe} {{\n"
            f"    private {classe}() {{\n    }}\n\n"
            + tableau("LUEURS") + tableau("ROUGES") + tableau("COUVERCLES")
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


def java_animations(modele, paquet, classe):
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
                c = canal(anim["keyframes"], nom, cible, conv)
                if c:
                    lignes.append(f'            .addAnimation("{anim["name"]}", {c})')
        lignes[-1] += "\n            .build();"
        champs.append("\n".join(lignes))
    return (ENTETE + f"package {paquet};\n\n"
            "import net.minecraft.client.animation.AnimationChannel;\n"
            "import net.minecraft.client.animation.AnimationDefinition;\n"
            "import net.minecraft.client.animation.Keyframe;\n"
            "import net.minecraft.client.animation.KeyframeAnimations;\n\n"
            f"public final class {classe} {{\n"
            f"    private {classe}() {{\n    }}\n"
            + "".join("\n" + c + "\n" for c in champs) + "}\n")


def main():
    args = sys.argv[1:]
    paquet = args[args.index("--paquet") + 1] if "--paquet" in args else "fr.atelier.grappin"
    nom = args[args.index("--nom") + 1] if "--nom" in args else "Golem"
    modele = json.loads(Path(args[0]).read_text(encoding="utf-8"))
    dossier = Path(args[1])
    (dossier / f"{nom}Geometrie.java").write_text(java_geometrie(modele, paquet, nom + "Geometrie"), encoding="utf-8")
    (dossier / f"{nom}Animations.java").write_text(java_animations(modele, paquet, nom + "Animations"), encoding="utf-8")
    print(f"{nom}Geometrie.java et {nom}Animations.java ecrits dans {dossier}")


if __name__ == "__main__":
    main()
