# Agent Execution Rules For MDBX

This file tells a lower-capability model how to work on MDBX without making destructive or low-quality decisions.

## Primary Goal

Produce work that is compatible with the rest of this spec set.
If any ambiguity appears, resolve it by preserving:

1. local-first behavior
2. long-term format stability
3. project-oriented storage
4. native attachment support
5. safe mergeability
6. better sync performance than KDBX

## Absolute Rules

You MUST:

- read `README.md` first
- read all numbered spec files before proposing architecture changes
- treat this folder as the source of truth
- keep terminology stable
- keep requirements testable
- prefer simpler designs when both satisfy the spec
- preserve backward compatibility in every revision
- preserve unknown fields when reading and writing structured records
- keep secrets encrypted at rest and authenticated

You MUST NOT:

- invent new core object types unless a spec update explicitly adds them
- replace `project` with a flat-entry-only model
- make attachments an afterthought or optional bolt-on with no schema support
- require a server for normal open, edit, save, import, export, merge, or recovery
- rely on whole-file rewrite for every small edit
- silently auto-merge conflicting secret values in the same field
- weaken long-term compatibility for short-term implementation convenience
- write design text that says `TBD` for a core behavior

## Required Working Method

When implementing or planning work:

1. identify which file in this folder governs the task
2. list the invariants affected
3. design the smallest compliant solution
4. define data shapes before APIs
5. define persistence before UI
6. define conflict behavior before sync code
7. define acceptance checks before calling the task done

## Required Domain Assumptions

Unless a later spec explicitly overrides them, assume all of the following:

- passwords are organized by `project`
- each project can contain multiple entries
- a project can have zero or more attachments
- an entry can also have zero or more attachments
- attachments are first-class database data, not undocumented side files
- sync providers are untrusted
- cloud-drive listing and rename behavior may be non-atomic
- the database must remain usable offline
- the format must be documented well enough for third-party implementations

## Required Spec Writing Style

When creating new spec text:

- lead with normative rules
- use short sections with stable headings
- define terms once and then reuse them consistently
- include explicit reject cases
- include acceptance criteria
- include migration implications if the change affects compatibility

## Decision Order

If you must choose between two designs, use this priority order:

1. security correctness
2. data durability and recoverability
3. compatibility stability
4. local-first behavior
5. sync friendliness
6. performance
7. implementation simplicity
8. UI convenience

## When A Requirement Conflicts With Performance

If a faster design harms durability, recoverability, compatibility, or merge safety, reject it.
MDBX is allowed to be complex internally if that complexity protects long-term reliability.

## When A Requirement Conflicts With Simplicity

Choose the simpler design only if:

- it does not weaken security
- it does not weaken compatibility
- it does not weaken sync behavior
- it does not remove project-based storage
- it does not remove native attachment capability

## Required Deliverable Shape For Tasks

When breaking MDBX work into tasks, every task SHOULD contain:

- goal
- files or modules affected
- assumptions
- non-goals
- implementation steps
- edge cases
- tests
- acceptance criteria

## Definition Of Failure

A proposed solution fails this spec if it does any of the following:

- rewrites the entire vault file for a tiny metadata edit in the normal case
- stores passwords as an unstructured flat list with no project container
- treats attachments as external ad hoc files with no tracked metadata and integrity rules
- cannot explain how concurrent edits are detected
- cannot explain how partial corruption is recovered from
- cannot explain compatibility behavior for future versions
