# Nearby metadata and attachment verification — 1.0.320

Date: 2026-10-15. Ordinary Android and F-Droid, with their existing version codes (12 and 27).

## Result

Folder/root metadata now carries actual attachment bytes through Android adoption and subsequent forwarding. The repair preserves portable attachment identities, standalone labels, extension fields, path-node fields and source ownership. Both editions use identical code for this repair.

The original regression was reproduced before implementation: the receiver had the attachment descriptors but its outgoing archive lacked the file, failing with `Missing metadata attachment bytes 0`. The same provider test now passes through three Room replicas.

| Verification | Ordinary | F-Droid |
| --- | --- | --- |
| Host LAN/storage regression cases | 78 passed, 1 skipped | 78 passed, 1 skipped |
| Android device cases, final outcome | 9 passed | 9 passed |
| Debug application and instrumentation builds | Passed | Passed |

The optional host `DeviceSelectedDatabaseTest` requires a matching native MDBX host library and was skipped by its existing environment check. Native database selection/export is exercised on Android by the added native → Room → another native vault bridge.

The first ordinary device run passed seven cases; its two coordinator cases encountered the emulator keyguard and correctly entered `locked`. After the runner explicitly woke the display and dismissed the keyguard, the same application and test APKs passed both cases. F-Droid passed all nine together. These initial failures remain recorded in the accompanying results JSON.

## Scenarios exercised

- Three Room replicas forward 280,017-byte and 180,003-byte metadata files and compare exact bytes.
- File-backed Room close/reopen preserves encrypted files, wrapped keys, portable identities and unknown descriptor fields.
- Attachment replacement, omitted asset lists, explicit empty lists and owner-specific purge preserve other owners' files. A replacement removes the previous local encrypted blob after commit.
- A corrupt second file and a database failure after writing a replacement both roll back metadata, remove new blobs and preserve the old file.
- Packaged native MDBX root metadata supports two independent owners, standalone labels, unknown marker fields, replacement, omitted assets/labels, retained trash, restore, purge and native vault reopen.
- Native folder metadata retains unknown payload/path fields. Unsupported attachment-bearing folder deletion is rejected before changing the native checkpoint.
- Deleting a folder that still has an active child rolls back preceding metadata commands and leaves the checkpoint and exported data unchanged.
- The complete native → Room → another native vault path preserves root/folder identities and 310,031-byte files through the production provider/repository and encrypted archive reader.
- Existing encrypted TCP, send-only/receive-only, automatic presence gating, unchanged reopening and toggling during export/import remain covered.
- The bulk transport fixture transfers 610 passwords plus live/trashed secure items, images/attachments and Passkeys, verifies the received contents and repeats the transfer without duplicates. Steam encrypted and one-way transfers remain covered.

## Storage and failure behavior

Room metadata has its own encrypted asset rows and encrypted blob directory because ordinary attachment rows require a password or secure-item parent. It reuses the existing attachment encryption and key-wrapping components. The ordinary attachment orphan collector cannot remove these metadata files. Database changes and owner metadata share a transaction; failed attempts clean up newly created files, while obsolete files are removed only after commit.

Native adoption plans label/file changes before writing and commits them with the owner marker in a composite operation. Root claims partition tags, standalone labels and files by source; unclaimed content remains with the local root. File hashes and sizes are verified before adoption and when reading outgoing assets. Diagnostic messages explain missing/corrupt files and unsupported folder deletion.

## Limits and environment

- Room does not have retained folder trash. Such incoming operations now fail explicitly before mutation instead of silently becoming permanent deletion.
- The bundled native engine cannot atomically trash or purge a folder with active folder attachments. These operations fail before writes and preserve the folder; restore it on the sending device before retrying. Root retained trash/purge and empty native folder purge are supported and tested. This repair does not add a new native engine operation or ABI.
- Tests ran on the shared `Monica_Issue136_API_32` x86_64 AVD in isolated Android user 10, using real Android storage/JNI and encrypted loopback TCP. This is not a claim of testing two physical phones on different Wi-Fi networks.
- The test runner restores the previous Android user. No screenshots or design assets are included. Test output records APK and source hashes; no APK is delivered.
- Legacy experimental candidate trees do not contain the full nearby-sync baseline and have extensive unrelated local changes. They were not overwritten with an incomplete subset. The active ordinary/F-Droid sources and their 1.0.320 release notes are updated.

Machine-readable counts, cases, hashes and the retained initial failure record are in `NEARBY-METADATA-ASSETS-320-RESULTS.json`.
