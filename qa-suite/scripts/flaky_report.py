#!/usr/bin/env python3
"""Write a flaky test report from Surefire XML files.

A test that fails, then passes on a rerun, is flaky. Surefire writes such a
test case with a flakyFailure or flakyError child. A test that fails on every
attempt keeps a failure or error child. The report lists both groups and the
exit code is 1 only when a test failed every time.
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
QA_DIR = SCRIPT_DIR.parent
DEFAULT_REPORTS = QA_DIR / "target" / "surefire-reports"
DEFAULT_OUTPUT = QA_DIR / "target" / "flaky-report.md"


def first_error_line(element: ElementTree.Element) -> str:
    """Return the first non-empty line of the error message."""
    message = (element.get("message") or "").strip()
    if not message:
        message = (element.findtext("stackTrace") or "").strip()
    for line in message.splitlines():
        line = line.strip()
        if line:
            return line
    return "no error message"


def read_reports(reports_dir: Path) -> tuple[list[tuple[str, str, str]], list[tuple[str, str, str]]]:
    """Read every XML file and return flaky and failed entries."""
    flaky: list[tuple[str, str, str]] = []
    failed: list[tuple[str, str, str]] = []
    if not reports_dir.is_dir():
        return flaky, failed

    for path in sorted(reports_dir.glob("*.xml")):
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError as error:
            failed.append(("unknown", path.name, f"cannot parse report: {error}"))
            continue

        for case in root.iter("testcase"):
            class_name = case.get("classname") or root.get("name") or "unknown"
            test_name = case.get("name") or "unknown"
            for element in case.findall("flakyFailure") + case.findall("flakyError"):
                flaky.append((class_name, test_name, first_error_line(element)))
            for element in case.findall("failure") + case.findall("error"):
                failed.append((class_name, test_name, first_error_line(element)))

    return flaky, failed


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ").strip()


def render(
    flaky: list[tuple[str, str, str]], failed: list[tuple[str, str, str]]
) -> str:
    lines = ["# Flaky test report", ""]
    lines.append(
        f"Flaky tests: {len(flaky)}. Tests that failed every time: {len(failed)}."
    )
    lines.append("")
    lines.append("## Flaky tests")
    lines.append("")
    if flaky:
        lines.append("| Class | Test | First error line |")
        lines.append("| --- | --- | --- |")
        for class_name, test_name, message in flaky:
            lines.append(
                f"| {escape_cell(class_name)} | {escape_cell(test_name)} "
                f"| {escape_cell(message)} |"
            )
    else:
        lines.append("No flaky tests.")
    lines.append("")
    lines.append("## Tests that failed every time")
    lines.append("")
    if failed:
        lines.append("| Class | Test | First error line |")
        lines.append("| --- | --- | --- |")
        for class_name, test_name, message in failed:
            lines.append(
                f"| {escape_cell(class_name)} | {escape_cell(test_name)} "
                f"| {escape_cell(message)} |"
            )
    else:
        lines.append("No test failed every time.")
    lines.append("")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Write a flaky test report.")
    parser.add_argument(
        "--reports",
        type=Path,
        default=DEFAULT_REPORTS,
        help="directory with Surefire TEST-*.xml files",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="path of the Markdown report",
    )
    args = parser.parse_args(argv)

    flaky, failed = read_reports(args.reports)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(flaky, failed) + "\n", encoding="utf-8")

    print(f"flaky report: {args.output}")
    print(f"flaky tests: {len(flaky)}, failed every time: {len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
