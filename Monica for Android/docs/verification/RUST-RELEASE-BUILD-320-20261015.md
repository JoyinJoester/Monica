# Rust tool discovery / Release verification — 1.0.320

Date: 2026-10-15. Windows host, Android Studio JBR, Gradle 8.9,
Cargo 1.86.0, cargo-ndk 4.1.2. No runtime application logic was changed.

## Failure and fix

The supplied build failed in `buildMonicaRustJniFromSource` because the Gradle
process could not launch `cargo` (`CreateProcess error=2`). Removing Rust's bin
directories from PATH reproduced that exact failure before the patch. Rust was
already installed. The R8 Kotlin metadata warnings were not the fatal error.

The launcher resolves an explicit `-PcargoExecutable=<path>` first, then PATH,
then CARGO_HOME/bin (or the user's .cargo/bin). It keeps rustup shim filenames
intact, supplies the tool directory to child processes, and uses the Android
plugin's SDK location when SDK environment variables are absent. Explicit
SDK/NDK settings are preserved. Missing tools fail with installation/configuration
guidance; no prebuilt-library fallback or automatic tool installation was added.
The ordinary JNI task and both F-Droid source-build tasks use the helper.

## Verification

| Check | Result |
| --- | --- |
| Original task with Rust removed from PATH, before fix | Reproduced original Cargo launch failure |
| Ordinary `:app:assembleRelease`, Rust removed from PATH and SDK variables removed | Passed; ARM32 and ARM64 packages |
| F-Droid `:app:assembleRelease -PfdroidUniversal=true`, Rust removed from PATH | Passed; ARM32, ARM64 and x86_64 libraries |
| Final launcher native tasks after preserving shim filenames | Passed in both editions |
| Real Cargo and cargo-ndk execution with default-home fallback | Passed |
| PATH lookup and preservation of explicit SDK/NDK settings | Passed |
| Custom CARGO_HOME with spaces and rustup shims | Passed |
| Valid explicit Cargo path; invalid explicit Cargo path | Valid path ran; invalid path rejected with actionable error |
| APK ZIP integrity and JNI/MDBX ELF contents against stripped build outputs | Passed for all packaged architectures |
| F-Droid APK signature verification | Passed |
| Candidate Gradle configuration with a deliberately missing explicit Cargo path | Passed; configuration does not require Rust execution |

Build commands used `--no-daemon --offline --max-workers=2`; native-only final
checks used `--rerun-tasks`. The final launcher adjustment preserves symlink
filenames; native tasks were rerun afterward, without repeating unchanged
Kotlin/R8 work. Artifact hashes and launcher source hashes are in the companion
JSON. Local raw logs and executable probes are retained under
`.codex-tasks/rust-release-build-320/` in the parent workspace.

## Scope and limits

Ordinary Release APKs remain unsigned by the existing Gradle configuration;
the signature checker correctly rejected them. No signing policy was changed.
F-Droid's local signature check is not an official F-Droid release verification.
Existing nonfatal R8 metadata warnings remain. This change was tested on Windows,
not on Linux/macOS or an Android device, because it changes the build launcher.

Both 1.0.320 unreleased notes and F-Droid fastlane 27 were updated. The local
F-Droid integration candidate received the same launcher fix and a configuration
check while preserving its other changes. The ordinary integration candidate
has no Gradle Rust source-build launcher, so no new pipeline was transplanted.
No screenshots, native binaries, APKs, release tags or published releases were added.
