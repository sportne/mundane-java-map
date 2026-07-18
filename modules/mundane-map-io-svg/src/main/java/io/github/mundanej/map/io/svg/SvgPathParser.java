package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.VectorPathCommand;
import java.util.Arrays;

final class SvgPathParser {
    private final String sourceId;
    private final SvgImportBudget budget;
    private final CancellationToken cancellation;
    private final SvgNumbers.Cursor input;
    private byte[] commandCodes;
    private double[] ordinates;
    private int commandCount;
    private int ordinateCount;
    private double x;
    private double y;
    private double startX;
    private double startY;
    private double previousControlX;
    private double previousControlY;
    private char previousCommand;
    private boolean active;
    private boolean closed;
    private boolean subpathHasSegment;
    private boolean abandonedOpenSubpath;
    private int segments;

    private SvgPathParser(
            String sourceId,
            String data,
            SvgImportLimits limits,
            SvgImportBudget budget,
            CancellationToken cancellation) {
        this.sourceId = sourceId;
        this.budget = budget;
        this.cancellation = cancellation;
        input = SvgNumbers.cursor(sourceId, data, "d", limits, budget);
        budget.chargeOwned(16L + 32L * Double.BYTES);
        commandCodes = new byte[16];
        ordinates = new double[32];
    }

    static SvgPathData parse(
            String sourceId,
            String data,
            SvgImportLimits limits,
            SvgImportBudget budget,
            CancellationToken cancellation) {
        if (data.isBlank()) {
            throw SvgFailures.value(sourceId, "d", "blank");
        }
        return new SvgPathParser(sourceId, data, limits, budget, cancellation).parse();
    }

    private SvgPathData parse() {
        char command = '\0';
        while (!input.atEnd()) {
            checkCancellation();
            char candidate = input.peekRaw();
            if (isLetter(candidate)) {
                command = input.readLetter();
                if (!supported(command)) {
                    throw SvgFailures.unsupported(sourceId, "pathCommand");
                }
                if (closed && command != 'M' && command != 'm') {
                    throw SvgFailures.value(sourceId, "d", "commandAfterClose");
                }
            } else if (command == '\0' || command == 'Z' || command == 'z') {
                throw SvgFailures.value(sourceId, "d", "syntax");
            }
            command = execute(command);
        }
        if (!active || !subpathHasSegment) {
            throw SvgFailures.value(sourceId, "d", "emptySubpath");
        }
        boolean open = abandonedOpenSubpath || !closed;
        budget.chargeOwned((long) commandCount * Long.BYTES + (long) ordinateCount * Double.BYTES);
        VectorPathCommand[] commandArray = new VectorPathCommand[commandCount];
        for (int index = 0; index < commandCount; index++) {
            commandArray[index] = decode(commandCodes[index]);
        }
        double[] ordinateArray = Arrays.copyOf(ordinates, ordinateCount);
        return new SvgPathData(commandArray, ordinateArray, segments, open);
    }

    private char execute(char command) {
        boolean relative = Character.isLowerCase(command);
        char upper = Character.toUpperCase(command);
        return switch (upper) {
            case 'M' -> move(relative, command);
            case 'L' -> line(relative, command);
            case 'H' -> horizontal(relative, command);
            case 'V' -> vertical(relative, command);
            case 'Q' -> quadratic(relative, command);
            case 'T' -> smoothQuadratic(relative, command);
            case 'C' -> cubic(relative, command);
            case 'S' -> smoothCubic(relative, command);
            case 'Z' -> close(command);
            default -> throw new IllegalStateException("unreachable path command");
        };
    }

    private char move(boolean relative, char command) {
        if (!input.nextIsNumber()) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        double nextX = input.read("d");
        double nextY = input.read("d");
        if (relative) {
            nextX += x;
            nextY += y;
        }
        if (active && !subpathHasSegment) {
            throw SvgFailures.value(sourceId, "d", "emptySubpath");
        }
        if (active && !closed) {
            abandonedOpenSubpath = true;
        }
        append(VectorPathCommand.MOVE_TO, nextX, nextY);
        x = nextX;
        y = nextY;
        startX = x;
        startY = y;
        active = true;
        closed = false;
        subpathHasSegment = false;
        previousCommand = command;
        char lineCommand = relative ? 'l' : 'L';
        while (input.nextIsNumber()) {
            line(relative, lineCommand);
        }
        return lineCommand;
    }

    private char line(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double nextX = input.read("d");
            double nextY = input.read("d");
            if (relative) {
                nextX += x;
                nextY += y;
            }
            appendSegment(VectorPathCommand.LINE_TO, nextX, nextY);
            x = nextX;
            y = nextY;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char horizontal(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double nextX = input.read("d") + (relative ? x : 0.0);
            appendSegment(VectorPathCommand.LINE_TO, nextX, y);
            x = nextX;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char vertical(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double nextY = input.read("d") + (relative ? y : 0.0);
            appendSegment(VectorPathCommand.LINE_TO, x, nextY);
            y = nextY;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char quadratic(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double controlX = input.read("d");
            double controlY = input.read("d");
            double nextX = input.read("d");
            double nextY = input.read("d");
            if (relative) {
                controlX += x;
                controlY += y;
                nextX += x;
                nextY += y;
            }
            appendSegment(VectorPathCommand.QUADRATIC_TO, controlX, controlY, nextX, nextY);
            x = nextX;
            y = nextY;
            previousControlX = controlX;
            previousControlY = controlY;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char smoothQuadratic(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double controlX = isPreviousQuadratic() ? 2.0 * x - previousControlX : x;
            double controlY = isPreviousQuadratic() ? 2.0 * y - previousControlY : y;
            double nextX = input.read("d");
            double nextY = input.read("d");
            if (relative) {
                nextX += x;
                nextY += y;
            }
            appendSegment(VectorPathCommand.QUADRATIC_TO, controlX, controlY, nextX, nextY);
            x = nextX;
            y = nextY;
            previousControlX = controlX;
            previousControlY = controlY;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char cubic(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double firstX = input.read("d");
            double firstY = input.read("d");
            double secondX = input.read("d");
            double secondY = input.read("d");
            double nextX = input.read("d");
            double nextY = input.read("d");
            if (relative) {
                firstX += x;
                firstY += y;
                secondX += x;
                secondY += y;
                nextX += x;
                nextY += y;
            }
            appendSegment(
                    VectorPathCommand.CUBIC_TO, firstX, firstY, secondX, secondY, nextX, nextY);
            x = nextX;
            y = nextY;
            previousControlX = secondX;
            previousControlY = secondY;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char smoothCubic(boolean relative, char command) {
        requireOpen();
        boolean read = false;
        do {
            double firstX = isPreviousCubic() ? 2.0 * x - previousControlX : x;
            double firstY = isPreviousCubic() ? 2.0 * y - previousControlY : y;
            double secondX = input.read("d");
            double secondY = input.read("d");
            double nextX = input.read("d");
            double nextY = input.read("d");
            if (relative) {
                secondX += x;
                secondY += y;
                nextX += x;
                nextY += y;
            }
            appendSegment(
                    VectorPathCommand.CUBIC_TO, firstX, firstY, secondX, secondY, nextX, nextY);
            x = nextX;
            y = nextY;
            previousControlX = secondX;
            previousControlY = secondY;
            previousCommand = command;
            read = true;
        } while (input.nextIsNumber());
        if (!read) {
            throw SvgFailures.value(sourceId, "d", "arity");
        }
        return command;
    }

    private char close(char command) {
        requireOpen();
        if (!subpathHasSegment) {
            throw SvgFailures.value(sourceId, "d", "emptySubpath");
        }
        appendSegment(VectorPathCommand.CLOSE);
        x = startX;
        y = startY;
        closed = true;
        previousCommand = command;
        return command;
    }

    private void requireOpen() {
        if (!active) {
            throw SvgFailures.value(sourceId, "d", "syntax");
        }
        if (closed) {
            throw SvgFailures.value(sourceId, "d", "commandAfterClose");
        }
    }

    private boolean isPreviousQuadratic() {
        char upper = Character.toUpperCase(previousCommand);
        return upper == 'Q' || upper == 'T';
    }

    private boolean isPreviousCubic() {
        char upper = Character.toUpperCase(previousCommand);
        return upper == 'C' || upper == 'S';
    }

    private void appendSegment(VectorPathCommand command, double... values) {
        budget.addSegments(1);
        append(command, values);
        segments++;
        subpathHasSegment = true;
    }

    private void append(VectorPathCommand command, double... values) {
        budget.addCommands(1);
        ensureCommandCapacity(commandCount + 1);
        commandCodes[commandCount++] = encode(command);
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw SvgFailures.value(sourceId, "d", "overflow");
            }
            ensureOrdinateCapacity(ordinateCount + 1);
            ordinates[ordinateCount++] = value == 0.0 ? 0.0 : value;
        }
        checkCancellation();
    }

    private void ensureCommandCapacity(int requested) {
        if (requested <= commandCodes.length) {
            return;
        }
        int capacity = Math.max(requested, Math.multiplyExact(commandCodes.length, 2));
        budget.chargeOwned(capacity);
        commandCodes = Arrays.copyOf(commandCodes, capacity);
    }

    private void ensureOrdinateCapacity(int requested) {
        if (requested <= ordinates.length) {
            return;
        }
        int capacity = Math.max(requested, Math.multiplyExact(ordinates.length, 2));
        budget.chargeOwned((long) capacity * Double.BYTES);
        ordinates = Arrays.copyOf(ordinates, capacity);
    }

    private void checkCancellation() {
        if (cancellation.isCancellationRequested()) {
            throw SvgFailures.cancelled(sourceId);
        }
    }

    private static boolean supported(char command) {
        return switch (Character.toUpperCase(command)) {
            case 'M', 'L', 'H', 'V', 'Q', 'T', 'C', 'S', 'Z' -> true;
            default -> false;
        };
    }

    private static byte encode(VectorPathCommand command) {
        return switch (command) {
            case MOVE_TO -> 1;
            case LINE_TO -> 2;
            case QUADRATIC_TO -> 3;
            case CUBIC_TO -> 4;
            case CLOSE -> 5;
        };
    }

    private static VectorPathCommand decode(byte code) {
        return switch (code) {
            case 1 -> VectorPathCommand.MOVE_TO;
            case 2 -> VectorPathCommand.LINE_TO;
            case 3 -> VectorPathCommand.QUADRATIC_TO;
            case 4 -> VectorPathCommand.CUBIC_TO;
            case 5 -> VectorPathCommand.CLOSE;
            default -> throw new IllegalStateException("unknown internal SVG path command");
        };
    }

    private static boolean isLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }
}
