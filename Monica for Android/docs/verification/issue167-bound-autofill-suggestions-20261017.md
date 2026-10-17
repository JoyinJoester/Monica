# Issue 167: saved bindings missing from autofill suggestions

Scope: existing app-package and website bindings should appear in the current target's recommendations. This change does not broaden recognized field types or redesign the picker.

## Findings

- A nested WebView scope discarded an origin reported on its ancestor when the nested node omitted it. The new parser-to-matcher regression failed on both simulated API 29 and 34: `accounts.example.com` became `null`, losing the bound website recommendation.
- Keyboard and active-fill notification entry points do not have framework-calculated suggested IDs. The picker previously loaded only those IDs, so a manually opened picker never calculated recommendations from its target, even when bindings existed.
- The matcher metadata cache already invalidates changed bindings. The existing cache tests and new add/remove binding tests cover this path.

## Changes

- Nested scopes inherit their ancestor's reported origin when absent, while retaining separate field scopes. Explicit child origins override it; unrelated sibling WebViews do not supply an origin.
- Manually opened pickers resolve the target package and any recent browser context, then reuse the existing matcher and shared source/domain settings. Framework-provided IDs, including an explicitly empty result, remain authoritative for that request's field scope.
- Recommendations use current active repository entries and preserve supplied ID ordering. Unknown browser origins do not fall back to shared browser-package credentials. Verification and secret access are unchanged.

## Validation

- JVM regression: 118 tests passed, with zero failures or skips. This includes 96 parser cases across API 29/34, binding matching and cache updates, and six picker suggestion cases covering manual targets, explicit empty framework results, current bindings, source filters and domain settings.
- Before replacing the installed app, both new picker UI tests failed because no recommendation appeared. The old app's diagnostics confirmed an authenticated picker with three loaded entries but `suggestedLoaded=0` for both the bound native package and the bound website.
- Ordinary Debug/test APK build passed in 7m. F-Droid Release/test APK builds passed in 4m 10s.
- Fixed-app emulator run: both new recommendation UI tests, native/WebView actual filling, two-step login, credential refresh and the packaged Rust index passed. The multiple-form fixture initially failed because it labelled the login username `section-login` but its password `section-email`; the parser correctly kept these sections separate. The fixture now uses `section-login` for the login pair and keeps the newsletter in its separate form/section. This is a test-data correction; production section isolation is unchanged. The focused rerun passed in 11.182s, confirming username/password filled and newsletter stayed empty. All nine selected device cases therefore passed across the initial eight successes and the corrected fixture rerun.
- Ordinary Release build passed in 4m 42s, including the existing R8 Kotlin metadata warnings. The test-only HTML correction does not change the app APK. Both test APKs were rebuilt for that fixture correction.

Only synthetic test credentials are used in shared AVD `Monica_Issue136_API_32`, isolated user 10. Device tests temporarily configure autofill and restore its prior value and the screen wake setting. Screenshots and APKs stay outside the committed files. These tests do not claim reproduction on the reporter's exact apps or manufacturer build.

Cleanup verified: user 10's autofill provider restored to Google, global stay-awake restored to 0, switched to user 0 and stopped the emulator started for this task. The public AVD configuration and data disk were retained.

The fix and release notes are mirrored to F-Droid under unreleased 1.0.321 / versionCode 28. No version bump, release or tag is created.
