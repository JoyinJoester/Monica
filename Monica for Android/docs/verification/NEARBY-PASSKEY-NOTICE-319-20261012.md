# Nearby Passkey reminder preference, 1.0.319

The shared Passkey preflight dialog now offers an unchecked “Don't show again”
checkbox. The coordinator saves the local choice only after confirming the
currently pending review. Cancelling or submitting a stale review cannot save
it. The preference survives new coordinator instances and application restarts,
and peer metadata saves cannot overwrite it.

“Sync contents and connection diagnostics” includes the current reminder setting,
a view of restricted credentials, a selected-database check and an option to
restore reminders. Restoring reminders also clears the process-local consent
cache, so previously acknowledged restrictions can be shown again. The detailed
viewer has only a close action; opening or closing it never confirms a transfer.

Diagnostic inspection uses the validated local provider and does not start a
network connection, change the active sync phase or classify its own failure as
a transfer failure. If sync is paused, inspection closes the decrypted replica
when finished. Results are invalidated when the selected database changes or the
session locks. Credential names and relying-party information are displayed only
in the local viewer, not appended to copied connection diagnostics.

The reminder preference does not change export behavior: every nonzero-counter
Passkey is still skipped, source keys/counters remain unchanged, and
non-exportable keys still carry only credential metadata. The checkbox and
buttons remain outside the scrollable explanation/list at large text sizes.

Local M3E Canvas sketches and previews are saved under the ignored
`docs/design/nearby-passkey-notice-319` directory. No design images or user logs
are committed.

Both editions passed all 65 host sync tests and 10 device tests, with no skips.
The device suites took 121.098 seconds (ordinary) and 122.145 seconds (F-Droid).
Both application and test APKs built successfully. Tests cover
preference persistence, stale confirmation and cancellation, restoring prompts,
inspection without network activity, dialog controls at 1.5x font size, the
diagnostics viewer, and the shared move/copy dialog. The real Android transport
fixture also verifies 610 passwords, Steam, Passkeys, folders, secure items and
six encrypted attachments across automatic and manual rounds with reminders
disabled after the first confirmation.

The initial device invocation could not start normally while the shared emulator
had system-wide interrupt saturation and unrelated background-service ANRs. The
existing AVD was restarted with its original data disk; that interrupted attempt
is excluded from these results. Both complete suites then passed. A local render
of the actual 1.5x-font dialog was inspected: checkbox, hint and both actions are
visible while long explanations and credential rows scroll.

Device tests reuse `Monica_Issue136_API_32` (Android 32, x86_64) and isolated
synthetic stores. TCP transport uses emulator loopback; this does not replace
physical Wi-Fi testing on Android 36/37. Both editions remain 1.0.319 unreleased.
