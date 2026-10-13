# Authenticator import naming — issue 164

Issue: https://github.com/Monica-Pass/Monica/issues/164

New authenticator names use the trimmed issuer, falling back to the account and
then the original label. QR imports, pasted/external links, migration selection
and saves, and generated authenticator titles in file imports share this rule.
Named editor drafts remain unchanged. Existing records are not bulk-renamed.
Original URI labels, issuer/account fields and OTP parameters remain intact.

Both Android editions remain at 1.0.320 (ordinary versionCode 12; F-Droid 27).
Bilingual release notes and F-Droid fastlane 27 include the fix.

Validation on 2026-10-14:

- Each edition passed 27 host tests without failures, errors or skips: import
  title, migration URI parser/planner, stored OTP resolver, external intent
  controller and portable backup codec suites.
- Each edition built app and instrumentation APKs with `-PincludeX86TestAbi`.
- Each edition passed 5 tests on the shared API 32 x86_64 AVD: ordinary 15.792s,
  F-Droid 9.907s. Real Compose editors exercise scanned and pasted URIs, preserve
  an explicit user title, keep account/secret/HOTP algorithm/digits/counter,
  and verify the saved data still produces an account-preserving URI payload.
  A real ViewModel batch save writes two accounts with one issuer as separate
  database rows. Issuer-less URI fallback and label-derived issuers are checked.
- Issuers containing colons are kept whole. Raw migration labels are unchanged.
  No existing authenticator is renamed, and deduplication still uses OTP identity.
- `git diff --check` and fastlane length limits pass. Tests use disposable
  synthetic records. The public emulator started for this task was stopped.
