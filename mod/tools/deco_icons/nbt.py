"""Minimal NBT reader for Structurize blueprints (gzipped compound)."""
import gzip, io, struct


def _read(f, t):
    if t == 1: return struct.unpack(">b", f.read(1))[0]
    if t == 2: return struct.unpack(">h", f.read(2))[0]
    if t == 3: return struct.unpack(">i", f.read(4))[0]
    if t == 4: return struct.unpack(">q", f.read(8))[0]
    if t == 5: return struct.unpack(">f", f.read(4))[0]
    if t == 6: return struct.unpack(">d", f.read(8))[0]
    if t == 7:
        n = struct.unpack(">i", f.read(4))[0]; return f.read(n)
    if t == 8:
        n = struct.unpack(">H", f.read(2))[0]; return f.read(n).decode("utf-8", "replace")
    if t == 9:
        et = f.read(1)[0]; n = struct.unpack(">i", f.read(4))[0]
        return [_read(f, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == 0: return d
            k = f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8", "replace")
            d[k] = _read(f, tt)
    if t == 11:
        n = struct.unpack(">i", f.read(4))[0]; return list(struct.unpack(">%di" % n, f.read(4 * n)))
    if t == 12:
        n = struct.unpack(">i", f.read(4))[0]; return list(struct.unpack(">%dq" % n, f.read(8 * n)))
    raise ValueError(t)


def load(data):
    try:
        data = gzip.decompress(data)
    except OSError:
        pass
    f = io.BytesIO(data)
    t = f.read(1)[0]
    f.read(struct.unpack(">H", f.read(2))[0])
    return _read(f, t)
