# MDBX Product Specification

Version: `MDBX-1-DRAFT`

This document uses `MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, and `MAY` in the RFC 2119 sense.

## 1. Abstract

MDBX is a local-first encrypted password database format and implementation architecture.
It is designed to replace the weak points of KDBX in cloud-drive sync, conflict handling, large libraries, attachment scaling, and long-term maintainability.

MDBX MUST organize passwords by `project` and MUST provide native database-level support for attachments.
These two properties are baseline architecture constraints.

## 2. Product Goals

MDBX MUST achieve all of the following:

- open and work fully offline
- remain readable and migratable after long periods of time
- make small edits cheap to save and cheap to sync
- avoid the KDBX pattern of frequent full-file rewrite
- support multiple devices editing the same vault with explicit conflict handling
- support attachments without exploding normal edit cost
- support modern security defaults and tunable user security modes

## 3. Non-Goals

MDBX does not require:

- a central sync server
- a proprietary cloud backend
- always-online collaboration
- hidden undocumented binary formats
- perfect metadata secrecy from cloud providers

## 4. Core Principles

### 4.1 Local-First

An MDBX implementation MUST allow create, read, update, delete, search, export, backup, and restore operations without a server.

### 4.2 4ever And 4ever

The format MUST prioritize long-term stability.
New versions MUST read older vaults.
When possible, older implementations SHOULD safely ignore unknown non-critical additions.
Implementations MUST NOT intentionally strand user data during migrations; data safety takes priority over convenience, cleanup, or format simplification.

### 4.3 Tiga Security Modes

MDBX MUST support three security modes:

- `power`
- `multi`
- `sky`

These modes MUST affect cryptographic parameters, caching behavior, and convenience features.
Compatibility display names MAY use `Power Type`, `Multi Type`, and `Sky Type`.
Semantic mapping: `power` = strongest protection, `multi` = balanced default, `sky` = flexible and portable while still secure.
Details are defined in `03-security-spec.md`.

### 4.4 Git-Like Conflict Prevention

MDBX MUST keep enough local history and causality metadata to detect concurrent edits and safely merge non-conflicting changes.

### 4.5 Performance Superiority Over KDBX

MDBX MUST target noticeably faster save, load, search, and cloud sync behavior than KDBX for real-world vaults.

## 5. KDBX Pain Points MDBX Must Solve

MDBX exists specifically to improve these areas:

- sync conflicts and merge difficulty
- full-database corruption blast radius
- full-file rewrite cost
- large attachment bloat
- cross-client ambiguity
- insufficient default hardening in some ecosystems
- poor multi-device edit ergonomics
- large-vault performance degradation

## 6. Primary Domain Model

### 6.1 Project-Centric Storage

Passwords MUST be stored by `project`.
A flat unordered bag of password entries is not a compliant primary model.

A `project` is the main user-facing unit.
Examples:

- one website account
- one company workspace
- one bank relationship
- one app account
- one infrastructure environment
- one personal identity bundle

A project MAY contain:

- one or more login entries
- one or more secret notes
- one or more cards or identity records
- one or more TOTP or passkey records
- zero or more attachments
- tags, labels, or group membership metadata

### 6.2 Entry Model

An `entry` is a typed record inside a project.
Recommended entry types:

- `login`
- `note`
- `card`
- `identity`
- `totp`
- `passkey`
- `ssh-key`
- `api-token`
- `document-ref`

An implementation MAY store some project data directly on the project object for fast display, but MUST preserve the project-entry hierarchy.

### 6.3 Attachment Model

MDBX MUST reserve native database structures for attachments from version 1.
Attachments MUST NOT be treated as an undefined future extension.

Each attachment MUST have:

- stable attachment ID
- owning project ID
- optional owning entry ID
- encrypted metadata
- integrity hash
- size metadata
- media type metadata
- storage mode metadata
- creation and update metadata

Attachment storage modes MAY include:

- embedded small attachment
- chunked embedded attachment
- external referenced attachment with content hash

The metadata schema for attachments MUST exist even if an MVP implementation supports only a subset of storage modes.

## 7. Required Object Types

The minimum object model MUST include:

- `project`
- `entry`
- `attachment`
- `tombstone`
- `commit`
- `branch-ref`
- `device-ref`
- `snapshot`

Recommended additional object types:

- `group`
- `conflict`
- `key-epoch`
- `audit-event`
- `plugin-state`

## 8. Project Object Requirements

A `project` object MUST include enough fields to support:

- stable identity
- display title
- description or note reference
- group or folder assignment
- tags
- favorite or pinned state
- archive or trash state
- current object version metadata
- attachment summary metadata

A project SHOULD be renderable in the UI without decrypting every child entry.

## 9. Entry Object Requirements

An `entry` object MUST include:

- stable entry ID
- parent project ID
- entry type
- encrypted field payload
- field-level or record-level version metadata
- creation and modification metadata
- delete state

If the entry type contains secret fields, conflicting concurrent writes to the same secret field MUST NOT be silently auto-merged.

## 10. Attachment Object Requirements

An `attachment` object MUST support at least the following metadata fields:

- `attachmentId`
- `projectId`
- `entryId` nullable
- `fileName` encrypted
- `mediaType` encrypted or privacy-safe encoded
- `originalSize`
- `storedSize`
- `contentHash`
- `storageMode`
- `chunkCount`
- `createdAt`
- `updatedAt`
- `deleted`

MDBX SHOULD prefer attachment metadata stability even if the binary content storage strategy evolves over time.

## 11. User Experience Requirements

A compliant product SHOULD expose the following visible concepts:

- current Tiga mode
- vault sync state
- project list
- project details
- attachment presence
- history or version state
- conflict state when relevant

The user MUST be able to understand which secrets belong to which project.

## 12. Import And Migration

KDBX import MUST map source items into the project model.

Default mapping rule:

- one imported logical item becomes one `project`
- the main password/login payload becomes one `entry`
- notes, TOTP, passkeys, cards, and extra material become sibling entries or typed child records under the same project
- imported files become `attachment` objects

If a source format lacks explicit project semantics, the importer MUST still construct projects deterministically.

## 13. Compatibility Rules

Future MDBX versions MUST preserve these invariants:

- project-based storage remains first-class
- attachment metadata remains first-class
- lower versions can detect unsupported critical extensions
- unknown non-critical fields can be preserved
- old vaults remain readable before, during, and after migrations
- portable unlock paths remain available unless the user deliberately chooses a stricter Tiga policy

## 14. Rejection Rules

A design is non-compliant if it does any of the following:

- models the vault only as entries with no project container
- leaves attachment handling unspecified
- requires whole-vault rewrite for routine small edits
- depends on a server to resolve normal local state
- resolves same-field secret conflicts silently
