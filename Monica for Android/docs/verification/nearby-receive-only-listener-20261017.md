# Nearby receive-only listener regression

The user reported `preparing / missing_sync_bundle / start` before any address attempt, on Android build `7bb0f4343a` (1.0.320). A fresh receiving database failed in receive-only mode but worked in bidirectional mode. The supplied recording also shows manual retries remaining in preparation failure.

The coordinator selects the listener by device ID, independently of synchronization direction. Receive-only correctly omits its local export and content key. Rust `start` nevertheless required an outgoing bundle for every listener, unlike `connect`. Existing Android tests assigned the receiving device to the connecting role; the Rust direction matrix supplied artificial outgoing bundles even for receive-only peers.

The fix allows a missing outgoing bundle only for a validated protocol-2 receive-only peer. Protocol-1, unspecified, bidirectional and send-only listeners keep the previous requirement. Supplied artifact directories still undergo the existing validation. Identity checks, encrypted handshakes and negotiated direction enforcement are unchanged.

The Android role-swap regression also exposed a manual-sync race after disabling automatic sync. Updating a manual request can replace the peer's advertised listener while a connection is beginning. The resulting `connection_failed` previously removed the pending manual request, preventing recovery with automatic sync disabled. Connection setup failures now retain an existing manual request only within its original deadline. No request is recreated after cancellation; authentication rejection, content/apply errors and pairing failures keep their previous handling.

## Validation

- Before the fix, the new real-socket listener test failed with `missing_sync_bundle`.
- After the fix, all 38 Rust tests passed. Coverage includes receive-only listeners with send-only/bidirectional connectors, actual bundle/attachment bytes, no reverse files or content key, required-bundle negative cases, direction conflicts, legacy negotiation and authentication failures.
- An initial Android run was stopped by the emulator's lock screen. With the screen awake, packaged JNI passed both roles and the normal-role coordinator test passed; the reversed-role test exposed the manual retry race above. Assertions now permit only transient connection setup errors while the manual request remains active; final data and success receipts from both peers are still required.
- Android packaged JNI, coordinator, discovery and Room regression: all 4 tests passed on the public API 32 emulator, test user 10, in 59.827 seconds. Both connection roles verified actual received data and completion receipts. The coordinator tests also verified no redundant transfer after reopening and successful manual synchronization with automatic synchronization disabled.
- Ordinary final Debug, Android test and Release APK builds passed in 6m 50s; the resulting Debug/test APKs were used for the passing emulator run. F-Droid final Release and Android test APK builds passed in 4m 44s. The ordinary build used `-PincludeX86TestAbi -PdailySeq=04` for the public x86_64 emulator; F-Droid used its default build configuration. The ordinary Release build emitted existing R8 Kotlin metadata compatibility warnings and completed successfully.

Android regression tests cover both connection roles with a fresh receiver, first import, background/reopen without redundant transfer, a subsequent update, and manual synchronization after disabling automatic synchronization. All records and keys are synthetic. Emulator tests do not replace a two-phone test on the user's Android 36 Wi-Fi network.

Changes are recorded under unreleased 1.0.321 for both distributions, including F-Droid fastlane 28. No version increment, tag or Release is created. User recordings and screenshots are kept outside the committed files.

Public AVD cleanup: restored the original stay-awake setting (0), switched back to user 0, and stopped the emulator started for this task. The shared AVD configuration and data disk were retained. No autofill, biometric or device-authentication settings were changed.
