#!/usr/bin/env python3
import argparse
import json
from pathlib import Path
from typing import Any, Dict, List


def extract_texts(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    texts: List[Dict[str, Any]] = []

    def traverse(obj: Any, context: Dict[str, Any]) -> None:
        if isinstance(obj, dict):
            current_context = context.copy()

            if "nummer" in obj:
                is_paragraf_obj = "stycke" in obj
                is_kapitel_obj = "paragraf" in obj
                is_stycke_obj = "text" in obj and "punkt" in obj

                if is_paragraf_obj:
                    current_context["paragraf"] = obj["nummer"]
                elif is_kapitel_obj:
                    current_context["kapitel"] = obj["nummer"]
                elif not is_stycke_obj:
                    if "kapitel" in context and "paragraf" not in context:
                        current_context["paragraf"] = obj["nummer"]
                    elif "kapitel" not in context:
                        current_context["kapitel"] = obj["nummer"]

            if "namn" in obj:
                if "kapitel" in current_context:
                    current_context["kapitel_namn"] = obj["namn"]
                else:
                    current_context["namn"] = obj["namn"]

            if "avdelning" in obj and isinstance(obj["avdelning"], dict):
                current_context["avdelning_id"] = obj["avdelning"].get("id", "")
                current_context["avdelning_namn"] = obj["avdelning"].get("namn", "")

            if "underavdelning" in obj and isinstance(obj["underavdelning"], dict):
                current_context["underavdelning_id"] = obj["underavdelning"].get("id", "")
                current_context["underavdelning_namn"] = obj["underavdelning"].get("namn", "")

            if "rubrik" in obj:
                current_context["rubrik"] = obj["rubrik"]
            if "underrubrik" in obj:
                current_context["underrubrik"] = obj["underrubrik"]

            if "referens" in obj and isinstance(obj["referens"], list) and obj["referens"]:
                current_context["referens"] = obj["referens"][0]

            if "periodisering" in obj:
                if "kapitel" in current_context and "paragraf" not in current_context:
                    current_context["kapitel_periodisering"] = obj["periodisering"]
                if "paragraf" in current_context:
                    current_context["paragraf_periodisering"] = obj["periodisering"]

            for version_key in ("versionStatus", "versionKind", "versionDate", "versionIdentity"):
                if version_key in obj:
                    current_context[version_key] = obj[version_key]

            if "kategori" in obj:
                current_context["kategori"] = obj["kategori"]

            if "stycke" in obj and isinstance(obj["stycke"], list):
                for stycke in obj["stycke"]:
                    if isinstance(stycke, dict):
                        stycke_context = current_context.copy()
                        stycke_context["stycke"] = stycke.get("nummer")
                        concatenated_text = "\n".join(stycke.get("text", []))
                        texts.append({"context": stycke_context, "text": concatenated_text})

            for value in obj.values():
                traverse(value, current_context)

        elif isinstance(obj, list):
            for item in obj:
                traverse(item, context)

    traverse(data, {})
    return texts


def assemble_item(item: Dict[str, Any], lag_label: str) -> Dict[str, Any]:
    context = item["context"]

    avdelning = f"{context.get('avdelning_id', '')} {context.get('avdelning_namn', '')}".strip()
    underavdelning = f"{context.get('underavdelning_id', '')} {context.get('underavdelning_namn', '')}".strip()

    out: Dict[str, Any] = {
        "lag": lag_label,
        "text": item["text"],
    }

    if context.get("kapitel") not in (None, ""):
        out["kapitel"] = context.get("kapitel")
    if context.get("paragraf") not in (None, ""):
        out["paragraf"] = context.get("paragraf")
    if context.get("stycke") not in (None, ""):
        out["stycke"] = context.get("stycke")

    if avdelning:
        out["avdelning"] = avdelning
    if underavdelning:
        out["underavdelning"] = underavdelning

    optional_fields = (
        ("kapitel_namn", "kapitel_namn"),
        ("kapitel_periodisering", "kapitel_periodisering"),
        ("rubrik", "paragraf_rubrik"),
        ("underrubrik", "paragraf_underrubrik"),
        ("paragraf_periodisering", "paragraf_periodisering"),
        ("referens", "referens"),
        ("kategori", "kategori"),
        ("versionStatus", "versionStatus"),
        ("versionKind", "versionKind"),
        ("versionDate", "versionDate"),
        ("versionIdentity", "versionIdentity"),
    )
    for src, dst in optional_fields:
        value = context.get(src)
        if value not in (None, ""):
            out[dst] = value

    return out


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Flatten structured legal JSON into one entry per stycke.")
    parser.add_argument(
        "--input",
        "-i",
        default="../data/SFB.json",
        help="Input JSON file (default: ../data/SFB.json)",
    )
    parser.add_argument(
        "--output",
        "-o",
        default=None,
        help="Output JSON file (default: <input-stem>-flat.json in current directory)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input)
    if not input_path.exists():
        raise FileNotFoundError(f"Input file does not exist: {input_path}")

    output_path = Path(args.output) if args.output else input_path.with_name(f"{input_path.stem}-flat.json")

    try:
        with input_path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except json.JSONDecodeError as exc:
        raise ValueError(
            f"Input is not valid JSON: {input_path}. "
            f"Use the structured output JSON (e.g. data/output.json or data/SFB.json)."
        ) from exc

    lag_namn = data.get("namn", "")
    lag_id = data.get("id", "")
    if lag_namn and lag_id and f"({lag_id})" in lag_namn:
        lag_label = lag_namn
    elif lag_namn and lag_id:
        lag_label = f"{lag_namn} ({lag_id})"
    elif lag_namn:
        lag_label = lag_namn
    elif lag_id:
        lag_label = lag_id
    else:
        lag_label = "Unknown law"

    texts_with_context = extract_texts(data)
    flattened = [assemble_item(item, lag_label) for item in texts_with_context]

    with output_path.open("w", encoding="utf-8") as file:
        json.dump(flattened, file, ensure_ascii=False, indent=2)
        file.write("\n")

    print(f"Wrote {len(flattened)} rows to {output_path}")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        raise SystemExit(str(exc))
