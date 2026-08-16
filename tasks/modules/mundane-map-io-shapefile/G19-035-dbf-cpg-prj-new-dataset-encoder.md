# G19-035 — DBF/CPG/PRJ new-dataset encoder

Status: Proposed
Depends on: G19-032, G19-033, G19-034
Gate: G19
Type: HITL

## Goal

Encode a portable attribute, text-encoding, and CRS sidecar set for a newly created Shapefile without
silent value or schema loss.

## Context

Shapefile geometry is not useful interchange if its DBF rows, encoding declaration, and CRS cannot be
read consistently. The portable exporter intentionally emits a narrower DBF profile than the reader
accepts and does not emit memo fields.

## Scope

- Freeze one portable DBF output version and its supported `C`/`N`/`F`/`L`/`D` schema/value mapping.
- Preflight one DBF row per SHP record, field-name uniqueness/length, widths, decimal scales, dates,
  nulls, numeric ranges, encoded byte lengths, row/file sizes, and deterministic record order.
- Require an explicit supported output encoding, reject unmappable text, and emit a canonical CPG
  declaration consistent with DBF bytes and the language-driver policy.
- Emit a deterministic pinned OGC or ESRI WKT 1 PRJ representation only when the declared CRS can be
  represented losslessly under the approved profile.
- Define strict caller-visible errors and opt-in mappings for project attribute types that have no
  direct portable DBF representation.

## Out of scope

- Memo/DBT output, arbitrary metadata preservation, encoding replacement, CRS guessing, lossy schema
  inference, or general dBASE updates.

## Acceptance criteria

- Exported DBF/CPG/PRJ components reopen with identical declared schema, supported values, encoding,
  row alignment, and CRS semantics through this module and independent GIS software.
- Unsupported names/types/values/CRSs fail before component publication unless the caller supplied a
  documented explicit lossless mapping.
- Output bytes, dates, numbers, nulls, field order, and WKT are locale/time-zone/platform independent.

## Required tests

- Golden and round-trip schema/value/null/date/numeric/text/code-page/CRS fixtures with two
  independent readers.
- Field collision/width/scale/overflow, unmappable text, unsupported type/CRS, row mismatch,
  cancellation, short-write, size limit, deterministic-byte, and cleanup tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved writer corpus
lane, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the portable DBF version, schema mapping, encoding policy,
WKT output dialect, and independent interoperability evidence before completion.
