# Swensen e-Coupon

Master: `Swensen/_MASTER/index.html`

Generation flow:
- Copy the Master.
- Replace the top/visible coupon code.
- Generate a new bars-only Code 128 for the exact same code.
- Replace the barcode and its alt text.
- Set a unique `COUPON_KEY` containing the lowercase code.
- Decode-test the barcode and require an exact match.
- Deploy as `Swensen/CODE/(N)/index.html`.

Old Swensen generator/validator/request-queue systems are removed. Existing coupon URLs are preserved.
