# [claude-build]
"""Lecture seule : compare le monde Arene Semaine, bloc par bloc, avec les structures, et controle biomes, heure et traces de passage.
Usage : verif_monde_semaine.py   (a lancer apres une sauvegarde du monde)"""
import gzip
import io
import json
import struct
import time
import zlib
from collections import Counter
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
W = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine"
conf = json.loads((R / "exports/semaine.json").read_text(encoding="utf-8"))
print("level.dat il y a", int(time.time() - (W / "level.dat").stat().st_mtime), "s")
SECTIONS = {}
BIOMES = {}


def chunks(f):
    b = f.read_bytes()
    if len(b) < 8192:
        return
    for i in range(1024):
        o = struct.unpack(">I", b[i * 4:i * 4 + 4])[0]
        off = o >> 8
        if not off:
            continue
        l, c = struct.unpack(">IB", b[off * 4096:off * 4096 + 5])
        raw = b[off * 4096 + 5:off * 4096 + 4 + l]
        raw = zlib.decompress(raw) if c == 2 else gzip.decompress(raw)
        yield nbtlib.File.parse(io.BytesIO(raw))


def tableau(bs):
    pal = [str(p["Name"]) for p in bs["palette"]]
    if "data" not in bs:
        return [pal[0]] * 4096
    n = max(4, (len(pal) - 1).bit_length())
    par, m, out = 64 // n, (1 << n) - 1, []
    for v in bs["data"]:
        v = int(v) & 0xFFFFFFFFFFFFFFFF
        for j in range(par):
            if len(out) < 4096:
                out.append(pal[(v >> (j * n)) & m])
    return out


for f in sorted((W / "region").glob("r.*.mca")):
    for c in chunks(f):
        if str(c.get("Status", "")).split(":")[-1] != "full":
            continue
        for s in c.get("sections", []):
            k = (int(c["xPos"]), int(s["Y"]), int(c["zPos"]))
            if s.get("block_states") is not None:
                SECTIONS[k] = s["block_states"]
            if s.get("biomes") is not None:
                BIOMES[k] = [str(b) for b in s["biomes"]["palette"]]
CACHE = {}


def bloc(x, y, z):
    k = (x >> 4, y >> 4, z >> 4)
    if k not in CACHE:
        CACHE[k] = tableau(SECTIONS[k]) if k in SECTIONS else None
    t = CACHE[k]
    return None if t is None else t[((y & 15) * 16 + (z & 15)) * 16 + (x & 15)]


def comparer(nom_, ox, oy, oz):
    f = nbtlib.load(R / "exports" / f"{nom_}.nbt")
    pal = [str(p["Name"]) for p in f["palette"]]
    ok, diff = 0, Counter()
    for b in f["blocks"]:
        x, y, z = (int(v) for v in b["pos"])
        attendu = pal[int(b["state"])]
        vu = bloc(ox + x, oy + y, oz + z)
        if vu == attendu:
            ok += 1
        elif attendu == "minecraft:barrier" and vu == "minecraft:air":
            RETIREES[nom_] += 1                           # 2026-11-08 : parois invisibles retirees du monde final (structures sans_barrieres_*)
        else:
            diff[(attendu.split(":")[1], str(vu).split(":")[-1])] += 1
    return ok, sum(diff.values()), diff.most_common(5)


RETIREES = Counter()


print("hub7", comparer("hub7", -52, 78, -52))
for a in conf["arenes"]:
    cx, cz = a["c"]
    ok, ko, d = comparer(a["f"], cx - 52, a["y"], cz - 52)
    k = (cx >> 4, (a["y"] + 20) >> 4, cz >> 4)
    print(f"{a['n']} {a['f']:<10} identiques {ok} differents {ko} {d} biome {BIOMES.get(k)}")
n_bar = sum(1 for bs in SECTIONS.values() if any(str(p["Name"]) == "minecraft:barrier" for p in bs["palette"]))
print("barrieres retirees (parois invisibles) :", dict(RETIREES), "| sections du monde qui contiennent encore une barriere :", n_bar)
n = nbtlib.load(W / "level.dat")["Data"]
print("heure", n.get("DayTime"), "difficulte", n.get("Difficulty"))
for f in (W / "playerdata").glob("*.dat"):
    p = nbtlib.load(f)
    print("joueur", [round(float(v), 1) for v in p["Pos"]], "effets", [str(x.get("id")) for x in p.get("active_effects", [])])
