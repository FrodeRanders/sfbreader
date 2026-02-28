#!/usr/bin/env python3
import argparse
import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


LAG_ID_RE = re.compile(r"\((\d{4}:\d+)\)\s*$")
PERIOD_DATE_RE = re.compile(
    r"(?i)(Upphor att galla|Upph[öo]r att g[äa]lla|Trader i kraft|Tr[äa]der i kraft)\s+([UI]):(\d{4}-\d{2}-\d{2})"
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Convert flattened legal JSON rows to RDF/Turtle instances.")
    p.add_argument("-i", "--input", required=True, help="Input flattened JSON file.")
    p.add_argument("-o", "--output", required=True, help="Output TTL file.")
    p.add_argument("--instance-base", default="http://fk.se/ontology/instances/", help="inst: base IRI")
    p.add_argument("--definitions-prefix", default="http://fk.se/ontology/definitions#", help="def: base IRI")
    p.add_argument(
        "--synthetic-context",
        action="store_true",
        help="Use synthetic avdelning/kapitel for chapterless rows (legacy mode).",
    )
    return p.parse_args()


def normalize_fragment(value: str) -> str:
    value = value.strip().replace(" ", "_")
    value = re.sub(r"[^A-Za-z0-9_:-]", "_", value)
    value = re.sub(r"_+", "_", value).strip("_")
    return value or "X"


def split_heading(raw: Optional[str]) -> Tuple[str, str]:
    if not raw:
        return "", ""
    s = raw.strip()
    if not s:
        return "", ""
    parts = s.split(" ", 1)
    return (parts[0], parts[1].strip()) if len(parts) > 1 else (parts[0], "")


def ttl_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def parse_periodisering_date(raw: Optional[str]) -> Optional[Tuple[str, str]]:
    if not raw:
        return None
    m = PERIOD_DATE_RE.search(raw)
    if not m:
        return None
    return m.group(2).upper(), m.group(3)


def as_int(value: object, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def sort_key_for_paragraph(number: str) -> Tuple[int, str]:
    m = re.match(r"^\s*(\d+)\s*([a-zA-Z]?)\s*$", str(number))
    if not m:
        return 10**9, str(number)
    return int(m.group(1)), m.group(2).lower()


def make_resource(*parts: str) -> str:
    fragment = "_".join(normalize_fragment(p) for p in parts if p and p.strip())
    return f"inst:{fragment}"


def emit_node(subject: str, rdf_type: str, predicates: List[str]) -> List[str]:
    lines = [f"{subject} a {rdf_type}"]
    lines.extend(f"    {p}" for p in predicates)
    if len(lines) == 1:
        return [lines[0] + " .", ""]
    lines[0] += " ;"
    for i in range(1, len(lines) - 1):
        lines[i] += " ;"
    lines[-1] += " ."
    lines.append("")
    return lines


def main() -> None:
    args = parse_args()
    rows = json.loads(Path(args.input).read_text(encoding="utf-8"))
    if not isinstance(rows, list) or not rows:
        raise SystemExit(f"Invalid or empty flat JSON: {args.input}")

    lag_label = str(rows[0].get("lag", "Unknown law"))
    lag_id_match = LAG_ID_RE.search(lag_label)
    lag_id = lag_id_match.group(1) if lag_id_match else normalize_fragment(lag_label)
    lag_res = make_resource("Lag", lag_id)

    avdelning_nodes: Dict[str, str] = {}
    avdelning_titles: Dict[str, str] = {}
    under_nodes: Dict[Tuple[str, str], str] = {}
    under_titles: Dict[Tuple[str, str], str] = {}
    kapitel_nodes: Dict[Tuple[str, str], str] = {}
    kapitel_titles: Dict[Tuple[str, str], str] = {}
    paragraf_nodes: Dict[Tuple[str, str, str, str], str] = {}
    paragraf_labels: Dict[str, str] = {}
    stycke_nodes: Dict[Tuple[str, str], str] = {}
    punkt_nodes: Dict[Tuple[str, str], str] = {}

    chapter_dates: Dict[str, Tuple[Optional[str], Optional[str]]] = {}
    paragraph_dates: Dict[str, Tuple[Optional[str], Optional[str]]] = {}
    point_text: Dict[str, str] = {}

    by_lag_avdelning: Dict[str, Set[str]] = defaultdict(set)
    by_avdelning_under: Dict[str, Set[str]] = defaultdict(set)
    by_avdelning_kapitel: Dict[str, Set[str]] = defaultdict(set)
    by_lag_kapitel_direct: Dict[str, Set[str]] = defaultdict(set)
    by_kapitel_paragraf: Dict[str, Set[str]] = defaultdict(set)
    by_lag_paragraf_direct: Dict[str, Set[str]] = defaultdict(set)
    by_paragraf_stycke: Dict[str, Set[str]] = defaultdict(set)
    by_stycke_punkt: Dict[str, Set[str]] = defaultdict(set)

    used_synthetic_context = False
    processed_rows = 0
    skipped_rows = 0

    for row in rows:
        avd_raw = str(row.get("avdelning", "")).strip()
        under_raw = str(row.get("underavdelning", "")).strip()
        kapitel = str(row.get("kapitel", "")).strip()
        paragraf = str(row.get("paragraf", "")).strip()
        stycke = str(row.get("stycke", "")).strip()
        text = str(row.get("text", "")).strip()

        if not paragraf or not stycke:
            skipped_rows += 1
            continue

        has_real_chapter = bool(kapitel)

        if not has_real_chapter and args.synthetic_context:
            kapitel = "0"
            if not avd_raw:
                avd_raw = "A AUTO"
            used_synthetic_context = True

        processed_rows += 1

        avd_res = None
        avd_id = ""
        if avd_raw:
            avd_id, avd_title = split_heading(avd_raw)
            avd_id = avd_id or "A"
            avd_res = avdelning_nodes.setdefault(avd_id, make_resource("Avdelning", avd_id))
            if avd_title:
                avdelning_titles[avd_id] = avd_title
            by_lag_avdelning[lag_res].add(avd_res)

            if under_raw:
                under_id, under_title = split_heading(under_raw)
                under_key = (avd_id, under_id or normalize_fragment(under_raw))
                under_res = under_nodes.setdefault(under_key, make_resource("Underavdelning", avd_id, under_key[1]))
                if under_title:
                    under_titles[under_key] = under_title
                by_avdelning_under[avd_res].add(under_res)

        kap_res = None
        kap_key = ("", "")
        if kapitel:
            cap_marker = avd_id if avd_id else "LAG"
            kap_key = (cap_marker, kapitel)
            kap_res = kapitel_nodes.setdefault(kap_key, make_resource("Kapitel", cap_marker, kapitel))
            if row.get("kapitel_namn"):
                kapitel_titles[kap_key] = str(row["kapitel_namn"]).strip()
            elif kapitel == "0" and used_synthetic_context:
                kapitel_titles[kap_key] = "Auto-generated chapter for chapterless act"
            if avd_res:
                by_avdelning_kapitel[avd_res].add(kap_res)
            else:
                by_lag_kapitel_direct[lag_res].add(kap_res)

        variant = str(row.get("paragraf_periodisering", "")).strip()
        par_marker = kap_key[0] if kapitel else "LAG"
        par_chapter = kapitel if kapitel else "DIRECT"
        par_key = (par_marker, par_chapter, paragraf, variant)
        par_res = paragraf_nodes.setdefault(
            par_key, make_resource("Paragraf", par_marker, par_chapter, paragraf, variant or "base")
        )
        if kap_res:
            by_kapitel_paragraf[kap_res].add(par_res)
        else:
            by_lag_paragraf_direct[lag_res].add(par_res)

        rubrik = str(row.get("paragraf_rubrik", "")).strip()
        underrubrik = str(row.get("paragraf_underrubrik", "")).strip()
        if kapitel:
            label_parts = [f"Kapitel {kapitel}", f"Paragraf {paragraf}"]
        else:
            label_parts = [f"Paragraf {paragraf}"]
        if rubrik:
            label_parts.append(rubrik)
        if underrubrik:
            label_parts.append(underrubrik)
        if variant:
            label_parts.append(f"variant: {variant}")
        paragraf_labels[par_res] = " - ".join(label_parts)

        sty_key = (par_res, stycke)
        sty_res = stycke_nodes.setdefault(sty_key, make_resource("Stycke", par_res, stycke))
        by_paragraf_stycke[par_res].add(sty_res)

        pkt_key = (sty_res, "1")
        pkt_res = punkt_nodes.setdefault(pkt_key, make_resource("Punkt", sty_res, "1"))
        by_stycke_punkt[sty_res].add(pkt_res)
        point_text[pkt_res] = point_text[pkt_res] + ("\n" if point_text.get(pkt_res) and text else "") + text if pkt_res in point_text else text

        p_period = parse_periodisering_date(row.get("paragraf_periodisering"))
        if p_period:
            start, end = paragraph_dates.get(par_res, (None, None))
            if p_period[0] == "I":
                start = p_period[1]
            else:
                end = p_period[1]
            paragraph_dates[par_res] = (start, end)

        k_period = parse_periodisering_date(row.get("kapitel_periodisering"))
        if kap_res and k_period:
            start, end = chapter_dates.get(kap_res, (None, None))
            if k_period[0] == "I":
                start = k_period[1]
            else:
                end = k_period[1]
            chapter_dates[kap_res] = (start, end)

    triples: List[str] = []
    triples.extend(
        emit_node(
            lag_res,
            "def:Lag",
            [
                f"rdfs:label {ttl_string(lag_label)}@sv",
                "dct:type eu:ACT",
            ],
        )
    )

    for avd_id, avd_res in sorted(avdelning_nodes.items()):
        triples.append(f"{lag_res} def:harAvdelning {avd_res} .")
        triples.append(f"{avd_res} def:ingårILag {lag_res} .")
        preds = [f"rdfs:label {ttl_string(f'Avdelning {avd_id}')}@sv"]
        if avdelning_titles.get(avd_id):
            preds.append(f"def:harTitel {ttl_string(avdelning_titles[avd_id])}")
        preds.append("def:subdivisionCode eu:PRT")
        triples.extend(emit_node(avd_res, "def:Avdelning", preds))

    for (avd_id, under_id), under_res in sorted(under_nodes.items()):
        avd_res = avdelning_nodes[avd_id]
        triples.append(f"{avd_res} def:harUnderavdelning {under_res} .")
        triples.append(f"{under_res} def:ingårIAvdelning {avd_res} .")
        preds = [f"rdfs:label {ttl_string(f'Underavdelning {under_id}')}@sv"]
        if under_titles.get((avd_id, under_id)):
            preds.append(f"def:harTitel {ttl_string(under_titles[(avd_id, under_id)])}")
        preds.append("def:subdivisionCode eu:TIS")
        triples.extend(emit_node(under_res, "def:Underavdelning", preds))

    for (marker, kap), kap_res in sorted(kapitel_nodes.items(), key=lambda kv: (kv[0][0], as_int(kv[0][1]), kv[0][1])):
        if marker == "LAG":
            triples.append(f"{lag_res} def:harKapitelDirekt {kap_res} .")
            triples.append(f"{kap_res} def:ingårILagKapitel {lag_res} .")
        else:
            avd_res = avdelning_nodes[marker]
            triples.append(f"{avd_res} def:harKapitel {kap_res} .")
            triples.append(f"{kap_res} def:ingårIAvdelningKapitel {avd_res} .")
        preds = [f"rdfs:label {ttl_string(f'Kapitel {kap}')}@sv"]
        if kapitel_titles.get((marker, kap)):
            preds.append(f"rdfs:comment {ttl_string(kapitel_titles[(marker, kap)])}@sv")
        start, end = chapter_dates.get(kap_res, (None, None))
        if start:
            preds.append(f"def:giltigFrom {ttl_string(start)}^^xsd:date")
        if end:
            preds.append(f"def:giltigTill {ttl_string(end)}^^xsd:date")
        preds.append("def:subdivisionCode eu:CPT")
        triples.extend(emit_node(kap_res, "def:Kapitel", preds))

    def paragraph_sort(item: Tuple[Tuple[str, str, str, str], str]) -> Tuple[str, int, int, str]:
        (marker, chap, par, _variant), _ = item
        p_num, p_suffix = sort_key_for_paragraph(par)
        chap_num = 0 if chap == "DIRECT" else as_int(chap)
        return marker, chap_num, p_num, p_suffix

    for (marker, chap, par, _variant), par_res in sorted(paragraf_nodes.items(), key=paragraph_sort):
        if chap == "DIRECT":
            triples.append(f"{lag_res} def:harParagrafDirekt {par_res} .")
            triples.append(f"{par_res} def:ingårILagParagraf {lag_res} .")
        else:
            kap_res = kapitel_nodes[(marker, chap)]
            triples.append(f"{kap_res} def:harParagraf {par_res} .")
            triples.append(f"{par_res} def:ingårIKapitel {kap_res} .")
        preds = [f"rdfs:label {ttl_string(paragraf_labels.get(par_res, f'Paragraf {par}'))}@sv"]
        start, end = paragraph_dates.get(par_res, (None, None))
        if start:
            preds.append(f"def:giltigFrom {ttl_string(start)}^^xsd:date")
        if end:
            preds.append(f"def:giltigTill {ttl_string(end)}^^xsd:date")
        preds.append("def:subdivisionCode eu:ART")
        triples.extend(emit_node(par_res, "def:Paragraf", preds))

    for (par_res, sty), sty_res in sorted(stycke_nodes.items(), key=lambda kv: (kv[0][0], as_int(kv[0][1]), kv[0][1])):
        triples.append(f"{par_res} def:harStycke {sty_res} .")
        triples.append(f"{sty_res} def:ingårIParagraf {par_res} .")
        triples.extend(
            emit_node(
                sty_res,
                "def:Stycke",
                [
                    f"rdfs:label {ttl_string(f'Stycke {sty}')}@sv",
                    "def:subdivisionCode eu:PAR",
                ],
            )
        )

    for (_sty_key, pkt_res) in sorted(punkt_nodes.items(), key=lambda kv: kv[1]):
        sty_res = _sty_key[0]
        triples.append(f"{sty_res} def:harPunkt {pkt_res} .")
        triples.append(f"{pkt_res} def:ingårIStycke {sty_res} .")
        triples.extend(
            emit_node(
                pkt_res,
                "def:Punkt",
                [
                    f"def:harText {ttl_string(point_text.get(pkt_res, ''))}",
                    f"rdfs:label {ttl_string('Textpunkt')}@sv",
                    "def:subdivisionCode eu:SUB",
                ],
            )
        )

    prefixes = [
        f"@prefix def: <{args.definitions_prefix}> .",
        f"@prefix inst: <{args.instance_base}> .",
        "@prefix dct: <http://purl.org/dc/terms/> .",
        "@prefix eu: <http://publications.europa.eu/resource/authority/subdivision/> .",
        "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .",
        "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .",
        "",
    ]

    Path(args.output).write_text("\n".join(prefixes + triples).rstrip() + "\n", encoding="utf-8")
    print(
        f"Wrote {args.output} "
        f"(source rows={len(rows)}, processed={processed_rows}, skipped={skipped_rows}, ttl-lines={len(triples)})"
    )


if __name__ == "__main__":
    main()
