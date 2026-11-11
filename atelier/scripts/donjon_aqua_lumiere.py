# [claude-build]
"""donjon_aqua_lumiere.py - eclairage du Sanctuaire des Marees : aucune case ou l'on puisse se tenir ou nager n'est dans le noir (pas d'apparition de monstres,
l'eau comprise). Les lanternes de mer et le lichen eclairent deja ; on propage la lumiere, puis on pose des blocs « light » invisibles la ou il manque (dans l'eau,
ils sont immerges pour ne pas laisser fuir l'eau). Execute par donjon_aqua.py apres le decor."""
import numpy as np

TRANSP = {"air", "water", "light", "lever", "stone_button", "stone_pressure_plate", "oak_wall_sign", "ladder", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "sea_pickle",
          "glow_lichen", "vine", "moss_carpet", "iron_bars"}
SEUIL = 5
EMET = {"sea_lantern": 15, "glow_lichen": 7, "light": 15}


def etat_lumiere():
    libre = C.copy()                                               # cases ou l'on peut etre : volumes creux sans bloc plein
    pas = C.copy()
    src = np.zeros((SX, SY, SZ), dtype=np.int8)
    for p, s_ in blocs.items():
        n_ = s_[0].split(":")[-1]
        t = n_ in TRANSP or n_.endswith(("_coral", "_coral_fan", "_coral_wall_fan", "_carpet"))
        pas[p] = t or n_ in EMET
        libre[p] = C[p] and t
        if n_ in EMET:
            src[p] = EMET[n_] if n_ != "sea_pickle" else 0
    L = src.copy()
    for _ in range(15):
        m = L.copy()
        for ax in range(3):
            a = [slice(None)] * 3
            b = [slice(None)] * 3
            a[ax], b[ax] = slice(1, None), slice(None, -1)
            m[tuple(a)] = np.maximum(m[tuple(a)], L[tuple(b)])
            m[tuple(b)] = np.maximum(m[tuple(b)], L[tuple(a)])
        L = np.where(pas, np.maximum(L, m - 1), 0).astype(np.int8)
        L = np.maximum(L, src)
    return L, libre


def placeable(p):
    return p not in blocs or blocs[p] == EAU


nl = 0
for tour in range(12):
    L, libre = etat_lumiere()
    noir = libre & (L < SEUIL)
    if not noir.any():
        break
    pts = np.argwhere(noir)
    choisi = {}
    for (x, y, z) in pts:
        p = (int(x), int(y), int(z))
        if not placeable(p):
            continue
        k = (p[0] // 6, p[1] // 6, p[2] // 6)
        d = abs(p[0] % 6 - 3) + abs(p[1] % 6 - 3) + abs(p[2] % 6 - 3)
        if k not in choisi or d < choisi[k][0]:
            choisi[k] = (d, p)
    if not choisi:
        raise SystemExit(f"cases sombres impossibles a eclairer : {len(pts)} (ex. {pts[:3].tolist()})")
    for _, p in choisi.values():
        put(*p, S("light", level=15, waterlogged=(blocs.get(p) == EAU)))
        nl += 1
L, libre = etat_lumiere()
mini = int(L[libre].min()) if libre.any() else 0
n_noir = int((libre & (L < SEUIL)).sum())
print(f"[4b] eclairage : {nl} blocs light | lumiere minimale {mini} sur {int(libre.sum())} cases libres | cases sous {SEUIL} : {n_noir}", flush=True)
if n_noir:
    raise SystemExit("cases sombres restantes")
