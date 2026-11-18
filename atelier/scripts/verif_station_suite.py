# [claude-build]
"""
verif_station_suite.py - controle de la suite du Donjon de la Station (station_secteurs.py), par la NAGE : on remplit d'un bloc a l'autre tout ce qui n'est pas plein (eau, air, echelles),
depuis un point, avec telle ou telle vanne ouverte. Pour chaque porte (vanne, ecluse, tartre, jet, colonne, bonde, Exutoire), on verifie qu'elle bloque fermee et qu'elle laisse passer ouverte.
Puis la lumiere : chaque case d'eau ou l'on nage dans les secteurs a au moins la lumiere 5 (l'eau l'eteint d'un cran par bloc, comme l'air).
  test-local/venv/Scripts/python.exe atelier/scripts/verif_station_suite.py
"""
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import station_acte1 as a1  # noqa: E402
import station_secteurs as s2  # noqa: E402
from suspendu_outils import AIR  # noqa: E402

PASSE = {"minecraft:air", "minecraft:water", "minecraft:ladder", "minecraft:chain", "minecraft:lever", "minecraft:light"}
EAU = ("minecraft:water", (("level", "0"),))
LIMITE = 900000
TRANSPARENT = PASSE | {"minecraft:iron_bars", "minecraft:waxed_copper_grate"}      # la lumiere passe les barreaux et les grilles ; pas le nageur


def nager(blocs, depart, borne=None):
    """Toutes les cases non pleines reliees a `depart` (6 voisins). Hors de la structure : de la pierre (sauf au dessus de la plaine)."""
    vu, file_ = {depart}, deque([depart])
    while file_:
        x, y, z = file_.popleft()
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if q in vu or q[1] < 0 or q[1] > a1.PLAINE:
                continue
            b = blocs.get(q)
            if b is None or b[0] not in PASSE:
                continue
            vu.add(q)
            file_.append(q)
            if len(vu) > LIMITE:
                return vu
    return vu


def ouvrir(blocs, *listes, bloc=EAU):
    b = dict(blocs)
    for cases in listes:
        for p in cases:
            b[p] = bloc
    return b


def verifier():
    blocs = s2.tout()
    _, i1 = a1.construire()
    _, inf = s2.construire()
    cuve = {}
    for (xa, xb, z) in i1["cuve"]["rangees"]:                       # la Cuve pleine (niveau Haut)
        for x in range(xa, xb + 1):
            for y in range(a1.FOND_CUVE + 1, a1.NIVEAUX["haut"] + 1):
                if blocs.get((x, y, z), AIR)[0] == "minecraft:air":
                    cuve[(x, y, z)] = EAU
    pleine = dict(blocs)
    pleine.update(cuve)
    erreurs = []

    def attendre(nom, vu, cible, voulu):
        if (cible in vu) != voulu:
            erreurs.append(f"{nom} : {'pas atteint' if voulu else 'atteint alors que la porte est fermee'} ({cible})")

    centre_cuve = (120, 50, 95)
    E = inf["ecluses"]
    # le Drain : du bassin d'essai on rejoint la Cuve (seche)
    d = inf["drain"]
    vu = nager(blocs, (d["bouche"][0], d["bouche"][1] + 2, d["bouche"][2]))
    attendre("Drain : du bassin d'essai au pied de la Cuve", vu, (125, 31, 110), True)
    # les ecluses fermees : rien ne passe de la Cuve aux secteurs
    vu = nager(pleine, centre_cuve)
    for nom, cible in (("secteur A", inf["a"]["pompe"][0]), ("secteur B", inf["b"]["plaques"][0][4]), ("secteur C", inf["c"]["pales"][1]["centre"]), ("Cheminee", inf["d"]["rampe"][0]),
                       ("Reservoir", inf["r"]["arrivee"])):
        attendre(f"Cuve, ecluses fermees -> {nom}", vu, (cible[0] + 1, cible[1], cible[2]) if nom == "secteur A" else cible, False)
    # secteur A
    A = inf["a"]
    a_ouvert = ouvrir(pleine, E["A"]["externe"], E["A"]["interne"])
    vu = nager(a_ouvert, centre_cuve)
    vu = nager(ouvrir(a_ouvert, *A["vannes"]), centre_cuve)
    attendre("A : galerie ouverte -> salle des lits", vu, (50, 66, 20), True)
    attendre("A : galerie ouverte -> volant de la pompe", vu, (A["volant_pos"][0] - 1, A["volant_pos"][1], A["volant_pos"][2]), True)
    vu = nager(ouvrir(a_ouvert, *A["vannes"], *A["vannes_cache"]), centre_cuve)
    attendre("A : chambre cachee ouverte", vu, (A["coffre"][0], A["coffre"][1] + 1, A["coffre"][2]), True)
    # secteur B
    B = inf["b"]
    b_ouvert = ouvrir(pleine, E["B"]["externe"], E["B"]["interne"])
    vu = nager(b_ouvert, centre_cuve)
    attendre("B : plaque 1", vu, B["plaques"][0][4], True)
    attendre("B : plaque 2 derriere son jet", vu, B["plaques"][1][4], False)
    vu = nager(ouvrir(b_ouvert, B["jets"][2]), centre_cuve)
    attendre("B : jet 2 ouvert -> plaque 2", vu, B["plaques"][1][4], True)
    attendre("B : plaque 3 derriere son jet", vu, B["plaques"][2][4], False)
    attendre("B : halle haute avant la colonne", vu, B["depart_b3"], False)
    vu = nager(ouvrir(b_ouvert, B["jets"][2], B["jets"][3], B["vanne_colonne"]), centre_cuve)
    attendre("B : colonne ouverte -> halle haute", vu, B["depart_b3"], True)
    # secteur C
    C = inf["c"]
    c_ouvert = ouvrir(pleine, E["C"]["externe"], E["C"]["interne"])
    vu = nager(c_ouvert, centre_cuve)
    attendre("C : etage 1", vu, (C["pales"][1]["centre"][0] + 5, C["pales"][1]["centre"][1], C["pales"][1]["centre"][2]), True)
    attendre("C : etage 2 sous le tartre", vu, (C["pales"][2]["centre"][0] + 5, C["pales"][2]["centre"][1], C["pales"][2]["centre"][2]), False)
    vu = nager(ouvrir(c_ouvert, C["tartres"][1]), centre_cuve)
    attendre("C : tartre 1 brise -> etage 2", vu, (C["pales"][2]["centre"][0] + 5, C["pales"][2]["centre"][1], C["pales"][2]["centre"][2]), True)
    attendre("C : etage 3 sous le tartre", vu, (C["pales"][3]["centre"][0] + 5, C["pales"][3]["centre"][1], C["pales"][3]["centre"][2]), False)
    vu = nager(ouvrir(c_ouvert, C["tartres"][1], C["tartres"][2]), centre_cuve)
    attendre("C : etage 3", vu, (C["pales"][3]["centre"][0] + 5, C["pales"][3]["centre"][1], C["pales"][3]["centre"][2]), True)
    attendre("C : salle de la pompe derriere sa grille", vu, C["levier"], False)
    vu = nager(ouvrir(c_ouvert, C["tartres"][1], C["tartres"][2], C["grille_salle"], bloc=AIR), centre_cuve)
    vu = nager(ouvrir(c_ouvert, C["tartres"][1], C["tartres"][2]) | {p: AIR for p in C["grille_salle"]}, centre_cuve)
    attendre("C : grille levee -> salle de la pompe", vu, (C["levier"][0], C["levier"][1] + 1, C["levier"][2]), True)
    # la Cheminee et le Poste
    D = inf["d"]
    bas = dict(blocs)
    for (xa, xb, z) in i1["cuve"]["rangees"]:
        for x in range(xa, xb + 1):
            for y in range(a1.FOND_CUVE + 1, a1.NIVEAUX["bas"] + 1):
                if blocs.get((x, y, z), AIR)[0] == "minecraft:air":
                    bas[(x, y, z)] = EAU
    vu = nager(ouvrir(bas, D["vanne_d"]), (120, 35, 95))
    vu = nager(ouvrir(bas, D["vanne_d"], *D["vannes"]), (120, 35, 95))
    attendre("Cheminee : galerie -> rampe", vu, D["rampe"][0], True)
    attendre("Cheminee : bassin du haut sous le tartre", vu, (170, 74, 46), False)
    vu = nager(ouvrir(bas, D["vanne_d"], *D["vannes"], D["tartre"]), (120, 35, 95))
    attendre("Cheminee : tartre brise -> bassin du haut", vu, (170, 74, 46), True)
    attendre("Cheminee : le Poste n'est pas sur la Coursive avant le levier", nager(blocs, (120, a1.SOL + 1, 128)), D["poste"], False)
    # le Reservoir et l'Exutoire
    R = inf["r"]
    res = ouvrir(bas, D["bonde"], D["puits_bonde"])
    vu = nager(res, (120, 35, 95))
    attendre("Bonde ouverte -> Reservoir", vu, R["arrivee"], True)
    attendre("Reservoir : le lac derriere l'Exutoire ferme", vu, R["lac"], False)
    vu = nager(ouvrir(res, R["exutoire"], R["grille_puits"]), (120, 35, 95))
    attendre("Exutoire ouvert -> lac", vu, (R["lac"][0], R["lac"][1] - 1, R["lac"][2]), True)
    return erreurs, blocs, pleine, inf


def lumiere(blocs, sources, cases, minimum=5):
    niveau, file_ = {}, deque()
    for p in sources:
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            b = blocs.get(q)
            if b is not None and b[0] in TRANSPARENT and niveau.get(q, 0) < 14:
                niveau[q] = 14
                file_.append(q)
    while file_:
        p = file_.popleft()
        n = niveau[p]
        if n <= minimum:
            continue
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            b = blocs.get(q)
            if b is not None and b[0] in TRANSPARENT and niveau.get(q, 0) < n - 1:
                niveau[q] = n - 1
                file_.append(q)
    return [c for c in cases if niveau.get(c, 0) < minimum]


if __name__ == "__main__":
    erreurs, blocs, pleine, inf = verifier()
    parts, _ = s2.construire()
    _, i1 = a1.construire()
    sources = [p for p, b in blocs.items() if b[0] in ("minecraft:sea_lantern", "minecraft:verdant_froglight", "minecraft:ochre_froglight", "minecraft:shroomlight", "minecraft:light")]
    cases = [p for nom in s2.ORDRE for p, b in parts[nom].items() if b[0] in ("minecraft:water", "minecraft:air") and p[1] < a1.PLAINE]
    noirs = lumiere(blocs, sources, cases)
    par = {}
    for p in noirs:
        nom = next(n for n in s2.ORDRE if p in parts[n])
        par[nom] = par.get(nom, 0) + 1
    print(f"lumiere : {len(cases) - len(noirs)} cases sur {len(cases)} a 5 ou plus ; dans le noir : {par}")
    if noirs:
        erreurs.append(f"{len(noirs)} cases d'eau ou d'air dans le noir : {par}")
    print("\n".join(erreurs) if erreurs else "OK : tout est conforme.")
    sys.exit(1 if erreurs else 0)
