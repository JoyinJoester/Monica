# Nearby sync preparation regression verification (1.0.319)

Date: 2026-10-12. Ordinary versionCode 12 and F-Droid versionCode 26 remain unreleased.

## Evidence and reproduced failures

The reported ordinary build was based on `73f5828636`. Its diagnostics showed successful QR decoding and the start of pairing, followed by repeated preparation failures with `IllegalArgumentException`, zero transfer attempts and no operation label. The supplied diagnostics did not include an exception stack, so they cannot establish which source record failed on the user's device.

Two synthetic reproductions reached a matching preparation exception in the real Room/Rust provider before the fix:

- A local Steam account used a shared local category that also had a Bitwarden folder binding. Folder identity preparation excluded the category while Steam export attempted to use it.
- A local Passkey retained an MDBX-owned mirror category reference. Local export attempted to read an unprepared local folder identity.

Both regression tests failed before the implementation change with `IllegalArgumentException` from the missing folder identity. They pass after the fix. This demonstrates two concrete causes of the reported failure class; it does not prove they were the only possible cause in the user's database.

## Change

Folder identity preparation and enumeration now use the same local-category classification as the rest of the app. A local folder can receive its identity on demand when created after the initial scan. Shared category names and assignments survive transfer without copying Bitwarden account or folder bindings. A local Passkey's stale MDBX category is omitted from the exported local record without changing its source category reference.

Preparation boundaries record an operation label and exception class. They preserve cancellation and existing native diagnostics without retaining arbitrary exception messages, causes or credential content. The sync wire format and transfer cryptography are unchanged.

## Completed checks

- Ordinary `:app:testDebugUnitTest --tests takagi.ru.monica.lansync.*`: 54 tests across 12 suites, zero failures, errors or skips. Includes the two red-to-green provider regressions and safe-diagnostic/cancellation coverage.
- Ordinary debug, Android test and release builds passed. Release completed R8 and packaging; existing Kotlin-metadata compatibility warnings remain.
- F-Droid debug and Android test builds passed. The existing `includeX86TestAbi` option supplied an x86_64 test APK. All nine changed source/test files match the ordinary implementation.
- Reused the already-running public `Monica_Issue136_API_32` emulator (API 32, x86_64), without wiping it or stopping it.
- Ordinary `encryptedSteamRoundTrip`: one instrumentation test passed in 36.676 seconds using a shared category with a synthetic Bitwarden binding, actual Android Keystore, packaged JNI, encrypted TCP and destination persistence.
- Ordinary/F-Droid `crossApplicationSteamPairing`: both independent app processes passed (ordinary 36.218 seconds, F-Droid 4.407 seconds). Both sent and received synthetic Steam accounts with shared folders. Assertions covered token/session fields, unknown maFile fields, folder assignment, independent same-Steam-ID records, encrypted destination fields and ciphertext artifacts; acknowledgement followed persistence validation.
- Both 1.0.319 bilingual unreleased notes were updated. F-Droid fastlane `26.txt` remains within 500 characters (English 492, Chinese 232). Published 1.0.318 notes and `25.txt` were not modified.

## Coverage limits

Transport validation used loopback between separate applications on one emulator. It does not verify physical Wi-Fi discovery, router isolation, vivo background behavior or API 36 behavior. The supplied user's database was not available to replay. New operation diagnostics will help identify another failing preparation path if one remains.

No real credentials were used in the test fixtures. User logs, screenshots, pairing invitations, generated APKs and design artifacts are not included in the commit.
