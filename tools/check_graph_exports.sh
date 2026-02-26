#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_FLAT_JSON="${1:-${ROOT_DIR}/data/SFB-flat.json}"
OUT_DIR="${2:-${ROOT_DIR}/data}"

if [[ ! -f "${INPUT_FLAT_JSON}" ]]; then
  echo "Flat JSON input not found: ${INPUT_FLAT_JSON}" >&2
  exit 2
fi

if [[ ! -d "${OUT_DIR}" ]]; then
  echo "Output directory not found: ${OUT_DIR}" >&2
  exit 2
fi

BASENAME="$(basename "${INPUT_FLAT_JSON}")"
BASESTEM="${BASENAME%.json}"
TTL_OUT="${OUT_DIR}/${BASESTEM}.ttl"
CYPHER_OUT="${OUT_DIR}/${BASESTEM}.cypher"

echo "[1/3] Regenerating TTL..."
"${ROOT_DIR}/tools/flat_json_to_ttl.py" -i "${INPUT_FLAT_JSON}" -o "${TTL_OUT}"

echo "[2/3] Regenerating Cypher..."
"${ROOT_DIR}/tools/flat_json_to_cypher.py" -i "${INPUT_FLAT_JSON}" -o "${CYPHER_OUT}"

if [[ ! -s "${TTL_OUT}" ]]; then
  echo "TTL output missing/empty: ${TTL_OUT}" >&2
  exit 3
fi
if [[ ! -s "${CYPHER_OUT}" ]]; then
  echo "Cypher output missing/empty: ${CYPHER_OUT}" >&2
  exit 3
fi

echo "[3/3] Validating structural count parity..."
python - "${TTL_OUT}" "${CYPHER_OUT}" <<'PY'
import re
import sys
from pathlib import Path

ttl_path = Path(sys.argv[1])
cypher_path = Path(sys.argv[2])
ttl = ttl_path.read_text(encoding="utf-8")
cypher = cypher_path.read_text(encoding="utf-8")

def count(pattern: str, text: str) -> int:
    return len(re.findall(pattern, text, flags=re.MULTILINE))

ttl_nodes = {
    "LagOrEnkelLag": count(r"\ba def:(?:Lag|EnkelLag)\b", ttl),
    "Avdelning": count(r"\ba def:Avdelning\b", ttl),
    "Underavdelning": count(r"\ba def:Underavdelning\b", ttl),
    "Kapitel": count(r"\ba def:Kapitel\b", ttl),
    "Paragraf": count(r"\ba def:Paragraf\b", ttl),
    "Stycke": count(r"\ba def:Stycke\b", ttl),
    "Punkt": count(r"\ba def:Punkt\b", ttl),
}
cypher_nodes = {
    "LagOrEnkelLag": count(r"MERGE \(n:Resurs:Lag(?:\:EnkelLag)? \{id:", cypher),
    "Avdelning": count(r"MERGE \(n:Resurs:Avdelning \{id:", cypher),
    "Underavdelning": count(r"MERGE \(n:Resurs:Underavdelning \{id:", cypher),
    "Kapitel": count(r"MERGE \(n:Resurs:Kapitel \{id:", cypher),
    "Paragraf": count(r"MERGE \(n:Resurs:Paragraf \{id:", cypher),
    "Stycke": count(r"MERGE \(n:Resurs:Stycke \{id:", cypher),
    "Punkt": count(r"MERGE \(n:Resurs:Punkt \{id:", cypher),
}

ttl_rels = {
    "HAR_AVDELNING": count(r"\bdef:harAvdelning\b", ttl),
    "HAR_UNDERAVDELNING": count(r"\bdef:harUnderavdelning\b", ttl),
    "HAR_KAPITEL": count(r"\bdef:harKapitel\b", ttl),
    "HAR_KAPITEL_DIREKT": count(r"\bdef:harKapitelDirekt\b", ttl),
    "HAR_PARAGRAF": count(r"\bdef:harParagraf\b", ttl),
    "HAR_PARAGRAF_DIREKT": count(r"\bdef:harParagrafDirekt\b", ttl),
    "HAR_STYCKE": count(r"\bdef:harStycke\b", ttl),
    "HAR_PUNKT": count(r"\bdef:harPunkt\b", ttl),
}
cypher_rels = {
    "HAR_AVDELNING": count(r"\[:HAR_AVDELNING\]", cypher),
    "HAR_UNDERAVDELNING": count(r"\[:HAR_UNDERAVDELNING\]", cypher),
    "HAR_KAPITEL": count(r"\[:HAR_KAPITEL\]", cypher),
    "HAR_KAPITEL_DIREKT": count(r"\[:HAR_KAPITEL_DIREKT\]", cypher),
    "HAR_PARAGRAF": count(r"\[:HAR_PARAGRAF\]", cypher),
    "HAR_PARAGRAF_DIREKT": count(r"\[:HAR_PARAGRAF_DIREKT\]", cypher),
    "HAR_STYCKE": count(r"\[:HAR_STYCKE\]", cypher),
    "HAR_PUNKT": count(r"\[:HAR_PUNKT\]", cypher),
}

errors = []
for k, v in ttl_nodes.items():
    if cypher_nodes.get(k) != v:
        errors.append(f"Node count mismatch {k}: ttl={v}, cypher={cypher_nodes.get(k)}")
for k, v in ttl_rels.items():
    if cypher_rels.get(k) != v:
        errors.append(f"Relation count mismatch {k}: ttl={v}, cypher={cypher_rels.get(k)}")

if errors:
    print("Graph export validation failed:")
    for e in errors:
        print(" -", e)
    sys.exit(11)

print("Graph export validation passed.")
print("Node counts:", ttl_nodes)
print("Relation counts:", ttl_rels)
PY

echo "Graph export check passed."
echo "  TTL:    ${TTL_OUT}"
echo "  Cypher: ${CYPHER_OUT}"
