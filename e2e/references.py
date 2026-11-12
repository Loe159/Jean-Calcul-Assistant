"""Fetch only pinned public UI references. No upload, API token or runtime source."""
import hashlib
import json
from pathlib import Path
import urllib.request


def fetch(directory):
    manifest = json.loads((Path(__file__).with_name('references.json')).read_text())
    directory.mkdir(parents=True, exist_ok=True)
    for name, digest in manifest['images'].items():
        path = directory / name
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == digest:
            continue
        url = f'https://raw.githubusercontent.com/Mangi-11/Eta/{manifest["commit"]}/docs/Screenshots/{name}'
        with urllib.request.urlopen(url, timeout=30) as response:
            content = response.read(2_000_001)
        if len(content) > 2_000_000 or hashlib.sha256(content).hexdigest() != digest:
            raise ValueError(f'Reference integrity mismatch: {name}')
        path.write_bytes(content)
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n')
    return manifest
