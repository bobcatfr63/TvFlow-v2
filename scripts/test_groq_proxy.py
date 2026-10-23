"""Offline safety tests: no API key must never trigger an upstream call."""
import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from unittest.mock import patch

import groq_proxy


class ProxyTests(unittest.TestCase):
    def test_disabled_api_makes_no_outbound_requests(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), groq_proxy.Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}/v1/intent"
            payload = json.dumps({"query": "Je veux un thriller francais recent"}).encode()
            with patch.object(groq_proxy, "KEY", ""), patch("urllib.request.urlopen", wraps=urllib.request.urlopen) as request:
                try:
                    urllib.request.urlopen(urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"}), timeout=3)
                    self.fail("Expected disabled status")
                except urllib.error.HTTPError as error:
                    self.assertEqual(error.code, 503)
                self.assertEqual(request.call_count, 1, "No upstream request should occur")
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
