package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VectorPathTest {
    @Test
    void storesEveryCommandInPackedOrderAndComputesAConservativeEnvelope() {
        VectorPathCommand[] commands = {
            VectorPathCommand.MOVE_TO,
            VectorPathCommand.LINE_TO,
            VectorPathCommand.QUADRATIC_TO,
            VectorPathCommand.CUBIC_TO,
            VectorPathCommand.CLOSE,
            VectorPathCommand.MOVE_TO,
            VectorPathCommand.LINE_TO
        };
        double[] ordinates = {
            0.0, 0.0, 1.0, 2.0, -3.0, 4.0, 5.0, 6.0, 7.0, -8.0, 9.0, 10.0, 11.0, 12.0, 20.0, 21.0,
            22.0, 23.0
        };

        VectorPath path = VectorPath.of(commands, ordinates);
        commands[0] = VectorPathCommand.CLOSE;
        ordinates[0] = 99.0;

        assertEquals(7, path.commandCount());
        assertEquals(VectorPathCommand.MOVE_TO, path.commandAt(0));
        assertEquals(VectorPathCommand.CUBIC_TO, path.commandAt(3));
        assertEquals(18, path.ordinateCount());
        assertEquals(0.0, path.ordinateAt(0));
        assertEquals(new Envelope(-3.0, -8.0, 22.0, 23.0), path.coordinateEnvelope());
        assertArrayEquals(
                new VectorPathCommand[] {
                    VectorPathCommand.MOVE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.QUADRATIC_TO,
                    VectorPathCommand.CUBIC_TO,
                    VectorPathCommand.CLOSE,
                    VectorPathCommand.MOVE_TO,
                    VectorPathCommand.LINE_TO
                },
                path.toCommandArray());

        VectorPathCommand[] commandCopy = path.toCommandArray();
        double[] ordinateCopy = path.toOrdinateArray();
        commandCopy[0] = VectorPathCommand.CLOSE;
        ordinateCopy[0] = 77.0;
        assertEquals(VectorPathCommand.MOVE_TO, path.commandAt(0));
        assertEquals(0.0, path.ordinateAt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> path.commandAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> path.commandAt(path.commandCount()));
        assertThrows(IndexOutOfBoundsException.class, () -> assertEquals(0.0, path.ordinateAt(-1)));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> assertEquals(0.0, path.ordinateAt(path.ordinateCount())));
    }

    @Test
    void equalityHashAndStringAreContentBased() {
        VectorPath first = triangle();
        VectorPath same = triangle();
        VectorPath different =
                VectorPath.builder().moveTo(0.0, 0.0).lineTo(2.0, 0.0).close().build();

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        assertTrue(first.toString().contains("MOVE_TO"));
        assertTrue(first.toString().contains("1.0"));
    }

    @Test
    void exactFactoryRejectsEveryInvalidStateOrOperandShape() {
        assertThrows(
                NullPointerException.class,
                () -> VectorPath.of((VectorPathCommand[]) null, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> VectorPath.of(new VectorPathCommand[0]));
        assertThrows(
                NullPointerException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {VectorPathCommand.MOVE_TO, null},
                                0.0,
                                0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> VectorPath.of(new VectorPathCommand[] {VectorPathCommand.LINE_TO}, 1.0, 2.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO, VectorPathCommand.MOVE_TO
                                },
                                0.0,
                                0.0,
                                1.0,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO, VectorPathCommand.CLOSE
                                },
                                0.0,
                                0.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO,
                                    VectorPathCommand.LINE_TO,
                                    VectorPathCommand.CLOSE,
                                    VectorPathCommand.LINE_TO
                                },
                                0.0,
                                0.0,
                                1.0,
                                0.0,
                                2.0,
                                0.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO,
                                    VectorPathCommand.LINE_TO,
                                    VectorPathCommand.CLOSE,
                                    VectorPathCommand.CLOSE
                                },
                                0.0,
                                0.0,
                                1.0,
                                0.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO,
                                    VectorPathCommand.LINE_TO,
                                    VectorPathCommand.MOVE_TO
                                },
                                0.0,
                                0.0,
                                1.0,
                                0.0,
                                2.0,
                                0.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO, VectorPathCommand.LINE_TO
                                },
                                0.0,
                                0.0,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO, VectorPathCommand.LINE_TO
                                },
                                0.0,
                                0.0,
                                1.0,
                                1.0,
                                2.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorPath.of(
                                new VectorPathCommand[] {
                                    VectorPathCommand.MOVE_TO, VectorPathCommand.LINE_TO
                                },
                                0.0,
                                0.0,
                                Double.NaN,
                                1.0));
    }

    @Test
    void builderPublishesOnceAndRecoversAfterRejectedOperations() {
        VectorPath.Builder builder = VectorPath.builder();
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, builder::close);
        assertThrows(IllegalArgumentException.class, () -> builder.moveTo(Double.NaN, 0.0));

        builder.moveTo(0.0, 0.0);
        assertThrows(IllegalStateException.class, () -> builder.moveTo(1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> builder.lineTo(1.0, Double.NaN));
        assertThrows(IllegalStateException.class, builder::build);
        builder.lineTo(1.0, 0.0).quadraticTo(2.0, 0.0, 2.0, 1.0);
        builder.cubicTo(2.0, 2.0, 1.0, 2.0, 0.0, 1.0).close();

        VectorPath path = builder.build();

        assertEquals(5, path.commandCount());
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, () -> builder.lineTo(3.0, 3.0));
        assertThrows(IllegalStateException.class, () -> builder.lineTo(Double.NaN, 3.0));
        assertThrows(IllegalStateException.class, builder::close);
    }

    @Test
    void commandAritiesAreStable() {
        assertEquals(2, VectorPathCommand.MOVE_TO.arity());
        assertEquals(2, VectorPathCommand.LINE_TO.arity());
        assertEquals(4, VectorPathCommand.QUADRATIC_TO.arity());
        assertEquals(6, VectorPathCommand.CUBIC_TO.arity());
        assertEquals(0, VectorPathCommand.CLOSE.arity());
    }

    private static VectorPath triangle() {
        return VectorPath.builder()
                .moveTo(0.0, 0.0)
                .lineTo(1.0, 0.0)
                .lineTo(0.0, 1.0)
                .close()
                .build();
    }
}
