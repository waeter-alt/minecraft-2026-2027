# [claude-build]
import math
import sys
import tempfile
import unittest
from pathlib import Path

import nbtlib

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import monde_atelier  # noqa: E402
import suspendu_salle1 as salle1  # noqa: E402
from suspendu_outils import S, ecrire_structure, lire_structure  # noqa: E402


class TestSalle1(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocs, cls.infos = salle1.construire()

    def test_la_salle_et_ses_reperes(self):
        i = self.infos
        self.assertGreater(len(i["salle"]), 600)                                         # environ 30 de large
        for z in i["zombies"]:
            self.assertIn((z[0], z[2]), i["salle"])                                      # les trois zombies et le coffre sont dans la salle
        self.assertIn((i["coffre"][0], i["coffre"][2]), i["salle"])
        self.assertEqual(len(i["zombies"]), 3)
        self.assertEqual(i["entree"][1], salle1.Y0 + 1)

    def test_le_batiment_est_clos_sauf_au_bout_du_couloir_d_entree(self):
        """Aucune case d'air n'a de voisin horizontal vide (un trou dans un mur) hors du bout ouvert du couloir d'entree."""
        fuites = []
        for (x, y, z), s in self.blocs.items():
            if s[0] != "minecraft:air" or y <= salle1.Y0:
                continue
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y, z + dz) not in self.blocs:
                    fuites.append((x, y, z))
        z_fin = salle1.DZ + salle1.COULOIRS["entree"]["rect"][3]
        fuites = [f for f in fuites if f[2] < z_fin - 1]
        self.assertEqual(fuites, [], fuites[:5])

    def test_les_trois_portes_sont_des_dalles_en_arc_avec_une_spirale(self):
        self.assertEqual(set(self.infos["cases_portes"]), {"1", "2", "3"})
        for nom, cases in self.infos["cases_portes"].items():
            rep = self.infos["reperes"][nom]
            self.assertGreaterEqual(len(cases), 8, nom)
            spirale = 0
            for (x, z) in cases:
                haut = salle1.profil(rep["largeur"], rep["u"][(x, z)])
                self.assertTrue(5 <= haut <= salle1.HAUT_PORTE)
                for yr in range(1, haut + 1):
                    n = self.blocs[(x, salle1.Y0 + yr, z)][0]
                    dalle, symbole = ("minecraft:" + m for m in salle1.COULEURS_PORTES[nom][:2])
                    self.assertIn(n, (dalle, symbole), (nom, x, yr, z))
                    spirale += n == symbole
                self.assertEqual(self.blocs[(x, salle1.Y0 + haut + 1, z)][0], "minecraft:chiseled_stone_bricks")     # l'arc qui encadre la dalle
            self.assertGreaterEqual(spirale, 15, nom)                                                              # la spirale est bien gravee
            self.assertEqual({math.floor(u + 0.5) for u in rep["u"].values()} >= set(range(-3, 4)), True, nom)                # et chaque colonne de la spirale a des cases
            self.assertEqual(salle1.profil(rep["largeur"], 0), salle1.HAUT_PORTE)                                  # le milieu de l'arc est le plus haut

    def test_chaque_porte_a_sa_couleur(self):
        self.assertEqual({n: c[2] for n, c in salle1.COULEURS_PORTES.items()}, {"1": "bleue", "2": "jaune", "3": "rouge"})
        self.assertEqual(len({c[0] for c in salle1.COULEURS_PORTES.values()}), 3)
        for dalle, symbole, _ in salle1.COULEURS_PORTES.values():                   # la spirale se lit sur la dalle
            self.assertNotEqual(dalle, symbole)

    def test_la_spirale_tient_dans_un_carre_de_7(self):
        s = salle1.SPIRALE
        self.assertEqual((min(u for u, v in s), max(u for u, v in s), min(v for u, v in s), max(v for u, v in s)), (-3, 3, -3, 3))
        self.assertGreater(len(s), 20)
        for u, v in s:                                                       # un trait d'un bloc : aucune case n'a plus de 2 voisines
            self.assertLessEqual(sum(((u + a, v + b) in s) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))), 2, (u, v))

    def test_chaque_marche_est_un_escalier_et_descend_d_un_cran(self):
        for nom, marches in self.infos["marches"].items():
            self.assertGreaterEqual(len(marches), 5 * salle1.NB_MARCHES[nom], nom)
            par_z = {}
            for (x, y, z) in marches:
                self.assertTrue(self.blocs[(x, y - 1, z)][0].endswith("_stairs"))
                par_z.setdefault(z, set()).add(y)
            zs = sorted(par_z)
            self.assertEqual(len(zs), salle1.NB_MARCHES[nom])
            for a, b in zip(zs, zs[1:]):
                self.assertEqual(max(par_z[b]), max(par_z[a]) - 1)

    def test_l_escalier_est_habille_de_bout_en_bout(self):
        """Refonte demandee par Luca (escalier moche : terre et roche brutes sur les cotes, tunnel bas, panneaux de lanternes de mer) : chaque case voisine de l'air de l'escalier est une brique ou une lanterne."""
        pas_de_terre = ("minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:sea_lantern")
        for nom, marches in self.infos["marches"].items():
            air_escalier = {(x, y + dy, z) for (x, y, z) in marches for dy in range(0, 3)}
            for (x, y, z) in marches:
                for n in ((x + 1, y, z), (x - 1, y, z)):
                    if n not in air_escalier:
                        self.assertIn(n, self.blocs, (nom, n))                                         # le flanc est ecrit (brique), pas laisse a la terre
                        self.assertNotIn(self.blocs[n][0], pas_de_terre, (nom, n))
        c1 = self.infos["marches"]["c1"]
        tunnel = [m for m in c1 if m[2] >= salle1.DZ + salle1.ESCALIERS["c1"] + salle1.NB_BATIMENT]
        for (x, y, z) in tunnel:
            for dy in range(0, salle1.HAUT_TUNNEL):
                self.assertEqual(self.blocs[(x, y + dy, z)][0], "minecraft:air", (x, y + dy, z))     # le tunnel a 6 de haut libres
            self.assertNotEqual(self.blocs[(x, y + salle1.HAUT_TUNNEL, z)][0], "minecraft:air")      # et un plafond de brique

    def test_l_escalier_a_des_bandes_des_lanternes_en_niche_et_un_tapis_central(self):
        for nom, marches in self.infos["marches"].items():
            par_z = {}
            for (x, y, z) in marches:
                par_z.setdefault(z, []).append((x, y))
            zs = sorted(par_z)
            for z, cases in par_z.items():
                centre = (min(c[0] for c in cases) + max(c[0] for c in cases)) / 2
                for (x, y) in cases:
                    attendu = "minecraft:polished_andesite_stairs" if abs(x - centre) <= 1.5 else "minecraft:stone_brick_stairs"
                    self.assertEqual(self.blocs[(x, y - 1, z)][0], attendu, (nom, x, y, z))
            niches = [(p, b) for p, b in self.blocs.items() if b[0] == "minecraft:lantern" and any(p[2] == z and p[1] == par_z[z][0][1] + 2 for z in zs)]
            self.assertGreaterEqual(len(niches), 2 * (salle1.NB_MARCHES[nom] // salle1.RANG_BANDE), nom)
        c1 = self.infos["marches"]["c1"]
        rang0 = salle1.DZ + salle1.ESCALIERS["c1"] + salle1.NB_BATIMENT
        y_rang0 = min(y for (x, y, z) in c1 if z == rang0)
        self.assertEqual(self.blocs[(100, y_rang0 + 1, rang0)][0], "minecraft:chiseled_stone_bricks")      # la bande sculptee, tous les 4 rangs, sur la paroi

    def test_aucune_case_du_sol_n_est_loin_d_une_source_de_lumiere(self):
        feux = [(f[0], f[1]) for f in self.infos["feux"]]
        suspendues = [(s[0], s[2]) for s in self.infos["sources"] if s[1] > salle1.Y0]
        marches = {(m[0], m[2]) for liste in self.infos["marches"].values() for m in liste}
        for (x, z) in self.infos["air"]:
            if (x, z) in marches:
                continue
            if (x, z) in self.infos["salle"]:                      # la salle : un feu de sol a moins de PAS_SOL, ou le lustre
                self.assertTrue(any(abs(x - px) + abs(z - pz) <= salle1.PAS_SOL for px, pz in feux)
                                or any(abs(x - px) + abs(z - pz) <= 4 for px, pz in suspendues), (x, z))
            else:                                                  # les couloirs : une lanterne suspendue a moins de PAS_LANTERNE (ou un feu de la salle tout proche)
                self.assertTrue(any(abs(x - px) + abs(z - pz) <= salle1.PAS_LANTERNE for px, pz in suspendues)
                                or any(abs(x - px) + abs(z - pz) <= salle1.PAS_SOL for px, pz in feux), (x, z))

    def test_peu_de_lanternes_au_plafond_de_la_salle(self):
        """Luca : trop de lanternes au plafond. Dans la salle, il ne reste que le lustre (5 lanternes) ; la lumiere vient de feux de sol."""
        suspendues = [p for p, b in self.blocs.items() if b[0] == "minecraft:lantern" and (p[0], p[2]) in self.infos["salle"]]
        self.assertLessEqual(len(suspendues), 5)
        self.assertGreaterEqual(len(self.infos["feux"]), 6)
        self.assertLessEqual(len(self.infos["feux"]), 12)
        for (x, y, z) in [(f[0], salle1.Y0, f[1]) for f in self.infos["feux"]]:
            self.assertEqual(self.blocs[(x, y, z)][0], "minecraft:sea_lantern")

    def test_les_pilastres_ne_bouchent_ni_porte_ni_couloir(self):
        ouvertures = {c for c in self.infos["air"] if c not in self.infos["salle"]}
        for (x, z) in self.infos["pilastres"]:
            self.assertGreater(len(self.infos["pilastres"]), 8)
            self.assertTrue(all(max(abs(x - o[0]), abs(z - o[1])) > 4 for o in ouvertures), (x, z))
            self.assertEqual(self.blocs[(x, salle1.Y0 + 5, z)][0], "minecraft:stone_bricks")


class TestOutilsEtMonde(unittest.TestCase):
    def test_une_structure_se_relit(self):
        blocs = {(10, 5, 3): S("stone"), (11, 5, 3): S("air"), (10, 6, 3): S("iron_bars", north=True, waterlogged=False)}
        with tempfile.TemporaryDirectory() as d:
            dec = ecrire_structure(blocs, Path(d) / "x.nbt")
            relu = lire_structure(Path(d) / "x.nbt", dec)
        self.assertEqual(dec, (10, 5, 3))
        self.assertEqual(relu, blocs)                                                    # l'air aussi est ecrit : une structure creuse la roche

    def test_un_monde_profond_en_survie(self):
        f = monde_atelier.level_neuf("Essai", couches=[("minecraft:bedrock", 1), ("minecraft:stone", 90)], difficulte=2, apparition=(0, 30, 6), mode_jeu=0)
        d = f["Data"]
        couches = d["WorldGenSettings"]["dimensions"]["minecraft:overworld"]["generator"]["settings"]["layers"]
        self.assertEqual([(str(c["block"]), int(c["height"])) for c in couches], [("minecraft:bedrock", 1), ("minecraft:stone", 90)])
        self.assertEqual((int(d["Difficulty"]), int(d["GameType"]), int(d["SpawnY"])), (2, 0, 30))

    def test_sans_option_le_monde_reste_celui_du_modele(self):
        f = monde_atelier.level_neuf("Essai")
        modele = nbtlib.load(monde_atelier.MODELE)["Data"]
        self.assertEqual(int(f["Data"]["Difficulty"]), int(modele["Difficulty"]))
        self.assertEqual(len(f["Data"]["WorldGenSettings"]["dimensions"]["minecraft:overworld"]["generator"]["settings"]["layers"]), 3)


if __name__ == "__main__":
    unittest.main()
