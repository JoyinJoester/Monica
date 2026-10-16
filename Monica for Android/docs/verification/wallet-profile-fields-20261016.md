# Independent profile fields and web autofill — 1.0.320

## Behavior

- Addresses and cards have optional, separately entered name parts. Full names are never split; parts are never merged into a stored full name. An absent part stays blank during autofill.
- Identity editing, legacy JSON import and Bitwarden synchronization follow the same rule. Pre-extension Bitwarden records preserve an existing local full name; an explicit encrypted empty field propagates intentional clearing. Full-name detail rows display only the stored full name.
- Address/identity profiles preserve district/county, address line 3 and independent home/work email and phone. Blank group values do not fall back to the default contact.
- Autofill keeps HTML field semantics rather than treating every contact as a username. Login email cannot be written into telephone fields. Phone segments use offline libphonenumber parsing (Apache-2.0; no Google Play service).
- HTML input street addresses use separators; textarea street addresses preserve line breaks. Individual address lines contain only their own line.
- Android list controls receive option indices, including country, US/Canadian region and month/year labels. Unknown or ambiguous options are left untouched; other valid targets still fill.
- HTML form, section, shipping/billing and contact qualifiers constrain the returned targets. Repeated unmarked card/address roles delimit separate blocks. Chromium's token arrays are parsed as a complete autocomplete grammar.
- Existing system authentication is shared: matching account metadata appears first, and selection verifies once using fingerprint or screen lock. The full picker remains protected when quick-fill verification is off.

## Verification

- Normal and F-Droid Debug/AndroidTest builds passed; 325 normal and 327 F-Droid focused unit tests passed, including Android API 29/34 framework simulations.
- Normal and F-Droid Release builds, including R8/resource shrinking, passed on the final source.
- Five API 32 device tests passed: encrypted Bitwarden card and identity upload/fresh download; KeePass save/edit/reopen; identity editor preservation; large-font address editor edit/save. Editing the given name preserved the different full name. Backend field metadata did not duplicate user custom fields. Both Bitwarden device tests were rerun in normal and F-Droid, including legacy full-name preservation and explicit clearing without changing the independent first name.
- Mozilla site tests use only synthetic accounts, cards, identity data and contacts in dedicated Android user 10. They select Monica through Android autofill and compare the browser DOM before/after; no form data is submitted.
- Full name `Jane Quinn Doe`, address/identity parts `Independent / Middle / Parts`, and card parts `CardFirst / CardMiddle / CardLast` deliberately differ. Home/work phone suffixes `0143`/`0144` and distinct emails prove group separation.
- Real testing identified and fixed additional issues: single-line HTML inputs stripping address line breaks; Chromium labeling textarea as input with type=textarea; qualifiers split across hint-array elements; dynamic expiry-month/year IDs incorrectly treated as a combined expiry date.
- The host driver now waits for the new document and leaves Android Back untouched; a temporary blank bottom margin makes original controls reachable above the IME. Test code never assigns field values to simulate autofill (except explicitly recorded dynamic-form setup).

## Browser coverage and limits

- Original discovery findings A1–A6 are kept in the earlier Mozilla report as historical evidence; the current report and result summary record the follow-up. Login email no longer spills into phone/name targets. Independent full and split names, home/work contacts, three address lines, country/state/expiry selects and shipping/billing/card groups were checked against actual browser values.
- Repeated unmarked address/card groups and explicit section tokens filled independently. Standard card-name and independent card-name-part entry points also worked.
- Twenty-one login/change forms across normal DOM and two Shadow DOM layouts were inspected. Every changed field remained within the focused form; explicitly marked new-password fields stayed untouched. Unmarked five-password forms still have no role information and receive the chosen password in all five fields. Pre-filled password entry points may offer no suggestion; these observations are not claimed as successful fills.
- Dynamic pages can add or replace controls after the selected country/card number changes. The user can select Monica again after new controls appear. The Android response does not claim to anticipate future DOM nodes or make a whole changing form fill in one operation. Unknown or ambiguous select values stay blank.
- The name-utils page is a name-splitting demonstration rather than an ordinary form; no suggestion is expected or claimed. HTTP Basic Authentication is unsupported by this GitHub Pages fixture. No website backend sign-in or submission was tested.
- An earlier sweep stopped after Chrome's GPU-channel timeout. Interrupted/invalid snapshots were rerun where needed, not counted as successful cases. The host driver also waits for a new document and for asynchronous authentication/fill completion.

- Follow-up summary contains 84 selection attempts across 18 input pages: 80 returned a selected entry, and 4 offered no suggestion (the name utility plus three pre-filled password entry points). Selection is not itself a correctness assertion. Dynamic card expiry, three textarea entry points, single-line addresses and password-only retries were checked against actual resulting values. See [machine-readable observations](wallet-profile-fields-20261016-results.json).
- A first request after emulator inactivity also exceeded Android's service response timeout during class verification; a subsequent request succeeded. This report does not claim cold-start timing is guaranteed on all devices.

## Scope and evidence

Device: shared `Monica_Issue136_API_32` emulator, Android 32, x86_64. API 29/34 are framework simulations, not claims of physical-device coverage. Chrome results and temporary screenshots are local; no design images are committed. The editable local Canvas design is under ignored `docs/design/wallet-profile-fields-320/`.

Both editions remain unreleased 1.0.320 (normal versionCode 12, F-Droid 27). No release/tag or APK delivery is part of this change.

## Cleanup

All interactive audit sessions ended through their cleanup command. Synthetic fixture rows and generated-history/settings snapshots were restored by the harness. User 10 is back to Swipe with zero fingerprints, Android autofill is Google, Chrome was restarted and its Google radio option checked. Gboard and the 600000 ms screen timeout remain unchanged. The audit tab and both adb forwards were removed, user 0 restored, and the task-started public AVD stopped without deleting its configuration/data.
