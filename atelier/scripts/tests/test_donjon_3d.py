# [claude-build] Tests des modeles Blockbench de la porte du Donjon Suspendu et du levier de racine (atelier/modeles/donjon/).
import json
import sys
import unittest
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "atelier/scripts"))
from bbmodel_vers_java import normaliser  # noqa: E402

DOSSIER = RACINE / "atelier/modeles/donjon"


def charger(nom):
    return normaliser(json.loads((DOSSIER / f"{nom}.bbmodel").read_text(encoding="utf-8")))


def arbre(m):
    os = {}

    def visiter(g, parent):
        os[g["name"]] = parent
        for c in g["children"]:
            if isinstance(c, dict):
                visiter(c, g["name"])
    for g in m["outliner"]:
        visiter(g, None)
    return os


def fin(anim, os, canal):
    for a in anim["animators"].values():
        if a["name"] == os:
            ks = [k for k in a.get("keyframes", []) if k["channel"] == canal]
            if ks:
                return [float(v) for v in max(ks, key=lambda k: k["time"])["data_points"][0].values()]
    return [0.0, 0.0, 0.0]


class TestPorte(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = charger("porte_donjon")

    def test_os(self):
        self.assertEqual({"racine": None, "dalle": "racine", "sceau": "dalle"}, arbre(self.m))

    def test_taille_arche_8_sur_8(self):
        de = [min(e["from"][k] for e in self.m["elements"]) for k in range(3)]
        a = [max(e["to"][k] for e in self.m["elements"]) for k in range(3)]
        self.assertAlmostEqual(128, a[0] - de[0], delta=1)
        self.assertAlmostEqual(128, a[1] - de[1], delta=1)
        self.assertLessEqual(a[2] - de[2], 20)

    def test_en_arche(self):
        """Demande de Luca : des portes en arche, pas carrees (meme profil que les arches du donjon, suspendu_salle1.profil : environ 6 blocs au bord, 8 au milieu)."""
        def haut(x0, x1):
            return max((e["to"][1] for e in self.m["elements"] if e["from"][0] < x1 and e["to"][0] > x0), default=0)
        self.assertLessEqual(haut(-64, -56), 100)
        self.assertLessEqual(haut(56, 64), 100)
        self.assertGreaterEqual(haut(-4, 4), 124)
        self.assertLess(haut(-40, -32), haut(-8, 0))

    def test_ouverture_monte_sans_tourner(self):
        anims = {a["name"]: a for a in self.m["animations"]}
        o = anims["ouverture"]
        self.assertAlmostEqual(3.0, o["length"])
        self.assertEqual("once", o["loop"])
        self.assertGreaterEqual(fin(o, "dalle", "position")[1], 128)
        self.assertEqual([0.0, 0.0, 0.0], fin(o, "dalle", "rotation"))

    def test_sceau_lumineux(self):
        self.assertTrue(any(e["name"].startswith("lueur_") for e in self.m["elements"]))


class TestLevier(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = charger("levier_racine")

    def test_os(self):
        self.assertEqual({"racine": None, "socle": "racine", "cliquet": "socle", "manche": "socle", "cristal": "manche"}, arbre(self.m))

    def test_hauteur(self):
        h = max(e["to"][1] for e in self.m["elements"]) - min(e["from"][1] for e in self.m["elements"])
        self.assertTrue(28 <= h <= 36, h)

    def test_animations(self):
        anims = {a["name"]: a for a in self.m["animations"]}
        self.assertEqual({"tirer", "relacher"}, set(anims))
        for a in anims.values():
            self.assertAlmostEqual(0.6, a["length"])
            self.assertEqual("hold", a["loop"])
        self.assertNotEqual(fin(anims["tirer"], "manche", "rotation"), fin(anims["relacher"], "manche", "rotation"))

    def test_cristal_lumineux(self):
        self.assertTrue(any(e["name"].startswith("lueur_") for e in self.m["elements"]))


if __name__ == "__main__":
    unittest.main()
