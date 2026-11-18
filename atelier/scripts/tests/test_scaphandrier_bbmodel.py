# [claude-build] Tests du modele Blockbench du Scaphandrier rouille (atelier/modeles/station/scaphandrier.bbmodel).
import json
import sys
import unittest
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "atelier/scripts"))
from bbmodel_vers_java import normaliser  # noqa: E402


class TestScaphandrier(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = normaliser(json.loads((RACINE / "atelier/modeles/station/scaphandrier.bbmodel").read_text(encoding="utf-8")))
        cls.os = {}

        def visiter(g, parent):
            cls.os[g["name"]] = parent
            for c in g["children"]:
                if isinstance(c, dict):
                    visiter(c, g["name"])
        for g in cls.m["outliner"]:
            visiter(g, None)

    def test_os(self):
        self.assertEqual({"racine": None, "corps": "racine", "casque": "corps", "bras_g": "corps", "cle": "bras_g", "fissure1": "casque", "fissure2": "casque", "hublot_brise": "casque", "bras_d": "corps",
                          "harpon": "bras_d", "tuyaux": "corps", "jambe_g": "racine", "jambe_d": "racine"}, self.os)

    def test_taille(self):
        h = max(e["to"][1] for e in self.m["elements"]) - min(e["from"][1] for e in self.m["elements"])
        self.assertTrue(38 <= h <= 44, h)

    def test_hublot(self):
        noms = {e["name"] for e in self.m["elements"]}
        self.assertTrue({"lueur_hublot", "fissure1", "fissure2", "hublot_brise"} <= noms)

    def test_animations(self):
        anims = {a["name"]: (a["length"], a["loop"]) for a in self.m["animations"]}
        self.assertEqual({"repos": (3.0, "loop"), "marche": (1.6, "loop"), "coup": (0.9, "once"), "viser": (1.0, "hold"),
                          "tirer": (0.4, "hold"), "coince": (1.5, "loop"), "mort": (2.0, "once")}, anims)


if __name__ == "__main__":
    unittest.main()
