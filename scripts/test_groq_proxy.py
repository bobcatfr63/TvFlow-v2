"""Offline integration and concurrency tests. No external network or API key."""
import concurrent.futures
import io
import json
from pathlib import Path
import sqlite3
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch
import groq_proxy as proxy

TOKEN = "test-only-" + "x" * 40


class ProxyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.budget = proxy.Budget(Path(self.temp.name) / "budget.db")
        self.gateway = proxy.Gateway(self.budget, key="test-key", token=TOKEN)
        self.server = proxy.Server(("127.0.0.1", 0), self.gateway)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join()
        self.temp.cleanup()
    def request(self, payload=None, auth=TOKEN, headers=None, raw=None):
        body = raw if raw is not None else json.dumps(payload or {"query": "Je cherche un film francais"}).encode()
        hdr = {"Content-Type": "application/json", "Authorization": "Bearer " + auth}
        hdr.update(headers or {})
        request = urllib.request.Request(f"http://127.0.0.1:{self.server.server_port}/v1/intent", data=body, headers=hdr)
        try:
            with urllib.request.urlopen(request, timeout=4) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.load(error)
    def answer(self, text="film francais"):
        return io.BytesIO(json.dumps({"choices": [{"message": {"content": json.dumps({"normalized_query": text})}}],
                                     "usage": {"total_tokens": 123}}).encode())
    def count(self):
        with self.budget.connect() as db:
            return db.execute("SELECT count(*) FROM calls").fetchone()[0]
    def test_missing_key_never_calls_groq(self):
        self.gateway.key = ""
        with patch.object(self.gateway.opener, "open") as upstream:
            self.assertEqual(self.request()[0], 503)
            upstream.assert_not_called()
        self.assertEqual(self.count(), 0)
    def test_auth_required_before_any_outbound(self):
        with patch.object(self.gateway.opener, "open") as upstream:
            self.assertEqual(self.request(auth="wrong")[0], 401)
            upstream.assert_not_called()
    def test_invalid_payloads_do_not_consume_quota(self):
        for body in (b"{", b"[]", b'{"query":12}', b'{"query":""}', b'{"query":"' + b"x" * 301 + b'"}'):
            self.assertEqual(self.request(raw=body)[0], 400)
        self.assertEqual(self.request(raw=b"x" * 2049)[0], 413)
        self.assertEqual(self.request(headers={"Content-Length": "bad"})[0], 400)
        self.assertEqual(self.count(), 0)
    def test_success_and_cache(self):
        with patch.object(self.gateway.opener, "open", return_value=self.answer()) as upstream:
            first = self.request()
            second = self.request()
            self.assertEqual(first[0], 200)
            self.assertFalse(first[1]["cached"])
            self.assertTrue(second[1]["cached"])
            self.assertEqual(upstream.call_count, 1)
        self.assertEqual(self.count(), 1)
    def test_cache_expiry(self):
        with patch.object(self.gateway.opener, "open", side_effect=lambda *a, **k: self.answer()) as upstream:
            self.request()
            for digest, (_, value) in list(self.gateway.cache.items()):
                self.gateway.cache[digest] = (0, value)
            self.request()
            self.assertEqual(upstream.call_count, 2)
    def test_daily_cap_survives_restart(self):
        self.budget.daily = 1
        self.budget.reserve(1000)
        reopened = proxy.Budget(self.budget.path, daily=1)
        with self.assertRaises(proxy.Rejected):
            reopened.reserve(1000)
    def test_atomic_concurrent_budget(self):
        self.budget.daily = 50
        def reserve(_):
            try:
                self.budget.reserve(1000)
                return True
            except proxy.Rejected:
                return False
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            self.assertEqual(sum(pool.map(reserve, range(20))), 6)
        self.assertEqual(self.count(), 6)
    def test_token_budget(self):
        self.budget.reserve(6500)
        with self.assertRaises(proxy.Rejected):
            self.budget.reserve(501)
    def test_upstream_failure_and_cooldown(self):
        with patch.object(self.gateway.opener, "open", side_effect=urllib.error.URLError("offline")) as upstream:
            self.assertEqual(self.request()[0], 503)
            self.assertEqual(self.request()[0], 503)
            self.assertEqual(upstream.call_count, 1)
        self.assertEqual(self.count(), 1)
    def test_malformed_model_answer(self):
        with patch.object(self.gateway.opener, "open", return_value=io.BytesIO(b'{"choices":[]}')):
            self.assertEqual(self.request()[0], 503)
    def test_short_queries_supported(self):
        with patch.object(self.gateway.opener, "open", return_value=self.answer("action")):
            self.assertEqual(self.request({"query": "SF"})[0], 200)
    def test_model_fixed_and_no_catalog_sent(self):
        with patch.object(self.gateway.opener, "open", return_value=self.answer()) as upstream:
            self.request({"query": "Un thriller francais de 2025", "catalog": ["secret"]})
            data = json.loads(upstream.call_args.args[0].data)
            self.assertEqual(data["model"], "openai/gpt-oss-20b")
            self.assertNotIn("secret", str(data))
            self.assertEqual(data["max_completion_tokens"], 512)
    def test_redirect_not_followed(self):
        self.assertIsNone(proxy.NoRedirect().redirect_request(None, None, 302, "", {}, "https://evil.example"))
    def test_no_query_logging(self):
        from contextlib import redirect_stderr
        output = io.StringIO()
        with redirect_stderr(output):
            self.request(auth="wrong")
        self.assertEqual(output.getvalue(), "")

    def test_midnight_does_not_reset_minute_budget(self):
        with patch("groq_proxy.time.time", return_value=172799.0):
            for _ in range(6):
                self.budget.reserve(100)
        with patch("groq_proxy.time.time", return_value=172801.0):
            with self.assertRaises(proxy.Rejected):
                self.budget.reserve(100)

    def test_daily_token_cap(self):
        with self.budget.connect() as db:
            db.execute("INSERT INTO calls VALUES (?,?)", (time.time() - 61, 99900))
        with self.assertRaises(proxy.Rejected):
            self.budget.reserve(101)

    def test_zero_cap_disables_calls(self):
        self.budget.daily = 0
        with patch.object(self.gateway.opener, "open") as upstream:
            self.assertEqual(self.request()[0], 429)
            upstream.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
