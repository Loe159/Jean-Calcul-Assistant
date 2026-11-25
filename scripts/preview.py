"""Render Jetpack Compose @Preview functions headlessly and check them.

Wraps Google's Android CLI (`android preview list|render`) for this repo:
  scripts/preview list [PATTERN]          FQNs of every @Preview (case-insensitive filter)
  scripts/preview render PATTERN|FQN ...  render matching previews to build/previews/

For every rendered preview it writes:
  build/previews/<PreviewId>.sheet.png   all variants side by side (light, dark, font 200 %, ...)
  build/previews/<pkg>/<File>Kt/<PreviewId>_<n>.png and .json (component tree)
and prints a lint report from the component trees:
  - clickable nodes smaller than 40 dp (COUI IconButton minimum) or 44 dp tall buttons
  - clickable nodes with no text and no content description
  - nodes that extend past the preview's right edge (clipped or overflowing text)

Run it through scripts/preview (enters `nix develop` when needed). See docs/agents/PREVIEWS.md.
"""

import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "previews"
TARGET = ":app"
MIN_TOUCH_DP = 40


def run_android(args):
    proc = subprocess.run(["android", *args], cwd=ROOT, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.stderr.write(proc.stdout[-4000:] + proc.stderr[-4000:])
        log = next((l for l in (proc.stdout + proc.stderr).splitlines() if "Build log:" in l), None)
        sys.exit(f"android {' '.join(args[:2])} failed{' (' + log.strip() + ')' if log else ''}")
    return proc.stdout


def list_previews(pattern=None):
    fqns = [l.strip() for l in run_android(["preview", "list", TARGET]).splitlines()
            if l.strip() and " " not in l.strip() and "." in l]
    if pattern:
        fqns = [f for f in fqns if pattern.lower() in f.lower()]
    return fqns


def resolve(selectors):
    explicit = [s for s in selectors if "Kt." in s]
    patterns = [s for s in selectors if "Kt." not in s]
    fqns = list(explicit)
    if patterns:
        every = list_previews()
        for p in patterns:
            hits = [f for f in every if p.lower() in f.lower()]
            if not hits:
                sys.exit(f"No @Preview matches '{p}'. Run: scripts/preview list")
            fqns += hits
    return list(dict.fromkeys(fqns))


def walk(node, parents=()):
    yield node, parents
    for child in node.get("children", []):
        yield from walk(child, parents + (node,))


def subtree_has_label(node):
    for n, _ in walk(node):
        sem = n.get("semantics", {})
        if sem.get("text") or sem.get("contentDescription"):
            return True
    return False


def describe(tree):
    dark = (tree.get("uiMode", 0) & 0x30) == 0x20
    scale = tree.get("densityDpi", 420) / 160
    w, h = round(tree["width"] / scale), round(tree["height"] / scale)
    return f"{'dark' if dark else 'light'} {w}x{h}dp font {tree.get('fontScale', 1.0):g}"


PROJECT_FILES = {p.name for p in (ROOT / "app" / "src").rglob("*.kt")}


def is_clickable(node):
    return "OnClick" in str(node.get("semantics", {}).get("actions", "")) or "clickable" in node.get("modifiers", {})


def project_location(node, parents):
    """The nearest source line in this repo (library internals point back at their caller)."""
    for n in (node, *reversed(parents)):
        loc = n.get("sourceLocation", {})
        if loc.get("fileName") in PROJECT_FILES:
            return f"{loc['fileName']}:{loc.get('lineNumber', '?')}"
    loc = node.get("sourceLocation", {})
    return f"{loc.get('fileName', '?')}:{loc.get('lineNumber', '?')} (library)"


def lint(tree):
    scale = tree.get("densityDpi", 420) / 160
    width = tree["width"]
    findings = []
    for root in tree.get("components", []):
        for node, parents in walk(root):
            sem = node.get("semantics", {})
            b = node.get("bounds", {})
            where = project_location(node, parents)
            clickable = is_clickable(node) and not any(is_clickable(p) for p in parents)
            if clickable and "disabled" not in sem:
                wdp, hdp = b.get("width", 0) / scale, b.get("height", 0) / scale
                if min(wdp, hdp) < MIN_TOUCH_DP:
                    findings.append(f"touch target {wdp:.0f}x{hdp:.0f}dp < {MIN_TOUCH_DP}dp  {node['name']} @ {where}")
                if not subtree_has_label(node):
                    findings.append(f"clickable without text/contentDescription  {node['name']} @ {where}")
            if sem.get("text") and b.get("right", 0) > width + 1:
                findings.append(f"text past right edge by {(b['right'] - width) / scale:.0f}dp  \"{sem['text'][:40]}\" @ {where}")
    return list(dict.fromkeys(findings))


def sheet(images, dest):
    magick = shutil.which("magick")
    if not magick or len(images) < 2:
        return None
    args = [magick]
    for img in images:
        args += ["(", str(img), "-resize", "x1100", ")"]
    args += ["-background", "#808080", "-splice", "24x0", "+append", "-chop", "24x0", str(dest)]
    subprocess.run(args, check=True)
    return dest


def render(selectors):
    fqns = resolve(selectors)
    OUT.mkdir(parents=True, exist_ok=True)
    args = ["preview", "render", TARGET, "--daemon", "--format=json", "--component-tree", f"--output={OUT}"]
    args += [f"--fqn={f}" for f in fqns]
    raw = run_android(args)
    result = json.loads(raw[raw.index("{"):])
    if not result.get("success"):
        sys.exit(raw)
    by_preview = {}
    for r in result["results"]:
        by_preview.setdefault(r["methodFqn"], []).append(r)
    total = 0
    for fqn, items in by_preview.items():
        items.sort(key=lambda r: r["imagePath"])
        pid = items[0]["previewId"]
        print(f"\n## {fqn}")
        trees = [json.loads(Path(r["componentTreePath"]).read_text()) for r in items]
        for i, (r, t) in enumerate(zip(items, trees), 1):
            print(f"  {i}. {describe(t):32} {r['imagePath']}")
        s = sheet([r["imagePath"] for r in items], OUT / f"{pid}.sheet.png")
        if s:
            print(f"  sheet (variants 1..{len(items)} left to right): {s}")
        for i, t in enumerate(trees, 1):
            for f in lint(t):
                total += 1
                print(f"  ! [{i}] {f}")
    print(f"\n{len(by_preview)} preview(s), {total} lint finding(s). Open the sheets and look before claiming anything.")


def main(argv):
    if len(argv) >= 1 and argv[0] == "list":
        print("\n".join(list_previews(argv[1] if len(argv) > 1 else None)))
    elif len(argv) >= 2 and argv[0] == "render":
        render(argv[1:])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
