# [claude-build]
"""Cree exports/Pack_ami.zip pour l'ami : le monde final des arenes (Arene Semaine), le monde du donjon n°1 v2 (dossier « Donjon 1 »),
le mod Grappin 2.3 et un mode d'emploi.
Usage : zip_donjon.py   (a lancer quand le monde n'est pas ouvert)"""
import zipfile
from pathlib import Path

R = Path(__file__).resolve().parent.parent
W = R / "launcher/instances/Build-1.21.1/minecraft/saves/Nouveau monde (1)"
JAR = R / "exports/grappin-2.3.0.jar"
Z = R / "exports/Pack_ami.zip"
ARENE = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine"
LISEZMOI = """Les 7 arenes (Arene Semaine) et le donjon n°1 - La Gorge suspendue, version 2 (grappin a balancier)

Il faut : Minecraft 1.21.1 avec NeoForge 21.1.216 et Create 6.0.9.

1. Mets mods/grappin-2.3.0.jar dans ton dossier mods (a cote de Create) et retire l'ancien grappin-1.1.0.jar si tu l'as.
2. Mets les dossiers saves/Arene Semaine et saves/Donjon 1 dans ton dossier saves (remplace l'ancien Donjon Grappin).
3. Lance le jeu. Les arenes : monde « Arene Semaine ». Le donjon : monde « Donjon 1 ».
4. Entree du donjon : /tp @s -77 -60 -4 -90 0  (le grappin est donne a l'entree). Remise a zero : /function atelier:donjon_reset

Grappin : clic droit lance le crochet (30 blocs). Il ne tient que sur les points d'ancrage (blocs metalliques lumineux).
Accroche, on se balance : les touches donnent de l'elan, la molette allonge ou raccourcit la corde, maintenir saut rembobine.
S'accroupir : lacher (on garde sa vitesse, avec un petit saut). Clic droit accroche : lacher et relancer.
Pas de degats de chute tant qu'on est accroche ; ils reviennent apres le lacher.
Le donjon dure environ 30 minutes. Cherche bien : il y a des leviers caches, une enigme, une cle... et 8 salles secretes.
"""
with zipfile.ZipFile(Z, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in sorted(W.rglob("*")):
        if p.is_file() and p.name != "session.lock":
            z.write(p, Path("saves/Donjon 1") / p.relative_to(W))
    for p in sorted(ARENE.rglob("*")):
        if p.is_file() and p.name != "session.lock":
            z.write(p, Path("saves/Arene Semaine") / p.relative_to(ARENE))
    z.write(JAR, "mods/" + JAR.name)
    z.writestr("LISEZMOI.txt", LISEZMOI)
with zipfile.ZipFile(Z) as z:
    assert z.testzip() is None, "archive abimee"
    noms = z.namelist()
    print("fichiers", len(noms), "taille", round(Z.stat().st_size / 1e6, 1), "Mo")
    print("mod :", [n for n in noms if n.startswith("mods/")], "| level.dat :", "saves/Donjon 1/level.dat" in noms,
          "| structure :", any(n.endswith("donjon2.nbt") for n in noms), "| regions :", sum(1 for n in noms if "/region/" in n),
          "| Arene Semaine :", "saves/Arene Semaine/level.dat" in noms)
