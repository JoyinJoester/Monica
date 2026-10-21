# Local raw-information management and confirmed deletion

Date: 2026-10-21. Ordinary Android and F-Droid: 1.0.321 unreleased.

## Behavior

- The Monica local database menu now opens the shared raw-information manager.
- Single-item and multi-selection actions include Delete. A separate centered confirmation names the pane, database and selection count. Cancel does not write; confirmation buttons and dismissal are disabled during deletion. Failures remain visible.
- Local passwords and secure items go to Trash, with attachment metadata soft-deleted and encrypted attachment files retained. Selected folders and passkeys are permanently deleted. Private-key cleanup happens after the Room transaction commits.
- KeePass and MDBX native selections are permanently deleted in one backend mutation. Parent/child selections are normalized. The confirmation explicitly describes permanent deletion.
- Selection IDs, unlocked state and source revision are checked before writing and again at the atomic backend boundary. Local folders referenced by external database entries are rejected before any local write, including hidden or unavailable external mirrors.

## Validation

Both editions built successfully with `:app:assembleDebug :app:assembleDebugAndroidTest -PincludeX86TestAbi`.

Both editions passed 12 JVM tests, with zero failures, errors or skips:

- `DatabaseManagerSelectionTest`: 3.
- `MdbxBrowserIndexTest`: 4.
- `DatabaseManagerPaneStateTest`: 5.

Both editions passed the same 10 instrumented tests on the existing shared Android 32 x86_64 AVD `Monica_Issue136_API_32`, isolated user 10, without clearing app data:

- `DatabaseManagerDeletionTest`: 5 tests covering recoverable local contents and attachments, permanent passkey deletion, unrelated items, stale/missing/locked selections, external folder ownership, MDBX native persistence and KeePass UUID-based mixed selection persistence.
- `DatabaseManagerDeletionUiTest`: 2 tests covering actual local-manager cancellation and selected-only deletion, source/count display, error display and disabled busy controls.
- `DatabaseManagerPaneTest.actualScreenCopiesSelectionRightAndMovesOneItemLeftIntoFocusedFolders`.
- `DatabaseManagerTransferTest.twoNativeFoldersMoveCompletelyWithoutInvalidatingTheNextSelection`.
- `DatabaseManagerTransferTest.nativeFavoriteAndCreationDateTransferWithoutARoomProjection`.

Ordinary device result: `OK (10 tests)`, 55.551 seconds, density 420 and font scale 1.0. F-Droid device result: `OK (10 tests)`, 44.961 seconds, density 280 and font scale 1.3. Native confirmation/error rendering was inspected; the local confirmation screenshot also captured the dialog transition. Editable local M3E Canvas design and screenshots are retained locally under the ignored `docs/design/local-manager-delete-321` directory.

All 16 task source/test/resource files match between editions. Both `git diff --check` checks passed. The device harness verified installed APK hashes and restored input method, autofill, accessibility, screen timeout and active user. Density, font scale and lockscreen setting were restored afterward. No synthetic `credential-transfer-*` directories remained in either app's test-user files. The pre-existing public AVD remains running.

## Limits

Native backend tests use disposable local KeePass/MDBX fixtures. Live remote-provider upload completion is not part of this run; native mutations use the existing pending-change/upload mechanisms. No APK delivery, Release or tag was created.
