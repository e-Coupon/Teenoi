# Swensen coupon requests

The generator watches this folder for files named `CODE.request`.

Put the exact same code in the file body. Example:

`Swensen/coupon-requests/026C0D9C2D840.request`

The automation consumes the request once, generates a coupon from **Swensen/_MASTER/index.html**, verifies the barcode, validates the page, and commits the result.
