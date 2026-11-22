#!/usr/bin/env python3
"""Check Maven's resolved runtime dependency list against OSV; fail on findings or incomplete checks."""
import argparse
import json
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dependency_list', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    source = re.sub(r'\x1b\[[0-9;]*m', '', args.dependency_list.read_text())
    dependencies = sorted(set(re.findall(
        r'^\s+([\w.-]+):([\w.-]+):jar:(?:[\w.-]+:)?([^:\s]+):(compile|runtime)\b', source, re.M)))
    if not dependencies:
        raise ValueError('No runtime dependencies found; generate dependency:list with includeScope=runtime first')
    queries = [{'package': {'ecosystem': 'Maven', 'name': f'{group}:{artifact}'}, 'version': version}
               for group, artifact, version, _ in dependencies]
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
        data=json.dumps({'queries': queries}).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=45) as response:
        result = json.load(response)
    results = result.get('results')
    if not isinstance(results, list) or len(results) != len(queries):
        raise ValueError('OSV returned an incomplete result; audit did not pass')
    report = {'queries': queries, 'results': results}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    affected = 0
    for query, entry in zip(queries, results):
        if not isinstance(entry, dict) or entry.get('error'):
            raise ValueError('OSV returned an invalid result; audit did not pass')
        vulnerabilities = entry.get('vulns', [])
        if vulnerabilities:
            affected += 1
            print(f"{query['package']['name']}:{query['version']}: " + ', '.join(v['id'] for v in vulnerabilities))
    print(f'Checked {len(queries)} resolved runtime dependencies; {affected} affected packages.')
    return 1 if affected else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, urllib.error.URLError) as error:
        print(f'Runtime dependency audit failed: {error}', file=sys.stderr)
        sys.exit(2)
