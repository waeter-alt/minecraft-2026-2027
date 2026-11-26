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

    def test_la_valve_du_bassin_ne_depend_plus_de_l_embuscade_mais_un_coffre_apparait(self):
        tick = lignes("station_tick")
        valve2 = [ligne for ligne in tick if "station_volant_2" in ligne]
        self.assertEqual(len(valve2), 1)
        self.assertNotIn("#emb", valve2[0])                                              # les trois valves s'ouvrent sans contrainte
        self.assertNotIn("station_volant_2_refus", F)
        fin = F["station_embuscade_fin"]
        self.assertIn("set #emb station 2", fin)
        x, y, z = INFOS["coffre_piscine"]
        self.assertIn(f"setblock {ds.txt((x, y, z))} minecraft:chest[", fin)           # tous les monstres vaincus : un coffre apparait dans la piscine
        self.assertIn(f"setblock {ds.txt((x, y, z))} minecraft:air", F["station_reset"])
        self.assertEqual(BLOCS[(x, y, z)][0], "minecraft:air")                           # la piscine est vide
        emb = F["station_embuscade"]
        self.assertEqual(emb.count("summon minecraft:zombie"), 6)
        self.assertEqual(emb.count("summon minecraft:skeleton"), 2)
        self.assertEqual(emb.count('id:"minecraft:bow"'), 2)                                       # chaque squelette a son arc
        for p in INFOS["embuscade"]:
            self.assertEqual(BLOCS[p], a1.AIR, p)

    def test_trois_valves_allument_trois_voyants_puis_ouvrent_la_porte_du_vestiaire(self):
        for k in (1, 2, 3):
            self.assertIn(f"run function atelier:station_volant_{k}", "\n".join(lignes("station_tick")))
            self.assertIn(f"summon minecraft:marker", F[f"station_volant_{k}"])             # le trait de lumiere part de la valve
            self.assertIn(f'Tags:["station_trait_{k}"]', F[f"station_volant_{k}"])
            self.assertIn("run function atelier:station_trait_fin_" + str(k), F[f"station_trait_{k}"])
            fin = F[f"station_trait_fin_{k}"]
            self.assertIn("scoreboard players add #temoins station 1", fin)
            self.assertIn("lit=true", fin)
            self.assertIn("matches 3 run function atelier:station_vestiaire_ouvrir", fin)
        self.assertIn("Ouverte set value 1b", F["station_vestiaire_ouvrir"])
        self.assertIn("station_porte_vestiaire", F["station_vestiaire_ouvrir"])
        self.assertIn("replace minecraft:barrier", F["station_vestiaire_barriere"])

    def test_les_voyants_sont_au_dessus_de_la_porte_du_vestiaire(self):
        porte = INFOS["portes"]["vestiaire"]
        for (x, y, z) in INFOS["temoins_sas"]:
            self.assertGreater(y, a1.SOL + 5)                                               # au dessus du linteau (la porte fait 5 de haut)
            self.assertLessEqual(abs(x - porte["x"]), 2)
            self.assertTrue(porte["z"] - porte["largeur"] / 2 <= z + 0.5 <= porte["z"] + porte["largeur"] / 2)

    def test_la_cape_est_dans_une_cabine_orange_et_le_levier_du_sas_apparait_quand_on_la_prend(self):
        x, y, z = INFOS["casier"]
        self.assertEqual(dict(BLOCS[(x, y, z)][1])["couleur"], "orange")
        self.assertEqual(dict(BLOCS[(x, y + 1, z)][1])["half"], "upper")
        reset = F["station_reset"]
        self.assertIn(f"setblock {ds.txt((x, y, z))} donjon:casier[facing=north,couleur=orange]{{Items:[{{Slot:13b,id:\"{ds.CAPE}\"", reset)
        oranges = {(px, pz) for (px, py, pz), b in BLOCS.items() if b[0] == "donjon:casier" and dict(b[1])["couleur"] == "orange" and dict(b[1])["half"] == "lower"}
        self.assertGreater(len(oranges), 3)                                                 # une cabine orange parmi plusieurs
        tick = "\n".join(lignes("station_tick"))
        self.assertIn(f"unless data block {ds.txt((x, y, z))} Items[{{id:\"{ds.CAPE}\"}}] run function atelier:station_cape_prise", tick)
        self.assertIn("if score #vest station matches 1 if score #cape station matches 0", tick)      # on ne regarde le casier que si quelqu'un est entre au Vestiaire
        lv = INFOS["levier_sas"]
        self.assertEqual(BLOCS[lv], a1.AIR)                                                 # la case du levier est libre tant que la cape n'est pas prise
        self.assertIn(f"setblock {ds.txt(lv)} donjon:levier_station[", F["station_cape_prise"])
        self.assertIn(f"if block {ds.txt(lv)} donjon:levier_station[powered=true] run function atelier:station_levier_sas", tick)
        self.assertNotIn(BLOCS[(lv[0] + 1, lv[1], lv[2])][0], ("minecraft:air", "minecraft:water"))                # un levier au mur (face=wall, facing=west) tient au mur est
        t = F["station_levier_sas"]
        self.assertIn("matches 0 run function atelier:station_cine_debut_sas", t)
        self.assertIn("unless score #libre cine matches 0 run function atelier:station_sas_ouvrir", t)
        self.assertIn("la Cape lestée se trouve dans l'une des cabines orange".lower(), F["station_vestiaire_entree"].lower())

    def test_les_portes_du_vestiaire_et_du_sas_sont_des_cloisons_a_barriere(self):
        for nom, cases in (("sas", INFOS["vanne_sas"]), ("vestiaire", INFOS["porte_vestiaire"])):
            p = INFOS["portes"][nom]
            self.assertTrue(all(BLOCS[c][0] == "minecraft:barrier" for c in cases), nom)
            self.assertIn(f"summon donjon:porte_station", F["station_reset"])
            self.assertIn(f"station_porte_{nom}", F["station_reset"])
            self.assertEqual(p["hauteur"], 5)
        self.assertEqual(len(INFOS["vanne_sas"]), 7 * 5)
        self.assertIn("Ouverte set value 1b", F["station_sas_ouvrir"])
        self.assertIn("replace minecraft:barrier", F["station_sas_barriere"])
        self.assertIn("minecraft:barrier", F["station_reset"])
        self.assertIn("replace minecraft:air", F["station_eau_40"])
        tout = "\n".join(F.values())
        self.assertNotIn("minecraft:iron_bars", tout)                                       # plus aucune grille vanilla dans les portes

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

    def test_les_valves_de_la_piscine_sont_des_blocs_3d(self):
        for k, p in INFOS["leviers"].items():
            self.assertEqual(BLOCS[p][0], "donjon:valve_station", p)
            self.assertNotIn(BLOCS[(p[0], p[1] - 1, p[2])][0], ("minecraft:air", "minecraft:water"), p)
            self.assertIn(f"if block {ds.txt(p)} donjon:valve_station[tournee=true] run function atelier:station_volant_{k}", F["station_tick"])
            self.assertIn(f"setblock {ds.txt(p)} donjon:valve_station[couleur=", F["station_reset"])

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

    def test_les_courants_sont_des_blocs_visibles_et_doux_pas_des_teleportations(self):
        self.assertFalse([ligne for ligne in F["station_tick"].splitlines() if " run tp @s ~" in ligne])        # plus de teleportation par tick : ca saccadait
        self.assertEqual(len(INFOS2["courants"]), 2)
        for c in INFOS2["courants"]:
            self.assertGreater(c["cases"], 100, c)
        coll = INFOS2["courants"][0]
        self.assertEqual((coll["sens"], coll["vitesse"]), ("west", 5))                                         # le collecteur de la Chaufferie : 0,5 bloc par tick vers l'ouest
        x0, x1, y0, y1, z0, z1 = coll["boite"]
        eau = [p for p, b in PARTS["station_b"].items() if x0 <= p[0] <= x1 and y0 <= p[1] <= y1 and z0 <= p[2] <= z1 and b[0] == "minecraft:water"]
        self.assertFalse(eau)                                                                                  # toute l'eau du collecteur est dans un bloc courant
        for p, b in PARTS["station_b"].items():
            if b[0] == "donjon:courant_station":
                self.assertEqual(dict(b[1])["waterlogged"], "true")

    def test_les_echelles_tiennent(self):
        dos = {"north": (0, 0, 1), "south": (0, 0, -1), "east": (-1, 0, 0), "west": (1, 0, 0)}
        for p, b in TOUT.items():
            if b[0] == "minecraft:ladder":
                d = dos[dict(b[1])["facing"]]
                q = (p[0] + d[0], p[1], p[2] + d[2])
                appui = TOUT.get(q, ("minecraft:stone", ()))[0]
                self.assertNotIn(appui, ("minecraft:air", "minecraft:water", "minecraft:ladder"), (p, q))

    def test_chaque_pompe_a_sa_valve_3d_au_bon_endroit(self):
        for cle, sect in (("pA", "a"), ("pB", "b"), ("pC", "c")):
            p = INFOS2[sect]["volant_pos"]
            self.assertEqual(TOUT[p][0], "donjon:valve_station", (cle, p))                  # une valve posee dans la roche ne se voit pas (bug du 18/11)
            self.assertNotIn(TOUT.get((p[0], p[1] - 1, p[2]), ("minecraft:water",))[0], ("minecraft:water", "minecraft:air"), (cle, p))
            self.assertIn(f"if block {ds.txt(p)} donjon:valve_station[tournee=true] run function atelier:station_pompe_{cle[1].lower()}", F["station_tick"])
            self.assertIn(f"setblock {ds.txt(p)} donjon:valve_station[couleur=", F["station_reset"])
        self.assertNotIn("InUse", "\n".join(F.values()))                                    # plus aucun volant Create

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


class TestVannesEtBarrages(unittest.TestCase):
    """Plus aucune porte en grille de bloc vanilla : les vannes et les barrages sont des blocs du mod (donjon:vanne_station, donjon:planche_station)."""

    def test_les_portes_ne_sont_plus_des_barreaux(self):
        gardes_corps = {p for p, b in TOUT.items() if b[0] == "minecraft:iron_bars"}
        portes = set(INFOS["bouches"]["A"] + INFOS["bouches"]["B"] + INFOS["bouches"]["C"] + INFOS["bouches"]["D"] + INFOS["vanne_sas"])
        for nom in ("A", "B", "C"):
            portes |= set(INFOS2["ecluses"][nom]["interne"])
        portes |= set(INFOS2["b"]["vanne_colonne"] + INFOS2["b"]["vanne_sortie"] + INFOS2["c"]["grille_salle"] + INFOS2["d"]["vanne_d"] + INFOS2["d"]["porte_poste"] + INFOS2["r"]["exutoire"])
        for cases in INFOS2["b"]["jets"].values():
            portes |= set(cases)
        self.assertFalse(portes & gardes_corps)
        for p in portes - set(INFOS["vanne_sas"]):
            self.assertEqual(TOUT[p][0], "donjon:vanne_station", p)

    def test_les_vannes_sont_dans_le_bon_plan(self):
        for p in INFOS2["ecluses"]["A"]["interne"]:
            self.assertEqual(dict(TOUT[p][1])["axis"], "z")                               # x constant : le plan est le long de z
        for p in INFOS2["ecluses"]["C"]["interne"]:
            self.assertEqual(dict(TOUT[p][1])["axis"], "x")
        for p in INFOS2["b"]["vanne_colonne"]:
            self.assertEqual(dict(TOUT[p][1])["axis"], "y")                               # la grille de la colonne de bulles est a plat
        for p in INFOS2["r"]["exutoire"]:
            self.assertEqual(dict(TOUT[p][1])["axis"], "x")

    def test_la_remise_a_zero_repose_les_vannes_du_mod(self):
        reset = "\n".join(F.values())
        self.assertIn("donjon:vanne_station[axis=", reset)
        for p in INFOS2["ecluses"]["A"]["interne"][:3]:
            self.assertIn(f"setblock {ds.txt(p)} donjon:vanne_station[axis=z,waterlogged=true]", F["station_ecluses_fermer"])

    def test_la_salle_des_lits_est_un_slalom_ferme_par_des_planches(self):
        lits = INFOS2["salle_lits"]
        self.assertGreater(len(lits["planches"]), 300)
        ax0, ax1, az0, az1, ay0, ay1 = INFOS2["a"]["a3"]
        for (x0, x1, z0, z1) in s2.LITS:                                                   # les lits montent jusqu'au plafond : on ne passe plus par dessus
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    self.assertNotIn(PARTS["station_a"][(x, ay1, z)][0], ("minecraft:air", "minecraft:water"), (x, z))
        for p, axe in lits["planches"]:
            self.assertEqual(PARTS["station_a"][p][0], "donjon:planche_station")
        for (x, z0, z1) in __import__("station_salle_lits").BARRAGES:                       # un barrage dans chaque passage du slalom
            for z in range(z0, z1 + 1):
                self.assertEqual(PARTS["station_a"][(x, 65, z)][0], "donjon:planche_station", (x, z))
        for nom in ("gardiens_a3", "noyes_a3"):
            for p in INFOS2["a"][nom]:
                self.assertNotEqual(PARTS["station_a"].get(p, ("",))[0], "donjon:planche_station", p)

    def test_la_salle_du_scaphandrier_a_quatre_cristaux_et_quatre_colonnes(self):
        C = INFOS2["c"]
        self.assertEqual(len(set(C["rotor_cristaux"])), 4)
        for p in C["rotor_cristaux"]:
            self.assertEqual(TOUT[(p[0], p[1] - 1, p[2])][0], "minecraft:waxed_copper_grate", p)       # le cristal repose sur la grille du sol
            self.assertEqual(TOUT.get(p, ("minecraft:water",))[0], "minecraft:water", p)
        for (px, pz) in C["colonnes_rotor"]:
            self.assertEqual(PARTS["station_c"][(px, 70, pz)][0], "create:copper_casing")
        rot = F["station_rotor_monstres"]
        self.assertIn("minecraft:resistance", rot)                                                    # protege des l'apparition
        self.assertEqual(rot.count("summon donjon:cristal_marin"), 4)
        self.assertIn("minecraft:resistance", F["station_rotor_remplacer"])
        self.assertIn("unless entity @e[tag=station_rotor_cristal] run function atelier:station_rotor_vulnerable", F["station_tick"])
        self.assertIn("effect clear @e[tag=station_rotor] minecraft:resistance", F["station_rotor_vulnerable"])
        self.assertIn("kill @e[tag=station_rotor_cristal]", F["station_reset"])
        self.assertIn("scoreboard players set #rvn station 0", F["station_reset"])
        self.assertFalse([n for n in F if n.startswith("station_rotor_valve")])

    def test_les_turbines_sont_refaites_sans_engrenage_a_foncer_ni_bouchon_a_briser(self):
        C = INFOS2["c"]
        tout = "\n".join(F.values())
        self.assertNotIn("station_c_turbine", tout)
        self.assertNotIn("station_c_tartre", tout)
        self.assertNotIn("dripstone_block", "\n".join(F[n] for n in F if n.startswith("station_c") or n == "station_reset" and False))
        for k in (1, 2):                                                                    # les trous des grilles sont des trappes du mod, a plat
            for p in C["tartres"][k]:
                self.assertEqual(PARTS["station_c"][p][0], "donjon:vanne_station", p)
                self.assertEqual(dict(PARTS["station_c"][p][1])["axis"], "y", p)
        for v in C["valves1"]:
            self.assertEqual(PARTS["station_c"][v][0], "donjon:valve_station")
            self.assertNotIn(TOUT.get((v[0], v[1] - 1, v[2]), ("minecraft:water",))[0], ("minecraft:water", "minecraft:air"), v)
        self.assertIn("run function atelier:station_c_trappe_1", F["station_c_v1a"])
        self.assertIn("run function atelier:station_c_trappe_1", F["station_c_v1b"])
        self.assertIn("tournee=false", F["station_c_v1_retombe"])
        self.assertIn("station_c_trappe_2", F["station_c_boss_mort"])
        for p in C["moteurs"].values():
            self.assertEqual(PARTS["station_c"][p][0], "create:creative_motor", p)           # les turbines tournent des le depart

    def test_les_plaques_lestees_sont_le_bloc_plaque_du_mod(self):
        for k, cases in enumerate(INFOS2["b"]["plaques"], start=1):
            for (x, y, z) in cases:
                self.assertEqual(PARTS["station_b"][(x, y - 1, z)][0], "donjon:plaque_station", (x, y, z))
            self.assertIn("donjon:plaque_station[active=true]", F[f"station_b_plaque_{k}"])
            self.assertIn("donjon:plaque_station[active=false]", F["station_reset"])

    def test_aucun_cristal_n_est_pris_dans_la_roche(self):
        libre = ("minecraft:water", "minecraft:air", "donjon:courant_station", "minecraft:light")
        for secteur, cle in (("r", "cristaux"), ("c", "rotor_cristaux")):
            for (x, y, z) in INFOS2[secteur][cle]:
                for a in (-1, 0, 1):
                    for b in (0, 1):
                        for c in (-1, 0, 1):                                                # le cristal fait 2 blocs de large et de haut : tout autour doit etre de l'eau
                            q = (x + a, y + b, z + c)
                            self.assertIn(TOUT.get(q, ("roche",))[0], libre, (secteur, (x, y, z), q))

    def test_apres_le_boss_une_cinematique_montre_la_porte_de_sortie_qui_s_ouvre(self):
        vaincu = F["station_boss_vaincu"]
        self.assertIn("schedule function atelier:station_sortie_scene 60t", vaincu)
        self.assertNotIn("minecraft:water", vaincu)                                              # la porte ne s'ouvre plus avant la cinematique
        self.assertIn("function atelier:station_cine_debut_sortie", F["station_sortie_scene"])
        self.assertEqual(F["station_cine_sortie"].count("function atelier:station_sortie_ouvrir\n"), 1)
        for p in INFOS2["r"]["exutoire"]:
            self.assertIn(f"setblock {ds.txt(p)} minecraft:water", F["station_sortie_ouvrir"])
        self.assertIn("schedule clear atelier:station_sortie_scene", F["station_reset"])
