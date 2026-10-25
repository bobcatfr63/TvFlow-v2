# TVFlow V2 — Smart Loading validation

## Scope and safety
Local Windows repository: G:\DevServer\repos\TvFlow-v2.
No Git commit or release published. Mi Box and personal phone untouched.
Existing uncommitted changes, including Groq integration, preserved.

## Root causes and changes
1. Xtream can respond with an empty array on a temporary server error. Previously, Room catalog replacement could delete existing rows. Added CatalogSyncPolicy.shouldReplaceCatalog and guards for live, VOD, and series before replacing populated local catalogs. Initial genuinely empty catalogs are allowed.
2. The manual full refresh in XtreamRepository.refreshAllWithProgress still used a combined media transaction. It now calls the independent VOD/series refresh path.
3. Coroutine cancellation during login's optional media refresh could be swallowed by runCatching. Cancellation is now rethrown. The same rule applies to profile switching and fallback refresh.
4. Room VOD and series replacements remain separate @Transaction methods and preserve selected metadata.

## Verified
- Gradle local :app:testDebugUnitTest :app:assembleDebug — BUILD SUCCESSFUL in 29s after final live guard edit.
- Gradle local :app:assembleDebugAndroidTest — passed.
- Android TV emulator-5554: two in-memory Room instrumentation tests passed, verifying that refreshing films preserves series and vice versa, and that existing metadata survives refresh.
- Android TV emulator-5554: APK installed and launched; logs showed first pre-draw ~1126ms, prefetch 18/18 images, no AndroidRuntime fatal entry in inspected filtered logs.
- Unit tests for catalog empty-response policy passed before final live guard edit.

## Not yet validated
- Cold start with new Xtream credentials on an isolated empty app profile.
- Real HTTP failures/timeouts and cancellation during Xtream download.
- 100,000-film load and peak memory consumption.
- Real end-to-end playback, EPG, and server reconnect.
- Release signing/publishing.

## Next
Run isolated end-to-end first-login tests using a disposable profile; do not clear existing emulator credentials or application data.

## 2026-10-25 follow-up: synthetic stress + loopback failures
- Git HEAD at inspection: 3287e95; existing uncommitted work preserved (including Groq).
- Both Android emulators detected; physical Mi Box and phone were not used.
- Added `CatalogLargeDeviceTest.kt`: Room in-memory 100,000-film insert and full replacement on Android TV emulator-5554. Instrumentation result: OK (1 test), 11.399 s overall. Logcat `CatalogStress`: insert=5334 ms, replace=5556 ms, reported allocated heap=23 MiB and native heap=24 MiB **at the end**, not peak. Existing local synopsis survived. This does not benchmark network/JSON decoding, cold start, peak RAM or weaker physical TV hardware.
- Added `XtreamLocalNetworkTest.kt`: 3 local HTTP tests passed (HTTP 500 for series with successful VOD, timeout treated as failure, legitimate empty array). These exercise Retrofit deserialization and transport, not full repository integration.
- Fixed `refreshAllWithProgress` so failure of live catalog does not block attempts to refresh VOD/series. Reports partial refresh if one branch succeeds and throws only when both fail. Added unit tests for COMPLETE/PARTIAL/FAILED outcome.
- Local Gradle `:app:testDebugUnitTest :app:assembleDebug --offline` passed: BUILD SUCCESSFUL in 37 s after code changes. Targeted HTTP test suite: 3 tests, 0 failures.
- APK installed and MainActivity started on Android TV emulator-5554 after changes; no fatal exception in the filtered log excerpt. No new Release/commit.

## Remaining gates
- End-to-end fresh profile / first Xtream login with controlled local HTTP server and real navigation.
- Real disconnect/reconnect during full repository sync, partial retry and cancellation tests.
- Large JSON payload parsing and peak RAM measurements on target Android TV hardware.
- Real playback, EPG and signed release verification.

## 2026-10-25: isolated emulator end-to-end tests and partial-status fix

- Inspected git status (HEAD 3287e95), repository sync paths, DTOs, SettingsViewModel, and emulator state. Existing Groq/uncommitted changes preserved.
- Added scripts/smart_loading_fixture.py: Python standard-library Xtream HTTP fixture, bound to 127.0.0.1:18888 and reached ONLY through adb reverse on emulator-5556. Fake credentials demo/demo; no external IPTV service.
- On emulator-5556, with no configured IPTV source initially, installed Debug APK preserving application data (adb install -r -d) and logged in to fixture full scenario. Fixture logs confirmed auth, live categories/streams, VOD categories/streams, series categories/streams. UI showed Connected, Synthetic Movie (2024) under Films and Synthetic Series (2023) under Series. This is an actual first-login UI/network/database smoke test with synthetic content.
- Reproduced HTTP 500 from get_series during Settings > Actualiser; Retrofit logged HttpException, the previous Synthetic Series remained visible, and films refreshed. Identified root cause: media method returned Unit even on partial success, so overall refresh incorrectly reported COMPLETE. Fixed private refreshMediaCatalog to return CatalogRefreshOutcome, combined live/media status with combinedCatalogRefreshOutcome, and SettingsViewModel now retains the final PARTIAL message instead of overwriting it.
- Reinstalled corrected Debug APK on emulator-5556; repeated get_series HTTP 500. Settings UI explicitly showed 'Actualisation partielle : catalogue disponible conservé'; Synthetic Series (2023) remained visible after the failed refresh.
- Tested fresh second profile with http://localhost:18888 on emulator-5556 while fixture returned HTTP 500 for get_live_streams. Authentication succeeded, media endpoints returned 200, profile became active, and Synthetic Series (2023) was visible. This validates media-only first-login partial success without changing the previous test profile.
- Unit tests + APK build: :app:testDebugUnitTest :app:assembleDebug --offline, BUILD SUCCESSFUL in 28s. Added regression assertions for media PARTIAL and failed live with successful media.
- Android TV emulator-5554: installed current APK and instrumentation APK without clearing data. CatalogTransactionsDeviceTest: OK (2 tests). MainActivity started; first pre-draw 1163ms; image prefetch 18/18; no AndroidRuntime fatal entries in inspected filtered logs.
- Physical Mi Box and phone untouched; no cloud runner, commit or Release. Test-only profiles on emulator-5556 remain (not deleted).

## Remaining before 100% sign-off

- End-to-end network disconnect/reconnect and cancellation while an active large Xtream download is running.
- Large Xtream JSON transfer + decoding benchmark and *peak* memory on low-end Android TV hardware; previous 100k Room benchmark is not this test.
- Real authorized IPTV stream playback, EPG and long-running recovery checks.
- Final signed release smoke test and publishing only after all gates pass.

## 2026-10-25 05:29 Europe/Paris — reconnect/cancellation and profile-switch resilience

- Inspected current Git status (HEAD 3287e95), both emulators, XtreamRepository, XtreamLocalNetworkTest and loopback fixture. No physical devices used; unrelated uncommitted files preserved.
- Found a remaining path where `switchProfile` and active-profile fallback after `deleteProfile` attempted media only after successful live refresh. Corrected both paths so live failure is logged and does not prevent the media attempt; coroutine cancellation is rethrown.
- Extended loopback-only JVM Retrofit tests with (a) HTTP 500 followed by a successful second request, (b) cancellation of an in-flight VOD request, (c) decoding 10,000 synthetic VOD rows. Test XML `app/build/test-results/testDebugUnitTest/TEST-com.tvflow.app.data.remote.XtreamLocalNetworkTest.xml` reports 6 tests, 0 failures/errors. The 10k parsing sample took 30ms on the Windows JVM; this is NOT Android TV peak-memory/network performance.
- First attempt failed Kotlin test compilation because `cancelAndJoin` extension import was missing; corrected import and reran.
- Full local `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain`: BUILD SUCCESSFUL in 38s.
- Installed latest debug APK on emulator-5554 only and launched MainActivity: first pre-draw 1380ms, image prefetch 18/18, no AndroidRuntime fatal lines in the inspected filtered log. Post-start `dumpsys meminfo`: TOTAL PSS 236105 KiB, TOTAL RSS 357836 KiB; these are snapshots with a preexisting catalog, NOT peak consumption during large Xtream sync.
- No real authorized IPTV playback test, no physical device install, no commit or Release.

### Outstanding release gates
- Android end-to-end active-download interruption/reconnect with large payload and full UI state validation.
- Peak memory profiling while parsing/downloading a large JSON catalog on low-end Android TV.
- Authorized real playback and EPG/reconnect smoke tests.
- Release-signing and distribution validation, only when above checks pass.

## 2026-10-25 — Android TV: interrupted 100k-film HTTP transfer and recovery

- Rechecked repository HEAD `3287e95`, working tree, and adb targets. Only emulator-5554 was used; the physical phone and Mi Box were not accessed. Uncommitted Groq work was left intact.
- Added `app/src/androidTest/java/com/tvflow/app/data/remote/XtreamReconnectDeviceTest.kt`: an isolated loopback HTTP server inside the Android TV instrumentation process. The first 100,000-item Xtream VOD JSON response advertises a full Content-Length but closes after one third of its bytes; the Retrofit request **must fail**, not return an empty catalog. The second request receives the complete 100,000 synthetic entries and verifies the final ID.
- Initial instrumentation failed because JUnit requires a void test method; corrected the Kotlin expression-body return type. Rebuilt and reran successfully.
- Android TV emulator-5554: `am instrument -w -e class com.tvflow.app.data.remote.XtreamReconnectDeviceTest ...` → **OK (1 test)**. Logcat: `truncated-response-rejected=true recovered=100000 elapsedMs=481 sampledPssKiB=184818`. The PSS value is the highest value **sampled at 20ms intervals during the second request**, not a guaranteed process peak or a production device measurement.
- Gradle `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline` → **BUILD SUCCESSFUL**; subsequent instrumentation rebuild → **BUILD SUCCESSFUL in 6s**.
- Test uses no real IPTV credentials, no persistent app profile and no remote network; only emulator loopback. No cloud runner, commit, release, or destructive app-data operation.
- This verifies transport-level failure/recovery and Android JSON deserialization under load, **not** end-to-end repository retry, UI behavior after an actual Wi-Fi outage, EPG or video playback.

### Still outstanding
- Full UI/repository flow with a network interruption mid-sync and preserved catalog; test on a representative low-memory Android TV device.
- Real authorized IPTV playback/EPG and recovery, sustained soak test, and signed release verification.
- Final targeted Android TV regression suite (XtreamReconnectDeviceTest + CatalogTransactionsDeviceTest + CatalogLargeDeviceTest): **OK (4 tests)** in 12.367 s. App subsequently relaunched on emulator-5554: first pre-draw 1230ms, prefetch 18/18, no AndroidRuntime fatal in inspected startup logs.

## 2026-10-25 07:23 Europe/Paris — 100k HTTP-to-Room pipeline on Android TV

- Re-inspected `git status --short`, Smart Loading validation history, XtreamRepository sync paths, fixture, existing device tests, and ADB device list. Only emulator-5554 was used; physical phone and Mi Box untouched. Existing uncommitted changes (including Groq) preserved.
- Added `app/src/androidTest/java/com/tvflow/app/data/remote/XtreamToRoomLargeDeviceTest.kt`. This test serves a synthetic 100,000-film Xtream JSON catalog on Android TV's own 127.0.0.1 socket, downloads/deserializes via Retrofit/Gson, maps to Room entities, writes to a disposable **in-memory** TVFlowDatabase, and verifies all 100,000 rows. It does not access saved IPTV credentials or persistent app data.
- Local Gradle `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` → BUILD SUCCESSFUL in 24s.
- New Android TV instrumentation test → OK (1 test), 8.115s. Logcat `CatalogPipeline`: `count=100000 httpParseMs=1199 roomMs=5906 sampledPssKiB=192043`. PSS sampled at 25ms intervals, **not a guaranteed peak**, and measured on the emulator rather than a low-memory physical Android TV.
- Targeted Android TV regression suite: `XtreamReconnectDeviceTest`, `XtreamToRoomLargeDeviceTest`, `CatalogTransactionsDeviceTest`, `CatalogLargeDeviceTest` → **OK (5 tests)** in 18.685s.
- No application production code changed in this run, no commit, no Release, no cloud runner.
- Still not a full UI/repository synchronization test under a real Wi-Fi outage. No authorized playback/EPG soak test or signed Release smoke test has been performed. Smart Loading **not yet 100% signed off**.

## 2026-10-25 — emulator UI: truncated 30k-film Xtream response, retry and cache consistency

- Rechecked local repo `G:\DevServer\repos\TvFlow-v2`, Git HEAD `3287e95`, uncommitted changes, and ADB. Only `emulator-5554` and `emulator-5556` were connected; no physical devices used.
- Expanded the **loopback-only** `scripts/smart_loading_fixture.py` with `--scenario vod-truncate-once --vod-count 30000`. First VOD response advertises 3,249,999 bytes but closes after 1,083,333 bytes; the second returns a complete catalog containing the original Synthetic Movie plus 30,000 additional synthetic films.
- With the existing **disposable synthetic IPTV profile** on `emulator-5556`, triggered Settings > Actualiser. The fixture recorded the truncated VOD transfer, and app logcat reported `java.net.ProtocolException: unexpected end of stream` in `TVFlowCatalog` rather than a fatal crash. The first-failure UI message was not captured in this run.
- Triggered Actualiser again. The fixture confirmed HTTP 200 for live, VOD and series. Settings displayed `Genres TMDB sur le catalogue actif : films 0/30001 ... séries 0/1` and later `Catalogues actualisés`. This verifies the actual repository + persistent Room + UI retry path for a **simulated HTTP truncation**, not a true Wi-Fi disconnect.
- Root cause found in production: the manual Live refresh duplicated the standard Live refresh path but omitted `applyRestoredLiveOrganization(profileId)` and `invalidateSearchCache(profileId)`, risking lost restored channel organization and stale Live search after manual refresh. Added both after the Live transaction, preserving the existing favorites step. This is a targeted production fix, not a broader refactor.
- Rebuilt with local Gradle `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` → **BUILD SUCCESSFUL in 45s**. Installed updated debug APK with `adb install -r` on emulator-5556 **without clearing app data**, refreshed the 30,001-film fixture successfully and observed `Catalogues actualisés` again.
- Sampled `dumpsys meminfo` during the updated refresh: 43 PSS samples over 15s, min 153,604 KiB, max **241,699 KiB** (~236 MiB). This is the highest sampled PSS, **not** a guaranteed peak or low-memory Android TV measurement.
- Reinstalled current debug/test APKs on Android TV `emulator-5554` without clearing data. Targeted instrumentation suite (`CatalogTransactionsDeviceTest`, `CatalogLargeDeviceTest`, `XtreamReconnectDeviceTest`, `XtreamToRoomLargeDeviceTest`) → **OK (5 tests)** in 19.502s.
- No `AndroidRuntime:E` output in the inspected filtered recent logs on either emulator. `git diff --check` returned no whitespace errors (Git emitted only existing LF/CRLF conversion warnings).
- Added read-only emulator UI hierarchy probe `scripts/smart_loading_ui_probe.py` and sampled-memory helper `scripts/smart_loading_mem_probe.py`. No real IPTV credentials used. Existing unrelated uncommitted work, including Groq, preserved. No cloud runner, commit or Release.

### Remaining sign-off gates
- End-to-end **actual Wi-Fi disconnect/reconnect** while a large Xtream sync is active, including explicit UI state and data-retention assertions after the failure.
- Memory and long-running stability on a representative low-memory physical Android TV (not the user's Mi Box without separate permission).
- Authorized IPTV playback and EPG correctness/reconnect smoke test.
- Signed release verification and publication only after functional sign-off.

## 2026-10-25 — UI failure-state evidence and cancellation cleanup

- Rechecked HEAD `3287e95`, Git working tree (including unrelated uncommitted Groq changes), and ADB targets. Only `emulator-5554` (Android TV) and `emulator-5556` (Android phone emulator) were used; no physical devices or cloud runners.
- Root cause addressed in `SettingsViewModel.refresh()`: `runCatching` wrapped the suspend repository refresh, potentially treating `CancellationException` as an ordinary error; the subsequent 500ms success delay could also be cancelled before the old cleanup lines, leaving `busy` / `_refreshProgress` uncleared. Replaced with explicit `try/catch` that rethrows cancellation and `finally` that resets both state flows. This is a production-code fix; cancellation itself was **not** independently exercised in an instrumented ViewModel test.
- Local `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` completed **BUILD SUCCESSFUL in 19s**. Both emulator APK installs used `adb install -r` and preserved app data.
- Android TV emulator-5554 targeted instrumentation (`CatalogTransactionsDeviceTest`, `CatalogLargeDeviceTest`, `XtreamReconnectDeviceTest`, `XtreamToRoomLargeDeviceTest`): **OK (5 tests)** in 19.935s.
- On emulator-5556, started loopback-only `scripts/smart_loading_fixture.py --scenario vod-truncate-once --vod-count 3000` and temporary `adb reverse tcp:18888 tcp:18888`. Used the existing synthetic `localhost:18888` profile, not real IPTV credentials.
- Pressed **Actualiser** from Settings. Fixture logged `TRUNCATED get_vod_streams bytes=106666/319999`, and emulator logcat recorded `W/TVFlowCatalog: VOD refresh failed` with `java.net.ProtocolException: unexpected end of stream`. Crucially, the **visible Settings UI** was inspected via `uiautomator` and showed **`Actualisation partielle : catalogue disponible conservé`** after failure (this message was not captured in the preceding run).
- Pressed **Actualiser** again. Fixture logged HTTP 200 for live, VOD and series endpoints, and the visible Settings UI changed to **`Catalogues actualisés`**. No fatal app crash was found in the filtered logs inspected for this scenario. This is a simulated truncated HTTP transfer + manual UI retry, **not** a physical Wi-Fi disconnect or automatic retry.
- `git diff --check` reported no whitespace errors; Git emitted only existing LF/CRLF conversion warnings. Test fixture was stopped and the temporary port-18888 ADB reverse removed; other existing reverse ports were untouched. No persistent app data or profiles deleted. No commit or Release.

### Still blocking 100% sign-off

- True Wi-Fi / network-path disconnect-reconnect during a full large Xtream sync, with UI and local-cache assertions.
- Dedicated lifecycle-cancellation instrumentation regression for the new Settings refresh cleanup.
- Low-memory physical Android TV soak / peak-memory test (without using the user's Mi Box unless explicitly permitted).
- Authorized real stream playback, EPG and reconnect verification; signed release smoke test and publication after all gates pass.

## 2026-10-25 10:30 Europe/Paris — cancellation-safe Settings operations

- Confirmed local Windows PC DESKTOP-8VD83BL online; Git HEAD `3287e95`; `git status --short` inspected before edits. Only `emulator-5554` and `emulator-5556` were connected to ADB. No physical devices, no cloud runners.
- Root cause: `SettingsViewModel.launchBusy` used `runCatching { block() }` for profile switching and other Settings operations. This catches `CancellationException` as a normal error and cleanup was not in `finally`; a cancelled profile refresh could leave the UI busy or display a misleading error.
- Extracted the focused `runSettingsBusyOperation` helper and wired `launchBusy` to it. It rethrows coroutine cancellation, reports ordinary exceptions, and always resets the busy state in `finally`. Existing `SettingsViewModel.refresh()` cancellation fix was preserved.
- Added `SettingsBusyOperationTest.kt`: cancellation while suspended, normal failure, and success. Local test XML: **3 tests, 0 failures, 0 errors**; cancelled action does not report success and busy returns to false.
- Local `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` → **BUILD SUCCESSFUL in 20s**.
- Installed debug APK and instrumentation APK using `adb install -r` on `emulator-5554` (no app-data wipe). Targeted Room/transport/large-pipeline instrumentation suite → **OK (5 tests)** in **19.179s**.
- Launched `com.tvflow.app/.MainActivity` on Android TV emulator-5554: `am start -W` returned **Status: ok**, **LaunchState: COLD**, **TotalTime: 2842ms**. App PID observed; startup logs showed first pre-draw 1379ms, prefetch 18/18, and no `AndroidRuntime:E` lines in the inspected emulator log. Post-start `dumpsys meminfo` reported **TOTAL PSS 209,984 KiB** (snapshot, not peak).
- `git diff --check` produced no whitespace errors; only existing LF/CRLF warnings. No Git commit, release, deletion, or change to unrelated Groq code.
- **Not validated in this run:** cancellation of the actual Settings ViewModel by navigating away during an Xtream request; real Wi-Fi/network disconnection and automatic recovery during full sync; long soak/low-memory device; authorized IPTV playback and EPG; signed release. Unit tests exercise the extracted busy-operation helper, not a complete UI cancellation scenario. Smart Loading is not signed off at 100%.

## 2026-10-25 11:30 Europe/Paris — Android TV HTTP failure + Room cache-retention regression

- Inspected `git status --short`, HEAD `3287e95`, Smart Loading implementation and prior validation report on local Windows `G:\DevServer\repos\TvFlow-v2`. Only ADB `emulator-5554` (Android TV) and `emulator-5556` (phone emulator) were connected; no physical device was used.
- Added `app/src/androidTest/java/com/tvflow/app/data/remote/XtreamCacheRetentionDeviceTest.kt`, an isolated instrumentation regression using synthetic HTTP responses and an **in-memory Room database** (no real Xtream account or persistent profile).
- Scenario: HTTP 200 containing 3 films -> persist and assert 3 original rows; next HTTP 200 deliberately advertises a full Content-Length but closes early -> Retrofit must fail, and Room must still contain the same 3 original films; third HTTP 200 returns 5 updated films -> persist and assert 5 updated rows.
- Initial instrumentation attempt exposed a **test-only JUnit signature mistake**: expression-bodied test returned a non-void value. Fixed to a void test method, recompiled and reran successfully. This was not an app-production bug.
- Local Gradle `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain`: **BUILD SUCCESSFUL in 10s**. Following the test fix, `:app:testDebugUnitTest :app:assembleDebugAndroidTest --offline --console=plain`: **BUILD SUCCESSFUL in 12s**.
- Installed debug APK and instrumentation APK using `adb -s emulator-5554 install -r`, without clearing data. New isolated Android TV instrumentation: **OK (1 test)**. Targeted regression suite including cache retention, 100k HTTP-to-Room, HTTP reconnect, and Room transactions: **OK (6 tests)** in **18.71s**.
- Android TV cold start `am start -W`: **Status: ok; TotalTime: 3012ms**. No `AndroidRuntime:E` lines in the last 400 logcat entries filtered for fatal AndroidRuntime. This is a limited log inspection, not a stability guarantee.
- Logs: `G:\DevServer\logs\smart-loading-run10-retest.log` and `G:\DevServer\logs\smart-loading-run10-device-tests.log`. The first compile log `smart-loading-run10-gradle.log` remains untracked in the repo; nothing deleted.
- No production Kotlin changes, no cloud runner, no Groq edits, no commit, no Release, no Mi Box or personal-phone interaction.
- **Limitations:** This verifies Retrofit transport failure + conditional Room persistence, **not** the entire repository/UI orchestration under a real Wi-Fi outage. Actual network-path disconnect/reconnect, UI lifecycle cancellation, prolonged low-memory Android TV stability, authorized real IPTV playback/EPG, and signed-release verification remain untested. **Not 100% signed off**.


## 2026-10-25 12:30 Europe/Paris — empty Xtream response and UI recovery

- Re-inspected Windows local repo `G:\DevServer\repos\TvFlow-v2`, `git status --short`, HEAD `3287e95`, Smart Loading policy/test files and ADB state. Both connected ADB targets were emulators (`emulator-5554` Android TV, `emulator-5556` Android phone emulator); neither the physical Mi Box nor personal phone was used. All existing uncommitted Groq and other work was preserved.
- Tested **real app Settings refresh** on emulator-5556 with its existing disposable synthetic `localhost:18888` profile. A loopback-only fixture (`scripts/smart_loading_fixture.py --scenario empty`) returned HTTP 200 with empty live/VOD/series lists. The fixture logged all seven relevant requests, including auth, live, VOD and series. The visible Settings UI reported `Actualisation impossible : direct et médias indisponibles` rather than claiming success. Importantly, the TMDB catalog coverage UI still displayed **films 0/3001, séries 0/1** after the empty refresh: existing persistent catalog was not replaced by empty responses.
- Stopped the empty fixture and restarted the same loopback fixture with `--scenario full --vod-count 3000`. Retried **Actualiser** on the same active synthetic profile. The visible Settings UI changed to **`Catalogues actualisés`**, and the catalog coverage UI still displayed **films 0/3001, séries 0/1**. No profile or app data was cleared. This is a UI/repository/Room integration smoke test with a synthetic Xtream server, **not** a real network disconnect or authorized stream playback.
- Local Windows Gradle `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` → **BUILD SUCCESSFUL in 7s**, all 85 tasks up-to-date. Existing XML results show **139 JVM tests across 21 suites, 0 failures/errors**; Gradle did **not** re-execute those up-to-date tests during this run. Log: `G:\DevServer\logs\smart-loading-run11-gradle.log`.
- Installed the existing Debug and AndroidTest APKs on Android TV `emulator-5554` using `adb install -r` (data preserved). Instrumentation tests for Room transactions, large Room catalog, 100k HTTP reconnect, 100k HTTP-to-Room and cache retention → **OK (6 tests)** in **19.066s**. Log: `G:\DevServer\logs\smart-loading-run11-device-tests.log`.
- Android TV emulator cold start `am start -W` → **Status: ok, LaunchState: COLD, TotalTime: 2811ms**; process PID observed. No `AndroidRuntime:E` entries in the inspected filtered startup log. Post-start PSS snapshot: **140,536 KiB**, not peak during sync. `git diff --check` returned no whitespace errors (only existing line-ending warnings).
- Stopped the temporary fixture and removed **only** the temporary `tcp:18888` reverse on emulator-5556. Existing reverse ports 8765 and 18765 remained unchanged. No production Kotlin changes, no deletions, no commit/Release, no cloud runner.

### Remaining before 100% sign-off

- End-to-end **actual network-path outage and restoration** while a full Xtream synchronization is active, including UI/cached-catalog assertions and automatic-retry behavior (today's tests used empty HTTP 200 and manual retry, not Wi-Fi loss).
- Lifecycle cancellation from Settings UI during an active sync; sustained low-memory Android TV/peak-memory profiling (physical Mi Box remains out of scope).
- Authorized real IPTV playback with EPG and reconnection; signed release smoke test after all functional gates pass.
- **Status: NOT 100% validated; continue next scheduled run.**
