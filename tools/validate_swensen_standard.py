#!/usr/bin/env python3
"""Enforce the locked Swensen coupon format for newly added production pages."""

from __future__ import annotations

import base64
import re
import sys
from pathlib import Path

import cv2
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "Swensen" / "026C0D9C2D840" / "(1)" / "index.html"
CODE_RE = re.compile(r"^[0-9A-Z]+$")
BARCODE_TAG_RE = re.compile(r'<img\s+id=["\']barcode["\'][^>]*>', re.I | re.S)
CODE_TAG_RE = re.compile(r'(<div\s+id=["\']code["\']\s*>)[^<]*(</div>)', re.I | re.S)
TITLE_RE = re.compile(r"(<title>[^<]*?—\s*)[^<]+(\s*</title>)", re.I | re.S)
KEY_RE = re.compile(r"const\s+COUPON_KEY\s*=\s*['\"]swensen_[^'\"]+_used_v1['\"]", re.I)
BARCODE_SRC_RE = re.compile(r'\bsrc=["\']([^"\']+)["\']', re.I)
BARCODE_ALT_RE = re.compile(r'\balt=["\']([^"\']*)["\']', re.I)


def fail(path: Path, message: str) -> None:
    raise RuntimeError(f"{path}: {message}")


def extract_barcode_src(path: Path, html: str) -> str:
    match = BARCODE_TAG_RE.search(html)
    if not match:
        fail(path, "missing #barcode image")
    tag = match.group(0)
    src = BARCODE_SRC_RE.search(tag)
    if not src:
        fail(path, "#barcode has no src")
    value = src.group(1).strip()
    prefix = "data:image/png;base64,"
    if not value.startswith(prefix):
        fail(path, "barcode must be an embedded PNG Base64 data URI; external URLs are forbidden")
    if value.count(prefix) != 1:
        fail(path, "barcode data URI prefix is duplicated")
    return value


def decode_barcode(path: Path, payload: bytes, expected: str) -> None:
    if payload[:8] != b"\x89PNG\r\n\x1a\n":
        fail(path, "barcode payload is not a PNG file")

    image = cv2.imdecode(np.frombuffer(payload, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        fail(path, "barcode PNG cannot be decoded as an image")

    height, width = image.shape[:2]
    if (width, height) != (594, 120):
        fail(path, f"barcode image size is {width}x{height}; required 594x120")

    detector = cv2.barcode.BarcodeDetector()
    decoded: list[str] = []
    decoded_types: list[str] = []

    try:
        result = detector.detectAndDecode(image)
        if len(result) == 4:
            ok, values, types, _ = result
            if ok and values:
                if isinstance(values, str):
                    decoded.append(values)
                else:
                    decoded.extend(v for v in values if v)
                if types:
                    decoded_types.extend(t for t in types if t)
    except Exception:
        pass

    if not decoded:
        try:
            result = detector.detectAndDecodeMulti(image)
            if len(result) >= 2:
                ok, values = result[0], result[1]
                if ok and values:
                    decoded.extend(v for v in values if v)
                if len(result) >= 3 and result[2]:
                    decoded_types.extend(t for t in result[2] if t)
        except Exception:
            pass

    decoded = [value.strip() for value in decoded if value and value.strip()]
    if expected not in decoded:
        fail(path, f"barcode does not decode to the exact coupon code {expected!r}; decoded={decoded!r}")

    if decoded_types:
        normalized_types = {re.sub(r"[^A-Z0-9]", "", t.upper()) for t in decoded_types}
        if not any("CODE128" in t for t in normalized_types):
            fail(path, f"barcode decoder did not identify Code 128; types={sorted(normalized_types)!r}")


def canonicalize(html: str) -> str:
    text = TITLE_RE.sub(r"\g<1>__SWENSEN_CODE__\g<2>", html, count=1)
    text = CODE_TAG_RE.sub(r"\g<1>__SWENSEN_CODE__\g<2>", text, count=1)

    def barcode_repl(match: re.Match[str]) -> str:
        tag = match.group(0)
        tag = BARCODE_SRC_RE.sub('src="__SWENSEN_BARCODE__"', tag, count=1)
        tag = BARCODE_ALT_RE.sub('alt="Barcode __SWENSEN_CODE__"', tag, count=1)
        return tag

    text = BARCODE_TAG_RE.sub(barcode_repl, text, count=1)
    text = KEY_RE.sub("const COUPON_KEY='swensen___SWENSEN_CODE___used_v1'", text, count=1)
    return text


def validate_page(path: Path) -> None:
    try:
        rel = path.relative_to(ROOT)
    except ValueError:
        fail(path, "path is outside repository root")

    parts = rel.parts
    if len(parts) != 4 or parts[0] != "Swensen" or parts[2] != "(1)" or parts[3] != "index.html":
        fail(path, "new Swensen production pages must be exactly Swensen/<CODE>/(1)/index.html")

    code = parts[1].upper()
    if not CODE_RE.fullmatch(code):
        fail(path, "invalid coupon code directory")

    html = path.read_text(encoding="utf-8")

    code_match = re.search(r'<div\s+id=["\']code["\']\s*>([^<]*)</div>', html, re.I)
    if not code_match or code_match.group(1).strip() != code:
        fail(path, "visible coupon code does not exactly match the directory code")

    title_match = re.search(r"<title>([^<]*)</title>", html, re.I)
    if not title_match or code not in title_match.group(1):
        fail(path, "title does not contain the exact coupon code")

    key_match = KEY_RE.search(html)
    if not key_match:
        fail(path, "missing Swensen COUPON_KEY ending in _used_v1")
    if code.lower() not in key_match.group(0).lower():
        fail(path, "COUPON_KEY does not contain the exact lowercase coupon code")

    barcode_src = extract_barcode_src(path, html)
    try:
        payload = base64.b64decode(barcode_src.split(",", 1)[1], validate=True)
    except Exception as exc:
        fail(path, f"barcode Base64 is invalid: {exc}")

    decode_barcode(path, payload, code)

    if not REFERENCE.is_file():
        fail(path, f"immutable reference is missing: {REFERENCE}")

    reference_html = REFERENCE.read_text(encoding="utf-8")
    if canonicalize(html) != canonicalize(reference_html):
        fail(
            path,
            "page structure/UI/behavior differs from the immutable reference after allowed "
            "coupon-specific fields are normalized",
        )

    print(f"PASS {rel}: locked format, immutable structure, 594x120 PNG, exact Code 128 decode")


def main() -> int:
    paths = [Path(arg).resolve() for arg in sys.argv[1:]]
    if not paths:
        print("No new Swensen coupon pages to validate.")
        return 0

    failures = 0
    for path in paths:
        try:
            validate_page(path)
        except Exception as exc:
            failures += 1
            print(f"FAIL {exc}")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
