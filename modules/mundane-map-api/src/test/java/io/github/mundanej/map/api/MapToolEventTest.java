package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapToolEventTest {
    @Test
    void copiesCollectionsAndPreservesPresentAndAbsentMapCoordinates() {
        Set<MapPointerButton> buttons = new HashSet<>();
        buttons.add(MapPointerButton.PRIMARY);
        Set<MapInputModifier> modifiers = new HashSet<>();
        modifiers.add(MapInputModifier.SHIFT);

        MapToolEvent event =
                event(
                        1,
                        MapToolEvent.Type.PRESS,
                        Optional.of(new Coordinate(3.0, 4.0)),
                        MapPointerButton.PRIMARY,
                        buttons,
                        modifiers,
                        0,
                        0.0,
                        false,
                        Optional.empty());
        buttons.clear();
        modifiers.clear();

        assertEquals(Set.of(MapPointerButton.PRIMARY), event.buttonsDown());
        assertEquals(Set.of(MapInputModifier.SHIFT), event.modifiers());
        assertEquals(new Coordinate(3.0, 4.0), event.mapCoordinate().orElseThrow());
        assertFalse(
                event(
                                2,
                                MapToolEvent.Type.MOVE,
                                Optional.empty(),
                                MapPointerButton.NONE,
                                Set.of(),
                                Set.of(),
                                0,
                                0.0,
                                false,
                                Optional.empty())
                        .mapCoordinate()
                        .isPresent());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.buttonsDown().add(MapPointerButton.SECONDARY));
    }

    @Test
    void enforcesEveryEventKindInvariant() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        event(
                                0,
                                MapToolEvent.Type.MOVE,
                                Optional.empty(),
                                MapPointerButton.NONE,
                                Set.of(),
                                Set.of(),
                                0,
                                0.0,
                                false,
                                Optional.empty()));
        assertInvalid(
                MapToolEvent.Type.PRESS,
                MapPointerButton.NONE,
                Set.of(),
                0,
                0.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.DRAG,
                MapPointerButton.NONE,
                Set.of(),
                0,
                0.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.RELEASE,
                MapPointerButton.PRIMARY,
                Set.of(MapPointerButton.PRIMARY),
                0,
                0.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.MOVE,
                MapPointerButton.NONE,
                Set.of(MapPointerButton.PRIMARY),
                0,
                0.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.CLICK,
                MapPointerButton.PRIMARY,
                Set.of(),
                0,
                0.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.WHEEL,
                MapPointerButton.NONE,
                Set.of(),
                1,
                1.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.CANCEL,
                MapPointerButton.NONE,
                Set.of(),
                0,
                0.0,
                false,
                Optional.empty());
        assertInvalid(
                MapToolEvent.Type.MOVE,
                MapPointerButton.NONE,
                Set.of(MapPointerButton.NONE),
                0,
                0.0,
                false,
                Optional.empty());
    }

    @Test
    void supportsAuxiliaryButtonsAndAllPublicResultValues() {
        MapPointerButton auxiliary = new MapPointerButton(20);
        MapToolEvent press =
                event(
                        3,
                        MapToolEvent.Type.PRESS,
                        Optional.empty(),
                        auxiliary,
                        Set.of(auxiliary),
                        Set.of(MapInputModifier.ALT_GRAPH),
                        2,
                        0.0,
                        true,
                        Optional.empty());

        assertEquals(auxiliary, press.button());
        assertEquals(3, MapToolResult.values().length);
        assertEquals(4, MapCursorIntent.values().length);
        assertThrows(IllegalArgumentException.class, () -> new MapPointerButton(-1));
    }

    private static void assertInvalid(
            MapToolEvent.Type type,
            MapPointerButton button,
            Set<MapPointerButton> buttonsDown,
            int clickCount,
            double wheel,
            boolean popup,
            Optional<MapToolCancelReason> cancelReason) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        event(
                                1,
                                type,
                                Optional.empty(),
                                button,
                                buttonsDown,
                                Set.of(),
                                clickCount,
                                wheel,
                                popup,
                                cancelReason));
    }

    private static MapToolEvent event(
            long sequence,
            MapToolEvent.Type type,
            Optional<Coordinate> mapCoordinate,
            MapPointerButton button,
            Set<MapPointerButton> buttonsDown,
            Set<MapInputModifier> modifiers,
            int clickCount,
            double wheel,
            boolean popup,
            Optional<MapToolCancelReason> cancelReason) {
        return new MapToolEvent(
                sequence,
                type,
                10.0,
                20.0,
                mapCoordinate,
                button,
                buttonsDown,
                modifiers,
                clickCount,
                wheel,
                popup,
                cancelReason);
    }
}
