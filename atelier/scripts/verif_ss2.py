# [claude-build]
"""
verif_ss2.py - controle de la salle -2 du Donjon Suspendu (le rez-de-chaussee et la salle -2 poses ensemble, la porte 2 ouverte), etape par etape, avec le grappin 3 :
- a pied, depuis l'entree du donjon : l'escalier de 46 marches, le couloir et l'ile d'entree ; rien d'autre (le vide est mortel) ;
- ETAPE 0 : seuls les ancrages bleus (les piliers et le point de l'ile du levier 1) existent : on atteint le levier 1 mais ni le levier 2 ni la cle 3 ;
- ETAPE k : le levier k a fait apparaitre ses ancrages cyan : on atteint le levier k+1 (ou, apres le 5, le coffre de la cle 3), et pas plus loin (l'ordre 1 a 5 est bien celui de l'enigme) ;
- les tirs peuvent s'enchainer en l'air : arrive a un ancrage sans sol dessous (les ancrages cyan 3 et 5 sont au milieu du vide), on tombe un peu puis on tire le suivant ;
- aucune case atteignable n'est dans le noir ; l'escalier de la porte 2 fait bien 46 marches, une a la fois.
  test-local/venv/Scripts/python.exe atelier/scripts/verif_ss2.py
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from suspendu_outils import S, lire_structure  # noqa: E402
from verif_donjon_suspendu import SEUIL_LUMIERE, Terrain, lumiere, marcher  # noqa: E402
from verif_ss1 import ARRIVEE, CHUTE, CRAN_CROCHET, HAUT_YEUX, PORTEE, centre, debout, lance, retirer, voit  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
CHUTE_AVANT_TIR = 0.78       # apres une arrivee en l'air, on tombe d'environ 1,5 bloc avant de retirer (6 ticks de recharge) : l'oeil est 0,78 sous le point d'arrivee


def chute_verticale(t, p):
    """Le centre du joueur est en p : on tombe tout droit. La case ou l'on se tient si le sol est a moins de CHUTE blocs (on survit), sinon None (on tombe dans l'abime : mortel, il faut retirer avant)."""
    x, y, z = math.floor(p[0]), math.floor(p[1] - 0.9 + 1e-6), math.floor(p[2])
    haut = y
    while y > haut - CHUTE:
        cell = (x, y, z)
        if t.solide(cell):
            return None
        if t.solide((x, y - 1, z)) and not t.solide((x, y + 1, z)) and not t.lave(cell):
            return cell
        if t.lave(cell):                                                                        # on tombe dans la lave : pas un point d'arrivee
            return None
        y -= 1
    return None


def arrivee(t, oeil, a, ancrages):
    """Tir de `oeil` sur l'ancrage a : (point d'arrivee du centre du joueur, case d'arrivee ou None) si le crochet s'accroche bien a a, sinon None."""
    ca = centre(a)
    longueur = math.dist(oeil, ca)
    if longueur > PORTEE or longueur < 1e-6 or lance(t, oeil, a, ancrages) != tuple(a):
        return None
    u = tuple((ca[i] - oeil[i]) / longueur for i in range(3))
    p = tuple(ca[i] - u[i] * (CRAN_CROCHET + ARRIVEE) for i in range(3))
    if not voit(t, (oeil[0], oeil[1] - 0.72, oeil[2]), p, a):
        return None
    return p, chute_verticale(t, p)


def fermeture(t, depart, ancrages):
    """Tout ce qu'on atteint depuis `depart` (des cases ou l'on se tient), a pied et au grappin, tirs enchaines en l'air compris. Renvoie (cases, nombre de points en l'air)."""
    vu = set(marcher(t, depart, CHUTE))
    traites, air, air_traites = set(), {}, set()
    tires = [tuple(a) for a in ancrages]
    changement = True
    while changement:
        changement = False
        for s in [c for c in vu if c not in traites]:
            traites.add(s)
            oeil = (s[0] + 0.5, s[1] + HAUT_YEUX, s[2] + 0.5)
            for a in tires:
                if abs(oeil[0] - a[0]) > PORTEE or abs(oeil[2] - a[2]) > PORTEE or abs(oeil[1] - a[1]) > PORTEE:
                    continue
                r = arrivee(t, oeil, a, tires)
                if r is None:
                    continue
                p, sol = r
                if sol is not None:
                    if sol not in vu:
                        vu |= marcher(t, [sol], CHUTE)
                        changement = True
                else:
                    cle = tuple(round(v) for v in p)
                    if cle not in air:
                        air[cle] = p
                        changement = True
        for cle, p in list(air.items()):
            if cle in air_traites:
                continue
            air_traites.add(cle)
            oeil = (p[0], p[1] - CHUTE_AVANT_TIR, p[2])
            for a in tires:
                if math.dist(oeil, centre(a)) > PORTEE:
                    continue
                r = arrivee(t, oeil, a, tires)
                if r is None:
                    continue
                p2, sol = r
                if sol is not None:
                    if sol not in vu:
                        vu |= marcher(t, [sol], CHUTE)
                        changement = True
                else:
                    c2 = tuple(round(v) for v in p2)
                    if c2 not in air:
                        air[c2] = p2
                        changement = True
    return vu, len(air)


def a_cote(vu, cible):
    x, y, z = cible
    return any(c in vu for c in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y, z)))


def verifier(exports):
    exports = Path(exports)
    ci1 = json.loads((exports / "suspendu_cibles.json").read_text(encoding="utf-8"))
    ci3 = json.loads((exports / "suspendu_ss2_cibles.json").read_text(encoding="utf-8"))
    base = lire_structure(exports / "suspendu_salle1.nbt", tuple(ci1["decalages"]["salle1"]))
    base.update(lire_structure(exports / "suspendu_ss2.nbt", tuple(ci3["decalages"]["ss2"])))                 # comme en jeu : posee apres
    for p in ci1["portes"].values():
        retirer(base, p)
    erreurs, infos = [], []
    sol = ci1["sol"]
    entree = tuple(ci1["entree"])
    # l'escalier de la porte 2 : 46 marches, une a la fois, et toutes praticables
    marches = sorted((tuple(m) for m in ci1["marches"]["c2"]), key=lambda m: m[2])
    par_z = {}
    for (x, y, z) in marches:
        par_z.setdefault(z, set()).add(y)
    zs = sorted(par_z)
    if len(zs) != 46 or any(b - a != 1 or max(par_z[b]) != max(par_z[a]) - 1 for a, b in zip(zs, zs[1:])):
        erreurs.append(f"escalier de la porte 2 : {len(zs)} marches, ou une marche ne descend pas d'un cran")
    abimees = [m for m in marches if not debout(Terrain(base, sol), m)]
    if abimees:
        erreurs.append(f"{len(abimees)} marche(s) de l'escalier de la porte 2 abimee(s) (ex. {abimees[:3]})")
    if min(par_z[zs[-1]]) != ci3["yf2"] + 2:
        erreurs.append(f"la derniere marche n'est pas a la hauteur du sol de la salle -2 (+1) : {min(par_z[zs[-1]])}")
    # a pied : jusqu'a l'ile d'entree, pas plus
    t0 = Terrain(base, sol)
    a_pied = marcher(t0, [entree], 14)
    if tuple(ci3["entree"]) not in a_pied:
        erreurs.append(f"a pied, l'ile d'entree {tuple(ci3['entree'])} n'est pas atteignable depuis l'entree du donjon")
    for k, (x, y, z) in ((k, tuple(v)) for k, v in ci3["leviers"].items()):
        if a_cote(a_pied, (x, y, z)):
            erreurs.append(f"a pied, le levier {k} est atteignable")
    # etape par etape
    actifs = [tuple(a) for a in ci3["ancrages_bleus"]]
    blocs = dict(base)
    for (x, y, z) in actifs:
        pass                                                                                                  # les ancrages bleus sont deja dans la structure
    precedent = None
    lumieres_cyan = []
    for etape in range(0, 6):
        if etape > 0:
            for (x, y, z) in (tuple(a) for a in ci3["ancrages_cyan"][str(etape)]):                              # le levier `etape` est tire : ses ancrages apparaissent (ancrage et chaine)
                for yy in range(y + 1, ci3["plafond"] + 1):
                    blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
                blocs[(x, y, z)] = S("donjon:ancrage")
                actifs.append((x, y, z))
            for p in ci3["piliers_cyan"].get(str(etape), []):                                                   # un ancrage au dessus du vide arrive avec son pilier (le corps, le dessus et la lumiere)
                for (cx, cz) in p["cases"]:
                    for yy in range(ci3["y_fond"] + 2, ci3["yf2"]):
                        blocs[(cx, yy, cz)] = S("stone_bricks")
                for (cx, cz, nom) in p["dessus"]:
                    blocs[(cx, ci3["yf2"], cz)] = S(nom)
                blocs[tuple(p["lumiere"])] = S("light", level=ci3["lum_niveau"], waterlogged=False)
                lumieres_cyan.append(tuple(p["lumiere"]) + (ci3["lum_niveau"],))
        t = Terrain(blocs, sol)
        vu, en_l_air = fermeture(t, [entree], actifs)
        infos.append(f"etape {etape} : {len(vu)} cases atteignables, {en_l_air} points d'arrivee en l'air, {len(actifs)} ancrages")
        for p in ci3["piliers_cyan"].get(str(etape), []):                                                       # le pilier du levier tire est bien un point d'arrivee : on s'y tient debout
            ax, ay, az = p["ancrage"]
            if (ax, ci3["yf2"] + 1, az) not in vu:
                erreurs.append(f"etape {etape} : on n'arrive pas debout sur le pilier de l'ancrage {tuple(p['ancrage'])}")
        suivant = etape + 1
        cibles = {k: tuple(v) for k, v in ci3["leviers"].items()}
        if suivant <= 5:
            if not a_cote(vu, cibles[str(suivant)]):
                erreurs.append(f"etape {etape} : le levier {suivant} n'est pas atteignable")
            for k in range(suivant + 1, 6):
                if a_cote(vu, cibles[str(k)]):
                    erreurs.append(f"etape {etape} : le levier {k} est deja atteignable (l'ordre 1 a 5 n'est pas respecte)")
        cle = tuple(ci3["coffre_cle"])
        if etape < 5 and a_cote(vu, cle):
            erreurs.append(f"etape {etape} : le coffre de la cle 3 est deja atteignable (avant le levier 5)")
        if etape == 5:
            if not a_cote(vu, cle):
                erreurs.append("apres le levier 5, le coffre de la cle 3 n'est pas atteignable")
            for c in ci3["coffres"]:
                if not a_cote(vu, tuple(c)):
                    erreurs.append(f"apres le levier 5, le coffre jaune {tuple(c)} n'est pas atteignable")
            for nom, liste in ci3["ennemis"].items():
                for m in liste:
                    if not a_cote(vu, tuple(m)):
                        erreurs.append(f"apres le levier 5, l'emplacement d'ennemi {tuple(m)} ({nom}) n'est pas atteignable")
            sources = [tuple(s) for s in ci1["sources"]] + [tuple(s) for s in ci3["sources"]] + lumieres_cyan
            cases_vues = [c for c in vu if c[1] < sol and not (c[0] < 24 and c[1] > ci3["yf2"] + 3)]               # l'escalier (jusqu'a sa derniere marche) est controle par verif_donjon_suspendu
            noirs = [c for c, n in lumiere(t, sources, cases_vues).items() if n < SEUIL_LUMIERE]
            if noirs:
                erreurs.append(f"{len(noirs)} cases atteignables sous la lumiere {SEUIL_LUMIERE} (ex. {sorted(noirs)[:4]})")
        precedent = vu
    (exports / "suspendu_ss2_verif.json").write_text(json.dumps({"ok": not erreurs, "erreurs": erreurs, "etapes": infos}), encoding="utf-8")
    return erreurs, infos


if __name__ == "__main__":
    err, infos = verifier(RACINE / "exports")
    for ligne in infos:
        print(" ", ligne)
    print("RESULTAT", "OK" if not err else f"{len(err)} ERREUR(S)")
    for e in err[:30]:
        print(" -", e)
    sys.exit(0 if not err else 1)
