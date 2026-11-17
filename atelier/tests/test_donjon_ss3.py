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
import suspendu_ss2_blocs as ss2  # noqa: E402
import suspendu_ss3_blocs as ss3  # noqa: E402
import suspendu_ss3_fonctions as sj  # noqa: E402
from suspendu_outils import AIR  # noqa: E402


class TestSalle3(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocs, cls.infos = ss3.construire()

    def test_tout_tient_dans_le_gabarit_sous_la_plaine(self):
        for (x, y, z) in self.blocs:
            self.assertTrue(0 <= x < dj.SX and 0 <= z < dj.SZ and -7 <= y < dj.SY, (x, y, z))               # jamais dans le socle (Y -8)
        for (x, y, z) in self.blocs:
            if x >= 245:
                self.assertLessEqual(y, 84, (x, y, z))                                                       # la salle du boss reste sous l'herbe (Y 85) : rien ne depasse sur la plaine

    def test_aucun_recouvrement_sauf_le_percage_du_mur_du_couloir_3(self):
        for autre, nom in ((ss1.construire()[0], "sous-sol 1"), (ss2.construire()[0], "salle -2")):
            self.assertEqual(len(set(autre) & set(self.blocs)), 0, nom)
        communs = set(rdc.construire()[0]) & set(self.blocs)
        self.assertTrue(communs)                                                                              # le palier perce le mur du bout du couloir 3...
        self.assertTrue(all(c[2] in (18, 19) and 69 <= c[0] <= 77 for c in communs), sorted(communs)[:3])     # ...en z 18 et 19, et nulle part ailleurs
        rez = rdc.construire()[0]
        self.assertTrue(all(rez[c][0] != "minecraft:air" for c in communs))                                   # ce ne sont que des blocs de mur du rez-de-chaussee, pas son air

    def test_le_palier_ouvre_le_couloir_3_et_l_escalier_fait_53_marches(self):
        self.assertEqual(self.infos["arrivee"], (73, rdc.Y0 + 1, 19))
        self.assertEqual(self.blocs[(73, rdc.Y0 + 1, 19)], AIR)
        marches = self.infos["marches"]
        par_x = {}
        for (x, y, z) in marches:
            par_x.setdefault(x, set()).add(y)
        xs = sorted(par_x)
        self.assertEqual(len(xs), ss3.NB_MARCHES)                                                           # 53 : une par colonne
        self.assertEqual(ss3.NB_MARCHES, 53)
        self.assertEqual([max(par_x[x]) for x in xs[:2]], [rdc.Y0, rdc.Y0 - 1])                             # une marche plus bas a chaque colonne
        self.assertTrue(all(b - a == 1 and max(par_x[b]) == max(par_x[a]) - 1 for a, b in zip(xs, xs[1:])))
        self.assertEqual(max(par_x[xs[-1]]), ss3.Y_SOL_SALLE + 2)                                           # la derniere marche mene au niveau du tunnel plat (sol a Y 31)
        self.assertEqual(ss3.Y_SOL_SALLE, 31)
        trous = {x for (a, b) in ss3.TROUS_TUNNEL for x in range(a - 1, b + 2)}                                    # (le bord d une fosse est irregulier : une case de plus, ici et la)
        for x in range(xs[-1] + 1, ss3.X_FIN):                                                               # puis le tunnel est plat jusqu'au couloir de la salle (hors des fosses : voir plus bas)
            self.assertIn(self.blocs[(x, ss3.Y_SOL_SALLE + 1, 11)][0], ("minecraft:air", "minecraft:chest"))        # (le coffre de l'ilot est sur l'axe du tunnel)
            if x not in trous:
                self.assertNotEqual(self.blocs[(x, ss3.Y_SOL_SALLE, 11)], AIR)

    def test_le_chemin_est_le_long_du_bord_nord_la_ou_rien_d_autre_ne_passe(self):
        for (x, y, z) in self.blocs:
            if x < 245:
                self.assertLessEqual(z, 20, (x, y, z))                                                       # le palier, l'escalier et le tunnel restent dans la bande z 7 a 20 du bord nord

    def test_l_arene_est_une_caverne_ronde_au_fond_de_lave(self):
        cx, cy, cz = self.infos["golem"]["centre"]
        self.assertEqual((cx, cz), (ss3.CX, ss3.CZ))
        self.assertEqual(self.blocs[(cx, cy, cz)], AIR)                                                       # le golem sort de la lave, au centre : l'air au dessus de la surface...
        for y in ss3.Y_LAVE:
            self.assertEqual(self.blocs[(cx, y, cz)][0], "minecraft:lava")                                    # ...la lave dessous
        self.assertGreater(sum(1 for v in self.blocs.values() if v[0] == "minecraft:lava"), 2 * 2000)
        self.assertEqual(ss3.Y_LAVE[1] + 1, ss3.Y_SURFACE)
        for (x, y, z), v in self.blocs.items():
            if v[0] == "minecraft:lava":
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):                                             # la lave ne coule pas : elle est entouree de roche, de lave ou d'air au dessus d'elle
                    self.assertNotEqual(self.blocs.get((x + dx, y, z + dz)), None, (x, y, z))
        sx, sy, sz = self.infos["depart_boss"]
        self.assertEqual(self.blocs[(sx, sy, sz)], AIR)
        self.assertNotEqual(self.blocs[(sx, sy - 1, sz)], AIR)                                                # le pont est solide
        self.assertEqual(sy - 1, ss3.Y_SOL_SALLE)
        self.assertEqual(len(self.infos["sortie"]), 12)
        for c in self.infos["sortie"]:
            self.assertEqual(self.blocs[c][0], "minecraft:iron_bars")                                         # la sortie est scellee jusqu'a la mort du boss

    def test_la_rampe_en_spirale_monte_d_une_marche_a_la_fois_et_entoure_le_golem(self):
        cx, cy, cz = self.infos["golem"]["centre"]
        r = self.infos["rampe"]
        self.assertGreater(r["cases"], 900)                                                                    # ~1140 cases entieres, moins les trous et les bords arraches
        self.assertLessEqual(r["pas"], 12)                                                                    # un point faible est a portee d'epee d'un sol de 7,6 en dessous a 4,4 au dessus de lui : une passe tous les 12 blocs au plus
        self.assertGreaterEqual(r["rayon"][0], 12.5)                                                          # hors de ses bras (11 de rayon)
        sols = {}
        for (x, y, z), v in self.blocs.items():
            if v[0] in ("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:cracked_stone_bricks") and self.blocs.get((x, y + 1, z)) == AIR and r["bas"] <= y <= r["haut"] and 12 < math.hypot(x + 0.5 - cx - 0.5, z + 0.5 - cz - 0.5) < 17 and z > 41:
                sols.setdefault((x, z), []).append(y)
        self.assertGreater(len(sols), 300)
        for (x, z), ys in sols.items():
            ys.sort()
            self.assertTrue(all(b - a >= r["pas"] - 1 for a, b in zip(ys, ys[1:])), (x, z, ys))              # les tours sont espaces d'un pas : on passe dessous sans se cogner
            for dx, dz in ((1, 0), (0, 1)):                                                                   # d'une case a la voisine, au plus une marche
                for y in ys:
                    voisin = sols.get((x + dx, z + dz))
                    if voisin and y < r["haut"] - 1:                                                                  # (le bout de la rampe est franc ; un trou ou un bord arrache peut manquer a la voisine : seules comptent les cases du meme tour)
                        self.assertTrue(all(abs(y - w) <= 1 for w in voisin if abs(y - w) < 6), (x, z, y, voisin))
        self.assertEqual(self.infos["rampe"]["haut"], 44)

    def test_le_tunnel_est_une_grotte_organique_et_non_plus_un_couloir_de_briques(self):
        roches = {"minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:mossy_cobblestone", "minecraft:dripstone_block", "minecraft:calcite", "minecraft:moss_block", "minecraft:deepslate", "minecraft:cobbled_deepslate",
                  "minecraft:blackstone", "minecraft:smooth_basalt", "minecraft:shroomlight"}
        murs, largeurs, hauteurs = [], set(), set()
        for (x, y, z), v in self.blocs.items():
            if 90 <= x <= 270 and y >= ss3.Y_SOL_SALLE + 1 and z <= 18 and any(self.blocs.get((x + dx, y, z + dz)) == AIR for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) and v != AIR and not v[0].endswith("_stairs") and v[0] not in (
                    "minecraft:chain", "minecraft:lantern", "minecraft:pointed_dripstone", "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:hanging_roots", "minecraft:light", "minecraft:chest", "grappin:ancrage"):
                murs.append(v[0])
        self.assertGreater(len(murs), 1000)
        self.assertFalse([n for n in murs if "stone_bricks" in n or "polished" in n], "plus de briques taillees dans le tunnel")
        self.assertGreater(sum(1 for n in murs if n in roches) / len(murs), 0.95)                            # de la roche brute (pierre, andesite, tuf, mousse...)
        for x in range(90, 270):
            zmin, zmax = ss3.bords_tunnel(x)
            largeurs.add(zmax - zmin + 1)
            hauteurs.add(ss3.haut_tunnel(x, 11))
            self.assertLessEqual(zmin, 8)                                                                      # au moins 7 de large (z 8 a 14)
            self.assertGreaterEqual(zmax, 14)
            self.assertGreaterEqual(zmin, 3)
            self.assertLessEqual(zmax, 15 if x < 116 else 17)                                                  # le rez-de-chaussee et le sous-sol 1 sont juste au sud
        self.assertGreaterEqual(len(largeurs), 6)                                                              # la largeur ondule
        self.assertGreaterEqual(len(hauteurs), 4)                                                              # le plafond aussi
        d = self.infos["tunnel"]["deco"]
        self.assertGreater(d["champignons"], 40)                                                               # des champignons lumineux dans les parois
        self.assertGreater(d["lianes"], 3)
        self.assertGreater(d["stalactites"], 20)
        self.assertGreater(d["racines"], 10)
        self.assertGreater(d["stalagmites"], 3)

    def test_trois_fosses_de_6_a_7_blocs_dans_le_tunnel_dont_une_avec_un_ilot(self):
        trous = self.infos["tunnel"]["trous"]
        self.assertEqual(len(trous), 4)                                                                        # trois fosses, la deuxieme coupee en deux par un ilot : quatre trous
        for (a, b) in trous:
            self.assertGreaterEqual(b - a + 1, 5)                                                              # un saut au sprint en franchit 4 au mieux
            for x in range(a, b + 1):
                for z in range(8, 15):                                                                         # sur toute la largeur du tunnel (au moins z 8 a 14)
                    self.assertEqual(self.blocs.get((x, ss3.Y_SOL_SALLE, z)), AIR, (x, z))
            self.assertEqual(self.blocs[((a + b) // 2, ss3.Y_LAVE_FOSSE[1], 8)][0], "minecraft:lava")        # de la lave au fond, 11 sous le sol
        ile = self.infos["tunnel"]["ile"]
        self.assertEqual(ile, list(ss3.ILE))
        self.assertEqual(trous[1][1] + 1, ile[0])                                                              # l'ilot est collé aux deux trous
        self.assertEqual(trous[2][0] - 1, ile[1])
        for x in range(ile[0], ile[1] + 1):
            for z in range(8, 15):
                self.assertNotEqual(self.blocs[(x, ss3.Y_SOL_SALLE, z)], AIR)                                  # l'ilot est entier, sur toute la largeur
        for x in range(ss3.ILE_PILE[0], ss3.ILE_PILE[1] + 1):
            for y in range(ss3.Y_LAVE_FOSSE[0], ss3.Y_SOL_SALLE):
                self.assertTrue(self.blocs[(x, y, ss3.ZMILIEU)][0].endswith("stone_bricks"), (x, y))           # une pile de briques le porte
        for (a, b), fo in zip(trous, self.infos["tunnel"]["fosses"]):
            (zx, zy, zz), (dx, dy, dz) = fo["zone"]
            self.assertLessEqual(zx, a)
            self.assertGreaterEqual(zx + dx, b)
            self.assertLess(zy + dy + 1.5, ss3.Y_SOL_SALLE + 1)                                                # la zone de chute n'englobe pas les points d'arrivee
            self.assertGreater(zy, ss3.Y_LAVE_FOSSE[1])                                                        # et reste au dessus de la lave
            self.assertEqual(self.blocs[fo["retour"]], AIR)
            self.assertNotEqual(self.blocs[(fo["retour"][0], fo["retour"][1] - 1, fo["retour"][2])], AIR)
        passages = self.infos["tunnel"]["passages"]
        self.assertEqual(len(passages), 4)

    def test_quatre_coffres_dans_le_tunnel_remplis_d_une_table_de_butin(self):
        coffres = {c["nom"]: c for c in self.infos["tunnel"]["coffres"]}
        self.assertEqual(sorted(coffres), ["chambre3", "corniche", "escalier", "ile"])
        for c in coffres.values():
            x, y, z = c["pos"]
            self.assertEqual(self.blocs[(x, y, z)][0], "minecraft:chest")
            self.assertNotEqual(self.blocs[(x, y - 1, z)], AIR)                                                # il repose sur un sol
            self.assertEqual(self.blocs[tuple(c["acces"])], AIR)                                               # une case libre pour l'ouvrir
        self.assertTrue(coffres["escalier"]["pos"][1] > 60 and coffres["escalier"]["pos"][0] < 130)           # le premier est dans l'escalier
        self.assertGreater(coffres["corniche"]["pos"][1], ss3.Y_SOL_SALLE + 4)                                 # un autre est sur une corniche haute
        self.assertTrue(ss3.ILE[0] <= coffres["ile"]["pos"][0] <= ss3.ILE[1])                                   # un autre sur l'ilot
        self.assertGreater(coffres["chambre3"]["pos"][0], ss3.TROUS_TUNNEL[-1][1])                              # le dernier apres la derniere fosse
        self.assertEqual(ss3.TABLE_BUTIN, "atelier:suspendu_tunnel")
        table = dj.TABLE_BUTIN_TUNNEL
        self.assertEqual(table["type"], "minecraft:chest")
        entrees = table["pools"][0]["entries"]
        self.assertGreaterEqual(len(entrees), 6)
        self.assertTrue(all(e["name"].startswith("minecraft:") and e["weight"] > 0 for e in entrees))
        import json as _json
        _json.dumps(table)                                                                                     # c'est du json
        import tempfile
        from suspendu_outils import ecrire_structure
        from nbtlib import Compound, String
        import nbtlib
        with tempfile.TemporaryDirectory() as tmp:                                                             # les coffres portent bien le LootTable dans la structure
            chemin = Path(tmp) / "x.nbt"
            ecrire_structure({(0, 0, 0): self.blocs[coffres["ile"]["pos"]]}, chemin, nbt={(0, 0, 0): Compound({"id": String("minecraft:chest"), "LootTable": String(ss3.TABLE_BUTIN)})})
            f = nbtlib.load(chemin)
            self.assertEqual(str(f["blocks"][0]["nbt"]["LootTable"]), "atelier:suspendu_tunnel")

    def test_le_pont_est_en_ruine_trois_trous_de_6_blocs_qu_on_ne_franchit_qu_au_grappin(self):
        pont = set(self.infos["pont"])
        troncons, trous = self.infos["troncons"], self.infos["trous"]
        self.assertEqual(len(troncons), 4)
        self.assertEqual(len(trous), 3)
        for (z0, z1) in trous:
            self.assertGreaterEqual(z1 - z0 + 1, 5)                                                           # un saut au sprint franchit 4 blocs au mieux
            for z in range(z0, z1 + 1):
                for x in range(ss3.PONT_X[0], ss3.PONT_X[1] + 1):
                    self.assertEqual(self.blocs.get((x, ss3.Y_SOL_SALLE, z)), AIR, (x, z))                    # le trou est un trou : rien sous les pieds, sur toute la largeur du pont
                    self.assertNotIn((x, ss3.Y_SOL_SALLE + 1, z), pont)
        for (z0, z1) in troncons:
            for z in range(z0, z1 + 1):
                self.assertIn((ss3.X_COULOIR, ss3.Y_SOL_SALLE + 1, z), pont)                                  # la file du milieu tient toujours : on peut se tenir sur chaque troncon
                self.assertNotEqual(self.blocs[(ss3.X_COULOIR, ss3.Y_SOL_SALLE, z)], AIR)
        self.assertEqual([t[0] - u[1] - 1 for u, t in zip(troncons, troncons[1:])], [6, 6, 6])
        self.assertEqual(troncons[0][0], ss3.Z_GORGE)                                                          # le premier troncon part du couloir
        etats = {self.blocs[(x, ss3.Y_SOL_SALLE, z)][0] for (x, _, z) in pont}
        self.assertTrue({"minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:stone_brick_stairs"} <= etats, etats)      # des briques fendues, moussues, des marches brisees
        manques = [(x, z) for (z0, z1) in troncons[:3] for z in range(z0, z1 + 1) for x in ss3.PONT_X if (x, ss3.Y_SOL_SALLE + 1, z) not in pont]
        self.assertGreater(len(manques), 3)                                                                    # des bords arraches

    def test_le_gouffre_est_creuse_jusqu_a_la_lave_sous_le_pont(self):
        for z in range(ss3.Z_GORGE, ss3.CZ - 22):
            self.assertEqual(self.blocs[(276, ss3.Y_SURFACE, z)], AIR, z)                                      # de l'air au fond (hors des piles du pont, qui sont en x 278 a 280)...
            for y in ss3.Y_LAVE:
                self.assertEqual(self.blocs[(276, y, z)][0], "minecraft:lava", (z, y))                         # ...de la lave dessous
        for (z0, z1) in self.infos["trous"]:
            self.assertEqual(self.blocs[(ss3.PONT_X[0], ss3.Y_SOL_SALLE - 5, (z0 + z1) // 2)], AIR)         # le vide sous le trou
        for y in (ss3.Y_SOL_SALLE + 1, ss3.Y_SOL_SALLE + 2, ss3.Y_SOL_SALLE + 3):                             # le couloir donne sur le gouffre, sans mur
            self.assertEqual(self.blocs[(ss3.X_COULOIR, y, ss3.Z_GORGE)], AIR)
            self.assertEqual(self.blocs[(ss3.X_COULOIR, y, ss3.Z_GORGE - 1)], AIR)
        y = ss3.Y_SOL_SALLE + 1
        while self.blocs.get((ss3.X_COULOIR, y, 26)) == AIR:
            y += 1
        self.assertTrue(ss3.GORGE["haut"] <= y <= ss3.GORGE["haut"] + 4, y)                                    # une voute de roche au dessus, a 47 a 50
        self.assertNotEqual(self.blocs[(ss3.X_COULOIR, y, 26)], AIR)

    def test_l_anneau_est_en_ruine_six_trous_de_6_blocs_et_un_ancrage_de_chaque_cote(self):
        trous = self.infos["rampe_trous"]
        theta = {tuple(c[:3]): c[3] for c in self.infos["rampe_theta"]}
        self.assertEqual(len(trous), 6)
        for (a, b) in trous:
            self.assertGreaterEqual((b - a) * math.pi / 180 * ss3.R_RAMPE[0], 5)                               # 5 blocs au moins sur la file interieure (la plus courte) : un saut au sprint en franchit 4 au mieux
            self.assertFalse([c for c, th in theta.items() if a <= th <= b], (a, b))                          # rien sous les pieds dans un trou, sur toute la largeur
        entre = [(b0, a1) for (_, b0), (a1, _) in zip(trous, trous[1:])]
        self.assertGreaterEqual(max(a - b for b, a in entre), 600)                                           # une bande du milieu entiere (de 238 a 887 degres) : c'est la qu'on se bat
        self.assertTrue(any(th < 238 for th in theta.values()) and any(th > 1083 for th in theta.values()))   # il reste du sol en bas et jusqu'au bout du haut
        ancrages = {a[0]: tuple(a[1:]) for a in self.infos["ancrages_boss"]}
        for i in range(1, 7):
            for cote in "ab":
                x, y, z = ancrages[f"rampe_{i}{cote}"]
                self.assertEqual(self.blocs[(x, y, z)][0], "grappin:ancrage")
                for yy in (y - 1, y - 2, y - 3):
                    self.assertTrue(self.blocs[(x, yy, z)][0].endswith("stone_bricks"), (i, cote, yy))        # une colonne brisee de 3 blocs porte l'ancrage
                self.assertNotIn((x, y - 3, z), theta)                                                         # on ne s'y tient pas : c'est une colonne
                self.assertTrue(self.blocs[(x, y - 4, z)][0].endswith("bricks"))                               # elle repose sur le sol de l'anneau
        self.assertTrue(any(v[0] == "minecraft:cracked_stone_bricks" for p_, v in self.blocs.items() if p_[1] > 8 and 12 < math.hypot(p_[0] + 0.5 - ss3.CX, p_[2] + 0.5 - ss3.CZ) < 17))   # des briques fendues

    def test_trente_neuf_ancrages_dont_deux_par_trou_du_pont_tenus_a_la_voute(self):
        ancrages = {a[0]: tuple(a[1:]) for a in self.infos["ancrages_boss"]}
        self.assertEqual(len(ancrages), 39)                                                                    # 6 au pont, 12 sur l'anneau, le balcon, l'alcove, 4 corniches, 15 dans le tunnel : 6 + 12 + 2 + 4 + 15
        self.assertEqual(len([n for n in ancrages if n.startswith("tunnel_")]), 15)                            # 2 par bord de fosse (l'ilot n'en a que 2, pour les deux sens) + celui de la corniche du coffre
        self.assertEqual(sorted(n for n in ancrages if n.startswith("pont_")), ["pont_a1", "pont_a2", "pont_b1", "pont_b2", "pont_c1", "pont_c2"])
        self.assertEqual(sorted(n for n in ancrages if n.startswith("corniche_")), ["corniche_ne", "corniche_no", "corniche_se", "corniche_so"])
        for nom, (x, y, z) in ancrages.items():
            self.assertEqual(self.blocs[(x, y, z)][0], "grappin:ancrage", nom)
            if not nom.startswith(("corniche_", "rampe_")):
                self.assertEqual(self.blocs[(x, y + 1, z)][0], "minecraft:chain", nom)                       # tenu par une chaine (jusqu'a la voute, ou au dessous de la rampe)
        for (a1, a2, (z0, z1)) in (("pont_a1", "pont_a2", self.infos["troncons"][1]), ("pont_b1", "pont_b2", self.infos["troncons"][2]), ("pont_c1", "pont_c2", self.infos["troncons"][3])):
            for nom in (a1, a2):
                x, y, z = ancrages[nom]
                self.assertTrue(ss3.Y_SOL_SALLE + 3 <= y <= ss3.Y_SOL_SALLE + 5, nom)                         # 3 a 5 au dessus du pont : on arrive juste au dessus de l'ilot d'en face et on tombe dessus
                self.assertTrue(z0 <= z <= z1 + 2, (nom, z))                                                    # au bout du troncon d'en face
        bx, by, bz = self.infos["balcon"]
        self.assertGreater(by - self.infos["rampe"]["haut"], 10)                                              # le balcon est bien au dessus du haut de la rampe : pas d'escalier pour y monter
        self.assertEqual(self.blocs[self.infos["coffre"]][0], "minecraft:chest")                              # le coffre de l'alcove (vide pour l'instant)

    def test_quatre_corniches_a_ancrage_mural_dans_la_paroi(self):
        self.assertEqual(sorted(self.infos["corniches"]), ["ne", "no", "se", "so"])
        cx, cz = ss3.CX, ss3.CZ
        for nom, (x, y, z) in self.infos["corniches"].items():
            self.assertEqual(self.blocs[(x, y, z)], AIR, nom)                                                  # on se tient sur le sol de la corniche
            self.assertNotEqual(self.blocs[(x, y - 1, z)], AIR, nom)
            self.assertGreater(math.hypot(x + 0.5 - cx, z + 0.5 - cz), ss3.R_RAMPE[1] + 5, nom)                # a plus de 5 blocs de la rampe : on ne saute pas d'une a l'autre
            _, ax, ay, az = next(a for a in self.infos["ancrages_boss"] if a[0] == "corniche_" + nom)
            self.assertEqual(ay, y + 1, nom)                                                                    # l'ancrage est encastre dans le mur du fond, 2 blocs au dessus du sol
            self.assertGreater(math.hypot(ax + 0.5 - cx, az + 0.5 - cz), math.hypot(x + 0.5 - cx, z + 0.5 - cz), nom)
            self.assertTrue(any(self.blocs.get((ax + dx, ay, az + dz)) == AIR for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))), nom)     # une face a l'air libre : le crochet y accroche

    def test_le_couloir_du_boss_prolonge_le_tunnel(self):
        x = ss3.X_COULOIR
        libres = ("minecraft:air", "minecraft:vine", "minecraft:cobweb", "minecraft:lantern", "minecraft:light", "minecraft:hanging_roots", "minecraft:chain")
        for y in (ss3.Y_SOL_SALLE + 1, ss3.Y_SOL_SALLE + 2):
            for z in range(14, 18):
                self.assertIn(self.blocs[(x, y, z)][0], libres, (x, y, z))                                    # la jonction entre le tunnel (z 14) et le couloir (z 15 a 17) est libre (la suite du couloir, de la salle de l'ancien donjon, a ses racines et ses lianes : le parcours a pied est verifie par verif_ss3.py)
        self.assertEqual(self.blocs[(x, ss3.Y_SOL_SALLE + 1, 14)], AIR)                                      # sans mur entre le tunnel et le couloir


class TestFonctionsSalle3(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        _, cls.infos = rdc.construire()
        _, cls.infos2 = ss1.construire()
        cls.infos3 = ss2.construire()[1]
        cls.infos4 = ss3.construire()[1]
        decalages = {n: (0, 0, 0) for n in ("salle1", "ss1", "ss2", "ss3", "porte1", "porte2", "porte3", "portem1")}
        cls.f, _ = dj.fonctions(cls.infos, decalages, cls.infos2, cls.infos3, cls.infos4)

    def test_le_boss_se_reveille_en_tete_de_pont(self):
        tick = self.f["suspendu_tick"]
        self.assertRegex(tick, r"execute if score #boss suspendu matches 0 positioned -?\d+ -?\d+ -?\d+ if entity @a\[gamemode=!spectator,dx=\d+,dy=5,dz=\d+\] run function atelier:suspendu_ss3_annonce")
        annonce = self.f["suspendu_ss3_annonce"]
        self.assertIn("scoreboard players set #boss suspendu 1", annonce)
        self.assertIn(f"schedule function atelier:suspendu_ss3_invoque {sj.DELAI_BOSS}t", annonce)
        invoque = self.f["suspendu_ss3_invoque"]
        self.assertEqual(invoque.count("summon grappin:golem"), 1)
        self.assertIn("unless entity @e[type=grappin:golem,distance=..150]", invoque)                         # jamais deux a la fois
        self.assertIn("Rotation:[180f,0f]", invoque)
        gx, gy, gz = (int(v) for v in dj.txt(self.infos4["golem"]["centre"]).split())
        self.assertIn(f"summon grappin:golem {gx + 0.5} {gy} {gz + 0.5}", invoque)                            # au centre de la salle, sur la lave

    def test_la_mort_du_golem_ouvre_la_sortie(self):
        self.assertEqual(sj.TAG_MORT, "golem_mort")                                                          # le tag de fonctions que le mod appelle (data/grappin/tags/function/golem_mort.json)
        self.assertIn("matches 2 run function atelier:suspendu_ss3_vaincu", self.f["suspendu_ss3_mort"])      # seulement si le boss avait ete invoque
        self.assertIn("function atelier:suspendu_ss3_ouvrir_sortie", self.f["suspendu_ss3_vaincu"])
        self.assertIn("minecraft:air", self.f["suspendu_ss3_ouvrir_sortie"])
        self.assertIn("minecraft:iron_bars", self.f["suspendu_ss3_fermer_sortie"])

    def test_la_chute_dans_le_gouffre_ramene_au_depart_du_pont_ou_en_tete_de_pont(self):
        tick = self.f["suspendu_tick"]
        self.assertIn("run function atelier:suspendu_ss3_chute", tick)
        self.assertIn("run kill @e[type=item,", tick)
        chute = self.f["suspendu_ss3_chute"]
        self.assertIn("slow_falling", chute)
        self.assertIn("function atelier:suspendu_ss3_decrocher with storage atelier:suspendu chute", chute)
        self.assertIn("$kill @e[type=grappin:crochet,nbt={Owner:$(uuid)}]", self.f["suspendu_ss3_decrocher"])
        sx, sy, sz = (float(v) for v in dj.txt(self.infos4["depart_boss"]).split())
        rx, ry, rz = (float(v) for v in dj.txt(self.infos4["retour_rampe"]).split())
        self.assertIn(f"if entity @s[tag=chute_pont] run tp @s {sx + 0.5} {sy} {sz + 0.5}", chute)             # tombe du pont : au depart du pont, a refranchir au grappin
        self.assertIn(f"unless entity @s[tag=chute_pont] run tp @s {rx + 0.5} {ry} {rz + 0.5}", chute)         # tombe de la rampe : en tete de pont
        self.assertIn("run tag @s add chute_pont", chute)
        self.assertIn("tag @s remove chute_pont", chute)                                                       # l'etiquette ne reste pas
        self.assertLess(chute.index("run tag @s add chute_pont"), chute.index("run tp @s"))
        (zx, zy, zz), (dx, dy, dz) = self.infos4["zone_chute"]
        self.assertLess(zy + dy + 1.5, self.infos4["depart_boss"][1])                                         # la zone de chute n'englobe pas les points d'arrivee (boucle de tp)
        self.assertLess(zy + dy + 1.5, self.infos4["retour_rampe"][1])
        self.assertLessEqual(zz, ss3.Z_GORGE)                                                                  # elle couvre aussi le gouffre sous le pont
        self.assertGreaterEqual(zz + dz, ss3.CZ + 26)
        self.assertLess(self.infos4["chute_pont_dz"] + zz, self.infos4["troncons"][3][0])                      # et la partie « pont » s'arrete avant la tete du pont
        x0, x1, z0, z1 = self.infos4["plateformes"]["D"]["bbox"]                                               # la tete du pont est la ou l'on reveille le boss
        self.assertEqual(z0, self.infos4["troncons"][3][0])

    def test_la_remise_a_zero_tue_le_boss_et_referme_la_sortie(self):
        reset = self.f["suspendu_reset"]
        for ligne in ("scoreboard players set #boss suspendu 0", "kill @e[type=grappin:golem]", "schedule clear atelier:suspendu_ss3_invoque", "function atelier:suspendu_ss3_fermer_sortie"):
            self.assertIn(ligne, reset)

    def test_les_noms_de_fonctions_sont_valides_et_existent(self):
        for nom in self.f:
            self.assertRegex(nom, r"^[a-z0-9_]+$", nom)
        for nom, texte in self.f.items():
            for appelee in re.findall(r"function atelier:([a-z0-9_]+)", texte):
                self.assertIn(appelee, self.f, f"{nom} appelle {appelee}")

    def test_le_gabarit_se_charge_en_deux_moities_de_moins_de_256_chunks(self):
        lignes = [l for l in self.f["suspendu_pose"].splitlines() if l.startswith("forceload add")]
        self.assertEqual(len(lignes), 2)
        zones = []
        for l in lignes:
            x0, z0, x1, z1 = (int(v) for v in l.split()[2:6])
            chunks = ((x1 >> 4) - (x0 >> 4) + 1) * ((z1 >> 4) - (z0 >> 4) + 1)
            self.assertLessEqual(chunks, 256, l)                                                               # la limite d'une commande forceload
            zones.append((x0, x1))
        self.assertEqual(min(z[0] for z in zones), dj.ORIGINE[0])
        self.assertEqual(max(z[1] for z in zones), dj.ORIGINE[0] + dj.SX - 1)                                  # et tout le gabarit y est
        self.assertTrue(any("place template atelier:suspendu_ss3 " in s for e, s in self.f.items() if e.startswith("suspendu_pose_2")))

    def test_chaque_fosse_du_tunnel_ramene_au_bord(self):
        tick = self.f["suspendu_tick"]
        for k, fo in enumerate(self.infos4["tunnel"]["fosses"], 1):
            self.assertIn(f"run function atelier:suspendu_ss3_fosse_{k}", tick)
            texte = self.f[f"suspendu_ss3_fosse_{k}"]
            self.assertIn("slow_falling", texte)
            self.assertIn("function atelier:suspendu_ss3_decrocher with storage atelier:suspendu chute", texte)
            tx, ty, tz = (float(v) for v in dj.txt(fo["retour"]).split())
            self.assertIn(f"tp @s {tx + 0.5} {ty} {tz + 0.5}", texte)


if __name__ == "__main__":
    unittest.main()
