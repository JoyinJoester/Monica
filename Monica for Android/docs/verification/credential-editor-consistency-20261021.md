# Credential editor consistency — 2026-10-21

## Behavior and compatibility

- The default and added account credential forms share password actions: a quick visibility control and a three-dot menu containing generation and, when there is more than one password, deletion. Password additions animate the form height. Both username fields use the Username label and the same primary-colored AutoAwesome generator icon.
- A plain, always-visible multiline Note field sits below Add Password for each account credential group. Multiple passwords within that account share its note. Each account's note is independent of other accounts and of existing page-level notes and content blocks.
- Optional string `note` is stored in existing version-1 credential metadata, repeated on the group's password rows. Legacy metadata without the key reads as empty. Unknown keys and stable group/password identities remain intact; malformed or conflicting metadata is rejected instead of silently overwritten.
- The editor restores notes when opened from an additional account's row, and details display the note in the corresponding credential section. A single account with a note also uses the grouped save path; clearing that note retains valid metadata. Independent batch account creation attaches only its own note metadata.

## Verification

Edition: ordinary Android.

Debug app and instrumentation APK builds passed using `-PincludeX86TestAbi`. Unit suite `ProjectCredentialGroupTest`: 5 passed, zero failures/errors/skips. Tests cover independent multiline notes, password removal, clearing, old metadata, conflicting or malformed notes, identities and unknown-field preservation.

Device suite: 9 passed, zero failures on the shared `Monica_Issue136_API_32` AVD (Android 32, x86_64), isolated Android user 10, synthetic credentials only:

- `ProjectCredentialEditorTest`: 5 cases. Measures the default form at an intermediate animation frame and after completion; exercises the overflow menu, two accounts with multiple passwords and independent notes, reopening from the additional account, detail rendering, single-account note save/reopen/clear, and existing authenticator/detail regressions.
- `ProjectCredentialStorageTest`: 4 selected cases covering local encrypted rows, MDBX native storage, KeePass native storage and a fresh Bitwarden fixture download. Checks notes, edited notes, password removal/reordering, stable identity and unchanged page-level notes. The Bitwarden case uses a fixture server rather than an external account.

The driver restores the original active Android user, user-10 Autofill/accessibility/IME settings, and the stay-awake setting. The emulator was started for this task and is stopped after verification, retaining its reusable AVD data.

An early test measured the final inner height instead of the animated container; its semantics tag now wraps the animation. Another test restored database ciphertext directly and falsely treated randomized OTP ciphertexts as conflicting; it now decrypts secrets using the same contract as the actual editor. The final counts above refer to the corrected complete run.

Inspected the rendered local M3E Canvas and native editor/detail screenshots using synthetic data. Editable JSON, the full local share link and screenshot evidence remain local under ignored `docs/design/credential-editor-321/`. The isolated Canvas project is `credential-editor-321-20261021-final` at `http://127.0.0.1:5186/`. No design images or image links are committed.

## Release notes and synchronization

Recorded this change in the bilingual 1.0.321 unreleased notes, preserving earlier expiry-cursor, app-binding and autofill dialog fixes. Ordinary and active F-Droid implementations and tests are synchronized while retaining their existing edition differences. F-Droid fastlane 28 notes contain 308 Chinese and 475 English characters.

Both local integration candidates received updated release notes (and the F-Droid candidate's fastlane notes). Their older editor baseline lacks the grouped credential implementation; this task does not transplant that missing feature or claim candidate source/build validation. Version configurations remain 1.0.321; no Release, tag or APK delivery is created.
