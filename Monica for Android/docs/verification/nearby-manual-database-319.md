# Nearby reference resolution, manual sync and database selection

Date: 2026-10-12. Ordinary 1.0.319/versionCode 12 and F-Droid 1.0.319/versionCode 26 remain unreleased.

## Report and reproduction

The reported build `9134c4caba` failed before attempting an address: `preparing / invalid_request / replica_resolve`, in a send-only to receive-only setup on Android 36. The old reference walker recursively interpreted fields named `path/id`, `parent`, `target` and bound-entry fields even inside opaque extension objects. Values such as `service/account:42` are ordinary extension data but are not valid replica identifiers.

A real Room/provider/Rust regression test failed before the fix with `invalid_request` from `normalizeNativeReferences` calling `replica_resolve`. With the fix, it exports, imports and verifies the original extension values and password. Reference processing now visits only protocol-owned slots and submits only valid replica identifiers. Opaque and legacy external values remain unchanged; the native request validator is not weakened. Existing valid folder and entry-link tests still pass.

This reproduces the reported operation and error class. The supplied report does not identify the user's offending record, and the user's database was not available for replay.

## Behavior

- Automatic sync and manual runs are independent. Turning automatic sync off leaves Sync now available. A manual run does not enable automatic sync and can be cancelled. Unconnected peers expire after a two-minute connection window; connected transfers and pending application of received edits are allowed to finish. Both devices must request a run when both have automatic sync off. Pairing preserves the automatic-sync preference.
- Sync database defaults to Local. A supported MDBX database may be selected explicitly; sending and incoming writes use that selection. An unavailable selection fails visibly instead of falling back to another store.
- Each selected store has its own replica file and logical writer. Switching does not scan omitted stores as deletions. The old combined replica is retained, and other database contents are not removed. The wire protocol and cryptography are unchanged.
- Selection and automatic-sync preferences persist in protected settings. The cached selection follows protected preference changes without decrypting unchanged settings on every progress tick.
- Native exceptions have a stable diagnostic kind instead of an R8-obfuscated class name. No arbitrary credential-bearing exception messages are added to diagnostics.
- The native UI uses the existing grouped settings and bottom-sheet components, with settings and Sync now placed above pairing actions. The editable local M3E Canvas document and its URL are preserved under ignored `docs/design/nearby-manual-319/`.

## Validation

- Ordinary nearby unit/integration suite: 58 tests, zero failures, errors or skips. The extension regression was first observed failing. Its initial post-fix assertion was corrected to compare JSON field values rather than object key order.
- The coordinator test runs send-only and receive-only coordinators over real host JNI transport with automatic sync disabled. Nothing starts before the manual request, the requested transfer completes, preferences remain disabled, and later source edits are not transmitted automatically. Expiring the connection deadline while received data waits for an open editor does not cancel it; a separate absent-peer test expires the request and releases the transport.
- The database test uses real Room and MDBX. Local export ignores an unreadable unrelated default MDBX vault; incoming data reaches only the selected MDBX store. Room data and another MDBX store remain untouched. Switching away and back preserves the replica revision, and an unavailable selection does not fall back.
- Ordinary and F-Droid debug/test APK builds passed. Ordinary release compilation, R8 and packaging passed; pre-existing Kotlin-metadata compatibility warnings remain.
- Reused the already-running public API 32/x86_64 emulator without wiping or replacing it.
- Ordinary device checks passed: the manual button works with automatic sync off, the database picker starts at Local and can select MDBX and return to Local, cancellation preserves the disabled preference, send-only to receive-only Steam transfer preserves encrypted destination storage, scanner Back returns correctly, and QR generation/cancellation works.
- F-Droid device controls passed. Its send-only to receive-only Steam test passed separately in 6.128 seconds. Running that protocol test immediately after a test Activity closed first encountered the app's session lock; the isolated run kept production lock behavior unchanged.
- Current ordinary and F-Droid builds also passed encrypted Steam transfer between separate app processes and UIDs on the emulator (server 33.614 seconds, client 6.369 seconds). Both directions preserve folders, encrypted destination storage, unknown maFile fields and independent records with the same Steam ID.
- The first ordinary controls run failed because the optional screenshot helper returned no initial frame. The test-only helper now permits capture of its synthetic Activity and retries frame acquisition; the controls rerun passed in 37.996 seconds. Actual ordinary main/picker rendering and F-Droid main rendering were inspected locally.
- All 16 changed source/test/resource files match between variants. Both bilingual unreleased notes and F-Droid fastlane `26.txt` are updated; the English and Chinese fastlane texts remain within 500 characters. Published 1.0.318 notes and `25.txt` are unchanged.

## Limits and retained artifacts

Transport checks use loopback on the host or emulator, including actual packaged native libraries and Android Keystore for device tests. They do not establish two physical Android 36 devices, Wi-Fi multicast discovery, router isolation or vivo background behavior. Test stores and credentials are synthetic. Raw user logs, screenshots, Canvas files, pairing invitations and APKs are not committed or delivered.
