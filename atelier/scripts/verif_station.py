# [claude-build]
"""
verif_station.py - controle des actes I et II du Donjon de la Station (station_acte1.py), relus depuis les deux structures exportees, sur la plaine (pleine sous y 108) :
- l'escalier du pavillon descend d'une marche a la fois, sur 24 marches ;
- A PIED, vanne fermee : depuis l'entree, on atteint le Hall, le Vestiaire, le bassin de 50 m (par ses echelles) et son volant (2), le fond du plongeon (par ses echelles) ; PAS le volant 1
  (sur la plateforme P3, au milieu du vide), PAS le volant 3 (au sommet de la tour), PAS la Grande Cuve (la vanne du Sas) ;
- AU GRAPPIN 3 (meme simulation que verif_ss2) : on atteint aussi les volants 1 et 3 ; toujours pas la Cuve tant que la vanne est la ;
- vanne levee : on atteint la coursive de la Cuve ;
- aucune case atteignable n'est dans le noir (lumiere >= 5).
  test-local/venv/Scripts/python.exe atelier/scripts/verif_station.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import station_acte1 as a1  # noqa: E402
from suspendu_outils import AIR, lire_structure  # noqa: E402
from verif_donjon_suspendu import SEUIL_LUMIERE, Terrain, lumiere, marcher  # noqa: E402
from verif_ss1 import CHUTE  # noqa: E402
from verif_ss2 import a_cote, fermeture  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
Z_COUPURE = 131


def relire(exports):
    """Les blocs des deux structures, en coordonnees du gabarit (les decalages : ceux qu'ecrit donjon_station.py, recalcules ici)."""
    blocs, _ = a1.construire()
    blocs_lus = {}
    for nom, garde in (("station_cuve", lambda p: p[2] < Z_COUPURE), ("station_hall", lambda p: p[2] >= Z_COUPURE)):
        partie = [p for p in blocs if garde(p)]
        dec = tuple(min(p[i] for p in partie) for i in range(3))
        blocs_lus.update(lire_structure(exports / f"{nom}.nbt", dec))
    return blocs_lus


def verifier(exports=RACINE / "exports"):
    blocs = relire(exports)
    _, infos = a1.construire()
    erreurs = []
    # l'escalier
    marches = sorted({(y, z) for (x, y, z) in infos["marches"]}, reverse=True)
    if len(marches) != a1.NB_MARCHES:
        erreurs.append(f"escalier : {len(marches)} marches au lieu de {a1.NB_MARCHES}")
    for (y0, z0), (y1, z1) in zip(marches, marches[1:]):
        if y0 - y1 != 1 or abs(z0 - z1) != 1:
            erreurs.append(f"escalier : saut entre ({y0},{z0}) et ({y1},{z1})")
    ancrages = [(x, y, z) for (_, x, y, z) in infos["ancrages"]]
    depart = [infos["entree"]]
    leviers = infos["leviers"]
    t = Terrain(blocs, a1.PLAINE)
    a_pied = marcher(t, depart, CHUTE)
    for nom, cible, attendu in (("le Hall", (120, a1.SOL + 1, 170), True), ("le Vestiaire", (30, a1.SOL + 1, 185), True), ("le volant 2", leviers[2], True),
                                ("le fond du plongeon", (78, a1.FOND_PLONGEON + 1, 168), True), ("le volant 1", leviers[1], False), ("le volant 3", leviers[3], False),
                                ("la coursive de la Cuve", infos["coursive"], False)):
        if a_cote(a_pied, cible) != attendu:
            erreurs.append(f"a pied : {nom} {'pas ' if attendu else ''}atteint")
    grappin, nb_air = fermeture(t, depart, ancrages)
    for nom, cible, attendu in (("le volant 1", leviers[1], True), ("le volant 3", leviers[3], True), ("la coursive de la Cuve", infos["coursive"], False)):
        if a_cote(grappin, cible) != attendu:
            erreurs.append(f"au grappin : {nom} {'pas ' if attendu else ''}atteint")
    ouvert = dict(blocs)
    for p in infos["vanne_sas"]:
        ouvert[p] = AIR
    t2 = Terrain(ouvert, a1.PLAINE)
    tout, _ = fermeture(t2, depart, ancrages)
    if not a_cote(tout, infos["coursive"]):
        erreurs.append("vanne levee : la coursive de la Cuve pas atteinte")
    niveaux = lumiere(t2, infos["sources"], [c for c in tout if c[1] <= a1.PLAINE])
    noirs = sorted(c for c, n in niveaux.items() if n < SEUIL_LUMIERE)
    if noirs:
        erreurs.append(f"{len(noirs)} cases atteignables dans le noir, par ex. {noirs[:8]}")
    print(f"a pied : {len(a_pied)} cases ; au grappin : {len(grappin)} cases ({nb_air} points en l'air) ; vanne levee : {len(tout)} cases")
    return erreurs


if __name__ == "__main__":
    e = verifier()
    print("\n".join(e) if e else "OK : tout est conforme.")
    sys.exit(1 if e else 0)
