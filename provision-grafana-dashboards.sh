#!/usr/bin/env bash
set -euo pipefail

# Provisions every Grafana dashboard JSON file found in monitoring/grafana/
# to Grafana Cloud. Add a new dashboard by dropping its JSON file into
# monitoring/grafana/ - no workflow changes are needed for it to be picked up.
#
# Required env vars:
#   GRAFANA_CLOUD_SA_TOKEN   - Grafana Cloud service account token
#   GRAFANA_CLOUD_STACK_URL  - base URL of the Grafana Cloud stack (e.g. https://<slug>.grafana.net)
# Optional env vars:
#   COMMIT_SHA - included in the provisioning message (defaults to "unknown")

if [ -z "${GRAFANA_CLOUD_SA_TOKEN:-}" ]; then
  echo "Error: GRAFANA_CLOUD_SA_TOKEN secret must be set" >&2
  exit 1
fi

if [ -z "${GRAFANA_CLOUD_STACK_URL:-}" ]; then
  echo "Error: GRAFANA_CLOUD_STACK_URL secret must be set" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dashboard_dir="$script_dir/monitoring/grafana"
commit_sha="${COMMIT_SHA:-unknown}"
folder_uid="james-platform"

ensure_folder() {
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" \
    "$GRAFANA_CLOUD_STACK_URL/api/folders/$folder_uid" \
    -H "Authorization: Bearer $GRAFANA_CLOUD_SA_TOKEN")
  if [ "$status" = "200" ]; then
    return 0
  fi

  echo "Folder '$folder_uid' not found, creating it..."
  curl --fail-with-body -s -X POST \
    "$GRAFANA_CLOUD_STACK_URL/api/folders" \
    -H "Authorization: Bearer $GRAFANA_CLOUD_SA_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg uid "$folder_uid" --arg title "James Platform" '{uid: $uid, title: $title}')"
  echo ""
}

provision_dashboard() {
  local dashboard_file="$1"
  local payload
  payload=$(jq -n \
    --argjson dashboard "$(cat "$dashboard_file")" \
    --arg message "Provisioned by GitHub Actions (commit $commit_sha)" \
    --arg folderUid "$folder_uid" \
    '{dashboard: $dashboard, overwrite: true, message: $message, folderUid: $folderUid}')
  local delays=(30 60 120 300)
  local attempt=0
  while true; do
    echo "Provisioning $dashboard_file (attempt $((attempt+1)))..."
    if curl --fail-with-body -s -X POST \
      "$GRAFANA_CLOUD_STACK_URL/api/dashboards/db" \
      -H "Authorization: Bearer $GRAFANA_CLOUD_SA_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$payload"; then
      echo ""
      echo "Successfully provisioned $dashboard_file"
      return 0
    fi
    if [ "$attempt" -ge "${#delays[@]}" ]; then
      echo "Error: Failed to provision $dashboard_file after all retries" >&2
      return 1
    fi
    local wait_time="${delays[$attempt]}"
    echo "Retrying in ${wait_time}s..."
    sleep "$wait_time"
    attempt=$((attempt+1))
  done
}

shopt -s nullglob
dashboard_files=("$dashboard_dir"/*.json)
if [ "${#dashboard_files[@]}" -eq 0 ]; then
  echo "Error: No dashboard files found in $dashboard_dir" >&2
  exit 1
fi

ensure_folder

exit_code=0
for dashboard_file in "${dashboard_files[@]}"; do
  provision_dashboard "$dashboard_file" || exit_code=1
done
exit "$exit_code"
