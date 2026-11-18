# [claude-build]
"""
donjon_suspendu.py - le Donjon Suspendu, construit salle par salle (Luca decrit chaque salle, on l'ajoute, on la teste en jeu). Quatre niveaux : un rez-de-chaussee en surface et trois sous-sols
(des sous-sols tailles dans la pierre : le monde est un superflat PROFOND, 90 blocs de pierre sous la terre, que ce script cree lui-meme). Pour l'instant : la salle 1 (rez-de-chaussee).
Chaque salle est un module (suspendu_salle1.py...) qui renvoie ses blocs ; ce script les ecrit en structures, ecrit les fonctions de datapack (combat, coffre, portes a cle, pose, remise a zero)
et la pose. Une salle ajoutee plus tard s'ajoute a SALLES, sans toucher aux autres.
  test-local/venv/Scripts/python.exe atelier/scripts/donjon_suspendu.py [--monde "Donjon Suspendu"] [--zip]
En jeu : le monde est cree s'il n'existe pas, la pose se fait a la premiere ouverture. Remise a zero : /function atelier:suspendu_reset ; reposer : /scoreboard players set #suspendu atelier 0 puis /reload.
"""
import argparse
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import monde_atelier  # noqa: E402
import suspendu_cine  # noqa: E402
import suspendu_salle1 as salle1  # noqa: E402
import suspendu_ss1_blocs as ss1  # noqa: E402
import suspendu_ss2_blocs as ss2  # noqa: E402
import suspendu_ss2_fonctions  # noqa: E402
import suspendu_ss3_blocs as ss3  # noqa: E402
import suspendu_ss3_fonctions  # noqa: E402
from nbtlib import Compound, String  # noqa: E402
from suspendu_outils import ecrire_structure  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
SAVES = RACINE.parent / "mod/run/client/saves"
NOM_MONDE = "Donjon Suspendu"
# le superflat profond : bedrock a Y-64, 90 de pierre (Y-63 a 26), 2 de terre, herbe a Y 29 ; le sol du rez-de-chaussee est le bloc d'herbe (local Y0 = salle1.Y0)
COUCHES = [("minecraft:bedrock", 1), ("minecraft:stone", 90), ("minecraft:dirt", 2), ("minecraft:grass_block", 1)]
HERBE = -64 + 1 + 90 + 2                                  # Y monde du bloc d'herbe (29)
Y_SOL = salle1.Y0                                         # Y local du sol du rez-de-chaussee
ORIGINE = (-74, HERBE - Y_SOL, -86)                       # monde = local + ORIGINE ; l'entree de la salle 1 est donc en (0, 30, 0)
SX, SY, SZ = 336, 120, 208                                # gabarit reserve a tout le donjon (agrandi quand une salle ne tient plus)
def _objet(nom, poids, mini, maxi=None):
    e = {"type": "minecraft:item", "name": nom, "weight": poids}
    if maxi is not None:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": mini, "max": maxi}}]
    return e


# PROVISOIRE (Luca definira le vrai butin) : de quoi se soigner, tirer et se payer quelques ameliorations ; chaque coffre en tire 3 a 5
TABLE_BUTIN_TUNNEL = {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
    _objet("minecraft:cooked_beef", 10, 3, 6), _objet("minecraft:arrow", 10, 8, 20), _objet("minecraft:iron_ingot", 8, 2, 5), _objet("minecraft:gold_ingot", 5, 1, 3),
    _objet("minecraft:emerald", 4, 1, 3), _objet("minecraft:experience_bottle", 5, 2, 6), _objet("minecraft:golden_apple", 2, 1, 1), _objet("minecraft:torch", 4, 8, 16), _objet("minecraft:lapis_lazuli", 4, 4, 9)]}]}

SALLES = {"salle1": salle1, "ss1": ss1, "ss2": ss2, "ss3": ss3}
DUREE_GLISSE = 36                                          # ticks que met une dalle de porte a monter
HAUTEUR_GLISSE = 8.6                                       # de combien elle monte (toute sa hauteur, jusque dans le linteau)
NOMS_CLES = {"1": "Clé bleu", "2": "Clé jaune", "3": "Clé rouge"}      # la cle de chaque porte porte la couleur de la porte (le nom « Clé bleu » est celui voulu par Luca)


def monde(p):
    return tuple(ORIGINE[i] + p[i] for i in range(3))


def txt(p):
    return " ".join(str(v) for v in monde(p))


def milieu(cases, y):
    xs = [c[0] for c in cases]
    zs = [c[1] for c in cases]
    return (round(sum(xs) / len(xs)), y, round(sum(zs) / len(zs)))


def tuiles(x0, x1, y0, y1, z0, z1, bloc):
    """fill par tuiles de 31 x 31 x 31 (limite de 32 768 blocs par commande), en coordonnees locales."""
    cmds = []
    for a in range(x0, x1 + 1, 31):
        for b in range(y0, y1 + 1, 31):
            for c in range(z0, z1 + 1, 31):
                cmds.append(f"fill {txt((a, b, c))} {txt((min(a + 30, x1), min(b + 30, y1), min(c + 30, z1)))} {bloc}")
    return cmds


def fonctions_porte(f, nom, p, dalles, materiaux, cle_ouverture=None):
    """Les fonctions d'une porte de pierre : ouvrir (porte a cle : la cle est consommee, la dalle se leve tout de suite ; porte sans cle : cinematique), lever (la dalle devient des blocs affiches), glisser, fin. `p` : {'centre', 'a', 'b'} en coordonnees
    monde (texte), la zone etant celle ou la dalle se trouve ; `dalles` : [(x, y, z, bloc)] du gabarit ; `materiaux` : (bloc de la dalle, bloc de la spirale, nom de la couleur) ;
    `cle_ouverture` : l'objet-cle (texte) ou None si la porte s'ouvre autrement (les monstres vaincus)."""
    dalle_bloc, spirale_bloc, couleur = materiaux
    rotation = "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[0.998f,0.998f,0.998f]"
    invoques = [f"summon minecraft:block_display {txt((x, y, z))} {{block_state:{{Name:\"{bloc}\"}},Tags:[\"porte{nom}_dalle\"],transformation:{{{rotation},translation:[0.001f,0.001f,0.001f]}}}}"
                for (x, y, z, bloc) in dalles]
    zone = f"{p['a']} {p['b']}"
    if cle_ouverture:                                                   # porte a cle : plus de cinematique (demande de Luca), la dalle se leve tout de suite
        f[f"suspendu_ouvrir_p{nom}"] = "\n".join([
            f"# [claude-build] Ouvre la porte {nom} ({couleur}) avec sa cle : la cle est consommee et la dalle se leve tout de suite, sans cinematique.",
            f"clear @s {cle_ouverture} 1", f"scoreboard players set #p{nom} suspendu 1", f"function atelier:suspendu_p{nom}_lever"]) + "\n"
    else:
        f[f"suspendu_ouvrir_p{nom}"] = "\n".join([
            f"# [claude-build] Ouvre la porte {nom} ({couleur}) : une cinematique montre la porte qui se leve (ou elle se leve tout de suite si une cinematique est en cours).",
            f"scoreboard players set #p{nom} suspendu 1", "scoreboard players operation #libre cine = #t cine",
            f"execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_porte{nom}",
            f"execute unless score #libre cine matches 0 run function atelier:suspendu_p{nom}_lever"]) + "\n"
    f[f"suspendu_p{nom}_lever"] = "\n".join([
        f"# [claude-build] La dalle de la porte {nom} se leve : elle devient des blocs affiches (meme couleur, meme spirale) ; ils montent au tick suivant.",
        *invoques, f"fill {zone} minecraft:air replace {dalle_bloc}", f"fill {zone} minecraft:air replace {spirale_bloc}",
        f"playsound minecraft:block.piston.extend master @a {p['centre']} 3 0.4", f"schedule function atelier:suspendu_p{nom}_glisse 2t",
        f"tellraw @a {{\"text\":\"La porte {couleur} se lève.\",\"color\":\"gray\"}}"]) + "\n"
    f[f"suspendu_p{nom}_glisse"] = "\n".join([
        f"# [claude-build] La dalle de la porte {nom} monte de {HAUTEUR_GLISSE} blocs en {DUREE_GLISSE} ticks (le client interpole : mouvement continu).",
        f"execute as @e[type=minecraft:block_display,tag=porte{nom}_dalle] run data merge entity @s {{start_interpolation:0,interpolation_duration:{DUREE_GLISSE},"
        f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[0.998f,0.998f,0.998f],translation:[0.001f,{HAUTEUR_GLISSE + 0.001:.3f}f,0.001f]}}}}",
        f"playsound minecraft:block.grindstone.use master @a {p['centre']} 2 0.5", f"schedule function atelier:suspendu_p{nom}_fin {DUREE_GLISSE + 2}t"]) + "\n"
    f[f"suspendu_p{nom}_fin"] = "\n".join([
        f"# [claude-build] La dalle de la porte {nom} a disparu dans le linteau : les blocs affiches sont retires.",
        f"kill @e[type=minecraft:block_display,tag=porte{nom}_dalle]", f"playsound minecraft:block.anvil.land master @a {p['centre']} 2 0.5"]) + "\n"


def fonctions(infos, decalages, infos2, infos3=None, infos4=None):
    """Texte des fonctions de datapack : {nom: texte}. `infos` : le rez-de-chaussee, `infos2` : le sous-sol 1."""
    f = {}
    coffre = txt(infos["coffre"])
    centre = txt(infos["centre"])
    portes = {}
    for nom, cases in infos["cases_portes"].items():
        xs = [c[0] for c in cases]
        zs = [c[1] for c in cases]
        portes[nom] = {"centre": txt(milieu(cases, Y_SOL + 3)), "a": txt((min(xs), Y_SOL + 1, min(zs))), "b": txt((max(xs), Y_SOL + salle1.HAUT_PORTE, max(zs))), "rayon": 8 if nom == "3" else 5}
    cases2 = infos2["cases_porte"]
    porte_m1 = {"centre": txt(milieu(cases2, ss1.YF + 3)), "a": txt((min(c[0] for c in cases2), ss1.YF + 1, min(c[1] for c in cases2))),
                "b": txt((max(c[0] for c in cases2), ss1.YF + salle1.HAUT_PORTE, max(c[1] for c in cases2)))}
    centre_petite, centre_musicale, centre_boss = txt(infos2["centre_petite"]), txt(infos2["centre_musicale"]), txt(infos2["mini_boss"])
    cle = lambda n: f"minecraft:tripwire_hook[minecraft:custom_name='\"{NOMS_CLES[n]}\"']"
    f["suspendu_charger"] = ("# [claude-build] A chaque chargement : les objectifs de scores du donjon et des cinematiques, la regle des monstres.\nscoreboard objectives add suspendu dummy\n"
                             "scoreboard objectives add cine dummy\nscoreboard objectives add cine_mode dummy\nexecute unless score #t cine matches 0.. run scoreboard players set #t cine 0\ngamerule doMobSpawning false\n")
    lignes = ["# [claude-build] Chaque tick : les cinematiques, le combat de la salle 1 et les portes a cle.",
              "execute if score #t cine matches 1.. run function atelier:suspendu_cine_cadre",
              "execute unless score #t cine matches 1.. as @a[tag=cine] run function atelier:suspendu_cine_retour_joueur",              # rattrapage : jamais de joueur reste en spectateur
              f"execute if score #s1 suspendu matches 0 positioned {centre} if entity @a[gamemode=!spectator,distance=..{salle1.RAYON_COMBAT}] run function atelier:suspendu_s1_debut",
              "execute if score #s1 suspendu matches 1 unless entity @e[type=minecraft:zombie,tag=suspendu_s1] run function atelier:suspendu_s1_fin"]
    for nom, p in portes.items():
        lignes.append(f"execute if score #p{nom} suspendu matches 0 positioned {p['centre']} as @a[gamemode=!spectator,distance=..{p['rayon']}] if items entity @s weapon.mainhand {cle(nom)} run function atelier:suspendu_ouvrir_p{nom}")
    lignes += [f"execute if score #s2 suspendu matches 0 positioned {centre_petite} if entity @a[gamemode=!spectator,distance=..9] run function atelier:suspendu_ss1_petite_debut",
               "execute if score #s2 suspendu matches 1 unless entity @e[tag=suspendu_ss1a] run function atelier:suspendu_ss1_petite_fin",
               f"execute if score #s3 suspendu matches 0 positioned {centre_musicale} if entity @a[gamemode=!spectator,distance=..14] run function atelier:suspendu_ss1_musicale_debut",
               f"execute if score #s4 suspendu matches 0 positioned {centre_boss} if entity @a[gamemode=!spectator,distance=..22] run function atelier:suspendu_ss1_boss_debut",
               "execute if score #s4 suspendu matches 1 unless entity @e[tag=suspendu_ss1m] run function atelier:suspendu_ss1_boss_fin"]
    f["suspendu_tick"] = "\n".join(lignes) + "\n"
    zombies = [f"summon minecraft:zombie {txt(z)} {{Tags:[\"suspendu_s1\"],PersistenceRequired:1b}}" for z in infos["zombies"]]
    f["suspendu_s1_debut"] = "\n".join([
        "# [claude-build] Un joueur est arrive au centre de la salle 1 : trois zombies apparaissent (une seule fois).",
        "scoreboard players set #s1 suspendu 1", *zombies,
        f"execute positioned {centre} run title @a[distance=..40] times 10 50 20",
        f"execute positioned {centre} run title @a[distance=..40] subtitle {{\"text\":\"Le coffre apparaîtra ensuite\",\"color\":\"gray\"}}",
        f"execute positioned {centre} run title @a[distance=..40] title {{\"text\":\"Vainquez les 3 zombies\",\"color\":\"red\"}}",
        f"execute positioned {centre} run playsound minecraft:entity.zombie.ambient master @a[distance=..40] ~ ~ ~ 2 0.7"]) + "\n"
    item = ("{Items:[{Slot:13b,id:\"minecraft:tripwire_hook\",count:1,components:{\"minecraft:custom_name\":'\"" + NOMS_CLES["1"] + "\"',"
            "\"minecraft:lore\":['\"Ouvre la porte " + salle1.COULEURS_PORTES["1"][2] + "\"']}}]}")
    f["suspendu_s1_fin"] = "\n".join([
        "# [claude-build] Les trois zombies sont vaincus : une cinematique montre le coffre qui apparait (ou le coffre apparait tout de suite si une autre cinematique est en cours).",
        "scoreboard players set #s1 suspendu 2",
        "scoreboard players operation #libre cine = #t cine",          # on retient si une scene etait deja en cours AVANT de lancer la notre (lancer la notre met #t a 1)
        "execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_coffre",
        "execute unless score #libre cine matches 0 run function atelier:suspendu_s1_coffre"]) + "\n"
    f["suspendu_s1_coffre"] = "\n".join([
        "# [claude-build] Le coffre apparait au centre de la salle, avec la cle de la porte 1.",
        f"setblock {coffre} minecraft:air",                            # un coffre deja la serait vide par setblock (il le vide, puis refuse de le remplacer par le meme bloc) : on repart d'une case vide
        f"setblock {coffre} minecraft:chest[facing=south]{item}",
        f"execute positioned {coffre} run particle minecraft:happy_villager ~ ~0.5 ~ 0.4 0.4 0.4 0.1 30",
        f"execute positioned {coffre} run playsound minecraft:block.chest.open master @a[distance=..40] ~ ~ ~ 2 1",
        f"execute positioned {centre} run title @a[distance=..40] title {{\"text\":\"Un coffre est apparu\",\"color\":\"gold\"}}"]) + "\n"
    for nom, p in portes.items():
        fonctions_porte(f, nom, p, infos["dalles"][nom], (f"minecraft:{salle1.COULEURS_PORTES[nom][0]}", f"minecraft:{salle1.COULEURS_PORTES[nom][1]}", salle1.COULEURS_PORTES[nom][2]), cle(nom))
    fonctions_porte(f, "m1", porte_m1, infos2["dalles"], (f"minecraft:{salle1.COULEURS_PORTES['1'][0]}", f"minecraft:{salle1.COULEURS_PORTES['1'][1]}", salle1.COULEURS_PORTES["1"][2]), None)
    # le sous-sol 1 : les deux monstres de la petite salle gardent la porte (elle s'ouvre quand ils sont vaincus) ; deux autres veillent la salle de l'enigme ; deux coffres verts sont remplis
    invoquer = lambda tag, pos: [f"summon minecraft:zombie {txt(pos[0])} {{Tags:[\"{tag}\"],PersistenceRequired:1b}}", f"summon minecraft:skeleton {txt(pos[1])} {{Tags:[\"{tag}\"],PersistenceRequired:1b}}"]
    f["suspendu_ss1_petite_debut"] = "\n".join([
        "# [claude-build] Un joueur entre dans la petite salle : un zombie et un squelette apparaissent (une seule fois) ; la porte s'ouvrira quand ils seront vaincus.",
        "scoreboard players set #s2 suspendu 1", *invoquer("suspendu_ss1a", infos2["monstres_petite"]),
        f"execute positioned {centre_petite} run title @a[distance=..30] title {{\"text\":\"Vainquez les gardiens\",\"color\":\"red\"}}",
        f"execute positioned {centre_petite} run playsound minecraft:entity.zombie.ambient master @a[distance=..30] ~ ~ ~ 2 0.7"]) + "\n"
    f["suspendu_ss1_petite_fin"] = "\n".join([
        "# [claude-build] Les deux gardiens sont vaincus : la porte bleue de la petite salle s'ouvre.",
        "scoreboard players set #s2 suspendu 2", "function atelier:suspendu_ouvrir_pm1"]) + "\n"
    f["suspendu_ss1_musicale_debut"] = "\n".join([
        "# [claude-build] Un joueur entre dans la salle de l'enigme musicale : un zombie et un squelette apparaissent (une seule fois).",
        "scoreboard players set #s3 suspendu 1", *invoquer("suspendu_ss1b", infos2["monstres_musicale"])]) + "\n"
    coffre_grappin, coffre_cle = txt(infos2["coffre_grappin"]), txt(infos2["coffre_cle"])
    contenu_grappin = "{Items:[{Slot:13b,id:\"grappin:grappin\",count:1}]}"
    contenu_cle = ("{Items:[{Slot:13b,id:\"minecraft:tripwire_hook\",count:1,components:{\"minecraft:custom_name\":'\"" + NOMS_CLES["2"] + "\"',"
                   "\"minecraft:lore\":['\"Ouvre la porte " + salle1.COULEURS_PORTES["2"][2] + "\"']}}]}")
    # le mini-boss de la grande salle : le Rodeur des Racines (grappin:rodeur, mod grappin 3.4.0+), qui se reveille quand un joueur s'approche ; le coffre du grappin, sur l'estrade, n'apparait qu'une fois le mini-boss vaincu
    f["suspendu_ss1_boss_debut"] = "\n".join([
        "# [claude-build] Un joueur s'approche du mini-boss de la grande salle : le Rodeur des Racines sort de la mousse (une seule fois). Sa vie et ses degats se reglent dans la config du mod grappin (rodeur*).",
        "scoreboard players set #s4 suspendu 1",
        f"summon grappin:rodeur {centre_boss} {{Tags:[\"suspendu_ss1m\"],PersistenceRequired:1b,CustomName:'{{\"text\":\"Rôdeur des Racines\",\"color\":\"red\"}}',CustomNameVisible:1b}}",
        f"execute positioned {centre_boss} run title @a[distance=..60] times 10 50 20",
        f"execute positioned {centre_boss} run title @a[distance=..60] subtitle {{\"text\":\"Le coffre du grappin apparaîtra sur l'estrade\",\"color\":\"gray\"}}",
        f"execute positioned {centre_boss} run title @a[distance=..60] title {{\"text\":\"Vainquez le Rôdeur des Racines\",\"color\":\"red\"}}",
        f"execute positioned {centre_boss} run playsound minecraft:entity.ravager.roar master @a[distance=..60] ~ ~ ~ 2 1.4"]) + "\n"
    f["suspendu_ss1_boss_fin"] = "\n".join([
        "# [claude-build] Le mini-boss est vaincu : une cinematique montre le coffre du grappin qui apparait sur l'estrade (ou le coffre apparait tout de suite si une autre cinematique est en cours).",
        "scoreboard players set #s4 suspendu 2",
        "scoreboard players operation #libre cine = #t cine",          # on retient si une scene etait deja en cours AVANT de lancer la notre (lancer la notre met #t a 1)
        "execute if score #libre cine matches 0 run function atelier:suspendu_cine_debut_boss",
        "execute unless score #libre cine matches 0 run function atelier:suspendu_ss1_boss_coffre"]) + "\n"
    f["suspendu_ss1_boss_coffre"] = "\n".join([
        "# [claude-build] Le coffre du grappin apparait sur l'estrade (au milieu de la cinematique, ou tout de suite) ; on repart d'une case vide, setblock viderait un coffre deja la.",
        f"setblock {coffre_grappin} minecraft:air", f"setblock {coffre_grappin} minecraft:chest[facing=north]{contenu_grappin}",
        f"execute positioned {coffre_grappin} run particle minecraft:happy_villager ~ ~0.5 ~ 0.4 0.4 0.4 0.1 30",
        f"execute positioned {coffre_grappin} run particle minecraft:end_rod ~ ~1 ~ 0.3 0.6 0.3 0.05 25",
        f"execute positioned {coffre_grappin} run playsound minecraft:block.chest.open master @a[distance=..60] ~ ~ ~ 2 1",
        f"execute positioned {coffre_grappin} run title @a[distance=..60] title {{\"text\":\"Un coffre est apparu sur l'estrade\",\"color\":\"gold\"}}"]) + "\n"
    # l'enigme du bloc musical : le bouton de depart joue la melodie (les lampes s'allument dans l'ordre, avec la note de chaque bloc), puis on la rejoue en frappant (clic droit) les blocs musicaux dans le meme ordre ;
    # une erreur remet la serie a zero ; la reussite fait apparaitre le coffre de la cle jaune. Un clic droit sur un bloc musical ajoute 1 a sa note : on le detecte, on remet la note (setblock) et on compte le coup.
    enigme = infos2["enigme"]
    pads, lampes, sequence = enigme["pads"], enigme["lampes"], enigme["sequence"]
    notes, instruments, couleurs = enigme["notes"], enigme["instruments"], enigme["couleurs"]
    depart_e = txt(enigme["depart"])

    def pitch(note):
        return f"{2 ** ((note - 12) / 12):.4f}"

    def etat_pad(i):
        return f"minecraft:note_block[instrument={instruments[i]},note={notes[i] - 1},powered=false]"

    tick_enigme = ["execute if score #mt suspendu matches 1.. run function atelier:suspendu_mus_cadre",
                   f"execute if score #mt suspendu matches 0 if score #mok suspendu matches 0 if block {depart_e} minecraft:stone_button[powered=true] run function atelier:suspendu_mus_start"]
    for i in range(len(pads)):
        tick_enigme.append(f"execute if block {txt(pads[i])} minecraft:note_block[note={notes[i]}] run function atelier:suspendu_mus_pad{i + 1}")
    lignes_tick_enigme = tick_enigme
    f["suspendu_mus_start"] = "\n".join([
        "# [claude-build] Le bouton de depart est presse : la melodie va etre jouee (les lampes s'allument dans l'ordre), puis c'est au joueur de la rejouer.",
        "scoreboard players set #mt suspendu 1", "scoreboard players set #mq suspendu 0",
        f"execute positioned {depart_e} run title @a[distance=..30] actionbar {{\"text\":\"Écoute la mélodie, puis rejoue-la sur les blocs musicaux\",\"color\":\"gold\"}}",
        f"execute positioned {depart_e} run playsound minecraft:block.note_block.chime master @a[distance=..30] ~ ~ ~ 1 1.2"]) + "\n"
    cadre = ["# [claude-build] Chaque tick pendant la melodie : on allume une lampe tous les 25 ticks, avec sa note, et on l'eteint 15 ticks plus tard ; la melodie finie, le joueur peut jouer.",
             "scoreboard players add #mt suspendu 1"]
    for rang, k in enumerate(sequence):
        i = k - 1
        t_on = 20 + 25 * rang
        cadre += [f"execute if score #mt suspendu matches {t_on} run setblock {txt(lampes[i])} minecraft:sea_lantern",
                  f"execute if score #mt suspendu matches {t_on} positioned {txt(pads[i])} run playsound minecraft:block.note_block.{instruments[i]} master @a[distance=..30] ~ ~ ~ 2 {pitch(notes[i])}",
                  f"execute if score #mt suspendu matches {t_on + 15} run setblock {txt(lampes[i])} minecraft:{couleurs[i]}_concrete"]
    cadre.append(f"execute if score #mt suspendu matches {20 + 25 * len(sequence) + 10} run scoreboard players set #mt suspendu 0")
    f["suspendu_mus_cadre"] = "\n".join(cadre) + "\n"
    for i in range(len(pads)):
        f[f"suspendu_mus_pad{i + 1}"] = "\n".join([
            f"# [claude-build] Un clic droit sur le bloc musical {i + 1} : on remet sa note ; si la melodie est finie et l'enigme pas resolue, on compte le coup.",
            f"setblock {txt(pads[i])} {etat_pad(i)}",
            f"execute positioned {txt(pads[i])} run particle minecraft:note ~ ~1.2 ~ 0.2 0.2 0.2 1 3",
            f"execute if score #mt suspendu matches 0 if score #mok suspendu matches 0 run function atelier:suspendu_mus_joue{i + 1}"]) + "\n"
        f[f"suspendu_mus_joue{i + 1}"] = "\n".join(
            [f"# [claude-build] Le bloc musical {i + 1} est frappe : bon si c'est le suivant de la melodie, faux sinon. On teste une COPIE de la serie (#mp) : bon l'incremente, et les lignes suivantes ne doivent pas relire le nouveau score.",
             "scoreboard players operation #mp suspendu = #mq suspendu"] +
            [f"execute if score #mp suspendu matches {q} run function atelier:suspendu_mus_{'bon' if sequence[q] == i + 1 else 'faux'}" for q in range(len(sequence))]) + "\n"
    f["suspendu_mus_bon"] = "\n".join([
        "# [claude-build] Le bon bloc : un coup de plus ; au dernier, l'enigme est resolue.",
        "scoreboard players add #mq suspendu 1",
        f"execute if score #mq suspendu matches {len(sequence)} run function atelier:suspendu_mus_fin"]) + "\n"
    f["suspendu_mus_faux"] = "\n".join([
        "# [claude-build] Le mauvais bloc : la serie repart de zero (on peut relancer la melodie avec le bouton).",
        "scoreboard players set #mq suspendu 0",
        f"execute positioned {depart_e} run playsound minecraft:entity.villager.no master @a[distance=..30] ~ ~ ~ 1 0.9",
        f"execute positioned {depart_e} run title @a[distance=..30] actionbar {{\"text\":\"Faux : la série recommence\",\"color\":\"red\"}}"]) + "\n"
    f["suspendu_mus_fin"] = "\n".join([
        "# [claude-build] La melodie est rejouee en entier : l'enigme est resolue, toutes les lampes s'allument et le coffre de la cle jaune apparait (on repart d'une case vide, setblock viderait un coffre deja la).",
        "scoreboard players set #mok suspendu 1",
        *[f"setblock {txt(l)} minecraft:sea_lantern" for l in lampes],
        f"execute positioned {depart_e} run playsound minecraft:block.note_block.chime master @a[distance=..40] ~ ~ ~ 2 1.5",
        f"execute positioned {depart_e} run playsound minecraft:entity.player.levelup master @a[distance=..40] ~ ~ ~ 1 1",
        f"setblock {coffre_cle} minecraft:air", f"setblock {coffre_cle} minecraft:chest[facing=south]{contenu_cle}",
        f"execute positioned {coffre_cle} run particle minecraft:happy_villager ~ ~0.5 ~ 0.4 0.4 0.4 0.1 30",
        f"execute positioned {coffre_cle} run title @a[distance=..40] title {{\"text\":\"Un coffre est apparu au fond de la salle\",\"color\":\"gold\"}}"]) + "\n"
    reset_enigme = ["scoreboard players set #mt suspendu 0", "scoreboard players set #mq suspendu 0", "scoreboard players set #mok suspendu 0",
                    *[f"setblock {txt(lampes[i])} minecraft:{couleurs[i]}_concrete" for i in range(len(lampes))],
                    *[f"setblock {txt(pads[i])} {etat_pad(i)}" for i in range(len(pads))]]
    f["suspendu_tick"] = f["suspendu_tick"] + "\n".join(lignes_tick_enigme) + "\n"
    coffres_verts = [f"setblock {coffre_grappin} minecraft:air",                                           # le coffre du grappin n'existe pas avant la mort du mini-boss
                     f"setblock {coffre_cle} minecraft:air"]                                                              # la cle jaune n'apparait qu'a la reussite de l'enigme du bloc musical
    reset = ["# [claude-build] Remise a zero du donjon : cinematique arretee, monstres retires, coffres remis, portes refermees, scores a zero, cles retirees.",
             "scoreboard objectives add suspendu dummy", "scoreboard objectives add cine dummy", "scoreboard objectives add cine_mode dummy",
             "execute if score #t cine matches 1.. run function atelier:suspendu_cine_fin", "gamerule doMobSpawning false",
             *[f"scoreboard players set #{v} suspendu 0" for v in ("s1", "s2", "s3", "s4", "p1", "p2", "p3", "pm1")],
             "kill @e[type=minecraft:zombie,tag=suspendu_s1]", "kill @e[tag=suspendu_ss1a]", "kill @e[tag=suspendu_ss1b]", "kill @e[tag=suspendu_ss1m]", f"setblock {coffre} minecraft:air",
             *[f"schedule clear atelier:suspendu_p{n}_{suite}" for n in (*portes, "m1") for suite in ("glisse", "fin")],
             *[f"kill @e[type=minecraft:block_display,tag=porte{n}_dalle]" for n in (*portes, "m1")],
             *[f"place template atelier:suspendu_porte{n} {txt(decalages[f'porte{n}'])}" for n in (*portes, "m1")],
             *coffres_verts, *reset_enigme,
             *[f"clear @a {cle(n)}" for n in portes], 'tellraw @a {"text":"Donjon remis à zéro.","color":"gray"}']
    f["suspendu_reset"] = "\n".join(reset) + "\n"
    # la salle -2 : les leviers qui font apparaitre les ancrages, les ennemis par ile, le coffre de la cle 3 (avec sa cinematique, ajoutee plus bas aux autres)
    infos3 = infos3 or ss2.construire()[1]
    cle_rouge = "{\"minecraft:custom_name\":'\"" + NOMS_CLES["3"] + "\"',\"minecraft:lore\":['\"Ouvre la porte " + salle1.COULEURS_PORTES["3"][2] + "\"']}"
    f_ss2, tick_ss2, reset_ss2, perso_ss2 = suspendu_ss2_fonctions.fonctions(infos3, txt, cle_rouge)
    f.update(f_ss2)
    f["suspendu_tick"] = f["suspendu_tick"] + "\n".join(tick_ss2) + "\n"
    f["suspendu_reset"] = f["suspendu_reset"] + "\n".join(reset_ss2) + "\n"
    # la salle 3 : le combat contre le Gardien des Racines (la salle du boss de l'ancien donjon 1)
    infos4 = infos4 or ss3.construire()[1]
    f_ss3, tick_ss3, reset_ss3 = suspendu_ss3_fonctions.fonctions(infos4, txt)
    f.update(f_ss3)
    f["suspendu_tick"] = f["suspendu_tick"] + "\n".join(tick_ss3) + "\n"
    f["suspendu_reset"] = f["suspendu_reset"] + "\n".join(reset_ss3) + "\n"
    cx_porte, cz_porte = infos2["centre_porte"]
    scene_m1 = suspendu_cine.scene_porte_generique((cx_porte, cz_porte), (0.0, 1.0), ss1.YF, distance=8.5)       # on regarde la porte depuis la petite salle (elle est au sud de la porte)
    f.update(suspendu_cine.fonctions(infos, salle1, ORIGINE, centre, {**{n: c[2] for n, c in salle1.COULEURS_PORTES.items()}, "m1": salle1.COULEURS_PORTES["1"][2]},
                                     extras={"m1": (scene_m1, txt((round(cx_porte), ss1.YF + 3, round(cz_porte))))},
                                     coffres={"boss": (suspendu_cine.scene_coffre_generique(infos2["coffre_grappin"]), txt(infos2["coffre_grappin"]), "suspendu_ss1_boss_coffre", "Le coffre du grappin est apparu sur l'estrade")},
                                     perso=perso_ss2))
    moitie = SX // 2                                          # `forceload add` ne charge que 256 chunks a la fois : le gabarit se charge en deux moities
    plages = [(ORIGINE[0], ORIGINE[2], ORIGINE[0] + moitie - 1, ORIGINE[2] + SZ - 1), (ORIGINE[0] + moitie, ORIGINE[2], ORIGINE[0] + SX - 1, ORIGINE[2] + SZ - 1)]
    f["suspendu_pose"] = "\n".join([
        "# [claude-build] Pose le donjon (sans pause).", *[f"forceload add {a} {b} {c} {d}" for (a, b, c, d) in plages],
        'tellraw @a {"text":"Donjon Suspendu : 30 secondes de chargement, puis la pose par morceaux (environ une minute, le jeu peut ralentir). Ne mets pas en pause.","color":"gold"}',
        "schedule function atelier:suspendu_pose_2 600t"]) + "\n"
    haut_pierre = HERBE - 2 - ORIGINE[1] - 1                 # dernier Y local de pierre (82)
    terrain = [*tuiles(0, SX - 1, 0, haut_pierre, 0, SZ - 1, "minecraft:stone"), *tuiles(0, SX - 1, haut_pierre + 1, Y_SOL - 1, 0, SZ - 1, "minecraft:dirt"),
               *tuiles(0, SX - 1, Y_SOL, Y_SOL, 0, SZ - 1, "minecraft:grass_block"), *tuiles(0, SX - 1, Y_SOL + 1, SY - 1, 0, SZ - 1, "minecraft:air")]
    places = [f"place template atelier:suspendu_{nom} {txt(decalages[nom])}" for nom in SALLES]
    # la pose se fait par etapes, une toutes les 2 ticks (40 `fill` ou une salle a la fois) : le gabarit est devenu gros (le sous-sol 2 seul fait plus d'un million de blocs) et tout faire dans un seul tick figerait le jeu
    etapes = [terrain[i:i + 40] for i in range(0, len(terrain), 40)] + [[ligne] for ligne in places]
    for i, commandes in enumerate(etapes):
        suite = ([f"schedule function atelier:suspendu_pose_2_{i + 1} 2t"] if i + 1 < len(etapes)
                 else ["kill @e[type=item]", "function atelier:suspendu_reset", "schedule function atelier:suspendu_pose_3 100t"])
        f["suspendu_pose_2" if i == 0 else f"suspendu_pose_2_{i}"] = "\n".join([f"# [claude-build] Pose du donjon, etape {i + 1} sur {len(etapes)} (le terrain du gabarit : pierre, terre, herbe, air ; puis les salles ; puis la remise a zero).", *commandes, *suite]) + "\n"
    entree = txt(infos["entree"])
    f["suspendu_pose_3"] = "\n".join(["# [claude-build] Decharge le gabarit et annonce.", *[f"forceload remove {a} {b} {c} {d}" for (a, b, c, d) in plages],
                                      monde_atelier.message_entree("Le Donjon Suspendu est prêt.", entree)]) + "\n"
    return f, {**portes, "m1": porte_m1}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--monde", default=NOM_MONDE, help="nom du monde tel qu'il s'affiche en jeu (cree s'il n'existe pas)")
    ap.add_argument("--zip", action="store_true", help="ecrit aussi atelier/mondes/<monde>.zip, un monde pret a copier dans saves/")
    args = ap.parse_args()
    options = {"couches": COUCHES, "difficulte": 2, "apparition": (0, HERBE + 1, 6), "mode_jeu": 0}          # normal et survie : il y a des zombies a combattre
    save = monde_atelier.trouver_ou_creer_monde(args.monde, SAVES, **options)
    print(f"[0] monde « {args.monde} » -> dossier {save.name}", flush=True)
    exports = RACINE / "exports"
    dp = monde_atelier.datapack(save)
    blocs, infos = salle1.construire()
    decalages = {}
    decalages["salle1"] = ecrire_structure(blocs, exports / "suspendu_salle1.nbt")
    for nom in infos["cases_portes"]:                                     # les trois dalles seules (avec leur spirale), pour les refermer a la remise a zero
        noms_blocs = {"minecraft:" + m for m in salle1.COULEURS_PORTES[nom][:2]}
        dalles = {p: s for p, s in blocs.items() if s[0] in noms_blocs and (p[0], p[2]) in infos["cases_portes"][nom]}
        decalages[f"porte{nom}"] = ecrire_structure(dalles, exports / f"suspendu_porte{nom}.nbt")
    for fichier in ("suspendu_salle1", "suspendu_porte1", "suspendu_porte2", "suspendu_porte3"):
        (dp / "data/atelier/structure" / f"{fichier}.nbt").write_bytes((exports / f"{fichier}.nbt").read_bytes())
    blocs2, infos2 = ss1.construire()
    decalages["ss1"] = ecrire_structure(blocs2, exports / "suspendu_ss1.nbt")
    noms_m1 = {"minecraft:" + m for m in salle1.COULEURS_PORTES["1"][:2]}
    decalages["portem1"] = ecrire_structure({p: s for p, s in blocs2.items() if s[0] in noms_m1 and (p[0], p[2]) in infos2["cases_porte"]}, exports / "suspendu_portem1.nbt")
    for fichier in ("suspendu_ss1", "suspendu_portem1"):
        (dp / "data/atelier/structure" / f"{fichier}.nbt").write_bytes((exports / f"{fichier}.nbt").read_bytes())
    blocs3, infos3 = ss2.construire()
    decalages["ss2"] = ecrire_structure(blocs3, exports / "suspendu_ss2.nbt")
    (dp / "data/atelier/structure" / "suspendu_ss2.nbt").write_bytes((exports / "suspendu_ss2.nbt").read_bytes())
    blocs4, infos4 = ss3.construire()
    coffres_nbt = {c["pos"]: Compound({"id": String("minecraft:chest"), "LootTable": String(ss3.TABLE_BUTIN)}) for c in infos4["tunnel"]["coffres"]}         # les coffres du tunnel se remplissent a la premiere ouverture
    decalages["ss3"] = ecrire_structure(blocs4, exports / "suspendu_ss3.nbt", nbt=coffres_nbt)
    (dp / "data/atelier/structure" / "suspendu_ss3.nbt").write_bytes((exports / "suspendu_ss3.nbt").read_bytes())
    textes, portes = fonctions(infos, decalages, infos2, infos3, infos4)
    fn = dp / "data/atelier/function"
    for nom, texte in textes.items():
        (fn / f"{nom}.mcfunction").write_text(texte, encoding="utf-8")
    for ancien in fn.glob("suspendu_*.mcfunction"):                       # les fonctions d'une version precedente (par exemple les rangs de l'ancienne ouverture des portes) ne doivent pas rester
        if ancien.stem not in textes:
            ancien.unlink()
    for etiquette, valeurs in (("tick", ["atelier:suspendu_tick"]), ("load", ["atelier:suspendu_charger"])):
        chemin = dp / f"data/minecraft/tags/function/{etiquette}.json"
        chemin.parent.mkdir(parents=True, exist_ok=True)
        existantes = json.loads(chemin.read_text(encoding="utf-8"))["values"] if chemin.exists() else []
        chemin.write_text(json.dumps({"values": existantes + [v for v in valeurs if v not in existantes]}, indent=2), encoding="utf-8")
    monde_atelier.installer_pose_auto(save, "suspendu_pose", "suspendu")
    cibles = {"_marque": "[claude-build]", "origine": ORIGINE, "taille": [SX, SY, SZ], "sol": Y_SOL, "entree": list(infos["entree"]), "coffre": list(infos["coffre"]),
              "zombies": [list(z) for z in infos["zombies"]], "centre": list(infos["centre"]),
              "portes": {n: {"cases": sorted(infos["cases_portes"][n]), "y": [Y_SOL + 1, Y_SOL + salle1.HAUT_PORTE], "blocs": ["minecraft:" + m for m in salle1.COULEURS_PORTES[n][:2]]} for n in infos["cases_portes"]},
              "marches": {n: [list(m) for m in infos["marches"][n]] for n in infos["marches"]}, "sources": [list(s) for s in infos["sources"]],
              "decalages": {k: list(v) for k, v in decalages.items()}}
    (exports / "suspendu_cibles.json").write_text(json.dumps(cibles, ensure_ascii=False), encoding="utf-8")
    cibles2 = {"_marque": "[claude-build]", "yf": ss1.YF, "arrivee": list(infos2["arrivee"]), "centre_petite": list(infos2["centre_petite"]), "centre_musicale": list(infos2["centre_musicale"]),
               "coffre_grappin": list(infos2["coffre_grappin"]), "coffre_cle": list(infos2["coffre_cle"]), "coffres": [list(c) for c in infos2["coffres"]], "ancrages": [list(a) for a in infos2["ancrages"]],
               "porte_m1": {"cases": sorted(infos2["cases_porte"]), "y": [ss1.YF + 1, ss1.YF + salle1.HAUT_PORTE], "blocs": sorted(noms_m1)}, "sources": [list(s) for s in infos2["sources"]],
               "fosses": [[list(a), list(b)] for a, b in infos2["fosses"]], "hall_ouest": list(infos2["hall_ouest"]), "hall_milieu": list(infos2["hall_milieu"]),
               "hall_est": list(infos2["hall_est"]), "monstres": [list(m) for m in infos2["monstres_petite"] + infos2["monstres_musicale"]], "monstres_salle_musicale": [list(m) for m in infos2["monstres_musicale"]],
               "plateformes": [list(c) for c in infos2["plateformes"]], "corniche": list(infos2["corniche"]), "sol_haut": infos2["sol_haut"], "echelles": [list(e) for e in infos2["echelles"]], "echelle_estrade": list(infos2["echelle_estrade"]), "enigme": {"pads": [list(q) for q in infos2["enigme"]["pads"]], "depart": list(infos2["enigme"]["depart"]), "lampes": [list(q) for q in infos2["enigme"]["lampes"]]}, "decalages": {k: list(v) for k, v in decalages.items()}}
    (exports / "suspendu_ss1_cibles.json").write_text(json.dumps(cibles2, ensure_ascii=False), encoding="utf-8")
    cibles3 = {"_marque": "[claude-build]", "yf2": ss2.YF2, "plafond": infos3["plafond"], "entree": list(infos3["entree"]), "arrivee": list(infos3["arrivee"]),
               "leviers": {str(k): list(v) for k, v in infos3["leviers"].items()}, "ancrages_bleus": [list(a) for a in infos3["ancrages_bleus"]],
               "ancrages_cyan": {str(k): [list(a) for a in v] for k, v in infos3["ancrages_cyan"].items()}, "coffres": [list(c) for c in infos3["coffres"]], "coffre_cle": list(infos3["coffre_cle"]), "y_fond": infos3["y_fond"], "lum_niveau": infos3["lum_niveau"],
               "piliers_cyan": {str(k): [{"ancrage": list(p["ancrage"]), "cases": [list(c) for c in p["cases"]], "dessus": [list(d) for d in p["dessus"]], "lumiere": list(p["lumiere"])} for p in v] for k, v in infos3["piliers_cyan"].items()},
               "ennemis": {k: [list(m) for m in v] for k, v in infos3["ennemis"].items()}, "sources": [list(s) for s in infos3["sources"]], "decalages": {k: list(v) for k, v in decalages.items()}}
    (exports / "suspendu_ss2_cibles.json").write_text(json.dumps(cibles3, ensure_ascii=False), encoding="utf-8")
    (dp / "data/atelier/loot_table").mkdir(parents=True, exist_ok=True)
    (dp / "data/atelier/loot_table" / (ss3.TABLE_BUTIN.split(":")[1] + ".json")).write_text(json.dumps(TABLE_BUTIN_TUNNEL, indent=2), encoding="utf-8")
    (dp / "data/grappin/tags/function").mkdir(parents=True, exist_ok=True)
    (dp / "data/grappin/tags/function" / f"{suspendu_ss3_fonctions.TAG_MORT}.json").write_text(json.dumps({"values": ["atelier:suspendu_ss3_mort"]}, indent=2), encoding="utf-8")      # le mod appelle ce tag a la mort du golem
    cibles4 = {"_marque": "[claude-build]", "arrivee": list(infos4["arrivee"]), "marches": [list(m) for m in infos4["marches"]], "sources": [list(s) for s in infos4["sources"]], "depart_boss": list(infos4["depart_boss"]),
               "golem": {k: (list(v) if isinstance(v, tuple) else v) for k, v in infos4["golem"].items()}, "plateformes": {n: {"bbox": list(q["bbox"]), "dessus": q["dessus"], "boss": list(q["boss"]) if q["boss"] else None} for n, q in infos4["plateformes"].items()},
               "retour_rampe": list(infos4["retour_rampe"]), "tunnel": {"trous": infos4["tunnel"]["trous"], "ile": infos4["tunnel"]["ile"], "coffres": [{"nom": c["nom"], "pos": list(c["pos"]), "acces": list(c["acces"])} for c in infos4["tunnel"]["coffres"]], "passages": [{k: list(v) for k, v in q.items()} for q in infos4["tunnel"]["passages"]]}, "rampe_trous": infos4["rampe_trous"], "rampe_theta": infos4["rampe_theta"], "pont": [list(c) for c in infos4["pont"]], "trous": infos4["trous"], "troncons": infos4["troncons"], "corniches": {n: list(c) for n, c in infos4["corniches"].items()},
               "ancrages_boss": [list(a) for a in infos4["ancrages_boss"]], "rampe": infos4["rampe"] | {"rayon": list(infos4["rampe"]["rayon"])}, "balcon": list(infos4["balcon"]), "alcove": list(infos4["alcove"]), "sortie": [list(c) for c in infos4["sortie"]], "decalages": {k: list(v) for k, v in decalages.items()}}
    (exports / "suspendu_ss3_cibles.json").write_text(json.dumps(cibles4, ensure_ascii=False), encoding="utf-8")
    print(f"Ecrit : {len(blocs)} blocs (salle 1) + {len(blocs2)} (sous-sol 1) + {len(blocs3)} (salle -2) + {len(blocs4)} (salle 3), {len(textes)} fonctions ; entree en {txt(infos['entree'])}")
    if args.zip:
        monde_atelier.zipper_monde(save, args.monde, RACINE / "mondes", **options)


if __name__ == "__main__":
    main()
