# [claude-build]
"""Lecture seule : quels blocs du monde voit-on dans le souterrain d'une arene ?
Prend les cases d'air sous le sol dans le .nbt, regarde leurs voisines que la structure ne fixe pas, et lit ce que le monde y contient.
Usage : scan_souterrain.py <structure.nbt> <sol local> "<monde>" <x> <y> <z> ["<monde>" <x> <y> <z> ...]"""
import gzip
import io
import math
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
f = nbtlib.load(R / sys.argv[1])
SOL = int(sys.argv[2])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G = {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}
sx, sy, sz = (int(v) for v in f["size"])
c = sx / 2
air = {p for p, n in G.items() if n in ("air", "light", "lava", "ladder") and p[1] < SOL and math.hypot(p[0] + .5 - c, p[2] + .5 - c) < 48}
faces = set()
for x, y, z in air:
    for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
        if q not in G and 0 <= q[1] < sy:
            faces.add(q)
print("cases d'air sous le sol", len(air), "| parois que la structure ne fixe pas", len(faces))
TERRE = {"dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mud", "farmland", "dirt_path"}


def chunks(fr):
    b = fr.read_bytes()
    if len(b) < 8192:
        return
    for i in range(1024):
        o = struct.unpack(">I", b[i * 4:i * 4 + 4])[0]
        off = o >> 8
        if not off:
            continue
        l, cc = struct.unpack(">IB", b[off * 4096:off * 4096 + 5])
        raw = b[off * 4096 + 5:off * 4096 + 4 + l]
        raw = zlib.decompress(raw) if cc == 2 else gzip.decompress(raw)
        yield nbtlib.File.parse(io.BytesIO(raw))


args = sys.argv[3:]
for k in range(0, len(args), 4):
    monde_, ox, oy, oz = args[k], *(int(v) for v in args[k + 1:k + 4])
    W = R / "launcher/instances/Build-1.21.1/minecraft/saves" / monde_
    voulu = {((x + ox) >> 4, (y + oy) >> 4, (z + oz) >> 4) for x, y, z in faces}
    sections, conteneurs = {}, Counter()
    for fr in sorted((W / "region").glob("r.*.mca")):
        for ch in chunks(fr):
            for s in ch.get("sections", []):
                key = (int(ch["xPos"]), int(s["Y"]), int(ch["zPos"]))
                if key in voulu and s.get("block_states") is not None:
                    sections[key] = s["block_states"]
            for b in ch.get("block_entities", []):
                if ox <= int(b["x"]) < ox + sx and oz <= int(b["z"]) < oz + sz and str(b["id"]).split(":")[1] in ("chest", "barrel", "shulker_box", "trapped_chest", "hopper", "dispenser", "dropper"):
                    conteneurs[str(b["id"])] += 1
    vus = Counter()
    for x, y, z in faces:
        wx, wy, wz = x + ox, y + oy, z + oz
        bs = sections.get((wx >> 4, wy >> 4, wz >> 4))
        if bs is None:
            vus["(non charge)"] += 1
            continue
        p_ = [str(p["Name"]).split(":")[1] for p in bs["palette"]]
        if "data" not in bs:
            vus[p_[0]] += 1
            continue
        n = max(4, (len(p_) - 1).bit_length())
        par = 64 // n
        i = ((wy & 15) * 16 + (wz & 15)) * 16 + (wx & 15)
        v = int(bs["data"][i // par]) & 0xFFFFFFFFFFFFFFFF
        vus[p_[(v >> ((i % par) * n)) & ((1 << n) - 1)]] += 1
    print(f"== {monde_} : blocs du monde visibles dans le souterrain", vus.most_common(12))
    print("   famille terre :", sum(v for k_, v in vus.items() if k_ in TERRE), "| conteneurs dans l'emprise :", dict(conteneurs))
