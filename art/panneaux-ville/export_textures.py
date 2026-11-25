"""Exporte les 5 textures retenues de propositions.html vers les ressources du mod.

Usage : python -I export_textures.py <propositions.html> <dossier_sortie> <dossier_travail>
"""
import base64
import json
import re
import subprocess
import sys
from pathlib import Path

src, out_dir, work = (Path(a) for a in sys.argv[1:4])
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
IDS = ["bois", "pierre", "ble", "brique", "minerai"]

html = src.read_text(encoding="utf-8")
inject = """<pre id="out"></pre><script>
const o={};[...document.querySelectorAll('#g-autres .card')].forEach((c,i)=>{o[i]=c.querySelector('.sway canvas').toDataURL('image/png')});
document.getElementById('out').textContent=JSON.stringify(o);
</script></body>"""
page = work / "export.html"
page.write_text(html.replace("</body>", inject), encoding="utf-8")

dom = subprocess.run(
    [EDGE, "--headless=new", "--disable-gpu", "--dump-dom", "--virtual-time-budget=4000",
     "--user-data-dir=" + str(work / "edge-profile"), page.as_uri()],
    capture_output=True, text=True, encoding="utf-8", timeout=120).stdout
m = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
if not m:
    sys.exit("rien exporté : " + dom[:300])
data = json.loads(m.group(1).replace("&quot;", '"').replace("&amp;", "&"))
out_dir.mkdir(parents=True, exist_ok=True)
for i, ident in enumerate(IDS):
    png = base64.b64decode(data[str(i)].split(",", 1)[1])
    (out_dir / f"colonie_{ident}.png").write_bytes(png)
    print(ident, len(png), "octets")
