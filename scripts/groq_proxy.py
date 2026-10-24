"""Private Groq intent gateway. Standard library; never publishes a listener."""
import collections
from contextlib import contextmanager
import hashlib
import hmac
import http.client
import json
import os
from pathlib import Path
import socket
import sqlite3
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL = "openai/gpt-oss-20b"
MAX_OUTPUT = 512
SYSTEM = ('Convertis une recherche de films ou series en mots-cles francais. '
          'Conserve tous les genres, annees, type film/serie et noms propres. '
          'Ne recommande aucun titre, ne cree aucune disponibilite. '
          'Retourne uniquement un objet JSON {"normalized_query":"..."}.')
KEY = os.getenv("GROQ_API_KEY", "").strip()
TOKEN = os.getenv("TVFLOW_PROXY_TOKEN", "").strip()


class Rejected(Exception):
    def __init__(self, code, reason):
        self.code, self.reason = code, reason


class Budget:
    """Cross-process, atomic reservations survive restarts, even failed calls."""
    def __init__(self, path, daily=50):
        self.path = str(path)
        self.daily = min(max(daily, 0), 50)
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.execute("CREATE TABLE IF NOT EXISTS calls (at REAL, reserved INTEGER)")
            db.execute("CREATE TABLE IF NOT EXISTS usage (at REAL, tokens INTEGER, latency REAL)")
    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=2)
        try:
            with db:
                yield db
        finally:
            db.close()
    def reserve(self, tokens):
        now = time.time()
        midnight = int(now // 86400) * 86400
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM calls WHERE at < ?", (midnight - 86400,))
            recent = db.execute("SELECT at,reserved FROM calls WHERE at>=?", (min(midnight, now - 60),)).fetchall()
            rows = [(at, n) for at, n in recent if at >= midnight]
            minute = [(at, n) for at, n in recent if at > now - 60]
            if (len(rows) >= self.daily or len(minute) >= 6
                    or sum(n for _, n in rows) + tokens > 100000
                    or sum(n for _, n in minute) + tokens > 7000):
                raise Rejected(429, "local_quota_exhausted")
            db.execute("INSERT INTO calls VALUES (?,?)", (now, tokens))
    def record(self, tokens, latency):
        with self.connect() as db:
            db.execute("INSERT INTO usage VALUES (?,?,?)", (time.time(), tokens, latency))


class Gateway:
    def __init__(self, budget, key=KEY, token=TOKEN):
        self.budget, self.key, self.token = budget, key, token
        self.lock = threading.Lock()
        self.cache = collections.OrderedDict()
        self.cooldown = 0.0
        self.opener = urllib.request.build_opener(NoRedirect())
    def rewrite(self, query):
        if not self.key:
            raise Rejected(503, "groq_disabled")
        digest = hashlib.sha256(query.encode()).hexdigest()
        # One upstream request at a time; fail fast rather than queueing clients.
        if not self.lock.acquire(blocking=False):
            raise Rejected(503, "gateway_busy")
        try:
            now = time.monotonic()
            cached = self.cache.get(digest)
            if cached and cached[0] > now:
                self.cache.move_to_end(digest)
                return {"normalized_query": cached[1], "cached": True}
            if now < self.cooldown:
                raise Rejected(503, "upstream_cooldown")
            payload = {"model": MODEL, "temperature": 0,
                       "max_completion_tokens": MAX_OUTPUT, "reasoning_effort": "low",
                       "response_format": {"type": "json_object"},
                       "messages": [{"role": "system", "content": SYSTEM},
                                    {"role": "user", "content": query}]}
            # Conservative byte-based token upper bound plus chat overhead.
            self.budget.reserve(len(SYSTEM.encode()) + len(query.encode()) + MAX_OUTPUT + 256)
            request = urllib.request.Request(
                "https://api.groq.com/openai/v1/chat/completions",
                data=json.dumps(payload).encode(),
                headers={"Authorization": "Bearer " + self.key,
                         "Content-Type": "application/json",
                         "Accept": "application/json",
                         "User-Agent": "TVFlow-Groq-Proxy/1.0"},
                method="POST")
            start = time.monotonic()
            try:
                with self.opener.open(request, timeout=3) as response:
                    data = response.read(32769)
                    if len(data) > 32768:
                        raise ValueError("response too large")
                    result = json.loads(data)
                content = json.loads(result["choices"][0]["message"]["content"])
                rewritten = content["normalized_query"]
                if set(content) != {"normalized_query"} or not isinstance(rewritten, str) or not 2 <= len(rewritten) <= 300:
                    raise ValueError("invalid intent")
                if any(ord(c) < 32 for c in rewritten):
                    raise ValueError("control character")
                self.budget.record(int(result.get("usage", {}).get("total_tokens", 0)), time.monotonic() - start)
            except urllib.error.HTTPError as error:
                self.cooldown = time.monotonic() + 60
                upstream_code = None
                try:
                    error_body = error.read(8193)
                    if len(error_body) <= 8192:
                        candidate = json.loads(error_body).get("error", {}).get("code")
                        if candidate in {"model_permission_blocked_org",
                                         "model_permission_blocked_project"}:
                            upstream_code = candidate
                except (OSError, ValueError, AttributeError, TypeError):
                    pass
                reason = {
                    400: "upstream_request_rejected",
                    401: "upstream_auth_failed",
                    403: "upstream_access_denied",
                    404: "upstream_model_unavailable",
                    429: "upstream_quota_exhausted",
                }.get(error.code, "upstream_http_error")
                if upstream_code:
                    reason = upstream_code
                raise Rejected(503, reason) from None
            except (OSError, http.client.HTTPException, ValueError, KeyError, IndexError, TypeError):
                self.cooldown = time.monotonic() + 60
                raise Rejected(503, "upstream_unavailable") from None
            self.cache[digest] = (time.monotonic() + 3600, rewritten)
            while len(self.cache) > 256:
                self.cache.popitem(last=False)
            return {"normalized_query": rewritten, "cached": False}
        finally:
            self.lock.release()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward credentials to a redirected origin.


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(2)
    def log_message(self, *args):
        pass  # No queries, headers, keys or URLs in logs.
    def respond(self, code, data):
        body = json.dumps(data).encode()
        try:
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except OSError:
            pass
    def do_POST(self):
        gateway = self.server.gateway
        try:
            if self.path != "/v1/intent":
                raise Rejected(404, "not_found")
            auth = self.headers.get("Authorization", "")
            if len(gateway.token) < 32 or not hmac.compare_digest(auth.encode("utf-8"), ("Bearer " + gateway.token).encode("utf-8")):
                raise Rejected(401, "unauthorized")
            if self.headers.get("Transfer-Encoding") or len(self.headers.get_all("Content-Length", [])) != 1:
                raise Rejected(400, "invalid_length")
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                raise Rejected(400, "invalid_length")
            if not 0 < length <= 2048:
                raise Rejected(413, "invalid_length")
            try:
                payload = json.loads(self.rfile.read(length))
                query = payload["query"]
                if not isinstance(query, str) or not 2 <= len(query.strip()) <= 300 or any(ord(c) < 32 for c in query):
                    raise ValueError()
            except (ValueError, KeyError, TypeError, socket.timeout):
                raise Rejected(400, "invalid_query")
            self.respond(200, gateway.rewrite(query.strip()))
        except Rejected as error:
            self.respond(error.code, {"error": error.reason})
        except (OSError, sqlite3.Error):
            self.respond(503, {"error": "gateway_unavailable"})


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 8
    def __init__(self, address, gateway):
        self.gateway = gateway
        self.slots = threading.BoundedSemaphore(8)
        super().__init__(address, Handler)
    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise
    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()


if __name__ == "__main__":
    if len(TOKEN) < 32:
        raise SystemExit("Configure a private proxy token before starting.")
    state = os.getenv("TVFLOW_GROQ_STATE", r"G:\DevServer\data\tvflow-groq\budget.sqlite3")
    budget = Budget(state, int(os.getenv("TVFLOW_GROQ_DAILY_CAP", "50")))
    print("TVFlow private gateway: loopback 8765; Groq configured:", bool(KEY), flush=True)
    Server(("127.0.0.1", 8765), Gateway(budget)).serve_forever()
