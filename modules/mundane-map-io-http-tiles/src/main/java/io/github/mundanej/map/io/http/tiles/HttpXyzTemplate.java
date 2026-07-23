package io.github.mundanej.map.io.http.tiles;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strict fixed-host hierarchical URI template containing one each of {@code {z}}, {@code {x}}, and
 * {@code {y}}.
 */
public final class HttpXyzTemplate {
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+");
    private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9._~!$&'()*+,;=:@/{\\}-]*");
    private final String value;
    private final URI parsed;

    private HttpXyzTemplate(String value, URI parsed) {
        this.value = value;
        this.parsed = parsed;
    }

    /**
     * Parses a structurally strict HTTPS or HTTP XYZ template without performing network I/O.
     *
     * @param value bounded printable-ASCII fixed-host template
     * @return validated redacting template
     */
    public static HttpXyzTemplate parse(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()
                || value.length() > 8_192
                || !value.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) {
            throw new IllegalArgumentException("XYZ template must be bounded printable ASCII");
        }
        if ((!value.startsWith("https://") && !value.startsWith("http://"))
                || value.indexOf('%') >= 0
                || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("XYZ template has an unsupported URI form");
        }
        for (String placeholder : new String[] {"{z}", "{x}", "{y}"}) {
            if (value.indexOf(placeholder) < 0
                    || value.indexOf(placeholder) != value.lastIndexOf(placeholder)) {
                throw new IllegalArgumentException(
                        "XYZ template must contain each placeholder exactly once");
            }
        }
        String concrete = value.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0");
        if (concrete.indexOf('{') >= 0 || concrete.indexOf('}') >= 0) {
            throw new IllegalArgumentException("XYZ template contains an unsupported token");
        }
        URI uri;
        try {
            uri = URI.create(concrete);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("XYZ template is not a valid URI");
        }
        if (uri.isOpaque()
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getHost() == null
                || !HOST.matcher(uri.getHost()).matches()
                || !SAFE_PATH
                        .matcher(value.substring(value.indexOf('/', value.indexOf("//") + 2)))
                        .matches()) {
            throw new IllegalArgumentException("XYZ template has an unsupported URI component");
        }
        validatePort(uri);
        String path = uri.getPath();
        if (path.equals("/.")
                || path.equals("/..")
                || path.contains("/./")
                || path.contains("/../")
                || path.endsWith("/.")
                || path.endsWith("/..")) {
            throw new IllegalArgumentException("XYZ template path may not contain dot segments");
        }
        return new HttpXyzTemplate(value, uri);
    }

    private static void validatePort(URI uri) {
        String authority = uri.getRawAuthority();
        int separator = authority.lastIndexOf(':');
        if (separator < 0) {
            return;
        }
        String port = authority.substring(separator + 1);
        if (port.isEmpty()
                || (port.length() > 1 && port.charAt(0) == '0')
                || !port.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("XYZ template port is not canonical");
        }
        int value;
        try {
            value = Integer.parseInt(port);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("XYZ template port is outside the supported range");
        }
        if (value < 1 || value > 65_535 || uri.getPort() != value) {
            throw new IllegalArgumentException("XYZ template port is outside the supported range");
        }
    }

    String scheme() {
        return parsed.getScheme();
    }

    URI resolve(XyzTileRegion region, int characterLimit) {
        String resolved =
                value.replace("{z}", Integer.toString(region.zoom()))
                        .replace("{x}", Integer.toString(region.minimumX()))
                        .replace("{y}", Integer.toString(region.minimumY()));
        if (resolved.length() > characterLimit) {
            throw new IllegalArgumentException("Resolved XYZ URI exceeds its configured limit");
        }
        URI result = URI.create(resolved);
        if (!result.getScheme().equals(parsed.getScheme())
                || !result.getHost()
                        .toLowerCase(Locale.ROOT)
                        .equals(parsed.getHost().toLowerCase(Locale.ROOT))
                || result.getPort() != parsed.getPort()) {
            throw new IllegalStateException("Resolved XYZ URI changed authority");
        }
        return result;
    }

    int length() {
        return value.length();
    }

    @Override
    public String toString() {
        return "HttpXyzTemplate[redacted]";
    }
}
