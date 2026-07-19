# G10-053 — Native Image GPX smoke

Status: Proposed
Depends on: G10-052
Gate: G10
Type: HITL

## Goal

Prove the bounded GPX parser, source, query, and rendering path on the required Linux Native Image
lane.

## Context

G10-052 closes JVM behavior and fixtures. The project treats Native Image compatibility as an
architectural requirement and requires explicit resource registration rather than inferred parser or
resource reachability.

## Scope

Extend the existing native executable and exact resource inventory with a tiny GPX fixture. Exercise
direct hardened StAX construction, one successful query/render path, one ignored-data warning, one
stable malformed outcome, and cleanup. Record the bounded Linux evidence and audit prohibited
mechanisms.

## Out of scope

Windows/macOS claims, general XML parser compatibility, dynamic resource discovery, reachability
metadata generated from execution, KML, and performance claims.

## Acceptance criteria

- `nativeSmoke` opens, queries, and renders the explicitly registered GPX fixture with the same
  observable result as the JVM path.
- Native execution retains one approved warning and one exact malformed diagnostic and releases all
  source/cursor resources.
- No reflection, service/classpath scanning, internal JDK API, dynamic proxy, or implicit resource
  lookup is required.
- The review records the exact Linux GPX Native Image support statement without implying broader
  platform or general XML support.

## Required tests

Native valid query/render, warning, malformed diagnostic, cleanup, explicit-resource inventory, and
architecture tests.

## Validation

```bash
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GPX Linux Native Image evidence review**. The maintainer must approve the
recorded executable evidence and bounded platform claim.
