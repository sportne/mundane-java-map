package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PointFeatureDraftTest {
    @Test
    void canonicalizesContentAndCreatesOnlyCompletePointRecords() {
        byte[] payload = {1, 2};
        Map<String, Object> mutable = new LinkedHashMap<>();
        mutable.put("count", 2L);
        mutable.put("payload", payload);
        PointFeatureDraft draft = new PointFeatureDraft("track-1", "Track 1", mutable);

        mutable.clear();
        payload[0] = 9;
        assertEquals(List.of("count", "payload"), List.copyOf(draft.attributes().keySet()));
        assertEquals(new AttributeBytes(new byte[] {1, 2}), draft.attributes().get("payload"));
        FeatureRecord record = draft.at(new Coordinate(3, 4));
        assertEquals("track-1", record.id());
        assertEquals(new PointGeometry(new Coordinate(3, 4)), record.geometry());
        assertEquals(draft.attributes(), record.attributes());
        assertThrows(
                UnsupportedOperationException.class,
                () -> draft.attributes().put("unexpected", "value"));
    }

    @Test
    void rejectsMalformedIdentityAndAttributes() {
        assertThrows(
                IllegalArgumentException.class, () -> new PointFeatureDraft(" ", "name", Map.of()));
        assertThrows(NullPointerException.class, () -> new PointFeatureDraft("id", null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PointFeatureDraft("id", "name", Map.of("bad", new Object())));
    }
}
