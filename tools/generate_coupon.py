#!/usr/bin/env python3
"""Generate one Teenoi /(3) coupon from the Master without hand-editing the page."""

from __future__ import annotations

import argparse
import base64
import io
import re
import sys
from pathlib import Path

import cv2
import numpy as np
import qrcode


ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / "MK00000" / "(3)" / "index.html"
CODE_RE = re.compile(r"^MK\d{6}$")


def fail(message: str) -> None:
    raise RuntimeError(message)


def qr_data_uri(code: str) -> str:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=8,
        border=4,
    )
    qr.add_data(code)
    qr.make(fit=True)

    image = qr.make_image()
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")


def replace_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.I | re.S)
    if count != 1:
        fail(f"Master transformation failed for {label}: expected exactly 1 match, got {count}")
    return updated


def transform_master(master_html: str, code: str) -> str:
    html = master_html

    html = replace_once(
        html,
        r'''(<title>[^<]*?—\s*)MK00000(\s*</title>)''',
        rf"\g<1>{code}\g<2>",
        "title code",
    )
    html = replace_once(
        html,
        r'''(<div\s+id=["']code["']\s*>)[^<]*(</div>)''',
        rf"\g<1>{code}\g<2>",
        "visible code",
    )
    html = replace_once(
        html,
        r'''(const\s+COUPON_KEY\s*=\s*["']teenoi_)mk00000(_used_v3_arrow_follow_reswipe["'];)''',
        rf"\g<1>{code.lower()}\g<2>",
        "storage key",
    )

    qr_uri = qr_data_uri(code)
    html = replace_once(
        html,
        r'''(<img\s+id=["']qr["'][^>]*\bsrc=["'])[^"']*(["'][^>]*\balt=["'])QR\s+Code\s+MK00000(["'])''',
        rf"\g<1>{qr_uri}\g<2>QR Code {code}\g<3>",
        "QR image",
    )

    # The template must not leak the Master code into generated identity fields.
    checks = (
        (r'''<title>[^<]*</title>''', "title"),
        (r'''<div\s+id=["']code["'][^>]*>[^<]*</div>''', "visible code"),
        (r'''const\s+COUPON_KEY\s*=\s*["'][^"']+["']''', "storage key"),
        (r'''<img\s+id=["']qr["'][^>]*\balt=["'][^"']*["']''', "QR alt"),
    )
    for pattern, label in checks:
        field = re.search(pattern, html, re.I | re.S)
        if field and "MK00000" in field.group(0):
            fail(f"Master code leaked into {label}")

    return html


def decode_qr_from_html(html: str, expected: str) -> None:
    match = re.search(
        r'''<img\s+id=["']qr["'][^>]*\bsrc=["'](data:image/png;base64,[^"']+)["']''',
        html,
        re.I | re.S,
    )
    if not match:
        fail("Generated page has no PNG data-URI QR")

    src = match.group(1)
    prefix = "data:image/png;base64,"
    if src.count(prefix) != 1:
        fail("Generated QR data URI is malformed or duplicated")

    try:
        raw = base64.b64decode(src[len(prefix):], validate=True)
    except Exception as exc:
        fail(f"Generated QR base64 is invalid: {exc}")

    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        fail("Generated QR PNG cannot be decoded as an image")

    detector = cv2.QRCodeDetector()
    decoded, _, _ = detector.detectAndDecode(image)
    if not decoded:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        decoded, _, _ = detector.detectAndDecode(gray)

    decoded = (decoded or "").strip()
    if decoded != expected:
        fail(f"Generated QR decodes to {decoded!r}, expected {expected!r}")


def generate(code: str, overwrite: bool = False) -> Path:
    if not CODE_RE.fullmatch(code):
        fail(f"Invalid coupon code: {code!r}; expected MK followed by 6 digits")
    if code == "MK00000":
        fail("MK00000 is the Master and cannot be generated as a coupon")

    if not MASTER.is_file():
        fail(f"Master file not found: {MASTER}")

    output_dir = ROOT / code / "(3)"
    output = output_dir / "index.html"
    if output.exists() and not overwrite:
        fail(f"Coupon already exists: {output}. Refusing to overwrite an existing coupon.")

    master_html = MASTER.read_text(encoding="utf-8")
    generated = transform_master(master_html, code)
    decode_qr_from_html(generated, code)

    output_dir.mkdir(parents=True, exist_ok=True)
    output.write_text(generated, encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("code", help="Coupon code, e.g. MK780944")
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Only for controlled maintenance; normal generation must not overwrite.",
    )
    args = parser.parse_args()

    try:
        output = generate(args.code.upper(), overwrite=args.overwrite)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(f"GENERATED {output.relative_to(ROOT)}")
    print("QR verified against the exact coupon code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
