"""Reject missing, failed, or skipped suites in required CI reports."""
import glob
import json
import re
import sys
import xml.etree.ElementTree as ET

spring_dir, tap_path, browser_path = sys.argv[1:]
results = [ET.parse(path).getroot() for path in glob.glob(f"{spring_dir}/TEST-*.xml")]
required = {"ChatWebSocketRateLimitTest", "ChatReportIntegrationTest", "MessageRepositoryTest", "RateLimiterTest"}
executed = {suite.attrib["name"].rsplit(".", 1)[-1] for suite in results if int(suite.attrib["tests"]) > 0}
if not required <= executed or any(int(s.attrib[key]) for s in results for key in ("failures", "errors", "skipped")):
    sys.exit(f"Missing or unsuccessful Spring tests. Missing suites: {sorted(required - executed)}")

with open(tap_path) as report:
    tap = report.read()
summary = {name: int(count) for name, count in re.findall(r"^# (tests|pass|fail|skipped|cancelled|todo) (\d+)\s*$", tap, re.M)}
if (
    summary.get("tests", 0) < 6
    or summary.get("pass") != summary.get("tests")
    or any(summary.get(key, -1) != 0 for key in ("fail", "skipped", "cancelled", "todo"))
    or not all(re.search(rf"^ok [0-9]+ - {name}", tap, re.M) for name in ("live:", "live depth:"))
):
    sys.exit(f"Missing or unsuccessful Node tests: {summary}")

with open(browser_path) as report:
    stats = json.load(report)["stats"]
if stats["expected"] < 4 or any(stats[key] for key in ("unexpected", "flaky", "skipped")):
    sys.exit(f"Missing or unsuccessful browser tests: {stats}")
print(f"Required tests executed: Spring {sum(int(s.attrib['tests']) for s in results)}, Node {summary['pass']}, Chromium {stats['expected']}")
