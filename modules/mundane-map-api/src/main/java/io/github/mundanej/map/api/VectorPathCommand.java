package io.github.mundanej.map.api;

/** A command in an immutable toolkit-neutral vector path. */
public enum VectorPathCommand {
    /** Starts a subpath at one x/y pair. */
    MOVE_TO(2),
    /** Adds a straight segment ending at one x/y pair. */
    LINE_TO(2),
    /** Adds a quadratic curve with one control and one end x/y pair. */
    QUADRATIC_TO(4),
    /** Adds a cubic curve with two controls and one end x/y pair. */
    CUBIC_TO(6),
    /** Closes the active subpath. */
    CLOSE(0);

    private final int arity;

    VectorPathCommand(int arity) {
        this.arity = arity;
    }

    /**
     * Returns the number of packed ordinates consumed by this command.
     *
     * @return non-negative operand arity
     */
    public int arity() {
        return arity;
    }
}
