#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RECONCILIATION_JSON="${1:-${ROOT_DIR}/data/reconciliation.json}"
MISMATCH_BASELINE="${2:-${ROOT_DIR}/data/periodisering-mismatch-baseline.txt}"
UNRESOLVED_BASELINE="${3:-${ROOT_DIR}/data/periodisering-unresolved-baseline.txt}"
INVALID_BASELINE="${4:-${ROOT_DIR}/data/periodisering-invalid-baseline.txt}"

if [[ ! -f "${RECONCILIATION_JSON}" ]]; then
  echo "Reconciliation JSON not found: ${RECONCILIATION_JSON}" >&2
  exit 2
fi

read -r MISMATCH UNRESOLVED INVALID <<EOF
$(python - "${RECONCILIATION_JSON}" <<'PY'
import json
import sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
by_type = data.get("byType", {})
print(
    int(by_type.get("paragraph_periodisering_mismatch", 0)),
    int(by_type.get("paragraph_periodisering_unresolved", 0)),
    int(by_type.get("paragraph_periodisering_invalid", 0)),
)
PY
)
EOF

printf '%s\n' "${MISMATCH}" > "${MISMATCH_BASELINE}"
printf '%s\n' "${UNRESOLVED}" > "${UNRESOLVED_BASELINE}"
printf '%s\n' "${INVALID}" > "${INVALID_BASELINE}"

echo "Updated periodisering baselines:"
echo "  ${MISMATCH_BASELINE}: ${MISMATCH}"
echo "  ${UNRESOLVED_BASELINE}: ${UNRESOLVED}"
echo "  ${INVALID_BASELINE}: ${INVALID}"
