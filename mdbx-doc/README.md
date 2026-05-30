# MDBX Spec Index

This folder contains the canonical specification set for the MDBX project.

MDBX is a local-first encrypted password database format and reference architecture.
Its design goals are:

- local-first operation
- long-term archival stability
- Git-like conflict prevention and recovery
- significantly better sync behavior than KDBX in cloud-drive workflows
- security modes through the Tiga model
- project-oriented password organization
- native attachment support

## Chinese Documents

Chinese versions are provided for direct review and planning:

- `README.zh-CN.md`
- `00-agent-rules.zh-CN.md`
- `01-product-spec.zh-CN.md`
- `02-storage-sync-spec.zh-CN.md`
- `03-security-spec.zh-CN.md`
- `04-roadmap-acceptance.zh-CN.md`

## Reading Order

If you are a lower-capability implementation model, read these files in order and obey them strictly:

1. `00-agent-rules.md`
2. `01-product-spec.md`
3. `02-storage-sync-spec.md`
4. `03-security-spec.md`
5. `04-roadmap-acceptance.md`

If you are a Chinese-speaking reviewer, read these files in order:

1. `00-agent-rules.zh-CN.md`
2. `01-product-spec.zh-CN.md`
3. `02-storage-sync-spec.zh-CN.md`
4. `03-security-spec.zh-CN.md`
5. `04-roadmap-acceptance.zh-CN.md`

## Document Roles

- `00-agent-rules.md`
  - Execution rules for implementation agents.
  - Defines what you must not invent.

- `01-product-spec.md`
  - Product goals, invariants, domain model, object model, and user-visible behavior.

- `02-storage-sync-spec.md`
  - Single-file container, internal database layout, incremental writes, delta sync, conflict model, and attachment storage rules.

- `03-security-spec.md`
  - Tiga modes, cryptography, key hierarchy, memory handling, and security constraints.

- `04-roadmap-acceptance.md`
  - MVP scope, later phases, acceptance criteria, test matrix, and benchmark requirements.

## Non-Negotiable Principles

Every implementation and every design choice MUST preserve all of the following:

- Local-first
- Long-term readability and migration friendliness
- Forward and backward compatibility
- No mandatory central server
- Project-oriented password storage
- Native attachment capability
- Safer sync and conflict behavior than KDBX
- Better cloud-drive performance than KDBX

## Core Vocabulary

- `vault`
  - One MDBX database file.

- `project`
  - The top-level logical container for a real-world account, service, website, app, organization, identity set, or working set of secrets.

- `entry`
  - A concrete secret-bearing record inside a project, such as login, note, card, identity fragment, key, or token.

- `attachment`
  - A file or binary payload referenced by a project or entry.

- `tiga mode`
  - One of `Power Type`, `Multi Type`, or `Sky Type`.
  - Semantic mapping: `Power Type` = strongest protection, `Multi Type` = balanced default, `Sky Type` = faster and lighter-weight use.

- `oplog`
  - Append-only change history used for sync and recovery.

- `snapshot`
  - A compact recoverable state image.

## Required Output Style For Future Specs

When adding more spec files to this folder:

- use RFC-style requirement words: `MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, `MAY`
- make each requirement testable
- separate normative requirements from advice
- include examples only after rules
- never leave core data model ambiguity unresolved

## Scope Boundary

This folder defines the spec and implementation guidance.
It does not contain production code.
Production code must follow this folder, not redefine it.
