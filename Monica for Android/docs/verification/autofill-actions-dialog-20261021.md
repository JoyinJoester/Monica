# Autofill account action dialog — 2026-10-21

## Scope

- Replaced both suggested and ordinary password action menus with one centered native Compose Dialog, following the editable local M3E Canvas design. Fill, copy, smart-copy and details actions use grouped rows; the fixed header retains its close button while long content scrolls, including at 200% font scale.
- Added an explicit Accessibility fill action to the dialog and inline password details. It uses the existing account/secret resolution and target-package policy independently of framework Autofill IDs and manual-mode routing. Disabled service presents an explanation and Android accessibility settings entry.
- Explicit Accessibility fill does not silently copy credentials on failure. Existing manual-entry fallback behavior is preserved.
- Framework authentication finishes within the caller's task. Manual/keyboard entry moves its separate picker task behind the target app. An initial device regression caught the caller's task being sent to the launcher; the final implementation fixes this and passes the same scenario.

## Verification

Environment: shared Monica_Issue136_API_32 AVD, Android 32 x86_64, isolated Android user 10. Only synthetic credentials were used. The test driver restores the original active user, secure Autofill/accessibility/IME settings and stay-awake setting.

Ordinary edition: debug app and instrumentation APK builds passed with `-PincludeX86TestAbi`. No version changes, release or tags were created.

Unit regressions: 54 passed, zero skipped/failures/errors:

- ManualFillTargetPolicyTest: 2.
- AccessibilityCredentialFillTest: 52, including Robolectric API 29 and 34 coverage of field selection and target restrictions.

Device regressions: 10 passed, zero failures:

- PasswordActionDialogTest: 6; all eight actions dispatch and dismiss correctly, suggested entries expose Accessibility fill, smart-copy respects notification permission, close performs no action, secrets are absent, 200% text can scroll to actions and the close control stays visible, and the native dialog has side margins.
- AutofillFlowInstrumentedTest: system Autofill picker can choose Accessibility fill and return to the actual target fields; keyboard launch contract can independently choose Accessibility fill; inline details can Accessibility fill; ordinary framework Autofill still fills the native login form.

The keyboard case uses the same intent arguments and flags as the keyboard, rather than tapping a physical keyboard button. New picker cases complete Monica's actual master-password verification. Fixture assertions check both username and password values in the target application; they do not merely assert that a callback fired.

Inspected the rendered local Canvas and captured native Compose dialog using synthetic example data. Design JSON, local share link and PNG evidence remain in ignored `docs/design/autofill-actions-321/` and are not committed. The dialog is centered, with a maximum width of 400dp, 24dp outer padding, 28dp outer corners and scrollable grouped actions.

## Release notes and synchronization

Recorded the change in the bilingual 1.0.321 unreleased notes, preserving today's earlier bound-account matching and expiry-cursor fixes. Synchronized the shared changes into the active F-Droid worktree and both local integration candidates using contextual edits to retain edition/candidate differences. F-Droid fastlane 28 notes remain within the 500-character limit.

The local candidates received source, tests and notes synchronization; the build and device checks above apply to the ordinary edition. The F-Droid copy of this report records its own build/device verification.
