# TVFlow AI — Groq optional, zero paid activation

- Default: **OFF**. No endpoint configured in Android; existing local search remains functional.
- Model: `openai/gpt-oss-20b` via Groq.
- Proxy: `scripts/groq_proxy.py`, Python standard library. No dependency installation required.
- No Groq key in APK. No automatic billing activation.
- Proxy listens **only** on 127.0.0.1:8765. Never forward this endpoint publicly as-is.

## Local proxy smoke test
`python scripts/groq_proxy.py`
With no `GROQ_API_KEY`, HTTP POST `/v1/intent` returns 503 (expected), with no outbound request.

## To enable in a private test environment later
1. Obtain a free-tier Groq API key and set `GROQ_API_KEY` only in the backend environment.
2. Default hard cap: 50 remote requests per day; set `TVFLOW_GROQ_DAILY_CAP=0` to disable.
3. Provide a **separately secured HTTPS backend endpoint** ending in `/v1/intent` as `TVFLOW_AI_ENDPOINT` when building the APK.
4. In production implement device authentication, per-account quotas, atomic persistent budget limiting, HTTPS termination, anti-abuse controls, cache and observability first.
5. Do not ship personal playlist credentials or full IPTV catalogue to Groq; proxy only sends the user search text.

Caution: local backend counters reset on process restart, so they are NOT a production budget cap. Groq's free tier has quotas; never enable pay-as-you-go billing without explicit approval.
