#!/usr/bin/env python3
"""Validate all generated Swensen's coupons."""

from __future__ import annotations

import base64
import io
import re
import sys
from pathlib import Path

from PIL import Image
from pyzbar.pyzbar import decode as zbar_decode


ROOT = Path(__file__).resolve().parents[1]
CODE_RE = re.compile(r"^[A-Z0-9]{6,32}$")
QR_RE = re.compile(
    r'<img\s+id=["\']barcode["\'][^>]*\bsrc=["\']([^"\']+)["\']',
    re.I | re.S,
)
CODE_HTML_RE = re.compile(r'<div\s+id=["\']code["\']>([^<]+)</div>', re.I)
ALT_RE = re.compile(r'<img\s+id=["\']barcode["\'][^>]*\balt=["\']([^"\']*)["\']', re.I)
KEY_RE = re.compile(r'const\s+COUPON_KEY\s*=\s*["\']([^"\']+)["\']')
TITLE_RE = re.compile(r"<title>([^<]+)</title>", re.I)


def fail(path: Path, message: str) -> None:
    raise RuntimeError(f"{path}: {message}")


def decode_barcode(path: Path, src: str, expected: str) -> None:
    if not src.startswith("data:image/png;base64,"):
        fail(path, "barcode must be embedded as a PNG data URI")
    payload = src.split(",", 1)[1]
    try:
        raw = base64.b64decode(payload, validate=True)
    except Exception as exc:
        fail(path, f"barcode base64 is invalid: {exc}")
    try:
        with Image.open(io.BytesIO(raw)) as img:
            if img.format != "PNG":
                fail(path, "barcode image is not PNG")
            assert_bars_only(path, img)
            decoded = [item.data.decode("utf-8", "strict").strip() for item in zbar_decode(img)]
    except Exception as exc:
        fail(path, f"barcode image could not be decoded: {exc}")
    if expected not in decoded:
        fail(path, f"barcode decodes to {decoded!r}, expected {expected!r}")


def assert_bars_only(path: Path, image: Image.Image) -> None:
    """Reject Code 128 images that contain human-readable text under the bars."""
    gray = image.convert("L")
    width, height = gray.size
    dense_rows = []
    row_dark = []
    for y in range(height):
        dark = sum(1 for x in range(width) if gray.getpixel((x, y)) < 128)
        row_dark.append(dark)
        if dark >= max(8, int(width * 0.18)):
            dense_rows.append(y)

    if not dense_rows:
        fail(path, "barcode has no dense bar region")

    top = min(dense_rows)
    bottom = max(dense_rows)

    # Any substantial dark content outside the dense bar band is treated as
    # human-readable text or another unwanted graphic element.
    outside_limit = max(2, int(width * 0.01))
    for y in range(0, max(0, top - 2)):
        if row_dark[y] > outside_limit:
            fail(path, "barcode contains extra graphics/text above bars")
    for y in range(min(height, bottom + 3), height):
        if row_dark[y] > outside_limit:
            fail(path, "barcode contains human-readable text/graphics below bars")


def validate_page(path: Path) -> None:
    rel = path.relative_to(ROOT)
    parts = rel.parts
    if len(parts) != 4 or parts[0] != "Swensen" or not CODE_RE.fullmatch(parts[1]):
        fail(path, "invalid Swensen coupon path")
    code = parts[1]
    if parts[2] == "_MASTER":
        return

    html = path.read_text(encoding="utf-8")
    code_match = CODE_HTML_RE.search(html)
    if not code_match or code_match.group(1).strip() != code:
        fail(path, "visible code does not match directory code")

    title_match = TITLE_RE.search(html)
    if not title_match or code not in title_match.group(1):
        fail(path, "title does not contain coupon code")

    key_match = KEY_RE.search(html)
    if not key_match or code.lower() not in key_match.group(1).lower():
        fail(path, "COUPON_KEY does not contain coupon code")
    if "swensen_" + code.lower() not in key_match.group(1).lower():
        fail(path, "COUPON_KEY format is invalid")

    qr_match = QR_RE.search(html)
    if not qr_match:
        fail(path, "missing #barcode")
    decode_barcode(path, qr_match.group(1).strip(), code)

    alt_match = ALT_RE.search(html)
    if not alt_match or code not in alt_match.group(1):
        fail(path, "barcode alt text does not identify coupon code")

    barcode_css = re.search(r"#barcode\s*\{([^}]*)\}", html, re.I | re.S)
    if not barcode_css:
        fail(path, "missing #barcode CSS")
    css = barcode_css.group(1)
    if "object-fit:contain" not in css.replace(" ", "") and "object-fit: contain" not in css:
        fail(path, "#barcode must preserve its image aspect ratio with object-fit:contain")

    for element_id in ("page1", "page2", "arrow", "page2wrap", "barcode", "code", "timer", "usedButton"):
        if f'id="{element_id}"' not in html:
            fail(path, f"missing required element #{element_id}")
    for marker in ("pointerdown", "pointermove", "pointerup", "PRESS USED"):
        if marker not in html:
            fail(path, f"missing required interaction marker: {marker}")


def main() -> int:
    pages = sorted(
        p for p in (ROOT / "Swensen").glob("*/*/index.html")
        if len(p.relative_to(ROOT).parts) == 4 and CODE_RE.fullmatch(p.relative_to(ROOT).parts[1])
    )
    failures = []
    for page in pages:
        try:
            validate_page(page)
            print(f"PASS {page.relative_to(ROOT)}")
        except Exception as exc:
            failures.append(str(exc))
            print(f"FAIL {exc}")
    print(f"Validated: {len(pages)} page(s), failures: {len(failures)}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
