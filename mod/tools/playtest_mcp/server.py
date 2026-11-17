"""MCP "playtest" : pilote le serveur de test du mod (demarrage, arret, rcon, logs).

Sans dependance : Python 3 standard uniquement, pour que n'importe quel contributeur
puisse le lancer (voir README.md). Protocole MCP en JSON-RPC sur stdin/stdout.

Etat gere par le script lui-meme (rien a preparer a la main) :
- active rcon dans run/server/server.properties avec un mot de passe genere ;
- leve/retire .claude/testing.flag a la racine du repo pendant que le serveur tourne.
"""
import json
import os
import re
import secrets
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

MOD_DIR = Path(__file__).resolve().parents[2]
REPO_DIR = MOD_DIR.parent
SERVER_DIR = Path(os.environ.get("PLAYTEST_SERVER_DIR", MOD_DIR / "run" / "server"))
PROPS = SERVER_DIR / "server.properties"
LOG = SERVER_DIR / "playtest-server.log"
PIDFILE = SERVER_DIR / ".playtest.pid"
FLAG = REPO_DIR / ".claude" / "testing.flag"
RCON_PORT = int(os.environ.get("PLAYTEST_RCON_PORT", "25575"))
MAX_LOG_LINES = 80
MAX_REPLY_CHARS = 6000


# --------------------------------------------------------------------- rcon
def _packet(req_id, ptype, body):
    data = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def _read_packet(sock):
    head = b""
    while len(head) < 4:
        chunk = sock.recv(4 - len(head))
        if not chunk:
            raise ConnectionError("rcon ferme")
        head += chunk
    (length,) = struct.unpack("<i", head)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("rcon ferme")
        data += chunk
    req_id, ptype = struct.unpack("<ii", data[:8])
    return req_id, ptype, data[8:-2].decode("utf-8", "replace")


def rcon(command, timeout=10):
    password = _props().get("rcon.password", "")
    if not password:
        raise RuntimeError("rcon non configure : lance d'abord server_start")
    with socket.create_connection(("127.0.0.1", RCON_PORT), timeout=timeout) as sock:
        sock.sendall(_packet(1, 3, password))
        req_id, _, _ = _read_packet(sock)
        if req_id == -1:
            raise RuntimeError("rcon : mot de passe refuse")
        sock.sendall(_packet(2, 2, command))
        _, _, reply = _read_packet(sock)
        return reply


# ------------------------------------------------------------ server state
def _props():
    out = {}
    if PROPS.exists():
        for line in PROPS.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" in line and not line.startswith("#"):
                key, _, value = line.partition("=")
                out[key.strip()] = value.strip()
    return out


def _write_props(updates):
    lines = PROPS.read_text(encoding="utf-8").splitlines() if PROPS.exists() else []
    seen = set()
    for i, line in enumerate(lines):
        key = line.partition("=")[0].strip()
        if key in updates:
            lines[i] = f"{key}={updates[key]}"
            seen.add(key)
    lines += [f"{k}={v}" for k, v in updates.items() if k not in seen]
    PROPS.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _pid():
    try:
        pid = int(PIDFILE.read_text().strip())
    except (OSError, ValueError):
        return None
    out = subprocess.run(["tasklist", "/FI", f"PID eq {pid}", "/NH"],
                         capture_output=True, text=True).stdout
    return pid if str(pid) in out else None


def _log_text():
    # logs/latest.log du serveur : la sortie console capturee reste vide quand le
    # processus est detache sous Windows. On ignore un latest.log anterieur au demarrage.
    try:
        if latest.stat().st_mtime < PIDFILE.stat().st_mtime:
            return ""
        return latest.read_bytes().decode("utf-8", "replace")
    except OSError:
        return ""


latest = SERVER_DIR / "logs" / "latest.log"


def _status():
    pid = _pid()
    text = _log_text()
    ready = "Done (" in text and pid is not None
    rcon_ok = False
    if ready:
        try:
            rcon("list", timeout=3)
            rcon_ok = True
        except Exception:
            pass
    players = None
    if rcon_ok:
        players = rcon("list", timeout=3)
    return {"running": pid is not None, "pid": pid, "ready": ready,
            "rcon": rcon_ok, "players": players, "log": str(latest)}


def _raise_flag(text):
    FLAG.parent.mkdir(parents=True, exist_ok=True)
    FLAG.write_text(text, encoding="utf-8")


def _clear_flag():
    try:
        FLAG.unlink()
    except OSError:
        pass


# -------------------------------------------------------------------- tools
def tool_server_start(args):
    if _pid():
        return "Serveur deja en cours (voir server_status)."
    if not (SERVER_DIR / "world").exists():
        return (f"Pas de monde dans {SERVER_DIR / 'world'}. Copie une save prete "
                "(ex. ColoniesV2) dans run/server/world avant de demarrer.")
    SERVER_DIR.mkdir(parents=True, exist_ok=True)
    password = _props().get("rcon.password") or secrets.token_hex(8)
    _write_props({"enable-rcon": "true", "rcon.password": password,
                  "rcon.port": str(RCON_PORT), "online-mode": "false"})
    (SERVER_DIR / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    gradlew = MOD_DIR / ("gradlew.bat" if os.name == "nt" else "gradlew")
    logfile = open(LOG, "wb")
    flags = (subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW
             if os.name == "nt" else 0)
    proc = subprocess.Popen([str(gradlew), "runServer", "--console=plain"],
                            cwd=MOD_DIR, stdout=logfile, stderr=subprocess.STDOUT,
                            stdin=subprocess.DEVNULL, creationflags=flags)
    PIDFILE.write_text(str(proc.pid))
    _raise_flag("serveur de test (playtest MCP) en cours\n")
    if not args.get("wait", True):
        return f"Demarrage lance (pid {proc.pid}). Suis-le avec server_status."
    deadline = time.time() + int(args.get("timeout_s", 300))
    while time.time() < deadline:
        if proc.poll() is not None:
            _clear_flag()
            console = LOG.read_bytes().decode("utf-8", "replace").splitlines()[-40:]
            return "Le serveur s'est arrete au demarrage :\n" + "\n".join(console)
        if "Done (" in _log_text():
            return f"Serveur pret (pid {proc.pid}), rcon sur 127.0.0.1:{RCON_PORT}."
        time.sleep(2)
    return "Pas pret apres le delai : voir server_status / tail_log. Serveur laisse en marche."


def tool_server_stop(args):
    pid = _pid()
    if not pid:
        _clear_flag()
        return "Aucun serveur en cours."
    try:
        rcon("stop", timeout=5)
    except Exception:
        pass
    deadline = time.time() + int(args.get("timeout_s", 90))
    while time.time() < deadline and _pid():
        time.sleep(2)
    if _pid():
        subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
        note = " (arret force)"
    else:
        note = ""
    PIDFILE.unlink(missing_ok=True)
    _clear_flag()
    return "Serveur arrete" + note + ", testing.flag retire."


def tool_server_status(_args):
    return json.dumps(_status(), ensure_ascii=False)


def tool_rcon(args):
    command = args["command"].lstrip("/")
    reply = rcon(command)
    # les codes couleur Minecraft (§x) sont du bruit pour un lecteur texte
    reply = re.sub("§.", "", reply)
    return reply[:MAX_REPLY_CHARS] or "(pas de reponse)"


def _tail(n, pattern):
    lines = _log_text().splitlines()
    if pattern:
        rx = re.compile(pattern, re.I)
        lines = [ln for ln in lines if rx.search(ln)]
    return "\n".join(lines[-n:])


def tool_tail_log(args):
    n = min(int(args.get("lines", 30)), MAX_LOG_LINES)
    pattern = args.get("filter", "")
    out = _tail(n, pattern)
    return out[-MAX_REPLY_CHARS:] or "(aucune ligne)"


TOOLS = {
    "server_start": (tool_server_start, "Demarre le serveur de test (run/server) avec rcon active, leve testing.flag. Attend le 'Done' sauf wait=false.",
                     {"wait": {"type": "boolean"}, "timeout_s": {"type": "integer"}}, []),
    "server_stop": (tool_server_stop, "Arrete proprement le serveur (rcon stop), retire testing.flag.",
                    {"timeout_s": {"type": "integer"}}, []),
    "server_status": (tool_server_status, "Etat compact : en cours, pret, rcon, joueurs connectes.", {}, []),
    "rcon": (tool_rcon, "Execute une commande serveur (sans le /), renvoie la reponse tronquee.",
             {"command": {"type": "string"}}, ["command"]),
    "tail_log": (tool_tail_log, f"Dernieres lignes du log serveur (max {MAX_LOG_LINES}), filtre regex optionnel (ex. 'Interregne|ERROR|WARN').",
                 {"lines": {"type": "integer"}, "filter": {"type": "string"}}, []),
}


# ------------------------------------------------------------ MCP protocol
def _reply(msg_id, result=None, error=None):
    msg = {"jsonrpc": "2.0", "id": msg_id}
    if error:
        msg["error"] = error
    else:
        msg["result"] = result
    sys.stdout.write(json.dumps(msg) + "\n")
    sys.stdout.flush()


def _handle(msg):
    method, msg_id = msg.get("method"), msg.get("id")
    if method == "initialize":
        _reply(msg_id, {"protocolVersion": msg.get("params", {}).get("protocolVersion", "2024-11-05"),
                        "capabilities": {"tools": {}},
                        "serverInfo": {"name": "playtest", "version": "0.1.0"}})
    elif method == "tools/list":
        _reply(msg_id, {"tools": [
            {"name": name, "description": desc,
             "inputSchema": {"type": "object", "properties": props, "required": req}}
            for name, (_fn, desc, props, req) in TOOLS.items()]})
    elif method == "tools/call":
        params = msg.get("params", {})
        entry = TOOLS.get(params.get("name"))
        if not entry:
            _reply(msg_id, error={"code": -32602, "message": "outil inconnu"})
            return
        try:
            text, is_error = entry[0](params.get("arguments") or {}), False
        except Exception as exc:  # une erreur d'outil ne doit pas tuer le MCP
            text, is_error = f"Erreur : {exc}", True
        _reply(msg_id, {"content": [{"type": "text", "text": text}], "isError": is_error})
    elif msg_id is not None:  # ping et autres requetes
        _reply(msg_id, {})


def main():
    for line in sys.stdin:
        line = line.strip()
        if line:
            _handle(json.loads(line))


if __name__ == "__main__":
    main()
