package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.io.image.EncodedRasterDecodeOptions;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable policy and limits captured by one HTTP XYZ client.
 *
 * @param schemePolicy accepted URI schemes
 * @param limits HTTP request, response, and retention limits
 * @param snapshotLimits limits captured by detached raster sources
 * @param decodeOptions encoded-image decode policy
 * @param cachePolicy explicit cache policy
 * @param connectTimeout connection timeout
 * @param requestTimeout per-request timeout
 * @param operationTimeout complete fetch-operation timeout
 * @param closeTimeout owned-executor close timeout
 */
public record HttpXyzClientOptions(
        HttpSchemePolicy schemePolicy,
        HttpXyzLimits limits,
        RasterSourceLimits snapshotLimits,
        EncodedRasterDecodeOptions decodeOptions,
        HttpTileCachePolicy cachePolicy,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration operationTimeout,
        Duration closeTimeout) {
    private static final HttpXyzClientOptions DEFAULTS =
            new HttpXyzClientOptions(
                    HttpSchemePolicy.HTTPS_ONLY,
                    HttpXyzLimits.defaults(),
                    RasterSourceLimits.LEVEL_1,
                    EncodedRasterDecodeOptions.defaults(),
                    HttpTileCachePolicy.DISABLED,
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(15),
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(2));

    /** Validates explicit policies and millisecond-resolution timeouts. */
    public HttpXyzClientOptions {
        Objects.requireNonNull(schemePolicy, "schemePolicy");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(snapshotLimits, "snapshotLimits");
        Objects.requireNonNull(decodeOptions, "decodeOptions");
        Objects.requireNonNull(cachePolicy, "cachePolicy");
        connectTimeout = timeout(connectTimeout, Duration.ofMinutes(2), "connectTimeout");
        requestTimeout = timeout(requestTimeout, Duration.ofMinutes(5), "requestTimeout");
        operationTimeout = timeout(operationTimeout, Duration.ofMinutes(15), "operationTimeout");
        closeTimeout = timeout(closeTimeout, Duration.ofSeconds(30), "closeTimeout");
        if (connectTimeout.compareTo(requestTimeout) > 0
                || requestTimeout.compareTo(operationTimeout) > 0) {
            throw new IllegalArgumentException(
                    "Timeouts must satisfy connect <= request <= operation");
        }
    }

    /**
     * Returns conservative HTTPS-only, no-cache defaults.
     *
     * @return shared immutable defaults
     */
    public static HttpXyzClientOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a copy explicitly permitting fixed-host loopback-style cleartext HTTP.
     *
     * @return options with cleartext HTTP enabled
     */
    public HttpXyzClientOptions allowingHttp() {
        return new HttpXyzClientOptions(
                HttpSchemePolicy.HTTPS_OR_HTTP,
                limits,
                snapshotLimits,
                decodeOptions,
                cachePolicy,
                connectTimeout,
                requestTimeout,
                operationTimeout,
                closeTimeout);
    }

    private static Duration timeout(Duration value, Duration maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero()
                || value.isNegative()
                || value.compareTo(maximum) > 0
                || value.toNanosPart() % 1_000_000 != 0) {
            throw new IllegalArgumentException(name + " is outside the supported range");
        }
        return value;
    }
}
