# [claude-build]
import math
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import donjon_suspendu as dj  # noqa: E402
import suspendu_salle1 as rdc  # noqa: E402
import suspendu_ss1_blocs as ss1  # noqa: E402
import suspendu_ss2 as plan  # noqa: E402
import suspendu_ss2_blocs as ss2  # noqa: E402
import suspendu_ss2_deco as deco  # noqa: E402
import suspendu_ss2_fonctions as sj  # noqa: E402
from suspendu_outils import AIR  # noqa: E402


class TestSalleMoins2(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocs, cls.infos = ss2.construire()
        cls.cases = cls.infos["cases"]

    def test_les_niveaux(self):
        self.assertEqual(ss2.YF2, ss1.YF - 19)                                                    # 47 sous le rez-de-chaussee
        self.assertEqual(ss2.Y_FOND, -7)                                                          # la fosse descend jusqu'au socle (Y -8 du gabarit)
        self.assertEqual(ss2.PROF_ABIME, 45)                                                      # 45 de chute
        genres = {}
        for (sol, haut, genre) in self.cases.values():
            genres.setdefault(genre, set()).add(sol)
        self.assertEqual(genres["ile"], {ss2.YF2})
        self.assertEqual(genres["pilier"], {ss2.YF2})                                             # les plateformes des piliers sont au niveau des iles
        self.assertEqual(genres["massif"], {ss2.YF2})                                             # le coeur des montagnes : plein jusqu'au plafond
        self.assertTrue(genres["grotte"] <= {ss2.YF2 - 1, ss2.YF2, ss2.YF2 + 1, ss2.YF2 + 2})         # le sol des grottes ondule de 0 a 2 blocs ; le ruisseau de lave est creuse un bloc plus bas
        self.assertGreaterEqual(len(genres["grotte"]), 3)
        self.assertEqual(genres["abime"], {ss2.Y_FOND})
        self.assertEqual(genres["couloir"], {ss2.YF2})
        self.assertEqual(genres["pont"], {ss2.YF2})                                               # le tablier du pont est au niveau des iles

    def test_les_huit_iles_et_le_pont(self):
        for nom in ("E", "B", "C", "A", "F", "G", "H", "I", "PONT"):
            self.assertGreater(self.infos["iles"][nom]["cases"], 20, nom)
        self.assertGreater(self.infos["iles"]["C"]["cases"], 250)                                  # la grande ile
        pont = [c for c, n in self.infos["ile_de"].items() if n == "PONT"]
        self.assertLessEqual(max(c[1] for c in pont) - min(c[1] for c in pont), 14)                # un chemin etroit, pas une ile

    def test_le_pont_est_en_bois_delabre(self):
        pont = sorted(c for c, n in self.infos["ile_de"].items() if n == "PONT")
        yf2, y_fond = ss2.YF2, ss2.Y_FOND
        bois = {"minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks", "minecraft:stripped_oak_log"}
        trous = [c for c in pont if self.blocs[(c[0], yf2, c[1])] == AIR]
        planches = [c for c in pont if self.blocs[(c[0], yf2, c[1])][0] in bois]
        self.assertEqual(len(trous) + len(planches), len(pont))                                      # un tablier de bois, avec des planches manquantes et rien d'autre
        self.assertGreaterEqual(len(trous), 5)                                                       # delabre : il manque des planches
        self.assertLess(len(trous), len(pont) * 0.15)                                                # mais on le traverse
        self.assertGreaterEqual(len({self.blocs[(c[0], yf2, c[1])][0] for c in planches}), 3)       # plusieurs bois
        ennemis = {(m[0], m[2]) for liste in self.infos["ennemis"].values() for m in liste}
        for (x, z) in trous:
            self.assertFalse(any((x + dx, z + dz) in trous for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dz) != (0, 0)), (x, z))   # jamais deux trous cote a cote : on les enjambe
            self.assertTrue(all(self.cases.get((x + dx, z + dz), (0, 0, ""))[2] == "pont" for dx in (-1, 0, 1) for dz in (-1, 0, 1)))    # ni sur un bord ni a la jonction avec une ile
            self.assertNotIn((x, z), ennemis)
        for (x, z) in pont:                                                                           # sous le tablier, le vide : l'abime n'est pas rempli de roche
            for y in range(y_fond + 2, yf2 - 1):
                self.assertIn(self.blocs[(x, y, z)][0], ("minecraft:air", "minecraft:oak_log", "minecraft:pointed_dripstone"), (x, y, z))
            self.assertIn(self.blocs[(x, y_fond, z)][0], deco.NOMS_FOND)                               # le fond de la fosse, dessous : de la roche noire ou de la lave
        poutres = [k for k, v in self.blocs.items() if v[0] == "minecraft:stripped_spruce_log"]
        self.assertGreater(len(poutres), 10)
        barrieres = [(x, z) for (x, z) in pont if self.blocs.get((x, yf2 + 1, z), ("",))[0] == "minecraft:oak_fence"]
        self.assertGreater(len(barrieres), 10)
        for (x, z) in barrieres:                                                                      # la barriere est sur les bords du pont, jamais au milieu, et jamais sur un ennemi
            self.assertTrue(any(self.cases.get((x + dx, z + dz), (0, 0, ""))[2] == "abime" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))))
            self.assertNotIn((x, z), ennemis)
        self.assertLess(len(barrieres), sum(1 for (x, z) in pont if any(self.cases.get((x + dx, z + dz), (0, 0, ""))[2] == "abime" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))))   # a moitie arrachee : il y a des manques
        self.assertTrue(any(v[0] == "minecraft:lantern" and k[1] == yf2 + 2 and (k[0], k[2]) in set(pont) for k, v in self.blocs.items()))

    def test_on_traverse_le_pont_a_pied_malgre_les_planches_manquantes(self):
        from verif_donjon_suspendu import Terrain, marcher
        t = Terrain(self.blocs, 85)
        lx, ly, lz = self.infos["leviers"][4]                                                         # le levier 4 est sur l'ile de depart du pont
        a_pied = marcher(t, [(lx + 1, ly, lz), (lx - 1, ly, lz), (lx, ly, lz + 1), (lx, ly, lz - 1)], 14)          # le levier lui-meme est un bloc : on part d'a cote
        cx, cy, cz = self.infos["coffres"][2]                                                         # le coffre jaune de l'ile G, de l'autre cote
        self.assertTrue(any(c in a_pied for c in ((cx + 1, cy, cz), (cx - 1, cy, cz), (cx, cy, cz + 1), (cx, cy, cz - 1))))
        for m in self.infos["ennemis"]["PONT"]:
            self.assertIn(tuple(m), a_pied)                                                           # on se tient debout sur les deux emplacements d'ennemis du pont

    def test_les_cinq_leviers_sont_sur_des_iles_et_pas_les_ancrages_cyan(self):
        self.assertEqual(sorted(self.infos["leviers"]), [1, 2, 3, 4, 5])
        for k, (x, y, z) in self.infos["leviers"].items():
            self.assertIn(self.cases[(x, z)][2], ("ile", "grotte"), k)
            nom, props = self.blocs[(x, y, z)]
            self.assertEqual(nom, "minecraft:lever")
            self.assertEqual(dict(props)["powered"], "false")
        cyan = self.infos["ancrages_cyan"]
        self.assertEqual({k: len(v) for k, v in cyan.items()}, {1: 2, 2: 1, 3: 2, 4: 1, 5: 4})
        for liste in cyan.values():
            for (x, y, z) in liste:
                self.assertEqual(y, ss2.YF2 + ss2.HAUT_ANCRAGE_CYAN)
                self.assertNotEqual(self.blocs.get((x, y, z), ("",))[0], "minecraft:grappin:ancrage")
                self.assertNotIn(self.blocs.get((x, y, z), ("",))[0], ("grappin:ancrage",))        # invisibles : ni ancrage ni chaine dans la structure
                self.assertEqual(self.blocs.get((x, y + 1, z), ("minecraft:air",))[0], "minecraft:air")

    def test_les_ancrages_toujours_actifs(self):
        self.assertEqual(len(self.infos["ancrages_bleus"]), 5)                                      # les 4 piliers et le point de l'ile du levier 1
        for (x, y, z) in self.infos["ancrages_bleus"]:
            self.assertEqual(self.blocs[(x, y, z)][0], "grappin:ancrage")
            self.assertTrue(all(self.blocs[(x, yy, z)][0] == "minecraft:chain" for yy in range(y + 1, ss2.YF2 + ss2.HAUT_SALLE2 + 1)))
        for (cx, cz) in self.infos["pilier_cases"]:
            self.assertEqual(self.cases[(cx, cz)][2], "pilier")
            self.assertGreaterEqual(sum(1 for c, v in self.cases.items() if v[2] == "pilier" and abs(c[0] - cx) <= 2 and abs(c[1] - cz) <= 2), 21)

    def test_coffres_ennemis_et_cle(self):
        self.assertEqual(len(self.infos["coffres"]), 3)
        for c in self.infos["coffres"]:
            self.assertEqual(self.blocs[c][0], "minecraft:chest")
        x, y, z = self.infos["coffre_cle"]
        self.assertEqual(self.cases[(x, z)][2], "ile")
        self.assertEqual(self.infos["ile_de"][(x, z)], "I")                                         # la cle 3 est sur l'ile du quart de cercle
        self.assertNotEqual(self.blocs.get((x, y, z), ("",))[0], "minecraft:chest")                 # posee par la remise a zero (avec son contenu)
        n = sum(len(v) for v in self.infos["ennemis"].values())
        self.assertEqual(n, 17)                                                                      # les 17 croix rouges du dessin
        for liste in self.infos["ennemis"].values():
            for (x, y, z) in liste:
                self.assertIn(self.cases[(x, z)][2], ("ile", "grotte", "pont"))

    def test_plus_aucun_muret_en_pierre(self):
        murets = [k for k, v in self.blocs.items() if v[0].endswith("_wall")]
        self.assertEqual(murets, [])                                                                  # les garde-corps ont ete retires a la demande de Luca
        self.assertNotIn("garde_corps", self.infos)

    def test_les_iles_des_leviers_2_et_5_sont_des_montagnes_creusees_d_une_grotte(self):
        yf2, plafond = ss2.YF2, ss2.YF2 + ss2.HAUT_SALLE2
        iles_de = self.infos["ile_de"]
        for nom, levier in (("C", 2), ("H", 5)):
            m = self.infos["montagnes"][nom]
            noyau, chambre, tunnel = set(m["noyau"]), set(m["chambre"]), set(m["tunnel"])
            self.assertGreater(len(noyau), 200)
            self.assertTrue(chambre <= noyau and tunnel <= noyau)
            self.assertGreater(len(chambre), 60)
            self.assertGreaterEqual(len(tunnel), 30)
            lx, ly, lz = self.infos["leviers"][levier]
            self.assertIn((lx, lz), chambre)                                                          # le levier est dans la chambre de la grotte
            # le massif monte jusqu'au plafond : au dessus du sol, aucun bloc d'air hors de la grotte
            for (x, z) in noyau - chambre - tunnel:
                self.assertEqual(self.cases[(x, z)][2], "massif")
                for y in range(yf2 + 1, plafond + 1):
                    self.assertNotEqual(self.blocs.get((x, y, z)), AIR, (x, y, z))
            # la grotte : un dome de 3 a 6 blocs, le tunnel de 4, un plafond de roche au dessus
            for (x, z) in chambre | tunnel:
                sol, haut, genre = self.cases[(x, z)]
                self.assertEqual(genre, "grotte")
                self.assertTrue(yf2 + 4 <= haut <= yf2 + 10, (x, z, haut))
                self.assertGreaterEqual(haut - sol, 4, (x, z))                                        # au moins 4 blocs d'air au dessus du sol, lave exceptee (elle est creusee un bloc plus bas)
                self.assertNotEqual(self.blocs[(x, haut + 1, z)], AIR)
            self.assertGreaterEqual(len({self.cases[c][1] for c in chambre}), 4)                      # un plafond irregulier : au moins quatre hauteurs
            self.assertGreaterEqual(len({self.cases[c][0] for c in chambre if c not in m["lave"]}), 2)   # un sol qui ondule
            self.assertTrue(any(v[0] == "minecraft:lantern" and k[1] == self.cases[(k[0], k[2])][1] and (k[0], k[2]) in chambre for k, v in self.blocs.items()))   # des lanternes au plafond du dome
            # une bordure plate d'au moins 5 blocs reste autour : c'est la qu'arrivent les ancrages
            bordure = [c for c, n in iles_de.items() if n == nom and c not in noyau]
            self.assertGreater(len(bordure), 100)
            self.assertTrue(all(self.cases[c][2] == "ile" for c in bordure))
        for liste in self.infos["ancrages_cyan"].values():                                            # aucun ancrage n'est pris dans la roche
            for (x, y, z) in liste:
                self.assertNotIn(self.cases[(x, z)][2], ("massif", "grotte"))

    def test_les_grottes_sont_organiques(self):
        for nom in ("C", "H"):
            m = self.infos["montagnes"][nom]
            cave = set(m["chambre"]) | set(m["tunnel"])
            lave = set(m["lave"])
            self.assertGreaterEqual(len(lave), 8)                                                     # un ruisseau de lave au fond
            for c in cave:
                sol, haut, genre = self.cases[c]
                for dx, dz in deco.VOISINS:
                    n = (c[0] + dx, c[1] + dz)
                    if n in cave and c not in lave and n not in lave:
                        self.assertLessEqual(abs(sol - self.cases[n][0]), 1, (c, n))                 # jamais plus d'une marche entre deux cases du sol
            for c in lave:                                                                            # la lave est creusee un bloc plus bas que le sol voisin et ne deborde pas
                sol, haut, genre = self.cases[c]
                self.assertEqual(self.blocs[(c[0], sol, c[1])][0], "minecraft:lava")
                for dx, dz in deco.VOISINS:
                    cote = self.blocs.get((c[0] + dx, sol, c[1] + dz))
                    self.assertTrue(cote is None or cote[0] in ("minecraft:lava",) or cote != AIR, (c, dx, dz))
            contour = [c for c in cave if any((c[0] + dx, c[1] + dz) not in cave for dx, dz in deco.VOISINS)]
            self.assertGreater(len(contour), 40)                                                      # un contour long
            cercle = 2 * math.sqrt(math.pi * len(cave))                                               # le contour d'un disque de meme aire
            self.assertGreater(len(contour), 1.15 * cercle)                                           # nettement plus long : des lobes, un contour ondule, un tunnel (pas une ellipse lisse)

    def test_la_fosse_descend_jusqu_au_socle_avec_des_rivieres_de_lave(self):
        yf2, y_fond = ss2.YF2, ss2.Y_FOND
        lave = set(map(tuple, self.infos["lave_fond"]))
        self.assertGreater(len(lave), 1000)                                                          # de longues rivieres
        pilier_cyan = {c for liste in self.infos["piliers_cyan"].values() for p in liste for c in p["cases"]}
        self.assertFalse(lave & pilier_cyan)                                                          # jamais sous un pilier qui apparait
        for (x, z) in lave:
            self.assertEqual(self.blocs[(x, y_fond, z)][0], "minecraft:lava")
            self.assertEqual(self.blocs[(x, y_fond + 1, z)], AIR)                                     # la lave est a un bloc sous les berges
            for dx, dz in deco.VOISINS:                                                               # entouree de roche ou de lave au niveau du socle : elle ne coule pas
                n = self.blocs.get((x + dx, y_fond, z + dz))
                self.assertTrue(n is None or n != AIR, (x, z))                                        # (rien d'ecrit = la roche du gabarit : sous une ile ou un pilier)
        fond = [c for c, v in self.cases.items() if v[2] in ("abime", "pont") and c not in lave]
        for (x, z) in fond:
            self.assertIn(self.blocs[(x, y_fond + 1, z)][0], deco.NOMS_FOND)                         # un banc de roche noire (ou de magma sur les berges)
        magma = sum(1 for (x, z) in fond if self.blocs[(x, y_fond + 1, z)][0] == "minecraft:magma_block")
        self.assertGreater(magma, 100)
        noms = [v[0] for k, v in self.blocs.items() if k[1] <= yf2 - 24]
        self.assertGreater(sum(1 for n in noms if n in ("minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:blackstone")), 3000)    # les parois du fond : roche profonde
        self.assertGreater(sum(1 for k, v in self.blocs.items() if k[1] >= yf2 + 10 and v[0] in ("minecraft:dripstone_block", "minecraft:calcite")), 300)   # la voute : dripstone et calcite

    def test_les_stalactites_stalagmites_et_lianes_tiennent_et_ne_genent_pas(self):
        yf2, y_fond = ss2.YF2, ss2.Y_FOND
        n_up = n_down = n_liane = 0
        for (x, y, z), (nom, props) in self.blocs.items():
            if nom == "minecraft:pointed_dripstone":
                p = dict(props)
                if p["vertical_direction"] == "down":
                    n_down += 1
                    haut = self.blocs.get((x, y + 1, z))
                    self.assertTrue(haut is not None and haut != AIR, (x, y, z))                      # accrochee au plafond (ou a une autre pointe)
                    self.assertTrue(p["thickness"] != "base" or haut[0] != "minecraft:pointed_dripstone", (x, y, z))
                    cave = self.cases[(x, z)][2] == "grotte"
                    if cave:
                        self.assertGreaterEqual(y, self.cases[(x, z)][0] + 4, (x, y, z))             # il reste 3 blocs de libres au dessus du sol de la grotte
                    elif self.cases[(x, z)][2] in ("ile", "pilier", "pont"):
                        self.assertGreaterEqual(y, yf2 + 8, (x, y, z))                               # sa pointe reste hors de portee du crochet
                else:
                    n_up += 1
                    bas = self.blocs.get((x, y - 1, z))
                    self.assertTrue(bas is not None and bas != AIR, (x, y, z))                        # posee sur le sol (ou sur une autre pointe)
                    self.assertNotIn(self.cases[(x, z)][2], ("ile", "pilier"))                        # jamais sur la surface d'une ile ni d'un pilier
            elif nom in ("minecraft:cave_vines", "minecraft:cave_vines_plant"):
                n_liane += 1
                haut = self.blocs.get((x, y + 1, z))
                self.assertTrue(haut is not None and haut != AIR, (x, y, z))
        self.assertGreater(n_down, 300)
        self.assertGreater(n_up, 400)
        self.assertGreater(n_liane, 300)
        for nom in ("C", "H"):                                                                        # dans les grottes aussi
            cave = set(self.infos["montagnes"][nom]["chambre"])
            self.assertTrue(any(v[0] == "minecraft:pointed_dripstone" and (k[0], k[2]) in cave for k, v in self.blocs.items()), nom)
        self.assertGreaterEqual(self.infos["deco"]["cristaux"], 2)
        self.assertTrue(any(v[0] == "minecraft:amethyst_cluster" for v in self.blocs.values()))

    def test_on_entre_dans_les_grottes_a_pied_depuis_la_bordure(self):
        from verif_donjon_suspendu import Terrain, marcher
        t = Terrain(self.blocs, 85)
        for nom, levier in (("C", 2), ("H", 5)):
            m = self.infos["montagnes"][nom]
            tunnel = set(m["tunnel"]) - set(m["chambre"])
            bordure = [c for c, n in self.infos["ile_de"].items() if n == nom and self.cases[c][2] == "ile"]
            ox, oz = min(bordure, key=lambda c: min(abs(c[0] - q[0]) + abs(c[1] - q[1]) for q in tunnel))   # la case de bordure la plus proche du tunnel
            a_pied = marcher(t, [(ox, ss2.YF2 + 1, oz)], 14)
            lx, ly, lz = self.infos["leviers"][levier]
            self.assertTrue(any(c in a_pied for c in ((lx + 1, ly, lz), (lx - 1, ly, lz), (lx, ly, lz + 1), (lx, ly, lz - 1))), nom)      # de la bordure jusqu'au levier, en traversant le massif

    def test_le_couloir_relie_la_fin_de_l_escalier_de_la_porte_2_a_l_entree(self):
        couloir = set(self.infos["couloir"])
        ax, ay, az = self.infos["arrivee"]
        self.assertIn((ax, az), couloir)
        self.assertEqual(ay, ss2.YF2 + 2)
        ex, ey, ez = self.infos["entree"]
        self.assertEqual(self.cases[(ex, ez)][2], "ile")
        self.assertTrue(any((x + 1, z) in self.cases and self.cases[(x + 1, z)][2] == "ile" for (x, z) in couloir if abs(z - ez) <= 3))      # il aboutit sur l'ile d'entree
        self.assertTrue(all(z >= ss2.Z_ARRIVEE or x >= 30 for (x, z) in couloir))                    # rien dans le tunnel de l'escalier (avant Z_ARRIVEE a l'ouest)
        marches = rdc.construire()[1]["marches"]["c2"]
        self.assertEqual(len({m[2] for m in marches}), 46)
        self.assertEqual(max(m[2] for m in marches), ss2.Z_ARRIVEE - 1)                              # la derniere marche est juste avant le couloir

    def test_aucun_recouvrement_avec_le_rez_de_chaussee_ni_le_sous_sol_1(self):
        b1 = rdc.construire()[0]
        b1b = ss1.construire()[0]
        for autre, nom in ((b1, "rez-de-chaussee"), (b1b, "sous-sol 1")):
            communs = set(autre) & set(self.blocs)
            self.assertEqual(len(communs), 0, nom)

    def test_tout_tient_dans_le_gabarit(self):
        for (x, y, z) in self.blocs:
            self.assertTrue(0 <= x < dj.SX and 0 <= z < dj.SZ and -8 <= y < dj.SY, (x, y, z))


class TestFonctionsSalleMoins2(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        _, cls.infos = rdc.construire()
        _, cls.infos2 = ss1.construire()
        cls.infos3 = ss2.construire()[1]
        decalages = {n: (0, 0, 0) for n in ("salle1", "ss1", "ss2", "ss3", "porte1", "porte2", "porte3", "portem1")}
        cls.f, _ = dj.fonctions(cls.infos, decalages, cls.infos2, cls.infos3)
        cls.blocs_ss2 = ss2.construire()[0]

    def _developper(self, nom):
        """Le texte de la fonction `nom` ou chaque ligne `function atelier:x` est remplacee par le texte de x."""
        lignes = []
        for ligne in self.f[nom].splitlines():
            m = re.fullmatch(r"function atelier:(\w+)", ligne)
            lignes.append(self._developper(m.group(1)) if m else ligne)
        return "\n".join(lignes)

    def texte_levier(self, k):
        """Tout ce que fait le levier k : pour le 5, les ancrages puis le coffre de la cle (la scene de camera en plus n'y est pas)."""
        return self._developper("suspendu_ss2_cle_tout") if k == 5 else self.f[f"suspendu_ss2_levier{k}"]

    def test_chaque_levier_fait_apparaitre_ses_ancrages(self):
        tick = self.f["suspendu_tick"]
        for k in range(1, 6):
            self.assertRegex(tick, r"if score #l%d suspendu matches 0 if block -?\d+ -?\d+ -?\d+ minecraft:lever\[powered=true\] run function atelier:suspendu_ss2_levier%d" % (k, k))
            texte = self.texte_levier(k)
            self.assertEqual(texte.count("grappin:ancrage"), len(self.infos3["ancrages_cyan"][k]))
            self.assertEqual(len(re.findall(r"^setblock \S+ \S+ \S+ grappin:ancrage$", texte, re.M)), len(self.infos3["ancrages_cyan"][k]))
            self.assertEqual(texte.count("minecraft:chain[axis=y]"), len(self.infos3["ancrages_cyan"][k]))   # l'ancrage et sa chaine, du plafond
            self.assertIn(f"scoreboard players set #l{k} suspendu 1", self.f[f"suspendu_ss2_levier{k}"])
        for nom, texte in self.f.items():
            if not nom.startswith(("suspendu_ss2_levier", "suspendu_ss2_l5_")):
                self.assertNotIn("grappin:ancrage", texte, nom)                                      # seuls les leviers posent les ancrages cyan

    @staticmethod
    def _appliquer(texte, ciel):
        """Rejoue les `fill` et `setblock` d'une fonction dans `ciel` {(x, y, z) du gabarit: nom du bloc}."""
        ox, oy, oz = dj.ORIGINE
        for ligne in texte.splitlines():
            m = re.match(r"fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (\S+)", ligne)
            if m:
                x1, y1, z1, x2, y2, z2 = (int(v) for v in m.groups()[:6])
                nom = m.group(7).split("[")[0]
                for x in range(min(x1, x2) - ox, max(x1, x2) - ox + 1):
                    for y in range(min(y1, y2) - oy, max(y1, y2) - oy + 1):
                        for z in range(min(z1, z2) - oz, max(z1, z2) - oz + 1):
                            ciel[(x, y, z)] = nom
                continue
            m = re.match(r"setblock (-?\d+) (-?\d+) (-?\d+) (\S+)", ligne)
            if m:
                x, y, z = int(m.group(1)) - ox, int(m.group(2)) - oy, int(m.group(3)) - oz
                ciel[(x, y, z)] = m.group(4).split("[")[0].split("{")[0]

    def test_un_ancrage_au_dessus_du_vide_arrive_avec_son_pilier(self):
        piliers = self.infos3["piliers_cyan"]
        self.assertEqual({k: len(v) for k, v in piliers.items()}, {1: 1, 3: 1, 5: 3})                   # le point intermediaire du levier 1, le premier du levier 3 et les trois premiers du levier 5 : ceux qui sont dans le vide
        yf2, y_fond = self.infos3["yf2"], self.infos3["y_fond"]
        for k in range(1, 6):
            texte = self.texte_levier(k)
            if k in piliers:
                self.assertEqual(texte.count("minecraft:light[level="), len(piliers[k]))
                self.assertIn("minecraft:stone_bricks", texte)
            else:
                self.assertNotIn("minecraft:stone_bricks", texte)                                    # les ancrages des leviers 2 et 4 sont sur une ile : pas de pilier
                self.assertNotIn("minecraft:light", texte)
        ciel = {}
        for k, liste in piliers.items():
            self._appliquer(self.texte_levier(k), ciel)
            for p in liste:
                self.assertEqual(len(p["cases"]), 21)                                                # 5 x 5 sans les coins, comme les piliers bleus
                self.assertEqual(ciel[tuple(p["ancrage"])], "grappin:ancrage")
                self.assertEqual(ciel[tuple(p["lumiere"])], "minecraft:light")
                for (cx, cz) in p["cases"]:
                    self.assertEqual(self.infos3["cases"][(cx, cz)][2], "abime")                      # le pilier ne touche ni une ile ni un autre pilier
                    for y in range(y_fond + 2, yf2):
                        self.assertIn(ciel[(cx, y, cz)], ("minecraft:stone_bricks", "minecraft:chiseled_stone_bricks"), (cx, y, cz))
                    self.assertIn(ciel[(cx, yf2, cz)], ("minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:mossy_cobblestone"))   # le dessus : la roche des iles
                    for y in range(y_fond + 2, yf2 + 1):
                        self.assertEqual(ss2.construire()[0].get((cx, y, cz)), AIR)                   # avant le levier, la fosse est vide
        self._appliquer(self.f["suspendu_reset"], ciel)
        for liste in piliers.values():
            for p in liste:
                self.assertEqual(ciel[tuple(p["lumiere"])], "minecraft:air")
                for (cx, cz) in p["cases"]:
                    for y in range(y_fond + 2, yf2 + 1):
                        self.assertEqual(ciel[(cx, y, cz)], "minecraft:air", (cx, y, cz))             # la remise a zero retire tout le pilier
                self.assertEqual(ciel[tuple(p["ancrage"])], "minecraft:air")

    def test_la_remise_a_zero_rend_les_leviers_et_retire_les_ancrages(self):
        reset = self.f["suspendu_reset"]
        for k in range(1, 6):
            self.assertIn(f"scoreboard players set #l{k} suspendu 0", reset)
        self.assertEqual(reset.count("lever[face=floor,facing=north,powered=false]"), 5)
        for k, liste in self.infos3["ancrages_cyan"].items():
            for (x, y, z) in liste:
                self.assertIn(f"minecraft:air", reset)
        self.assertGreaterEqual(reset.count("minecraft:air"), 5)
        self.assertIn("kill @e[tag=suspendu_ss2e]", reset)
        self.assertEqual([l for l in reset.splitlines() if l.startswith("setblock") and "tripwire_hook" in l], [])        # la remise a zero ne pose plus le coffre de la cle : il n'apparait qu'au levier 5
        self.assertIn(f"setblock {dj.txt(self.infos3['coffre_cle'])} minecraft:air", reset)                            # elle le retire

    def test_le_coffre_de_la_cle_n_apparait_qu_au_levier_5(self):
        coffre = dj.txt(self.infos3["coffre_cle"])
        pose = self.f["suspendu_ss2_cle_coffre"]
        lignes = [l for l in pose.splitlines() if l.startswith(f"setblock {coffre} minecraft:chest") and "tripwire_hook" in l and "Clé rouge" in l]
        self.assertEqual(len(lignes), 1)                                                             # le coffre et la cle rouge qu'il contient
        self.assertIn(f"setblock {coffre} minecraft:air", pose)                                      # on repart d'une case vide
        self.assertIn("particle minecraft:end_rod", pose)
        self.assertIn(" force", pose)                                                                # un rayon de lumiere visible de loin
        for nom, texte in self.f.items():
            if nom not in ("suspendu_ss2_cle_tout", "suspendu_cine_cle"):
                self.assertNotIn("function atelier:suspendu_ss2_cle_coffre", texte, nom)               # seuls le levier 5 (tout de suite) et sa cinematique le posent
        self.assertEqual(self.blocs_ss2[self.infos3["coffre_cle"]], AIR)                              # et il n'est pas dans la structure : l'emplacement est vide

    def test_le_levier_5_lance_la_cinematique_sauf_si_une_autre_est_en_cours(self):
        l5 = self.f["suspendu_ss2_levier5"]
        self.assertIn("scoreboard players operation #libre cine = #t cine", l5)
        self.assertIn("execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_cle", l5)
        self.assertIn("execute unless score #libre cine matches 0 run function atelier:suspendu_ss2_cle_tout", l5)      # sinon tout apparait tout de suite
        tout = self.f["suspendu_ss2_cle_tout"].splitlines()
        n = len(self.infos3["ancrages_cyan"][5])
        self.assertEqual([l for l in tout if l.startswith("function")], [f"function atelier:suspendu_ss2_l5_a{i}" for i in range(1, n + 1)] + ["function atelier:suspendu_ss2_cle_coffre"])
        for k in (1, 2, 3, 4):
            self.assertNotIn("cine", self.f[f"suspendu_ss2_levier{k}"])                              # les autres leviers n'ont pas de cinematique

    def test_la_cinematique_de_la_cle_survole_la_salle_puis_montre_le_coffre(self):
        ox, oy, oz = dj.ORIGINE
        scene = self.f["suspendu_cine_cle"]
        images = re.findall(r"matches (\d+) run tp @e\[type=minecraft:armor_stand,tag=cine_cam\] (\S+) (\S+) (\S+) (\S+) (\S+)", scene)
        self.assertEqual(len(images), sj.DUREE_CLE)                                                  # une image par tick, plus longue que les 110 ticks des autres scenes
        pts = [(float(x) - ox, float(y) - oy, float(z) - oz, float(yaw), float(pitch)) for (_, x, y, z, yaw, pitch) in images]
        yf2 = self.infos3["yf2"]
        cx, cy, cz = self.infos3["coffre_cle"]
        for (x, y, z, yaw, pitch) in pts:
            self.assertTrue(yf2 + 2 <= y <= yf2 + 7.5, y)                                            # au dessus du sol des iles, sous la pointe des stalactites (>= YF2 + 8)
            self.assertTrue(86 <= x <= 234 and 23 <= z <= 201)                                       # dans la salle
        for a, b in zip(pts, pts[1:]):
            self.assertLessEqual(math.dist(a[:3], b[:3]), 1.2)                                       # jamais plus de 1,2 bloc par tick : au dela un joueur serait pris pour quelqu'un qui a quitte la camera (seuil 1,5)
            self.assertGreaterEqual(b[0], a[0] - 1e-6)                                               # elle avance toujours vers l'est
        self.assertLess(max(p[3] for p in pts) - min(p[3] for p in pts), 0.01)                       # elle ne tourne jamais (fluidite)
        self.assertLess(max(p[4] for p in pts) - min(p[4] for p in pts), 0.01)
        self.assertTrue(60 < abs(pts[0][3]) < 120 and abs(pts[0][4]) < 10)                           # elle regarde vers l'est, a plat (loin de +-180 ou l'angle saute)
        self.assertTrue(120 <= pts[0][0] <= 140)                                                      # elle part au bord est de la grotte du levier 5
        x0, y0, z0 = (int(math.floor(v)) for v in pts[0][:3])
        self.assertEqual(self.blocs_ss2.get((x0, y0, z0)), AIR)                                       # a l'air libre, pas dans la roche
        self.assertTrue(15 <= math.dist(pts[-1][:3], (cx + 0.5, cy + 0.5, cz + 0.5)) <= 25)         # elle finit a 15 a 25 blocs du coffre
        for k, (xx, yy, zz) in enumerate(self.infos3["ancrages_cyan"][5], start=1):
            ligne = next(l for l in scene.splitlines() if l.endswith(f"run function atelier:suspendu_ss2_l5_a{k}"))
            self.assertIn("matches", ligne)
        ticks_ancrages = [int(re.search(r"matches (\d+) run function atelier:suspendu_ss2_l5_a%d$" % k, scene, re.M).group(1)) for k in range(1, len(self.infos3["ancrages_cyan"][5]) + 1)]
        self.assertEqual(ticks_ancrages, sorted(ticks_ancrages))                                      # les ancrages apparaissent un a un, du plus proche au plus loin
        self.assertGreater(len(set(ticks_ancrages)), 3)
        t_coffre = int(re.search(r"matches (\d+) run function atelier:suspendu_ss2_cle_coffre$", scene, re.M).group(1))
        self.assertGreaterEqual(t_coffre, ticks_ancrages[-1] + 30)                                    # le coffre apparait apres le dernier ancrage
        self.assertLessEqual(t_coffre + 40, sj.DUREE_CLE)                                             # et on a le temps de le regarder
        self.assertIn("block.beacon.activate", scene)
        self.assertEqual(scene.count("block.note_block.chime"), 4)                                    # les quatre carillons
        debut = self.f["suspendu_cine_debut_cle"]
        self.assertIn("summon minecraft:armor_stand", debut)
        self.assertIn(f"positioned {dj.txt(self.infos3['leviers'][5])} as @a[distance=..{sj.RAYON_CLE},gamemode=!spectator]", debut)      # les joueurs de la grotte du levier 5
        cadre = self.f["suspendu_cine_cadre"]
        self.assertIn(f"matches {sj.DUREE_CLE + 2}.. run function atelier:suspendu_cine_fin", cadre)   # la scene s'arrete d'elle-meme, a sa propre duree
        self.assertRegex(cadre, r"unless score #id cine matches \d+ if score #t cine matches 112\.\. run function atelier:suspendu_cine_fin")      # sans arreter les autres a 112
        ids = re.findall(r"scoreboard players set #id cine (\d+)", "".join(v for k, v in self.f.items() if k.startswith("suspendu_cine_debut_")))
        self.assertEqual(len(ids), len(set(ids)))                                                     # un numero de scene par scene

    def test_les_ennemis_apparaissent_quand_on_s_approche_de_leur_ile(self):
        tick = self.f["suspendu_tick"]
        for nom, liste in self.infos3["ennemis"].items():
            self.assertIn(f"function atelier:suspendu_ss2_ennemis_{nom.lower()}", tick)
            texte = self.f[f"suspendu_ss2_ennemis_{nom.lower()}"]
            self.assertEqual(len(re.findall(r"summon minecraft:(zombie|skeleton) ", texte)), len(liste))
            self.assertIn("suspendu_ss2e", texte)
            self.assertIn(f"#e_{nom} suspendu 1", texte)
            self.assertIn(f"scoreboard players set #e_{nom} suspendu 0", self.f["suspendu_reset"])

    def test_les_noms_de_fonctions_sont_valides_pour_le_jeu(self):
        for nom in self.f:
            self.assertRegex(nom, r"^[a-z0-9_]+$", nom)                                              # un identifiant avec une majuscule est refuse : la fonction ne se chargerait pas
        for texte in self.f.values():
            for appelee in re.findall(r"function atelier:(\S+)", texte):
                self.assertRegex(appelee, r"^[a-z0-9_]+$", appelee)

    def test_toutes_les_fonctions_appelees_existent(self):
        for nom, texte in self.f.items():
            for appelee in re.findall(r"function atelier:([a-z0-9_]+)", texte):
                self.assertIn(appelee, self.f, f"{nom} appelle {appelee}")


if __name__ == "__main__":
    unittest.main()
