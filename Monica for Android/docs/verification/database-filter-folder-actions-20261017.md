# Database filter folder actions

## Report and cause

In the database filter menu, entering edit mode and tapping a folder only opened actions for Monica local categories. Password quick folders resolved only `CategoryFilter.Custom`; the unified filter menu also accepted only a local category callback. KeePass groups, MDBX folders and Bitwarden folders therefore never reached the action dialog.

Six device regression cases reproduced the missing dialog before the fix: all three external sources in the password quick-folder component and in the unified menu used by authenticator, wallet, notes and Passkeys.

## Change

- Local and external folders reuse the same action dialog. Existing local move/copy actions remain available.
- External rename/delete operations retain the selected database and native folder identity. KeePass resolves UUIDs, rejects ambiguous legacy paths and uses a current native revision token. MDBX and Bitwarden verify folder membership in the selected database.
- Writes use the existing database repositories and their locking/read-only rules. Deletion requires confirmation; Bitwarden explains that its items are retained. Failure retains the dialog and typed name for retry.
- Successful changes refresh the owning folder cache. The unified menu now requests MDBX folders when its selected database changes; missing MDBX bindings in consumer pages are connected.
- Deleting the currently selected Bitwarden folder returns the filter to its owning vault.
- Normal folder navigation and the parent/back item remain separate from edit actions.

## Verification

| Check | Result |
| --- | --- |
| Before-fix device reproduction | 6 failures, all missing the folder action dialog |
| Menu/dialog device regression tests | 9 passed on the final application sources |
| Real KeePass/MDBX repository and Bitwarden ownership guard tests | 3 passed |
| Ordinary Debug / AndroidTest build | Passed (`includeX86TestAbi`, `dailySeq=03`); 1m 34s |
| Ordinary Release build | Passed; 7m 27s, ARM 32/64 builds |
| F-Droid Release / AndroidTest build | Passed; 4m 2s |

The complete suite passed twice: 12 tests in 63.685s before the final selected-folder reset, then 12 tests in 66.46s with the final application sources. An earlier cold run after installation hit an input-focus ANR while builds were running; it is not counted as a pass. A warm single-test check and both complete reruns passed. No application fatal exception was found in the captured diagnostics.

Device suite:

```text
adb -s emulator-5554 shell am instrument --user 10 -w -r -e class takagi.ru.monica.ui.FilterFolderActionsTest,takagi.ru.monica.repository.ExternalFolderRepositoryTest takagi.ru.monica.test/androidx.test.runner.AndroidJUnitRunner
```

The repository fixtures use isolated in-memory Room databases and synthetic native vault files. They check duplicate KeePass names by UUID, read-only rejection, stale paths after rename, MDBX database isolation and root protection, plus rejecting an absent Bitwarden folder before making an account request. No production Bitwarden account is used; authenticated folder HTTP operations reuse the existing repository implementation.

The ordinary Release build completed with R8 Kotlin-metadata compatibility warnings. Runtime regression tests used the x86_64 Debug APK on API 32; the ARM Release APKs were built, not installed on physical devices.

The editable M3E Canvas source and render checks are retained locally under `docs/design/filter-folder-actions-321/` with project key `monica-filter-folder-actions-321`. Design files, screenshots and APKs are excluded from Git.

The actual native dialog was visually checked on the synthetic MDBX menu fixture: title, folder name, rename/delete buttons and cancel action rendered without clipping. The optional test screenshot is written to app cache, since Android restricts reading another user's external app directory. After this test-only output-path change, ordinary and F-Droid AndroidTest builds passed again (59s / 29s), and the dialog capture test passed in 11.593s.

## Version and cleanup

Both distributions remain unreleased 1.0.321 (ordinary versionCode 12; F-Droid 28). Bilingual release notes, F-Droid fastlane 28 and the local candidate notes are updated. No Release or tag is created.

Public AVD: `Monica_Issue136_API_32`, API 32, test user 10. Test-owned native vault files are cleaned by the fixtures. The internal dialog screenshot and shell capture were removed. User 0 was restored and the emulator started by this task was stopped; its configuration and data disk are retained. Authentication and autofill settings were not changed. The private browser used to inspect the Canvas draft was closed.
