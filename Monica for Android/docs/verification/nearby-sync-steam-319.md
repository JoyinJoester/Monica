# Nearby sync, Steam and F-Droid verification (1.0.319)

Date: 2026-10-11. Development builds remain unreleased: ordinary versionCode 12, F-Droid versionCode 26. No release tag was created.

## Changes

- Nearby and scanner use separate navigation destinations with the shared settings transitions. Both scanner Back paths return to Nearby. Scanner overlays avoid a second entrance animation when the destination already provides one.
- M3E settings groups and bottom sheets replace the previous form. QR generation exposes a copyable pairing link. The editable local Canvas document and actual device captures stay in ignored `docs/design/nearby-sync-319/`.
- An immediate pairing failure previously stopped a pending foreground-service start before Android received `startForeground`, matching the reported foreground deadline crash. Pending starts now promote first; `onStartCommand` then handles cancellation. Already-running services still stop immediately.
- Ordinary Gradle packaging now builds the JNI bridge from current source. This prevents stale local JNI libraries from omitting nearby-sync entry points. F-Droid retains its existing source build, extended to track the LAN crate.
- The independent Steam Room database participates in encrypted sync through the existing `steam-mafile` wire type. Tokens, session fields, unknown maFile fields and folder assignments survive transfer; destination fields and supplemental metadata are encrypted. Independent copies of the same Steam ID retain separate identities. Current-account selection is local state.

## Completed checks

- Ordinary `testDebugUnitTest --tests takagi.ru.monica.lansync.*`: 50 tests, zero failures/errors/skips. Includes six Steam regression cases and two foreground-service cancellation cases.
- Ordinary debug APK and Android test APK built. Ordinary release build, including R8, completed. Both ARM release APKs contain `Java_takagi_ru_monica_lansync_RustLanSync_nativeRequest` in the generated library.
- Ordinary `lintDebug` passed with zero unbaselined errors; the project still reports 1,357 warnings and its existing baseline filters 104 errors and 847 warnings. Existing R8 Kotlin-metadata compatibility warnings remain.
- F-Droid debug APKs and Android test APK built with the vendored MDBX source and current Rust JNI source for ARM64, ARMv7 and x86_64.
- Reused the existing `Monica_Issue136_API_32` emulator (API 32, x86_64). It was already running and was not replaced or erased.
- Ordinary device tests: 4 passed. Covered scanner toolbar/system Back, generated QR and copied invitation, three start/cancel cycles followed by the foreground deadline, encrypted Steam persistence, and receive-only exclusion of outgoing vault artifacts/content keys.
- F-Droid device tests: 3 passed. Covered both scanner Back paths, QR/link generation and cancellation, and receive-only transport behavior. Actual main/QR/scanner rendering was inspected locally.
- F-Droid QR/link/cancellation was also rerun at font scale 1.5: 1 test passed (55.435 s). The optional screenshot helper retries after display configuration changes; the first capture-only attempt encountered an unavailable screenshot. Main and QR rendering at the enlarged scale was inspected locally.
- Cross-application test: ordinary and F-Droid ran in separate application processes/UIDs and exchanged two synthetic Steam accounts over encrypted TCP. Both sides passed verification of complete token/session data, unknown fields, folder assignment, independent same-ID records, encrypted destination fields and ciphertext artifacts. Acknowledgement followed destination validation and persistence.
- The first cross-app harness attempt ended before the cold-started sender exposed its invitation; after extending the harness startup wait and completing the F-Droid standalone run, both sides passed (ordinary 34.716 s, F-Droid 7.296 s).

## F-Droid assessment

The feature uses FLOSS Rust components and Android LAN APIs. It adds no Google Nearby SDK, Play Services dependency or hosted relay; transferring Steam fixtures does not contact Valve. The Rust JNI dependency graph contains 98 packages, all with license metadata (MIT/Apache/BSD/Unicode/Unlicense alternatives, with the JNI crate GPL-3.0-only). Existing F-Droid exclusions, including OneDrive/MSAL, remain intact.

This is consistent with the published [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/) and [anti-feature guidance](https://f-droid.org/docs/Anti-Features/). Official acceptance, signing and distribution remain F-Droid's decision. Published 1.0.318 notes, tag and fastlane `25.txt` are unchanged; new notes use 1.0.319 and `26.txt`.

## Coverage limits

The transfer tests used loopback networking between isolated stores or separate apps on one emulator. They exercise packaged native transport and real Android persistence, but do not establish physical Wi-Fi multicast discovery, access-point isolation behavior, OEM background limits or vivo/API 36 behavior. No real user credentials were transferred. Device screenshots, raw user logs and pairing invitations are not committed. The public emulator and its data remain available for reuse.
