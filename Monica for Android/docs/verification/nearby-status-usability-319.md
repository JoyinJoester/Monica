# Nearby sync status and diagnostics usability

Date: 2026-10-12. Ordinary 1.0.319/versionCode 12 and F-Droid 1.0.319/versionCode 26 remain unreleased.

## Problem and change

Progress, failure messages and cancel controls were conditionally inserted into the scrolling column, moving paired-device cards as preparation started and stopped. A persistent status panel now reserves the same text, progress and action space across idle, preparation, transfer, saving, failure, completion and pairing. The primary button switches between Sync now and Cancel this sync in place. Text slots use fixed heights derived from the active typography and font scale; full guidance remains available in diagnostics. An initial minLines-only implementation still shifted cards by 2dp under Chinese 1.5x fallback-font metrics, which the device test caught. The fixed slots address that real failure without loosening the 0.5dp position tolerance.

The panel describes the actual phase, including encrypted transfer and saving, instead of labeling byte transfer as preparation. Pausing clears stale progress. Manual Passkey confirmation renews the connection window, so time spent reading a transfer warning does not consume the subsequent connection window.

Diagnostics are visible without an error. They include the selected database and direction, automatic/manual mode, default network type, paired/connected/listening counts, address attempts, last discovery result, latest connection start, latest successful sync and retry delay. Errors show a concise code and operation plus stage-specific recovery guidance. A detected Wi-Fi network is explicitly not evidence that a peer is reachable. The connection-error text no longer claims every address was tried when that has not happened.

Copy diagnostics is near the top of the sheet. It excludes vault contents, passkey record titles, pairing invitations/codes/keys, peer names and network addresses. A known failing record title can still be shown locally when available. Network observation uses Android framework APIs and unregisters its callback when the sheet closes; no new dependency or service is added to F-Droid.

## Validation

- Nearby host unit/integration suite: 58 tests, zero failures/errors/skips. Real JNI/Room coordinator checks verify send-only to receive-only manual transfer, subsequent edits staying local with automatic sync off, and those edits arriving without another request after automatic sync is enabled. Live discovery/connection diagnostic fields are asserted from the actual coordinator. The absent-peer deadline and in-progress edit gate regressions remain passing.
- Ordinary controls/navigation: 3 tests passed in 100.604 seconds. Covered Local/MDBX selection, manual request and cancellation with automatic sync off, scanner toolbar/system Back and packaged-native QR generation/copy/cancel.
- Ordinary send-only to receive-only Steam device transfer: passed in 17.633 seconds. Verifies fields, folders and encrypted destination persistence.
- Initial ordinary status/diagnostics checks: 3 tests passed in 91.84 seconds. Measured paired-device top position and status height through eight state changes, including 1.5x font size; also opened the real diagnostics sheet and tested the actual clipboard in healthy and failed states.
- Final ordinary bilingual status/diagnostics checks: 3 tests passed in 55.65 seconds. F-Droid status/diagnostics and manual controls: 4 tests passed in 45.84 seconds. Chinese at 1.5x font scale retains the same status height and device-card position through the state transitions, with a 0.5dp tolerance.
- Final Chinese 1.5x font capture run: 1 test passed in 56.896 seconds. Compose images of transfer and failure were inspected: the title, guidance, byte counts, progress and diagnostics action render legibly within the reserved panel. UiAutomation had returned blank full-window images for this locale fixture, so the test-only capture helper now captures the actual composed status pixels; those blank images were not accepted as visual evidence.
- Ordinary/F-Droid cross-application Steam transfer over emulator loopback: both passed (45.745 seconds server, 9.365 seconds client). Each application uses its own process, UID, Android Keystore and synthetic Room stores; the test checks transferred fields, folders and encrypted destination persistence. Host timing recorded server instrumentation launch at 11:07:42.662, invitation readiness at 11:08:04.641 and immediate client launch at 11:08:04.645 on 2026-10-12.
- An earlier cross-application run failed: server timed out after 105.304 seconds and the client reported connection_failed after 7.761 seconds. Log timing placed the client test after the server wait ended. The rerun above ran after builds finished, with launch/readiness timestamps recorded. No production or test timeout was increased; the first failure is retained and the cause of its startup delay is not proven.
- Debug/test packaging passed for both variants, including the existing x86_64 test option. Final ordinary release packaging passed in 4 minutes 43 seconds; existing R8/Kotlin metadata warnings remain. The later test-only screenshot helper does not change production sources.

## Evidence and limits

The existing public API 32/x86_64 AVD was reused without wiping it or creating another AVD. Test data is synthetic. Screenshots and editable local M3E Canvas documents are retained only under ignored `docs/design/nearby-status-319/`; raw user images/logs, design files and APKs are not committed or delivered.

Host/emulator transport exercises real native libraries, encryption and persistence. It does not establish behavior on two physical Android 36 phones, Wi-Fi multicast across a router, client isolation, or vendor background restrictions. Diagnostic network type is descriptive and does not gate transfer or claim reachability.
