# Bitwarden team vault sync — issue #168

Issue: https://github.com/Monica-Pass/Monica/issues/168

## Behavior

The sync profile previously discarded its organizations, and cipher processing always used the personal account key. Organization ciphers therefore failed to decrypt. Sync now unwraps the authenticated account private key, resolves each organization key, and then resolves the optional per-item key before using the existing login, note, card, identity, SSH, Passkey and attachment paths.

Team data appears in the connected Bitwarden account through the existing item pages. This change does not add organization administration, collection creation or sharing controls. Existing organization and collection membership is preserved on update. Items whose server policy hides password visibility are not imported as readable secrets; read-only items can be synced but cannot be uploaded as edits.

## Safety checks

- Organization keys are scoped to one authenticated account response and wiped after use. Decrypted organization/private key material is not added to persistent storage.
- RSA-OAEP SHA-256 and legacy SHA-1 organization envelopes are supported. Private and per-item symmetric key envelopes require authenticated encryption.
- Missing organization keys, malformed item keys and failed key authentication never fall back to the personal account key.
- An unreadable organization does not prevent readable personal/other organization data from syncing. Affected existing entries are retained and an incomplete-sync error is shown instead of reporting success.
- Updates require a current remote source. Team edit permission must be explicitly granted. Organization ID, collection IDs, wrapped item key and revision token are preserved; server rejections leave local edits pending.
- Passkey updates continue patching the original encrypted document, preserving sibling credentials, login data and unknown fields, and verifying the response by reading it back.
- Attachment metadata and payload-key resolution use the same account/organization/item hierarchy.
- No TLS verification changes, new dependencies, cloud relays or plaintext key logging were introduced.

## Verification

| Check | Result |
| --- | --- |
| Android `BitwardenOrganizationKeysTest` | 8 passed |
| Android `BitwardenAttachmentKeyResolverTest` | 7 passed |
| Android Debug and AndroidTest assembly | Passed |
| F-Droid equivalent unit suites | 15 passed, 0 failures/errors |
| F-Droid Release and AndroidTest assembly | Passed; Release rechecked after updating build detail tag to 28 |
| Android Release assembly | Passed |
| Public API 32 emulator: organization sync + Passkey update tests | 14 passed, 0 failed; 11.786 seconds |

The unit tests independently construct encrypted envelopes and cover both response casings, both RSA modes, organization/item key selection, wrong or missing keys, disposal, malformed organizations, ownership preservation, edit-policy checks and authenticated profile retrieval.

The device tests use the real Retrofit and Room paths against a local MockWebServer. Synthetic fixtures include a personal vault, two organizations, RSA-SHA-1 and RSA-SHA-256 organization keys, an independent item key, a password, read-only login, secure note, card, identity, SSH entry and encrypted attachment metadata. Assertions cover first/repeated sync, updates readable with the correct key, collection retention, 403/409 failures, missing-key recovery, no duplicate records, and removing a no-longer-returned shared entry. Eleven Passkey scenarios include organization and independent-item-key updates, sibling preservation, folder moves, concurrent local edits, conflicts, lost responses and server-side data loss detection.

No real Bitwarden organization credentials were supplied, so no live production organization was tested. F-Droid device behavior is covered by identical shared source/tests and its own builds/JVM suites; the emulator run used the ordinary Debug APK.

## Version and cleanup

Both distributions enter unreleased **1.0.321** by user request. Ordinary versionCode remains **12**; F-Droid increments to **28**. The previous 1.0.320 release notes are archived verbatim, fastlane 27 remains unchanged, and bilingual fastlane 28 contains only the new team-vault change. The local integration candidate version/notes are also updated.

The existing public AVD was initially stopped, so it was started for this task. Tests used user 10 and synthetic records; fixture records were removed, the current user was restored to 0, and the AVD was stopped without deleting its configuration/data disk. No screenshots, design files or APKs are included in the commit. No GitHub Release or tag is created.
