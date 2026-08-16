# G19-171 — Workspace resource identity, reference, and authority model

Status: Proposed
Depends on: G19-050, G19-170
Gate: G19
Type: HITL

## Goal

Represent local, packaged, and remote workspace resources without granting filesystem, network, decoder,
or credential authority.

## Context

Complete projects reference many source/style/catalog/resource types. Paths and URLs alone cannot express
stable identity, multi-file closure, authorization, ownership, or portable packaging decisions safely.

## Scope

- Define stable workspace resource IDs, kinds, media/profile, location modes, content identity,
  adapter/options versions, dependencies and explicit `EMBED`/`REFERENCE` intent.
- Model guarded relative local references, package entry references and inert authorized-HTTPS service/
  resource selections with non-secret request policy and optional opaque credential aliases.
- Add explicit registries for source/resource adapter descriptors, complete multi-file/sidecar closure,
  authorization, credential resolution, decoder/catalog binding and immutable typed options.
- Preflight dependency cycles, aliases, duplicates, missing/incompatible resources, authority, limits and
  ownership before opening/fetching; keep paths/URIs/secrets out of public diagnostics.

## Out of scope

- Secrets, credential-store locations, ambient lookup, workspace-granted authority, automatic fetch,
  decoder/plugin discovery, sidecar guessing, transcode, reprojection, or resource execution.

## Acceptance criteria

- Every supported resource can declare a complete explicit identity/reference/closure without adapter-type leakage.
- Opening does no filesystem/network/credential/decoder work before host authorization and full preflight.
- Malicious references, aliases, cycles and registry collisions fail stably without leaking attacker values.

## Required tests

- Local/package/HTTPS/reference/embed/resource-set/sidecar/dependency/adapter/options/credential-alias matrices.
- Path traversal/symlink/case/Unicode/replacement, URI/redirect/header/credential tricks, cycles/duplicates,
  missing/incompatible resources, registry races, limits, cancellation, ownership and no-secret-leak tests.

## Validation

Run workspace reference/security and HTTP-policy integration checks, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve reference forms, authorization/credential-alias boundary, resource-closure SPI and
diagnostic redaction before portable package or remote restore work.
