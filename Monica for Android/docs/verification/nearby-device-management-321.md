# Nearby paired-device management and deletion consent — 1.0.321

Date: 2026-10-21. Applies to ordinary Android and F-Droid, unreleased 1.0.321.

## Behavior

- Connection diagnostics are centered within the status card, including pairing and Chinese dark mode at 1.5x font scale.
- Paired devices reuse the vault's swipe interaction: swipe left to remove, swipe right or long press to select. Selection supports multiple rows, select all, batch removal and Back to exit. Single and batch removal require confirmation; locally saved content is retained.
- Incoming changes are authenticated and previewed before applying deletions. The dialog identifies the sending device and deletion count, and offers applying deletions, receiving additions/updates while keeping local content, or cancelling the sync. The dialog is hosted at the unlocked app root so automatic sync can request consent outside the nearby page.
- Keeping local content records the skipped deletion versions locally. The same or older versions do not prompt again, including after reopening the replica. Newer deletion versions still require consent. This metadata is neither transmitted nor advertised as a content change.
- A decision is tied to a preview of the local replica. Local changes during the dialog invalidate that preview and require another review. Missing, stale, expired or cancelled consent cannot authorize deletion. Locking, backgrounding, changing the target database or forgetting the reviewing peer cancels the pending operation.
- Keeping content also repairs older pending tombstones against a complete physical snapshot, rather than acknowledging a live local item as deleted. Incoming additions and updates continue through the existing guarded import path. The wire protocol is unchanged.

## Verification

The machine-readable companion `nearby-device-management-321-results.json` records source parity, suite counts, environment restoration and final packaged APK hashes. Native code is built from source; the packaged test ABI library is compared byte-for-byte with its generated JNI artifact.

- Native replica tests: 18 pass, covering authenticated read-only previews, include/omit decisions, hard and soft deletions, additions/updates, legacy pending tombstones, retained-content forwarding, repeated skipped versions after reopen, newer deletion versions and tamper rejection.
- Each edition's JVM suites: 37 pass, zero failures/errors/skips. These cover manual sync and consent cancellation, vault import, Steam content, selected-database isolation and progress calculations. The selected-database test loads the real compatible MDBX host library.
- Each edition's packaged UI suites: 10 pass. They exercise long press and multiple selection, cancelling and accepting batch removal, left/right swipes, the three distinct incoming-deletion actions, diagnostics alignment, status sizing and existing manual controls.
- The existing public `Monica_Issue136_API_32` / `emulator-5554` is reused with isolated user 10. Installed APK hashes are checked. The prior Android user, Autofill, input method, accessibility, screen timeout and lockscreen setting are restored after testing. The previously running AVD remains running.

Editable local M3E Canvas project: `nearby-device-management-321-20261021`, with paired-list, selection, removal-confirmation and incoming-deletion states. Canvas drafts and native screenshots remain in ignored local design storage. Native renders are inspected separately from the Canvas prototype.

Validation covers native-host/loopback synchronization and packaged UI on the shared API 32 x86_64 emulator. It does not constitute two-physical-device Wi-Fi acceptance across different access-point or multicast configurations.
