package io.github.mundanej.map.symbology.milstd2525;

import io.github.mundanej.map.api.Rgba;
import java.util.Objects;

/** Immutable approved colors for the bounded MIL-STD-2525 rendering profile. */
public final class MilitarySymbolPalette {
    private static final MilitarySymbolPalette LIGHT =
            new MilitarySymbolPalette(
                    Rgba.rgb(225, 220, 0),
                    Rgba.rgb(0, 107, 140),
                    Rgba.rgb(0, 160, 0),
                    Rgba.rgb(255, 188, 1),
                    Rgba.rgb(200, 0, 0),
                    Rgba.rgb(0, 0, 0));
    private static final MilitarySymbolPalette DARK =
            new MilitarySymbolPalette(
                    Rgba.rgb(255, 255, 128),
                    Rgba.rgb(128, 225, 255),
                    Rgba.rgb(170, 255, 170),
                    Rgba.rgb(255, 229, 153),
                    Rgba.rgb(255, 128, 128),
                    Rgba.rgb(255, 255, 255));

    private final Rgba unknown;
    private final Rgba friend;
    private final Rgba neutral;
    private final Rgba suspect;
    private final Rgba hostile;
    private final Rgba ink;

    private MilitarySymbolPalette(
            Rgba unknown, Rgba friend, Rgba neutral, Rgba suspect, Rgba hostile, Rgba ink) {
        this.unknown = Objects.requireNonNull(unknown, "unknown");
        this.friend = Objects.requireNonNull(friend, "friend");
        this.neutral = Objects.requireNonNull(neutral, "neutral");
        this.suspect = Objects.requireNonNull(suspect, "suspect");
        this.hostile = Objects.requireNonNull(hostile, "hostile");
        this.ink = Objects.requireNonNull(ink, "ink");
    }

    /**
     * Returns colors intended for a light map background.
     *
     * @return shared immutable palette
     */
    public static MilitarySymbolPalette lightBackground() {
        return LIGHT;
    }

    /**
     * Returns colors intended for a dark map background.
     *
     * @return shared immutable palette
     */
    public static MilitarySymbolPalette darkBackground() {
        return DARK;
    }

    /**
     * Returns the pending/unknown frame fill.
     *
     * @return immutable color
     */
    public Rgba unknown() {
        return unknown;
    }

    /**
     * Returns the friend/assumed-friend frame fill.
     *
     * @return immutable color
     */
    public Rgba friend() {
        return friend;
    }

    /**
     * Returns the neutral frame fill.
     *
     * @return immutable color
     */
    public Rgba neutral() {
        return neutral;
    }

    /**
     * Returns the suspect frame fill.
     *
     * @return immutable color
     */
    public Rgba suspect() {
        return suspect;
    }

    /**
     * Returns the hostile frame fill.
     *
     * @return immutable color
     */
    public Rgba hostile() {
        return hostile;
    }

    /**
     * Returns the frame, icon, and modifier line color.
     *
     * @return immutable color
     */
    public Rgba ink() {
        return ink;
    }

    Rgba fillForIdentity(int identity) {
        return switch (identity) {
            case 0, 1 -> unknown;
            case 2, 3 -> friend;
            case 4 -> neutral;
            case 5 -> suspect;
            case 6 -> hostile;
            default -> throw new IllegalArgumentException("unsupported standard identity");
        };
    }
}
