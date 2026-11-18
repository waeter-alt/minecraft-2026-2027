# [claude-build]
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import suspendu_salle1 as rdc  # noqa: E402
import suspendu_ss1 as plan  # noqa: E402
import suspendu_ss1_blocs as ss1  # noqa: E402


class TestSousSol1(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocs, cls.infos = ss1.construire()
        _, cls.infos_rdc = rdc.construire()

    def test_le_sol_est_28_sous_le_rez_de_chaussee_et_l_escalier_y_arrive(self):
        self.assertEqual(ss1.YF, rdc.Y0 - 28)
        self.assertEqual(self.infos["arrivee"][1], ss1.YF + 1)
        derniere = max(self.infos_rdc["marches"]["c1"], key=lambda m: m[2])
        self.assertEqual(derniere[1], ss1.YF + 2)                                         # la derniere des 27 marches : un cran au dessus du sol
        self.assertEqual(len({m[2] for m in self.infos_rdc["marches"]["c1"]}), 27)
        self.assertEqual(derniere[2] + 1, self.infos["arrivee"][2] - 0)                   # et le sol du sous-sol commence juste apres

    def test_le_sous_sol_ne_touche_pas_au_tunnel_de_l_escalier(self):
        for (x, y, z) in self.infos_rdc["marches"]["c1"]:
            if z >= ss1.TUNNEL_PROTEGE[2]:
                for dy in range(-1, 3):
                    self.assertNotIn((x, y + dy, z), self.blocs, (x, y + dy, z))          # ni la marche, ni l'air qui la surmonte

    def test_tout_reste_sous_le_rez_de_chaussee_et_dans_le_gabarit(self):
        self.assertLessEqual(max(p[1] for p in self.blocs), rdc.Y0 - 5)                    # au moins 4 blocs de roche entre le toit de la grande salle et le sol du rez-de-chaussee
        for (x, y, z) in self.blocs:
            self.assertTrue(0 <= x < 160 and 0 <= z < 160 and 0 < y < 120)

    def test_les_hauteurs_de_chaque_espace(self):
        par_genre = {}
        for (sol, haut, genre) in self.infos["cases"].values():
            par_genre.setdefault(genre, set()).add(haut - sol)
        self.assertEqual(par_genre["petite"], {8})                                         # petite salle, couloir, passage : 8 de haut
        self.assertEqual(par_genre["couloir"], {8})
        self.assertEqual(par_genre["hall"], {11})                                          # le hall et le bulbe : sol a YF + 11, air jusqu'a YF + 22
        self.assertEqual(par_genre["bulbe"], {11})
        self.assertEqual(par_genre["est"], {9})                                            # le couloir est et la salle de l'enigme : air jusqu'a YF + 20
        self.assertEqual(max(par_genre["musicale"]), 9)
        self.assertGreaterEqual(min(par_genre["musicale"]), 4)                             # sous l'escalier, au plus bas : 4 de haut, ca se traverse
        plafonds = {haut for (sol, haut, genre) in self.infos["cases"].values() if genre == "caverne"}
        self.assertEqual(max(plafonds), ss1.YF + 22)                                       # grande salle : un plafond plat a 22 au dessus du sol de la petite salle...
        self.assertGreater(len([1 for (s, h, g) in self.infos["cases"].values() if g == "caverne" and h == ss1.YF + 22]), 2000)
        self.assertGreaterEqual(min(plafonds), ss1.YF + 19)                                # ... sauf sous l'escalier de la porte 2, qu'il suit en gradins

    def test_le_plafond_suit_le_dessous_des_escaliers(self):
        for nom in ("c1", "c2"):
            for (x, y, z) in self.infos_rdc["marches"][nom]:
                for (sol, haut, genre) in [self.infos["cases"].get((x + dx, z))for dx in (-2, -1, 0, 1, 2) if (x + dx, z) in self.infos["cases"]]:
                    if genre not in ("petite", "passage", "couloir", "porte"):
                        self.assertLessEqual(haut + 2, y - 1, (nom, x, y, z, haut))        # le toit du sous-sol reste sous la marche

    def test_le_sol_de_la_grande_salle_monte_par_marches_d_un_bloc(self):
        sols = {c: s for c, (s, h, g) in self.infos["cases"].items() if g == "caverne"}                  # l'estrade et les piliers sont des hauteurs a part
        for (x, z), s in sols.items():
            for dx, dz in ((1, 0), (0, 1)):
                v = sols.get((x + dx, z + dz))
                if v is not None:
                    self.assertLessEqual(abs(v - s), 2, ((x, z), s, v))                    # jamais plus d'une marche, ou deux entre une plateforme et le sol
        self.assertGreaterEqual(max(sols.values()) - min(sols.values()), 7)                # et il monte bien d'une dizaine de blocs en tout

    def test_la_grande_salle_est_30_plus_profonde_avec_un_balcon_et_une_echelle(self):
        """Demande de Luca : la grande salle est creusee 30 blocs plus bas pour que les piliers soient plus hauts. Le passage d'arrivee reste a YF : il donne sur un balcon, d'ou une echelle descend jusqu'au sol."""
        cases = self.infos["cases"]
        sols = [v[0] for v in cases.values() if v[2] == "caverne"]
        self.assertEqual(min(sols), ss1.YF - 4 - ss1.PROFONDEUR_SALLE)                                  # le sol de la salle descend de YF - 34 ...
        self.assertLessEqual(max(sols), ss1.YF + 6 - ss1.PROFONDEUR_SALLE)                              # ... a YF - 24 (les bandes de 1 bloc d'avant, 30 plus bas)
        balcon = {c: v for c, v in cases.items() if v[2] == "balcon"}
        self.assertGreater(len(balcon), 60)
        self.assertTrue(all(v[0] == ss1.YF for v in balcon.values()))                                  # le balcon est au niveau du passage
        passage = [c for c, v in cases.items() if v[2] == "passage"]
        self.assertTrue(any((x - 1, z) in balcon for (x, z) in passage))                              # et touche le passage en arc
        ex, bas, haut_e, ez = self.infos["echelle_balcon"]
        self.assertEqual(cases[(ex, ez)][2], "caverne")
        self.assertEqual(cases[(ex + 1, ez)][2], "balcon")                                            # l'echelle est contre la paroi ouest du balcon
        self.assertEqual(haut_e, ss1.YF)
        self.assertGreaterEqual(haut_e - bas + 1, ss1.PROFONDEUR_SALLE - 4)                            # ~30 barreaux
        for y in range(bas, haut_e + 1):
            self.assertEqual(self.blocs[(ex, y, ez)][0], "minecraft:ladder", y)
            self.assertNotEqual(self.blocs[(ex + 1, y, ez)][0], "minecraft:air", y)                    # tenue par la paroi du balcon
        lanternes = [p for p, b in self.blocs.items() if b[0] == "minecraft:lantern" and p[0] == ex + 1 and abs(p[2] - ez) == 1]
        self.assertGreaterEqual(len(lanternes), 2 * ((haut_e - bas) // 4 - 1))                         # des lanternes en niche tout le long

    def test_le_balcon_a_un_garde_corps_sauf_devant_l_echelle(self):
        """Demande de Luca : un garde-corps sur le balcon (tomber de 30 blocs tue). Il borde tout le vide, sauf la case devant l'echelle."""
        cases = self.infos["cases"]
        garde = set(self.infos["garde_corps"])
        ex, bas, haut_e, ez = self.infos["echelle_balcon"]
        trou = (ex + 1, ez)
        self.assertNotIn(trou, garde)
        for (x, z), v in cases.items():
            if v[2] != "balcon":
                continue
            sur_le_vide = any(cases.get((x + dx, z + dz), (0, 0, ""))[2] == "caverne" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            self.assertEqual((x, z) in garde, sur_le_vide and (x, z) != trou, (x, z))
        self.assertGreater(len(garde), 8)
        for (x, z) in garde:
            nom, props = self.blocs[(x, ss1.YF + 1, z)]
            self.assertEqual(nom, "minecraft:stone_brick_wall")
            self.assertIn(self.blocs[(x, ss1.YF + 2, z)][0], ("minecraft:air", "minecraft:light"))       # un seul bloc de haut : au dessus, de l'air (ou un bloc de lumiere invisible), le tir du grappin passe
        self.assertEqual(cases[(ex, ez)][2], "caverne")                                                   # le trou donne sur la colonne de l'echelle
        self.assertEqual(self.blocs[(trou[0], ss1.YF + 1, trou[1])][0], "minecraft:air")                 # la breche est libre

    def test_l_estrade_du_grappin_est_en_hauteur_avec_une_echelle(self):
        """Demande de Luca : l'estrade du coffre du grappin est un peu en hauteur, comme les piliers, avec une echelle."""
        cases = self.infos["cases"]
        estrade = {c: v for c, v in cases.items() if v[2] == "estrade"}
        self.assertGreater(len(estrade), 200)
        for (x, z), (sol, haut, genre) in estrade.items():
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                v = cases.get((x + dx, z + dz))
                if v is not None and v[2] == "caverne":
                    self.assertGreaterEqual(sol - v[0], ss1.HAUT_ESTRADE - 1, ((x, z), sol, v[0]))      # au moins 5 de falaise tout autour : on n'y monte pas a pied
        ex, bas, haut_e, ez = self.infos["echelle_estrade"]
        self.assertEqual(cases[(ex, ez)][2], "caverne")
        self.assertEqual(cases[(ex, ez + 1)][2], "estrade")                                            # l'echelle est contre la face nord de l'estrade
        self.assertEqual(haut_e, cases[(ex, ez + 1)][0])
        self.assertGreaterEqual(haut_e - bas + 1, ss1.HAUT_ESTRADE)
        for y in range(bas, haut_e + 1):
            self.assertEqual(self.blocs[(ex, y, ez)][0], "minecraft:ladder", y)
            self.assertNotEqual(self.blocs[(ex, y, ez + 1)][0], "minecraft:air", y)                     # et tenue par la paroi de l'estrade
        cx, cy, cz = self.infos["coffre_grappin"]
        self.assertEqual(cases[(cx, cz)][2], "estrade")                                                # le coffre du grappin est sur l'estrade
        self.assertEqual(self.blocs[(cx, cy, cz)][0], "minecraft:air")                                 # mais n'existe pas dans la structure : il apparait quand le mini-boss est vaincu

    def test_l_enigme_du_bloc_musical(self):
        """Cinq blocs musicaux sur l'estrade de dalles de la salle de l'enigme, chacun sur un bloc d'instrument different, avec de l'air au dessus (sinon ils se taisent), une lampe suspendue et un bouton de depart."""
        e = self.infos["enigme"]
        self.assertEqual(sorted(e["sequence"]), [1, 2, 3, 4, 5])                                      # une melodie qui fait jouer chaque bloc une fois
        self.assertEqual(len(set(e["notes"])), 5)
        self.assertTrue(all(1 <= n <= 24 for n in e["notes"]))                                         # la note posee (n - 1) reste dans 0..24
        self.assertEqual(len(set(e["instruments"])), 5)
        z_pads = [p[2] for p in e["pads"]]
        self.assertEqual(z_pads, sorted(z_pads))
        for i, (x, y, z) in enumerate(e["pads"]):
            self.assertEqual(self.infos["cases"][(x, z)][2], "bloc")
            nom, props = self.blocs[(x, y, z)]
            self.assertEqual(nom, "minecraft:note_block")
            self.assertEqual(dict(props), {"instrument": e["instruments"][i], "note": str(e["notes"][i] - 1), "powered": "false"})
            self.assertEqual(self.blocs[(x, y + 1, z)][0], "minecraft:air")                            # de l'air juste au dessus
            self.assertEqual(self.blocs[(x, y + 2, z)][0], "minecraft:" + e["couleurs"][i] + "_concrete")      # la lampe, 2 au dessus
            haut = self.infos["cases"][(x, z)][1]
            self.assertTrue(all(self.blocs[(x, yy, z)][0] == "minecraft:chain" for yy in range(y + 3, haut + 1)))
            self.assertNotEqual(self.blocs[(x, y - 1, z)][0], "minecraft:deepslate_tiles")             # un bloc d'instrument dessous
        dx, dy, dz = e["depart"]
        self.assertEqual(self.blocs[(dx, dy, dz)][0], "minecraft:stone_button")
        self.assertLess(dz, min(z_pads))
        cx, cy, cz = self.infos["coffre_cle"]
        self.assertEqual(self.blocs[(cx, cy, cz)][0], "minecraft:air")                                 # la cle jaune n'est pas la au depart : elle apparait a la reussite
        lumieres = {(x, z) for (x, y, z) in self.infos["lumieres"]}
        for (x, y, z) in e["pads"]:
            self.assertNotIn((x, z), lumieres)                                                         # pas de bloc de lumiere au dessus d'un bloc musical

    def test_l_estrade_est_20_blocs_plus_haute_et_les_deux_piliers_ajoutes_font_marches(self):
        """Demande de Luca : l'estrade du coffre du grappin monte de 20 blocs (26 au dessus du sol) pour qu'on y atteigne les piliers, et 1 ou 2 piliers de plus."""
        self.assertEqual(ss1.HAUT_ESTRADE, 26)
        cases = self.infos["cases"]
        estrade = [v[0] - ss1.YF for v in cases.values() if v[2] == "estrade"]
        self.assertLessEqual(max(estrade), -6)                                                         # le sol de l'estrade : -7 / -6
        sommets = [p[1] - 1 - ss1.YF for p in self.infos["plateformes"]]
        self.assertEqual(sommets[5:], list(ss1.PILIERS_PLUS_SOMMET))                                   # les deux piliers ajoutes : deux marches (0 et +4)
        self.assertTrue(all(s > max(estrade) for s in sommets))                                        # tous les sommets sont au dessus de l'estrade : on les atteint d'en bas
        for (cx, y, cz) in self.infos["plateformes"][5:]:
            self.assertEqual(cases[(cx, cz)][2], "plateforme")
        ex, bas, haut_e, ez = self.infos["echelle_estrade"]
        self.assertEqual(haut_e - bas + 1, 26)                                                         # une echelle de 26 barreaux
        lanternes = [p for p, b in self.blocs.items() if b[0] == "minecraft:lantern" and abs(p[0] - ex) == 1 and p[2] == ez + 1 and bas <= p[1] <= haut_e]
        self.assertGreaterEqual(len(lanternes), 2 * ((haut_e - bas) // 4 - 1))                         # des lanternes en niche tout le long de l'echelle

    def test_les_piliers_sont_hauts_et_isoles_du_sol(self):
        plateformes = {c: v for c, v in self.infos["cases"].items() if v[2] == "plateforme"}
        self.assertEqual(len(self.infos["plateformes"]), 7)                                            # les 5 piliers du dessin + les 2 ajoutes (demande de Luca)
        for (cx, y, cz) in self.infos["plateformes"]:
            haut_p = y - 1
            self.assertEqual(plateformes[(cx, cz)][0], haut_p)
            self.assertGreaterEqual(sum(1 for c, v in plateformes.items() if abs(c[0] - cx) <= 2 and abs(c[1] - cz) <= 2 and v[0] == haut_p), 21)         # 5 x 5 sans les coins
            autour = [self.infos["cases"][(cx + dx, cz + dz)][0] for dx in range(-3, 4) for dz in range(-3, 4)
                      if (cx + dx, cz + dz) in self.infos["cases"] and self.infos["cases"][(cx + dx, cz + dz)][2] in ("caverne", "estrade")]
            self.assertGreaterEqual(haut_p - max(autour), ss1.PROFONDEUR_SALLE, (cx, cz))          # au moins 30 au dessus du sol qui l'entoure (la salle est 30 plus profonde) : on n'y monte pas a pied
            self.assertEqual(self.blocs[(cx, haut_p, cz)][0], "minecraft:stone_bricks")
            for y in range(haut_p - 5, haut_p):                                                 # le flanc du pilier (sa colonne du bord) est en briques de pierre, pas en roche
                self.assertIn(self.blocs[(cx + 2, y, cz)][0], ("minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:cracked_stone_bricks"), (cx, y, cz))

    def test_la_zone_haute_est_une_falaise_depuis_la_grande_salle(self):
        cases = self.infos["cases"]
        haute = {c for c, (s, h, g) in cases.items() if s >= ss1.SOL_HALL and g in ("hall", "bulbe", "est", "musicale", "seuil", "bloc")}
        self.assertTrue(all(cases[c][0] == ss1.SOL_HALL or cases[c][2] == "bloc" for c in haute))
        for (x, z) in haute:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                v = cases.get((x + dx, z + dz))
                if v is not None and v[2] in ("caverne", "estrade", "plateforme") and v[2] != "plateforme":
                    self.assertGreaterEqual(ss1.SOL_HALL - v[0], 5, ((x, z), v))           # le bulbe domine la grande salle d'au moins 5 blocs : une falaise
                if v is not None and v[2] in ("petite", "passage", "couloir"):
                    self.fail(f"la zone haute touche le parcours du bas en {(x, z)}")

    def test_les_fosses_barrent_tout_le_hall(self):
        fosses = {c for c, (s, h, g) in self.infos["cases"].items() if g == "fosse"}
        for (a, b) in self.infos["fosses"]:
            x_milieu = (a[0] + b[0]) // 2
            colonne = [(x, z) for (x, z), (s, h, g) in self.infos["cases"].items() if x == x_milieu and g in ("hall", "fosse")]
            self.assertGreater(len(colonne), 10)
            self.assertTrue(all(c in fosses for c in colonne), "un passage a pied contourne la fosse")
        for (x, z) in fosses:
            self.assertEqual(self.infos["cases"][(x, z)][0], ss1.SOL_HALL - ss1.PROFONDEUR_FOSSE)

    def test_les_ancrages_tiennent_au_plafond_par_une_chaine(self):
        self.assertEqual(len(self.infos["ancrages"]), 10)                                  # 5 piliers, la corniche, 2 fosses (au dessus de leur rive est), puis les 2 piliers ajoutes
        for (x, y, z) in self.infos["ancrages"]:
            self.assertEqual(self.blocs[(x, y, z)][0], "donjon:ancrage")
            plafond = self.infos["cases"][(x, z)][1]
            self.assertTrue(all(self.blocs[(x, yy, z)][0] == "minecraft:chain" for yy in range(y + 1, plafond + 1)), (x, y, z))
        for (x, y, z), (px, py, pz) in zip(self.infos["ancrages"][:5], self.infos["plateformes"]):
            self.assertEqual((x, z, y), (px, pz, py - 1 + ss1.HAUT_ANCRAGE))                # chaque ancrage est 4 au dessus de la plateforme de son pilier
        x, y, z = self.infos["ancrages"][5]                                                # celui de la corniche est a l'entree du hall, 3 au dessus du sol
        self.assertEqual((self.infos["cases"][(x, z)][2], y - ss1.SOL_HALL), ("hall", ss1.HAUT_ANCRAGE_CORNICHE))
        for (a, b), (x, y, z) in zip(self.infos["fosses"], self.infos["ancrages"][6:]):      # ceux des fosses sont au dessus de la rive est de leur fosse, a 3 du sol (on est tire d'un bord a l'autre)
            self.assertEqual(self.infos["cases"][(x, z)][2], "hall")
            self.assertEqual(y - ss1.SOL_HALL, ss1.HAUT_ANCRAGE_FOSSE)
            self.assertTrue(b[0] < x <= b[0] + 14 and a[1] < z < b[1], (a, b, x, z))

    def test_la_porte_bleue(self):
        cases = self.infos["cases_porte"]
        self.assertEqual(len(cases), 2 * self.infos["largeur_porte"])
        self.assertGreaterEqual(self.infos["largeur_porte"], 10)
        dalle, spirale = (s[0] for s in rdc.MATERIAUX_PORTES["1"])
        noms = {d[3] for d in self.infos["dalles"]}
        self.assertEqual(noms, {dalle, spirale})                                           # la dalle de la porte 1 (bleue) et sa spirale
        self.assertGreaterEqual(sum(1 for d in self.infos["dalles"] if d[3] == spirale), 20)
        for (x, z) in cases:                                                               # la porte ferme la petite salle de bout en bout (8 de haut au plus)
            self.assertIn(self.blocs[(x, ss1.YF + 1, z)][0], (dalle, spirale))
        self.assertTrue(all(z in (76, 77) for (x, z) in cases))

    def test_les_coffres_et_les_monstres(self):
        self.assertEqual(len(self.infos["coffres"]), 8)                                    # les coffres jaunes du dessin, vides pour l'instant
        for c in self.infos["coffres"]:
            self.assertEqual(self.blocs[c][0], "minecraft:chest")
        for c in (self.infos["coffre_grappin"], self.infos["coffre_cle"]):                 # les deux coffres verts : la structure laisse l'air, la fonction de remise a zero les pose avec leur contenu
            self.assertEqual(self.blocs[c][0], "minecraft:air")
        self.assertEqual(len(self.infos["monstres_petite"]), 2)
        self.assertEqual(len(self.infos["monstres_musicale"]), 2)
        for (x, y, z) in self.infos["monstres_petite"]:
            self.assertEqual(self.infos["cases"][(x, z)][2], "petite")
        for (x, y, z) in self.infos["monstres_musicale"]:
            self.assertEqual(self.infos["cases"][(x, z)][2], "musicale")

    def test_la_lumiere_couvre_tout_le_sol(self):
        feux = {(x, z) for (x, y, z) in self.infos["lumieres"]}
        for fosse in (True, False):
            sources = [(x, z) for (x, z) in feux if (self.infos["cases"].get((x, z), (0, 0, ""))[2] == "fosse") == fosse]
            for (x, z), (sol, haut, genre) in self.infos["cases"].items():
                if (genre == "fosse") == fosse and genre != "porte":
                    self.assertTrue(any(abs(x - a) + abs(z - b) <= ss1.LUM_PAS for a, b in sources), (x, z, genre))


class TestPlan(unittest.TestCase):
    def test_les_coordonnees_du_dessin(self):
        self.assertAlmostEqual(plan.vers_plan(*plan.ESCALIER_BAS_SP)[0], 44.5)             # le bas de l'escalier du dessin est le bas de celui de la porte 1
        self.assertAlmostEqual(plan.vers_plan(*plan.ESCALIER_BAS_SP)[1], 38.0)
        x0, x1 = min(p[0] for p in plan.CAVERNE), max(p[0] for p in plan.CAVERNE)
        z0, z1 = min(p[1] for p in plan.CAVERNE), max(p[1] for p in plan.CAVERNE)
        self.assertAlmostEqual((x1 - x0) * plan.ECHELLE, 51.5, delta=1)                   # la grande salle : environ 50 de large
        self.assertAlmostEqual((z1 - z0) * plan.ECHELLE, 87.0, delta=1)                   # et 85 de long


if __name__ == "__main__":
    unittest.main()
