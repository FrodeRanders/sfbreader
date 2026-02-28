#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_PATH="${1:-${ROOT_DIR}/data/sfs-2010-110.txt.xml}"
SCHEDULE_WINDOW_DAYS="${2:-0}"

echo "[1/4] Running unit tests..."
(cd "${ROOT_DIR}" && mvn -q test)

echo "[2/4] Running reconciliation + baseline checks..."
"${ROOT_DIR}/tools/check_reconciliation.sh" "${INPUT_PATH}" \
  "${ROOT_DIR}/data/reconciliation-baseline.txt" \
  "/tmp/sfsreader-check-all" \
  "${ROOT_DIR}/data/periodisering-mismatch-baseline.txt" \
  "${ROOT_DIR}/data/periodisering-unresolved-baseline.txt" \
  "${ROOT_DIR}/data/periodisering-invalid-baseline.txt" \
  "${SCHEDULE_WINDOW_DAYS}"

echo "[3/4] Running schedule guard..."
"${ROOT_DIR}/tools/check_periodisering_schedule.sh" \
  "${INPUT_PATH%/*}/periodisering-schedule.json" \
  "$(date +%F)" \
  "${SCHEDULE_WINDOW_DAYS}"

echo "[4/4] Checking baseline snapshot note..."
"${ROOT_DIR}/tools/check_baseline_note.sh"

echo "All checks passed."
