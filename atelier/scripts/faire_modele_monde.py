# [claude-build]
"""faire_modele_monde.py - fabrique le modele de monde superflat (atelier/modeles/level_superflat.dat) a partir du level.dat d'un monde superflat cree PAR LE JEU, nettoye de tout
ce qui est propre a la partie de son auteur (joueur, heure, evenements, paquets). A relancer si la version du jeu change (DataVersion).
  python atelier/scripts/faire_modele_monde.py "chemin/vers/un/monde/superflat/level.dat" """
import sys
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Int, List, Long, String

if len(sys.argv) != 2:
    raise SystemExit(__doc__)
f = nbtlib.load(sys.argv[1])
d = f["Data"]
type_ = str(d["WorldGenSettings"]["dimensions"]["minecraft:overworld"]["generator"]["type"])
if type_ != "minecraft:flat":
    raise SystemExit(f"ce monde n'est pas un superflat ({type_})")
for cle in ("Player", "ScheduledEvents", "CustomBossEvents", "DragonFight", "ServerBrands"):
    d.pop(cle, None)
d["LevelName"] = String("Modele")
d["Time"] = Long(0)
d["DayTime"] = Long(0)
d["LastPlayed"] = Long(0)
d["SpawnX"], d["SpawnY"], d["SpawnZ"] = Int(0), Int(-60), Int(0)
d["DataPacks"] = Compound({"Enabled": List[String]([String("vanilla")]), "Disabled": List[String]([])})
d["GameType"] = Int(1)
d["allowCommands"] = Byte(1)
cible = Path(__file__).resolve().parent.parent / "modeles" / "level_superflat.dat"
cible.parent.mkdir(parents=True, exist_ok=True)
f.save(cible)
print("ecrit", cible, cible.stat().st_size, "octets ; DataVersion", int(d["DataVersion"]))
