#!/usr/bin/env python3
"""Offline regression tests for reviewed migration naming."""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location(
    "migration_track", Path(__file__).with_name("check-migration-track.py")
)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class MigrationTrackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.track = Path(self.temp.name)

    def test_empty_and_unique_forward_versions(self):
        self.assertEqual(checker.check_track(self.track), [])
        (self.track / "V26__first.sql").touch()
        (self.track / "V27__second.sql").touch()
        self.assertEqual(checker.check_track(self.track), [])

    def test_rejects_duplicate_legacy_and_padded_versions(self):
        for name in ("V15__legacy.sql", "V26__a.sql", "V26__b.sql", "V026__c.sql"):
            (self.track / name).touch()
        errors = checker.check_track(self.track)
        self.assertTrue(any("Duplicate version 26" in error for error in errors))
        self.assertEqual(sum("unpadded integer" in error for error in errors), 2)

    def test_rejects_unexpected_sql_and_subdirectories(self):
        (self.track / "repeatable.sql").touch()
        (self.track / "nested").mkdir()
        (self.track / "nested" / "V26__nested.sql").touch()
        self.assertEqual(len(checker.check_track(self.track)), 2)

    def test_missing_directory_fails(self):
        self.assertTrue(checker.check_track(self.track / "missing"))


if __name__ == "__main__":
    unittest.main()
