# Verification: external folder actions and local WebView suggestions

Both Android editions remain at 1.0.321 (unreleased). No release or tag is created.

## Folder actions

The password shortcut row and shared category selector now expose Rename, Move, Copy and Delete for KeePass, MDBX and Bitwarden folders. Move/copy open a centered destination dialog with asynchronous folder loading, scrolling, progress and visible failures.

KeePass and MDBX use the existing native database manager. UUIDs identify KeePass groups even after their display paths change. Self/descendant destinations are excluded and validated again on execution. Read-only KeePass sources allow copying, while moves stop before creating destination folders. Native tree transfers preserve native fields and profiles, copy all descendants before deleting the source, and retain source data after failed conversions. Existing portability checks still reject structures or fields that a destination cannot preserve; Monica local destinations currently accept flat folders at the root.

Bitwarden supports folder move/copy within the original account. Slash-separated folder names preserve the hierarchy; moving renames the whole subtree and retains cipher membership. A failed rename attempts to restore prior names and reports an incomplete restoration. Copies allocate distinct folder names and preserve encrypted item keys and unknown JSON fields. All source documents are checked before destination creation. Pending local edits block copying until they have been synchronized, avoiding stale server-only copies. Attachments, passkeys, organization-owned and deleted entries block copies. HTTP/verification failures keep original folders and entries. Failed multi-request copies may leave partial destination folders or entries. Cross-account and cross-format Bitwarden folder transfer is not offered.

## Local WebView matching

A user-provided diagnostic log from 2026-10-21 identifies `com.cn21.ecloud`, a `file:` WebView without a web domain, and `packageMatchAllowed=false`, yielding zero matches from 540 source-filtered candidates. The picker used the framework-provided empty suggestion list. The configured source filter was LOCAL; credentials in external stores still require changing that filter.

The service now permits exact app/package bindings for a non-browser local file WebView. Title similarity and package-token heuristics stay disabled in that context. Known browsers, remote WebViews and unknown WebView schemes without an origin retain the previous protection. Additional diagnostics report the exact-package policy independently.

The official Tianyi APK still crashes under ARM translation on the shared x86 emulator, so no claim is made that its actual login UI was exercised. Device regression uses synthetic context matching with the packaged native index. Final verification results are recorded below after execution; user logs and screenshots remain local and are not committed.

## Validation

Both production debug builds succeeded, including the packaged x86_64 native engines. Each edition passed 22 JVM tests: request-context policy (4), autofill matching (13) and Bitwarden folder planning/document preservation (5), with no failures or skipped tests.

Each edition also passed all 20 instrumentation tests on the existing shared API 32 x86_64 AVD, using isolated Android user 10. The suite covers native KeePass/MDBX tree persistence and failure protection (3), same-account Bitwarden copy/move, rename rollback and unsafe/pending-copy guards against a synthetic localhost server (3), existing external-folder mutation boundaries (3), explicit local-WebView package matching with the actual packaged native index (1), and folder actions, destination selection and visible retryable errors (10). Bitwarden tests do not constitute a live public-server interoperability test.

F-Droid ran with density override 280 and font scale 1.3; the ordinary edition ran at default density 420 and font scale 1.0. Native dialog screenshots were inspected locally. Source/test/resource parity was checked between editions. The initial device run exposed fixture-cleanup foreign-key ordering and a native-index test below its activation threshold; both test issues were corrected before the successful complete reruns. Only explicitly identified failed synthetic fixtures were cleaned up.

The runner verified installed production/test APK hashes and restored the original input method, autofill, accessibility, screen timeout and active user. Density, font scale and user-10 lockscreen configuration were restored separately. The shared AVD was already running and was left running. Real Tianyi login-page validation remains unavailable due to the emulator ARM-translation crash described above.
