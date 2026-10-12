# Nearby manual sync menu, 1.0.319

The shared top-right overflow menu offers manual Wi-Fi sync after a device has
been paired. This covers password, Passkey, authenticator, note, wallet, Steam
and vault menus without maintaining separate actions for each page.

The entry remains available with automatic sync off. It dismisses the menu,
opens the existing nearby-sync destination, and calls the existing coordinator's
manual sync action. The configured database, direction and paired peers remain
the source of sync scope; the current page filter does not change them.

While preparation, a manual round, an active connection or a Passkey review is
in progress, the entry opens status without starting another round. Pairing and
locked states disable the entry. The action rechecks current state on click;
forgetting every peer removes it. Existing Passkey consent, remembered reminders,
nonzero-counter skips and connection diagnostics remain on the sync page.

The local M3E Canvas sketch and screenshots are kept in the ignored
`docs/design/nearby-sync-menu-319` directory. No design images or user data are
included in this change. Both editions remain 1.0.319 unreleased.

Both application and instrumentation APKs built successfully. Each edition
passed 11 instrumentation tests without skips on the shared
`Monica_Issue136_API_32` emulator (Android 32, x86_64): 61.162 seconds for the
ordinary edition and 60.192 seconds for F-Droid. The suites cover five new menu
cases, three automatic/manual controls and diagnostics cases, the large-text
Passkey reminder, and two real pairing/scanner navigation cases. The rendered
native shared menu was visually inspected; the label and existing actions fit.

An initial combined run passed the menu tests but failed the existing automatic
toggle test after menu fixtures had started the application-wide coordinator.
The toggle test passed in isolation. Menu tests now navigate using synthetic
state and the production sync content, keeping their coordinator state isolated;
the separate pairing/scanner tests still exercise the real destination. Both
complete suites then passed. Production synchronization and session behavior
were not changed to accommodate this test fixture issue.

The shortcut delegates to the existing coordinator; this change does not alter
the encrypted transport, attachment handling or Passkey skip rules. These runs
verify the menu and navigation flow, not a new physical two-device Wi-Fi trial.
