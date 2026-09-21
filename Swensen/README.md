# Swensen's coupon system

Swensen coupon pages are generated from **Swensen/_MASTER/index.html**.

To create a coupon, place a request file at:

`Swensen/coupon-requests/CODE.request`

The file content must be exactly the same code. The GitHub Action then:
1. generates the page from Master;
2. creates a fresh Code 128 barcode for the exact code;
3. decodes the generated barcode before commit;
4. assigns the next available sequence number `(N)`;
5. validates all Swensen pages; and
6. commits the result automatically.

Existing Swensen pages are kept unchanged. The generated URL pattern is:

`https://e-coupon.github.io/Teenoi/Swensen/CODE/(N)/`
