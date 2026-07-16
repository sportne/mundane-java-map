package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.api.VectorPathCommand;
import java.awt.geom.Path2D;
import java.util.Objects;

/** Package-confined conversion from toolkit-neutral packed paths to fresh Java2D paths. */
final class VectorPath2D {
    private VectorPath2D() {}

    static Converted convert(VectorPath source) {
        Objects.requireNonNull(source, "source");
        Path2D.Double stroke = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        Path2D.Double fill = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        int ordinateIndex = 0;
        int subpathCommandStart = -1;
        int subpathOrdinateStart = -1;
        for (int commandIndex = 0; commandIndex < source.commandCount(); commandIndex++) {
            VectorPathCommand command = source.commandAt(commandIndex);
            if (command == VectorPathCommand.MOVE_TO) {
                subpathCommandStart = commandIndex;
                subpathOrdinateStart = ordinateIndex;
            }
            ordinateIndex = append(stroke, source, command, ordinateIndex);
            if (command == VectorPathCommand.CLOSE) {
                appendRange(fill, source, subpathCommandStart, commandIndex, subpathOrdinateStart);
            }
        }
        return new Converted(stroke, fill);
    }

    static StreamCounts preflight(VectorPath source) {
        Objects.requireNonNull(source, "source");
        long fillCommands = 0;
        long fillOrdinates = 0;
        long subpathCommands = 0;
        long subpathOrdinates = 0;
        for (int index = 0; index < source.commandCount(); index++) {
            VectorPathCommand command = source.commandAt(index);
            if (command == VectorPathCommand.MOVE_TO) {
                subpathCommands = 0;
                subpathOrdinates = 0;
            }
            subpathCommands = Math.addExact(subpathCommands, 1);
            subpathOrdinates = Math.addExact(subpathOrdinates, command.arity());
            if (command == VectorPathCommand.CLOSE) {
                fillCommands = Math.addExact(fillCommands, subpathCommands);
                fillOrdinates = Math.addExact(fillOrdinates, subpathOrdinates);
            }
        }
        return new StreamCounts(
                source.commandCount(), source.ordinateCount(), fillCommands, fillOrdinates);
    }

    private static void appendRange(
            Path2D.Double target,
            VectorPath source,
            int firstCommand,
            int lastCommand,
            int firstOrdinate) {
        int ordinateIndex = firstOrdinate;
        for (int commandIndex = firstCommand; commandIndex <= lastCommand; commandIndex++) {
            ordinateIndex = append(target, source, source.commandAt(commandIndex), ordinateIndex);
        }
    }

    private static int append(
            Path2D.Double target, VectorPath source, VectorPathCommand command, int ordinateIndex) {
        switch (command) {
            case MOVE_TO ->
                    target.moveTo(
                            source.ordinateAt(ordinateIndex), source.ordinateAt(ordinateIndex + 1));
            case LINE_TO ->
                    target.lineTo(
                            source.ordinateAt(ordinateIndex), source.ordinateAt(ordinateIndex + 1));
            case QUADRATIC_TO ->
                    target.quadTo(
                            source.ordinateAt(ordinateIndex),
                            source.ordinateAt(ordinateIndex + 1),
                            source.ordinateAt(ordinateIndex + 2),
                            source.ordinateAt(ordinateIndex + 3));
            case CUBIC_TO ->
                    target.curveTo(
                            source.ordinateAt(ordinateIndex),
                            source.ordinateAt(ordinateIndex + 1),
                            source.ordinateAt(ordinateIndex + 2),
                            source.ordinateAt(ordinateIndex + 3),
                            source.ordinateAt(ordinateIndex + 4),
                            source.ordinateAt(ordinateIndex + 5));
            case CLOSE -> target.closePath();
        }
        return ordinateIndex + command.arity();
    }

    record Converted(Path2D.Double strokePath, Path2D.Double fillPath) {}

    record StreamCounts(
            long strokeCommands, long strokeOrdinates, long fillCommands, long fillOrdinates) {
        long strokeUnits() {
            return Math.addExact(strokeCommands, strokeOrdinates);
        }

        long fillUnits() {
            return Math.addExact(fillCommands, fillOrdinates);
        }
    }
}
