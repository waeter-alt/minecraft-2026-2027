# [claude-build]
"""
verif_ss1.py - controle a pied et au grappin du sous-sol 1 du Donjon Suspendu (le rez-de-chaussee et le sous-sol poses ensemble, comme en jeu : l'air du sous-sol creuse la roche) :
- portes du rez-de-chaussee ouvertes, porte bleue fermee : on atteint le bas de l'escalier (27 marches) et la petite salle des monstres, mais ni le passage, ni la grande salle, ni la salle de l'enigme ;
- porte bleue ouverte, A PIED : on atteint la grande salle (le coffre du grappin sur l'estrade) mais NI les plateformes des piliers, NI la corniche du bulbe, NI le hall, NI la salle de l'enigme (la zone haute) ;
- AU GRAPPIN 3 (le vol du crochet est simule tick par tick comme dans le mod : 2,5 blocs par tick sur 30 blocs, il s'arrete sur un bloc plein, et il s'accroche au premier ancrage qui frole sa trajectoire a moins de 1 bloc, meme si ce n'est pas celui qu'on visait ; accroche, on est tire EN LIGNE DROITE vers l'ancrage, on s'arrete a 1,2 bloc de lui, le crochet se detache et on tombe tout droit) :
  on monte de pilier en pilier jusqu'a la corniche, on traverse les deux fosses, on atteint la salle de l'enigme, les coffres, les monstres, et on ressort d'une fosse ;
- aucune case atteignable n'est dans le noir ; l'escalier de la porte 1 descend d'une marche a la fois, sur 27 marches, et le sous-sol n'en a abime aucune.
  test-local/venv/Scripts/python.exe atelier/scripts/verif_ss1.py
"""
import json
import math
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from suspendu_outils import lire_structure  # noqa: E402
from verif_donjon_suspendu import SEUIL_LUMIERE, Terrain, lumiere, marcher  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
PORTEE = 30.0              # le crochet vole a 30 blocs
ARRIVEE = 1.2              # grappin 3 : le centre du joueur s'arrete a 1,2 bloc du crochet, qui est colle a la face de l'ancrage (0,55 du centre de l'ancrage)
CRAN_CROCHET = 0.55
CHUTE = 14                 # on peut sauter dans une fosse (12 de profondeur) : des degats, mais on y survit
HAUT_YEUX = 1.62
VITESSE_CROCHET = 2.5      # blocs par tick
AIMANT = 1.0               # le crochet s'accroche a un ancrage qu'il frole a moins de 1 bloc de sa surface (grappin 3 ; 1,5 avant)
DECALAGES_CROCHET = ((0, 0, 0), (0.125, 0, 0), (-0.125, 0, 0), (0, 0.125, 0), (0, -0.125, 0), (0, 0, 0.125), (0, 0, -0.125))     # le crochet a une boite de 0,25


def retirer(blocs, cible):
    """Les portes ouvertes : on retire la dalle et sa spirale."""
    for (x, z) in cible["cases"]:
        for y in range(cible["y"][0], cible["y"][1] + 1):
            if blocs.get((x, y, z), ("",))[0] in cible["blocs"]:
                blocs[(x, y, z)] = ("minecraft:air", ())


def voit(t, a, b, ignorer):
    """Ligne de vue de a a b (points flottants) : aucun bloc plein entre les deux (hors la case `ignorer`, l'ancrage lui-meme)."""
    d = math.dist(a, b)
    n = max(2, int(d / 0.25))
    for i in range(1, n):
        p = (a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n, a[2] + (b[2] - a[2]) * i / n)
        c = (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))
        if c != ignorer and t.solide(c):
            return False
    return True


def centre(a):
    return (a[0] + 0.5, a[1] + 0.5, a[2] + 0.5)


def debout(t, c):
    return not t.solide(c) and not t.solide((c[0], c[1] + 1, c[2])) and t.solide((c[0], c[1] - 1, c[2]))


def atterrissage(t, s, a):
    """Grappin 3 : tire de la case s sur l'ancrage a, on est tire en ligne droite, on s'arrete quand le centre du joueur est a ARRIVEE du crochet, puis on tombe tout droit : la case ou l'on se retrouve (ou None)."""
    oeil = (s[0] + 0.5, s[1] + HAUT_YEUX, s[2] + 0.5)
    ca = centre(a)
    longueur = math.dist(oeil, ca)
    u = tuple((ca[i] - oeil[i]) / longueur for i in range(3))
    p = tuple(ca[i] - u[i] * (CRAN_CROCHET + ARRIVEE) for i in range(3))         # le centre du joueur a l'arrivee
    if not voit(t, (s[0] + 0.5, s[1] + 0.9, s[2] + 0.5), p, a):
        return None
    x, y, z = math.floor(p[0]), math.floor(p[1] - 0.9 + 1e-6), math.floor(p[2])
    while y > 1:
        cell = (x, y, z)
        if t.solide(cell):
            return None
        if t.solide((x, y - 1, z)) and not t.solide((x, y + 1, z)):
            return cell
        y -= 1
    return None


def bloque(t, c, q):
    """Le point q (dans la case c) est-il dans un bloc plein ? Un muret (_wall) monte a 1,5 : il bloque aussi la moitie basse de la case au dessus."""
    if t.solide(c):
        return True
    dessous = t.blocs.get((c[0], c[1] - 1, c[2]))
    return dessous is not None and dessous[0].endswith(("_wall", "_fence")) and q[1] - math.floor(q[1]) < 0.5


def lance(t, oeil, cible, ancrages):
    """L'ancrage auquel s'accroche le crochet lance de `oeil` droit vers le centre de `cible` (ou None), comme Crochet.java : a chaque tick il avance de 2,5 blocs ; un bloc plein l'arrete (un ancrage
    l'accroche, tout autre bloc le supprime) ; sinon le premier ancrage visible a moins de 1,5 bloc de la trajectoire du tick l'accroche, le plus proche d'abord."""
    ca = centre(cible)
    longueur = math.dist(oeil, ca)
    if longueur < 1e-6:
        return None
    u = tuple((ca[i] - oeil[i]) / longueur for i in range(3))
    centres = {tuple(a): centre(a) for a in ancrages}
    p0 = oeil
    for _ in range(int(PORTEE / VITESSE_CROCHET) + 1):
        p1 = tuple(p0[i] + u[i] * VITESSE_CROCHET for i in range(3))
        for k in range(1, int(VITESSE_CROCHET / 0.2) + 1):                          # collision le long du tick
            f = k * 0.2 / VITESSE_CROCHET
            for o in DECALAGES_CROCHET:
                q = tuple(p0[i] + (p1[i] - p0[i]) * f + o[i] for i in range(3))
                c = (math.floor(q[0]), math.floor(q[1]), math.floor(q[2]))
                if bloque(t, c, q):
                    return c if c in centres else None
        d = tuple(p1[i] - p0[i] for i in range(3))
        lg2 = d[0] ** 2 + d[1] ** 2 + d[2] ** 2
        meilleur, dmin = None, AIMANT
        for a, cc in centres.items():
            s = max(0.0, min(1.0, ((cc[0] - p0[0]) * d[0] + (cc[1] - p0[1]) * d[1] + (cc[2] - p0[2]) * d[2]) / lg2))
            q = (p0[0] + d[0] * s, p0[1] + d[1] * s, p0[2] + d[2] * s)
            dd = math.dist(cc, q) - 0.5
            if dd < dmin and voit(t, q, cc, a):
                meilleur, dmin = a, dd
        if meilleur is not None:
            return meilleur
        p0 = p1
    return None


def cases_de_tir(t, vu, a, ancrages):
    """Les cases de `vu` d'ou un tir droit vers l'ancrage a l'accroche bien (et pas un autre)."""
    ca = centre(a)
    res = []
    for c in vu:
        if abs(c[0] - a[0]) <= PORTEE and abs(c[2] - a[2]) <= PORTEE and abs(c[1] - a[1]) <= PORTEE:
            oeil = (c[0] + 0.5, c[1] + HAUT_YEUX, c[2] + 0.5)
            if math.dist(oeil, ca) <= PORTEE and lance(t, oeil, a, ancrages) == tuple(a):
                res.append(c)
    return res


def tirable(t, vu, a, ancrages):
    """Vrai si une case de `vu` accroche l'ancrage a d'un tir droit."""
    return bool(cases_de_tir(t, vu, a, ancrages))


def au_grappin(t, depart, ancrages):
    """Tout ce qu'on atteint depuis `depart` avec le grappin : (cases, ancrages utilises dans l'ordre). On ne tire que depuis une case ou l'on se tient ; chaque tir mene a UNE case d'arrivee."""
    vu = marcher(t, depart, CHUTE)
    ordre = []
    restant = [tuple(a) for a in ancrages]
    changement = True
    while changement:
        changement = False
        for a in list(restant):
            tirs = cases_de_tir(t, vu, a, ancrages)
            if not tirs:
                continue
            restant.remove(a)
            ordre.append(a)
            arrivees = {l for l in (atterrissage(t, s, a) for s in tirs) if l is not None} - vu
            if arrivees:
                vu |= marcher(t, list(arrivees), CHUTE)
            changement = True
    return vu, ordre


def verifier(exports):
    exports = Path(exports)
    ci1 = json.loads((exports / "suspendu_cibles.json").read_text(encoding="utf-8"))
    ci2 = json.loads((exports / "suspendu_ss1_cibles.json").read_text(encoding="utf-8"))
    blocs = lire_structure(exports / "suspendu_salle1.nbt", tuple(ci1["decalages"]["salle1"]))
    blocs.update(lire_structure(exports / "suspendu_ss1.nbt", tuple(ci2["decalages"]["ss1"])))                 # comme en jeu : posee apres, elle creuse
    for p in ci1["portes"].values():
        retirer(blocs, p)
    erreurs = []
    sol = ci1["sol"]
    entree = tuple(ci1["entree"])
    t_ferme = Terrain(blocs, sol)
    vu_a = marcher(t_ferme, [entree])
    for nom in ("arrivee", "centre_petite"):
        if tuple(ci2[nom]) not in vu_a:
            erreurs.append(f"porte bleue fermee : {nom} {tuple(ci2[nom])} n'est pas atteignable")
    for nom in ("coffre_grappin", "centre_musicale", "hall_ouest", "corniche"):
        if tuple(ci2[nom]) in vu_a:
            erreurs.append(f"porte bleue fermee : {nom} est atteignable (la porte ne ferme rien)")
    # l'escalier de la porte 1 : 27 marches, une de moins a chaque pas vers le sud, et toutes praticables apres la pose du sous-sol
    marches = sorted((m for m in ci1["marches"]["c1"]), key=lambda m: m[2])
    par_z = {}
    for (x, y, z) in marches:
        par_z.setdefault(z, set()).add(y)
    zs = sorted(par_z)
    if len(zs) != 27 or any(b - a != 1 or max(par_z[b]) != max(par_z[a]) - 1 for a, b in zip(zs, zs[1:])):
        erreurs.append(f"escalier de la porte 1 : {len(zs)} marches, ou une marche ne descend pas d'un cran")
    if min(y for y in par_z[zs[-1]]) != ci2["yf"] + 2:
        erreurs.append(f"escalier de la porte 1 : la derniere marche n'est pas a la hauteur du sol du sous-sol 1 (+1) : {min(par_z[zs[-1]])}")
    toutes = [tuple(m) for nom in ci1["marches"] for m in ci1["marches"][nom]]
    abimees = [m for m in toutes if not debout(t_ferme, m)]
    if abimees:
        erreurs.append(f"{len(abimees)} marche(s) des escaliers du rez-de-chaussee abimee(s) par le sous-sol (ex. {sorted(abimees)[:3]})")
    # porte bleue ouverte : a pied
    ouvert = dict(blocs)
    retirer(ouvert, ci2["porte_m1"])
    t_ouvert = Terrain(ouvert, sol)
    vu_b = marcher(t_ouvert, [entree], CHUTE)
    if tuple(ci2["coffre_grappin"]) not in vu_b:
        erreurs.append(f"porte bleue ouverte : coffre_grappin {tuple(ci2['coffre_grappin'])} n'est pas atteignable")
    interdits = ["corniche", "hall_ouest", "hall_milieu", "hall_est", "centre_musicale", "coffre_cle"]
    for nom in interdits:
        if tuple(ci2[nom]) in vu_b:
            erreurs.append(f"sans le grappin, {nom} est atteignable : la zone haute n'est pas fermee")
    for c in ci2["plateformes"]:
        if tuple(c) in vu_b:
            erreurs.append(f"sans le grappin, la plateforme du pilier {tuple(c)} est atteignable")
    for m in ci2["monstres_salle_musicale"]:
        if tuple(m) in vu_b:
            erreurs.append(f"sans le grappin, l'emplacement de monstre {tuple(m)} de la salle de l'enigme est atteignable")
    haut = ci2["sol_haut"]
    escaliers = {tuple(m) for nom in ci1["marches"] for m in ci1["marches"][nom]}                                # les marches du rez-de-chaussee montent plus haut que la zone haute : on ne les compte pas
    hautes = [c for c in vu_b if haut < c[1] < sol and c not in escaliers]
    if hautes:
        erreurs.append(f"sans le grappin, {len(hautes)} case(s) de la zone haute sont atteignables (ex. {sorted(hautes)[:3]})")
    # au grappin
    vu_c, ordre = au_grappin(t_ouvert, [entree], ci2["ancrages"])
    for nom in ["coffre_grappin", *interdits]:
        if tuple(ci2[nom]) not in vu_c:
            erreurs.append(f"au grappin : {nom} {tuple(ci2[nom])} n'est pas atteignable")
    for c in ci2["plateformes"]:
        if tuple(c) not in vu_c:
            erreurs.append(f"au grappin : la plateforme du pilier {tuple(c)} n'est pas atteignable")
    for m in ci2["monstres"]:
        if tuple(m) not in vu_c:
            erreurs.append(f"au grappin : l'emplacement de monstre {tuple(m)} n'est pas atteignable")
    en = ci2["enigme"]                                                                                         # l'enigme du bloc musical : le bouton de depart et chaque bloc musical se touchent, et chaque bloc a de l'air juste au dessus (sinon il se tait)
    if tuple(en["depart"]) not in vu_c:
        erreurs.append(f"le bouton de depart de l'enigme {tuple(en['depart'])} n'est pas atteignable")
    for (x, y, z) in en["pads"]:
        if ouvert.get((x, y + 1, z), ("",))[0] != "minecraft:air":
            erreurs.append(f"le bloc musical {(x, y, z)} a un bloc juste au dessus : il resterait muet")
        if not any((x + dx, y, z + dz) in vu_c for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            erreurs.append(f"le bloc musical {(x, y, z)} n'a aucune case atteignable a cote")
    for c in ci2["coffres"]:
        if not any((c[0] + dx, c[1], c[2] + dz) in vu_c for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) and (c[0], c[1], c[2]) not in vu_c:
            erreurs.append(f"au grappin : le coffre {tuple(c)} n'a aucune case atteignable a cote")
    if len(ordre) != len(ci2["ancrages"]):
        erreurs.append(f"au grappin : {len(ci2['ancrages']) - len(ordre)} ancrage(s) jamais atteint(s)")
    # le sol de la grande salle ne voit pas l'ancrage de la corniche (le rebord le cache) : il faut etre en haut d'un pilier pour le tirer ; en revanche on tire l'ancrage du pilier le plus proche depuis le sol
    corniche_a = tuple(ci2["ancrages"][5])
    if tirable(t_ouvert, vu_b, corniche_a, ci2["ancrages"]):
        erreurs.append("a pied, on tire deja l'ancrage de la corniche depuis le sol : les piliers ne servent a rien")
    if not tirable(t_ouvert, vu_b, tuple(ci2["ancrages"][1]), ci2["ancrages"]):
        erreurs.append("a pied, on ne voit pas l'ancrage du pilier du nord depuis le sol : le parcours ne commence pas")
    # demande de Luca : l'estrade du coffre du grappin est assez haute pour qu'on atteigne de la les differents piliers (les ancrages 0 a 4 sont les piliers du dessin, les deux derniers les piliers ajoutes)
    cx_c, cy_c, cz_c = ci2["coffre_grappin"]
    sur_l_estrade = [c for c in vu_b if abs(c[0] - cx_c) <= 20 and 88 <= c[2] <= cz_c and cy_c - 1 <= c[1] <= cy_c + 1]
    if len(sur_l_estrade) < 100:
        erreurs.append(f"l'estrade du grappin est trop petite ou inatteignable ({len(sur_l_estrade)} cases)")
    piliers = [tuple(a) for a in ci2["ancrages"][:5]] + [tuple(a) for a in ci2["ancrages"][8:]]
    visibles = [a for a in piliers if cases_de_tir(t_ouvert, sur_l_estrade, a, ci2["ancrages"])]
    if len(visibles) < 4:
        erreurs.append(f"de l'estrade du grappin on ne tire que {len(visibles)} pilier(s) sur {len(piliers)} (il en faut au moins 4)")
    # on monte vraiment de pilier en pilier : la corniche exige un ancrage du bas puis un pilier (jamais depuis le sol directement a l'ancrage de la corniche)
    sans_corniche, _ = au_grappin(t_ouvert, [entree], [a for a in ci2["ancrages"] if tuple(a) != tuple(ci2["ancrages"][5])])
    if tuple(ci2["corniche"]) in sans_corniche:
        erreurs.append("sans l'ancrage de la corniche, la corniche est quand meme atteignable")
    # la fosse : on peut y tomber (des degats), et le fond ne mene nulle part a pied
    for (a, b), anc, (ex, bas, haut_e, ez) in zip(ci2["fosses"], ci2["ancrages"][6:], ci2["echelles"]):
        fond = [c for c in vu_c if a[0] <= c[0] <= b[0] and min(a[1], b[1]) <= c[2] <= max(a[1], b[1]) and c[1] < haut]
        if not fond:
            erreurs.append(f"le fond de la fosse {a} n'est pas atteignable (on peut y tomber)")
        if (ex + 1, bas, ez) not in vu_c:
            erreurs.append(f"le pied de l'echelle de la fosse {a} n'est pas atteignable")
        if any(ouvert.get((ex, y, ez), ("",))[0] != "minecraft:ladder" for y in range(bas, haut_e + 1)):
            erreurs.append(f"l'echelle de la fosse {a} n'est pas continue du fond a la rive")
        if (ex - 1, haut_e + 1, ez) not in vu_c:
            erreurs.append(f"le haut de l'echelle de la fosse {a} ne donne pas sur la rive ouest")
        ouest = [c for c in vu_c if c[0] == a[0] - 1 and c[1] >= haut + 1 and min(a[1], b[1]) <= c[2] <= max(a[1], b[1])]
        if not tirable(t_ouvert, ouest, tuple(anc), ci2["ancrages"]):                            # du bord ouest on tire l'ancrage de la rive est : c'est la traversee
            erreurs.append(f"du bord ouest de la fosse {a}, on ne peut pas tirer l'ancrage {tuple(anc)} de la rive est")
    sources = [tuple(s) for s in ci1["sources"]] + [tuple(s) for s in ci2["sources"]]
    cases_vues = [c for c in vu_c if c[1] < sol]
    noirs = [c for c, n in lumiere(t_ouvert, sources, cases_vues).items() if n < SEUIL_LUMIERE]
    if noirs:
        erreurs.append(f"{len(noirs)} cases atteignables sous la lumiere {SEUIL_LUMIERE} (ex. {sorted(noirs)[:4]})")
    (exports / "suspendu_ss1_verif.json").write_text(json.dumps({"ok": not erreurs, "erreurs": erreurs, "cases_porte_fermee": len(vu_a), "cases_a_pied": len(vu_b), "cases_au_grappin": len(vu_c),
                                                                   "ancrages_dans_l_ordre": [list(a) for a in ordre]}), encoding="utf-8")
    return erreurs


if __name__ == "__main__":
    err = verifier(RACINE / "exports")
    print("RESULTAT", "OK" if not err else f"{len(err)} ERREUR(S)")
    for e in err[:30]:
        print(" -", e)
    sys.exit(0 if not err else 1)
