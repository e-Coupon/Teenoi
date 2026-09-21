#!/usr/bin/env python3
"""Validate Teenoi coupon pages before they are considered usable."""

from __future__ import annotations

import base64
import re
import sys
from pathlib import Path
from urllib.request import urlopen

import cv2
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
COUPON_RE = re.compile(r"^MK\d+$")
QR_RE = re.compile(r'<img\s+id=["\']qr["\'][^>]*\bsrc=["\']([^"\']+)["\']', re.I)
CODE_RE = re.compile(r'<div\s+id=["\']code["\']>([^<]+)</div>', re.I)
ALT_RE = re.compile(r'<img\s+id=["\']qr["\'][^>]*\balt=["\']([^"\']*)["\']', re.I)
KEY_RE = re.compile(r"const\s+COUPON_KEY\s*=\s*['\"]([^'\"]+)['\"]")
TITLE_RE = re.compile(r"<title>([^<]+)</title>", re.I)


def fail(path: Path, message: str) -> None:
    raise RuntimeError(f"{path}: {message}")


def load_qr_bytes(html_path: Path, src: str) -> bytes:
    if src.startswith("data:image/png;base64,"):
        payload = src[len("data:image/png;base64,"):]
        if payload.startswith("data:image/png;base64,"):
            fail(html_path, "QR data URI prefix is duplicated")
        try:
            return base64.b64decode(payload, validate=True)
        except Exception as exc:
            fail(html_path, f"QR base64 is invalid: {exc}")

    if src.startswith("data:image/"):
        fail(html_path, "QR must be a valid PNG data URI or a valid image URL")

    if src.startswith("https://") or src.startswith("http://"):
        try:
            with urlopen(src, timeout=15) as response:
                return response.read()
        except Exception as exc:
            fail(html_path, f"QR image URL cannot be fetched: {exc}")

    candidate = (html_path.parent / src).resolve()
    try:
        candidate.relative_to(ROOT.resolve())
    except ValueError:
        fail(html_path, "QR local path escapes repository root")
    if not candidate.is_file():
        fail(html_path, f"QR image file not found: {src}")
    return candidate.read_bytes()


def decode_qr(image_bytes: bytes, html_path: Path) -> str:
    data = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if image is None:
        fail(html_path, "QR image bytes cannot be decoded as an image")

    detector = cv2.QRCodeDetector()
    decoded, points, _ = detector.detectAndDecode(image)
    if not decoded:
        # Try a grayscale pass as a second independent decode attempt.
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        decoded, points, _ = detector.detectAndDecode(gray)

    if not decoded:
        fail(html_path, "QR image exists but could not be decoded")
    return decoded.strip()


def validate_page(path: Path) -> None:
    parts = path.relative_to(ROOT).parts
    expected = parts[0]
    if not COUPON_RE.fullmatch(expected):
        fail(path, "invalid coupon directory name")

    html = path.read_text(encoding="utf-8")

    code_match = CODE_RE.search(html)
    if not code_match:
        fail(path, "missing #code")
    visible_code = code_match.group(1).strip()
    if visible_code != expected:
        fail(path, f"visible code is {visible_code!r}, expected {expected!r}")

    title_match = TITLE_RE.search(html)
    if not title_match or expected not in title_match.group(1):
        fail(path, "title does not contain the coupon code")

    key_match = KEY_RE.search(html)
    if not key_match:
        fail(path, "missing COUPON_KEY")
    coupon_key = key_match.group(1)
    if expected.lower() not in coupon_key.lower():
        fail(path, f"COUPON_KEY does not contain {expected}")
    if expected != "MK00000" and "mk00000" in coupon_key.lower():
        fail(path, "non-master coupon still uses MK00000 storage key")

    qr_match = QR_RE.search(html)
    if not qr_match:
        fail(path, "missing #qr src")
    src = qr_match.group(1).strip()
    if not src:
        fail(path, "empty #qr src")
    if src.count("data:image/png;base64,") > 1:
        fail(path, "QR data URI prefix appears more than once")

    alt_match = ALT_RE.search(html)
    if not alt_match or expected not in alt_match.group(1):
        fail(path, "QR alt text does not identify the coupon code")

    decoded = decode_qr(load_qr_bytes(path, src), path)
    if decoded != expected:
        fail(path, f"QR decodes to {decoded!r}, expected {expected!r}")

    required_ids = (
        "page1", "page2", "arrow", "page2wrap", "qr", "code",
        "timer-group", "timer", "usedMask", "usedButton",
    )
    for element_id in required_ids:
        if f'id="{element_id}"' not in html:
            fail(path, f"missing required element #{element_id}")

    for marker in ("pointerdown", "pointermove", "pointerup", "PRESS USED"):
        if marker not in html:
            fail(path, f"missing required interaction marker: {marker}")


def main() -> int:
    pages = sorted(
        p
        for p in ROOT.glob("MK*/(3)/index.html")
        if COUPON_RE.fullmatch(p.parts[-3])
    )
    if not pages:
        print("No Teenoi /(3)/ coupon pages found.")
        return 0

    failures: list[str] = []
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
