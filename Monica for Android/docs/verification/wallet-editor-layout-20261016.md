# Wallet editor layout verification — 2026-10-16

Scope: unreleased Android and F-Droid 1.0.320 wallet forms. Version codes remain 12 and 27 respectively.

## Changes

- Shared name/contact fields use Monica's filled form wrapper and connected field grouping.
- Name-parts guidance sits outside the clipped field group, with its own padding. Full names, name parts and default/home/work contacts remain independent values.
- Billing-address optional content uses the same compact content cards and separate editors as cards/documents. Returning closes the editor; card order is saved without changing address values. Older records without order metadata still decode.
- Photo cards own their rounded surface instead of being clipped inside a second form container. Photo actions stack on narrow screens and with large text.
- The editor dialog inherits the parent font scale and language resources; titles can wrap onto two lines.

## Build and unit results

| Checkout | Verification | Result |
| --- | --- | --- |
| Android | `:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease` | Passed on final source |
| F-Droid | `:app:assembleRelease :app:assembleDebugAndroidTest` | Passed on final source |
| Both | `CardWalletDataCodecTest` | 10 tests each, 0 failures/errors |
| Both | `WalletEditorOrderTest` | 4 tests each, 0 failures/errors |

The selected JVM suites were run after the model/order changes; subsequent changes were restricted to layout and instrumentation. The ordinary release build emitted Kotlin metadata compatibility warnings from R8 but completed successfully.

## Device results

Reused public AVD `Monica_Issue136_API_32`, Android 32, dedicated test user 10. Final ordinary Debug and test APKs were installed. Display override was 640 × 1400 pixels at 320 dpi (320dp width). Large-text cases explicitly used 1.7 font scale; the Chinese name-help test also asserts the actual dialog text-layout density is 1.7 and reports no visual overflow.

`WalletContentEditorTest`: **5 passed, 0 failed**, final run 140.819 seconds.

1. Name help and front/back photo cards remain readable with Chinese large text; newly added empty name fields can be closed and reopened.
2. Existing document identity survives section movement, editor reopening, activity recreation and saving.
3. Billing-address name parts, extra address, default/home/work contacts, hidden custom fields and notes can be added, collapsed, reordered, saved and reopened. Assertions compare the independent stored values and saved order.
4. A new bank card preserves reordered content and sensitive values after saving and reopening.
5. Extended/custom fields retain connected layout and empty added fields; the active input and confirmation action remain reachable with the keyboard visible on a narrow large-text screen.

Inspected the final screenshots for collapsed billing content, Chinese name parts, both photo cards, extended fields with keyboard, and hidden custom fields with keyboard. Text and intended rounded corners are visible; photo actions stack and the content scrolls below the fixed toolbar. Screenshots contain synthetic test content and remain local and ignored.

Instrumentation used the ordinary build. F-Droid was verified by its release/test builds, JVM suites and byte-identical shared source/tests, not a second device run. This UI pass does not retest camera hardware or cloud synchronization.

## Local design editor

M3E Canvas participated in the design before the native changes. The local editor now scopes draft storage, view state and edit locks by `?project=...`. Its build and 57 project-scope/share tests passed; a same-browser multi-tab check confirmed different projects remain editable, duplicate tabs of one project stay protected, the prior default draft stays intact, and reloading restores the wallet design.

Project URL: `http://127.0.0.1:5186/?project=monica-wallet-profile-320`. The editable JSON and full import link are saved under the ignored local `docs/design/wallet-profile-fields-320/` directory. Canvas tooling and design assets are local workspace changes, not Android repository payloads.

## Cleanup and delivery

- Restored the original display override to 1080 × 2100 pixels and retained the existing 320 dpi override.
- Returned to user 0; user 10 system font scale remains 1.0. No security or autofill settings were changed in this UI pass.
- Left the already-running public emulator available for reuse.
- Updated bilingual 1.0.320 unreleased notes, the local integration candidate notes and F-Droid fastlane 27. Historical releases are unchanged.
- No design/screenshot files, generated APKs, release tags or GitHub Releases are included.
