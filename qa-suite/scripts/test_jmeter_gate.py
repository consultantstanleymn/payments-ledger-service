import importlib.util
import json
import unittest
from pathlib import Path

_SPEC = importlib.util.spec_from_file_location(
    "jmeter_gate", Path(__file__).resolve().parent / "jmeter_gate.py"
)
jmeter_gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(jmeter_gate)


FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"
THRESHOLDS_PATH = Path(__file__).resolve().parents[1] / "perf" / "thresholds.json"


def load_json(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


class JmeterGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.thresholds = load_json(THRESHOLDS_PATH)
        cls.pass_stats = load_json(FIXTURES_DIR / "statistics-pass.json")
        cls.fail_stats = load_json(FIXTURES_DIR / "statistics-fail.json")

    def test_pass_fixture(self):
        ok, rows = jmeter_gate.evaluate(self.pass_stats, self.thresholds)
        self.assertTrue(ok)
        labels = {row[0] for row in rows}
        self.assertIn("Total", labels)
        self.assertIn("authorize", labels)
        self.assertIn("capture", labels)
        self.assertIn("get payment", labels)

    def test_fail_fixture(self):
        ok, _ = jmeter_gate.evaluate(self.fail_stats, self.thresholds)
        self.assertFalse(ok)

    def test_missing_sampler_fails(self):
        missing = json.loads(json.dumps(self.pass_stats))
        del missing["capture"]
        ok, _ = jmeter_gate.evaluate(missing, self.thresholds)
        self.assertFalse(ok)

    def test_table_contains_total(self):
        ok, rows = jmeter_gate.evaluate(self.pass_stats, self.thresholds)
        table = jmeter_gate.format_table(rows)
        self.assertIn("Total", table)


if __name__ == "__main__":
    unittest.main()
