# [claude-build]
import json
import sys
import tempfile
import unittest
from pathlib import Path

import nbtlib
from nbtlib import Compound, String

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import monde_atelier as monde  # noqa: E402


class TestMondeAtelier(unittest.TestCase):
    def test_un_monde_absent_est_cree_en_superflat(self):
        with tempfile.TemporaryDirectory() as d:
            save = monde.trouver_ou_creer_monde("Essai", Path(d))
            self.assertEqual(save, Path(d) / "Essai")
            data = nbtlib.load(save / "level.dat")["Data"]
            self.assertEqual(str(data["LevelName"]), "Essai")
            self.assertEqual(str(data["WorldGenSettings"]["dimensions"]["minecraft:overworld"]["generator"]["type"]), "minecraft:flat")
            self.assertEqual(int(data["GameType"]), 1)                     # creatif
            self.assertEqual(int(data["allowCommands"]), 1)
            self.assertNotIn("Player", data)                               # rien de la partie de l'auteur du modele
            self.assertTrue(monde.monde_superflat(save))
            self.assertTrue((save / "datapacks/atelier/pack.mcmeta").exists())

    def test_un_monde_existant_n_est_pas_modifie(self):
        with tempfile.TemporaryDirectory() as d:
            (Path(d) / "Un dossier").mkdir()
            nbtlib.File({"Data": Compound({"LevelName": String("Essai")})}, gzipped=True).save(Path(d) / "Un dossier" / "level.dat")
            avant = (Path(d) / "Un dossier" / "level.dat").read_bytes()
            self.assertEqual(monde.trouver_ou_creer_monde("Essai", Path(d)), Path(d) / "Un dossier")
            self.assertEqual((Path(d) / "Un dossier" / "level.dat").read_bytes(), avant)
            self.assertFalse((Path(d) / "Essai").exists())                 # pas de second monde cree a cote

    def test_deuxieme_appel_sans_effet(self):
        with tempfile.TemporaryDirectory() as d:
            a = monde.trouver_ou_creer_monde("Essai", Path(d))
            avant = (a / "level.dat").read_bytes()
            b = monde.trouver_ou_creer_monde("Essai", Path(d))
            self.assertEqual(a, b)
            self.assertEqual((b / "level.dat").read_bytes(), avant)

    def test_la_pose_automatique_une_seule_fois(self):
        with tempfile.TemporaryDirectory() as d:
            save = monde.trouver_ou_creer_monde("Essai", Path(d))
            monde.installer_pose_auto(save, "ciel_pose", "ciel")
            monde.installer_pose_auto(save, "ciel_pose", "ciel")           # idempotent
            monde.installer_pose_auto(save, "aqua_pose", "aqua")           # un autre donjon dans le meme monde
            dp = save / "datapacks/atelier/data"
            valeurs = json.loads((dp / "minecraft/tags/function/load.json").read_text(encoding="utf-8"))["values"]
            self.assertEqual(sorted(valeurs), ["atelier:charger_aqua", "atelier:charger_ciel"])
            f = (dp / "atelier/function/charger_ciel.mcfunction").read_text(encoding="utf-8")
            self.assertIn("execute unless score #ciel atelier matches 1 run schedule function atelier:ciel_pose 100t", f)
            self.assertIn("scoreboard players set #ciel atelier 1", f)     # le drapeau est leve juste apres la pose : jamais deux poses
            self.assertLess(f.index("function atelier:ciel_pose"), f.index("scoreboard players set #ciel atelier 1"))   # sinon la condition serait deja fausse


    def test_le_message_d_entree_a_un_tp_cliquable(self):
        cmd = monde.message_entree("Le temple est prêt.", "215 22 -3")
        data = json.loads(cmd.removeprefix("tellraw @a "))                 # JSON valide
        clic = data[1]["clickEvent"]
        self.assertEqual(clic["action"], "run_command")
        self.assertEqual(clic["value"], "/tp @s 215 22 -3 0 0")


if __name__ == "__main__":
    unittest.main()
