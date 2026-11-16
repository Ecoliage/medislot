import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LATENCE_SECONDES = 1.2


class Passerelle(BaseHTTPRequestHandler):
    def do_POST(self):
        taille = int(self.headers.get("Content-Length", 0))
        self.rfile.read(taille)
        time.sleep(LATENCE_SECONDES)
        corps = json.dumps({"statut": "ENVOYE"}).encode()
        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def log_message(self, format, *args):
        print("sms-passerelle", self.path, *args, flush=True)


ThreadingHTTPServer(("0.0.0.0", 8089), Passerelle).serve_forever()
