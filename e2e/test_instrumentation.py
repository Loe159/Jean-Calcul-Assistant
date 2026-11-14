import tempfile
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from instrumentation import write_results
from run import check_instrumentation_results


class DirectInstrumentationEvidenceTests(unittest.TestCase):
    expected = {('sample.SmokeTest', 'first'), ('sample.SmokeTest', 'second')}

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.path = self.directory / 'TEST-smoke.xml'

    def output(self, codes=(0, 0), summary='OK (2 tests)', finish='INSTRUMENTATION_CODE: -1'):
        lines = []
        for name, code in zip(('first', 'second'), codes):
            lines += ['INSTRUMENTATION_STATUS: class=sample.SmokeTest',
                      'INSTRUMENTATION_STATUS: test=' + name,
                      'INSTRUMENTATION_STATUS_CODE: 1',
                      'INSTRUMENTATION_STATUS: class=sample.SmokeTest',
                      'INSTRUMENTATION_STATUS: test=' + name,
                      'INSTRUMENTATION_STATUS_CODE: ' + str(code)]
        return CompletedProcess([], 0, '\n'.join(lines + [summary, finish]))

    def test_finished_tests_create_fresh_device_bound_junit(self):
        write_results(self.output(), self.path, 'emulator-5580', self.expected)
        self.assertEqual([self.path], check_instrumentation_results(self.directory, 'emulator-5580'))

    def test_zero_adb_exit_does_not_mask_a_failed_test(self):
        with self.assertRaises(RuntimeError):
            write_results(self.output((0, -2), 'FAILURES!!!'), self.path, 'emulator-5580', self.expected)
        with self.assertRaises(RuntimeError):
            check_instrumentation_results(self.directory, 'emulator-5580')

    def test_missing_or_interrupted_test_cannot_pass(self):
        for result in (self.output((0,), 'OK (1 test)'), self.output(finish='INSTRUMENTATION_CODE: 0')):
            with self.subTest(stdout=result.stdout), self.assertRaises(RuntimeError):
                write_results(result, self.path, 'emulator-5580', self.expected)

    def test_ignored_test_cannot_count_as_a_success(self):
        with self.assertRaises(RuntimeError):
            write_results(self.output((0, -3)), self.path, 'emulator-5580', self.expected)

    def test_an_unrelated_class_cannot_satisfy_the_contract(self):
        result = self.output();result.stdout = result.stdout.replace('sample.SmokeTest', 'other.Test')
        with self.assertRaises(RuntimeError):
            write_results(result, self.path, 'emulator-5580', self.expected)

    def test_duplicate_completion_cannot_replace_missing_test(self):
        result = self.output();result.stdout = result.stdout.replace('test=second', 'test=first')
        with self.assertRaises(RuntimeError):
            write_results(result, self.path, 'emulator-5580', self.expected)
