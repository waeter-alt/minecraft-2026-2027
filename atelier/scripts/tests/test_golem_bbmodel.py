# [claude-build] Tests du modele Blockbench du Gardien des Racines (atelier/modeles/golem/golem.bbmodel).
# Lancer : python -I -m unittest discover -s atelier/scripts/tests -v   (depuis la racine du depot)
import base64
import json
import math
import re
import unittest
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
BBMODEL = RACINE / "atelier/modeles/golem/golem.bbmodel"
GOLEM_JAVA = RACINE / "atelier/donjon/arene/src/main/java/fr/atelier/grappin/Golem.java"
TEXTURE = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/textures/entity/golem.png"

# Nombre de cubes de GolemModele.creerCouche() avant le passage en os (compte fait a la main sur le code du 2026-11-17 :
# 138 cubes du corps et des veines, 5 x (4 noeuds + bourgeon + couvercle) = 30 pour les points faibles, 2 yeux).
CUBES_ORIGINE = 170

OS = {"racine": None, "base": "racine", "fixe": "racine", "corps": "racine", "tete": "corps", "machoire": "tete",
      "bras_g": "corps", "main_g": "bras_g", "bras_d": "corps", "poing_d": "bras_d", "branches_dos": "corps", "lianes": "corps"}


def constantes():
    texte = GOLEM_JAVA.read_text(encoding="utf-8")

    def tableau(nom):
        m = re.search(nom + r"\s*=\s*\{([^}]*)\}", texte)
        return [float(v) for v in m.group(1).split(",")]
    largeur = float(re.search(r"FACTEUR_LARGEUR\s*=\s*([0-9.]+)F", texte).group(1))
    return tableau("AZIMUTS"), tableau("RAYONS"), tableau("HAUTEURS"), tableau("TAILLES"), largeur


def charger():
    return json.loads(BBMODEL.read_text(encoding="utf-8"))


def arbre(modele):
    """{nom d'os: (parent, [elements])} a partir de l'outliner."""
    elements = {e["uuid"]: e for e in modele["elements"]}
    os = {}

    def visiter(groupe, parent):
        enfants = []
        os[groupe["name"]] = (parent, enfants)
        for c in groupe["children"]:
            if isinstance(c, dict):
                visiter(c, groupe["name"])
            else:
                enfants.append(elements[c])
    for g in modele["outliner"]:
        visiter(g, None)
    return os


def centre_java(e):
    """Centre d'un cube en coordonnees du modele Java (x_java = -x_bb, y_java = 24 - y_bb, z_java = z_bb)."""
    c = [(a + b) / 2 for a, b in zip(e["from"], e["to"])]
    return (-c[0], 24 - c[1], c[2])


class TestGolemBbmodel(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.modele = charger()
        cls.os = arbre(cls.modele)

    def test_os_presents(self):
        for nom, parent in OS.items():
            self.assertIn(nom, self.os, nom)
            self.assertEqual(parent, self.os[nom][0], nom)

    def test_points_faibles_dans_fixe_et_a_leur_place(self):
        az, rayons, hauteurs, tailles, largeur = constantes()
        fixe = {e["name"]: e for e in self.os["fixe"][1]}
        for i in range(5):
            self.assertIn(f"rouge{i}", fixe)
            couv = fixe[f"couv{i}"]
            rm = rayons[i] / largeur
            cx, cz = -rm * math.sin(math.radians(az[i])), rm * math.cos(math.radians(az[i]))
            attendu = (cx * 4, 24 - hauteurs[i] * 4, -cz * 4)
            for a, b in zip(centre_java(couv), attendu):
                self.assertAlmostEqual(a, b, delta=1e-6)
            t = tailles[i]
            w = t / largeur
            taille = [b - a for a, b in zip(couv["from"], couv["to"])]
            for a, b in zip(taille, (w * 4, t * 4, w * 4)):
                self.assertAlmostEqual(a, b, delta=1e-6)

    def test_meme_nombre_de_cubes(self):
        self.assertEqual(CUBES_ORIGINE, len(self.modele["elements"]))

    def test_texture_et_uv(self):
        self.assertEqual({"width": 512, "height": 512}, self.modele["resolution"])
        self.assertTrue(all(e["box_uv"] for e in self.modele["elements"]))
        source = self.modele["textures"][0]["source"]
        self.assertEqual(TEXTURE.read_bytes(), base64.b64decode(source.split(",", 1)[1]))

    def test_lueurs(self):
        noms = [e["name"] for e in self.modele["elements"]]
        self.assertGreaterEqual(sum(n.startswith("lueur_") for n in noms), 2)
        self.assertGreaterEqual(sum(n.startswith("veine_") for n in noms), 1)
        self.assertEqual(5, sum(re.fullmatch(r"rouge[0-4]", n) is not None for n in noms))


if __name__ == "__main__":
    unittest.main()
