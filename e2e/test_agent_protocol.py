#!/usr/bin/env python3
"""Local protocol regression checks; synthetic PNGs are NOT reviewed images."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from PIL import Image
from review import make_report, visual_difference

ROOT = Path(__file__).resolve().parent


class AgentProtocolTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.capture = self.root / "captures"
        self.capture.mkdir()
        self.baselines = self.root / "baselines"
        self.baselines.mkdir()
        self.criteria = json.loads((ROOT / "visual-criteria.json").read_text())["criteria"]
        self.needed = sorted({n for item in self.criteria for n in item["captures"]})
        for name in self.needed:
            Image.new("RGB", (360, 780), "#f7f8fa").save(self.capture / name)
        self.out = self.root / "out"
        self.out.mkdir()

    def run_report(self, instrumentation="PASS"):
        return make_report(self.capture, self.out, instrumentation,
                           self.out / "agent-verdicts.json", self.baselines)

    def write_synthetic_review(self):
        self.out.joinpath("agent-verdicts.json").write_text(json.dumps({
            "schema_version": 1, "reviewer": "implementing-agent",
            "verdicts": {
                item["id"]: {
                    "status": "PASS",
                    "reason": "Synthetic acceptance for protocol unit test only",
                    "evidence": item["captures"],
                }
                for item in self.criteria if item["type"] != "stability"
            },
        }))

    def test_screenshots_alone_cannot_pass(self):
        counts = self.run_report()
        self.assertEqual(counts, {"FAIL": 0, "NOT_RUN": 8, "PASS": 1})

    def test_valid_agent_verdicts_enable_gate(self):
        self.write_synthetic_review()
        self.assertEqual(self.run_report(), {"FAIL": 0, "NOT_RUN": 0, "PASS": 9})

    def test_missing_image_fails_even_with_agent_pass(self):
        self.write_synthetic_review()
        (self.capture / "01-empty.png").unlink()
        self.assertGreater(self.run_report()["FAIL"], 0)

    def test_instrumentation_failure_cannot_be_overridden(self):
        self.write_synthetic_review()
        self.assertGreater(self.run_report("FAIL")["FAIL"], 0)

    def test_baseline_detector_vetoes_agent_pass(self):
        self.write_synthetic_review()
        Image.new("RGB", (360, 780), "#000000").save(self.baselines / "05-dark.png")
        self.assertGreater(visual_difference(self.capture / "05-dark.png",
                                              self.baselines / "05-dark.png"), .025)
        self.assertGreater(self.run_report()["FAIL"], 0)


if __name__ == "__main__":
    unittest.main()
