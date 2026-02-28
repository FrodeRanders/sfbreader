#!/usr/bin/env python3
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Download an SFS law XML by lagreferens, run sfbreader, and produce named JSON + flat JSON."
    )
    p.add_argument("lagref", help='Lagreferens, e.g. "2018:585"')
    p.add_argument("--data-dir", default="data", help="Directory for source and outputs (default: data)")
    p.add_argument("--jar", default="target/sfsreader-1.0-SNAPSHOT.jar", help="Path to SFS-reader jar")
    p.add_argument("--source-mode", default="hybrid", choices=("html", "text", "hybrid"), help="Source mode")
    p.add_argument("--skip-download", action="store_true", help="Do not download if the XML file already exists")
    p.add_argument("--force-download", action="store_true", help="Always download, even if local file exists")
    return p.parse_args()


def normalize_lagref(lagref: str) -> str:
    lagref = lagref.strip()
    if not re.fullmatch(r"\d{4}:\d+[a-zA-Z]?", lagref):
        raise ValueError(f"Invalid lagreferens '{lagref}'. Expected format like 2018:585")
    return lagref


def safe_filename(label: str) -> str:
    # Keep Swedish letters/spaces, remove filesystem-hostile characters.
    out = label.replace(":", "-")
    out = re.sub(r"[\\/<>\"|?*]", "-", out)
    out = re.sub(r"\s+", " ", out).strip()
    return out


def extract_title(xml_path: Path) -> str | None:
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError:
        return None
    root = tree.getroot()
    title = root.findtext("./dokument/titel")
    if title is None:
        return None
    title = title.strip()
    return title or None


def run(cmd: list[str]) -> None:
    subprocess.run(cmd, check=True)


def main() -> None:
    args = parse_args()
    lagref = normalize_lagref(args.lagref)

    data_dir = Path(args.data_dir)
    data_dir.mkdir(parents=True, exist_ok=True)

    lagref_dash = lagref.replace(":", "-")
    source_xml = data_dir / f"sfs-{lagref_dash}.txt.xml"
    source_url = f"https://data.riksdagen.se/dokument/sfs-{lagref_dash}.txt"

    do_download = args.force_download or not source_xml.exists()
    if args.skip_download and source_xml.exists():
        do_download = False

    if do_download:
        run(["curl", "-fsSL", "-o", str(source_xml), source_url])

    title = extract_title(source_xml) or f"SFS {lagref}"
    base = safe_filename(title)
    json_out = data_dir / f"{base}.json"
    flat_out = data_dir / f"{base}-flat.json"

    run(
        [
            "java",
            "-jar",
            str(args.jar),
            "-s",
            args.source_mode,
            "-o",
            str(json_out),
            "--",
            str(source_xml),
        ]
    )

    run(
        [
            "python3",
            "tools/flatten_json.py",
            "-i",
            str(json_out),
            "-o",
            str(flat_out),
        ]
    )

    print(f"Source XML: {source_xml}")
    print(f"Structured JSON: {json_out}")
    print(f"Flattened JSON: {flat_out}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as e:
        raise SystemExit(f"Command failed (exit {e.returncode}): {' '.join(e.cmd)}")
    except Exception as e:
        raise SystemExit(str(e))
