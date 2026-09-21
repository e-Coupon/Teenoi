#!/usr/bin/env python3
"""Generate one Swensen's coupon from the Swensen Master."""

from __future__ import annotations

import argparse
import base64
import io
import re
import sys
import tempfile
from pathlib import Path

import barcode
from barcode.writer import ImageWriter
from PIL import Image
from pyzbar.pyzbar import decode as zbar_decode


ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / "Swensen" / "_MASTER" / "index.html"
PLACEHOLDER = "SW000000000000"
CODE_RE = re.compile(r"^[A-Z0-9]{6,32}$")
STORAGE_RE = re.compile(r"swensen_[a-z0-9]+_used_v1", re.I)
BARCODE_RE = re.compile(
    r'(<img\s+id=["\']barcode["\'][^>]*\bsrc=["\'])[^"\']+((?:["\'][^>]*\balt=["\']Barcode\s+)[A-Z0-9]{6,32}["\'])',
    re.I | re.S,
)


def fail(message: str) -> None:
    raise RuntimeError(message)


def barcode_data_uri(code: str) -> str:
    with tempfile.TemporaryDirectory() as td:
        target = Path(td) / "barcode"
        instance = barcode.get("code128", code, writer=ImageWriter())
        options = {
            "write_text": True,
            "font_size": 20,
            "text_distance": 4,
            "module_width": 0.33,
            "module_height": 18.0,
            "quiet_zone": 3.0,
        }
        output = Path(instance.write(str(target), options))
        raw = output.read_bytes()
    # Re-encode once through Pillow so the output is a deterministic PNG.
    with Image.open(io.BytesIO(raw)) as img:
        png = io.BytesIO()
        img.convert("RGB").save(png, format="PNG", optimize=True)
        encoded = base64.b64encode(png.getvalue()).decode("ascii")
    return "data:image/png;base64," + encoded


def replace_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.I | re.S)
    if count != 1:
        fail(f"Master transformation failed for {label}: expected exactly 1 match, got {count}")
    return updated


def transform_master(master_html: str, code: str) -> str:
    if PLACEHOLDER not in master_html:
        fail("Master placeholder is missing")

    html = master_html
    html = replace_once(
        html,
        rf"(<title>[^<]*?—\s*){re.escape(PLACEHOLDER)}(\s*</title>)",
        rf"\g<1>{code}\g<2>",
        "title code",
    )
    html = replace_once(
        html,
        rf'(<div\s+id=["\']code["\']\s*>){re.escape(PLACEHOLDER)}(</div>)',
        rf"\g<1>{code}\g<2>",
        "visible code",
    )
    html = replace_once(
        html,
        rf'(const\s+COUPON_KEY\s*=\s*["\']swensen_){re.escape(PLACEHOLDER.lower())}(_used_v1["\'];)',
        rf"\g<1>{code.lower()}\g<2>",
        "storage key",
    )
    barcode_uri = barcode_data_uri(code)
    html = replace_once(
        html,
        r'(<img\s+id=["\']barcode["\'][^>]*\bsrc=["\'])[^"\']+(["\'][^>]*\balt=["\']Barcode\s+)[A-Z0-9]{6,32}(["\'])',
        rf"\g<1>{barcode_uri}\g<2>{code}\g<3>",
        "barcode image",
    )

    for pattern, label in (
        (r"<title>[^<]*</title>", "title"),
        (r'<div\s+id=["\']code["\'][^>]*>[^<]*</div>', "visible code"),
        (r'const\s+COUPON_KEY\s*=\s*["\'][^"\']+["\']', "storage key"),
        (r'<img\s+id=["\']barcode["\'][^>]*\balt=["\'][^"\']*["\']', "barcode alt"),
    ):
        m = re.search(pattern, html, re.I | re.S)
        if not m:
            fail(f"Generated page is missing {label}")
        if PLACEHOLDER.lower() in m.group(0).lower():
            fail(f"Master placeholder leaked into {label}")

    return html


def decode_barcode_from_html(html: str, expected: str) -> None:
    match = re.search(
        r'<img\s+id=["\']barcode["\'][^>]*\bsrc=["\'](data:image/png;base64,[^"\']+)["\']',
        html,
        re.I | re.S,
    )
    if not match:
        fail("Generated page has no PNG barcode data URI")

    raw = base64.b64decode(match.group(1).split(",", 1)[1], validate=True)
    with Image.open(io.BytesIO(raw)) as img:
        if img.format != "PNG":
            fail("Embedded barcode is not PNG")
        decoded = [item.data.decode("utf-8", "strict").strip() for item in zbar_decode(img)]
    if expected not in decoded:
        fail(f"Barcode does not decode to {expected!r}; decoded={decoded!r}")


def next_sequence() -> int:
    highest = 0
    for p in (ROOT / "Swensen").glob("*/*/index.html"):
        m = re.fullmatch(r"\((\d+)\)", p.parent.name)
        if m:
            highest = max(highest, int(m.group(1)))
    return highest + 1


def generate(code: str) -> Path:
    code = code.upper()
    if not CODE_RE.fullmatch(code):
        fail(f"Invalid Swensen coupon code: {code!r}; expected 6-32 uppercase letters/digits")
    if code == PLACEHOLDER:
        fail("The Master placeholder cannot be generated")
    if not MASTER.is_file():
        fail(f"Master file not found: {MASTER}")
    if (ROOT / "Swensen" / code).exists():
        fail(f"Coupon already exists: Swensen/{code}")

    master_html = MASTER.read_text(encoding="utf-8")
    generated = transform_master(master_html, code)
    decode_barcode_from_html(generated, code)

    sequence = next_sequence()
    output_dir = ROOT / "Swensen" / code / f"({sequence})"
    output_dir.mkdir(parents=True, exist_ok=False)
    output = output_dir / "index.html"
    output.write_text(generated, encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("code", help="Swensen coupon code")
    args = parser.parse_args()
    try:
        output = generate(args.code)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(f"GENERATED {output.relative_to(ROOT)}")
    print("Barcode verified against the exact coupon code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
