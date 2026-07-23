# HTTP XYZ tile adapter

This module supports bounded Java 21 JVM acquisition of fixed-host XYZ PNG/JPEG tiles into detached
EPSG:3857 raster sources. It is JDK-only and AWT-free; applications explicitly provide the encoded
raster decoder registry and perform `fetch` on a worker thread.

The closed profile has no redirects, retries, credentials, proxy use, cookies, compression, disk
cache, live-network `RasterSource`, public-service default, or Native Image claim. A separate evidence
task is required before adding JDK HTTP/TLS/executor behavior to the shared native executable. This
closeout was verified on OpenJDK 21.0.11, Ubuntu 24.04.1 under WSL2, x86-64; that evidence is not a
latency, public-network, security-boundary, or cross-platform performance claim.

The loopback-only automated suite covers bounded concurrent batches, deterministic row-major mosaics,
missing tiles, transactional memory caching, hostile response profiles, cancellation, interruption,
close races, detached rendering, and stable redacted diagnostics.
