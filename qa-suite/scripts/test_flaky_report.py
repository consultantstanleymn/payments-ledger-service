#!/usr/bin/env python3
"""Tests for flaky_report.py. They use the fixture XML files."""

from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import flaky_report  # noqa: E402

FIXTURES = SCRIPT_DIR / "fixtures"
REPORT_SCRIPT = SCRIPT_DIR / "flaky_report.py"

FAILED_XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.stanley.ledger.qa.api.NegativeApiTest" tests="1" errors="0" skipped="0" failures="1" flakes="0" time="0.2">
  <testcase name="rejectsMissingKey" classname="com.stanley.ledger.qa.api.NegativeApiTest" time="0.2">
    <failure message="expected HTTP 401 but got 200" type="java.lang.AssertionError">
      <stackTrace>java.lang.AssertionError: expected HTTP 401 but got 200
	at com.stanley.ledger.qa.api.NegativeApiTest.rejectsMissingKey(NegativeApiTest.java:42)</stackTrace>
    </failure>
  </testcase>
</testsuite>
"""


class FlakyReportTest(unittest.TestCase):
    def run_report(self, reports: Path, output: Path) -> subprocess.CompletedProcess:
        return subprocess.run(
            [
                sys.executable,
                str(REPORT_SCRIPT),
                "--reports",
                str(reports),
                "--output",
                str(output),
            ],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_clean_run_exits_zero_and_says_no_flaky_tests(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            reports = base / "reports"
            reports.mkdir()
            shutil.copy(FIXTURES / "surefire-clean.xml", reports / "TEST-clean.xml")
            output = base / "flaky-report.md"

            result = self.run_report(reports, output)

            self.assertEqual(result.returncode, 0, result.stderr)
            text = output.read_text(encoding="utf-8")
            self.assertIn("No flaky tests.", text)
            self.assertIn("No test failed every time.", text)
            self.assertIn("Flaky tests: 0", text)

    def test_flaky_run_lists_class_test_and_first_error_line(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            reports = base / "reports"
            reports.mkdir()
            shutil.copy(FIXTURES / "surefire-flaky.xml", reports)
            output = base / "flaky-report.md"

            result = self.run_report(reports, output)

            self.assertEqual(result.returncode, 0, result.stderr)
            text = output.read_text(encoding="utf-8")
            self.assertIn("Flaky tests: 1", text)
            self.assertIn("com.stanley.ledger.qa.api.PaymentsApiTest", text)
            self.assertIn("authorizeCaptureRefundLifecycle", text)
            self.assertIn("expected HTTP 200 but got 503: service starting", text)
            self.assertNotIn("No flaky tests.", text)

    def test_failed_every_time_exits_one_and_lists_the_test(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            reports = base / "reports"
            reports.mkdir()
            (reports / "TEST-negative.xml").write_text(FAILED_XML, encoding="utf-8")
            output = base / "flaky-report.md"

            result = self.run_report(reports, output)

            self.assertEqual(result.returncode, 1, result.stderr)
            text = output.read_text(encoding="utf-8")
            self.assertIn("Tests that failed every time: 1", text)
            self.assertIn("rejectsMissingKey", text)
            self.assertIn("expected HTTP 401 but got 200", text)

    def test_missing_reports_directory_writes_an_empty_report(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            output = base / "flaky-report.md"

            result = self.run_report(base / "absent", output)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(output.is_file())
            self.assertIn("Flaky tests: 0", output.read_text(encoding="utf-8"))

    def test_parser_keeps_class_and_test_names(self) -> None:
        flaky, failed = flaky_report.read_reports(FIXTURES)

        self.assertEqual(len(flaky), 1)
        self.assertEqual(len(failed), 0)
        class_name, test_name, message = flaky[0]
        self.assertEqual(class_name, "com.stanley.ledger.qa.api.PaymentsApiTest")
        self.assertEqual(test_name, "authorizeCaptureRefundLifecycle")
        self.assertEqual(message, "expected HTTP 200 but got 503: service starting")


if __name__ == "__main__":
    unittest.main()
