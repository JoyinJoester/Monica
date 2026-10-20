# Authenticator edit and database switching regression

## Reproduction and cause

Synthetic KeePass vault only: create two native authenticator entries, retain a legacy Room projection without `keepassEntryUuid`, edit its title/notes through `TotpViewModel.saveTotpAcrossTargets`, then switch database filters and reopen the vault.

Before the fix, the device test failed deterministically: native entry count was **3 instead of 2** immediately after editing. The current projection with a UUID passed the same scenario. The failure happens during persistence, before list aggregation: the mutation resolver generated a move UUID for an existing same-database legacy projection. The native writer found no entry with that UUID and appended a new one. Switching databases later exposed both originals and the new entry.

## Change

Resolve the native identity before changing fields when an existing same-database authenticator projection has no UUID. Use a unique Monica source ID first; without one, require a unique match on the original title, group path and parsed OTP identity. Exclude recycled native entries. Preserve existing UUID routing and stable UUID creation for new entries and cross-database moves.

Ambiguous or missing matches fail before either vault or projection writes and use a localized error. Equal secrets alone never identify an entry. Existing duplicate native entries are not automatically removed because they can represent intentional independent entries.

## Regression coverage

`TotpEditDatabaseIdentityDeviceTest` exercises the real Android repositories and encrypted KDBX persistence:

- Legacy projection with source ID: rename/update notes in place, retain UUID and Room ID, switch filters six times and reopen KDBX.
- Current UUID projection: edit in place while retaining another native entry with the same OTP secret.
- Legacy projection without source ID: resolve the original fields before applying a rename, then refresh/reopen.
- Two indistinguishable legacy candidates: reject with an error; leave both native entries and the local projection unchanged.
- Move a local authenticator to KeePass: still create exactly one native entry linked to the existing Room ID.

## Validation

Both ordinary Android and F-Droid editions passed `assembleDebug`, `assembleDebugAndroidTest`, **39 related unit tests** (KeePass OTP codecs, display sources, projection matching, cross-database identity and editor identity) and **5 device regression tests** each. The pre-fix run failed with 3 native entries instead of 2; the fixed run preserves the two independent UUIDs and original edited Room ID through six filter-switch/reopen cycles. Final device runs also restore the prior authenticator filter after every test.

The shared API 32 x86_64 AVD was reused with isolated Android user 10. Fixture cleanup removes only synthetic vaults and items; existing app data is retained. User selection, Autofill, keyboard/accessibility and wake settings are restored after each run. The emulator started for this task is stopped after validation. Version remains 1.0.321 unreleased, ordinary code 12 / F-Droid code 28. Bilingual release notes and local candidate notes include the fix; F-Droid fastlane 28 text stays within 500 characters per locale.
