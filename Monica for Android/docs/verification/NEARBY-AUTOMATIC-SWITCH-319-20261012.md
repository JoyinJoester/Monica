# Nearby automatic sync switch, 1.0.319

The automatic-sync setting previously used the same mutex as the coordinator's
export/import loop. Holding that mutex and invoking the real `setEnabled` API
reproduced the unresponsive switch before the fix. Progress refreshes and old
encrypted preference snapshots also need to respect the latest user choice.

The local scheduling flag now has an independent SharedPreferences key. Its
in-memory value and Compose state change immediately; Android queues its disk
write without waiting for export/import. Existing settings remain the fallback
until the user changes the switch. Saving peer metadata cannot overwrite it.
Pairing secrets and replica keys remain encrypted, and session-lock checks and
Passkey consent still gate transfers.

Turning automatic sync off prevents new automatic connections, including when
the choice changes during preparation or discovery. Idle listeners are stopped
on the next tick. Established transfers finish applying and exchanging receipts;
the foreground service stays active for that work. Manual synchronization and
pairing remain independent. Cancelling the Passkey review still cancels the
operation. The existing switch subtitle explains the current-transfer behavior.

Validation uses the public `Monica_Issue136_API_32` AVD, reused without resetting
its data. The coordinator test creates isolated Room databases, preference
stores, replicas and encrypted attachment blobs. It uses real Android Keystore
operations, the production coordinator mutex, provider, JNI and TCP transport.
All test records and files are synthetic.

- The host regression holds the coordinator gate during preparing, sending,
  applying, confirming, failed and waiting states. Repeated taps update and save
  immediately, retain the phase, and survive both progress publication and a
  stale settings save.
- The Compose test clicks the real switch while the coordinator gate is held,
  including pairing-wait state, and checks both semantics and saved settings.
  Existing manual controls and database selection are also exercised.
- The Android integration test creates 610 passwords, six live secure-item types,
  six trashed types, folder relationships, custom fields, password history,
  linked notes, Steam and counter-0/counter-7 Passkeys. Six attachments include a
  4 MiB + 17 byte binary, duplicate filenames with distinct contents, a PNG, an
  empty file and a trashed attachment.
- During actual export, it toggles off/on/off with the production gate held and
  verifies that no connection starts. During actual receiver import it toggles
  again and turns both sides off, then requires successful receipts from both.
- It verifies fields, folder/link relationships, Steam secrets, the nonzero
  Passkey skip policy, attachment MIME/size/SHA-256 and decrypted bytes. A later
  password edit is not transferred while automatic sync is off. Explicit manual
  synchronization then transfers it and passes the attachment checks again.

Results: each edition passed all 63 host sync tests (zero skipped) and all three
device tests. The device suites took 302.771 seconds for ordinary and 172.220
seconds for F-Droid. Both debug application and instrumentation APK builds
succeeded, including the x86_64 test ABI. F-Droid's host JNI was rebuilt from
its current sources before running its host tests.

Both builds remain 1.0.319 unreleased (ordinary versionCode 12, F-Droid 26).
Device transport testing runs over loopback on one API 32 emulator. It does not
establish physical Wi-Fi interoperability on the reported Android 36/37 phones.
No screenshots, user logs, credentials or generated APKs belong in this report.
