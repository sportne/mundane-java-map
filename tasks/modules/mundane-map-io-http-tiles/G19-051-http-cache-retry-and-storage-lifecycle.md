# G19-051 — HTTP cache, retry, and storage lifecycle

Status: Proposed
Depends on: G19-050
Gate: G19
Type: HITL

## Goal

Implement a bounded RFC 9111 cache and idempotent resilience profile for tile and metadata retrieval.

## Context

The current decoded-memory cache has no HTTP freshness, validators, `Vary`, negative entries, stale
policy, retry/backoff, persistent encoded storage, integrity metadata, or crash recovery.

## Scope

- Pin applicable RFC 9110/9111 cache-control, age, validator, conditional request, `Vary`, range,
  status, warning, and invalidation behavior.
- Add deterministic freshness/revalidation for `ETag` and `Last-Modified`, bounded negative caching,
  and explicitly approved `stale-while-revalidate`/`stale-if-error` behavior.
- Add cancellation-aware bounded retry/backoff for approved idempotent connection/status failures,
  including capped `Retry-After`, jitter policy, and one aggregate attempt/deadline budget.
- Coordinate encoded and decoded memory caches and an optional caller-selected disk cache with exact
  ownership, byte/entry ceilings, integrity, atomic entries, eviction, cleanup, and crash recovery.
- Define cache keys across URI normalization, method, selected request headers, `Vary`, content
  negotiation, credentials, decoder/profile identity, and authorization partitions.
- Provide observable cache/retry results without exposing secrets or unbounded server values.

## Out of scope

- A shared system/browser cache, unbounded offline mirroring, non-idempotent retries, or an ambient
  default disk location.

## Acceptance criteria

- Conditional/freshness/stale decisions match the pinned RFC profile under deterministic clocks.
- Retry never exceeds the configured attempts, delay, deadline, bytes, or cancellation boundary.
- Cache corruption, partial writes, concurrent readers, eviction, close, and process restart cannot
  expose partial content, cross credentials, or leak files/locks/resources.

## Required tests

- Scripted validator/freshness/age/`Vary`/negative/stale/range/status/`Retry-After` and retry matrix.
- Deterministic-clock, cache-key partition, corruption, disk-full, atomic-write, eviction, crash-
  recovery, concurrency, cancellation, ownership, cleanup, and sensitive-diagnostic tests.

## Validation

Run `./gradlew :modules:mundane-map-io-http-tiles:check --console=plain`, its cache/network fixture
lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the RFC cache/retry profile, persistent-cache filesystem
claims, deterministic evidence, and security partitioning before completion.
