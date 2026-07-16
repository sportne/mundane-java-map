package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;

/**
 * Explicit factory for the Level 1 JDK ImageIO raster decoder bridge.
 *
 * <p>The factory performs no application-classpath codec discovery: it accepts exactly one {@code
 * java.desktop} reader provider for each required format and reports stable configuration context
 * otherwise. Returned registries are immutable and instance-owned.
 */
public final class AwtRasterDecoders {
    private AwtRasterDecoders() {}

    /**
     * Builds a fresh immutable PNG/JPEG registry backed only by {@code java.desktop} readers.
     *
     * @return the explicit Level 1 decoder registry
     * @throws DecoderConfigurationException if exactly one JDK provider cannot be selected for each
     *     required format
     */
    public static EncodedRasterDecoderRegistry level1() {
        EnumMap<EncodedRasterFormat, ImageReaderSpi> providers =
                new EnumMap<>(EncodedRasterFormat.class);
        providers.put(EncodedRasterFormat.PNG, select(EncodedRasterFormat.PNG));
        providers.put(EncodedRasterFormat.JPEG, select(EncodedRasterFormat.JPEG));
        ImageIoRasterDecoder decoder = new ImageIoRasterDecoder(providers);
        return EncodedRasterDecoderRegistry.builder()
                .register(EncodedRasterFormat.PNG, decoder)
                .register(EncodedRasterFormat.JPEG, decoder)
                .build();
    }

    private static ImageReaderSpi select(EncodedRasterFormat format) {
        List<ImageReaderSpi> eligible = new ArrayList<>();
        Iterator<ImageReaderSpi> providers =
                IIORegistry.getDefaultInstance().getServiceProviders(ImageReaderSpi.class, true);
        while (providers.hasNext()) {
            ImageReaderSpi provider = providers.next();
            if ("java.desktop".equals(provider.getClass().getModule().getName())
                    && declares(provider, format)) {
                eligible.add(provider);
            }
        }
        if (eligible.size() != 1) {
            String code =
                    eligible.isEmpty()
                            ? "RASTER_DECODER_JDK_READER_UNAVAILABLE"
                            : "RASTER_DECODER_JDK_READER_AMBIGUOUS";
            throw new DecoderConfigurationException(
                    code,
                    Map.of(
                            "format", format.name(),
                            "eligibleCount", Integer.toString(eligible.size())));
        }
        return eligible.getFirst();
    }

    private static boolean declares(ImageReaderSpi provider, EncodedRasterFormat format) {
        String expected = format == EncodedRasterFormat.PNG ? "png" : "jpeg";
        for (String name : provider.getFormatNames()) {
            if (expected.equals(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stable unavailable/ambiguous JDK-reader configuration failure.
     *
     * <p>{@link #code()} and {@link #context()} are stable diagnostic data. The inherited message
     * is explanatory text and is not a compatibility contract.
     */
    @SuppressWarnings("serial")
    public static final class DecoderConfigurationException extends IllegalStateException {
        /** Stable configuration diagnostic code. */
        private final String code;

        /** Immutable stable diagnostic context. */
        private final Map<String, String> context;

        private DecoderConfigurationException(String code, Map<String, String> context) {
            super("Cannot select the fixed JDK image reader");
            this.code = code;
            this.context = Map.copyOf(context);
        }

        /**
         * Returns the stable configuration code.
         *
         * @return the stable configuration code
         */
        public String code() {
            return code;
        }

        /**
         * Returns immutable stable context in deterministic key order.
         *
         * @return immutable stable context
         */
        public Map<String, String> context() {
            return context;
        }
    }
}
