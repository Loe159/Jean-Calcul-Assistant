#!/usr/bin/env python3
"""Proof that a deliberate synthetic visual regression is detected."""
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw
from review import visual_difference

with tempfile.TemporaryDirectory() as temp:
    baseline = Path(temp) / "baseline.png"
    defect = Path(temp) / "defect.png"
    reference = Image.new("RGB", (360, 780), "#f7f8fa")
    draw = ImageDraw.Draw(reference)
    draw.rounded_rectangle((20, 600, 340, 720), radius=28, fill="#496be5")
    reference.save(baseline)
    changed = reference.copy()
    draw = ImageDraw.Draw(changed)
    # Deliberate defect: opaque obstruction over the lower composer.
    draw.rectangle((0, 585, 360, 775), fill="#000000")
    changed.save(defect)
    score = visual_difference(defect, baseline)
    assert score > 0.025, f"Defect undetected: {score}"
    assert visual_difference(baseline, baseline) == 0
    print(f"PASS: intentional visual defect detected (changed={score:.2%})")
