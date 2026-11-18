# [claude-build] Tests de la traduction .bbmodel -> Java (atelier/scripts/bbmodel_vers_java.py) : conventions de repere de bout en bout.
import json
import math
import sys
import unittest
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "atelier/scripts"))
import bbmodel_vers_java as bj  # noqa: E402


def f(v):
    return repr(round(v, 6) + 0.0) + "F"


MINI = {
    "resolution": {"width": 64, "height": 64},
    "elements": [{"name": "cube", "uuid": "e1", "from": [1, 1, 1], "to": [3, 2, 4], "origin": [2, 3, 4],
                  "rotation": [0, 0, 0], "uv_offset": [0, 0], "box_uv": True}],
    "outliner": [{"name": "a", "origin": [2, 3, 4], "rotation": [10, 20, 30], "uuid": "g1", "children": ["e1"]}],
    "animations": [{"name": "test", "loop": "once", "length": 1, "animators": {"g1": {"name": "a", "type": "bone", "keyframes": [
        {"channel": "rotation", "time": 0.5, "interpolation": "linear", "data_points": [{"x": "5", "y": "6", "z": "7"}]},
        {"channel": "position", "time": 0.5, "interpolation": "linear", "data_points": [{"x": "1", "y": "2", "z": "3"}]},
    ]}}}],
}


class TestBbmodelVersJava(unittest.TestCase):
    def test_aller_retour(self):
        geo = bj.java_geometrie(MINI, "fr.test", "Geo")
        attendu = "PartPose.offsetAndRotation(-2.0F, 21.0F, 4.0F, " + ", ".join(f(math.radians(v)) for v in (-10, -20, 30)) + ")"
        self.assertIn(attendu, geo)
        anim = bj.java_animations(MINI, "fr.test", "Anims")
        self.assertIn("KeyframeAnimations.degreeVec(-5.0F, -6.0F, 7.0F)", anim)
        self.assertIn("KeyframeAnimations.posVec(-1.0F, 2.0F, 3.0F)", anim)
        self.assertIn(".withLength(1.0F)", anim)

    def test_chemins_points_faibles(self):
        modele = json.loads((RACINE / "atelier/modeles/golem/golem.bbmodel").read_text(encoding="utf-8"))
        chemins = bj.chemins(modele)
        self.assertEqual(["corps/tete/pf0/rouge0", "corps/pf1/rouge1", "corps/bras_d/pf2/rouge2", "corps/pf3/rouge3",
                          "corps/pf4/rouge4"], chemins["ROUGES"])
        self.assertIn("corps/tete/pf0/rouge0", chemins["LUEURS"])
        self.assertTrue(any(c.startswith("corps/tete/lueur_") for c in chemins["LUEURS"]))

    def test_chemins_coques(self):
        modele = json.loads((RACINE / "atelier/modeles/golem/golem.bbmodel").read_text(encoding="utf-8"))
        ch = bj.chemins(modele)
        self.assertEqual(["corps/tete/pf0/pf0_masque", "", "", "", ""], ch["MASQUES"])
        self.assertEqual([], ch["PETALES"][0])
        self.assertEqual([f"corps/pf1/pf1_petale{k}" for k in range(4)], ch["PETALES"][1])
        for i in range(1, 5):
            self.assertEqual(4, len(ch["PETALES"][i]))
        self.assertIn("String[][] PETALES", bj.java_geometrie(modele, "fr.test", "Geo"))

    def test_entete_source(self):
        geo = bj.java_geometrie(MINI, "fr.test", "Geo", source="atelier/modeles/rodeur/rodeur.bbmodel")
        self.assertIn("depuis atelier/modeles/rodeur/rodeur.bbmodel", geo.splitlines()[0])
        anim = bj.java_animations(MINI, "fr.test", "Anims", source="atelier/modeles/rodeur/rodeur.bbmodel")
        self.assertIn("depuis atelier/modeles/rodeur/rodeur.bbmodel", anim.splitlines()[0])

    def test_format_blockbench_5(self):
        """Blockbench 5 enregistre les os a part (groups) et ne garde que leurs uuid dans l'outliner : meme Java qu'avant."""
        v5 = json.loads(json.dumps(MINI))
        g = v5["outliner"][0]
        v5["groups"] = [{k: g[k] for k in ("name", "origin", "rotation", "uuid")}]
        v5["outliner"] = [{"uuid": g["uuid"], "isOpen": True, "children": g["children"]}]
        v5["elements"][0]["rotation"] = None
        self.assertEqual(bj.java_geometrie(MINI, "fr.test", "Geo"), bj.java_geometrie(v5, "fr.test", "Geo"))

    def test_boucle(self):
        boucle = json.loads(json.dumps(MINI))
        boucle["animations"][0]["loop"] = "loop"
        self.assertIn(".looping()", bj.java_animations(boucle, "fr.test", "Anims"))
        self.assertNotIn(".looping()", bj.java_animations(MINI, "fr.test", "Anims"))


if __name__ == "__main__":
    unittest.main()
