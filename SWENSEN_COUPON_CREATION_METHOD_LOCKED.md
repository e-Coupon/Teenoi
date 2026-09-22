# SWENSEN COUPON CREATION METHOD — LOCKED

Status: PERMANENT / READ-ONLY
Purpose: Fixed creation procedure for every future Swensen e-Coupon.

## Canonical source

Always start from the read-only production reference:

Swensen/026C0D9C2D840/(1)/index.html

The reference supplies, unchanged:

- page structure
- header/coupon images
- CSS
- timer starting at 02:00:00
- first-page swipe/drag interaction
- PRESS USED control
- USED state
- JavaScript structure and behavior
- positions, sizing, spacing, controls, arrows, expiry presentation, and display geometry

## Fixed creation sequence

1. Read the canonical reference.
2. Copy its complete HTML structure as the basis for the new coupon.
3. Do not redesign, rebuild, simplify, resize, reposition, replace, or independently recreate unrelated parts.
4. Change only coupon-specific fields:
   - coupon code
   - barcode image and barcode alt text
   - document title code
   - per-coupon storage/state key
5. Generate a real Code 128-B barcode for the exact new coupon code.
6. Barcode requirements:
   - real PNG
   - Base64 data URI embedded inside the HTML
   - 594 x 120 px canvas
   - black bars on solid white
   - bars only, no human-readable text
   - centered, established wide geometry
7. Verify the actual barcode asset decodes to the exact coupon code, character-for-character.
8. Compare the completed HTML against the canonical reference after normalizing only the explicitly allowed coupon-specific fields.
9. Reject the coupon when any unrelated HTML/CSS/JavaScript/image/position/behavior changes.
10. Create the new coupon only at:
    Swensen/<CODE>/(1)/index.html
11. Never overwrite, rename, delete, or modify the canonical reference or an existing completed Swensen coupon.
12. After the GitHub commit, wait for the real GitHub Pages deployment for that commit.
13. Open the deployed production URL and verify the deployed page and barcode again.
14. Only after the deployed page passes the same checks may the production URL be reported as ready.

## Immutable rule

The method itself, the canonical reference, and the locked standard are read-only. Future ordinary coupon creation must create a new coupon only and must not modify any of them.

## Core rule

COPY THE REFERENCE.
CHANGE ONLY COUPON DATA.
VERIFY THE REAL BARCODE.
VERIFY THE DEPLOYED PAGE.
NEVER TOUCH UNRELATED PARTS.
