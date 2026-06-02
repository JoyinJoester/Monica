# MDBX Storage And Sync Specification

Version: `MDBX-1-DRAFT`

This document defines the single-file container strategy, internal persistence rules, incremental update behavior, sync model, and attachment storage behavior.

## 1. Container Strategy

MDBX SHOULD use a single portable `.mdbx` file as the user-visible vault artifact.

Inside the `.mdbx` file, the preferred engine is:

- `SQLite + custom encryption layer`

`LMDB` MAY be explored later, but SQLite is the preferred baseline because of tooling maturity, portability, recovery tooling, and schema evolution support.

## 2. Internal Storage Goals

The internal layout MUST support all of the following:

- append-friendly writes
- partial updates
- crash recovery
- attachment metadata storage
- attachment binary storage indirection
- version history
- conflict detection metadata
- future migration hooks

## 3. Minimum Internal Logical Tables

The minimum logical schema MUST reserve space for at least these record classes:

- `projects`
- `entries`
- `attachments`
- `attachment_chunks`
- `commits`
- `commit_parents`
- `device_heads`
- `branches`
- `tombstones`
- `snapshots`
- `key_epochs`
- `conflicts`
- `unlock_methods`
- `object_versions`
- `project_tags`

An MVP MAY omit some secondary indexes or optional tables, but MUST NOT omit `projects` or `attachments`.

## 4. Project-Oriented Schema Rules

The `projects` table is mandatory.
The `entries` table MUST reference `project_id`.

This means:

- every password-like secret belongs to a project
- queries MUST be able to fetch a project and then its child entries
- sync and merge logic MUST preserve project membership

## 5. Attachment Schema Rules

The `attachments` table is mandatory from version 1.
The `attachment_chunks` table SHOULD be present from version 1 even if chunked storage is only partially used in MVP.

The schema MUST support:

- attachment owned by project
- attachment optionally owned by a specific entry
- content hash
- chunked binary data or external content reference
- soft delete via tombstone or delete marker
- integrity verification

## 6. Write Path Requirements

Routine small edits MUST avoid full logical rewrite of the entire vault contents.

A compliant write path SHOULD:

1. update changed project or entry rows only
2. append a commit or oplog record
3. update lightweight head metadata
4. avoid touching unrelated attachment rows
5. avoid touching unrelated large binary pages

## 7. WAL And Append Strategy

The preferred implementation SHOULD use SQLite WAL mode or an equivalent append-friendly strategy.

Design goals:

- small edits generate small write deltas
- cloud sync tools can propagate small changed regions where supported
- periodic compaction is explicit and infrequent

The implementation MUST document how it preserves durability during power loss or crash.

## 8. Commit And History Model

MDBX MUST maintain a Git-like logical history.

Minimum requirements:

- each local mutation produces a commit-like history record
- commits reference one or more parent commits
- device-local order is monotonic
- concurrent histories remain representable until merged

A commit record SHOULD include:

- commit ID
- device ID
- local sequence number
- parent commit IDs
- changed object references
- timestamp
- optional merge metadata
- integrity data

## 9. Conflict Detection

MDBX MUST detect concurrent edits using causal metadata, not timestamp alone.

Minimum acceptable mechanisms:

- version vectors
- device sequence graph
- per-record revision lineage
- field-level conflict markers where necessary

Different-field concurrent changes within the same project MAY auto-merge when safe.
Same-field concurrent secret changes MUST create an explicit conflict.

## 10. Merge Model

MDBX SHOULD support:

- fast-forward merge
- three-way merge for non-secret text fields
- conflict record creation for unsafe merges
- user-visible merge resolution later

The merge system MUST preserve both sides when automatic resolution is unsafe.

## 11. Snapshot And Recovery

MDBX MUST support recovery from logical corruption or interrupted sync.

Minimum requirements:

- historical commits remain replayable
- snapshots can be produced periodically
- snapshots can rebuild projects, entries, and attachments metadata
- partially damaged vaults SHOULD still allow partial recovery

## 12. Attachment Storage Modes

MDBX MUST define these storage modes even if not all are enabled in MVP:

- `embedded-inline`
  - small binary stored directly in attachment payload

- `embedded-chunked`
  - attachment stored in encrypted chunks inside the database

- `external-hash-ref`
  - database stores metadata plus verified external blob reference

Default recommendation:

- small attachments MAY be embedded
- large attachments SHOULD be chunked or externally referenced by content hash

## 13. Attachment Update Rules

Editing project metadata MUST NOT require rewriting large attachment content.
Editing entry fields MUST NOT require rewriting unrelated attachment content.
Renaming an attachment MUST update metadata only.

## 14. Cloud-Drive Optimization

MDBX is designed for sync through tools such as Syncthing, Git, Nextcloud, WebDAV-backed sync layers, Dropbox, and OneDrive.

The implementation SHOULD:

- minimize rewritten regions for small edits
- prefer append-heavy patterns over random rewrite where practical
- compact only when thresholds are met
- keep attachment bodies isolated from routine metadata edits

## 15. Performance Targets

Target goals for a healthy implementation:

- common metadata save under `100 ms`
- project open fast enough for interactive UI
- search clearly faster than large KDBX libraries
- cloud-drive delta for small edit remains in `KB` scale in normal cases

These are product goals and must be tracked with benchmarks.

## 16. Required Indexing

The storage engine SHOULD maintain indexes for at least:

- project title
- project tag membership
- project group membership
- entry type by project
- recent modification time
- attachment ownership
- tombstone lookup
- commit lineage lookup

Full-text search MAY use temporary indexes for decrypted titles during an unlocked session. Persistent FTS tables MUST NOT store decrypted project titles or other secret-bearing text.

## 17. Compaction Rules

Compaction MAY rewrite larger portions of the vault, but it MUST be:

- explicit or policy-driven
- recoverable if interrupted
- unnecessary for routine edits
- safe for attachment integrity

## 18. Minimum Export Requirements

The storage layer MUST support export paths for:

- full vault export
- project export
- attachment extraction with integrity check
- KDBX export bridge

## 19. Rejection Rules

A storage design is non-compliant if it:

- lacks a first-class `projects` structure
- lacks first-class `attachments` structure
- rewrites the whole vault on ordinary small field edits by design
- cannot represent concurrent histories
- cannot explain recovery after interruption
