# G19-042 — JPEG common-interchange coding and sampling

Status: Proposed
Depends on: G18-061
Gate: G19
Type: HITL

## Goal

Complete the bounded 8-bit Huffman DCT coding and sampling surface commonly exchanged as JPEG while
keeping other JPEG coding families explicitly unsupported.

## Context

The current pure-Java path decodes a bounded baseline/progressive subset, but its supported scan,
sampling, restart, and table combinations have not been closed against ITU-T T.81 / ISO/IEC
10918-1 or an independent common-interchange corpus.

## Scope

- Pin ITU-T T.81 / ISO/IEC 10918-1 and the applicable corrections used by the decoder profile.
- Support 8-bit Huffman baseline, extended sequential, and progressive DCT frames used in common
  interchange, including valid multi-scan component organization.
- Support conforming common component sampling factors, quantization/Huffman table selection,
  restart intervals, byte stuffing, spectral selection, and successive approximation.
- Make coefficient, block, MCU, scan, marker, table, entropy-byte, arithmetic, and output-pixel work
  prospectively bounded and cancellation-aware.
- Return closed stable diagnostics for arithmetic, lossless, differential/hierarchical, non-8-bit,
  and non-JPEG family signatures.

## Out of scope

- Arithmetic coding, lossless JPEG, differential/hierarchical processes, 12-bit or other precision,
  JPEG-LS, JPEG 2000, JPEG XL, encoding, and transcoding.

## Acceptance criteria

- The coding/sampling matrix in `modules/mundane-map-io-image/CAPABILITIES.md` is implemented and
  tested against independent common-interchange decoders.
- Valid scan/table/restart combinations decode deterministically; unsupported SOF/process families
  fail before unbounded coefficient or pixel allocation.
- Malformed entropy, table, scan, marker, dimension, and progressive-refinement inputs fail
  atomically with stable, value-safe diagnostics.

## Required tests

- Baseline, extended sequential, progressive, grayscale/multicomponent, multi-scan, sampling-factor,
  restart, table-remapping, byte-stuffing, and independent-corpus tests.
- Hostile marker lengths, excessive scans/blocks, invalid refinement, truncation, cancellation,
  allocation, unsupported-process, and decompression-dimension tests.

## Validation

Run `./gradlew :modules:mundane-map-io-image:check --console=plain`, its approved JPEG corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact T.81/ISO edition, declared process boundary,
corpus provenance/licensing, and independent-decoder evidence before completion.
