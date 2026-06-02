# MDBX Roadmap And Acceptance

Version: `MDBX-1-DRAFT`

This document defines what to build first, what can wait, how to validate completion, and how to assign work to lower-capability models.

## 1. Delivery Strategy

Build MDBX in layers.
Do not begin with UI-first work.
Do not begin with plugin systems.
Do not begin with speculative advanced sync infrastructure.

Correct order:

1. data model and file format
2. storage engine and encrypted persistence
3. history and conflict detection
4. import and export bridges
5. benchmarks and recovery tests
6. reference UI and client integration

## 2. MVP Scope

MVP MUST include:

- single `.mdbx` vault file
- SQLite-backed internal storage
- encrypted project records
- encrypted entry records
- attachment schema and at least one functioning attachment storage mode
- Tiga three-mode support
- append-friendly history records
- basic conflict detection metadata
- snapshot or equivalent recovery checkpoint
- KDBX import
- KDBX export or clearly scoped export bridge plan
- benchmark harness against KDBX
- one reference client implementation

## 3. Post-MVP Scope

Later phases SHOULD add:

- advanced merge UI
- richer CRDT text handling
- chunked large-attachment optimization
- external attachment hash reference mode
- multi-platform clients
- browser extension autofill integration
- plugin sandbox
- audit tooling

## 4. Required Workstreams

Every roadmap or ticket plan SHOULD map work into these tracks:

- `format`
- `storage`
- `sync`
- `security`
- `migration`
- `benchmarks`
- `reference-client`
- `docs`

## 5. Low-End Model Task Template

Use this template when assigning a task to a weaker model.

### Goal

State exactly what must be produced.

### Inputs

List the governing spec files in `docs/`.

### Constraints

List non-negotiable rules.

### Deliverables

List exact output files or modules.

### Acceptance Criteria

List tests and conditions that must pass.

### Forbidden Changes

List what must not be changed or invented.

## 6. Example Task Breakdown

### Task A: schema draft

Goal:

- define the first SQLite schema for projects, entries, attachments, commits, and tombstones

Acceptance:

- schema includes `projects`
- schema includes `attachments`
- `entries` references `project_id`
- attachment ownership and hash fields exist
- no table design assumes flat ungrouped passwords

### Task B: basic encrypted storage layer

Goal:

- implement record encryption and authenticated persistence for projects and entries

Acceptance:

- can create vault
- can create project
- can create entry under project
- can reopen and read both
- authentication failure is detected on tamper

### Task C: attachment MVP

Goal:

- implement metadata plus one small-file embedded attachment mode

Acceptance:

- attachment can be added to project
- attachment metadata survives reopen
- integrity mismatch is detected
- project metadata edit does not rewrite attachment payload logic unnecessarily

## 7. Benchmark Requirements

MDBX MUST produce benchmark evidence for:

- small edit save latency
- vault open latency
- search latency
- attachment add latency
- sync delta size after small metadata edit
- sync delta size after attachment rename
- sync delta size after attachment content replacement

MDBX results MUST be compared against KDBX baselines where feasible.

## 8. Recovery Test Matrix

The verification plan MUST include:

- interrupted save
- interrupted sync
- corrupted metadata page
- corrupted attachment chunk
- stale device head
- concurrent same-field secret edit
- concurrent different-field edit
- project delete and restore
- attachment delete and restore

## 9. Compatibility Test Matrix

The verification plan SHOULD include:

- older reader opens newer non-critical vault
- newer reader opens older vault
- unknown fields preserved through rewrite
- KDBX import round-trip sanity checks
- attachment metadata retained through export and import where supported

## 10. Done Criteria For The Spec Phase

The spec phase is only complete when all of the following exist:

- stable terminology
- mandatory project-based model
- mandatory attachment model
- security mode definition
- storage and sync rules
- roadmap and acceptance rules
- enough clarity that another model can implement without inventing core architecture

## 11. Rejection Rules

A roadmap or task plan is unacceptable if it:

- postpones `project` modeling until later
- postpones `attachment` modeling until later
- makes performance claims without benchmark plan
- ignores recovery testing
- leaves KDBX migration unspecified
