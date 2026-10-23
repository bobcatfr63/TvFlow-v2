"""Optional zero-billing Groq intent proxy for local TVFlow testing.
Binds loopback ONLY; do not expose publicly without authentication, TLS and per-user quotas.
No key -> no outbound requests. Install nothing: Python standard library only.
"""
import json
import os
import time
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KEY = os.getenv("GROQ_API_KEY", "").strip()
DAILY_CAP = min(max(int(os.getenv("TVFLOW_GROQ_DAILY_CAP", "50")), 0), 500)
requests_today = 0
day = time.strftime("%Y-%m-%d")

class Handler(BaseHTTPRequestHandler):
    def respond(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        global requests_today, day
        if self.path != "/v1/intent":
            return self.respond(404, {"error": "not_found"})
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 < length <= 2048:
            return self.respond(413, {"error": "invalid_length"})
        try:
            query = json.loads(self.rfile.read(length))["query"]
            if not isinstance(query, str) or len(query) not in range(18, 301):
                return self.respond(400, {"error": "invalid_query"})
        except (ValueError, KeyError, TypeError):
            return self.respond(400, {"error": "invalid_json"})
        today = time.strftime("%Y-%m-%d")
        if day != today:
            day, requests_today = today, 0
        if not KEY or requests_today >= DAILY_CAP:
            return self.respond(503, {"error": "ai_disabled_or_quota_exhausted"})
        requests_today += 1  # Fail closed: unsuccessful calls also consume local quota.
        payload = {
            "model": "openai/gpt-oss-20b",
            "temperature": 0,
            "max_completion_tokens": 180,
            "reasoning_effort": "low",
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": "Reformule la recherche de films ou séries en français en mots-clés simples pour un moteur de catalogue. Préserve genre, année, type et noms propres. Réponds UNIQUEMENT en JSON de forme {\\\"normalized_query\\\":\\\"...\\\"}. Ne propose aucun titre et ne suppose aucune disponibilité."},
                {"role": "user", "content": query},
            ],
        }
        req = urllib.request.Request(
            "https://api.groq.com/openai/v1/chat/completions",
            data=json.dumps(payload).encode(),
            headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=4) as resp:
                result = json.load(resp)
            rewritten = json.loads(result["choices"][0]["message"]["content"])["normalized_query"]
            if not isinstance(rewritten, str) or not 2 <= len(rewritten) <= 300:
                raise ValueError("Invalid model answer")
            return self.respond(200, {"normalized_query": rewritten})
        except (urllib.error.URLError, ValueError, KeyError, IndexError, TypeError):
            return self.respond(503, {"error": "upstream_unavailable"})

if __name__ == "__main__":
    print("TVFlow AI proxy on 127.0.0.1:8765; outbound Groq enabled:", bool(KEY))
    ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
