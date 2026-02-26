#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATUS_PATH="${1:-${ROOT_DIR}/STATUS.md}"
MISMATCH_BASELINE="${2:-${ROOT_DIR}/data/periodisering-mismatch-baseline.txt}"
UNRESOLVED_BASELINE="${3:-${ROOT_DIR}/data/periodisering-unresolved-baseline.txt}"
INVALID_BASELINE="${4:-${ROOT_DIR}/data/periodisering-invalid-baseline.txt}"

for f in "${STATUS_PATH}" "${MISMATCH_BASELINE}" "${UNRESOLVED_BASELINE}" "${INVALID_BASELINE}"; do
  if [[ ! -f "${f}" ]]; then
    echo "Required file not found: ${f}" >&2
    exit 2
  fi
done

MISMATCH="$(tr -d '[:space:]' < "${MISMATCH_BASELINE}")"
UNRESOLVED="$(tr -d '[:space:]' < "${UNRESOLVED_BASELINE}")"
INVALID="$(tr -d '[:space:]' < "${INVALID_BASELINE}")"

for value in "${MISMATCH}" "${UNRESOLVED}" "${INVALID}"; do
  if [[ ! "${value}" =~ ^[0-9]+$ ]]; then
    echo "Baseline value must be an integer. Found: ${value}" >&2
    exit 2
  fi
done

EXPECTED_LINE="Baseline snapshot: mismatch=${MISMATCH} unresolved=${UNRESOLVED} invalid=${INVALID}"
if rg -Fq "${EXPECTED_LINE}" "${STATUS_PATH}"; then
  echo "Baseline note check passed: ${EXPECTED_LINE}"
  exit 0
fi

echo "Baseline note missing in ${STATUS_PATH}" >&2
echo "Expected line:" >&2
echo "  ${EXPECTED_LINE}" >&2
echo "Add/update this line in STATUS.md when baselines change." >&2
exit 15
