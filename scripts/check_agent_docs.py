#!/usr/bin/env python3
"""Offline integrity checks for the agent handbook, specs, tasks and local skills."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

REQUIREMENT = re.compile(r"^\*\*([A-Z]+-\d{2})\s+[—–-]", re.MULTILINE)
LINK = re.compile(r"\[[^\]\n]*\]\(([^)\n]+)\)")


def prose(text: str) -> str:
    """Examples inside fenced blocks are not live Markdown links or declarations."""
    return re.sub(r"^(`{3,}|~{3,})[^\n]*\n.*?^\1\s*$", "", text, flags=re.M | re.S)


def anchors(text: str) -> set[str]:
    counts: dict[str, int] = {}
    result: set[str] = set()
    for heading in re.findall(r"^#{1,6}\s+(.+?)(?:\s+#+)?$", prose(text), flags=re.M):
        heading = re.sub(r"\[([^]]+)\]\([^)]*\)", r"\1", heading)
        slug = re.sub(r"[^\w\- ]", "", heading.lower()).replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        result.add(f"{slug}-{count}" if count else slug)
    return result


def local_path(root: Path, value: str, errors: list[str], owner: str) -> Path | None:
    if not isinstance(value, str) or not value or Path(value).is_absolute():
        errors.append(f"{owner}: chemin relatif invalide : {value!r}")
        return None
    target = (root / value).resolve()
    if not target.is_relative_to(root):
        errors.append(f"{owner}: chemin hors dépôt : {value}")
        return None
    if not target.exists():
        errors.append(f"{owner}: chemin absent : {value}")
        return None
    return target


def documents(root: Path) -> list[Path]:
    paths = {root / "AGENTS.md", root / "README.md", root / "docs/plan/README.md"}
    for folder in ("docs/agents", "docs/specs", ".agents"):
        paths.update((root / folder).rglob("*.md"))
    paths.update((root / "android").rglob("AGENTS.md"))
    for relative in ("docs/AGENTS.md", "companion/AGENTS.md",
                     "docs/architecture/0018-agent-delegation-contract.md"):
        if (root / relative).exists():
            paths.add(root / relative)
    return sorted(p for p in paths if p.is_file())


def check_links(root: Path, paths: list[Path], errors: list[str]) -> None:
    for path in paths:
        for match in LINK.finditer(prose(path.read_text(encoding="utf-8"))):
            value = match.group(1).strip()
            if value.startswith("<") and value.endswith(">"):
                value = value[1:-1]
            parsed = urlsplit(value)
            if parsed.scheme or parsed.netloc:
                continue  # No network access; external URLs are not claimed as verified.
            target = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path
            owner = str(path.relative_to(root))
            if not target.is_relative_to(root):
                errors.append(f"{owner}: lien hors dépôt : {value}")
            elif not target.exists():
                errors.append(f"{owner}: lien cassé : {value}")
            elif parsed.fragment and target.is_file() and target.suffix == ".md":
                if unquote(parsed.fragment) not in anchors(target.read_text(encoding="utf-8")):
                    errors.append(f"{owner}: ancre absente : {value}")


def check_skills(root: Path, errors: list[str]) -> None:
    for path in sorted((root / ".agents/skills").glob("*/SKILL.md")):
        text = path.read_text(encoding="utf-8")
        parts = text.split("---", 2)
        if len(parts) != 3 or parts[0].strip():
            errors.append(f"{path.relative_to(root)}: frontmatter manquant")
            continue
        fields = dict(re.findall(r"^(name|description):\s*(.+)$", parts[1], flags=re.M))
        name = fields.get("name", "")
        if name != path.parent.name or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", name):
            errors.append(f"{path.relative_to(root)}: nom de skill invalide")
        description = fields.get("description", "")
        if not description or not parts[2].strip():
            errors.append(f"{path.relative_to(root)}: description ou instructions absentes")
        if ": " in description and not description.startswith(('"', "'")):
            errors.append(f"{path.relative_to(root)}: description YAML à citer")


def check_manifest(root: Path, errors: list[str]) -> tuple[dict, dict[str, str]]:
    requirements: dict[str, str] = {}
    for path in sorted((root / "docs/specs").glob("*.md")):
        for requirement in REQUIREMENT.findall(prose(path.read_text(encoding="utf-8"))):
            if requirement in requirements:
                errors.append(f"Exigence dupliquée : {requirement}")
            requirements[requirement] = str(path.relative_to(root))
    manifest_path = root / "docs/agents/tasks/manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        errors.append(f"Manifest illisible : {error}")
        return {}, requirements
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        errors.append("Version/structure de manifest non supportée")
        return {}, requirements
    tasks = manifest.get("tasks")
    if not isinstance(tasks, list) or not tasks:
        errors.append("Le manifest doit contenir des missions")
        return manifest, requirements
    by_id: dict[str, dict] = {}
    covered: set[str] = set()
    used_files: set[str] = set()
    for task in tasks:
        if not isinstance(task, dict) or not re.fullmatch(r"JC-\d{2}", str(task.get("id", ""))):
            errors.append(f"Mission invalide : {task!r}")
            continue
        task_id = task["id"]
        if task_id in by_id:
            errors.append(f"Mission dupliquée : {task_id}")
        by_id[task_id] = task
        for field in ("depends_on", "issues", "specs", "requirements", "sources"):
            if not isinstance(task.get(field), list):
                errors.append(f"{task_id}: liste {field} absente")
                task[field] = []
        if not task.get("title") or not task["requirements"] or not task["sources"]:
            errors.append(f"{task_id}: titre/exigences/sources absents")
        if not task["issues"] or any(not isinstance(n, int) or n <= 0 for n in task["issues"]):
            errors.append(f"{task_id}: issues parentes invalides")
        file_value = task.get("file", "")
        if file_value in used_files:
            errors.append(f"{task_id}: fiche réutilisée : {file_value}")
        used_files.add(file_value)
        path = local_path(root, file_value, errors, task_id)
        if path and path.is_file():
            content = path.read_text(encoding="utf-8")
            if not content.startswith(f"# {task_id} —"):
                errors.append(f"{task_id}: titre de fiche incohérent")
            for requirement in task["requirements"]:
                if requirement not in content:
                    errors.append(f"{task_id}: exigence absente de la fiche : {requirement}")
        skill = task.get("skill", "")
        local_path(root, skill, errors, task_id)
        if not isinstance(skill, str) or not re.fullmatch(r"\.agents/skills/[^/]+/SKILL\.md", skill):
            errors.append(f"{task_id}: chemin skill invalide")
        for value in task["specs"] + task["sources"]:
            local_path(root, value, errors, task_id)
        for requirement in task["requirements"]:
            if requirement not in requirements:
                errors.append(f"{task_id}: exigence inconnue : {requirement}")
            elif requirements[requirement] not in task["specs"]:
                errors.append(f"{task_id}: spec non liée pour {requirement}")
            covered.add(requirement)
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(task_id: str) -> None:
        if task_id in visiting:
            errors.append(f"Cycle de dépendances impliquant {task_id}")
            return
        if task_id in visited:
            return
        visiting.add(task_id)
        for dependency in by_id[task_id]["depends_on"]:
            if not isinstance(dependency, str) or dependency not in by_id:
                errors.append(f"{task_id}: dépendance inconnue : {dependency}")
            else:
                visit(dependency)
        visiting.remove(task_id)
        visited.add(task_id)

    for task_id in by_id:
        visit(task_id)
    for requirement in sorted(set(requirements) - covered):
        errors.append(f"Exigence sans mission : {requirement}")
    actual_files = {str(p.relative_to(root)) for p in (root / "docs/agents/tasks").glob("jc-*.md")}
    for orphan in sorted(actual_files - used_files):
        errors.append(f"Fiche hors manifest : {orphan}")
    if not re.fullmatch(r"[0-9a-f]{40}", str(manifest.get("baseline_commit", ""))):
        errors.append("Commit de base absent/invalide")
    return manifest, requirements


def validate(root: Path) -> tuple[list[str], dict, int]:
    root = root.resolve()
    errors: list[str] = []
    paths = documents(root)
    check_links(root, paths, errors)
    check_skills(root, errors)
    manifest, requirements = check_manifest(root, errors)
    return errors, manifest, len(requirements)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--task", help="Afficher le paquet de lecture d'une mission, sans l'exécuter")
    args = parser.parse_args()
    errors, manifest, count = validate(args.root)
    if errors:
        print("Documentation invalide :", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    tasks = manifest["tasks"]
    print(f"Documentation valide : {len(tasks)} missions, {count} exigences couvertes.")
    if args.task:
        task = next((t for t in tasks if t["id"] == args.task), None)
        if task is None:
            parser.error(f"mission inconnue : {args.task}")
        print(json.dumps({"read_first": ["AGENTS.md", "docs/agents/current-state.md", task["file"]],
                          **task}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
