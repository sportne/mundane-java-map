# G19-200 — Supported Native Image host and toolchain matrix

Status: Proposed
Depends on: G19-192
Gate: G19
Type: HITL

## Goal

Freeze and continuously build the exact host, architecture, GraalVM/JDK, compiler, libc, and CPU-baseline matrix described by the native capability profile.

## Context

The current workflow proves only Ubuntu x86-64. A Native Image claim is not portable merely because one host builds, and a cross-compiled or emulated result is not equivalent to execution on the advertised host.

## Scope

- Pin the reviewed GraalVM/JDK and Native Build Tools versions plus each host's native compiler and runner image.
- Build and execute on Linux x86-64/AArch64, macOS x86-64/AArch64, and Windows x86-64 native runners.
- Use a compatibility-oriented CPU target and record the effective architecture, compiler, linker, libc/system-library, and image options.
- Support standard dynamically linked host executables only; reject accidental static, mostly-static, musl, cross-compilation, or host-native CPU release profiles.
- Make a failed, skipped, unavailable, or substituted required matrix row block the native support gate.

## Out of scope

- Windows AArch64, other hosts, musl, static/mostly-static images, native shared libraries, and cross-compilation support claims.

## Acceptance criteria

- Every required row builds and executes the same versioned smoke entry point on its native host.
- Evidence identifies the exact toolchain and linkage profile and proves the executable uses the declared portable CPU baseline.
- Unsupported rows and linkage profiles are documented without being implied by a generic “Native Image compatible” claim.

## Required tests

- Five-row CI build/run matrix, toolchain manifest checks, executable/linkage inspection, and a synthetic missing-row gate failure.
- Negative configuration tests for `-march=native`, static/musl, and unapproved target substitution.

## Validation

Run the complete host CI matrix, `./gradlew nativeSmoke --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve the pinned host/toolchain manifest and evidence from each native runner.
