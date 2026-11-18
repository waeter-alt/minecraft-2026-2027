# [claude-build]
"""lire_monde.py - lit les blocs d'un monde Minecraft 1.21 (fichiers region/*.mca) sans le jeu : bloc(x, y, z) -> (nom, props triees).
Format Anvil : 1024 chunks par region, chacun en NBT compresse ; chaque section de 16 blocs de haut a une palette et un tableau de longs
(indices de palette, au moins 4 bits, sans chevauchement d'un long a l'autre)."""
import io
import struct
import zlib
from pathlib import Path

import nbtlib


class Monde:
    def __init__(self, save):
        self.dossier = Path(save) / "region"
        self.chunks = {}

    def _chunk(self, cx, cz):
        if (cx, cz) in self.chunks:
            return self.chunks[(cx, cz)]
        rx, rz = cx >> 5, cz >> 5
        f = self.dossier / f"r.{rx}.{rz}.mca"
        sections = None
        if f.exists():
            data = f.read_bytes()
            i = 4 * ((cx & 31) + (cz & 31) * 32)
            off = int.from_bytes(data[i:i + 3], "big")
            if off:
                debut = off * 4096
                longueur = struct.unpack(">I", data[debut:debut + 4])[0]
                comp = data[debut + 4]
                brut = data[debut + 5:debut + 4 + longueur]
                nbt = nbtlib.File.parse(io.BytesIO(zlib.decompress(brut) if comp == 2 else brut))
                sections = {}
                for s in nbt.get("sections", []):
                    bs = s.get("block_states")
                    if bs is None:
                        continue
                    pal = [(str(p["Name"]), tuple(sorted((str(k), str(v)) for k, v in dict(p.get("Properties", {})).items()))) for p in bs["palette"]]
                    sections[int(s["Y"])] = (pal, [int(v) & 0xFFFFFFFFFFFFFFFF for v in bs.get("data", [])])
        self.chunks[(cx, cz)] = sections
        return sections

    def bloc(self, x, y, z):
        """Le bloc en (x, y, z) (coordonnees monde), ou None si le chunk n'existe pas."""
        sections = self._chunk(x >> 4, z >> 4)
        if sections is None:
            return None
        s = sections.get(y >> 4)
        if s is None:
            return ("minecraft:air", ())
        pal, data = s
        if len(pal) == 1 or not data:
            return pal[0]
        bits = max(4, (len(pal) - 1).bit_length())
        par_long = 64 // bits
        i = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
        v = (data[i // par_long] >> ((i % par_long) * bits)) & ((1 << bits) - 1)
        return pal[v]
