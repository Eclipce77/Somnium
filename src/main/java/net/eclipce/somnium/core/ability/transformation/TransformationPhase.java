package net.eclipce.somnium.core.ability.transformation;

/**
 * Represents the current phase of a transformation's lifecycle.
 *
 * <p>A transformation is a state machine that moves through these phases in order:</p>
 * <ol>
 *     <li>{@link #IDLE} — not transformed</li>
 *     <li>{@link #TRANSFORMING_IN} — playing the transform-in animation/sequence</li>
 *     <li>{@link #TRANSFORMED} — fully transformed, sustained state</li>
 *     <li>{@link #TRANSFORMING_OUT} — playing the de-transform animation/sequence</li>
 *     <li>Back to {@link #IDLE}</li>
 * </ol>
 *
 * <p>During the {@link #TRANSFORMING_IN} and {@link #TRANSFORMING_OUT} phases,
 * the player is in a transitional state — other transformation abilities are
 * disabled, and the transformation may or may not be cancelable depending on
 * the {@link TransformationAbilityType}'s configuration.</p>
 */
public enum TransformationPhase {

    /**
     * Not transformed. The default state.
     */
    IDLE,

    /**
     * The transformation sequence is playing. The player is transitioning
     * into their transformed form. Duration is defined by
     * {@link TransformationAbilityType#getTransformInDuration()}.
     */
    TRANSFORMING_IN,

    /**
     * Fully transformed. The player's model, attributes, and available
     * abilities reflect the transformed state. This phase persists until
     * the player de-transforms (manually or via trigger/duration).
     */
    TRANSFORMED,

    /**
     * The de-transformation sequence is playing. The player is reverting
     * to their normal form. Duration is defined by
     * {@link TransformationAbilityType#getTransformOutDuration()}.
     */
    TRANSFORMING_OUT;

    /**
     * @return {@code true} if the player is in any transformed state
     *         (including the transition phases)
     */
    public boolean isTransformed() {
        return this != IDLE;
    }

    /**
     * @return {@code true} if the player is in a transition phase
     *         (transforming in or out, not yet settled)
     */
    public boolean isTransitioning() {
        return this == TRANSFORMING_IN || this == TRANSFORMING_OUT;
    }

    /**
     * @return {@code true} if the player is fully transformed and in
     *         the sustained state (not transitioning)
     */
    public boolean isFullyTransformed() {
        return this == TRANSFORMED;
    }
}