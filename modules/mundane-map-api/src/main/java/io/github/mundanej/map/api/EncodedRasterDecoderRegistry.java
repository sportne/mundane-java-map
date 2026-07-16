package io.github.mundanej.map.api;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable declaration-ordered explicit encoded-raster decoder registry. */
public final class EncodedRasterDecoderRegistry {
    private final Map<EncodedRasterFormat, EncodedRasterDecoder> decoders;
    private final List<EncodedRasterFormat> formats;

    private EncodedRasterDecoderRegistry(Builder builder) {
        decoders = Map.copyOf(builder.decoders);
        formats = List.copyOf(builder.formats);
    }

    /**
     * Creates a single-use registry builder.
     *
     * @return a single-use registry builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns registered formats in declaration order.
     *
     * @return registered formats in declaration order
     */
    public List<EncodedRasterFormat> formats() {
        return formats;
    }

    /**
     * Finds one exact registration.
     *
     * @param format the exact encoded format
     * @return the explicitly registered decoder, if any
     */
    public Optional<EncodedRasterDecoder> find(EncodedRasterFormat format) {
        return Optional.ofNullable(decoders.get(Objects.requireNonNull(format, "format")));
    }

    /** Single-use explicit registry builder. */
    public static final class Builder {
        private final EnumMap<EncodedRasterFormat, EncodedRasterDecoder> decoders =
                new EnumMap<>(EncodedRasterFormat.class);
        private final List<EncodedRasterFormat> formats = new ArrayList<>();
        private boolean built;

        private Builder() {}

        /**
         * Registers one explicit decoder.
         *
         * @param format exact registration key
         * @param decoder explicit decoder instance
         * @return this builder
         * @throws NullPointerException if {@code format} or {@code decoder} is {@code null}
         * @throws RegistrationException with code {@code RASTER_DECODER_DUPLICATE} and {@code
         *     format}, {@code firstIndex}, and {@code duplicateIndex} context when the exact format
         *     is already registered
         * @throws IllegalStateException if this single-use builder has already built a registry
         */
        public Builder register(EncodedRasterFormat format, EncodedRasterDecoder decoder) {
            requireOpen();
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(decoder, "decoder");
            int duplicateIndex = formats.size();
            int firstIndex = formats.indexOf(format);
            if (firstIndex >= 0) {
                throw new RegistrationException(format, firstIndex, duplicateIndex);
            }
            decoders.put(format, decoder);
            formats.add(format);
            return this;
        }

        /**
         * Builds and consumes this builder.
         *
         * @return the immutable registry
         * @throws IllegalStateException if this single-use builder has already built a registry
         */
        public EncodedRasterDecoderRegistry build() {
            requireOpen();
            built = true;
            return new EncodedRasterDecoderRegistry(this);
        }

        private void requireOpen() {
            if (built) {
                throw new IllegalStateException("Decoder registry builder has already built");
            }
        }
    }

    /** Stable duplicate-registration configuration failure. */
    @SuppressWarnings("serial")
    public static final class RegistrationException extends IllegalArgumentException {
        /** Stable duplicate-registration code retained for serialization. */
        private final String code;

        /** Immutable duplicate-registration context retained for serialization. */
        private final Map<String, String> context;

        private RegistrationException(
                EncodedRasterFormat format, int firstIndex, int duplicateIndex) {
            super("A decoder is already registered for " + format);
            code = "RASTER_DECODER_DUPLICATE";
            context =
                    Map.of(
                            "format", format.name(),
                            "firstIndex", Integer.toString(firstIndex),
                            "duplicateIndex", Integer.toString(duplicateIndex));
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
         * Returns immutable stable context.
         *
         * @return immutable stable context
         */
        public Map<String, String> context() {
            return context;
        }
    }
}
