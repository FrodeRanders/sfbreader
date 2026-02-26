#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEDULE_JSON="${1:-${ROOT_DIR}/data/periodisering-schedule.json}"
REFERENCE_DATE="${2:-$(date +%F)}"
WINDOW_DAYS="${3:-0}"

if [[ ! -f "${SCHEDULE_JSON}" ]]; then
  echo "Periodisering schedule file not found: ${SCHEDULE_JSON}" >&2
  exit 2
fi

if [[ ! "${REFERENCE_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "Invalid reference date: ${REFERENCE_DATE} (expected YYYY-MM-DD)" >&2
  exit 2
fi
if [[ ! "${WINDOW_DAYS}" =~ ^[0-9]+$ ]]; then
  echo "Invalid window days: ${WINDOW_DAYS} (expected non-negative integer)" >&2
  exit 2
fi

NEXT_DATE="$(
  python - "${SCHEDULE_JSON}" <<'PY'
import json,sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
print(data.get("nextTransitionDate") or "")
PY
)"

if [[ -z "${NEXT_DATE}" ]]; then
  echo "No upcoming periodisering transition date in schedule."
  exit 0
fi

if [[ ! "${NEXT_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "Invalid nextTransitionDate in schedule: ${NEXT_DATE}" >&2
  exit 2
fi

REFERENCE_PLUS_WINDOW="$(date -j -v+"${WINDOW_DAYS}"d -f "%Y-%m-%d" "${REFERENCE_DATE}" "+%Y-%m-%d")"
if [[ "${NEXT_DATE}" > "${REFERENCE_PLUS_WINDOW}" ]]; then
  echo "Next transition date is ${NEXT_DATE} (outside window ending ${REFERENCE_PLUS_WINDOW})."
  exit 0
fi

echo "Transition date due: next=${NEXT_DATE}, reference=${REFERENCE_DATE}, windowDays=${WINDOW_DAYS}" >&2
echo "Re-run and refresh published structure/baselines." >&2
exit 14
