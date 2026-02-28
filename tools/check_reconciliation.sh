#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="${ROOT_DIR}/target/sfsreader-1.0-SNAPSHOT.jar"
INPUT_PATH="${1:-${ROOT_DIR}/data/sfs-2010-110.txt.xml}"
BASELINE_PATH="${2:-${ROOT_DIR}/data/reconciliation-baseline.txt}"
OUT_DIR="${3:-/tmp/sfsreader-hybrid-check}"
PERIODISERING_BASELINE_PATH="${4:-${ROOT_DIR}/data/periodisering-mismatch-baseline.txt}"
PERIODISERING_UNRESOLVED_BASELINE_PATH="${5:-${ROOT_DIR}/data/periodisering-unresolved-baseline.txt}"
PERIODISERING_INVALID_BASELINE_PATH="${6:-${ROOT_DIR}/data/periodisering-invalid-baseline.txt}"
SCHEDULE_WINDOW_DAYS="${7:-0}"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Jar not found: ${JAR_PATH}" >&2
  echo "Build first with: mvn -q -DskipTests package" >&2
  exit 2
fi

if [[ ! -f "${INPUT_PATH}" ]]; then
  echo "Input file not found: ${INPUT_PATH}" >&2
  exit 2
fi

if [[ ! -f "${BASELINE_PATH}" ]]; then
  echo "Baseline file not found: ${BASELINE_PATH}" >&2
  exit 2
fi

if [[ ! -f "${PERIODISERING_BASELINE_PATH}" ]]; then
  echo "Periodisering baseline file not found: ${PERIODISERING_BASELINE_PATH}" >&2
  exit 2
fi
if [[ ! -f "${PERIODISERING_UNRESOLVED_BASELINE_PATH}" ]]; then
  echo "Periodisering unresolved baseline file not found: ${PERIODISERING_UNRESOLVED_BASELINE_PATH}" >&2
  exit 2
fi
if [[ ! -f "${PERIODISERING_INVALID_BASELINE_PATH}" ]]; then
  echo "Periodisering invalid baseline file not found: ${PERIODISERING_INVALID_BASELINE_PATH}" >&2
  exit 2
fi

mkdir -p "${OUT_DIR}"

java -jar "${JAR_PATH}" \
  -s hybrid \
  -b "${BASELINE_PATH}" \
  -f \
  -t "${ROOT_DIR}/template/sfs.stg" \
  -d "${OUT_DIR}" \
  -- "${INPUT_PATH}"

RECONCILIATION_JSON="${INPUT_PATH%/*}/reconciliation.json"
if [[ ! -f "${RECONCILIATION_JSON}" ]]; then
  echo "Expected reconciliation JSON not found: ${RECONCILIATION_JSON}" >&2
  exit 2
fi

ACTUAL_PERIODISERING_MISMATCHES="$(
  python - "${RECONCILIATION_JSON}" <<'PY'
import json
import sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
print(int(data.get('byType', {}).get('paragraph_periodisering_mismatch', 0)))
PY
)"
ACTUAL_PERIODISERING_UNRESOLVED="$(
  python - "${RECONCILIATION_JSON}" <<'PY'
import json
import sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
print(int(data.get('byType', {}).get('paragraph_periodisering_unresolved', 0)))
PY
)"
ACTUAL_PERIODISERING_INVALID="$(
  python - "${RECONCILIATION_JSON}" <<'PY'
import json
import sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
print(int(data.get('byType', {}).get('paragraph_periodisering_invalid', 0)))
PY
)"

BASELINE_PERIODISERING_MISMATCHES="$(tr -d '[:space:]' < "${PERIODISERING_BASELINE_PATH}")"
BASELINE_PERIODISERING_UNRESOLVED="$(tr -d '[:space:]' < "${PERIODISERING_UNRESOLVED_BASELINE_PATH}")"
BASELINE_PERIODISERING_INVALID="$(tr -d '[:space:]' < "${PERIODISERING_INVALID_BASELINE_PATH}")"
if [[ ! "${BASELINE_PERIODISERING_MISMATCHES}" =~ ^[0-9]+$ ]]; then
  echo "Invalid baseline value in ${PERIODISERING_BASELINE_PATH}: ${BASELINE_PERIODISERING_MISMATCHES}" >&2
  exit 2
fi
if [[ ! "${BASELINE_PERIODISERING_UNRESOLVED}" =~ ^[0-9]+$ ]]; then
  echo "Invalid baseline value in ${PERIODISERING_UNRESOLVED_BASELINE_PATH}: ${BASELINE_PERIODISERING_UNRESOLVED}" >&2
  exit 2
fi
if [[ ! "${BASELINE_PERIODISERING_INVALID}" =~ ^[0-9]+$ ]]; then
  echo "Invalid baseline value in ${PERIODISERING_INVALID_BASELINE_PATH}: ${BASELINE_PERIODISERING_INVALID}" >&2
  exit 2
fi

if (( ACTUAL_PERIODISERING_MISMATCHES > BASELINE_PERIODISERING_MISMATCHES )); then
  echo "Periodisering mismatch count regression: actual=${ACTUAL_PERIODISERING_MISMATCHES}, baseline=${BASELINE_PERIODISERING_MISMATCHES}" >&2
  echo "Type checked: paragraph_periodisering_mismatch" >&2
  exit 11
fi
if (( ACTUAL_PERIODISERING_UNRESOLVED > BASELINE_PERIODISERING_UNRESOLVED )); then
  echo "Periodisering unresolved count regression: actual=${ACTUAL_PERIODISERING_UNRESOLVED}, baseline=${BASELINE_PERIODISERING_UNRESOLVED}" >&2
  echo "Type checked: paragraph_periodisering_unresolved" >&2
  exit 11
fi
if (( ACTUAL_PERIODISERING_INVALID > BASELINE_PERIODISERING_INVALID )); then
  echo "Periodisering invalid count regression: actual=${ACTUAL_PERIODISERING_INVALID}, baseline=${BASELINE_PERIODISERING_INVALID}" >&2
  echo "Type checked: paragraph_periodisering_invalid" >&2
  exit 11
fi

echo "Reconciliation check passed."
echo "See report files next to input:"
echo "  ${INPUT_PATH%/*}/reconciliation-report.txt"
echo "  ${INPUT_PATH%/*}/reconciliation-new-high.txt"
echo "  ${INPUT_PATH%/*}/reconciliation.json"
echo "Periodisering mismatch count: ${ACTUAL_PERIODISERING_MISMATCHES} (baseline ${BASELINE_PERIODISERING_MISMATCHES})"
echo "Periodisering unresolved count: ${ACTUAL_PERIODISERING_UNRESOLVED} (baseline ${BASELINE_PERIODISERING_UNRESOLVED})"
echo "Periodisering invalid count: ${ACTUAL_PERIODISERING_INVALID} (baseline ${BASELINE_PERIODISERING_INVALID})"

SCHEDULE_JSON="${INPUT_PATH%/*}/periodisering-schedule.json"
if [[ -x "${ROOT_DIR}/tools/check_periodisering_schedule.sh" ]]; then
  "${ROOT_DIR}/tools/check_periodisering_schedule.sh" "${SCHEDULE_JSON}" "$(date +%F)" "${SCHEDULE_WINDOW_DAYS}"
fi
