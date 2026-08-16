# G19-115 — Controlled KML network links

Status: Proposed
Depends on: G19-050, G19-051, G19-112, G19-114
Gate: G19
Type: HITL

## Goal

Implement explicitly authorized, bounded KML `NetworkLink`/`Link` retrieval, refresh, view templating,
`NetworkLinkControl`, caching, and lifecycle behavior.

## Context

Network links are a major KML capability but naïve execution creates SSRF, credential propagation,
refresh storms, recursive document growth, cache confusion, and stale-state races.

## Scope

- Parse/preserve all standard Link/NetworkLink/NetworkLinkControl values, refresh/view-refresh modes,
  intervals/scales/bounds/templates, expiry/minimum refresh/session length, visibility/fly-to, and safe metadata.
- Route KML/KMZ retrieval only through the shared explicit HTTP authority/cache/retry/validator/cancellation policy.
- Define template substitution/encoding, view/time/bounds parameters, redirects/origins, cookies/query additions,
  credential/referrer policy, media sniffing, cache partitioning, and same-document/KMZ links.
- Schedule on-change/interval/expire/view-stop/region refresh deterministically with coalescing, generation checks,
  minimum periods, maximum sessions, link depth/fan-out/documents/requests/bytes/state limits, and teardown.
- Stage complete linked scenes/resources atomically; retain or remove prior content according to stable failure policy.

## Out of scope

- Ambient network/filesystem access, unrestricted URI templates, indefinite refresh, server hosting, and remote mutation.

## Acceptance criteria

- Authorized links refresh at declared bounded times and unauthorized/stale/recursive work cannot reach the network or scene.
- Cache/validator/redirect/template/region behavior is deterministic and lifecycle cleanup ends all scheduled/request work.
- Terminal and transient failures publish stable bounded diagnostics without partial linked-document replacement.

## Required tests

- Scripted HTTP/KMZ servers covering all refresh/view/template/cache/validator/redirect/media/failure paths.
- SSRF/origin/credential/query/depth/fan-out/rate/bytes/session/cancellation/detach/close/stale-generation tests.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, network/OGC corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves authority/template/credential/refresh policies and network evidence.
