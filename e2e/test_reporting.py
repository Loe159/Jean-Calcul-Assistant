import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from report import visual_result, render
from run import redact, SCENARIOS, check_instrumentation_results, complete_png


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.image = self.directory / 'empty.png'
        self.image.write_bytes(b'synthetic-image')
        digest = hashlib.sha256(self.image.read_bytes()).hexdigest()
        self.capture = {'status': 'PASS', 'path': 'empty.png', 'sha256': digest}
        self.review = {'status': 'FAIL', 'sha256': digest, 'opened_with': 'native image viewer',
                       'observation': 'Composer hidden by red overlay'}

    def test_png_alone_does_not_pass_visual_review(self):
        self.assertEqual(visual_result(self.directory, self.capture, {})[0], 'NOT_RUN')

    def test_observation_and_native_opening_are_required(self):
        self.review['opened_with'] = ''
        self.assertEqual(visual_result(self.directory, self.capture, self.review)[0], 'NOT_RUN')

    def test_changed_image_invalidates_review(self):
        self.image.write_bytes(b'different-image')
        self.assertEqual(visual_result(self.directory, self.capture, self.review)[0], 'NOT_RUN')

    def test_motivated_review_retains_failure(self):
        self.assertEqual(visual_result(self.directory, self.capture, self.review)[0], 'FAIL')

    def test_deliberate_defect_must_fail_to_prove_detection(self):
        report = {'attempt': 1, 'commit': 'synthetic', 'working_tree_dirty': False,
                  'api': 35, 'serial': 'emulator-5580', 'technical': {'status': 'PASS'},
                  'captures': {name: self.capture for name in SCENARIOS}}
        (self.directory / 'report.json').write_text(json.dumps(report))
        reviews = {name: {**self.review, 'status': 'PASS'} for name in SCENARIOS}
        (self.directory / 'visual-review.json').write_text(json.dumps(reviews))
        self.assertEqual(render(self.directory), ('PASS', 'FAIL'))
        reviews['regression_fixture']['status'] = 'FAIL'
        (self.directory / 'visual-review.json').write_text(json.dumps(reviews))
        self.assertEqual(render(self.directory), ('PASS', 'PASS'))
        self.assertIn('NOT_RUN', (self.directory / 'report.md').read_text())

    def test_scoped_review_passes_only_named_state(self):
        report = {'attempt': 1, 'commit': 'synthetic', 'working_tree_dirty': False,
                  'api': 35, 'serial': 'emulator-5580', 'technical': {'status': 'PASS'},
                  'captures': {name: self.capture for name in SCENARIOS}}
        (self.directory / 'report.json').write_text(json.dumps(report))
        state = next(name for name in SCENARIOS if name != 'regression_fixture')
        reviews = {'_scope': {'states': [state], 'reason': 'Only this screen changed'},
                   state: {**self.review, 'status': 'PASS'}}
        (self.directory / 'visual-review.json').write_text(json.dumps(reviews))
        self.assertEqual(render(self.directory), ('PASS', 'NOT_RUN'))
        result = json.loads((self.directory / 'report.json').read_text())
        self.assertEqual(result['visual_review']['scope'], [state])
        self.assertEqual(result['captures']['regression_fixture']['visual_status'], 'NOT_RUN')

    def test_gradle_success_cannot_mask_xml_failure(self):
        (self.directory / 'TEST-device.xml').write_text(
            '<testsuite><properties><property name="device" value="emulator-5580"/>'
            '</properties><testcase name="example"><failure>broken</failure></testcase></testsuite>')
        with self.assertRaises(RuntimeError):
            check_instrumentation_results(self.directory, 'emulator-5580')

    def test_missing_device_results_cannot_pass(self):
        with self.assertRaises(RuntimeError):
            check_instrumentation_results(self.directory, 'emulator-5580')

    def test_empty_or_truncated_screenshot_is_not_a_png(self):
        import base64
        png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9sAAAAASUVORK5CYII=')
        self.assertTrue(complete_png(png))
        for incomplete in (b'', png[:8], png[:-1]):
            with self.subTest(size=len(incomplete)):
                self.assertFalse(complete_png(incomplete))

    def test_secret_redaction(self):
        value = redact('Bearer abc123 token=private user@example.org')
        self.assertNotIn('abc123', value)
        self.assertNotIn('private', value)
        self.assertNotIn('user@example.org', value)


if __name__ == '__main__':
    unittest.main()
