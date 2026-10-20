# Credential detail cards — 2026-10-21

## Changes

- Each credential note is the last connected segment of its account/password/authenticator group. On details it follows the authenticator code with the standard 2dp group gap; the authenticator keeps small bottom corners and the note supplies the rounded group bottom. Empty and whitespace-only notes do not render. Multiline notes remain selectable and copyable. Existing page-level notes stay separate.
- Both primary and added credential editors place the simple multiline note immediately after the verification-key input, with 4dp adjoining corners, a 24dp rounded bottom and a 2dp gap. The code preview and Add Password action follow the connected fields. Blank editor notes remain editable.
- Password strength and the delete icon share one horizontal footer. The separate text Delete button is removed. The icon retains its 48dp touch target and localized accessibility description, including when strength analysis is disabled. Existing deletion eligibility and confirmation callbacks remain in place.
- Ordinary and active F-Droid source, strings and device tests are synchronized. The editable local M3E Canvas lives in ignored `docs/design/credential-detail-321/`, with project identifier `credential-detail-321-connected-v3-20261021` at `http://127.0.0.1:5186/`. Its JSON and full local share link are saved to disk; the actual single-page preview was visually checked before native implementation.

## Validation

Editions: ordinary Android and active F-Droid (each built and tested separately).

Debug app and instrumentation APK builds: passed. Device suite `ProjectCredentialEditorTest`: 7 passed in each edition, zero failures. Shared `Monica_Issue136_API_32` AVD, Android 32 x86_64, isolated Android user 10 and synthetic credentials.

The seven cases cover the existing editor animation, note persistence and clearing, editing multiple credentials, details and authenticator behavior, plus two added detail checks:

- Delete icon and strength badge have the same vertical center, the icon has a 48dp touch target and a Delete accessibility description, no text Delete button remains, and the deletion callback still works with strength analysis disabled.
- At 200% text size, a multiline note renders for its own credential while a whitespace-only note does not create a card. Legacy credentials without a note also have no note card. The save/edit/detail flow checks note ownership and its proximity to the authenticator. Editor geometry checks verify the 2dp key-to-note gap for primary and added credentials, with the preview and Add Password below the note.

An initial emulator process crashed in the Windows host's `combase.dll` before the device suite completed. The same shared AVD was restarted using software rendering. An initial completed run caught the delete control's visual bounds below 48dp; the final implementation explicitly sizes it to 48dp and both editions' complete reruns pass.

Native primary/added credential editor, compact-password, connected grouped-detail and large-text blank-note screenshots were inspected. Only local ignored evidence is retained. The test driver restores the original active user, Autofill/accessibility/IME settings and stay-awake setting. The task-started emulator is stopped after verification; its reusable AVD is preserved.

## Release notes

Added Chinese and English entries to the ordinary and F-Droid 1.0.321 unreleased notes and both local integration candidate notes. F-Droid fastlane 28 contains 352 Chinese and 488 English characters. Version numbers, existing published history, tags and Releases are unchanged.

The integration candidates still have an older editor baseline without grouped credential models. Their notes are synchronized, but this change does not claim to port or validate those missing source features. No APK is copied to a delivery folder.
