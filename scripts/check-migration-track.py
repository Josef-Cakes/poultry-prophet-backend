#!/usr/bin/env python3
"""Check the reviewed Flyway track without opening a database connection."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
TRACK = ROOT / "src/main/resources/db/reviewed-migration"
NAME = re.compile(r"V([0-9]+)__[A-Za-z0-9_]+\.sql")


def check_track(track: Path) -> list[str]:
    if not track.is_dir():
        return [f"Reviewed migration directory missing: {track}"]
    errors = []
    versions = {}
    for path in sorted(track.rglob("*")):
        if not path.is_file() or path.suffix.lower() != ".sql":
            continue
        match = NAME.fullmatch(path.name)
        if path.parent != track or not match:
            errors.append(f"Invalid reviewed migration path or name: {path}")
            continue
        version = int(match.group(1))
        if version < 26 or match.group(1) != str(version):
            errors.append(f"Version must be an unpadded integer >= 26: {path.name}")
        if version in versions:
            errors.append(f"Duplicate version {version}: {versions[version]} and {path.name}")
        versions[version] = path.name
    return errors


def main() -> int:
    errors = check_track(TRACK)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Reviewed migration track naming is valid; no database was contacted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
