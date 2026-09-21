# Swensen Standalone System

This directory is the **only generator system for Swensen's coupons**.

It is intentionally separate from the Teenoi generator:
- Master: `SwensenSystem/_MASTER/index.html`
- Generator: `SwensenSystem/generate_swensen.py`
- Validator: `SwensenSystem/validate_swensen.py`
- Request queue: `SwensenSystem/requests/`
- Automation: `.github/workflows/swensen-standalone.yml`

The generated coupon pages remain under `Swensen/CODE/(N)/index.html` so existing
GitHub Pages URLs stay unchanged.

Generation flow:
request → Master transform → fresh Code 128 → decode verification → full validation → commit.

No Teenoi generator, Master, storage key, or validation code is used by this subsystem.
