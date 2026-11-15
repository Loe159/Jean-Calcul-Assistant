#!/usr/bin/env python3
"""Fail CI if packaged UI resources regress to Chinese or upstream branding."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

errors = []
for path in Path("app/src/main/res").glob("values*/*.xml"):
    root = ET.parse(path).getroot()
    for element in root:
        if element.tag not in {"string", "string-array", "plurals"}:
            continue
        text = "".join(element.itertext())
        if re.search(r"[\u3400-\u9fff]|\bETA\b|\bEta\b", text):
            errors.append(f"{path}: {element.get('name')}")
if list(Path("app/src/main/res").glob("values-b+zh+*")):
    errors.append("Chinese resource variants must not be packaged")
for path in Path("app/src/main/assets/builtin_skills").rglob("*"):
    if path.suffix in {".md", ".json"} and re.search(r"[\u3400-\u9fff]|\bETA\b|\bEta\b", path.read_text()):
        errors.append(str(path))
# Vendor voice IDs and locale-specific date patterns are protocol/format data.
ui_data_files = {"SpeechVoicePresets.kt", "ConversationTimeLabels.kt"}
for path in Path("app/src/main/kotlin/io/github/mangi/eta/ui").rglob("*.kt"):
    if path.name in ui_data_files:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if line.lstrip().startswith(("//", "*", "/*")):
            continue
        if '"' in line and re.search(r"[\u3400-\u9fff]", line):
            errors.append(f"{path}:{number}")
if errors:
    raise SystemExit("Untranslated product text:\n" + "\n".join(errors))
print("English UI resources and Jean-Calcul branding: PASS")
