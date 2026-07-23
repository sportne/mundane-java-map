package io.github.mundanej.map.io.http.tiles;

/** Network schemes accepted by an explicitly constructed XYZ client. */
public enum HttpSchemePolicy {
    /** Accept only TLS-protected HTTPS templates. */
    HTTPS_ONLY,
    /** Accept HTTPS or explicitly configured cleartext HTTP. */
    HTTPS_OR_HTTP
}
