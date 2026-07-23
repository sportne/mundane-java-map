package io.github.mundanej.map.io.http.tiles;

/**
 * Immutable ceilings for HTTP XYZ templates, requests, responses, and retained state.
 *
 * @param templateCharacters maximum template or resolved-URI characters
 * @param zoom maximum XYZ zoom
 * @param tilesPerRequest maximum tiles in one future region request
 * @param regionAxisTiles maximum future region width or height in tiles
 * @param concurrency maximum future concurrent request count
 * @param responseHeaders maximum response-header values
 * @param headerNameOrValueCharacters maximum characters in one header name or value
 * @param aggregateHeaderCharacters maximum aggregate response-header characters
 * @param responseBodyBytes maximum encoded bytes for one tile
 * @param cumulativeResponseBytes maximum encoded bytes for one operation
 * @param ownedBytes maximum conservatively owned operation bytes
 * @param warnings maximum retained warnings
 * @param cacheEntries maximum future cache entries
 * @param cacheBytes maximum future cache bytes
 */
public record HttpXyzLimits(
        int templateCharacters,
        int zoom,
        int tilesPerRequest,
        int regionAxisTiles,
        int concurrency,
        int responseHeaders,
        int headerNameOrValueCharacters,
        int aggregateHeaderCharacters,
        int responseBodyBytes,
        long cumulativeResponseBytes,
        long ownedBytes,
        int warnings,
        int cacheEntries,
        long cacheBytes) {
    private static final HttpXyzLimits DEFAULTS =
            new HttpXyzLimits(
                    2_048,
                    22,
                    64,
                    32,
                    4,
                    64,
                    4_096,
                    16_384,
                    2 * 1_024 * 1_024,
                    67L * 1_024 * 1_024,
                    268L * 1_024 * 1_024,
                    256,
                    64,
                    16L * 1_024 * 1_024);

    /** Validates the approved hard envelope. */
    public HttpXyzLimits {
        requireRange(templateCharacters, 1, 8_192, "templateCharacters");
        requireRange(zoom, 0, 22, "zoom");
        requireRange(tilesPerRequest, 1, 1_024, "tilesPerRequest");
        requireRange(regionAxisTiles, 1, 128, "regionAxisTiles");
        requireRange(concurrency, 1, 16, "concurrency");
        requireRange(responseHeaders, 1, 256, "responseHeaders");
        requireRange(headerNameOrValueCharacters, 1, 16_384, "headerNameOrValueCharacters");
        requireRange(aggregateHeaderCharacters, 1, 65_536, "aggregateHeaderCharacters");
        requireRange(responseBodyBytes, 1, 16 * 1_024 * 1_024, "responseBodyBytes");
        requireRange(cumulativeResponseBytes, 1, 512L * 1_024 * 1_024, "cumulativeResponseBytes");
        requireRange(ownedBytes, 1, Integer.MAX_VALUE, "ownedBytes");
        requireRange(warnings, 1, 4_096, "warnings");
        requireRange(cacheEntries, 1, 1_024, "cacheEntries");
        requireRange(cacheBytes, 1, 256L * 1_024 * 1_024, "cacheBytes");
        if (tilesPerRequest > (long) regionAxisTiles * regionAxisTiles
                || concurrency > tilesPerRequest
                || responseBodyBytes > cumulativeResponseBytes
                || cacheBytes > ownedBytes) {
            throw new IllegalArgumentException("HTTP XYZ limits are internally inconsistent");
        }
    }

    /**
     * Returns conservative defaults.
     *
     * @return shared immutable defaults
     */
    public static HttpXyzLimits defaults() {
        return DEFAULTS;
    }

    private static void requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside the supported range");
        }
    }
}
