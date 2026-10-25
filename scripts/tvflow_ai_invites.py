"""TVFlow-only invitation management. Run locally; no secrets stored in files.

Environment: TVFLOW_SUPABASE_URL and TVFLOW_SUPABASE_SERVICE_ROLE_KEY.
Examples: python scripts/tvflow_ai_invites.py create --days 7 --limit 15
          python scripts/tvflow_ai_invites.py list
          python scripts/tvflow_ai_invites.py revoke --id <UUID>

Never distribute the service-role key or commit generated invitation codes.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

TABLE = "tvflow_ai_access"

def request(method, path, payload=None):
    base = os.environ.get("TVFLOW_SUPABASE_URL", "").rstrip("/")
    token = os.environ.get("TVFLOW_SUPABASE_SERVICE_ROLE_KEY", "")
    if not base.startswith("https://") or not token:
        raise RuntimeError("Configure TVFLOW_SUPABASE_URL (HTTPS) and TVFLOW_SUPABASE_SERVICE_ROLE_KEY in the current session")
    if base != "https://sxynniwybomzkguzomyn.supabase.co":
        raise RuntimeError("Wrong Supabase project: refusing to modify another project")
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(base + "/rest/v1/" + TABLE + path, data=data, method=method,
        headers={"apikey": token, "Authorization": "Bearer " + token,
                 "Content-Type": "application/json", "Prefer": "return=representation"})
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8") or "[]")
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"Supabase returned HTTP {exc.code}; verify permissions and table policy") from None

def main():
    parser = argparse.ArgumentParser(description="Manage private TVFlow AI invitations")
    sub = parser.add_subparsers(dest="command", required=True)
    create = sub.add_parser("create")
    create.add_argument("--days", type=int, default=7)
    create.add_argument("--limit", type=int, default=15)
    sub.add_parser("list")
    revoke = sub.add_parser("revoke")
    revoke.add_argument("--id", required=True)
    args = parser.parse_args()
    if args.command == "create":
        if not 1 <= args.days <= 30 or not 1 <= args.limit <= 50:
            parser.error("Private beta bounds: --days 1..30, --limit 1..50")
        code = secrets.token_hex(24)
        hash_ = hashlib.sha256(code.encode("ascii")).hexdigest()
        expiry = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=args.days)).isoformat()
        response = request("POST", "", [{"invite_code_hash": hash_, "expires_at": expiry,
                                          "daily_request_limit": args.limit}])
        if not response or "id" not in response[0]:
            raise RuntimeError("Invitation not saved")
        print("Invite ID:", response[0]["id"])
        print("ONE-TIME INVITATION CODE (share privately; not stored in database):", code)
    elif args.command == "list":
        result = request("GET", "?select=id,status,created_at,expires_at,daily_request_limit,requests_today&order=created_at.desc&limit=100")
        print(json.dumps(result, indent=2))
    elif args.command == "revoke":
        invite_id = str(uuid.UUID(args.id))
        result = request("PATCH", "?id=eq." + urllib.parse.quote(invite_id), {"status": "revoked"})
        if not result:
            raise RuntimeError("No invitation matched")
        print("Revoked:", invite_id)

if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
