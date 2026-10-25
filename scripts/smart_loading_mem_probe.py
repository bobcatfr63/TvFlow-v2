"""Sample app PSS during an isolated emulator refresh; not a true peak measurement."""
import re
import subprocess
import time

ADB = r"G:\DevServer\android-sdk\platform-tools\adb.exe"
SERIAL = "emulator-5556"
values = []
started = time.monotonic()
while time.monotonic() - started < 15:
    result = subprocess.run(
        [ADB, "-s", SERIAL, "shell", "dumpsys", "meminfo", "com.tvflow.app"],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=5
    )
    match = re.search(r"TOTAL PSS:\s*(\d+)", result.stdout)
    if match:
        values.append(int(match.group(1)))
    time.sleep(0.2)
print(f"PSS samples={len(values)} minKiB={min(values) if values else 'n/a'} maxKiB={max(values) if values else 'n/a'}")
