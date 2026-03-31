package net.eclipce.somnium.client.config;

/**
 * Defines where the ability bar is positioned on screen.
 *
 * <p>When positioned at the top of the screen ({@link #TOP_LEFT} or
 * {@link #TOP_RIGHT}), the bar texture is flipped vertically so the
 * rounded end faces the screen edge.</p>
 */
public enum BarPosition {
    /** Bottom-right corner of the screen (default). */
    BOTTOM_RIGHT,
    /** Bottom-left corner of the screen. */
    BOTTOM_LEFT,
    /** Top-right corner, bar texture flipped vertically. */
    TOP_RIGHT,
    /** Top-left corner, bar texture flipped vertically. */
    TOP_LEFT;

    /**
     * @return {@code true} if the bar is on the right side of the screen
     */
    public boolean isRight() {
        return this == BOTTOM_RIGHT || this == TOP_RIGHT;
    }

    /**
     * @return {@code true} if the bar is at the top of the screen
     *         (texture should be flipped vertically)
     */
    public boolean isTop() {
        return this == TOP_LEFT || this == TOP_RIGHT;
    }
}