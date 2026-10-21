# Nearby status, connection waiting and cancellation — 1.0.321

Date: 2026-10-21. Scope: ordinary Android, F-Droid, and the browser extension's paired-device connection setup.

## Behavior

- The status card is the first content under the nearby-sync app bar. Its indicator and text slots remain allocated in idle, preparing, waiting, transferring, failed, completed, cancelling and cancelled states.
- The official Material 3 Expressive indicator has no outer background. It is static when stopped and animated while active. Center text uses the higher-contrast black/white foreground against the actual indicator color, including custom themes. Known phase totals show actual percentages; unknown totals show an ellipsis rather than invented progress.
- Both devices still require their own manual request with automatic sync disabled. They can request in either order. Android grants a full two-minute connection window after snapshot preparation and consent. The browser retains discovery while the phone prepares and rediscovers replaced listeners for transient connection-setup failures.
- Authentication rejection, invalid data and save failures remain terminal. Setup retries reuse the prepared snapshot and do not replay saves or acknowledgements.
- Cancel publishes the cancelling state before acquiring the data gate, cancels the active preparation coroutine, and signals native workers. Cleanup retains pairing and committed content. Generation checks discard manual requests queued before Cancel. Automatic-sync preference is retained.

## Diagnosis and regression evidence

- The original browser discovery stopped after three rounds. A regression where the authenticated paired listener first appears on discovery round five failed before the fix and passed afterwards.
- Android Cancel originally queued behind the coordinator gate. The regression holds that real gate, checks immediate cancelling feedback, and verifies stale progress cannot restore an active state.
- A second Android regression holds the real snapshot provider mutex and verifies cancellation interrupts the waiting tick before that mutex is released. A queued pre-cancel manual request must not execute later.
- Native host tests use isolated synthetic databases and the production coordinator/native transport. They also retain existing manual sync, connection expiry, unlock, mode, preparation and receiver-save-rejection coverage.
- Browser regression command: `npx vitest run src/nearby/local-coordinator.test.ts src/nearby/manager-client.test.ts src/nearby/native-transport.test.ts src/background/nearby-owner.test.ts`. Result: 108 tests passed; production build passed.

## Acceptance

Both Android editions passed debug APK and instrumentation APK assembly, and all 17 selected JVM tests per edition passed with no failures or skips. Each edition passed all seven status/manual-controls UI tests on the existing shared `Monica_Issue136_API_32` emulator. After changing the stopped indicator from the default circle endpoint to the official indeterminate shape sequence's static first shape, the three status UI tests passed again on both final packaged builds.

The UI checks cover top placement; stable card and indicator bounds across preparation, waiting, transfer, errors, completion and cancellation; a real 25% byte-progress label; diagnostics; manual controls; and Chinese dark-mode rendering at 1.5x font scale. Final native captures were inspected for background removal and readable center text. Installed app/test APK hashes were verified, and the original user, IME, Autofill, accessibility, screen timeout and isolated-user lockscreen setting were restored. The existing public emulator remains running.

Machine-readable suite results and final APK hashes are in `nearby-status-cancellation-321-results.json`. Main, F-Droid and browser diffs pass whitespace checks. Eight task source/test/resource files match between Android editions, resources parse with unique names, and bilingual release entries are unique. F-Droid fastlane 28 entries are 498 characters in each language.

Local editable M3E Canvas: project `nearby-status-321-20261021`, with idle, waiting and transfer frames. Draft JSON and screenshots stay in ignored local design files.

This validates coordinator behavior with controlled native-host timing and the packaged Android UI on the shared API 32 x86_64 emulator. It does not establish behavior across every physical Wi-Fi access point, multicast policy or two physical devices.
