"""Read-only Compose UI hierarchy probe. Only the isolated Android emulator is allowed."""
import re
import sys
import subprocess
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
import xml.etree.ElementTree as ET

ADB = r"G:\DevServer\android-sdk\platform-tools\adb.exe"
SERIAL = "emulator-5556"

def adb(*args):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], text=True, encoding="utf-8", errors="replace")

adb("shell", "uiautomator", "dump", "/sdcard/tvflow-hierarchy.xml")
tree = ET.fromstring(adb("shell", "cat", "/sdcard/tvflow-hierarchy.xml"))
for node in tree.iter("node"):
    title = node.attrib.get("text", "").strip() or node.attrib.get("content-desc", "").strip()
    if title:
        print(f'{title[:90]:90} {node.attrib.get("bounds", "")}')
