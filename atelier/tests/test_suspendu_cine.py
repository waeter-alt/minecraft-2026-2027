# [claude-build]
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import donjon_suspendu as dj  # noqa: E402
import suspendu_cine as cine  # noqa: E402
import suspendu_salle1 as salle1  # noqa: E402
import suspendu_ss1_blocs as ss1  # noqa: E402


class TestChemin(unittest.TestCase):
    def test_le_chemin_passe_par_ses_cles_et_part_et_finit_a_l_arret(self):
        cles = [(1, (0, 0, 0)), (30, (10, 5, 0)), (110, (10, 5, 20))]
        f = cine.chemin(cles)
        for t, p in cles:
            for a, b in zip(f(t), p):
                self.assertAlmostEqual(a, b, places=6)
        self.assertLess(abs(f(2)[0] - f(1)[0]), 0.1)                         # depart en douceur

    def test_les_angles(self):
        self.assertAlmostEqual(cine.angles((0, 0, 0), (0, 0, 10))[0], 0)                  # vers le sud : yaw 0
        self.assertAlmostEqual(cine.angles((0, 0, 0), (-10, 0, 0))[0], 90)                # vers l'ouest : 90
        self.assertAlmostEqual(cine.angles((0, 0, 0), (10, 0, 0))[0], -90)                # vers l'est : -90
        self.assertAlmostEqual(cine.angles((0, 0, 0), (0, -10, 10))[1], 45)               # regarder vers le bas : pitch positif
        self.assertLess(cine.angles((0, 0, 0), (0, 10, 10))[1], 0)


class TestScenes(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocs, cls.infos = salle1.construire()
        cls.scenes = {"coffre": cine.scene_coffre(cls.infos, salle1.Y0)}                    # les portes a cle n'ont plus de cinematique

    def test_la_camera_ne_tourne_pas_pendant_le_travelling(self):
        """Fluidite : le serveur n'envoie l'orientation d'une entite que par pas de 1,4 degre, une rotation lente se verrait par a-coups. La camera avance donc en ligne droite sans tourner."""
        for nom, (cam, vise) in self.scenes.items():
            im = cine.images(cam, vise)
            self.assertLess(max(i[3] for i in im) - min(i[3] for i in im), 0.01, nom)
            self.assertLess(max(i[4] for i in im) - min(i[4] for i in im), 0.01, nom)

    def test_la_camera_avance_toujours_dans_le_meme_sens_et_freine_aux_extremites(self):
        for nom, (cam, vise) in self.scenes.items():
            im = cine.images(cam, vise)
            cible = vise[0][1]
            distances = [((i[0] - cible[0]) ** 2 + (i[1] - cible[1]) ** 2 + (i[2] - cible[2]) ** 2) ** 0.5 for i in im]
            for a, b in zip(distances, distances[1:]):
                self.assertLessEqual(b, a + 1e-9, nom)                                                            # on se rapproche, sans jamais reculer
            pas = [a - b for a, b in zip(distances, distances[1:])]
            self.assertLess(pas[0], max(pas) * 0.5, nom)                                                          # depart en douceur
            self.assertLess(pas[-1], max(pas) * 0.5, nom)                                                         # arrivee en douceur

    def test_les_images_sont_fluides(self):
        for nom, (cam, vise) in self.scenes.items():
            im = cine.images(cam, vise)
            self.assertEqual(len(im), cine.DUREE, nom)
            for a, b in zip(im, im[1:]):
                self.assertLess(((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5, 0.35, nom)          # la camera ne saute jamais
                self.assertLess(abs(a[3] - b[3]), 15, (nom, a[3], b[3]))                                                  # ni de saut d'angle (le passage 180 / -180 fait tourner la vue)
                self.assertLess(abs(a[4] - b[4]), 10, nom)

    def test_la_camera_reste_dans_la_salle_au_dessus_du_sol(self):
        xs = [c[0] for c in self.infos["salle"]]
        zs = [c[1] for c in self.infos["salle"]]
        for nom, (cam, vise) in self.scenes.items():
            for (x, y, z, yaw, pitch) in cine.images(cam, vise):
                self.assertTrue(min(xs) - 1 <= x <= max(xs) + 2 and min(zs) - 1 <= z <= max(zs) + 2, (nom, x, z))
                self.assertGreater(y, salle1.Y0 + 2.5, nom)
                self.assertLess(y, salle1.Y0 + salle1.HAUT_SALLE, nom)

    def test_la_camera_regarde_le_coffre(self):
        cam, vise = self.scenes["coffre"]
        premier = cine.images(cam, vise)[0]
        yaw, pitch = cine.angles(premier[:3], vise[0][1])
        self.assertAlmostEqual(premier[3], yaw, places=3)


class TestFonctions(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        _, cls.infos = salle1.construire()
        _, cls.infos2 = ss1.construire()
        decalages = {"salle1": (0, 0, 0), "ss1": (0, 0, 0), "ss2": (0, 0, 0), "ss3": (0, 0, 0), "porte1": (0, 0, 0), "porte2": (0, 0, 0), "porte3": (0, 0, 0), "portem1": (0, 0, 0)}
        cls.f, _ = dj.fonctions(cls.infos, decalages, cls.infos2)

    def test_toutes_les_fonctions_appelees_existent(self):
        for nom, texte in self.f.items():
            for appelee in re.findall(r"function atelier:([a-z0-9_]+)", texte):
                self.assertIn(appelee, self.f, f"{nom} appelle {appelee}")

    def test_une_image_par_tick_et_les_evenements(self):
        for nom in ("coffre", "portem1"):
            texte = self.f[f"suspendu_cine_{nom}"]
            self.assertEqual(len(re.findall(r"tp @e\[type=minecraft:armor_stand,tag=cine_cam\] ", texte)), cine.DUREE, nom)
        self.assertIn("matches 25 run function atelier:suspendu_s1_coffre", self.f["suspendu_cine_coffre"])           # le coffre apparait en pleine scene
        self.assertIn(f"matches {cine.LEVER} run function atelier:suspendu_pm1_lever", self.f["suspendu_cine_portem1"])   # la porte du sous-sol 1 (sans cle) garde sa scene

    def test_la_dalle_glisse_au_lieu_de_disparaitre_par_rangs(self):
        for n in "123":
            lever, glisse, fin = (self.f[f"suspendu_p{n}_{x}"] for x in ("lever", "glisse", "fin"))
            blocs = self.infos["dalles"][n]
            self.assertGreater(len(blocs), 40, n)
            self.assertEqual(lever.count("summon minecraft:block_display"), len(blocs), n)                         # un bloc affiche par bloc de la dalle
            self.assertIn(f"tag=porte{n}_dalle", glisse)
            self.assertIn(f"interpolation_duration:{dj.DUREE_GLISSE}", glisse)                                    # un seul mouvement, interpole par le client
            self.assertIn(f"translation:[0.001f,{dj.HAUTEUR_GLISSE + 0.001:.3f}f,0.001f]", glisse)
            self.assertIn(f"schedule function atelier:suspendu_p{n}_glisse 2t", lever)                              # on laisse un tick au client pour afficher l'etat de depart
            self.assertIn(f"kill @e[type=minecraft:block_display,tag=porte{n}_dalle]", fin)
            self.assertIn(f"kill @e[type=minecraft:block_display,tag=porte{n}_dalle]", self.f["suspendu_reset"])    # et la remise a zero les retire
            self.assertFalse(any(nom.startswith(f"suspendu_p{n}_rang") for nom in self.f), n)                      # plus de rangs qui disparaissent un par un

    def test_les_joueurs_sont_toujours_rendus(self):
        self.assertIn("execute unless score #t cine matches 1.. as @a[tag=cine] run function atelier:suspendu_cine_retour_joueur", self.f["suspendu_tick"])        # rattrapage
        self.assertIn("suspendu_cine_fin", self.f["suspendu_reset"])
        retour = self.f["suspendu_cine_retour_joueur"]
        for attendu in ("spectate", "gamemode survival", "gamemode creative", "gamemode adventure", "tag @s remove cine"):
            self.assertIn(attendu, retour)
        self.assertIn("matches 112..", self.f["suspendu_cine_cadre"])                                       # la scene s'arrete d'elle-meme

    def test_la_cle_ouvre_la_porte_tout_de_suite_sans_cinematique(self):
        """Demande de Luca : plus de cinematique quand on ouvre une porte avec sa cle."""
        for n in "123":
            t = self.f[f"suspendu_ouvrir_p{n}"]
            self.assertIn(f"function atelier:suspendu_p{n}_lever", t)                                                # la dalle se leve tout de suite
            self.assertIn("clear @s minecraft:tripwire_hook", t)                                                      # la cle est consommee
            self.assertFalse([l for l in t.splitlines() if not l.startswith("#") and "cine" in l], n)                # aucune commande de cinematique
            self.assertNotIn(f"suspendu_cine_porte{n}", self.f)
            self.assertNotIn(f"suspendu_cine_debut_porte{n}", self.f)
            self.assertFalse(any(f"suspendu_cine_porte{n}" in texte for texte in self.f.values()), n)               # aucune fonction ne l'appelle plus
        m1 = self.f["suspendu_ouvrir_pm1"]                                                                            # la porte du sous-sol 1 s'ouvre sans cle, aux monstres vaincus : elle garde sa scene
        self.assertIn("execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_portem1", m1)
        self.assertIn("execute unless score #libre cine matches 0 run function atelier:suspendu_pm1_lever", m1)
        self.assertIn("suspendu_cine_debut_coffre", self.f["suspendu_s1_fin"])
        self.assertIn("setblock", self.f["suspendu_s1_coffre"])

    def jouer(self, pad, etat):
        """Simule la logique de l'enigme : un clic droit sur le bloc musical `pad` (1 a 5), si la melodie est finie et l'enigme pas resolue."""
        if etat["mt"] != 0 or etat["mok"] != 0:
            return

        def appelle(nom):
            for ligne in self.f[nom].splitlines()[1:]:
                m = re.search(r"if score #(mq|mp) suspendu matches (\d+) run function atelier:(\w+)", ligne)
                if m:
                    if etat[m.group(1)] == int(m.group(2)):
                        appelle(m.group(3))
                elif ligne.startswith("scoreboard players operation #mp suspendu = #mq suspendu"):
                    etat["mp"] = etat["mq"]                                                            # la copie de la serie, prise avant de jouer
                elif ligne.startswith("scoreboard players add #mq"):
                    etat["mq"] += 1
                elif ligne.startswith("scoreboard players set #mq"):
                    etat["mq"] = 0
                elif ligne.startswith("scoreboard players set #mok suspendu 1"):
                    etat["mok"] = 1
                    etat["coffre"] = etat.get("coffre", 0) + 1
        appelle(f"suspendu_mus_joue{pad}")

    def test_l_enigme_du_bloc_musical_se_joue_comme_prevu(self):
        """Demande de Luca : la cle jaune n'apparait que quand l'enigme du bloc musical est resolue (meme principe que les lampes du donjon 3 : on ecoute la melodie, on la rejoue)."""
        sequence = self.infos2["enigme"]["sequence"]
        notes = self.infos2["enigme"]["notes"]
        tick = self.f["suspendu_tick"]
        for i in range(5):
            self.assertRegex(tick, r"if block -?\d+ -?\d+ -?\d+ minecraft:note_block\[note=%d\] run function atelier:suspendu_mus_pad%d" % (notes[i], i + 1))
            self.assertIn(f"note={notes[i] - 1},", self.f[f"suspendu_mus_pad{i + 1}"])               # on remet la note posee (n - 1) apres le clic (qui l'a montee d'un cran)
        self.assertIn("matches 1.. run function atelier:suspendu_mus_cadre", tick)
        etat = {"mt": 0, "mq": 0, "mok": 0}                                                            # la bonne serie fait apparaitre le coffre, une seule fois
        for k in sequence:
            self.assertEqual(etat["mok"], 0)
            self.jouer(k, etat)
        self.assertEqual((etat["mok"], etat.get("coffre")), (1, 1))
        self.jouer(sequence[0], etat)                                                                  # apres la reussite, plus rien ne bouge
        self.assertEqual(etat.get("coffre"), 1)
        etat = {"mt": 0, "mq": 0, "mok": 0}                                                            # une erreur remet la serie a zero
        for k in sequence[:3]:
            self.jouer(k, etat)
        self.assertEqual(etat["mq"], 3)
        self.jouer(next(k for k in range(1, 6) if k != sequence[3]), etat)
        self.assertEqual((etat["mq"], etat["mok"]), (0, 0))
        for k in sequence:                                                                             # et on peut recommencer
            self.jouer(k, etat)
        self.assertEqual(etat["mok"], 1)
        etat = {"mt": 30, "mq": 0, "mok": 0}                                                           # pendant la melodie, les clics ne comptent pas
        self.jouer(sequence[0], etat)
        self.assertEqual(etat["mq"], 0)

    def test_la_cle_jaune_n_est_posee_qu_a_la_reussite_de_l_enigme(self):
        for nom, texte in self.f.items():
            for ligne in texte.splitlines():
                if "minecraft:chest" in ligne and "Clé jaune" in ligne:
                    self.assertEqual(nom, "suspendu_mus_fin", nom)                                     # le coffre qui contient la cle jaune n'est cree que par la reussite
        fin = self.f["suspendu_mus_fin"]
        self.assertTrue(any("minecraft:chest" in l and "Clé jaune" in l for l in fin.splitlines()))
        self.assertIn("#mok suspendu 0", self.f["suspendu_reset"])

    def test_la_melodie_allume_les_lampes_dans_l_ordre_avec_la_bonne_note(self):
        e = self.infos2["enigme"]
        cadre = self.f["suspendu_mus_cadre"]
        for rang, k in enumerate(e["sequence"]):
            i = k - 1
            t_on = 20 + 25 * rang
            self.assertIn(f"matches {t_on} run setblock", cadre)
            self.assertIn(f"matches {t_on + 15} run setblock", cadre)
            ligne = next(l for l in cadre.splitlines() if f"matches {t_on} positioned" in l)
            self.assertIn(f"block.note_block.{e['instruments'][i]} ", ligne)
            self.assertIn(f"{2 ** ((e['notes'][i] - 12) / 12):.4f}", ligne)                              # la note qu'on entendra en frappant ce bloc
        self.assertIn(f"matches {20 + 25 * len(e['sequence']) + 10} run scoreboard players set #mt suspendu 0", cadre)

    def test_la_cinematique_montre_le_coffre_de_l_estrade_a_la_mort_du_mini_boss(self):
        """Demande de Luca : quand le mini-boss est battu, une animation montre que le coffre est apparu."""
        fin = self.f["suspendu_ss1_boss_fin"].splitlines()
        memo = fin.index("scoreboard players operation #libre cine = #t cine")
        lance = next(i for i, l in enumerate(fin) if "suspendu_cine_debut_boss" in l)
        self.assertLess(memo, lance)                                                                          # on retient si une scene est deja en cours avant de lancer la notre
        self.assertTrue(any("unless score #libre cine matches 0" in l and "suspendu_ss1_boss_coffre" in l for l in fin))      # sinon le coffre apparait tout de suite
        scene = self.f["suspendu_cine_boss"]
        self.assertEqual(len(re.findall(r"tp @e\[type=minecraft:armor_stand,tag=cine_cam\] ", scene)), cine.DUREE)             # une image par tick
        self.assertIn("matches 25 run function atelier:suspendu_ss1_boss_coffre", scene)                      # le coffre apparait en pleine scene, comme celui du rez-de-chaussee
        self.assertIn("Le coffre du grappin est apparu", scene)
        debut = self.f["suspendu_cine_debut_boss"]
        self.assertIn("scoreboard players set #id cine 40", debut)
        self.assertIn("distance=..70", debut)                                                                  # ceux qui se battent dans la grande salle, partout, voient le coffre
        self.assertIn("matches 40 run function atelier:suspendu_cine_boss", self.f["suspendu_cine_cadre"])
        cam, vise = cine.scene_coffre_generique(self.infos2["coffre_grappin"])
        im = cine.images(cam, vise)
        self.assertLess(max(i[3] for i in im) - min(i[3] for i in im), 0.01)                                  # la camera ne tourne pas : fluide
        self.assertLess(abs(im[0][3]), 45)                                                                    # elle regarde vers le sud (loin de +-180)
        for a, b in zip(im, im[1:]):
            self.assertLess(((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5, 0.35)

    def test_le_coffre_du_grappin_n_apparait_qu_apres_le_mini_boss(self):
        """Demande de Luca : le coffre de l'estrade n'apparait qu'une fois le mini-boss vaincu."""
        tick, debut, fin, reset = (self.f[n] for n in ("suspendu_tick", "suspendu_ss1_boss_debut", "suspendu_ss1_boss_fin", "suspendu_reset"))
        self.assertIn("suspendu_ss1_boss_debut", tick)
        self.assertIn("matches 1 unless entity @e[tag=suspendu_ss1m] run function atelier:suspendu_ss1_boss_fin", tick)     # la mort du mini-boss declenche le coffre
        self.assertIn("summon minecraft:vindicator", debut)
        self.assertIn("generic.max_health base set 60", debut)
        coffre = self.f["suspendu_ss1_boss_coffre"]
        self.assertIn("grappin:grappin", coffre)                                                                               # le coffre du grappin est pose a sa mort (par la fonction que la cinematique appelle)...
        self.assertIn("minecraft:chest", coffre)
        self.assertNotIn("grappin:grappin", fin)
        self.assertNotIn("grappin:grappin", reset)                                                                              # ... et jamais a la remise a zero
        self.assertIn("kill @e[tag=suspendu_ss1m]", reset)
        self.assertIn("#s4 suspendu 0", reset)
        for nom, texte in self.f.items():
            if nom not in ("suspendu_ss1_boss_coffre",):
                self.assertNotIn("grappin:grappin", texte, nom)

    def test_le_coffre_n_est_pose_qu_une_fois_et_garde_sa_cle(self):
        """Bug vu en jeu : tester `#t cine` APRES avoir lance la scene (qui met #t a 1) posait le coffre deux fois ; le second setblock vide le coffre (setblock le vide, puis refuse de remplacer un bloc par le meme)."""
        for nom, direct in (("suspendu_s1_fin", "suspendu_s1_coffre"), ("suspendu_ouvrir_pm1", "suspendu_pm1_lever")):
            lignes = self.f[nom].splitlines()
            memo = lignes.index("scoreboard players operation #libre cine = #t cine")
            lance = next(i for i, l in enumerate(lignes) if "suspendu_cine_debut_" in l)
            self.assertLess(memo, lance, nom)                                                                         # on retient l'etat avant de lancer la scene
            self.assertTrue(all("#t cine" not in l for l in lignes[lance:] if "function" in l), nom)                    # et on ne reteste jamais #t apres
            self.assertTrue(any("unless score #libre cine matches 0" in l and direct in l for l in lignes), nom)
        coffre = self.f["suspendu_s1_coffre"].splitlines()
        i = next(k for k, l in enumerate(coffre) if "minecraft:chest" in l)
        self.assertTrue(coffre[i - 1].endswith("minecraft:air"))                                                    # on repart d'une case vide
        self.assertIn("tripwire_hook", coffre[i])                                                                   # avec la cle dedans


# les particules de Minecraft 1.21.1 (fichiers assets/minecraft/particles/*.json du jeu, NeoForge 21.1.216) : un nom faux fait rejeter TOUTE la fonction au chargement
PARTICULES_1_21_1 = {"ambient_entity_effect", "angry_villager", "ash", "bubble", "bubble_column_up", "bubble_pop", "campfire_cosy_smoke", "campfire_signal_smoke", "cherry_leaves", "cloud", "composter", "crimson_spore", "crit", "current_down", "damage_indicator", "dolphin", "dragon_breath", "dripping_dripstone_lava", "dripping_dripstone_water", "dripping_honey", "dripping_lava", "dripping_obsidian_tear", "dripping_water", "dust", "dust_color_transition", "dust_plume", "effect", "egg_crack", "electric_spark", "enchant", "enchanted_hit", "end_rod", "entity_effect", "explosion", "falling_dripstone_lava", "falling_dripstone_water", "falling_dust", "falling_honey", "falling_lava", "falling_nectar", "falling_obsidian_tear", "falling_spore_blossom", "falling_water", "firework", "fishing", "flame", "flash", "glow", "glow_squid_ink", "gust", "happy_villager", "heart", "infested", "instant_effect", "landing_honey", "landing_lava", "landing_obsidian_tear", "large_smoke", "lava", "mycelium", "nautilus", "note", "ominous_spawning", "poof", "portal", "raid_omen", "rain", "reverse_portal", "scrape", "sculk_charge", "sculk_charge_pop", "sculk_soul", "shriek", "small_flame", "small_gust", "smoke", "sneeze", "snowflake", "sonic_boom", "soul", "soul_fire_flame", "spit", "splash", "spore_blossom_air", "squid_ink", "sweep_attack", "totem_of_undying", "trial_omen", "trial_spawner_detection", "trial_spawner_detection_ominous", "underwater", "vault_connection", "vibration", "warped_spore", "wax_off", "wax_on", "white_ash", "white_smoke", "witch"}


class TestParticulesValides(unittest.TestCase):
    def test_toutes_les_particules_des_fonctions_existent_dans_le_jeu(self):
        import re
        import donjon_suspendu as dj
        import suspendu_salle1 as rdc
        import suspendu_ss1_blocs as s1
        import suspendu_ss2_blocs as s2
        import suspendu_ss3_blocs as s3
        decalages = {n: (0, 0, 0) for n in ("salle1", "ss1", "ss2", "ss3", "porte1", "porte2", "porte3", "portem1")}
        f, _ = dj.fonctions(rdc.construire()[1], decalages, s1.construire()[1], s2.construire()[1], s3.construire()[1])
        vus = set()
        for nom, texte in f.items():
            for m in re.finditer(r"particle minecraft:([a-z_]+)", texte):
                vus.add(m.group(1))
                self.assertIn(m.group(1), PARTICULES_1_21_1, nom)
        self.assertIn("campfire_cosy_smoke", vus)


if __name__ == "__main__":
    unittest.main()
