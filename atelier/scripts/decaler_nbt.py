# [claude-build]
"""Decale une structure verticalement (pour comparer deux versions dont le gabarit ne commence pas a la meme hauteur).
Usage : decaler_nbt.py <entree.nbt> <sortie.nbt> <decalage Y> [hauteur totale]"""
import sys
from pathlib import Path

import nbtlib
from nbtlib import Int, List

R = Path(__file__).resolve().parent.parent
f = nbtlib.load(R / sys.argv[1])
dy = int(sys.argv[3])
for b in f["blocks"]:
    b["pos"][1] = Int(int(b["pos"][1]) + dy)
taille = [int(v) for v in f["size"]]
taille[1] = int(sys.argv[4]) if len(sys.argv) > 4 else taille[1] + dy
f["size"] = List[Int]([Int(v) for v in taille])
f.save(R / sys.argv[2])
print("decale de", dy, "| taille", taille, "| blocs", len(f["blocks"]))
