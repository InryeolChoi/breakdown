#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"
mkdir -p build/ci

: "${PGPORT:=5432}"
export PGHOST=127.0.0.1 PGPORT PGUSER=breakground PGPASSWORD=breakground
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$PGPORT/breakground_ci"
export SPRING_DATASOURCE_USERNAME="$PGUSER" SPRING_DATASOURCE_PASSWORD="$PGPASSWORD"
export SERVER_ADDRESS=127.0.0.1 SERVER_PORT=18080
export RATE_LIMIT_MESSAGE_CAPACITY=100000
export RATE_LIMIT_SESSION_ISSUE_CAPACITY=100000
export RATE_LIMIT_REPORT_CAPACITY=100000
export COMPARE_URL=http://127.0.0.1:13000

# PostgreSQL only allows CREATE DATABASE outside a transaction. Requiring a fresh
# name also prevents the fixture from replacing room settings in an existing DB.
psql -X -v ON_ERROR_STOP=1 -d postgres -c 'CREATE DATABASE breakground_ci'
./gradlew --no-daemon bootJar
java -jar build/libs/breakground-0.0.1-SNAPSHOT.jar > build/ci/spring-live.log 2>&1 &
java_pid=$!
node_pid=''
cleanup() {
  if [[ -n "$node_pid" ]]; then kill "$node_pid" 2>/dev/null || true; fi
  kill "$java_pid" 2>/dev/null || true
}
trap cleanup EXIT

wait_http() {
  for _ in {1..90}; do
    if curl --silent --fail --max-time 2 "$1" >/dev/null; then return 0; fi
    kill -0 "$2" 2>/dev/null || break
    sleep 1
  done
  printf 'Service unavailable: %s\n' "$1" >&2
  tail -100 build/ci/*-live.log >&2
  return 1
}

wait_http http://127.0.0.1:18080/rooms "$java_pid"
psql -X -v ON_ERROR_STOP=1 -d breakground_ci <<'SQL'
UPDATE room
SET weekday_open = TRUE,
    weekend_open = TRUE,
    open_at = ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') - interval '10 minutes')::time,
    close_at = ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') + interval '45 minutes')::time;
SQL
(
  cd frontend
  export NODE_ENV=production PORT=13000 BACKEND_URL=http://127.0.0.1:18080
  exec node server.mjs
) > build/ci/frontend-live.log 2>&1 &
node_pid=$!
wait_http "$COMPARE_URL/api/rooms" "$node_pid"
(cd frontend && RUN_LIVE_TESTS=1 RUN_LIVE_DEPTH_TESTS=1 node --test --test-reporter=tap tests/*.test.*) | tee build/ci/node.tap
PLAYWRIGHT_JSON_OUTPUT_FILE="$root_dir/build/ci/playwright.json" npm --prefix frontend run test:e2e -- --reporter=json
python3 scripts/check-ci-results.py build/test-results/test build/ci/node.tap build/ci/playwright.json
