package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;

/** An immutable vector path stored as packed command codes and ordinates. */
public final class VectorPath {
    private static final byte MOVE_TO_CODE = 1;
    private static final byte LINE_TO_CODE = 2;
    private static final byte QUADRATIC_TO_CODE = 3;
    private static final byte CUBIC_TO_CODE = 4;
    private static final byte CLOSE_CODE = 5;

    private final byte[] commandCodes;
    private final double[] ordinates;
    private final Envelope coordinateEnvelope;

    private VectorPath(VectorPathCommand[] commands, double[] ordinates) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(ordinates, "ordinates");
        this.commandCodes = new byte[commands.length];
        this.ordinates = ordinates.clone();
        validateAndEncode(commands);
        this.coordinateEnvelope = computeEnvelope();
    }

    /** Creates a validated path from commands and their packed operands. */
    public static VectorPath of(VectorPathCommand[] commands, double... ordinates) {
        return new VectorPath(commands.clone(), ordinates);
    }

    /** Returns a new single-owner fluent path builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the number of path commands. */
    public int commandCount() {
        return commandCodes.length;
    }

    /** Returns the command at the supplied command index. */
    public VectorPathCommand commandAt(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= commandCodes.length) {
            throw new IndexOutOfBoundsException(commandIndex);
        }
        return decode(commandCodes[commandIndex]);
    }

    /** Returns the number of packed ordinates. */
    public int ordinateCount() {
        return ordinates.length;
    }

    /** Returns the ordinate at the supplied global packed index. */
    public double ordinateAt(int ordinateIndex) {
        if (ordinateIndex < 0 || ordinateIndex >= ordinates.length) {
            throw new IndexOutOfBoundsException(ordinateIndex);
        }
        return ordinates[ordinateIndex];
    }

    /** Returns a defensive copy of the commands. */
    public VectorPathCommand[] toCommandArray() {
        VectorPathCommand[] result = new VectorPathCommand[commandCodes.length];
        for (int index = 0; index < commandCodes.length; index++) {
            result[index] = decode(commandCodes[index]);
        }
        return result;
    }

    /** Returns a defensive copy of the packed ordinates. */
    public double[] toOrdinateArray() {
        return ordinates.clone();
    }

    /** Returns the conservative envelope of all path coordinates and controls. */
    public Envelope coordinateEnvelope() {
        return coordinateEnvelope;
    }

    private void validateAndEncode(VectorPathCommand[] commands) {
        if (commands.length == 0) {
            throw new IllegalArgumentException("commands must contain a drawing subpath");
        }
        int requiredOrdinates = 0;
        boolean active = false;
        boolean closed = false;
        boolean subpathHasSegment = false;
        boolean hasSegment = false;
        for (int index = 0; index < commands.length; index++) {
            VectorPathCommand command = Objects.requireNonNull(commands[index], "command");
            requiredOrdinates = Math.addExact(requiredOrdinates, command.arity());
            switch (command) {
                case MOVE_TO -> {
                    if (active && !subpathHasSegment) {
                        throw new IllegalArgumentException(
                                "MOVE_TO cannot abandon an empty subpath at command " + index);
                    }
                    active = true;
                    closed = false;
                    subpathHasSegment = false;
                }
                case LINE_TO, QUADRATIC_TO, CUBIC_TO -> {
                    if (!active || closed) {
                        throw new IllegalArgumentException(
                                command + " requires an open subpath at command " + index);
                    }
                    subpathHasSegment = true;
                    hasSegment = true;
                }
                case CLOSE -> {
                    if (!active || closed || !subpathHasSegment) {
                        throw new IllegalArgumentException(
                                "CLOSE requires a non-empty open subpath at command " + index);
                    }
                    closed = true;
                }
            }
            commandCodes[index] = encode(command);
        }
        if (!subpathHasSegment || !hasSegment) {
            throw new IllegalArgumentException("commands must end with a non-empty subpath");
        }
        if (requiredOrdinates != ordinates.length) {
            throw new IllegalArgumentException(
                    "ordinates length must equal command arity sum " + requiredOrdinates);
        }
        for (double ordinate : ordinates) {
            if (!Double.isFinite(ordinate)) {
                throw new IllegalArgumentException("ordinates must be finite");
            }
        }
    }

    private Envelope computeEnvelope() {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < ordinates.length; index += 2) {
            minimumX = Math.min(minimumX, ordinates[index]);
            minimumY = Math.min(minimumY, ordinates[index + 1]);
            maximumX = Math.max(maximumX, ordinates[index]);
            maximumY = Math.max(maximumY, ordinates[index + 1]);
        }
        return new Envelope(minimumX, minimumY, maximumX, maximumY);
    }

    private static byte encode(VectorPathCommand command) {
        return switch (command) {
            case MOVE_TO -> MOVE_TO_CODE;
            case LINE_TO -> LINE_TO_CODE;
            case QUADRATIC_TO -> QUADRATIC_TO_CODE;
            case CUBIC_TO -> CUBIC_TO_CODE;
            case CLOSE -> CLOSE_CODE;
        };
    }

    private static VectorPathCommand decode(byte code) {
        return switch (code) {
            case MOVE_TO_CODE -> VectorPathCommand.MOVE_TO;
            case LINE_TO_CODE -> VectorPathCommand.LINE_TO;
            case QUADRATIC_TO_CODE -> VectorPathCommand.QUADRATIC_TO;
            case CUBIC_TO_CODE -> VectorPathCommand.CUBIC_TO;
            case CLOSE_CODE -> VectorPathCommand.CLOSE;
            default -> throw new IllegalStateException("Unknown internal vector path command code");
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VectorPath path
                && Arrays.equals(commandCodes, path.commandCodes)
                && Arrays.equals(ordinates, path.ordinates);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(commandCodes) + Arrays.hashCode(ordinates);
    }

    @Override
    public String toString() {
        return "VectorPath{commands="
                + Arrays.toString(toCommandArray())
                + ", ordinates="
                + Arrays.toString(ordinates)
                + '}';
    }

    /** A mutable single-owner construction aid that publishes one immutable path. */
    public static final class Builder {
        private byte[] commands = new byte[8];
        private double[] ordinates = new double[16];
        private int commandCount;
        private int ordinateCount;
        private boolean active;
        private boolean closed;
        private boolean subpathHasSegment;
        private boolean consumed;

        private Builder() {}

        /** Starts a subpath at the supplied coordinate. */
        public Builder moveTo(double x, double y) {
            requireUsable();
            requireFinite(x, "x");
            requireFinite(y, "y");
            if (active && !subpathHasSegment) {
                throw new IllegalStateException("MOVE_TO cannot abandon an empty subpath");
            }
            append(VectorPathCommand.MOVE_TO, x, y);
            active = true;
            closed = false;
            subpathHasSegment = false;
            return this;
        }

        /** Adds a straight segment. */
        public Builder lineTo(double x, double y) {
            requireUsable();
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireDrawable(VectorPathCommand.LINE_TO);
            append(VectorPathCommand.LINE_TO, x, y);
            subpathHasSegment = true;
            return this;
        }

        /** Adds a quadratic curve segment. */
        public Builder quadraticTo(double controlX, double controlY, double x, double y) {
            requireUsable();
            requireFinite(controlX, "controlX");
            requireFinite(controlY, "controlY");
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireDrawable(VectorPathCommand.QUADRATIC_TO);
            append(VectorPathCommand.QUADRATIC_TO, controlX, controlY, x, y);
            subpathHasSegment = true;
            return this;
        }

        /** Adds a cubic curve segment. */
        public Builder cubicTo(
                double control1X,
                double control1Y,
                double control2X,
                double control2Y,
                double x,
                double y) {
            requireUsable();
            requireFinite(control1X, "control1X");
            requireFinite(control1Y, "control1Y");
            requireFinite(control2X, "control2X");
            requireFinite(control2Y, "control2Y");
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireDrawable(VectorPathCommand.CUBIC_TO);
            append(VectorPathCommand.CUBIC_TO, control1X, control1Y, control2X, control2Y, x, y);
            subpathHasSegment = true;
            return this;
        }

        /** Closes the active non-empty subpath. */
        public Builder close() {
            requireUsable();
            if (!active || closed || !subpathHasSegment) {
                throw new IllegalStateException("CLOSE requires a non-empty open subpath");
            }
            append(VectorPathCommand.CLOSE);
            closed = true;
            return this;
        }

        /** Validates and publishes the one immutable path produced by this builder. */
        public VectorPath build() {
            requireUsable();
            if (commandCount == 0 || !subpathHasSegment) {
                throw new IllegalStateException("builder requires a non-empty drawing subpath");
            }
            VectorPathCommand[] commandCopy = new VectorPathCommand[commandCount];
            for (int index = 0; index < commandCount; index++) {
                commandCopy[index] = decode(commands[index]);
            }
            VectorPath result = VectorPath.of(commandCopy, Arrays.copyOf(ordinates, ordinateCount));
            consumed = true;
            return result;
        }

        private void requireDrawable(VectorPathCommand command) {
            if (!active || closed) {
                throw new IllegalStateException(command + " requires an open subpath");
            }
        }

        private void append(VectorPathCommand command, double... values) {
            commands = ensureCapacity(commands, commandCount + 1);
            ordinates = ensureCapacity(ordinates, ordinateCount + values.length);
            commands[commandCount++] = encode(command);
            System.arraycopy(values, 0, ordinates, ordinateCount, values.length);
            ordinateCount += values.length;
        }

        private void requireUsable() {
            if (consumed) {
                throw new IllegalStateException("builder has already published a path");
            }
        }

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }

        private static byte[] ensureCapacity(byte[] values, int required) {
            return required <= values.length
                    ? values
                    : Arrays.copyOf(values, Math.max(required, values.length * 2));
        }

        private static double[] ensureCapacity(double[] values, int required) {
            return required <= values.length
                    ? values
                    : Arrays.copyOf(values, Math.max(required, values.length * 2));
        }
    }
}
