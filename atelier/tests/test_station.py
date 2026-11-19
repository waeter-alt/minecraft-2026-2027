# [claude-build]
"""Les fonctions et la geometrie des actes I et II du Donjon de la Station (donjon_station.py, station_acte1.py, station_cine.py). Le parcours (a pied, au grappin, la lumiere) : verif_station.py."""
import math
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import donjon_station as ds  # noqa: E402
import station_acte1 as a1  # noqa: E402
import station_logique as sl  # noqa: E402
import station_secteurs as s2  # noqa: E402
import suspendu_cine as sc  # noqa: E402
from test_suspendu_cine import PARTICULES_1_21_1  # noqa: E402

BLOCS, INFOS = a1.construire()
F = ds.fonctions(INFOS, {n: (0, 0, 0) for n in ("station_cuve", "station_hall") + s2.ORDRE})
PARTS, INFOS2 = s2.construire()
TOUT = s2.tout()


def lignes(nom):
    return [ligne for ligne in F[nom].splitlines() if ligne and not ligne.startswith("#")]


class TestFonctions(unittest.TestCase):
    def test_toutes_les_fonctions_appelees_existent(self):
        for nom, texte in F.items():
            for m in re.finditer(r"function atelier:([a-z_0-9]+)", texte):
                self.assertIn(m.group(1), F, nom)

    def test_les_particules_existent_dans_le_jeu(self):
        for nom, texte in F.items():
            for m in re.finditer(r"particle minecraft:([a-z_]+)", texte):
                self.assertIn(m.group(1), PARTICULES_1_21_1 | {"block"}, nom)          # « block » n'a pas de fichier de particule, mais existe

    def test_les_noms_de_fonction_sont_valides(self):
        for nom in F:
            self.assertRegex(nom, r"^[a-z0-9_]+$")                # une majuscule fait refuser la fonction

    def test_aucun_fill_ne_depasse_la_limite_du_jeu(self):
        for nom, texte in F.items():
            for m in re.finditer(r"fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) ", texte):
                a = [int(v) for v in m.groups()]
                self.assertLessEqual((abs(a[3] - a[0]) + 1) * (abs(a[4] - a[1]) + 1) * (abs(a[5] - a[2]) + 1), 32768, nom)

    def test_le_volant_du_bassin_attend_la_fin_de_l_embuscade(self):
        tick = lignes("station_tick")
        compte = [ligne for ligne in tick if "scoreboard players add #cv2 station 1" in ligne]
        self.assertEqual(len(compte), 1)
        self.assertIn("if score #emb station matches 2", compte[0])                     # tourne avant la fin de l'embuscade, il ne compte pas
        refus = [ligne for ligne in tick if "station_volant_2_refus" in ligne]
        self.assertEqual(len(refus), 1)
        self.assertIn("unless score #emb station matches 2", refus[0])
        self.assertIn("set #emb station 2", F["station_embuscade_fin"])
        emb = F["station_embuscade"]
        self.assertEqual(emb.count("summon minecraft:zombie"), 3)
        self.assertEqual(emb.count("summon minecraft:skeleton"), 1)

    def test_trois_temoins_puis_le_sas_et_le_casier(self):
        for k in (1, 2, 3):
            self.assertIn("scoreboard players add #temoins station 1", F[f"station_volant_{k}"])
            self.assertIn("lit=true", F[f"station_volant_{k}"])
            self.assertIn("matches 3 run function atelier:station_trois_volants", F[f"station_volant_{k}"])
        t = F["station_trois_volants"]
        self.assertIn("matches 0 run function atelier:station_cine_debut_sas", t)
        self.assertIn("unless score #libre cine matches 0 run function atelier:station_sas_ouvrir", t)
        self.assertIn("unless score #libre cine matches 0 run function atelier:station_casier", t)
        self.assertIn(ds.CAPE, F["station_casier"])
        x, y, z = INFOS["casier"]
        haut = ds.txt((x, y + 1, z))
        self.assertIn(f"setblock {haut} minecraft:air", F["station_casier"])          # on vide les deux moitiés avant de reposer le casier plein
        self.assertIn("donjon:casier[facing=south,couleur=orange]{Items:", F["station_casier"])
        self.assertIn(f"setblock {ds.txt((x, y, z))} donjon:casier[facing=south,couleur=orange]\n", F["station_reset"])
        self.assertEqual(BLOCS[(x, y, z)][0], "donjon:casier")
        self.assertEqual(dict(BLOCS[(x, y + 1, z)][1])["half"], "upper")
        appels = [nom for nom, texte in F.items() if "function atelier:station_casier" in texte]
        self.assertEqual(sorted(appels), ["station_cine_sas", "station_trois_volants"])

    def test_la_vanne_se_leve_et_se_referme(self):
        self.assertIn("minecraft:air", F["station_sas_ouvrir"])
        self.assertIn("replace minecraft:air", F["station_eau_40"])
        self.assertIn("minecraft:iron_bars", F["station_reset"])
        self.assertTrue(all(BLOCS[p][0] == "minecraft:iron_bars" for p in INFOS["vanne_sas"]))

    def test_la_mise_en_eau_remplit_chaque_couche_une_fois(self):
        cine = F["station_cine_eau"]
        for y in range(a1.FOND_CUVE + 1, a1.NIVEAUX["haut"] + 1):
            self.assertEqual(cine.count(f"run function atelier:station_eau_{y}\n"), 1, y)
            self.assertIn(f"function atelier:station_eau_{y}", F["station_eau_tout"])
        self.assertNotIn(f"station_eau_{a1.NIVEAUX['haut'] + 1}", F)

    def test_l_eau_ne_remplace_que_l_interieur_de_la_cuve(self):
        for (xa, xb, z) in INFOS["cuve"]["rangees"]:
            for x in (xa, xb):
                self.assertLess(math.hypot(x + 0.5 - a1.CUVE[0], z + 0.5 - a1.CUVE[1]), a1.R_CUVE + 1)

    def test_la_camera_monte_au_dessus_de_l_eau_sans_tourner(self):
        cam, vise = ds.scene_eau(INFOS)
        images = sc.images(cam, vise, ds.DUREE_EAU)
        surface = a1.FOND_CUVE
        couches = {ds.tick_couche(y): y for y in range(a1.FOND_CUVE + 1, a1.NIVEAUX["haut"] + 1)}
        for t, (x, y, z, yaw, pitch) in enumerate(images, start=1):
            surface = max(surface, couches.get(t, surface))
            self.assertGreater(y, surface + 1, t)
            self.assertLess(math.hypot(x - a1.CUVE[0], z - a1.CUVE[1]), a1.R_CUVE - 2)
            self.assertAlmostEqual(yaw, images[0][3], places=3)
            self.assertAlmostEqual(pitch, images[0][4], places=3)

    def test_les_cinematiques_finissent_et_rendent_les_joueurs(self):
        cadre = F["station_cine_cadre"]
        self.assertIn(f"matches {ds.DUREE_EAU + 2}.. run function atelier:station_cine_fin", cadre)
        self.assertIn(f"matches {ds.DUREE_SAS + 2}.. run function atelier:station_cine_fin", cadre)
        self.assertIn("station_cine_retour_joueur", F["station_cine_fin"])
        self.assertIn("function atelier:station_cine_fin", F["station_reset"])

    def test_la_pose_finit_par_la_remise_a_zero(self):
        etapes = sorted((nom for nom in F if nom.startswith("station_pose_2")), key=lambda n: int(n.split("_")[-1]) if n != "station_pose_2" else 0)
        self.assertIn("function atelier:station_reset", F[etapes[-1]])
        self.assertIn("place template atelier:station_hall", "".join(F[n] for n in etapes))
        self.assertIn("place template atelier:station_cuve", "".join(F[n] for n in etapes))


class TestGeometrie(unittest.TestCase):
    def test_les_temoins_sont_dans_les_murs(self):
        for p in INFOS["temoins_sas"] + INFOS["temoins_pompes"]:
            self.assertEqual(BLOCS[p][0], "minecraft:waxed_copper_bulb", p)

    def test_les_volants_sont_des_volants_create_lus_par_le_datapack(self):
        for k, p in INFOS["leviers"].items():
            self.assertRegex(BLOCS[p][0], r"^create:[a-z_]*valve_handle$", p)
            self.assertNotIn(BLOCS[(p[0], p[1] - 1, p[2])][0], ("minecraft:air", "minecraft:water"), p)
            self.assertIn(f"run data get block {ds.txt(p)} InUse", F["station_tick"])
        for cle in ("pA", "pB", "pC"):
            self.assertIn(f"scoreboard players add #c{cle} station 1", F["station_tick"])

    def test_les_ancrages_sont_poses(self):
        for (nom, x, y, z) in INFOS["ancrages"]:
            self.assertEqual(BLOCS[(x, y, z)][0], "donjon:ancrage", nom)

    def test_l_escalier_a_vingt_quatre_marches(self):
        self.assertEqual(len({(y, z) for (_, y, z) in INFOS["marches"]}), a1.NB_MARCHES)

    def test_la_coupure_entre_les_deux_structures_ne_coupe_pas_la_cuve(self):
        self.assertTrue(all(z < ds.Z_COUPURE for (_, _, z) in INFOS["cuve"]["rangees"]))


class TestSuite(unittest.TestCase):
    """Le Drain, les secteurs, la Cheminee, le Reservoir (station_secteurs.py, station_logique.py)."""

    def test_toutes_les_variables_sont_initialisees(self):
        for v in sl.VARIABLES:
            self.assertIn(f"unless score {v} station matches", F["station_charger"])
            self.assertIn(f"scoreboard players set {v} station 0", F["station_reset"] + F["station_ecluses_fermer"])

    def test_la_cuve_reste_seche_avant_la_mise_en_eau(self):
        for (xa, xb, z) in INFOS["cuve"]["rangees"]:
            for x in (xa, (xa + xb) // 2, xb):
                for y in (31, 40, 60):
                    self.assertNotEqual(TOUT.get((x, y, z), ("minecraft:air", ()))[0], "minecraft:water", (x, y, z))

    def test_une_pompe_baisse_la_cuve_d_un_cran(self):
        pf = F["station_pompe_faite"]
        self.assertIn("matches 1 if score #libre cine matches 0 run function atelier:station_cine_debut_median", pf)
        self.assertIn("matches 2 if score #libre cine matches 0 run function atelier:station_cine_debut_bas", pf)
        self.assertIn("matches 3 if score #libre cine matches 0 run function atelier:station_cine_debut_vanne_d", pf)
        for nom in ("station_pompe_a", "station_pompe_b", "station_pompe_c"):
            self.assertIn("function atelier:station_pompe_faite", F[nom])
        self.assertIn(f"set #niv station {a1.NIVEAUX['median']}", F["station_baisse_median"])
        self.assertIn(f"set #niv station {a1.NIVEAUX['bas']}", F["station_baisse_bas"])
        for y in range(a1.NIVEAUX["bas"] + 1, a1.NIVEAUX["haut"] + 1):
            self.assertEqual(F["station_cine_median"].count(f"station_vide_{y}\n") + F["station_cine_bas"].count(f"station_vide_{y}\n"), 1, y)

    def test_les_bouches_s_ouvrent_pour_de_bon_quand_la_cuve_est_pleine(self):
        self.assertIn("function atelier:station_bouches_ouvrir", F["station_eau_tout"])
        self.assertIn("function atelier:station_bouches_ouvrir", F["station_cine_eau"])
        for s_, e in INFOS2["ecluses"].items():
            for p in e["externe"] + e["interne"]:
                self.assertIn(f"setblock {ds.txt(p)} minecraft:water", F["station_bouches_ouvrir"])
        self.assertNotIn("station_ecluses_fermer", F["station_pompe_faite"])          # baisser la Cuve ne rescelle rien
        self.assertIn("function atelier:station_ecluses_fermer", F["station_reset"])

    def test_le_toboggan_descend_du_sommet_au_bassin(self):
        r0, r1 = a1.TOBOGGAN["r"]
        anneau = [p for p, b in BLOCS.items() if r0 <= math.hypot(p[0] + 0.5 - a1.TOUR[0], p[2] + 0.5 - a1.TOUR[1]) < r1]
        self.assertFalse([p for p in anneau if BLOCS[p][0] == "minecraft:water"])                     # sans eau (demande de Luca)
        sols = {p[1] for p in anneau if BLOCS[p][0] in ("minecraft:light_blue_concrete", "minecraft:white_concrete") and BLOCS.get((p[0], p[1] + 1, p[2])) == ("minecraft:air", ())}
        self.assertIn(a1.TOBOGGAN["haut"], sols)
        self.assertIn(a1.FOND50, sols)                                                                  # la derniere marche est au niveau du fond du bassin
        self.assertGreaterEqual(a1.SOMMET_TOUR - a1.TOBOGGAN["haut"], 3)                                 # on saute dans la goulotte, on ne la remonte pas jusqu'au sommet

    def test_les_courants_ne_poussent_que_qui_flotte(self):
        poussees = [ligne for ligne in F["station_tick"].splitlines() if " run tp @s ~" in ligne]
        self.assertGreaterEqual(len(poussees), 5)
        for ligne in poussees:
            self.assertIn("if block ~ ~-0.6 ~ minecraft:water", ligne)

    def test_les_echelles_tiennent(self):
        dos = {"north": (0, 0, 1), "south": (0, 0, -1), "east": (-1, 0, 0), "west": (1, 0, 0)}
        for p, b in TOUT.items():
            if b[0] == "minecraft:ladder":
                d = dos[dict(b[1])["facing"]]
                q = (p[0] + d[0], p[1], p[2] + d[2])
                appui = TOUT.get(q, ("minecraft:stone", ()))[0]
                self.assertNotIn(appui, ("minecraft:air", "minecraft:water", "minecraft:ladder"), (p, q))

    def test_chaque_pompe_a_son_volant_create_au_bon_endroit(self):
        for cle, sect in (("pA", "a"), ("pB", "b"), ("pC", "c")):
            p = INFOS2[sect]["volant_pos"]
            self.assertRegex(TOUT[p][0], r"^create:[a-z_]*valve_handle$", (cle, p))         # un volant pose dans la roche ne se voit pas (bug du 18/11)
            self.assertNotIn(TOUT.get((p[0], p[1] - 1, p[2]), ("minecraft:water",))[0], ("minecraft:water", "minecraft:air"), (cle, p))
            self.assertIn(f"data get block {ds.txt(p)} InUse", F["station_tick"])

    def test_les_panneaux_n_ont_pas_d_apostrophe_qui_casse_la_fonction(self):
        import re
        for ligne in lignes("station_panneaux"):
            m = re.search(r"messages:\[(.*)\]\}", ligne)
            self.assertIsNotNone(m, ligne)
            libres = len(re.findall(r"(?<!\\)'", m.group(1)))
            self.assertEqual(libres, 8, ligne)                   # 4 chaines entre apostrophes ; une apostrophe non echappee fait refuser toute la fonction (bug du 18/11)

    def test_la_vanne_d_se_leve_rangee_par_rangee_en_cinematique(self):
        cine = F["station_cine_vanne_d"]
        rangs = sorted({p[1] for p in INFOS2["d"]["vanne_d"]})
        for y in rangs:
            self.assertIn(f"run function atelier:station_vanne_d_rang_{y}\n", cine)
        cases = [p for y in rangs for p in INFOS2["d"]["vanne_d"] if p[1] == y]
        self.assertEqual(len(cases), len(INFOS2["d"]["vanne_d"]))
        self.assertIn("function atelier:station_vanne_d_ouverte", cine)
        self.assertNotIn("yellow", F["station_objectif"])

    def test_le_boss_est_dans_une_fonction_a_part(self):
        for nom, texte in F.items():
            if "summon donjon:leviathan" in texte:
                self.assertEqual(nom, "station_boss_apparition")
        self.assertIn("unless entity @e[tag=station_boss] run function atelier:station_boss_vaincu", F["station_tick"])

    def test_la_bonde_est_scellee_jusqu_au_levier_general(self):
        for p in INFOS2["d"]["bonde"]:
            self.assertEqual(TOUT[p][0], "minecraft:waxed_copper_grate")
        for p in INFOS2["d"]["puits_bonde"]:
            self.assertNotIn(TOUT.get(p, ("minecraft:stone", ()))[0], ("minecraft:air", "minecraft:water"))
        self.assertIn("minecraft:magma_block", F["station_bonde_ouvrir"])
        self.assertIn("function atelier:station_cine_debut_bonde", F["station_levier_general"])
        self.assertIn("function atelier:station_bonde_ouvrir", F["station_cine_bonde"])
        self.assertNotIn("bonde est ouverte", F["station_levier_general"] + F["station_bonde_ouvrir"])


if __name__ == "__main__":
    unittest.main()
