package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ElevationQueryValuesTest {
    @Test
    void modesAreTheExactExplicitPolicies() {
        assertArrayEquals(
                new ElevationQueryMode[] {ElevationQueryMode.NEAREST, ElevationQueryMode.BILINEAR},
                ElevationQueryMode.values());
    }

    @Test
    void valueRetainsEveryUnitAndHasRecordValueBehavior() {
        for (ElevationUnit unit : ElevationUnit.values()) {
            ElevationValue value = new ElevationValue(12.5, unit);
            assertEquals(12.5, value.value());
            assertEquals(unit, value.unit());
            assertEquals(value, new ElevationValue(12.5, unit));
            assertEquals(value.hashCode(), new ElevationValue(12.5, unit).hashCode());
        }
    }

    @Test
    void valueRejectsNonFiniteAndNullAndCanonicalizesSignedZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationValue(Double.NaN, ElevationUnit.METRE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationValue(Double.POSITIVE_INFINITY, ElevationUnit.METRE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationValue(Double.NEGATIVE_INFINITY, ElevationUnit.METRE));
        assertThrows(NullPointerException.class, () -> new ElevationValue(1.0, null));

        ElevationValue zero = new ElevationValue(-0.0, ElevationUnit.US_SURVEY_FOOT);
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(zero.value()));
    }
}
