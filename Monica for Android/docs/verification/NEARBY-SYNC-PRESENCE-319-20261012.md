# Nearby automatic sync presence verification — 1.0.319

Date: 2026-10-12. Ordinary Android and F-Droid remain unreleased.

## Behavior

Automatic synchronization participates only while the local app is visible and
unlocked. Android NSD advertises app availability and watches configured peers;
absence of an available peer returns before vault preparation or native discovery.
Direct discovery targets are restricted to available peers and explicit manual
requests. Backgrounding unregisters presence and cancels sync handles; activity
transitions have a 250 ms visibility grace period. Manual requests still bypass
the peer-presence gate while both apps are open.

Reopening or temporarily losing a peer no longer forces an unchanged full
transfer. Revision, interval, explicit request and settings checks still decide
when to synchronize. A failed transfer retains the existing retry behavior.

This is not a zero-packet or incremental-transfer implementation. Android mDNS
still sends small availability messages; loss notifications can be delayed by
the network/OS. When a real sync is needed, the existing eligible replica and
attachment transfer remains in use. Discovery is only an availability hint:
Noise authentication, saved peer trust, encrypted payloads, attachment checks and
Passkey restrictions remain authoritative. Older clients and networks blocking
service discovery can use explicit manual sync on both devices.

## Validation

- Both app and instrumentation APKs built from source with `-PincludeX86TestAbi`.
- Each edition passed 76 host tests, with zero failures, errors or skips: the
  `takagi.ru.monica.lansync.*` and `takagi.ru.monica.security.SessionManager*`
  suites. The matching host MDBX library was supplied through
  `MONICA_MDBX_HOST_LIBRARY`; JNI integration tests executed rather than skipping.
- Each edition passed 16 instrumentation tests without skips on the shared
  `Monica_Issue136_API_32` AVD (Android 32, x86_64), sequentially: ordinary
  144.604 seconds, F-Droid 132.386 seconds.
- Real Android NSD discovered two isolated endpoints, removed a stopped endpoint
  and detected reopening. Activity start/stop tests exercised the production
  visibility callback. A real coordinator/NSD/JNI round trip verified initial
  sync, background stop, unchanged reopening without a new successful transfer,
  and a subsequent edit reaching the receiver.
- The absent-peer fixture performed 20 ticks without a snapshot, native discovery
  timestamp or connection attempt, and rejected background manual work. The
  manual transport fixture synchronized successfully with both NSD peer sets
  empty, then verified automatic synchronization after presence returned.
- The rich transfer fixture exercised 610 passwords, live and trashed item types,
  Steam secrets, zero/nonzero-counter Passkeys, and six attachments
  including a 4 MiB + 17 byte binary, duplicate names, a PNG and an empty file.
  It verified received bytes, data preservation, manual repeats, counter skips,
  reminder persistence and toggling automatic sync during preparation/import.
- UI tests cover the shared menu, immediate automatic toggle, manual/database
  controls, Passkey diagnostics, large-text warning and pairing/scanner Back.
  The waiting state explains system discovery and keeps manual sync accessible.
  Both native renders were inspected; the shortened hint fits the stable status
  area. Canvas source/links and screenshots remain local in the ignored
  `docs/design/nearby-presence-319` directory.

## Failures investigated

The initial host manual-to-automatic test assumed wall-clock sleeping advanced
Robolectric time. Removing forced reopening transfers exposed that assumption;
the fixture now advances the cadence clock explicitly.

An initial F-Droid UI run failed to toggle automatic sync. Session logs showed a
sync observer locking the session during `markUnlocked`: the unlocked flow was
published before its timestamps were initialized. Timestamps now precede the
notification, with a regression test that observes the emission immediately.
The complete device suites passed after this production fix.

An early instrumentation attempt installed an older test APK after a combined
Gradle invocation failed its unit-test task. Its constructor mismatch was a
stale-artifact failure; separate successful APK builds preceded the final runs.

The existing F-Droid release checker expects the root released notes to have the
development version, so it rejects the deliberately preserved 1.0.318 root notes.
Per repository policy, 1.0.319 changes are in the next-version notes and fastlane
26.txt, not in published history. Their bilingual sections, version configuration
and 500-character changelog limits were checked separately. No release or tag was
created.

These are emulator tests using isolated synthetic stores and local transport;
they are not a physical two-phone Wi-Fi trial or a packet-volume benchmark across
different routers and Android OEMs.
