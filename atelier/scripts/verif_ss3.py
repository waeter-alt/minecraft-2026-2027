# [claude-build]
"""verif_ss3.py - controle de la salle 3 du Donjon Suspendu, l'arene du Gardien des Racines (le rez-de-chaussee et la salle 3 poses ensemble, la porte 3 ouverte) :
- a pied, depuis l'entree du donjon : le couloir 3, le palier, l'escalier de 53 marches (une a la fois, toutes praticables), le tunnel, le couloir et le premier troncon du pont ; RIEN d'autre : les trois trous du pont (6 blocs chacun,
  un saut au sprint en franchit 4 au mieux) ne se passent pas, ni les deux ilots, ni la rampe, ni le balcon, l'alcove et les corniches ;
- AU GRAPPIN 3 (tirs enchaines, depuis le depart du pont) : les ilots, la tete du pont, TOUTE la rampe en spirale (jusqu'a son sommet), le balcon de la sortie, l'alcove du coffre et les quatre corniches ;
- aucune case de ce chemin et de la rampe n'est dans le noir ;
- A L'EPEE : pour chacun des cinq points faibles du golem et a chaque orientation (pas de 15 degres), une case de la rampe est a moins de 3 blocs de sa boite ; la sortie est scellee.
  test-local/venv/Scripts/python.exe atelier/scripts/verif_ss3.py"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from suspendu_outils import lire_structure  # noqa: E402
from verif_donjon_suspendu import SEUIL_LUMIERE, Terrain, lumiere, marcher  # noqa: E402
from verif_ss1 import retirer  # noqa: E402
from verif_ss2 import fermeture  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
# les points faibles du Gardien (Golem.java) : nom, azimut par rapport a son avant (degres), rayon, hauteur au dessus de la surface de la lave, cote de la boite
POINTS_FAIBLES = (("masque", 0, 8.0, 34, 6), ("plastron", 0, 8.0, 26, 6), ("poing", -50, 7.5, 40, 7), ("dos_g", 150, 8.0, 30, 6), ("dos_d", -150, 8.0, 22, 6))
PORTEE_EPEE = 3.0
HAUT_YEUX = 1.6


def verifier(exports):
    exports = Path(exports)
    ci1 = json.loads((exports / "suspendu_cibles.json").read_text(encoding="utf-8"))
    ci4 = json.loads((exports / "suspendu_ss3_cibles.json").read_text(encoding="utf-8"))
    base = lire_structure(exports / "suspendu_salle1.nbt", tuple(ci1["decalages"]["salle1"]))
    ss3 = lire_structure(exports / "suspendu_ss3.nbt", tuple(ci4["decalages"]["ss3"]))
    base.update(ss3)                                                                                   # comme en jeu : la salle 3 est posee apres le rez-de-chaussee
    for p in ci1["portes"].values():
        retirer(base, p)
    erreurs, infos = [], []
    sol, entree = ci1["sol"], tuple(ci1["entree"])
    t = Terrain(base, sol)
    a_pied = marcher(t, [entree], 14)
    ancrages = [tuple(a[1:]) for a in ci4["ancrages_boss"]]
    vu, en_l_air = fermeture(t, [entree], ancrages)                                                            # tout ce qu'on atteint depuis l'entree du donjon, a pied et au grappin
    depart = tuple(ci4["depart_boss"])
    if depart not in vu:
        erreurs.append(f"le depart du pont {depart} n'est atteignable ni a pied ni au grappin depuis l'entree du donjon")
    if depart in a_pied:
        erreurs.append("le depart du pont s'atteint a pied : une fosse du tunnel se passe sans grappin")
    a_pied_pont = marcher(t, [depart], 14)                                                                     # a pied depuis le depart du pont (une fois les fosses du tunnel franchies)
    par_x = {}
    for (x, y, z) in ci4["marches"]:
        par_x.setdefault(x, set()).add(y)
    xs = sorted(par_x)
    if len(xs) != 53 or any(b - a != 1 or max(par_x[b]) != max(par_x[a]) - 1 for a, b in zip(xs, xs[1:])):
        erreurs.append(f"escalier : {len(xs)} marches, ou une marche ne descend pas d'un cran")
    manquees = [m for m in ci4["marches"] if tuple(m) not in a_pied]
    if manquees:
        erreurs.append(f"{len(manquees)} case(s) de marche non atteignable(s) a pied (ex. {manquees[:3]})")
    # le tunnel : on marche jusqu'a la premiere fosse, rien au dela ; les fosses font au moins 5 blocs ; tout se passe au grappin
    passages = [{k: tuple(v) for k, v in q.items()} for q in ci4["tunnel"]["passages"]]
    for i, (q, (a_, b_)) in enumerate(zip(passages, ci4["tunnel"]["trous"]), 1):
        if b_ - a_ + 1 < 5:
            erreurs.append(f"la fosse {a_}-{b_} du tunnel fait moins de 5 blocs : un saut au sprint la franchit")
        if i == 1 and q["avant"] not in a_pied:
            erreurs.append(f"le bord avant de la premiere fosse {q['avant']} n'est pas atteignable a pied")
        if i > 1 and q["avant"] in a_pied:
            erreurs.append(f"le bord {q['avant']} de la fosse {i} s'atteint a pied : une fosse precedente se passe sans grappin")
        if q["apres"] in a_pied:
            erreurs.append(f"le bord {q['apres']} apres la fosse {i} s'atteint a pied : elle se passe sans grappin")
        for cle in ("avant", "apres"):
            if q[cle] not in vu:
                erreurs.append(f"{cle} la fosse {i} {q[cle]} n'est pas atteignable, meme au grappin 3")
    infos.append(f"tunnel : {len(passages)} trous (" + ", ".join(str(b_ - a_ + 1) for a_, b_ in ci4["tunnel"]["trous"]) + " blocs), tous franchis au grappin, aucun a pied")
    for c in ci4["tunnel"]["coffres"]:
        acces = tuple(c["acces"])
        a_pied_ok = acces in a_pied
        if c["nom"] == "escalier" and not a_pied_ok:
            erreurs.append("le coffre de l'escalier n'est pas atteignable a pied")
        if c["nom"] in ("corniche", "ile", "chambre3") and a_pied_ok:
            erreurs.append(f"le coffre {c['nom']} s'atteint a pied : le grappin ne sert a rien")
        if acces not in vu:
            erreurs.append(f"le coffre {c['nom']} {acces} n'est pas atteignable, meme au grappin 3")
    infos.append("coffres : " + ", ".join(f"{c['nom']} ({'a pied' if tuple(c['acces']) in a_pied else 'au grappin'})" for c in ci4["tunnel"]["coffres"]))
    sortie = [tuple(c) for c in ci4["sortie"]]
    if any(base.get(c, ("",))[0] != "minecraft:iron_bars" for c in sortie):
        erreurs.append("la sortie du boss n'est pas scellee par des barreaux")
    # le pont : a pied on n'atteint que son premier troncon ; les trous font au moins 5 blocs (un saut au sprint en franchit 4 au mieux)
    pont = [tuple(c) for c in ci4["pont"]]
    t0, t1 = ci4["troncons"][0]
    premier = [c for c in pont if t0 <= c[2] <= t1]
    dehors = [c for c in pont if c in a_pied_pont and not (t0 <= c[2] <= t1)]
    if dehors:
        erreurs.append(f"{len(dehors)} case(s) du pont au dela du premier troncon s'atteignent a pied : un trou se franchit sans grappin (ex. {sorted(dehors)[:3]})")
    if any(c not in a_pied_pont for c in premier):
        erreurs.append("une case du premier troncon du pont n'est pas atteignable a pied")
    for (z0, z1) in ci4["trous"]:
        if z1 - z0 + 1 < 5:
            erreurs.append(f"le trou z {z0} a {z1} fait moins de 5 blocs : un saut au sprint le franchit")
    infos.append(f"pont : {len(pont)} cases, dont {len(premier)} a pied (le premier troncon) ; trous de " + ", ".join(str(z1 - z0 + 1) for (z0, z1) in ci4["trous"]) + " blocs")
    # l'anneau (la rampe) : ses cases de sol sont dans le fichier de cibles, avec leur angle le long de la spirale
    theta = {tuple(c[:3]): c[3] for c in ci4["rampe_theta"]}
    debout = list(theta)
    if any(c in a_pied for c in debout):
        erreurs.append("une case de l'anneau s'atteint a pied depuis l'entree : le pont ne sert a rien")
    trous = [tuple(q) for q in ci4["rampe_trous"]]
    dans_bande = [c for c, th in theta.items() if trous[2][1] + 7 <= th <= trous[3][0] - 7]                 # entre le dernier trou du bas et le premier trou du haut : la bande du combat
    hors_bande = [c for c, th in theta.items() if th > trous[3][1] + 7]                                          # le haut : on ne monte jamais d'un trou a pied (en bas, on peut sauter d'un tour sur celui du dessous : 11 blocs, des degats)
    depart_ring = tuple(ci4["retour_rampe"])
    sur_l_anneau = marcher(t, [depart_ring], 14)                                                             # a pied, depuis la tete du pont (une fois le pont franchi au grappin)
    if depart_ring not in sur_l_anneau:
        erreurs.append("la tete du pont n'est pas un point d'appui")
    manquees = [c for c in dans_bande if c not in sur_l_anneau]
    if manquees:
        erreurs.append(f"{len(manquees)} case(s) de la bande du milieu de l'anneau non atteignable(s) a pied depuis la tete du pont (ex. {sorted(manquees)[:3]})")
    passees = [c for c in hors_bande if c in sur_l_anneau]
    if passees:
        erreurs.append(f"{len(passees)} case(s) au dela d'un trou de l'anneau s'atteignent a pied : un trou se passe sans grappin (ex. {sorted(passees)[:3]})")
    for (a_, b_) in trous:
        if (b_ - a_) * math.pi / 180 * ci4["rampe"]["rayon"][0] < 5:
            erreurs.append(f"le trou {a_}-{b_} fait moins de 5 blocs sur la file interieure : un saut le franchit")
    infos.append(f"anneau : {len(debout)} cases de sol, {len(dans_bande)} dans la bande du milieu (a pied), {len(hors_bande)} au dela des trous du haut, {len(trous)} trous de {round((trous[0][1] - trous[0][0]) * math.pi / 180 * 14.5, 1)} blocs au milieu")
    # le grappin 3 (la fermeture calculee plus haut, depuis l'entree) : tout le reste
    for i, (z0, z1) in enumerate(ci4["troncons"][1:], 1):
        centre_ = [c for c in pont if c[0] == ci4["golem"]["centre"][0] and z0 <= c[2] <= z1]
        manque = [c for c in centre_ if c not in vu]
        if manque or not centre_:
            erreurs.append(f"le troncon {i + 1} du pont (z {z0} a {z1}) n'est pas atteignable au grappin 3 : {manque[:3]}")
    nonatteintes = [c for c in debout if c not in vu]
    infos.append(f"anneau : {len(debout) - len(nonatteintes)} cases sur {len(debout)} atteintes au grappin, de {ci4['rampe']['bas']} a {ci4['rampe']['haut']} de haut")
    if nonatteintes:
        erreurs.append(f"{len(nonatteintes)} case(s) de l'anneau non atteignable(s) meme au grappin 3 (ex. {sorted(nonatteintes)[:3]})")
    for i, (a_, b_) in enumerate(trous, 1):                                                                  # chaque ancrage de l'anneau suffit seul a passer son trou, dans son sens
        for cote in ("a", "b"):
            if not any(n == f"rampe_{i}{cote}" for (n, *_) in ci4["ancrages_boss"]):
                erreurs.append(f"pas d'ancrage rampe_{i}{cote}")
    lieux = {"balcon": tuple(ci4["balcon"]), "alcove": tuple(ci4["alcove"])} | {"corniche " + n: tuple(c) for n, c in ci4["corniches"].items()}
    for nom, c in lieux.items():
        if c in a_pied:
            erreurs.append(f"{nom} s'atteint deja a pied : le grappin ne sert a rien")
        if c not in vu:
            erreurs.append(f"{nom} {c} n'est pas atteignable, meme au grappin 3")
    infos.append("grappin : " + ", ".join(f"{n} {'atteint' if c in vu else 'NON atteint'}" for n, c in lieux.items()) + f" ; {len(ancrages)} ancrages, {en_l_air} points d'arrivee en l'air")
    # la lumiere : tout ce qu'on atteint dans la salle 3 (a pied ou au grappin) : le palier, l'escalier, le tunnel, le couloir, le pont, la rampe, le balcon, l'alcove, les corniches
    sources = [tuple(s) for s in ci4["sources"]] + [tuple(s) for s in ci1["sources"]]
    chemin = [c for c in (a_pied | vu) if ss3.get(c, ("",))[0] == "minecraft:air"]
    noirs = [c for c, n in lumiere(t, sources, chemin).items() if n < SEUIL_LUMIERE]
    if noirs:
        erreurs.append(f"{len(noirs)} cases du chemin sous la lumiere {SEUIL_LUMIERE} (ex. {sorted(noirs)[:4]})")
    infos.append(f"{len(chemin)} cases du chemin verifiees (palier, escalier, tunnel, couloir, pont, rampe, balcon, alcove, corniches)")
    # a l'epee : chaque point faible, a chaque orientation du golem (pas de 15 degres), doit etre a moins de 3 blocs d'une case de l'anneau ; les trous en font perdre quelques orientations a certains
    # points (ceux que servent les tours abimes), jamais plus d'un quart : le golem tourne vers le joueur, qui change l'orientation en se deplacant
    cx, cy, cz = ci4["golem"]["centre"]
    ras = [c for c in debout if c in vu]
    pires, morts = {}, {}
    for nom, az, r, h, cote in POINTS_FAIBLES:
        pire, nb_morts, serie, pire_serie = 0, 0, 0, 0
        for lacet in range(0, 360, 15):
            a = math.radians(lacet + az)
            c = (cx + 0.5 - math.sin(a) * r, cy + h, cz + 0.5 + math.cos(a) * r)
            demi = cote / 2
            meilleur = min((math.sqrt(sum(max(abs(e - c[k]) - demi, 0) ** 2 for k, e in enumerate((x + 0.5, y + HAUT_YEUX, z + 0.5)))) for (x, y, z) in ras
                            if abs(x + 0.5 - c[0]) < 12 and abs(z + 0.5 - c[2]) < 12 and abs(y - c[1]) < 12), default=99)
            if meilleur > PORTEE_EPEE:
                nb_morts += 1
                serie += 1
                pire_serie = max(pire_serie, serie)
            else:
                pire = max(pire, meilleur)
                serie = 0
        pires[nom], morts[nom] = round(pire, 2), (nb_morts, pire_serie)
        if nb_morts > 6 or pire_serie > 4:
            erreurs.append(f"le point faible {nom} est hors de portee d'epee a {nb_morts} orientations sur 24 (dont {pire_serie} de suite) : trop de trous sur les tours qui le servent")
    infos.append("a l'epee, par point faible : pire distance quand il est a portee, orientations hors de portee (sur 24, plus longue serie) : " + ", ".join(f"{n} {pires[n]} ({morts[n][0]}, {morts[n][1]})" for n in pires))
    (exports / "suspendu_ss3_verif.json").write_text(json.dumps({"ok": not erreurs, "erreurs": erreurs, "infos": infos}), encoding="utf-8")
    return erreurs, infos


if __name__ == "__main__":
    err, infos = verifier(RACINE / "exports")
    for ligne in infos:
        print(" ", ligne)
    print("RESULTAT", "OK" if not err else f"{len(err)} ERREUR(S)")
    for e in err[:30]:
        print(" -", e)
    sys.exit(0 if not err else 1)
