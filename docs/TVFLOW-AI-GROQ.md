# TVFlow AI 2.0 — private Groq integration

Model is fixed to `openai/gpt-oss-20b`. No SDK, subscription, billing activation,
cloud runner, paid fallback, or Groq key in Android. A quota is a usage limit;
it cannot verify whether the Groq organization itself has billing enabled.
Use an organization on the Free plan. Never enable billing for this project.

## Architecture

Android settings → authenticated private gateway → Groq → catalog lookup hints.
The original query remains authoritative for genres, release year, film/series
filtering and ranking. Results are always existing repository objects, filtered
to visible categories; Groq cannot supply playable objects or invent availability.
Similarity searches, actor verification and EPG use their existing dedicated
paths without a Groq call.

The optional gateway failure returns null to the unchanged deterministic
catalog engine. Android has a 4.5-second total HTTP deadline, cancellation support,
no redirect following or automatic retries, bounded responses and a 30-second
failure cooldown. When unconfigured, no HTTP call occurs.

The Python gateway:
- Binds exclusively to 127.0.0.1:8765. Do not open firewall ports or use Funnel.
- Requires a separate bearer token of at least 32 characters.
- Caps concurrent handlers at 8 and concurrent Groq requests at 1.
- Validates input and bounds request/response sizes.
- Uses SQLite atomic reservations shared by processes and surviving restarts.
- Maximum 50 attempts/day UTC, 6/minute, 7,000 reserved tokens/minute,
  100,000 reserved tokens/day. Attempts that fail still consume reservations.
- Uses conservative UTF-8 byte-based token reservation plus 512 output tokens
  and framing overhead. It may stop earlier than Groq's own free quota.
- Caches 256 successful normalized queries for one hour in memory.
- Stops upstream calls for 60 seconds after errors, including 429.
- Never logs keys, tokens, queries, playlist credentials or catalogs.
- Sends only the user search text and a static instruction to Groq.
- Rejects redirects to prevent credential forwarding.
- Saves actual successful upstream usage and elapsed seconds in the budget DB.

Reference free limits checked 2026-10-23:
https://console.groq.com/docs/rate-limits
30 RPM, 1,000 RPD, 8,000 TPM, 200,000 TPD for this model.
The account's exact limits may differ and are shared at organization level.

## Secure Windows setup

Revoke any key previously pasted into a chat, then generate a replacement.
Run on DESKTOP-8VD83BL, as the Windows account that will launch the gateway:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File G:\DevServer\repos\TvFlow-v2\scripts\configure-groq.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File G:\DevServer\repos\TvFlow-v2\scripts\start-groq.ps1
```

The first script prompts with Read-Host -AsSecureString. It stores the Groq key
and a separately generated proxy token with Windows DPAPI under
G:\DevServer\secrets\tvflow-groq. Directory ACLs grant only this user and SYSTEM.
The launch script decrypts into the child process environment only; it clears
its environment afterward. No secret is supplied in command arguments.

Do not paste the Groq key into the Android settings. Android explicitly rejects
gsk_ keys. The device-owned proxy token is encrypted with Android Keystore AES-GCM
and is excluded from Android backups (application allowBackup=false).
Gateway settings are intentionally absent from TVFlow portable backups.

## Device access

For private emulator debugging only:

```
adb -s emulator-5554 reverse tcp:8765 tcp:8765
```

In a DEBUG APK, configure http://127.0.0.1:8765/v1/intent and the private proxy
token through Settings → TVFlow AI 2.0. Cleartext is accepted only for debug
loopback; release builds accept HTTPS only.

For a physical TV or phone, a separately configured private HTTPS reverse proxy
(for example within an existing VPN) must reach the loopback gateway. Use
https://private-host[:port]/v1/intent. Authentication remains mandatory.
No public listener or automatic tunnel is installed by this change. Until a
private HTTPS route is configured and verified, physical devices use local AI.
Do not disable TLS verification or embed credentials in endpoint URLs.
The HTTPS endpoint may also be supplied as TVFLOW_AI_ENDPOINT at build time;
the proxy token is always provisioned at runtime.

## Verification

Run Python tests with no real Groq API calls:

```powershell
python -m unittest discover -s scripts -p test_groq_proxy.py -v
```

Android checks (local Windows PC only):

```
gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=2
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=2
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -e class com.tvflow.app.ui.search.GroqIntentDeviceTest com.tvflow.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use a disposable AVD for Gradle connectedDebugAndroidTest: its test lifecycle may
uninstall the target app, deleting Android Keystore credentials. On an existing
configured test device, use the manual ADB commands above and never uninstall the
target app. A file-only backup cannot restore an Android Keystore secret.

Device tests use isolated preferences and a loopback fake HTTP server.
They validate Android Keystore storage, proxy authentication, disabled gateway,
network failures, response validation and cooldown. They do not measure Groq
latency or prove IPTV playback.

A real validation must additionally record French movies, series genres,
combined criteria, similarity, empty results, Groq latency/usage, TV remote
navigation, phone layout and actual video progression. Do not publish an
official release based solely on compilation or mocked integration tests.

## Compatibility changes found during validation

Core library desugaring supports java.time and java.util.Base64 on API 24–25.
Notifications explicitly check permission and handle SecurityException.
Media3 unstable APIs are opted into; Compose reads LocalConfiguration.
No IPTV credentials, database schema, playlists, backups or player behavior
are changed.
