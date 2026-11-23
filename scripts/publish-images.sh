#!/usr/bin/env bash
set -euo pipefail

[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]]
[[ "$(git rev-parse HEAD)" == "$RELEASE_SHA" ]]
registry="ghcr.io/${GITHUB_REPOSITORY,,}"
previous_manifest="$(mktemp)"
trap 'rm -f "$previous_manifest"' EXIT

# Only actual published manifests count. A skipped release workflow may also have a success conclusion.
run_ids="$(gh run list --repo "$GITHUB_REPOSITORY" --workflow release.yml --branch main --status success --limit 20 \
  --json databaseId,headSha --jq ".[] | select(.headSha != \"$RELEASE_SHA\") | .databaseId")"
while read -r run_id; do
  [[ -n "$run_id" ]] || continue
  artifact_id="$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$run_id/artifacts" \
    --jq '[.artifacts[] | select(.name == "release-manifest" and (.expired | not))][0].id // empty')"
  if [[ -n "$artifact_id" ]]; then
    archive="$(mktemp)"
    gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$artifact_id/zip" > "$archive"
    unzip -p "$archive" release.json > "$previous_manifest"
    rm -f "$archive"
    break
  fi
done <<< "$run_ids"
if [[ ! -s "$previous_manifest" ]]; then echo null > "$previous_manifest"; fi

export DOCKER_CONFIG
DOCKER_CONFIG="$(mktemp -d)"
trap 'rm -rf "$DOCKER_CONFIG"; rm -f "$previous_manifest"' EXIT
printf '%s' "$GH_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin

publish() {
  local service="$1" ref="$registry-$1:$RELEASE_SHA" current digest local_config remote_config
  if current="$(docker buildx imagetools inspect "$ref" 2>&1)"; then
    local_config="$(docker image inspect "breakground-$service:$RELEASE_SHA" --format '{{.Id}}')"
    remote_config="$(docker buildx imagetools inspect --raw "$ref" | jq -er .config.digest)"
    if [[ "$local_config" != "$remote_config" ]]; then
      printf 'Existing SHA tag for %s does not match the scanned image\n' "$ref" >&2
      return 1
    fi
    echo "Ref already published, refusing to overwrite $ref" >&2
  elif [[ "$current" == *'not found'* || "$current" == *'MANIFEST_UNKNOWN'* || "$current" == *'manifest unknown'* ]]; then
    docker tag "breakground-$service:$RELEASE_SHA" "$ref"
    docker push "$ref" >&2
    current="$(docker buildx imagetools inspect "$ref")"
  else
    printf 'Registry lookup failed for %s\n%s\n' "$ref" "$current" >&2
    return 1
  fi
  digest="$(awk '/^Digest:/ {print $2; exit}' <<< "$current")"
  [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
  printf '%s@%s\n' "${ref%:*}" "$digest"
}

frontend="$(publish frontend)"
backend="$(publish backend)"
jq -n --arg sha "$RELEASE_SHA" --arg ci_run "$CI_RUN_ID" --arg frontend "$frontend" --arg backend "$backend" \
  --slurpfile previous "$previous_manifest" \
  '{sha:$sha, ci_run:$ci_run, images:{frontend:$frontend, backend:$backend},
    previous_verified_release:($previous[0] | if . == null then null else {sha, images} end)}' > release.json
cat release.json
