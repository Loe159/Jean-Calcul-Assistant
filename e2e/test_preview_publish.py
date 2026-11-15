import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("publish_preview", Path(__file__).parents[1] / "scripts/publish-preview.py")
publish = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publish)


class PreviewPublicationTest(unittest.TestCase):
    def test_older_rerun_never_overwrites_newer_manifest(self):
        self.run_publication(existing_code=2030000300, expect_upload=False)

    def test_versioned_apk_is_uploaded_before_manifest(self):
        self.run_publication(existing_code=2030000100, expect_upload=True)

    def run_publication(self, existing_code, expect_upload):
        calls = []
        env = {"UPDATE_CHANNEL": "preview-pr-62", "PREVIEW_VERSION_CODE": "2030000200",
               "GITHUB_REPOSITORY": "Loe159/Jean-Calcul-Assistant", "GITHUB_SHA": "a" * 40}
        def gh(*args, **kwargs):
            calls.append(args)
            if args[:2] == ("release", "download"):
                return subprocess.CompletedProcess(args, 0, json.dumps({"versionCode": existing_code}), "")
            if args[:2] == ("release", "upload") and args[3].endswith("update.json"):
                manifest = json.loads(Path(args[3]).read_text())
                self.assertEqual("fr.loevan.jeancalcul.v2", manifest["applicationId"])
                self.assertEqual(2030000200, manifest["versionCode"])
                self.assertEqual(64, len(manifest["sha256"]))
                self.assertEqual(13, manifest["size"])
            return subprocess.CompletedProcess(args, 0, "", "")
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, env), patch.object(publish, "gh", gh):
            apk = Path(directory) / "test.apk"
            apk.write_bytes(b"synthetic APK")
            with patch.object(Path, "glob", return_value=iter([apk])):
                publish.main()
        uploads = [call for call in calls if call[:2] == ("release", "upload")]
        self.assertEqual(2 if expect_upload else 0, len(uploads))
        if expect_upload:
            self.assertTrue(uploads[0][3].endswith("JeanCalcul-2030000200.apk"))
            self.assertTrue(uploads[1][3].endswith("update.json"))
