# Nearby sync: trashed items, Passkey counters and merge receipts

## Reproduction and correction

The reported 1.0.319 build `3d9c086e94` failed on the receiver with
`applying / invalid_password_payload`; the sender then reported
`confirming / connection_closed`.

A synthetic Room/JNI regression with 610 passwords and six new trashed secure
items reproduced the same exception before the fix. New trashed notes, TOTP,
cards, documents, billing addresses and payment accounts fell through to the
password importer because dispatch only tested `!deleted`. Dispatch now also
recognizes content-bearing trash (`trashed=true`). Repeating the import checks
that entries are not duplicated and trash remains in trash.

The coordinator previously acknowledged an unsuccessful merge and immediately
cancelled its worker. It now lets the authenticated rejection complete, bounded
to ten seconds, while preserving the original receiver error. Lock and user
cancellation still stop transfers immediately. A real coordinator/Room/native
test injects a database write rejection: neither side records success, the
sender receives `peer_merge_failed`, and a retry succeeds after the fault is
removed. Validation diagnostics identify schema fields, never their values.

## Passkey policy

The shared preflight dialog offers “Skip and continue”. Every nonzero-counter
Passkey is excluded from the encrypted outgoing wire copy, including cached and
conflict copies with the same RP/credential identity. The local scan remains
complete, so exclusion does not manufacture a tombstone. Excluded-only assets
are omitted too. Review tracks this policy independently of other portability
warnings, including device-only and backup-restricted credentials.

Tests cover first export, a previously synced credential subsequently used,
existing destination copies, a fresh destination, replica reopening, consent,
other entries continuing to sync, and unchanged source keys/counters. Incoming
metadata cannot decrease an existing local counter.

## Verification

- Rust sync suite: 36 passed, including receipt, authentication, direction,
  cancellation and cached-copy exclusion coverage.
- Ordinary and F-Droid host sync suites: 62 passed per edition, zero failures or skipped tests; real Room
  and host JNI, with a deterministic Android security substitute.
- Both editions built debug APKs for arm64, ARMv7 and the x86_64 test ABI, plus
  their instrumentation APKs, from the current Kotlin and Rust sources.
- Device suites: 7 passed per edition. Each includes a complete two-round bulk
  transfer with all six attachments, bidirectional and send-only/receive-only
  Steam transfer, 1.5x text Passkey review, stable status layout and diagnostics.
- Direct ordinary/F-Droid cross-application pairing: one server test and one
  client test passed, verifying real independent Android storage encryption.

The expanded Android fixture seeds 610 password records (including SSH, Wi-Fi,
API-key, SSO, barcode and archived records), six live secure-item kinds, six
trashed secure items, folders, Steam accounts and zero/nonzero-counter Passkeys.
It also creates protected/plain custom fields, password history and a linked
note. Six attachments cover a 4 MiB + 17 byte binary, a PNG, an empty text file,
two identically named files with different contents, and a trash attachment.
It verifies every seeded schema field, folder/link mapping, history, archive
state, attachment MIME/size/SHA-256 and decrypted bytes, then repeats the sync.

This broader fixture uncovered another real failure: the native asset appender
used the non-empty protocol-string helper for Base64 data. Empty attachments
therefore failed with `invalid_request` during preparation. A native regression
failed before accepting empty string data and passed after the correction;
empty attachments still have an authenticated encrypted final chunk and reject
an incorrect key.

The initial bulk device test exceeded its 90-second harness deadline. The bulk
fixture now allows 600 seconds and records only phase/count progress, matching
the existing native merge window. Production timeouts were not changed.
The final bulk runs reached `completed` on both sides on both rounds. All 626
incoming objects were applied on the first round; repeated-sync assertions
confirmed unchanged item/attachment counts, field values and decrypted bytes.

The public `Monica_Issue136_API_32` AVD is reused. Device tests use synthetic
isolated databases, real Android Keystore and encrypted TCP over loopback.
They do not replace a two-physical-device Wi-Fi test on Android 36/37. No user
vaults, credentials, screenshots, design files or raw user logs are committed.
Both editions remain unreleased 1.0.319 (ordinary code 12, F-Droid code 26).
