"""Loopback-only Xtream fixture for isolated Android emulator smoke tests.

Usage: python -u scripts/smart_loading_fixture.py --scenario full
Then: adb -s emulator-5556 reverse tcp:18888 tcp:18888
Login to http://127.0.0.1:18888 with demo/demo on the emulator.
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

parser = argparse.ArgumentParser()
parser.add_argument("--scenario", choices=["full", "live-500", "series-500", "empty", "vod-truncate-once"], default="full")
parser.add_argument("--vod-count", type=int, default=0, help="Extra synthetic films; max 50000")
args = parser.parse_args()
if not 0 <= args.vod_count <= 50_000:
    parser.error("--vod-count must be between 0 and 50000")
if args.vod_count:
    print(f"Generating {args.vod_count} extra synthetic films", flush=True)

DATA = {
    "get_live_categories": [{"category_id": "live", "category_name": "Test Live"}],
    "get_live_streams": [{"stream_id": 101, "name": "Synthetic Channel", "category_id": "live", "stream_type": "live"}],
    "get_vod_categories": [{"category_id": "movies", "category_name": "Test Movies"}],
    "get_vod_streams": [{"stream_id": 201, "name": "Synthetic Movie (2024)", "category_id": "movies", "container_extension": "mp4"}],
    "get_series_categories": [{"category_id": "shows", "category_name": "Test Shows"}],
    "get_series": [{"series_id": 301, "name": "Synthetic Series (2023)", "category_id": "shows"}],
    "get_short_epg": {"epg_listings": []},
}

if args.vod_count:
    DATA["get_vod_streams"] += [
        {"stream_id": 1000 + i, "name": f"Synthetic Film {i}", "category_id": "movies", "container_extension": "mp4"}
        for i in range(args.vod_count)
    ]

REQUESTS = {"get_vod_streams": 0}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlsplit(self.path)
        query = parse_qs(parsed.query)
        action = query.get("action", ["auth"])[0]
        if parsed.path != "/player_api.php":
            self.send_error(404)
            return
        if query.get("username") != ["demo"] or query.get("password") != ["demo"]:
            payload = {"user_info": {"auth": 0, "status": "Disabled"}}
        elif action == "auth":
            payload = {"user_info": {"auth": 1, "status": "Active", "username": "demo"}, "server_info": {"url": "127.0.0.1", "port": "18888", "protocol": "http"}}
        elif (args.scenario == "live-500" and action == "get_live_streams") or (args.scenario == "series-500" and action == "get_series"):
            self.send_response(500)
            self.send_header("Content-Length", "0")
            self.end_headers()
            print(f"HTTP 500 {action}", flush=True)
            return
        else:
            payload = [] if args.scenario == "empty" and action != "get_short_epg" else DATA.get(action, [])
        body = json.dumps(payload).encode()
        if args.scenario == "vod-truncate-once" and action == "get_vod_streams":
            REQUESTS[action] += 1
            if REQUESTS[action] == 1:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                time.sleep(0.5)
                self.wfile.write(body[:max(1, len(body) // 3)])
                self.wfile.flush()
                self.close_connection = True
                print(f"TRUNCATED {action} bytes={len(body)//3}/{len(body)}", flush=True)
                return
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        print(f"HTTP 200 {action}", flush=True)

    def log_message(self, *_args):
        pass

print(f"Fixture ready on 127.0.0.1:18888; scenario={args.scenario}", flush=True)
ThreadingHTTPServer(("127.0.0.1", 18888), Handler).serve_forever()
