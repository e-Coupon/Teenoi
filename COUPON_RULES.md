# Coupon Creation Rules — Source of Truth

These rules are mandatory for future coupon creation work.

## 1. Master and preservation
- Use only the designated Master as the source template.
- For Teenoi: use Master `MK00000/(3)` unless the user explicitly designates another Master.
- Never modify the Master unless the user explicitly requests a Master change.
- Create new coupons by adding new files/folders; do not modify, overwrite, rename, or delete completed coupons.
- Never delete or alter existing coupon files as part of creating a new coupon.

## 2. Exact duplication of behavior
- Preserve the Master’s UI, layout, CSS, JavaScript, buttons, positions, sizing, arrows, timer/drag/swipe behavior, and other established behavior.
- Change only the data required for the new coupon, such as its code, QR/barcode, and per-code storage/key values.
- Do not independently redesign, “improve,” resize, reposition, or otherwise change unrelated elements.
- If an apparent problem in the Master or an existing coupon would require unrelated changes, stop and ask before changing it.

## 3. QR / barcode correctness
- Generate a real QR/barcode for the exact coupon code.
- Verify by decoding/scanning the generated code that it resolves to the exact intended coupon code.
- For Swensen, when the requirement is CODE 128: use a real Code 128 encoding, with no human-readable text under the barcode, and place it as specified by the established template.
- Do not substitute a decorative image or a manually drawn look-alike and call it a valid barcode.

## 4. GitHub implementation
- Modify/create the actual production file in the GitHub repository, not merely an image, screenshot, or mockup.
- Keep each coupon in its own code-specific path according to the established repository structure.
- Commit the change without disturbing existing coupons.

## 5. Deployment and verification gate
A coupon is NOT complete merely because the GitHub commit succeeded.
The required completion gates are:
1. File created/updated correctly.
2. Code/QR/barcode verified against the intended code.
3. Commit completed.
4. GitHub Pages deployment verified as successful.
5. The deployed production URL is opened and checked.
6. The deployed coupon’s visible UI and required behavior are checked.
7. Existing coupons/files are confirmed not to have been deleted or unintentionally modified.

Only when every gate passes may the work be reported as complete or the deployed link be presented as ready for use.

## 6. No guessing
- If any required verification cannot be performed, do not claim success.
- Do not treat assumptions, source-code inspection alone, or a successful commit as proof of deployment or runtime correctness.
- If a required step is blocked or ambiguous, stop at that point and report the specific blocker.

## 7. Communication while working
- Do the work first; do not provide repeated progress narration or step-by-step chatter.
- Report the result after the work is actually complete.
- If a blocking problem prevents completion, report only the relevant blocker rather than pretending the job is finished.

## Core rule
**Create new; do not damage existing.**
**Complete = all verification gates passed in the real deployed page.**


## Swensen barcode generation specification
- Output the Swensen Code 128 as a real **PNG** embedded in the coupon HTML. Do not use SVG for the barcode.
- PNG canvas: **594×120 px**, matching the established Swensen coupon barcode asset size.
- Barcode content must be **bars only**: no human-readable text beneath the bars.
- Encode the exact coupon code as **Code 128-B**, including the proper start, checksum, and stop patterns.
- Scale the encoded bar/space pattern to occupy the established wide barcode area (approximately **500 px of the 594 px canvas**) while keeping it horizontally centered; do not use a fixed 2-px-per-module scale that leaves the bars unnecessarily narrow.
- Keep the barcode black on white, vertically centered in the 120 px canvas, with balanced left/right quiet zones.
- Verify the generated PNG by decoding/scanning it and require an exact match to the intended coupon code before the coupon can be reported complete.
