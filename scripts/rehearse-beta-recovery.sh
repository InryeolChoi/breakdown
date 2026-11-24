#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 4 ]]; then
  echo 'Usage: bash scripts/rehearse-beta-recovery.sh FRONTEND BACKEND PREVIOUS_FRONTEND PREVIOUS_BACKEND' >&2
  exit 2
fi
if [[ -n "${DOCKER_HOST:-}" ]] || [[ "$(docker context inspect --format '{{.Endpoints.docker.Host}}')" != unix://* ]]; then
  echo 'This rehearsal requires a local Unix Docker socket.' >&2
  exit 2
fi
for image in "$@"; do docker image inspect "$image" >/dev/null; done

cd "$(dirname "$0")/.."
scratch="$(mktemp -d)"
project="breakground-rehearsal-$(basename "$scratch" | tr '[:upper:].' '[:lower:]-')"
export BREAKGROUND_SITE=localhost BREAKGROUND_TLS='tls internal'
export BREAKGROUND_BIND_IP=127.0.0.1
export BREAKGROUND_HTTP_PORT=${REHEARSAL_HTTP_PORT:-19080}
export BREAKGROUND_HTTPS_PORT=${REHEARSAL_HTTPS_PORT:-19443}
export BREAKGROUND_DB_PASSWORD=breakground-rehearsal-fixture
export BREAKGROUND_APP_DB_USER=breakground BREAKGROUND_APP_DB_PASSWORD="$BREAKGROUND_DB_PASSWORD"
export BREAKGROUND_FLYWAY_DB_USER=breakground BREAKGROUND_FLYWAY_DB_PASSWORD="$BREAKGROUND_DB_PASSWORD"
export BREAKGROUND_FRONTEND_IMAGE="$1" BREAKGROUND_BACKEND_IMAGE="$2"

compose=(docker compose --env-file /dev/null -p "$project" -f compose.production.yml)
cleanup() {
  "${compose[@]}" down --volumes >/dev/null
  rm -rf "$scratch"
}
trap cleanup EXIT

pg() { "${compose[@]}" exec -T postgres "$@"; }
wait_http() {
  local base="https://localhost:$BREAKGROUND_HTTPS_PORT"
  for _ in {1..45}; do
    if curl -fsS --max-time 3 --cacert "$scratch/ca.pem" "$base/minimal" >/dev/null 2>&1 &&
       curl -fsS --max-time 3 --cacert "$scratch/ca.pem" "$base/api/rooms" > "$scratch/rooms.json" 2>/dev/null; then
      python3 -c 'import json, sys; assert isinstance(json.load(open(sys.argv[1])), list)' "$scratch/rooms.json"
      return
    fi
    sleep 1
  done
  "${compose[@]}" logs --tail 30 frontend backend gateway >&2
  return 1
}

"${compose[@]}" config --quiet
"${compose[@]}" up --no-build -d --wait
for _ in {1..30}; do
  if "${compose[@]}" cp gateway:/data/caddy/pki/authorities/local/root.crt "$scratch/ca.pem" >/dev/null 2>&1; then break; fi
  sleep 1
done
test -s "$scratch/ca.pem"
wait_http
echo 'Candidate images serve the home and rooms API over verified local TLS.'

pg pg_dump -U breakground -Fc breakground > "$scratch/fixture.dump"
pg createdb -U breakground rehearsal_restore
pg pg_restore -U breakground --exit-on-error -d rehearsal_restore < "$scratch/fixture.dump"
query='SELECT count(*) FROM room; SELECT count(*) FROM flyway_schema_history WHERE success;'
pg psql -U breakground -d breakground -AXqt -c "$query" > "$scratch/source-counts"
pg psql -U breakground -d rehearsal_restore -AXqt -c "$query" > "$scratch/restored-counts"
diff -u "$scratch/source-counts" "$scratch/restored-counts"
printf 'Restored fixture rooms and Flyway entries: '
paste -sd, "$scratch/restored-counts"

cat > "$scratch/no-migrations.yml" <<'YAML'
services:
  backend:
    environment:
      SPRING_FLYWAY_ENABLED: 'false'
YAML
db_before="$("${compose[@]}" ps -q postgres)"
export BREAKGROUND_FRONTEND_IMAGE="$3" BREAKGROUND_BACKEND_IMAGE="$4"
"${compose[@]}" -f "$scratch/no-migrations.yml" up --no-deps --no-build -d frontend backend
wait_http
for service in frontend backend; do
  ref="$BREAKGROUND_FRONTEND_IMAGE"
  if [[ "$service" == backend ]]; then ref="$BREAKGROUND_BACKEND_IMAGE"; fi
  expected="$(docker image inspect --format '{{.Id}}' "$ref")"
  running="$(docker inspect --format '{{.Image}}' "$("${compose[@]}" ps -q "$service")")"
  [[ "$expected" == "$running" ]]
done
[[ "$db_before" == "$("${compose[@]}" ps -q postgres)" ]]
echo 'Both previous images restored. PostgreSQL was left running and Flyway was disabled during rollback.'
