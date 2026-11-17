# [claude-build] Tests du modele Blockbench du Rodeur des Racines (atelier/modeles/rodeur/rodeur.bbmodel).
import base64
import json
import unittest
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
BBMODEL = RACINE / "atelier/modeles/rodeur/rodeur.bbmodel"
TEXTURE = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/textures/entity/golem.png"

OS = {"racine": None, "corps": "racine", "cou": "corps", "tete": "cou", "machoire": "tete", "patte_av_g": "corps",
      "patte_av_d": "corps", "patte_ar_g": "corps", "patte_ar_d": "corps", "queue": "corps", "crete": "corps", "lianes": "corps"}
ANIMS = {"repos": (3.0, "loop"), "marche": (1.0, "loop"), "morsure": (0.5, "once"), "grattage": (1.0, "loop"),
         "charge": (0.5, "loop"), "sonne": (1.5, "loop"), "reveil": (2.5, "once"), "mort": (1.0, "once")}


class TestRodeurBbmodel(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = json.loads(BBMODEL.read_text(encoding="utf-8"))
        cls.os = {}

        def visiter(g, parent):
            cls.os[g["name"]] = parent
            for c in g["children"]:
                if isinstance(c, dict):
                    visiter(c, g["name"])
        for g in cls.m["outliner"]:
            visiter(g, None)

    def test_os(self):
        self.assertEqual(OS, self.os)

    def test_taille(self):
        de = [min(e["from"][k] for e in self.m["elements"]) for k in range(3)]
        a = [max(e["to"][k] for e in self.m["elements"]) for k in range(3)]
        largeur, hauteur, longueur = (a[k] - de[k] for k in range(3))
        self.assertTrue(56 <= longueur <= 84, longueur)
        self.assertTrue(32 <= hauteur <= 44, hauteur)
        self.assertTrue(20 <= largeur <= 32, largeur)

    def test_lueurs(self):
        noms = [e["name"] for e in self.m["elements"]]
        self.assertEqual(2, sum(n.startswith("lueur_oeil") for n in noms))
        self.assertGreaterEqual(sum(n.startswith("lueur_baie") for n in noms), 3)

    def test_animations(self):
        anims = {a["name"]: a for a in self.m["animations"]}
        self.assertEqual(set(ANIMS), set(anims))
        for nom, (duree, boucle) in ANIMS.items():
            self.assertAlmostEqual(duree, anims[nom]["length"])
            self.assertEqual(boucle, anims[nom]["loop"], nom)

    def test_boucles_sans_saut(self):
        for a in self.m["animations"]:
            if a["loop"] != "loop":
                continue
            for anim in a["animators"].values():
                for canal in ("rotation", "position"):
                    ks = sorted((k for k in anim["keyframes"] if k["channel"] == canal), key=lambda k: k["time"])
                    if ks:
                        self.assertAlmostEqual(0.0, ks[0]["time"])
                        self.assertAlmostEqual(a["length"], ks[-1]["time"])
                        self.assertEqual(ks[0]["data_points"], ks[-1]["data_points"], (a["name"], anim["name"]))

    def test_texture(self):
        self.assertTrue(all(e["box_uv"] for e in self.m["elements"]))
        self.assertEqual(TEXTURE.read_bytes(), base64.b64decode(self.m["textures"][0]["source"].split(",", 1)[1]))

    def test_pas_de_gros_bloc(self):
        for e in self.m["elements"]:
            taille = [e["to"][k] - e["from"][k] for k in range(3)]
            self.assertFalse(all(t > 16 for t in taille), e["name"])


if __name__ == "__main__":
    unittest.main()
