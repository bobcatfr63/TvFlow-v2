"""Explicit live verification; run only via start-groq.ps1 -Verify with a Free key.
Uses the same persistent local budget; never prints credentials or exception text.
Does not prove catalog availability or player behavior.
"""
import json
import os
import time
from groq_proxy import Budget, Gateway, Rejected

QUERIES = [
    "Je cherche une comedie francaise",
    "Une serie policiere et mysterieuse",
    "Un film action et aventure sorti en 2025",
    "Un film de science fiction avec des extraterrestres",
]

def main():
    if not os.getenv("GROQ_API_KEY"):
        raise SystemExit("No key configured; no outbound request made.")
    budget = Budget(os.environ["TVFLOW_GROQ_STATE"])
    gateway = Gateway(budget)
    results = []
    for query in QUERIES:
        start = time.monotonic()
        try:
            result = gateway.rewrite(query)
            results.append({"query": query, **result, "elapsed_ms": round((time.monotonic() - start) * 1000)})
        except Rejected as error:
            results.append({"query": query, "status": error.code, "error": error.reason})
            break
    with budget.connect() as db:
        day = int(time.time() // 86400) * 86400
        calls, reserved = db.execute("SELECT count(*),coalesce(sum(reserved),0) FROM calls WHERE at>=?", (day,)).fetchone()
        usage = db.execute("SELECT tokens,latency FROM usage WHERE at>=?", (day,)).fetchall()
    print(json.dumps({"model": "openai/gpt-oss-20b", "results": results,
                      "daily_local_attempts": calls, "daily_reserved_tokens": reserved,
                      "successful_actual_tokens": sum(row[0] for row in usage)}, ensure_ascii=True, indent=2))
    return 0 if len(results) == len(QUERIES) and all("normalized_query" in result for result in results) else 1

if __name__ == "__main__":
    raise SystemExit(main())
