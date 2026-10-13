# Bitwarden Authenticator CSV import — 1.0.320

Separate source: **Import Data → Files → CSV → Bitwarden Auth.** (F-Droid opens file import directly.) The existing Bitwarden CSV entry remains for the password manager.

The standalone importer accepts the supplied Android export layout: six named columns (`folder,favorite,type,name,login_uri,login_totp`) with three extra values per data row. The OTP URI supplies identity and parameters; unnamed trailing values never become passwords. Standard six-column rows, reordered headers, UTF-8 BOM, quoted commas/quotes and multiline quoted notes are supported. Password-manager headers are rejected by this importer.

Items are standalone authenticators. The default title uses the issuer, while account, algorithm, digits, period and HOTP counter remain in their own fields. The existing destination writer handles encryption, duplicate detection, background execution and imported/skipped/failed counts. Invalid records are counted without including source rows or OTP secrets in errors; malformed files fail before writing.

## Verification

- Both ordinary and F-Droid editions: 20 host tests passed per edition (10 new parser tests, 4 naming tests, 6 existing CSV format tests).
- Both ordinary and F-Droid editions: debug app and instrumentation APK builds passed.
- Public Android 32 x86_64 AVD: all 3 device tests passed per edition. Two synthetic authenticators were saved into each of local, KeePass, MDBX and a mock Bitwarden vault. A repeated import skipped both records. Invalid-row counts, account separation, nondefault parameters and the 64-bit HOTP counter were verified. No password entries were created.
- The F-Droid UI test was adapted to its existing direct file-import screen; both variants passed their full flow.
- The actual user-supplied CSV parsed as 2 valid authenticators, with 0 rejected rows. It was used only locally and removed from emulator storage after testing.
- The real Compose screen showed separate Bitwarden CSV and Bitwarden Auth. entries. Selecting the latter dispatched the authenticator importer, retained the chosen KeePass destination and displayed its result.
- Local M3E Canvas rendered and supported editing/export. Native UI rendering was inspected. Canvas documents and screenshots remain ignored local artifacts; no private exports or design screenshots are included in Git.

The Bitwarden destination test uses a local mock server; it does not claim live account or server interoperability beyond the existing destination writer. No new library, network service or proprietary dependency was added. Development versions remain 1.0.320 (ordinary code 12, F-Droid code 27).
