# MDBX Security Specification

Version: `MDBX-1-DRAFT`

This document defines the Tiga model, required cryptography, key hierarchy, and memory handling rules.

## 1. Security Objectives

MDBX MUST protect against:

- offline file theft
- untrusted cloud storage
- tampering of encrypted records
- accidental long-lived plaintext exposure in normal workflows
- weak default KDF settings

MDBX cannot fully hide file size, access timing, or overall sync activity from the storage provider.

## 2. Required Algorithms

Baseline recommendations:

- password KDF: `Argon2id`
- authenticated encryption: `XChaCha20-Poly1305` or `AES-256-GCM`
- key derivation: `HKDF-SHA-256`
- hashing: `SHA-256`
- filename or identifier MAC where needed: `HMAC-SHA-256`

The preferred default stack is:

- `Argon2id + HKDF-SHA-256 + XChaCha20-Poly1305`

## 3. Tiga Modes

MDBX MUST expose three user-selectable security modes.

Naming convention:

- `Power Type`
  - strongest-protection mode

- `Multi Type`
  - balanced default mode

- `Sky Type`
  - faster and lighter-weight mode

### 3.1 Power Type

Purpose:

- maximum resistance against offline attack and local leakage

Typical effects:

- highest Argon2id cost
- shorter in-memory secret lifetime
- less plaintext caching
- stronger warnings before export or copy
- stricter attachment handling defaults

### 3.2 Multi Type

Purpose:

- recommended default balance

Typical effects:

- strong Argon2id cost
- moderate caching allowed
- usability features enabled when low risk

### 3.3 Sky Type

Purpose:

- convenience and speed in lower-risk environments

Typical effects:

- lower but still acceptable KDF cost floor
- more permissive caching
- faster unlock and routine operations

## 4. Tiga Mode Scope

Tiga mode MUST support:

- global default mode
- optional project-level override
- optional entry-level override for especially sensitive secrets

A narrower scope override MUST take precedence over a broader one.

## 5. Required User Warnings

When switching to a weaker mode, the UI MUST:

- clearly state the new risk profile
- require explicit confirmation
- show which protections become weaker

## 6. Key Hierarchy

A compliant implementation SHOULD use a layered hierarchy:

- user secret inputs
- master unlock key
- vault key
- purpose keys
- record or object keys

A recommended derivation chain is:

- unlock key from `Argon2id`
- vault key from `HKDF`
- subkeys for metadata, records, attachments, and history

## 7. Record Authentication

MDBX MUST authenticate:

- vault header metadata that affects decryption
- project records
- entry records
- attachment metadata
- attachment content or chunk content
- history records
- snapshot records

Moving ciphertext into the wrong context MUST fail authentication.

## 8. Attachment Security Rules

Attachments are first-class sensitive data.

Therefore MDBX MUST:

- authenticate attachment metadata
- authenticate attachment content
- prevent metadata-only rename from invalidating unrelated content
- verify content hash before trusting attachment reconstruction

If external referenced attachments are supported, the external content MUST still be integrity-bound to the database metadata.

## 9. Memory Safety Rules

Implementations SHOULD:

- minimize plaintext lifetime in memory
- zero sensitive buffers when practical
- avoid logging secrets
- avoid crash dumps with raw secret payloads where possible
- isolate attachment streaming so large files do not remain fully decrypted in memory unnecessarily

## 10. Unlock Factors

MDBX SHOULD clearly distinguish between user-visible unlock methods and the underlying cryptographic secret model.

User-visible unlock methods MAY include:

- `PIN`
- `password`
- `security key`
- biometric unlock wrapper where supported by platform

The actual vault protection boundary SHOULD still be enforced by the master unlock key, vault key, and their derivation chain.

MDBX SHOULD support combinations of:

- master password
- key file
- security key or hardware-backed key material
- biometric unlock wrapper where supported by platform

### 10.1 PIN Unlock

`PIN` MAY be offered as a user-visible fast unlock method.

However, `PIN` SHOULD NOT be treated as the true vault master secret by itself.
A better model is:

- `PIN` unlocks a locally protected wrapping key
- the wrapping key then unlocks the real vault key material

This avoids making a short PIN the only real security boundary.

### 10.2 Password Unlock

`password` is a core unlock method that MDBX SHOULD support strongly.

Password input MUST support Unicode.
This means:

- Chinese passwords MUST be supported
- implementations MUST NOT assume passwords are ASCII-only
- the spec and implementation SHOULD define a stable encoding and normalization strategy

Recommended requirement:

- before entering the KDF, the password should go through a stable Unicode string handling pipeline
- implementations MUST avoid cross-platform differences that make the same Chinese password fail on another device

### 10.3 Security Key Unlock

`security key` SHOULD be supported as one of the unlock methods.

It may be used to:

- provide a hardware-protected unlock factor
- wrap or release locally stored key material
- combine with password or PIN for stronger unlock flows

It SHOULD NOT be described as a mandatory cloud-dependent unlock mechanism.
MDBX MUST remain local-first.

Biometric unlock SHOULD wrap a stronger underlying secret and SHOULD NOT replace the actual cryptographic vault secret model.

## 10.4 Minimum Unlock Capability

A user-facing MDBX implementation SHOULD support at least two of the following three unlock methods, and a full implementation SHOULD support all three:

- `PIN`
- `password`
- `security key`

If an implementation claims password support, that password support MUST include Chinese input.

## 11. Minimum Parameter Philosophy

MDBX MUST define minimum supported security floors.
Even `Sky Type` MUST remain meaningfully secure and MUST NOT degrade into a toy configuration.

The exact parameter table SHOULD be published separately and versioned.

## 12. Audit And Logging Rules

Logs MUST NOT contain:

- plaintext passwords
- TOTP seeds
- passkey private material
- decrypted attachment names unless the user explicitly exports diagnostic data

## 13. Recovery And Rotation

MDBX SHOULD support:

- key rotation
- backup verification
- snapshot verification
- attachment integrity scan

Rotation MUST preserve readability of records until migration is complete.

## 14. Rejection Rules

A security design is non-compliant if it:

- lacks authenticated encryption
- leaves attachment integrity undefined
- allows weaker mode switching without explicit user acknowledgement
- stores long-lived plaintext secrets by default without strong justification
- treats biometric unlock as the only real secret
