# [claude-build]
"""monde_atelier.py - le monde de test d'un generateur : on le trouve par son nom en jeu (LevelName), et s'il n'existe pas on le CREE (superflat, creatif, commandes permises), avec son
datapack. Un generateur n'a ainsi jamais besoin d'un monde cree a la main (regle du CLAUDE.md : un script ne suppose aucun etat local deja en place).
Le modele de monde (atelier/modeles/level_superflat.dat) vient d'un monde superflat cree par le jeu (voir faire_modele_monde.py). Un monde qui existe deja n'est JAMAIS modifie.
La pose automatique (installer_pose_auto) fait poser la structure une seule fois, a la premiere ouverture du monde : plus de /function a taper ni d'etiquette load a ajouter a la main."""
import json
import random
import tempfile
import time
import zipfile
from pathlib import Path

import nbtlib
from nbtlib import Long, String

MODELE = Path(__file__).resolve().parent.parent / "modeles" / "level_superflat.dat"


def dossier_du_monde(nom, saves):
    """Dossier du monde dont le LevelName vaut `nom` ; a defaut `saves / nom` (qui peut ne pas exister)."""
    saves = Path(saves)
    for d in (sorted(saves.iterdir()) if saves.exists() else []):
        try:
            if str(nbtlib.load(d / "level.dat")["Data"]["LevelName"]) == nom:
                return d
        except Exception:
            continue
    return saves / nom


def monde_superflat(save):
    """Vrai si le monde est un superflat ; vrai aussi quand level.dat est illisible (on ne sait pas, on ne crie pas au loup)."""
    try:
        dims = nbtlib.load(Path(save) / "level.dat")["Data"]["WorldGenSettings"]["dimensions"]
        return str(dims["minecraft:overworld"]["generator"]["type"]) == "minecraft:flat"
    except Exception:
        return True


def datapack(save):
    """Dossier du datapack « atelier » du monde, cree avec son pack.mcmeta s'il manque."""
    dp = Path(save) / "datapacks/atelier"
    (dp / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    (dp / "data/atelier/function").mkdir(parents=True, exist_ok=True)
    if not (dp / "pack.mcmeta").exists():
        (dp / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Atelier - structures generees"}}, indent=2), encoding="utf-8")
    return dp


def level_neuf(nom):
    """level.dat d'un monde superflat neuf, au nom `nom`, tire du modele."""
    f = nbtlib.load(MODELE)
    d = f["Data"]
    d["LevelName"] = String(nom)
    d["LastPlayed"] = Long(int(time.time() * 1000))
    d["WorldGenSettings"]["seed"] = Long(random.randint(-2 ** 62, 2 ** 62))
    return f


def trouver_ou_creer_monde(nom, saves):
    """Renvoie le dossier du monde `nom` : celui qui existe, sinon un monde superflat neuf cree dans `saves / nom`."""
    saves = Path(saves)
    save = dossier_du_monde(nom, saves)
    if (save / "level.dat").exists():
        return save
    save.mkdir(parents=True, exist_ok=True)
    level_neuf(nom).save(save / "level.dat")
    datapack(save)
    print(f"Monde « {nom} » cree (superflat) dans {save}", flush=True)
    return save


def installer_pose_auto(save, fonction_pose, drapeau):
    """A l'ouverture du monde, pose la structure une seule fois : une fonction `charger_<drapeau>` appelle `fonction_pose` 5 s plus tard (le temps que le joueur soit connecte, sinon son message n.est vu de personne) puis leve un drapeau, et l'etiquette minecraft:load l'appelle.
    Plusieurs generateurs peuvent partager un monde (un drapeau chacun). Pour reposer : /scoreboard players set #<drapeau> atelier 0 puis /reload."""
    dp = datapack(save)
    (dp / "data/atelier/function" / f"charger_{drapeau}.mcfunction").write_text(
        f"# [claude-build] Pose automatique, une seule fois (drapeau #{drapeau}).\nscoreboard objectives add atelier dummy\n"
        f"execute unless score #{drapeau} atelier matches 1 run schedule function atelier:{fonction_pose} 100t\n"
        f"scoreboard players set #{drapeau} atelier 1\n", encoding="utf-8")
    etiquette = dp / "data/minecraft/tags/function/load.json"
    etiquette.parent.mkdir(parents=True, exist_ok=True)
    valeurs = json.loads(etiquette.read_text(encoding="utf-8"))["values"] if etiquette.exists() else []
    if f"atelier:charger_{drapeau}" not in valeurs:
        valeurs.append(f"atelier:charger_{drapeau}")
    etiquette.write_text(json.dumps({"values": valeurs}, indent=2), encoding="utf-8")


def message_entree(phrase, coords):
    """Commande tellraw « <phrase> Entree : [/tp ...] » dont le /tp est cliquable (inutile de le recopier)."""
    tp = f"/tp @s {coords} 0 0"
    return ('tellraw @a [{"text":"' + phrase + ' Entrée : ","color":"green"},{"text":"[' + tp + ']","color":"aqua","underlined":true,'
            '"clickEvent":{"action":"run_command","value":"' + tp + '"},"hoverEvent":{"action":"show_text","contents":"Cliquer pour y aller"}}]')


def zipper_monde(save, nom, dossier_zip):
    """Ecrit `dossier_zip / <nom>.zip` : un monde propre, pret a copier dans saves/ (le dossier du zip porte le nom du monde). Il contient un level.dat neuf et le datapack de `save`,
    rien d'autre : pas de joueur, pas de drapeau de pose, donc la structure se pose toute seule a la premiere ouverture. Renvoie le chemin du zip."""
    dossier_zip = Path(dossier_zip)
    dossier_zip.mkdir(parents=True, exist_ok=True)
    cible = dossier_zip / f"{nom}.zip"
    dp = datapack(save)
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(cible, "w", zipfile.ZIP_DEFLATED) as z:
        level_neuf(nom).save(Path(tmp) / "level.dat")
        z.write(Path(tmp) / "level.dat", f"{nom}/level.dat")
        for fichier in sorted(dp.rglob("*")):
            if fichier.is_file():
                z.write(fichier, f"{nom}/datapacks/atelier/{fichier.relative_to(dp).as_posix()}")
    print(f"Monde pret a copier : {cible}", flush=True)
    return cible
