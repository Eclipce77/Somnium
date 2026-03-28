package net.eclipce.somnium.core.ability.transformation;

import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-player state for a transformation ability. Extends {@link AbilityInstance}
 * with transformation-specific fields: the current {@link TransformationPhase},
 * how many ticks the current phase has lasted, trigger cooldown tracking, and
 * whether the current activation was forced by a {@link TransformationTrigger}.
 *
 * <p>Created automatically when a {@link TransformationAbilityType} calls
 * {@link TransformationAbilityType#createInstance()}. Addon developers generally
 * don't create these directly — the API manages them.</p>
 *
 * <p>Serializes its transformation-specific state via the
 * {@link #writeAdditionalData}/{@link #readAdditionalData} hooks added
 * to AbilityInstance, so all transformation state survives save/load cycles.</p>
 *
 * @see TransformationAbilityType
 * @see TransformationPhase
 */
public class TransformationInstance extends AbilityInstance {

    // ═══════════════════════════════════════════════════════════════════
    //  NBT keys for transformation-specific serialization
    // ═══════════════════════════════════════════════════════════════════

    private static final String TAG_PHASE = "TransPhase";
    private static final String TAG_PHASE_TICKS = "TransPhaseTicks";
    private static final String TAG_TRIGGER_COOLDOWN = "TransTriggerCooldown";
    private static final String TAG_TRIGGERED_BY_CONDITION = "TransTriggered";
    private static final String TAG_TOTAL_TRANSFORMED_TICKS = "TransTotalTicks";

    // ═══════════════════════════════════════════════════════════════════
    //  Fields
    // ═══════════════════════════════════════════════════════════════════

    /** The current phase of the transformation lifecycle. */
    private TransformationPhase phase;

    /** How many ticks the current phase has been active. Reset on phase change. */
    private int phaseTicks;

    /**
     * Total ticks spent in the TRANSFORMED state during the current
     * transformation. Used for max duration checking. Reset when
     * entering TRANSFORMING_IN.
     */
    private int totalTransformedTicks;

    /**
     * Remaining cooldown ticks before the trigger can force-activate again.
     * Only relevant for transformations with a {@link TransformationTrigger}.
     * Decrements each tick while the player is not transformed.
     */
    private int triggerCooldownRemaining;

    /**
     * Whether the current (or most recent) activation was forced by a
     * trigger condition rather than voluntary player input.
     */
    private boolean triggeredByCondition;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new TransformationInstance for the given type.
     * Called by {@link TransformationAbilityType#createInstance()}.
     *
     * @param abilityType the transformation type (must be a TransformationAbilityType)
     */
    public TransformationInstance(AbilityType abilityType) {
        super(abilityType);
        this.phase = TransformationPhase.IDLE;
        this.phaseTicks = 0;
        this.totalTransformedTicks = 0;
        this.triggerCooldownRemaining = 0;
        this.triggeredByCondition = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Phase management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return the current transformation phase
     */
    public TransformationPhase getPhase() {
        return phase;
    }

    /**
     * Sets the transformation phase. Also resets the phase tick counter.
     * Called by the transformation system during phase transitions.
     *
     * @param phase the new phase
     */
    public void setPhase(TransformationPhase phase) {
        this.phase = phase;
        this.phaseTicks = 0;
    }

    /**
     * @return how many ticks the current phase has been active
     */
    public int getPhaseTicks() {
        return phaseTicks;
    }

    /**
     * Increments the phase tick counter by one. Called each tick by
     * the transformation tick system.
     */
    public void incrementPhaseTicks() {
        phaseTicks++;
    }

    /**
     * Resets the phase tick counter to zero.
     */
    public void resetPhaseTicks() {
        phaseTicks = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Total transformed time
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return total ticks spent in the TRANSFORMED state during the
     *         current transformation cycle. Used for max duration checking.
     */
    public int getTotalTransformedTicks() {
        return totalTransformedTicks;
    }

    /**
     * Increments the total transformed tick counter. Called each tick
     * while in the TRANSFORMED phase.
     */
    public void incrementTotalTransformedTicks() {
        totalTransformedTicks++;
    }

    /**
     * Resets the total transformed tick counter. Called when starting
     * a new transformation (entering TRANSFORMING_IN).
     */
    public void resetTotalTransformedTicks() {
        totalTransformedTicks = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Trigger cooldown
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return remaining ticks before the trigger can force-activate again
     */
    public int getTriggerCooldownRemaining() {
        return triggerCooldownRemaining;
    }

    /**
     * @return {@code true} if the trigger is currently on cooldown
     */
    public boolean isTriggerOnCooldown() {
        return triggerCooldownRemaining > 0;
    }

    /**
     * Starts the trigger cooldown. Called when a triggered transformation
     * ends (either by de-transforming or being canceled).
     *
     * @param ticks the cooldown duration in ticks
     */
    public void startTriggerCooldown(int ticks) {
        this.triggerCooldownRemaining = Math.max(0, ticks);
    }

    /**
     * Decrements the trigger cooldown by one tick. Called each tick
     * while the player is not transformed and the cooldown is active.
     */
    public void tickTriggerCooldown() {
        if (triggerCooldownRemaining > 0) {
            triggerCooldownRemaining--;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Triggered state
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return {@code true} if the current activation was forced by a trigger
     *         condition rather than voluntary player input
     */
    public boolean isTriggeredByCondition() {
        return triggeredByCondition;
    }

    /**
     * Sets whether the current activation was forced by a trigger.
     *
     * @param triggered {@code true} if this was a forced activation
     */
    public void setTriggeredByCondition(boolean triggered) {
        this.triggeredByCondition = triggered;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Convenience methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return {@code true} if the player is in any transformed state
     *         (including transition phases)
     */
    public boolean isTransformed() {
        return phase.isTransformed();
    }

    /**
     * @return {@code true} if the player is fully transformed (not
     *         in a transition phase)
     */
    public boolean isFullyTransformed() {
        return phase.isFullyTransformed();
    }

    /**
     * @return {@code true} if the player is in a transition phase
     *         (transforming in or out)
     */
    public boolean isTransitioning() {
        return phase.isTransitioning();
    }

    /**
     * Returns the transformation's phase completion progress as a float
     * from 0.0 to 1.0. Useful for rendering transition effects.
     *
     * <p>During TRANSFORMING_IN: 0.0 = just started, 1.0 = about to complete.</p>
     * <p>During TRANSFORMING_OUT: 0.0 = just started reverting, 1.0 = about to finish.</p>
     * <p>During TRANSFORMED: always returns 1.0.</p>
     * <p>During IDLE: always returns 0.0.</p>
     *
     * @return the phase progress (0.0 to 1.0)
     */
    public float getPhaseProgress() {
        if (!(getAbilityType() instanceof TransformationAbilityType transType)) {
            return 0f;
        }
        return switch (phase) {
            case IDLE -> 0f;
            case TRANSFORMING_IN -> transType.getTransformInDuration() > 0
                    ? (float) phaseTicks / transType.getTransformInDuration()
                    : 1f;
            case TRANSFORMED -> 1f;
            case TRANSFORMING_OUT -> transType.getTransformOutDuration() > 0
                    ? (float) phaseTicks / transType.getTransformOutDuration()
                    : 1f;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Serialization hooks
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void writeAdditionalData(CompoundTag tag) {
        super.writeAdditionalData(tag);
        tag.putString(TAG_PHASE, phase.name());
        tag.putInt(TAG_PHASE_TICKS, phaseTicks);
        tag.putInt(TAG_TOTAL_TRANSFORMED_TICKS, totalTransformedTicks);
        tag.putInt(TAG_TRIGGER_COOLDOWN, triggerCooldownRemaining);
        tag.putBoolean(TAG_TRIGGERED_BY_CONDITION, triggeredByCondition);
    }

    @Override
    protected void readAdditionalData(CompoundTag tag) {
        super.readAdditionalData(tag);

        if (tag.contains(TAG_PHASE)) {
            try {
                this.phase = TransformationPhase.valueOf(tag.getString(TAG_PHASE));
            } catch (IllegalArgumentException e) {
                // Invalid phase name in save data — reset to IDLE
                this.phase = TransformationPhase.IDLE;
            }
        }

        this.phaseTicks = tag.getInt(TAG_PHASE_TICKS);
        this.totalTransformedTicks = tag.getInt(TAG_TOTAL_TRANSFORMED_TICKS);
        this.triggerCooldownRemaining = tag.getInt(TAG_TRIGGER_COOLDOWN);
        this.triggeredByCondition = tag.getBoolean(TAG_TRIGGERED_BY_CONDITION);
    }
}