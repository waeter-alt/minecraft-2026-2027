# [claude-build]
"""
monde_test_ocean.py - monde de test de la visibilite sous l'eau avec la Cape lestee : un VRAI monde vanilla (graine fixe, generation normale), apparition au milieu d'un
ocean profond. Le monde est un zip pret a copier : atelier/mondes/Test ocean profond.zip, a dezipper dans mod/run/client/saves/ (aucune commande a taper).
  python atelier/scripts/monde_test_ocean.py
Graine 20261123. La zone (biome minecraft:deep_ocean, mesuree avec /locate biome puis sondee bloc par bloc sur le serveur de test) s'etend sur x 750..1400, z -1680..-1170 ;
le monde apparait en (1184, -1232) : surface a y 62, dernier bloc solide du fond a y 28 (34 blocs d'eau), et une fosse un peu plus loin, en (1200, -1232), dont le fond est a y 24 (38 blocs d'eau).
Le fond du reste de la zone est entre y 31 et 45 (17 a 31 blocs d'eau). Premiere ouverture : un equipement (cape, grappin) et jour clair sont donnes automatiquement au bout de 5 s.
Se redonner l'equipement : /function atelier:ocean_kit.
"""
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import monde_atelier
from nbtlib import Byte, Compound, Int, Long, String

NOM = "Test ocean profond"
RACINE = HERE.parent
GRAINE = 20261123
APPARITION = (1184, 63, -1232)

KIT = [
    "gamemode survival @s",
    "give @s grappin:cape_lestee",
    "give @s grappin:grappin",
    "give @s minecraft:cooked_beef 16",
]


def retoucher(d):
    """Overworld vanilla (generateur a bruit, relief et biomes normaux, structures comprises) sur la graine fixe ; survie, paisible, commandes permises."""
    w = d["WorldGenSettings"]
    w["seed"] = Long(GRAINE)
    w["generate_features"] = Byte(1)
    w["dimensions"]["minecraft:overworld"]["generator"] = Compound({
        "type": String("minecraft:noise"),
        "settings": String("minecraft:overworld"),
        "biome_source": Compound({"type": String("minecraft:multi_noise"), "preset": String("minecraft:overworld")}),
    })
    d["SpawnX"], d["SpawnY"], d["SpawnZ"] = (Int(v) for v in APPARITION)
    d["GameType"] = Int(0)
    d["Difficulty"] = Byte(0)
    d["allowCommands"] = Byte(1)


def ecrire(save):
    fonctions = monde_atelier.datapack(save) / "data/atelier/function"
    (fonctions / "ocean_kit_un.mcfunction").write_text("# [claude-build]\n" + "\n".join(KIT) + "\n", encoding="utf-8")
    (fonctions / "ocean_kit.mcfunction").write_text("# [claude-build] equipement de test\nexecute as @a run function atelier:ocean_kit_un\n", encoding="utf-8")
    accueil = [
        "# [claude-build] accueil du monde de test de l'ocean profond (une seule fois, a la premiere ouverture)",
        "gamerule doMobSpawning false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "time set day",
        "weather clear",
        "function atelier:ocean_kit",
        'tellraw @a [{"text":"Monde de test : ocean profond vanilla. Equipe la cape lestee (emplacement cape d\'Accessories) puis plonge : plus de brouillard ni de voile bleu, et la vue reste claire jusqu\'au fond.","color":"yellow"}]',
        'tellraw @a [{"text":"Fosse la plus profonde : ","color":"gray"},{"text":"[/tp @s 1200 62 -1232]","color":"aqua","underlined":true,"clickEvent":{"action":"run_command","value":"/tp @s 1200 62 -1232"},"hoverEvent":{"action":"show_text","contents":"Cliquer pour y aller (38 blocs d\'eau)"}}]',
        'tellraw @a [{"text":"Comparer : ","color":"gray"},{"text":"retire la cape (touche de l\'inventaire), le brouillard bleu revient.","color":"white"}]',
    ]
    (fonctions / "ocean_pose.mcfunction").write_text("\n".join(accueil) + "\n", encoding="utf-8")
    monde_atelier.installer_pose_auto(save, "ocean_pose", "ocean")


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as tmp:
        save = Path(tmp) / NOM
        ecrire(save)
        monde_atelier.zipper_monde(save, NOM, RACINE / "mondes", retoucher=retoucher)
