# Swensen e-Coupon

Master: `Swensen/Master/index.html`

Generation flow:
- Copy the Master.
- Replace the top/visible coupon code.
- Generate a new bars-only Code 128 for the exact same code.
- Replace the barcode and its alt text.
- Set a unique `COUPON_KEY` containing the lowercase code.
- Decode-test the barcode and require an exact match.
- Deploy as `Swensen/CODE/(N)/index.html`.

Old Swensen generator/validator/request-queue systems are removed. Existing coupon URLs are preserved.

- Barcode asset method is fixed: generate a real **PNG 594×120 px** for Code 128-B, bars only, black on white, horizontally centered, with the bars scaled to the established wide area (approximately 500 px of the canvas). Do not generate the Swensen barcode as SVG or leave it narrow because of a fixed module scale.
