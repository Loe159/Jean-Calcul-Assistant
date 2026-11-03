"""Exercise handbook integrity failures without network or Android dependencies."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/check_agent_docs.py"
SPEC = importlib.util.spec_from_file_location("check_agent_docs", SCRIPT)
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class DocumentationIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write("AGENTS.md", "# Instructions\n\n[Spec](docs/specs/test.md#section)\n")
        self.write("docs/specs/test.md", "# Spec\n\n## Section\n\n**TEST-01 — Comportement.** Testable.\n")
        self.write("docs/agents/tasks/jc-01-test.md", "# JC-01 — Test\n\nTEST-01\n")
        self.write(".agents/skills/jc-test/SKILL.md",
                   '---\nname: jc-test\ndescription: "Traiter le cas de test."\n---\n\n# Instructions\nLire la spec.\n')
        self.write("android/source.kt", "// Fixture\n")
        self.manifest = {
            "schema_version": 1,
            "baseline_commit": "a" * 40,
            "tasks": [{
                "id": "JC-01", "title": "Test", "file": "docs/agents/tasks/jc-01-test.md",
                "depends_on": [], "issues": [40], "skill": ".agents/skills/jc-test/SKILL.md",
                "specs": ["docs/specs/test.md"], "requirements": ["TEST-01"],
                "sources": ["android/source.kt"],
            }],
        }
        self.save_manifest()

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    def save_manifest(self):
        self.write("docs/agents/tasks/manifest.json", json.dumps(self.manifest))

    def errors(self):
        return CHECK.validate(self.root)[0]

    def test_complete_fixture_passes(self):
        errors, manifest, requirements = CHECK.validate(self.root)
        self.assertEqual(errors, [])
        self.assertEqual(requirements, 1)
        self.assertEqual(len(manifest["tasks"]), 1)

    def test_missing_source_is_rejected(self):
        (self.root / "android/source.kt").unlink()
        self.assertTrue(any("chemin absent" in e for e in self.errors()))

    def test_broken_link_and_anchor_are_reported(self):
        self.write("AGENTS.md", "[A](missing.md) [B](docs/specs/test.md#absente)\n")
        errors = self.errors()
        self.assertTrue(any("lien cassé" in e for e in errors))
        self.assertTrue(any("ancre absente" in e for e in errors))

    def test_links_in_fenced_examples_are_ignored(self):
        self.write("AGENTS.md", "# Exemple\n\n```text\n[À remplacer](non-existant.md)\n```\n")
        self.assertEqual(self.errors(), [])

    def test_external_links_are_not_network_verified(self):
        self.write("AGENTS.md", "[Documentation](https://example.invalid/not-fetched)\n")
        self.assertEqual(self.errors(), [])

    def test_dependency_cycle_is_rejected(self):
        self.manifest["tasks"][0]["depends_on"] = ["JC-01"]
        self.save_manifest()
        self.assertTrue(any("Cycle" in e for e in self.errors()))

    def test_unknown_dependency_is_rejected(self):
        self.manifest["tasks"][0]["depends_on"] = ["JC-99"]
        self.save_manifest()
        self.assertTrue(any("dépendance inconnue" in e for e in self.errors()))

    def test_unassigned_requirement_is_rejected(self):
        self.write("docs/specs/other.md", "# Autre\n\n**TEST-02 — Autre exigence.**\n")
        self.assertTrue(any("sans mission : TEST-02" in e for e in self.errors()))

    def test_unknown_requirement_is_rejected(self):
        self.manifest["tasks"][0]["requirements"].append("TEST-99")
        self.save_manifest()
        self.assertTrue(any("exigence inconnue : TEST-99" in e for e in self.errors()))

    def test_requirement_spec_must_be_in_reading_packet(self):
        self.manifest["tasks"][0]["specs"] = []
        self.save_manifest()
        self.assertTrue(any("spec non liée" in e for e in self.errors()))

    def test_paths_cannot_escape_repository(self):
        self.manifest["tasks"][0]["sources"] = ["../outside.kt"]
        self.save_manifest()
        self.assertTrue(any("hors dépôt" in e for e in self.errors()))

    def test_skill_name_must_match_folder(self):
        self.write(".agents/skills/jc-test/SKILL.md",
                   "---\nname: another-name\ndescription: Test\n---\n\nInstructions\n")
        self.assertTrue(any("nom de skill invalide" in e for e in self.errors()))

    def test_orphan_task_is_rejected(self):
        self.write("docs/agents/tasks/jc-02-orphan.md", "# JC-02 — Sans manifest\n")
        self.assertTrue(any("Fiche hors manifest" in e for e in self.errors()))

    def test_invalid_manifest_is_reported(self):
        self.write("docs/agents/tasks/manifest.json", "not-json")
        self.assertTrue(any("Manifest illisible" in e for e in self.errors()))


if __name__ == "__main__":
    unittest.main()
