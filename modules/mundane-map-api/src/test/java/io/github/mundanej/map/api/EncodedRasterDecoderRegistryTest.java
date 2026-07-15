package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EncodedRasterDecoderRegistryTest {
    private final EncodedRasterDecoder decoder = (input, context) -> null;

    @Test
    void retainsDeclarationOrderAndInstances() {
        EncodedRasterDecoderRegistry registry =
                EncodedRasterDecoderRegistry.builder()
                        .register(EncodedRasterFormat.JPEG, decoder)
                        .register(EncodedRasterFormat.PNG, decoder)
                        .build();

        assertEquals(
                java.util.List.of(EncodedRasterFormat.JPEG, EncodedRasterFormat.PNG),
                registry.formats());
        assertSame(decoder, registry.find(EncodedRasterFormat.PNG).orElseThrow());
    }

    @Test
    void rejectsDuplicatesWithStableContextAndConsumesBuilder() {
        EncodedRasterDecoderRegistry.Builder builder =
                EncodedRasterDecoderRegistry.builder().register(EncodedRasterFormat.PNG, decoder);
        EncodedRasterDecoderRegistry.RegistrationException failure =
                assertThrows(
                        EncodedRasterDecoderRegistry.RegistrationException.class,
                        () -> builder.register(EncodedRasterFormat.PNG, decoder));

        assertEquals("RASTER_DECODER_DUPLICATE", failure.code());
        assertEquals(
                java.util.Map.of("format", "PNG", "firstIndex", "0", "duplicateIndex", "1"),
                failure.context());
        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
    }
}
