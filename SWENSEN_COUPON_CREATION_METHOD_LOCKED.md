# SWENSEN COUPON CREATION METHOD — LOCKED

Status: PERMANENT / READ-ONLY
Purpose: Fixed creation procedure for every future Swensen e-Coupon.

## Canonical source

Always start from the read-only production reference:

Swensen/026C0D9C2D840/(1)/index.html

For every new coupon:
- copy the canonical reference
- create only a new path: Swensen/<CODE>/(1)/index.html
- change only coupon-specific data, especially the coupon code and barcode
- do not redesign or alter unrelated UI, CSS, JavaScript, images, positions, or behavior
- never overwrite an existing completed coupon or the canonical reference

## Barcode standard

Every coupon barcode must be:
- Code 128-B
- a real PNG
- embedded as a Base64 data URI
- 594 x 120 px
- black bars on white
- bars only, without human-readable text

## Verification gate

To keep coupon creation fast, automated verification is reduced to one functional check:

**SCAN THE ACTUAL BARCODE AND REQUIRE THE DECODED VALUE TO MATCH THE COUPON CODE EXACTLY, CHARACTER FOR CHARACTER.**

A barcode that cannot be decoded, decodes to a different value, or is not Code 128 fails verification.

## Core rule

COPY THE REFERENCE.
CHANGE ONLY COUPON DATA.
SCAN THE ACTUAL BARCODE.
MATCH THE EXACT COUPON CODE.
NEVER TOUCH UNRELATED PARTS.
