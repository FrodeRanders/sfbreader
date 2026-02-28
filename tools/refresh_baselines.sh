#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="${ROOT_DIR}/target/sfsreader-1.0-SNAPSHOT.jar"
INPUT_PATH="${1:-${ROOT_DIR}/data/sfs-2010-110.txt.xml}"
OUT_DIR="${2:-/tmp/sfsreader-refresh-baselines}"
TEMPLATE_PATH="${ROOT_DIR}/template/sfs.stg"
RECON_BASELINE="${ROOT_DIR}/data/reconciliation-baseline.txt"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Jar not found: ${JAR_PATH}" >&2
  echo "Build first with: mvn -q -DskipTests package" >&2
  exit 2
fi
if [[ ! -f "${INPUT_PATH}" ]]; then
  echo "Input file not found: ${INPUT_PATH}" >&2
  exit 2
fi
if [[ ! -f "${TEMPLATE_PATH}" ]]; then
  echo "Template file not found: ${TEMPLATE_PATH}" >&2
  exit 2
fi

mkdir -p "${OUT_DIR}"

java -jar "${JAR_PATH}" \
  -s hybrid \
  -w "${RECON_BASELINE}" \
  -t "${TEMPLATE_PATH}" \
  -d "${OUT_DIR}" \
  -- "${INPUT_PATH}"

"${ROOT_DIR}/tools/update_periodisering_baselines.sh" "${INPUT_PATH%/*}/reconciliation.json"

echo "Refreshed baselines:"
echo "  ${RECON_BASELINE}"
echo "  ${ROOT_DIR}/data/periodisering-mismatch-baseline.txt"
echo "  ${ROOT_DIR}/data/periodisering-unresolved-baseline.txt"
echo "  ${ROOT_DIR}/data/periodisering-invalid-baseline.txt"
