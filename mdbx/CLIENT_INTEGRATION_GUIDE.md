# MDBX Client Integration Guide

This document is for implementers who want to support Monica MDBX in another client.

It does not replace the full format specifications. It answers three practical questions:

- What counts as a correct MDBX integration.
- Which management screens a user-facing client must provide.
- Which shortcuts will break sync, history, snapshots, recovery, or cross-client consistency.

Read these lower-level specs as well:

- `../mdbx-doc/01-product-spec.md`
- `../mdbx-doc/02-storage-sync-spec.md`
- `../mdbx-doc/03-security-spec.md`
- `../mdbx-doc/06-sqlite-schema-v1.zh-CN.md`

## 1. Integration Boundary

MDBX is not just a password table stored in a SQLite file.

A correct client must treat MDBX as a complete vault format, including:

- vault metadata
- unlock / key epochs
- Tiga security mode
- projects, folders, entries, and attachments
- tombstones
- commit DAG
- object versions
- snapshots
- conflicts
- sync state
- diagnostics and maintenance

A client may be read-only. Once a client supports writes, it must preserve history, tombstones, snapshots, and conflict metadata.

## 2. Recommended Integration Levels

### 2.1 L0: Read-Only Viewer

A read-only viewer may implement only:

- open `.mdbx` files
- unlock vaults
- read projects, folders, and entries
- read attachment metadata
- show the current head state

A read-only viewer must not:

- modify SQLite tables
- prune tombstones
- create commits
- fake snapshots
- auto-resolve conflicts

The UI should clearly mark the vault as read-only.

### 2.2 L1: Basic Read/Write Client

A basic read/write client must implement:

- vault creation
- open / unlock
- create, update, and delete entries
- create, update, and delete folders or project containers
- tombstone writes
- commit generation for every user-level mutation
- object version updates
- device head / branch head updates
- basic snapshot maintenance
- local display cache refresh

A basic read/write client must not create unnecessary per-object commits.

For example, when the user batch-moves 100 passwords into MDBX, that is one user-level operation. The client should create one batch commit whose changed object list contains all affected objects, not 100 commits and 100 automatic snapshots.

### 2.3 L2: Sync Client

A sync client must additionally implement:

- sync state read/write
- commit DAG merge
- parent commit validation
- concurrent edit detection
- conflict records
- three-way merge or field-level merge
- tombstone anti-resurrection logic
- attachment chunk / external hash reference verification
- pending upload flush
- remote state download and apply/replay

A sync client must not overwrite the whole vault by timestamp alone.

### 2.4 L3: Full Monica-Compatible Client

A full client should implement:

- Monica local category / quick-folder mapping
- nested folder create, move, and copy
- snapshot structure preview
- current-vs-snapshot structure comparison
- commit history details
- field-level change display
- conflict merge UI
- database diagnostics / maintenance UI
- WebDAV / OneDrive / local external file compatibility
- background preload for the currently selected vault, without preloading all configured vaults

## 3. Recommended Code Entry Points

The Rust workspace is split by responsibility:

- `crates/mdbx-core`
  - core domain types
- `crates/mdbx-crypto`
  - encryption, KDF, and key material handling
- `crates/mdbx-sync`
  - sync payload / object payload model
- `crates/mdbx-storage`
  - SQLite schema, vault initialization, repositories, search, snapshots, conflicts, recovery

Clients should prefer storage / repository APIs over hand-written SQL.

Unless you are implementing the low-level storage library itself, client code should not directly write these tables:

- `commits`
- `commit_parents`
- `object_versions`
- `tombstones`
- `snapshots`
- `key_epochs`
- `conflicts`
- `device_heads`
- `branches`

Direct writes to these tables often produce vaults that appear to save correctly but disagree across clients, lose delete history, explode history size, or fail snapshot recovery.

## 4. Write Rules

### 4.1 User-Level Operations Map To Commits

Commit granularity should follow user intent, not internal object count.

These operations must be coalesced into one commit:

- batch delete
- batch move
- batch copy
- batch import
- importing a KDBX folder
- migrating a group of entries from Monica local storage
- moving a folder together with its children

These operations may become multiple commits:

- the user explicitly saves multiple times
- a long transaction is interrupted and resumed
- the client must split work for memory reasons and the UI clearly presents multiple batches

### 4.2 Deletion Must Use Tombstones

Deleting an object must:

- mark the object deleted or remove it from the visible index
- write a tombstone
- write a commit
- write an object version
- update the device head

Sync clients must use tombstones to prevent old remote state or another client from resurrecting deleted objects.

Clients must not only remove a row from the current list.

### 4.3 Folders And Paths

Clients must preserve stable folder IDs. Do not rely only on title or path strings.

Nested folders must preserve parent relationships. When the user enters `a/b/c`, breadcrumbs or path displays must be able to recover the full chain, not `a/c`.

Folder lists should:

- show folders before regular items
- keep stable ordering among siblings
- indicate nesting with indentation or guide lines
- treat expand/collapse as UI state only

Move, copy, and create-entry flows must be able to target MDBX folders, not only the vault root.

### 4.4 Attachments

Attachments are first-class MDBX objects.

Clients must:

- preserve attachment IDs
- preserve project / entry ownership
- verify content hashes
- support chunk metadata
- distinguish embedded, chunked, and external hash reference modes

Clients must not rewrite unrelated attachment content when editing an entry title or password.

### 4.5 Snapshots

Snapshots support recovery and structure comparison. They are not ordinary logs.

Clients should:

- support manual snapshots
- support automatic snapshots
- support pruning automatic snapshots
- require confirmation before snapshot rollback
- show snapshot structure preview

Batch operations should avoid producing many automatic snapshots.

## 5. Required User Management Screens

Any user-facing client that lets users manage MDBX should provide the following screens.

### 5.1 MDBX Format Management Home

Purpose: manage vaults by storage location.

It must show:

- local MDBX
- WebDAV MDBX, if supported
- OneDrive / cloud MDBX, if supported
- vault count per source
- create vault
- open existing vault

Opening "MDBX format management" should land on this home screen, not automatically enter the last opened database detail page.

The password list may remember the current vault for preload. The format-management entry should remain neutral.

### 5.2 Database Detail Page

Purpose: manage one vault.

It must show:

- vault name
- storage path
- storage type
- Tiga mode
- default status
- sync status
- health status
- commit count
- snapshot count
- tombstone count
- attachment count and size

It must provide:

- sync
- conflict management
- snapshots
- commit history
- diagnostics / maintenance
- delete vault

Normal user UI should not expose developer tools such as raw bundle import/export, benchmarks, or low-level chunk debugging. They may stay behind developer mode or internal tooling.

### 5.3 Folder / Structure Management

Purpose: manage vault organization.

It must support:

- root directory
- nested folders
- create child folder
- rename folder
- move folder
- delete folder
- expand / collapse
- breadcrumb path
- quick status bar

When the user creates a password while inside an MDBX subfolder, the create screen should default to that MDBX database and folder.

### 5.4 Move / Copy Target Picker

Purpose: move or copy items to another category or vault.

Recommended flow:

1. Select storage category or database.
2. Select target folder.
3. Confirm the operation.

The picker must support MDBX folder targets.

After a target is selected, the multi-select menu should close and progress should be shown by a quick status bar or background task panel. Do not leave the UI looking like the operation has not started.

### 5.5 Conflict Management

Purpose: resolve concurrent edits.

It must show:

- conflicted object title
- object type
- local version
- remote / incoming version
- conflicting fields
- creation time
- related commit

It must support:

- keep local
- use remote
- field-level merge, if supported
- write a new commit after resolution

Conflict display should use parsed field diffs, not raw JSON or SQL code blocks.

### 5.6 Commit History

Purpose: explain what changed.

It must show:

- commit sequence or short ID
- commit time
- device ID
- operation type
- affected object count
- change summary

Details should show field-level unified-diff-style changes:

```text
Title:
-   null
+   example.com

Username:
-   old@example.com
+   new@example.com
```

This is a unified diff structure, not a code view. The UI should parse fields and values so regular users can understand it.

Deleted objects should be shown as "deleted password entry / folder", not primarily as a low-level `deleted: true/false` field change.

### 5.7 Snapshots

Purpose: recovery and structure inspection.

It must show:

- manual snapshots
- automatic snapshots
- creation time
- creating device
- base commit
- full / incremental marker
- prune automatic snapshots
- create snapshot
- revert snapshot

Snapshot revert must require confirmation.

### 5.8 Snapshot Structure Preview

Purpose: inspect snapshot structure like a file explorer.

It must support:

- folder display
- folders before regular items
- expand / collapse
- nesting guide lines
- current path title
- snapshot version node status

Landscape or wide mode should support side-by-side comparison:

- left: current version
- right: snapshot version
- center: simple divider, no heavy card wrapper needed

### 5.9 Diagnostics / Maintenance

Purpose: help users and support staff judge vault health.

It must show key metrics:

- readable or not
- sync status
- pending sync count
- unresolved conflict count
- commit count
- snapshot count
- tombstone count
- entry count
- folder / project count
- attachment count and size
- file path

It must show advanced details:

- format version
- default Tiga mode
- active key epoch
- branch count
- device head count
- dangling parent
- dangling branch head
- dangling device head
- attachment chunk mismatch
- external hash reference count

It must provide maintenance actions:

- refresh diagnostics
- sync
- upload pending writes
- verify attachment chunks
- prune automatic snapshots

The diagnostics page should stay concise. Put low-frequency details in a secondary section. Do not dump benchmarks, raw bundles, or low-level payloads into the normal user page.

### 5.10 Unlock And Security

It must support:

- password unlock
- Tiga mode display
- Tiga mode selection or default-mode explanation
- retry / lockout messaging, if implemented
- biometric or system credential wrapping, if supported by the platform

Clients must clearly distinguish:

- user-visible unlock method
- cryptographic key material actually used by the storage layer

Clients must not log master passwords, derived keys, or epoch keys.

## 6. Performance Requirements

### 6.1 Startup And Open

Clients should:

- preload only the currently selected vault
- avoid opening every configured vault at startup
- use stale-while-revalidate caches for list pages
- avoid clearing a list before repopulating it, which causes blank flashes and sort jumps

If a user manages many MDBX vaults, the client must not unlock all vaults, read all history, and scan all attachments on startup.

### 6.2 Writes

Clients should:

- batch writes
- use one transaction for one user-level operation
- use one commit for one user-level operation
- refresh UI incrementally after writes

Clients should not:

- open / close the vault once per item
- create one snapshot per item
- delete and rebuild the full UI cache

### 6.3 Sync

Sync should run in the background and report progress through a status bar or task panel.

At minimum, sync state should include:

- waiting
- uploading
- downloading
- merging
- conflicts pending
- complete
- failed

## 7. Compatibility Requirements

### 7.1 Format Version

Clients must check `format_version` when opening a vault.

If the vault contains an unknown critical extension, the client must refuse writes. Read-only mode is acceptable if it can be guaranteed safe.

### 7.2 Stable IDs

Clients must preserve:

- vault ID
- device ID
- branch ID
- project / folder ID
- entry ID
- attachment ID
- commit ID
- snapshot ID

Clients must not regenerate object IDs from titles, paths, or sort positions.

### 7.3 Time And Ordering

Clients should use ISO-8601 UTC timestamps.

List ordering should be stable. Refreshing data must not randomly reorder the same set of items because rows were re-imported.

## 8. Minimum Test Checklist

Before claiming MDBX support, another client should pass at least these scenarios:

- Create a vault, close it, and reopen it.
- Create an entry in the root directory.
- Create an entry inside a nested folder.
- Create an entry from inside an MDBX subfolder and keep that folder as target.
- Batch move 100 entries into MDBX and create one user-level commit.
- Batch delete 100 entries and create one user-level commit with tombstones.
- Two clients open the same vault and show the same item count.
- One client deletes an item; another client syncs and does not resurrect it.
- Concurrent edits to the same field create a conflict.
- Concurrent edits to different fields auto-merge or present a clear merge prompt.
- Create a manual snapshot.
- Prune automatic snapshots.
- Snapshot rollback requires confirmation.
- Snapshot structure shows folders, with folders before entries.
- Attachment chunk verification failure appears in diagnostics.
- Opening MDBX format management lands on the home screen, not the last database detail page.
- Normal user UI does not expose raw advanced tools.

## 9. Common Mistakes

### 9.1 Writing Current Tables But Not History Tables

Result:

- empty commit history
- unusable snapshots
- conflict detection cannot work
- deleted objects may resurrect

### 9.2 One Commit Per Object

Result:

- huge history after batch operations
- huge snapshot count
- slower sync
- unreadable management UI

### 9.3 Storing Folders Only As Path Strings

Result:

- duplicate folder names collide
- moved paths break
- breadcrumbs are wrong
- cross-client target selection fails

### 9.4 Management Page Auto-Enters Last Vault

Result:

- users tap "format management" but cannot see the management home
- users assume only one database can be managed
- multi-vault workflows become confusing

Correct behavior:

- the password list may remember the current vault
- the format-management entry always opens the MDBX management home
- database detail opens only after explicit user selection

### 9.5 Exposing Developer Tools To Normal Users

Result:

- users see benchmarks, raw bundles, or chunk payloads they cannot interpret
- users may import the wrong payload
- the management page becomes noisy

Correct behavior:

- normal users see sync, conflicts, snapshots, history, and diagnostics / maintenance
- raw bundles, benchmarks, and low-level chunk debugging stay behind developer mode

## 10. Completion Standard

A client may claim "Monica MDBX support" only when it can at least:

- open an MDBX vault created by Monica
- display folders, entries, and attachment metadata correctly
- create, move, and copy entries inside nested folders
- write commits, object versions, and tombstones
- show commit history
- show and revert snapshots
- detect and show conflicts
- show diagnostics / maintenance
- avoid generating excessive commits for batch operations
- show the same item count as another client reading the same vault

If the client can only read data and cannot preserve write history, label it as "MDBX read-only support", not full support.
