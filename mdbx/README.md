# MDBX

This directory contains the Rust workspace and implementation notes for Monica MDBX.

MDBX is Monica's local-first encrypted vault format. It is designed around stable long-term storage, Git-like logical history, sync conflict handling, native attachments, snapshots, and Tiga security modes.

For the normative format documents, see `../mdbx-doc/`.

## Workspace Layout

- `crates/mdbx-core`
  - Core domain types.
- `crates/mdbx-crypto`
  - Encryption, KDF, and key material handling.
- `crates/mdbx-sync`
  - Sync payload and object payload model.
- `crates/mdbx-storage`
  - SQLite schema, vault initialization, repositories, search, snapshots, conflicts, recovery, and sync state.
- `crates/mdbx-cli`
  - CLI entry point for local testing and development.
- `tests/`
  - Compatibility, concurrency, and recovery scenarios.

## Documents In This Directory

- `CLIENT_INTEGRATION_GUIDE.md`
  - English guide for implementing MDBX support in another client.
- `CLIENT_INTEGRATION_GUIDE.zh-CN.md`
  - Chinese guide for implementing MDBX support in another client.

## Specification Documents

Read the spec set in `../mdbx-doc/` before changing storage behavior:

- `README.md` / `README.zh-CN.md`
- `01-product-spec.md`
- `02-storage-sync-spec.md`
- `03-security-spec.md`
- `06-sqlite-schema-v1.zh-CN.md`

The `mdbx-doc` directory defines the format and product constraints. This directory implements and documents practical integration.

## Client Support Levels

MDBX support should be labeled honestly:

- **Read-only support**
  - Open and unlock a vault.
  - Display folders, entries, and attachment metadata.
  - Do not write tables, commits, tombstones, snapshots, or conflicts.
- **Basic read/write support**
  - Create and edit entries and folders.
  - Preserve commits, object versions, tombstones, snapshots, branch heads, and device heads.
- **Sync support**
  - Merge commit DAGs, preserve tombstones, detect conflicts, and apply sync state safely.
- **Full Monica-compatible support**
  - Provide the required management screens, diagnostics, snapshot structure preview, field-level history, and folder-aware move/copy/create flows.

See `CLIENT_INTEGRATION_GUIDE.md` for the complete checklist.

## Required User-Facing Management Screens

A full user-facing client should include:

- MDBX format-management home
- database detail page
- folder / structure management
- move / copy target picker
- conflict management
- commit history
- snapshots
- snapshot structure preview
- diagnostics / maintenance
- unlock and security

The format-management entry should always land on the MDBX management home. It should not automatically enter the last opened vault detail page.

Normal users should not see raw developer tools such as sync bundle import/export, benchmarks, or low-level chunk debugging. Keep those behind developer mode.

## Development Commands

From this directory:

```powershell
cargo test
```

Run the CLI during local development:

```powershell
cargo run -p mdbx-cli -- --help
```

## Implementation Rules

Do not bypass repository/storage APIs from client code unless you are changing the storage layer itself.

Client code should not directly write:

- `commits`
- `commit_parents`
- `object_versions`
- `tombstones`
- `snapshots`
- `key_epochs`
- `conflicts`
- `device_heads`
- `branches`

Batch user operations should normally produce one user-level commit, not one commit per object.

## Compatibility Checklist

Before claiming full support, a client should verify:

- Monica-created MDBX vaults open correctly.
- Nested folders can be created and selected as targets.
- Batch move/copy/delete creates coalesced commits.
- Tombstones prevent deleted objects from reappearing.
- Two clients show the same item count for the same vault.
- Conflicts are detected and displayed.
- Snapshots can be created and reverted with confirmation.
- Diagnostics expose sync, health, history, tombstone, attachment, and dangling-head status.
