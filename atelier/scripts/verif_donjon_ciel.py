# [claude-build]
"""
verif_donjon_ciel.py - Controle de l'Archipel des Nuees : ce qu'on atteint, etape par etape, avec la paravoile, et rien d'autre.
Deplacements a pied : marche, marches de 1, chutes de 6 au plus, echelles. Vol : la paravoile (mod Grappin 2.6.0) est simulee avec ses constantes par defaut :
jauge de 10 s, 0,36 bloc/tick (7,2 blocs/s) en avant, 0,09 bloc/tick de descente a plat, soit une finesse de 4 pour 1 : un vol de L blocs fait perdre au moins L/4 de hauteur.
Deux niveaux de vol : « realiste » (un joueur moyen : L <= 60, chute entre L/3 et L, trajet libre de tout bloc), qui doit suffire a finir le donjon ;
« genereux » (vol parfait : L <= 80, chute au moins L/4, sans tenir compte des obstacles), qui ne doit ouvrir aucun raccourci.
Controles : (1) avec le realiste, tout ce qui est prevu s'atteint et la chambre du boss reste fermee ; (2) a chaque etape, le genereux n'atteint rien de ce que le realiste
n'atteint pas encore ; (3) aucune ile n'est sautee (vol genereux vers une ile plus loin que la suivante) ; (4) sans paravoile, on ne quitte pas le Debarcadere ;
(5) aucune impasse : de toute ile atteinte, on peut rejoindre l'arene du boss.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\verif_donjon_ciel.py
"""
import json
import math
import sys
from collections import deque
from pathlib import Path

import nbtlib
import numpy as np

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/ciel_cibles.json").read_text(encoding="utf-8"))
f = nbtlib.load(RACINE / "exports/ciel.nbt")
SX, SY, SZ = (int(v) for v in f["size"])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
DECOR_PASSE = {"chain", "candle", "fern", "large_fern", "tall_grass", "rose_bush", "lilac", "peony", "sunflower", "poppy", "dandelion", "blue_orchid", "allium", "azure_bluet",
               "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "lightning_rod"}
PASSE = {"air", "light", "lever", "stone_button", "oak_wall_sign", "short_grass", "lantern", "ladder", "tripwire_hook"}
SOL0, LAD = set(), set()
for b in f["blocks"]:
    p = tuple(int(v) for v in b["pos"])
    n = pal[int(b["state"])]
    if n == "ladder":
        LAD.add(p)
    elif n not in PASSE and not n.endswith(("_carpet", "_banner", "_wall_banner")) and n not in DECOR_PASSE and not n.startswith("potted_"):
        SOL0.add(p)

PORTES = conf["portes"]
GATE_CELLS = {nom: [tuple(c) for c in p["cases"]] for nom, p in PORTES.items()}
TOUTES_CASES = {c for cs in GATE_CELLS.values() for c in cs}
SOL_BASE = SOL0 - TOUTES_CASES
ILES = conf["iles"]
ORDRE = conf["ordre"]
POINTS = {k: tuple(v) for k, v in conf["points"].items()}
DEPART = tuple(conf["depart"])
VITESSE_MAX = 7.2
JAUGE = 10.0


def sol_etat(ouvertes):
    return SOL_BASE | {c for nom, cs in GATE_CELLS.items() if nom not in ouvertes for c in cs}


def ile_de(x, z):
    meilleur, nom = 9.0, None
    for k, i in ILES.items():
        ax, bz = i["rect"] if i["rect"] else (i["r"], i["r"])
        d = max(abs(x - i["cx"]) / (ax * 1.3 + 6), abs(z - i["cz"]) / (bz * 1.3 + 6)) if i["rect"] else math.hypot(x - i["cx"], z - i["cz"]) / (i["r"] * 1.3 + 6)
        if d < meilleur and d <= 1:
            meilleur, nom = d, k
    return nom


class Monde:
    """Le monde a un instant donne : cases solides (portes fermees comprises), marche a pied."""

    def __init__(self, ouvertes):
        self.SOL = sol_etat(ouvertes)

    def libre(self, c):
        x, y, z = c
        return 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ and c not in self.SOL

    def valide(self, c):
        x, y, z = c
        return self.libre(c) and self.libre((x, y + 1, z))

    def appui(self, c):
        x, y, z = c
        return (x, y - 1, z) in self.SOL or c in LAD or (x, y - 1, z) in LAD

    def voisins(self, c):
        x, y, z = c
        out = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            n = (nx, y, nz)
            if self.valide(n):
                if self.appui(n):
                    out.append(n)
                else:
                    yy = y
                    while not self.appui((nx, yy, nz)) and self.valide((nx, yy - 1, nz)) and y - yy <= 6:
                        yy -= 1
                    if self.appui((nx, yy, nz)) and y - yy <= 6:
                        out.append((nx, yy, nz))
            u = (nx, y + 1, nz)
            if self.valide(u) and self.libre((x, y + 2, z)) and (nx, y, nz) in self.SOL:
                out.append(u)
        if c in LAD or (x, y - 1, z) in LAD:
            if self.valide((x, y + 1, z)) and (x, y + 1, z) in LAD:
                out.append((x, y + 1, z))
            if self.valide((x, y - 1, z)):
                out.append((x, y - 1, z))
        if (x, y, z) in LAD and self.valide((x, y + 1, z)) and (x, y + 1, z) not in LAD:
            out.append((x, y + 1, z))
        return out

    def marche(self, departs, vu=None):
        vu = set() if vu is None else vu
        file_ = deque()
        for c in departs:
            if c not in vu and self.valide(c):
                vu.add(c)
                file_.append(c)
        while file_:
            c = file_.popleft()
            for n in self.voisins(c):
                if n not in vu:
                    vu.add(n)
                    file_.append(n)
        return vu


MONDE_OUVERT = Monde(set(PORTES) - {"SORTIE"})
# cases ou l'on peut se tenir, dans le monde entierement ouvert : candidates au decollage et a l'atterrissage
STAND = [(x, y + 1, z) for (x, y, z) in MONDE_OUVERT.SOL if y + 3 < SY and MONDE_OUVERT.valide((x, y + 1, z)) and MONDE_OUVERT.libre((x, y + 3, z))]
def ciel_ouvert(c):
    """A ciel ouvert : rien au dessus sur 8 cases, et au moins 4 des 8 colonnes voisines aussi (on exclut le fond d'une cage d'escalier, que le vol ne peut pas atteindre)."""
    x, y, z = c
    if any((x, y + k, z) in MONDE_OUVERT.SOL for k in range(1, 9)):
        return False
    libres = sum(all((x + dx, y + k, z + dz) not in MONDE_OUVERT.SOL for k in range(1, 9)) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dz) != (0, 0))
    return libres >= 4


STAND = [c for c in STAND if ciel_ouvert(c)]
ACCES = MONDE_OUVERT.marche([DEPART] + [tuple(v) for v in POINTS.values()])                                      # cases ou l'on peut marcher (pas le sommet d'un parapet ni d'un pilier)
STAND = [c for c in STAND if c in ACCES]
ILE_DE = {c: ile_de(c[0], c[2]) for c in STAND}
STAND = [c for c in STAND if ILE_DE[c]]
print(f"cases a ciel ouvert : {len(STAND)}", flush=True)


def est_bord(c):
    """Case de decollage : un cote donne sur le vide, et en ligne droite de ce cote rien ne barre le passage sur 14 cases (on est dehors, pas dans une tour)."""
    x, y, z = c
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = (x + dx, y, z + dz)
        if MONDE_OUVERT.valide(n) and not MONDE_OUVERT.appui(n) and all(MONDE_OUVERT.valide((x + dx * k, y, z + dz * k)) for k in range(1, 15)):
            return True
    return False


BORDS = [c for c in STAND if est_bord(c)]
ATTERR = {}
for c in STAND:
    if (c[0] + c[2]) % 2 == 0:
        ATTERR.setdefault(ILE_DE[c], []).append(c)
ATT = {k: np.array(v, dtype=np.int32) for k, v in ATTERR.items()}
print(f"decollages : {len(BORDS)} | iles avec atterrissage : {len(ATT)}", flush=True)


ANNEAUX = [tuple(a) for a in conf.get("anneaux", [])]
DEMI = conf.get("demi_anneau", 4)


def echantillons(a, b, via):
    """Points (x, y, z) (pieds, flottants) du trajet de a a b : un palier de 1,5 bloc a hauteur constante, puis une descente lineaire en suivant les points de passage."""
    ax, ay, az = a[0] + 0.5, float(a[1]), a[2] + 0.5
    bx, by, bz = b[0] + 0.5, float(b[1]), b[2] + 0.5
    pts = [(ax, az)] + [(vx + 0.5, vz + 0.5) for vx, vz in via] + [(bx, bz)]
    d0 = math.hypot(pts[1][0] - ax, pts[1][1] - az) or 1.0
    start = (ax + (pts[1][0] - ax) / d0 * 1.5, az + (pts[1][1] - az) / d0 * 1.5)
    chemin = [start] + pts[1:]
    total = sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip(chemin[:-1], chemin[1:]))
    out = []
    for t in np.arange(0.0, 1.5, 0.5):
        out.append((ax + (start[0] - ax) * t / 1.5, ay, az + (start[1] - az) * t / 1.5))
    cumul = 0.0
    for p, q in zip(chemin[:-1], chemin[1:]):
        seg = math.hypot(q[0] - p[0], q[1] - p[1])
        n = max(1, int(seg / 0.5))
        for i in range(n):
            t = i / n
            out.append((p[0] + (q[0] - p[0]) * t, ay + (by - ay) * (cumul + seg * t) / (total or 1.0), p[1] + (q[1] - p[1]) * t))
        cumul += seg
    return out


def passe_anneaux(a, b, via):
    """Le trajet traverse-t-il, dans l'ordre, l'ouverture de chacun des anneaux de vol (la zone detectee : 1 de profondeur, 5 de large, 5 de haut) ?"""
    pts = echantillons(a, b, via)
    for (ax_, az_, ay_) in ANNEAUX:
        ok = False
        for (px, py, pz) in pts:
            if ax_ - 0.3 < px < ax_ + 1.3 and az_ - DEMI < pz + 0.3 and pz - 0.3 < az_ + DEMI + 1 and ay_ - DEMI < py + 1.8 and py < ay_ + DEMI + 1:
                ok = True
                break
        if not ok:
            return False
    return True


def libre_chemin(a, b, via):
    """Le trajet de a a b (par les points `via`, en (x, z)) est-il libre de tout bloc ? Une glisse droite de a vers b, en commencant 1,5 bloc au dela du bord de a, puis
    en descendant lineairement ; le joueur occupe 0,6 x 1,8 x 0,6."""
    ax, ay, az = a[0] + 0.5, float(a[1]), a[2] + 0.5
    bx, by, bz = b[0] + 0.5, float(b[1]), b[2] + 0.5
    pts = [(ax, az)] + [(vx + 0.5, vz + 0.5) for vx, vz in via] + [(bx, bz)]
    d0 = math.hypot(pts[1][0] - ax, pts[1][1] - az) or 1.0
    start = (ax + (pts[1][0] - ax) / d0 * 1.5, az + (pts[1][1] - az) / d0 * 1.5)
    chemin = [start] + pts[1:]
    total = sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip(chemin[:-1], chemin[1:]))
    if total < 1e-6:
        return True
    cumul = 0.0
    S = MONDE_OUVERT.SOL
    # d'abord le palier de 1,5 bloc a hauteur constante
    for t in np.arange(0.0, 1.5, 0.5):
        px, pz = ax + (start[0] - ax) * t / 1.5, az + (start[1] - az) * t / 1.5
        if not _cases_libres(S, px, ay, pz):
            return False
    for p, q in zip(chemin[:-1], chemin[1:]):
        seg = math.hypot(q[0] - p[0], q[1] - p[1])
        n = max(1, int(seg / 0.5))
        for i in range(n):
            t = i / n
            px, pz = p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t
            py = ay + (by - ay) * (cumul + seg * t) / total
            if not _cases_libres(S, px, py, pz):
                return False
        cumul += seg
    return True


def _cases_libres(S, px, py, pz):
    y0, y1 = math.floor(py + 1e-6), math.floor(py + 1.8 - 1e-6)
    for x in (math.floor(px - 0.3), math.floor(px + 0.3)):
        for z in (math.floor(pz - 0.3), math.floor(pz + 0.3)):
            for y in range(y0, y1 + 1):
                if (x, y, z) in S:
                    return False
    return True


# ------------------------------------------------------------------ les vols : realiste (avec obstacles) et genereux (distance et hauteur seulement)
VIA = {tuple(k.split(">")): v for k, v in conf["vols_chemins"].items()}
VOLS_R, VOLS_G = {}, {}                                            # case de decollage -> cases d'atterrissage
PAIRES_G, PAIRES_R, MEILLEUR = set(), set(), {}
for a in BORDS:
    ia = ILE_DE[a]
    for ib, arr in ATT.items():
        if ib == ia:
            continue
        dx, dz = arr[:, 0] - a[0], arr[:, 2] - a[2]
        L = np.hypot(dx, dz)
        D = a[1] - arr[:, 1]
        mg = (L <= 80) & (D >= L / 4) & (L > 0)
        if mg.any():
            VOLS_G.setdefault(a, []).extend(tuple(int(v) for v in arr[i]) for i in np.nonzero(mg)[0][:4])
            PAIRES_G.add((ia, ib))
        via = VIA.get((ia, ib), [])
        if via:
            Lr = np.array([sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip([(a[0], a[2])] + via, via + [(int(t[0]), int(t[2]))])) for t in arr])
        else:
            Lr = L
        mr = (Lr <= 60) & (D >= Lr / 3) & (D <= Lr) & (Lr > 3)
        if mr.any():
            idx = np.nonzero(mr)[0]
            idx = idx[np.argsort(Lr[idx])][:10]
            bons = []
            for i in idx:
                b = tuple(int(v) for v in arr[i])
                if libre_chemin(a, b, via) and (not ANNEAUX or (ia, ib) != ("RL", "CP") or passe_anneaux(a, b, via)):
                    bons.append(b)
                    clef = (ia, ib)
                    if clef not in MEILLEUR or (Lr[i] > MEILLEUR[clef]["L"]):
                        MEILLEUR[clef] = {"L": round(float(Lr[i]), 1), "chute": int(D[i]), "de": list(a), "vers": list(b)}
                    if len(bons) >= 3:
                        break
            if bons:
                VOLS_R.setdefault(a, []).extend(bons)
                PAIRES_R.add((ia, ib))
print(f"vols realistes : {len(PAIRES_R)} paires d'iles | genereux : {len(PAIRES_G)}", flush=True)


# ------------------------------------------------------------------ progression : etape par etape, avec les leviers atteints
def portee(vu, lev):
    lx, ly, lz = lev
    return any((lx + dx, ly + dy, lz + dz) in vu for dx in range(-2, 3) for dy in range(-3, 1) for dz in range(-2, 3))


def atteint(ouvertes, vols):
    m = Monde(ouvertes)
    vu = m.marche([DEPART])
    while True:
        neufs = [b for a in list(vu) if a in vols for b in vols[a] if b not in vu]
        if not neufs:
            return vu
        m.marche(neufs, vu)


def pres(vu, c, r=1):
    cx, cy, cz = c
    return any((cx + dx, cy + dy, cz + dz) in vu for dx in range(-r, r + 1) for dy in range(-r, r + 1) for dz in range(-r, r + 1))


erreurs = []
etapes, ouvertes = [], set()
exclues = {"SORTIE"}
while True:
    vu_r = atteint(ouvertes, VOLS_R)
    nouvelles = [g for g, p in PORTES.items() if g not in ouvertes and g not in exclues and p["leviers"] and all(portee(vu_r, tuple(lv)) for lv in p["leviers"])]
    etapes.append({"ouvertes": sorted(ouvertes), "vu_r": vu_r})
    if not nouvelles:
        break
    ouvertes |= set(nouvelles)
print(f"etapes : {len(etapes)} | portes ouvertes dans l'ordre : {[e['ouvertes'] for e in etapes[1:]][-1:]}", flush=True)
fermees = sorted(set(PORTES) - ouvertes - exclues)
if fermees:
    erreurs.append(f"portes jamais ouvertes : {fermees}")
VU_FINAL = etapes[-1]["vu_r"]

# (1) tout ce qui est prevu s'atteint ; la chambre du boss reste fermee
for t, c in POINTS.items():
    atte = pres(VU_FINAL, c)
    if t == "BO_chambre":
        if atte:
            erreurs.append("la chambre du boss est atteignable alors que la sortie est scellee")
    elif t.startswith("H") and t[1:].isdigit() or t in ORDRE or t.endswith(("_balcon", "_haut", "_toit", "_salle", "_crypte", "_aile_ouest")) or t.startswith(("AI_niv", "eclat_")) or t in ("HA", "HB", "glyphes", "rappel", "cr_lampes", "autel", "retour_debut"):
        if not atte:
            erreurs.append(f"cible inatteignable avec la paravoile : {t} {c}")

# premiere etape ou chaque cible est atteinte (realiste)
ETAPE_DE = {}
for t, c in POINTS.items():
    for i, e in enumerate(etapes):
        if pres(e["vu_r"], c):
            ETAPE_DE[t] = i
            break

# (2) le genereux, a chaque etape, n'atteint rien de ce que le realiste n'atteint pas encore
for i, e in enumerate(etapes):
    vu_g = atteint(set(e["ouvertes"]), VOLS_G)
    for t, c in POINTS.items():
        if t == "BO_chambre":
            continue
        if pres(vu_g, c) and not pres(e["vu_r"], c) and ETAPE_DE.get(t, 99) > i:
            erreurs.append(f"raccourci : a l'etape {i} (portes {e['ouvertes']}), le vol genereux atteint {t} {c} (realiste : etape {ETAPE_DE.get(t)})")

# (3) aucune ile sautee
ORD = {k: i["ordre"] for k, i in ILES.items()}
for (ia, ib) in sorted(PAIRES_G):
    if ORD[ib] - ORD[ia] > 1.0 + 1e-9:
        erreurs.append(f"ile sautee : vol genereux {ia} -> {ib}")

# (4) sans paravoile : on ne quitte pas le Debarcadere (toutes portes ouvertes)
vu_sans = Monde(set(PORTES) - {"SORTIE"}).marche([DEPART])
iles_sans = {ile_de(c[0], c[2]) for c in vu_sans}
if iles_sans - {"D"}:
    erreurs.append(f"sans paravoile, on atteint autre chose que le Debarcadere : {sorted(i for i in iles_sans if i != 'D')}")

# (5) aucune impasse : de chaque ile atteinte, le realiste mene a l'arene du boss
aretes = {}
for (ia, ib) in PAIRES_R:
    aretes.setdefault(ia, set()).add(ib)
vers_boss = {"BO"}
chg = True
while chg:
    chg = False
    for a_, bs in aretes.items():
        if a_ not in vers_boss and bs & vers_boss:
            vers_boss.add(a_)
            chg = True
iles_atteintes = {ile_de(c[0], c[2]) for c in VU_FINAL}
for k in sorted(iles_atteintes - {None}):
    if k not in vers_boss:
        erreurs.append(f"impasse : de {k}, on ne peut pas rejoindre l'arene du boss")

# vols prevus : tableau des plus longs vols realistes entre iles voisines
tab = {f"{a}>{b}": v for (a, b), v in sorted(MEILLEUR.items(), key=lambda kv: (ORD[kv[0][0]], ORD[kv[0][1]])) if 0 < ORD[b] - ORD[a] <= 1.0 + 1e-9}
print("vols prevus (le plus long, realiste) :")
for k, v in tab.items():
    print(f"  {k:8s} L={v['L']:5.1f} blocs, chute {v['chute']:3d}, {v['L'] / VITESSE_MAX:4.1f} s sur {JAUGE:.0f} s")
for k, v in tab.items():
    if v["L"] / VITESSE_MAX > JAUGE * 0.85:
        erreurs.append(f"vol trop long pour la jauge : {k} {v}")
suivantes = [(a, b) for a, b in zip(ORDRE[:-1], ORDRE[1:])]
for (a, b) in suivantes:
    if (a, b) not in PAIRES_R:
        erreurs.append(f"pas de vol realiste de {a} vers {b}")

res = {"ok": not erreurs, "erreurs": erreurs, "etapes": [{"ouvertes": e["ouvertes"]} for e in etapes], "etape_de": ETAPE_DE, "vols": tab,
       "paires_realistes": sorted(f"{a}>{b}" for a, b in PAIRES_R), "paires_genereuses": sorted(f"{a}>{b}" for a, b in PAIRES_G)}
(RACINE / "exports/ciel_verif.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
print("RESULTAT", "OK" if not erreurs else f"{len(erreurs)} ERREUR(S)")
for e in erreurs[:40]:
    print(" -", e)
sys.exit(0 if not erreurs else 1)
