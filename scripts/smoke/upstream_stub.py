"""Stub of the MyŠkoda Public API for the native smoke test (scripts/smoke-test-native.ps1).

Serves GET /api/v1/vehicles/<vin>?include=... with the fixture given as first argument and the
headers the real API sends (key expiry, rate limit). Every request is logged to stdout as
"REQUEST <path> api-key=<value>" so the smoke test can count upstream calls. Standard library only.
"""
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FIXTURE = open(sys.argv[1], "rb").read()
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8080


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        print(f"REQUEST {self.path} api-key={self.headers.get('X-API-Key')}", flush=True)
        if not self.path.startswith("/api/v1/vehicles/"):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(FIXTURE)))
        self.send_header("X-API-Key-Expires-At", "2026-12-31T00:00:00Z")
        self.send_header("RateLimit-Limit", "20")
        self.send_header("RateLimit-Remaining", "19")
        self.send_header("RateLimit-Reset", "3600")
        self.end_headers()
        self.wfile.write(FIXTURE)

    def log_message(self, format, *args):  # keep stdout to the REQUEST lines
        pass


ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
