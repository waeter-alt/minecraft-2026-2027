"""Installe dans le client de test les mods RPG Series du pack du lanceur.

Pourquoi : `mod/run/client/mods/` n'est pas versionné (dossier `run/` ignoré par git), donc
une machine neuve, ou un `run/` effacé, n'a pas les mods. Ce script les retélécharge depuis les
fiches `launcher/pack/mods/*.pw.toml`, vérifie chaque hash, et copie les jars. Il ne suppose
aucun état local : il crée les dossiers dont il a besoin et peut être relancé sans risque.

Usage (depuis la racine du dépôt) :
    python -I launcher/scripts/install_test_mods.py            # client de test
    python -I launcher/scripts/install_test_mods.py --server   # client + serveur de test
    python -I launcher/scripts/install_test_mods.py --clean    # retire d'abord les jars RPG installés

Avant de lancer le client : FERMER OVERWOLF. Il s'injecte dans le JVM Minecraft et a fait planter
le client de test (plantage natif dans le ramasse-miettes) avec cette pile de mods.

Le datapack `launcher/test-datapacks/rpg_limits` (limites de points de Skill Tree relevées) est copié
dans la sauvegarde `mod/run/client/saves/rpgtest` si elle existe.
"""
import argparse
import hashlib
import re
import shutil
import sys
import tomllib
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACK_MODS = ROOT / "launcher" / "pack" / "mods"
CACHE = ROOT / "mod" / "run" / "mods-cache"

# Pile RPG Series et ses dépendances. Les autres mods du pack (MineColonies, Create...) viennent
# de `mod/libs/` ; les mods de rendu (Sodium, Iris...) sont volontairement exclus du client de dev.
MODS = """spell-engine spell-power wizards paladins-and-priests rogues-and-warriors archers jewelry runes
arsenal-rpg-series relics-rpg armory-rpg-series skill-tree more-rpg-library elemental-wizards-rpg gazebos
armor-model-api puffish-skills gachaaddiction ranged-weapon-api structure-pool-api bundle-api shield-api
critical-strike better-combat playeranimator cloth-config accessories owo-lib forgified-fabric-api
azurelib-armor""".split()


def entry(name):
    data = tomllib.loads((PACK_MODS / f"{name}.pw.toml").read_text(encoding="utf8"))
    d = data["download"]
    return data["filename"], d["url"], d["hash-format"], d["hash"]


def fetch(filename, url, fmt, expected):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / filename
    if not path.exists() or hashlib.new(fmt, path.read_bytes()).hexdigest() != expected:
        req = urllib.request.Request(url, headers={"User-Agent": "interregne-test-mods"})
        path.write_bytes(urllib.request.urlopen(req, timeout=180).read())
    if hashlib.new(fmt, path.read_bytes()).hexdigest() != expected:
        sys.exit(f"Hash incorrect pour {filename} : fichier refusé.")
    return path


def install(dest, entries, clean):
    dest.mkdir(parents=True, exist_ok=True)
    if clean:
        for _, filename in entries:
            (dest / filename).unlink(missing_ok=True)
    for path, filename in entries:
        shutil.copy2(path, dest / filename)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", action="store_true", help="installe aussi dans mod/run/server/mods")
    ap.add_argument("--clean", action="store_true", help="retire les jars RPG avant de réinstaller")
    args = ap.parse_args()

    entries = []
    for name in MODS:
        filename, url, fmt, h = entry(name)
        entries.append((fetch(filename, url, fmt, h), filename))
        print(f"OK  {filename}")

    if args.clean:
        # retire aussi les anciennes versions éventuelles de ces mods (autres noms de fichier)
        for sub in ("client", "server"):
            mods = ROOT / "mod" / "run" / sub / "mods"
            if mods.is_dir():
                keep = {f for _, f in entries}
                for jar in mods.glob("*.jar"):
                    if jar.name not in keep and not jar.name.startswith("grappin") and re.match(
                            r"(spell_|wizards|paladins|rogues|archers|jewelry|runes|arsenal|relics|armory|skill_tree|"
                            r"mrpg|more_rpg|elemental_wizards|gazebo|armor_model|puffish|gachaaddiction|ranged_weapon|"
                            r"structure_pool|bundle-api|shield_api|critical_strike|bettercombat|player-animation|cloth-config|"
                            r"accessories|owo-lib|forgified|azurelib)", jar.name):
                        jar.unlink()
                        print(f"retiré (ancienne version) : {jar.name}")

    install(ROOT / "mod" / "run" / "client" / "mods", entries, args.clean)
    if args.server:
        install(ROOT / "mod" / "run" / "server" / "mods", entries, args.clean)

    save = ROOT / "mod" / "run" / "client" / "saves" / "rpgtest"
    if save.is_dir():
        shutil.copytree(ROOT / "launcher" / "test-datapacks" / "rpg_limits",
                        save / "datapacks" / "rpg_limits", dirs_exist_ok=True)
        print("datapack rpg_limits copié dans la sauvegarde rpgtest")
    print(f"\n{len(entries)} mods installés. Ferme Overwolf avant de lancer le client.")


if __name__ == "__main__":
    main()
