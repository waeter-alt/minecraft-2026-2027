# [claude-build]
"""Lecture seule : compare une structure posee dans un monde avec son .nbt, bloc par bloc, et liste les entites Create et decoratives de la zone.
Usage : verif_pose.py "<dossier du monde>" <fichier.nbt> <x> <y> <z>   (a lancer apres une sauvegarde du monde)"""
import gzip
import io
import struct
import sys
import time
import zlib
from collections import Counter
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
W = R / "launcher/instances/Build-1.21.1/minecraft/saves" / sys.argv[1]
f = nbtlib.load(R / sys.argv[2])
ox, oy, oz = (int(v) for v in sys.argv[3:6])
sx, sy, sz = (int(v) for v in f["size"])
print("level.dat il y a", int(time.time() - (W / "level.dat").stat().st_mtime), "s")


def chunks(fichier):
    b = fichier.read_bytes()
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


SECTIONS, BE = {}, []
for fr in sorted((W / "region").glob("r.*.mca")):
    for c in chunks(fr):
        for s in c.get("sections", []):
            if s.get("block_states") is not None:
                SECTIONS[(int(c["xPos"]), int(s["Y"]), int(c["zPos"]))] = s["block_states"]
        for b in c.get("block_entities", []):
            if ox <= int(b["x"]) < ox + sx and oz <= int(b["z"]) < oz + sz:
                BE.append(b)
CACHE = {}


def bloc(x, y, z):
    k = (x >> 4, y >> 4, z >> 4)
    if k not in CACHE:
        CACHE[k] = tableau(SECTIONS[k]) if k in SECTIONS else None
    t = CACHE[k]
    return None if t is None else t[((y & 15) * 16 + (z & 15)) * 16 + (x & 15)]


pal = [str(p["Name"]) for p in f["palette"]]
ok, diff = 0, Counter()
for b in f["blocks"]:
    x, y, z = (int(v) for v in b["pos"])
    att, vu = pal[int(b["state"])], bloc(ox + x, oy + y, oz + z)
    if att == vu:
        ok += 1
    else:
        diff[(att.split(":")[1], str(vu).split(":")[-1])] += 1
print("identiques", ok, "differents", sum(diff.values()), diff.most_common(8))
for b in BE:
    if str(b["id"]) == "create:mechanical_bearing":
        print("roulement", int(b["x"]), int(b["y"]), int(b["z"]), "Running", b.get("Running"), "Speed", b.get("Speed"), "Angle", b.get("Angle"))
print("entites de bloc Create", Counter(str(b["id"]) for b in BE if str(b["id"]).startswith("create:")).most_common(8))
ent = Counter()
for fr in sorted((W / "entities").glob("r.*.mca")):
    for c in chunks(fr):
        for e in c.get("Entities", []):
            p = [float(v) for v in e["Pos"]]
            if ox <= p[0] < ox + sx and oz <= p[2] < oz + sz:
                i = str(e.get("id"))
                ent[i] += 1
                if "contraption" in i:
                    print("contraption en", [round(v, 1) for v in p], "blocs", len(e["Contraption"]["Blocks"]["BlockList"]))
print("entites", ent.most_common(10))
