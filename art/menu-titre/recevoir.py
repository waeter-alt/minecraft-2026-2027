# Reçoit les images rendues par rendu.html et les range dans frames/intro et frames/main.
# Usage (depuis art/menu-titre) : python recevoir.py
import http.server, os, re

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'frames')


class H(http.server.BaseHTTPRequestHandler):
    def cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Headers', '*')

    def do_OPTIONS(self):
        self.send_response(204); self.cors(); self.end_headers()

    def do_POST(self):
        m = re.fullmatch(r'/(intro|main)/(\d{4}\.png)', self.path)
        if not m:
            self.send_response(400); self.cors(); self.end_headers(); return
        d = os.path.join(OUT, m.group(1))
        os.makedirs(d, exist_ok=True)
        body = self.rfile.read(int(self.headers['Content-Length']))
        with open(os.path.join(d, m.group(2)), 'wb') as f:
            f.write(body)
        self.send_response(200); self.cors(); self.end_headers()

    def log_message(self, *a):
        pass


print('Réception des images sur http://127.0.0.1:8091 ->', OUT)
http.server.HTTPServer(('127.0.0.1', 8091), H).serve_forever()
