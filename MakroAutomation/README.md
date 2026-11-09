# Makro PRO Bot (Android prototype)

This is an isolated Android project on the `makro-pro-bot` branch. It does not edit the existing coupon files or the default `main` branch.

## Flow taken from the supplied 92-second screen recording

1. Open the **installed Makro PRO standalone icon** (the web app installed separately from the website), not a normal Chrome tab.
2. Tap **เข้าสู่ระบบ**.
3. Enter the account phone/email in **เบอร์มือถือหรืออีเมล** and tap **เริ่มช้อปเลย**.
4. Enter the shared password and tap **เข้าสู่ระบบ**. The recording shows password login and a green **เข้าสู่ระบบสำเร็จ** banner; it does not show an OTP challenge being completed.
5. On the Makro PRO catalogue, use the **ค้นหาสินค้า** field; add the requested item(s) with the red **+** control and set quantity.
6. Open the cart icon, tap the blue delivery-address strip, and use the **เลือกพิกัดที่อยู่** map search.
7. Select the matching **The Kim** result and confirm the pin.
8. On **สร้างที่อยู่ / 2/2**, edit contact details and enter the requested phone; replace **ที่อยู่** with the requested address and tap **บันทึก**.
9. Return to the catalogue, open the account menu, and tap **ออกจากระบบ**.
10. Repeat with the next account.

The bot intentionally does not tap **เช็คเอาท์**, place an order, or pay.

## Standalone app targeting

- The Start button now searches Android's launcher list for the installed app whose name contains **Makro**, then launches that app's actual launcher activity. It does not open `https://account.makro.pro` in the default browser.
- If more than one matching icon exists, it asks which installed Makro icon to use.
- The Accessibility service checks the active window's package against the selected app package before it can type or tap. If the device switches to another app, the run stops instead of typing into the wrong window.
- Some installed web apps do not expose the HTML input hint to Android Accessibility. In that case, the bot can use an editable field only when it is the sole editable field on that screen. It does not guess between multiple unlabeled fields.

## First-run setup

- Install the APK on the Android phone.
- Open the app and enable its Accessibility service in Android Settings.
- Return to the bot. Enter up to 10 account IDs, one shared password, and up to three product codes with quantities.
- Press **เริ่มทำงาน**. The bot opens the installed Makro PRO icon directly.
- Keep the phone awake and Makro PRO in the foreground while the run is active.

## Credentials and privacy

The password is held only in runtime memory for the active run. It is not written to GitHub, a file, or persistent preferences; the password field is cleared from the app screen when starting. Do not put real account IDs or passwords into source files, issues, workflow inputs, or chat logs.

## Safety stops

The prototype stops rather than guessing when it cannot identify a required screen, product-add control, or exact `The Kim` search result. If Makro displays an OTP/CAPTCHA verification challenge, the bot stops for the account holder; it does not bypass that challenge. It does not attempt checkout/payment.

## Build

A GitHub Actions workflow builds a debug APK on pushes to this branch. The workflow caches its debug signing key so subsequent APKs are signed consistently while that cache remains available. The existing older APK was built before this fixed-key cache; install the new build cleanly once, then later builds can update it without another uninstall (provided the signing-key cache is retained).

## Prototype status / required device test

This remains an initial implementation and has not been verified on a live Makro account or on the target phone. Makro PRO's accessibility labels can change, and the app must be tested with one account and one product first. Product matching and map selection remain conservative; do not run all 10 accounts until the first-account test confirms the correct product and The Kim pin. No live order has been placed.
