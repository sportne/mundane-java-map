# G19-203 — Platform-sensitive JDK services and offscreen rendering

Status: Proposed
Depends on: G19-202
Gate: G19
Type: AFK

## Goal

Prove that the supported host matrix behaves consistently at the JDK, operating-system, network, filesystem, and headless Java2D boundaries used by native-targeted modules.

## Context

Charset, path, locale, timezone, XML, compression, TLS, and offscreen rendering behavior can differ even when the same closed-world image builds successfully.

## Scope

- Test UTF-8 and registered legacy charsets, Unicode filenames, separators, case sensitivity, reserved names, long paths, temporary files, locks, atomic replacement, and cleanup.
- Test fixed locale/timezone behavior, XML provider hardening, ZIP/deflate, image codecs, and explicit resource lookup.
- Test loopback HTTP/TLS policy, redirects, cancellation, timeouts, certificate failure, and resource closure without relying on the public internet.
- Test headless/offscreen Java2D fonts, color, transforms, raster output, and deterministic tolerant render oracles without claiming native desktop-window support.
- Partition truly platform-specific expectations explicitly while preserving shared stable diagnostics and bounds.

## Out of scope

- Swing windowing, operating-system UI integration, arbitrary installed fonts, or live external-service tests.

## Acceptance criteria

- Every used platform-sensitive JDK service has successful and failure evidence on each supported row.
- Host differences are intentional, named, bounded, and do not alter the public semantic or diagnostic contract.
- Files, sockets, threads, images, and temporary resources return to baseline after success, failure, and cancellation.

## Required tests

- Cross-platform filesystem/charset/locale/timezone/XML/compression/resource/TLS matrix.
- Headless Java2D tolerant rendering and exact cleanup/leak assertions on every host.

## Validation

Run the platform-service native matrix, `./gradlew nativeSmoke --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.
