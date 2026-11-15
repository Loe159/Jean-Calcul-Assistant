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
if errors:
    raise SystemExit("Untranslated product text:\n" + "\n".join(errors))
print("English UI resources and Jean-Calcul branding: PASS")
