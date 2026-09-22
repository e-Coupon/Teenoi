# SWENSEN COUPON STANDARD — LOCKED

Status: PERMANENT / READ-ONLY STANDARD
Effective from: 2026-09-22
Reference build: Swensen coupon 02689B3BD7791
Reference implementation: `Swensen/026C0D9C2D840/(1)/index.html`

> This file is a locked source-of-truth record for future Swensen coupon creation.
> Do not edit, replace, rename, or delete this file as part of ordinary coupon creation.

## 1. Source template
- Use the production reference `Swensen/026C0D9C2D840/(1)/index.html) as the read-only visual/behavior reference.
- The reference coupon must never be modified to make another coupon.
- Preserve the existing page structure, images, CSS, JavaScript, dimensions, positions, controls, arrows, timer, swipe/drag interactions, USED state, and expiry presentation.
- Do not redesign, resize, reposition, simplify, or otherwise change unrelated UI.

## 2. New coupon creation
- Create a new code-specific directory under `Swensen/<CODE>/(1)/index.html`.
- Never overwrite, rename, delete, or alter an existing completed Swensen coupon.
- Change only the coupon-specific data: coupon code, barcode asset, document title/alt text, and per-coupon storage key/state identifiers.
- Keep each coupon's local storage/state key unique to that coupon code.

## 3. Swensen barcode standard
- Symbology: Code 128-B.
- Output: a real PNG embedded in HTML as a Base64 data URI.
- PNG canvas: 594 x 120 px.
- Background: solid white.
- Bars: solid black.
- Bars only; no human-readable text under the barcode.
- Use proper Code 128-B start, data, checksum, and stop patterns.
- Center the encoded pattern horizontally with balanced quiet zones.
- Use the established wide barcode geometry; do not produce a narrow decorative approximation.
- Preserve the reference page display geometry: `#barcode` remains width 90%, height 140 px, centered.

## 4. Barcode verification gate
For every new Swensen coupon:
1. Generate the barcode from the exact intended coupon code.
2. Inspect the actual PNG asset.
3. Decode/scan that actual PNG.
4. Require the decoded value to equal the intended code exactly, character-for-character.
5. Reject and regenerate any mismatch.
6. Only embed the barcode after exact decoding succeeds.
7. After GitHub Pages deployment, verify the production page again and confirm the deployed barcode decodes to the same exact code.

A screenshot, decorative image, SVG substitute, manually drawn look-alike, or typed text is never a valid replacement for the real barcode asset.

## 5. Completion gate
A Swensen coupon is complete only when:
- the new production file exists in its own code path;
- the code-specific barcode has passed exact decode verification;
- the GitHub commit succeeds;
- GitHub Pages deployment reports success;
- the deployed production URL is opened and checked;
- the deployed UI and required interactions are checked;
- no existing coupon or reference file was modified or deleted.

If any required verification cannot be performed, do not report the coupon as fully verified.

## 6. Immutable reference
The following production reference is read-only and must remain unchanged:
`Swensen/026C0D9C2D840/(1)/index.html`

The following locked standard is also read-only:
`Swensen/SWENSEN_STANDARD_LOCKED.md`

Core rule:
**CREATE NEW. PRESERVE EXISTING. VERIFY THE REAL BARCODE. VERIFY THE REAL DEPLOYMENT.**
