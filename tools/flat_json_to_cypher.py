#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple


LAG_ID_RE = re.compile(r"\((\d{4}:\d+)\)\s*$")
PERIOD_DATE_RE = re.compile(
    r"(?i)(Upphor att galla|Upph[öo]r att g[äa]lla|Trader i kraft|Tr[äa]der i kraft)\s+([UI]):(\d{4}-\d{2}-\d{2})"
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Convert flattened legal JSON rows to Cypher.")
    p.add_argument("-i", "--input", required=True, help="Input flat JSON.")
    p.add_argument("-o", "--output", required=True, help="Output cypher file.")
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
    p = s.split(" ", 1)
    return (p[0], p[1].strip()) if len(p) > 1 else (p[0], "")


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
    return "inst:" + "_".join(normalize_fragment(p) for p in parts if p and p.strip())


def cypher_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "\\'")


def props_to_map(props: Dict[str, object]) -> str:
    out: List[str] = []
    for k, v in props.items():
        if v is None:
            continue
        if isinstance(v, bool):
            out.append(f"{k}: {'true' if v else 'false'}")
        elif isinstance(v, (int, float)):
            out.append(f"{k}: {v}")
        else:
            out.append(f"{k}: '{cypher_escape(str(v))}'")
    return "{ " + ", ".join(out) + " }"


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
    point_text: Dict[str, str] = {}

    chapter_dates: Dict[str, Tuple[Optional[str], Optional[str]]] = {}
    paragraph_dates: Dict[str, Tuple[Optional[str], Optional[str]]] = {}

    rel_har_avdelning: List[Tuple[str, str]] = []
    rel_har_underavdelning: List[Tuple[str, str]] = []
    rel_har_kapitel: List[Tuple[str, str]] = []
    rel_har_kapitel_direkt: List[Tuple[str, str]] = []
    rel_har_paragraf: List[Tuple[str, str]] = []
    rel_har_paragraf_direkt: List[Tuple[str, str]] = []
    rel_har_stycke: List[Tuple[str, str]] = []
    rel_har_punkt: List[Tuple[str, str]] = []

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
            rel_har_avdelning.append((lag_res, avd_res))

            if under_raw:
                under_id, under_title = split_heading(under_raw)
                under_key = (avd_id, under_id or normalize_fragment(under_raw))
                under_res = under_nodes.setdefault(under_key, make_resource("Underavdelning", avd_id, under_key[1]))
                if under_title:
                    under_titles[under_key] = under_title
                rel_har_underavdelning.append((avd_res, under_res))

        kap_res = None
        cap_marker = avd_id if avd_id else "LAG"
        if kapitel:
            kap_key = (cap_marker, kapitel)
            kap_res = kapitel_nodes.setdefault(kap_key, make_resource("Kapitel", cap_marker, kapitel))
            if row.get("kapitel_namn"):
                kapitel_titles[kap_key] = str(row["kapitel_namn"]).strip()
            elif kapitel == "0" and used_synthetic_context:
                kapitel_titles[kap_key] = "Auto-generated chapter for chapterless act"
            if avd_res:
                rel_har_kapitel.append((avd_res, kap_res))
            else:
                rel_har_kapitel_direkt.append((lag_res, kap_res))

        variant = str(row.get("paragraf_periodisering", "")).strip()
        par_marker = cap_marker if kapitel else "LAG"
        par_chapter = kapitel if kapitel else "DIRECT"
        par_key = (par_marker, par_chapter, paragraf, variant)
        par_res = paragraf_nodes.setdefault(
            par_key, make_resource("Paragraf", par_marker, par_chapter, paragraf, variant or "base")
        )
        if kap_res:
            rel_har_paragraf.append((kap_res, par_res))
        else:
            rel_har_paragraf_direkt.append((lag_res, par_res))

        rubrik = str(row.get("paragraf_rubrik", "")).strip()
        underrubrik = str(row.get("paragraf_underrubrik", "")).strip()
        if kapitel:
            parts = [f"Kapitel {kapitel}", f"Paragraf {paragraf}"]
        else:
            parts = [f"Paragraf {paragraf}"]
        if rubrik:
            parts.append(rubrik)
        if underrubrik:
            parts.append(underrubrik)
        if variant:
            parts.append(f"variant: {variant}")
        paragraf_labels[par_res] = " - ".join(parts)

        sty_key = (par_res, stycke)
        sty_res = stycke_nodes.setdefault(sty_key, make_resource("Stycke", par_res, stycke))
        rel_har_stycke.append((par_res, sty_res))

        pkt_key = (sty_res, "1")
        pkt_res = punkt_nodes.setdefault(pkt_key, make_resource("Punkt", sty_res, "1"))
        rel_har_punkt.append((sty_res, pkt_res))
        if pkt_res in point_text:
            if text:
                point_text[pkt_res] = point_text[pkt_res] + "\n" + text
        else:
            point_text[pkt_res] = text

        p_period = parse_periodisering_date(row.get("paragraf_periodisering"))
        if p_period:
            s, e = paragraph_dates.get(par_res, (None, None))
            if p_period[0] == "I":
                s = p_period[1]
            else:
                e = p_period[1]
            paragraph_dates[par_res] = (s, e)

        k_period = parse_periodisering_date(row.get("kapitel_periodisering"))
        if kap_res and k_period:
            s, e = chapter_dates.get(kap_res, (None, None))
            if k_period[0] == "I":
                s = k_period[1]
            else:
                e = k_period[1]
            chapter_dates[kap_res] = (s, e)

    lag_labels = ":Resurs:Lag"

    lines: List[str] = []
    lines.append("// Generated from flat JSON by tools/flat_json_to_cypher.py")
    lines.append("CREATE CONSTRAINT resurs_id IF NOT EXISTS FOR (n:Resurs) REQUIRE n.id IS UNIQUE;")
    lines.append("")
    lines.append(
        f"MERGE (n{lag_labels} {{id: '{cypher_escape(lag_res)}'}}) "
        f"SET n += {props_to_map({'label_sv': lag_label, 'uri': lag_res, 'eliType': 'eli:LegalResource', 'subdivisionCode': 'eu:ACT'})};"
    )
    lines.append("")

    for avd_id, avd_res in sorted(avdelning_nodes.items()):
        props = {
            "label_sv": f"Avdelning {avd_id}",
            "uri": avd_res,
            "eliType": "eli:LegalResourceSubdivision",
            "subdivisionCode": "eu:PRT",
        }
        if avd_id in avdelning_titles:
            props["harTitel"] = avdelning_titles[avd_id]
        lines.append(f"MERGE (n:Resurs:Avdelning {{id: '{cypher_escape(avd_res)}'}}) SET n += {props_to_map(props)};")

    for (avd_id, under_id), under_res in sorted(under_nodes.items()):
        props = {
            "label_sv": f"Underavdelning {under_id}",
            "uri": under_res,
            "eliType": "eli:LegalResourceSubdivision",
            "subdivisionCode": "eu:TIS",
        }
        if (avd_id, under_id) in under_titles:
            props["harTitel"] = under_titles[(avd_id, under_id)]
        lines.append(
            f"MERGE (n:Resurs:Underavdelning {{id: '{cypher_escape(under_res)}'}}) SET n += {props_to_map(props)};"
        )

    for (marker, kap), kap_res in sorted(kapitel_nodes.items(), key=lambda kv: (kv[0][0], as_int(kv[0][1]), kv[0][1])):
        props = {
            "label_sv": f"Kapitel {kap}",
            "uri": kap_res,
            "eliType": "eli:LegalResourceSubdivision",
            "subdivisionCode": "eu:CPT",
        }
        if (marker, kap) in kapitel_titles:
            props["namn"] = kapitel_titles[(marker, kap)]
        s, e = chapter_dates.get(kap_res, (None, None))
        if s:
            props["giltigFrom"] = s
        if e:
            props["giltigTill"] = e
        lines.append(f"MERGE (n:Resurs:Kapitel {{id: '{cypher_escape(kap_res)}'}}) SET n += {props_to_map(props)};")

    def par_sort(item: Tuple[Tuple[str, str, str, str], str]) -> Tuple[str, int, int, str]:
        (marker, chap, par, _), _res = item
        p_num, p_suffix = sort_key_for_paragraph(par)
        c_num = 0 if chap == "DIRECT" else as_int(chap)
        return marker, c_num, p_num, p_suffix

    for _key, par_res in sorted(paragraf_nodes.items(), key=par_sort):
        props = {
            "label_sv": paragraf_labels.get(par_res, "Paragraf"),
            "uri": par_res,
            "eliType": "eli:LegalResourceSubdivision",
            "subdivisionCode": "eu:ART",
        }
        s, e = paragraph_dates.get(par_res, (None, None))
        if s:
            props["giltigFrom"] = s
        if e:
            props["giltigTill"] = e
        lines.append(f"MERGE (n:Resurs:Paragraf {{id: '{cypher_escape(par_res)}'}}) SET n += {props_to_map(props)};")

    for (_par, sty), sty_res in sorted(stycke_nodes.items(), key=lambda kv: (kv[0][0], as_int(kv[0][1]), kv[0][1])):
        props = {
            "label_sv": f"Stycke {sty}",
            "uri": sty_res,
            "eliType": "eli:LegalResourceSubdivision",
            "subdivisionCode": "eu:PAR",
        }
        lines.append(f"MERGE (n:Resurs:Stycke {{id: '{cypher_escape(sty_res)}'}}) SET n += {props_to_map(props)};")

    for (_sty, _), pkt_res in sorted(punkt_nodes.items(), key=lambda kv: kv[1]):
        props = {
            "label_sv": "Textpunkt",
            "uri": pkt_res,
            "harText": point_text.get(pkt_res, ""),
            "eliType": "eli:LegalResourceSubdivision",
            "subdivisionCode": "eu:SUB",
        }
        lines.append(f"MERGE (n:Resurs:Punkt {{id: '{cypher_escape(pkt_res)}'}}) SET n += {props_to_map(props)};")

    lines.append("")

    def emit_rel(pairs: List[Tuple[str, str]], forward: str, inverse: str) -> None:
        seen = set()
        for src, dst in pairs:
            if (src, dst) in seen:
                continue
            seen.add((src, dst))
            lines.append(
                f"MATCH (a:Resurs {{id: '{cypher_escape(src)}'}}), (b:Resurs {{id: '{cypher_escape(dst)}'}}) MERGE (a)-[:{forward}]->(b);"
            )
            lines.append(
                f"MATCH (a:Resurs {{id: '{cypher_escape(src)}'}}), (b:Resurs {{id: '{cypher_escape(dst)}'}}) MERGE (b)-[:{inverse}]->(a);"
            )

    emit_rel(rel_har_avdelning, "HAR_AVDELNING", "INGAR_I_LAG")
    emit_rel(rel_har_underavdelning, "HAR_UNDERAVDELNING", "INGAR_I_AVDELNING")
    emit_rel(rel_har_kapitel, "HAR_KAPITEL", "INGAR_I_AVDELNING_KAPITEL")
    emit_rel(rel_har_kapitel_direkt, "HAR_KAPITEL_DIREKT", "INGAR_I_LAG_KAPITEL")
    emit_rel(rel_har_paragraf, "HAR_PARAGRAF", "INGAR_I_KAPITEL")
    emit_rel(rel_har_paragraf_direkt, "HAR_PARAGRAF_DIREKT", "INGAR_I_LAG_PARAGRAF")
    emit_rel(rel_har_stycke, "HAR_STYCKE", "INGAR_I_PARAGRAF")
    emit_rel(rel_har_punkt, "HAR_PUNKT", "INGAR_I_STYCKE")

    lines.append("")
    lines.append(
        f"// Summary: source_rows={len(rows)}, processed={processed_rows}, skipped={skipped_rows}, "
        f"synthetic_context={'true' if used_synthetic_context else 'false'}"
    )

    Path(args.output).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(
        f"Wrote {args.output} "
        f"(source rows={len(rows)}, processed={processed_rows}, skipped={skipped_rows}, cypher-lines={len(lines)})"
    )


if __name__ == "__main__":
    main()
