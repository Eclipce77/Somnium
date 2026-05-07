package net.eclipce.somnium.core.composition;

import net.minecraft.nbt.CompoundTag;

/**
 * Composition — a scalable value unique to each player that directly
 * determines their stamina capacity. Starts at 0 (100 base stamina)
 * and grows through gameplay, with diminishing returns at higher values.
 *
 * <p><b>Stamina formula:</b> {@code maxStamina = BASE_STAMINA + (int)composition}</p>
 *
 * <h3>Growth sources</h3>
 * <ul>
 *     <li><b>Ability Usage:</b> +0.5 base per activation</li>
 *     <li><b>Stamina Recovery:</b> +3.0 base per full 0→max cycle</li>
 *     <li><b>Overuse Survival:</b> +15 base per stage when overuse ends</li>
 *     <li><b>Milestones:</b> addon-dev defined one-time grants</li>
 * </ul>
 *
 * <p>All gains are multiplied by a diminishing returns multiplier:
 * {@code 1.0 / (1.0 + (composition / DIMINISHING_DIVISOR))}.</p>
 *
 * <h3>Death penalty</h3>
 * <p>Controlled by gamerule {@code somniumCompositionLossOnDeath} (default true)
 * and config values for loss percentage and cap.</p>
 *
 * <h3>Addon dev usage</h3>
 * <pre>{@code
 * // Read composition for custom meter scaling
 * double comp = data.getComposition().getValue();
 * myMeter.setMaxValue(50 + (float)(comp * 0.5));
 *
 * // Grant milestone composition
 * data.getComposition().addGrowth(50.0, CompositionSource.MILESTONE);
 * }</pre>
 */
public class CompositionData {

    // ═══════════════════════════════════════════════════════════════════
    //  Constants — tune these to adjust game feel
    // ═══════════════════════════════════════════════════════════════════

    /** Base stamina every player starts with. */
    public static final int BASE_STAMINA = 100;

    /**
     * Divisor for diminishing returns curve.
     * Higher = slower diminishing. At composition == this value,
     * growth rate is exactly 50% of base.
     */
    public static final double DIMINISHING_DIVISOR = 1000.0;

    /** Base composition gain per ability activation. */
    public static final double GAIN_ABILITY_USE = 0.05;

    /** Base composition gain per full stamina recovery cycle (0→max). */
    public static final double GAIN_STAMINA_RECOVERY = 0.5;

    /** Base composition gain per overuse stage survived. */
    public static final double GAIN_OVERUSE_SURVIVAL_PER_STAGE = 5.0;

    /** Default death loss percentage (0.0 to 1.0). */
    public static final double DEFAULT_DEATH_LOSS_PERCENT = 0.05;

    /** Default maximum composition points lost on death. */
    public static final double DEFAULT_DEATH_LOSS_CAP = 50.0;

    // ═══════════════════════════════════════════════════════════════════
    //  State
    // ═══════════════════════════════════════════════════════════════════

    /** The raw composition value. Fractional part accumulates toward next whole number. */
    private double value = 0.0;

    /**
     * Tracks whether the player has completed a full stamina recovery
     * cycle (went to 0 or below, then recovered to max).
     * Set to true when stamina hits ≤0, consumed when stamina reaches max.
     */
    private boolean staminaRecoveryCyclePending = false;

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /** @return the raw composition value (double precision) */
    public double getValue() { return value; }

    /** @return the integer part of composition (used for stamina calculation) */
    public int getIntValue() { return (int) value; }

    /** @return the player's effective max stamina: base + composition */
    public float getEffectiveMaxStamina() {
        return BASE_STAMINA + (float) getIntValue();
    }

    /**
     * Returns the current growth multiplier based on diminishing returns.
     * Ranges from 1.0 (at composition 0) down toward 0 (never reaches 0).
     */
    public double getGrowthMultiplier() {
        return 1.0 / (1.0 + (value / DIMINISHING_DIVISOR));
    }

    public boolean isStaminaRecoveryCyclePending() { return staminaRecoveryCyclePending; }

    // ═══════════════════════════════════════════════════════════════════
    //  Growth
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds composition growth with diminishing returns applied.
     *
     * @param baseAmount the base amount before diminishing returns
     * @param source     the source of the growth (for events)
     * @return the actual amount added (after diminishing)
     */
    public double addGrowth(double baseAmount, CompositionSource source) {
        double multiplier = getGrowthMultiplier();
        double actual = baseAmount * multiplier;
        double oldValue = value;
        value += actual;
        return actual;
    }

    /**
     * Adds composition directly without diminishing returns.
     * Use for commands, admin tools, or special milestone grants
     * where the addon dev wants exact control.
     *
     * @param amount the exact amount to add
     */
    public void addDirect(double amount) {
        value += amount;
        if (value < 0) value = 0;
    }

    /**
     * Sets composition to an exact value. Use for commands.
     */
    public void setValue(double newValue) {
        this.value = Math.max(0, newValue);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Stamina recovery tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when stamina hits ≤0. Marks that a recovery cycle has started.
     */
    public void markStaminaDepleted() {
        staminaRecoveryCyclePending = true;
    }

    /**
     * Called when stamina reaches max. If a depletion was pending,
     * grants recovery composition and clears the flag.
     *
     * @return true if a recovery cycle was completed (composition was granted)
     */
    public boolean completeRecoveryCycle() {
        if (staminaRecoveryCyclePending) {
            staminaRecoveryCyclePending = false;
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Death penalty
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies death penalty. Removes a percentage of composition,
     * capped at a maximum amount.
     *
     * @param lossPercent percentage to lose (0.0 to 1.0)
     * @param lossCap     maximum points to lose
     * @return the actual amount lost
     */
    public double applyDeathPenalty(double lossPercent, double lossCap) {
        double loss = value * lossPercent;
        if (loss > lossCap) loss = lossCap;
        value -= loss;
        if (value < 0) value = 0;
        return loss;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Serialization
    // ═══════════════════════════════════════════════════════════════════

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Value", value);
        tag.putBoolean("RecoveryCyclePending", staminaRecoveryCyclePending);
        return tag;
    }

    public static CompositionData deserialize(CompoundTag tag) {
        CompositionData data = new CompositionData();
        data.value = tag.getDouble("Value");
        data.staminaRecoveryCyclePending = tag.getBoolean("RecoveryCyclePending");
        return data;
    }

    public void copyFrom(CompositionData source) {
        this.value = source.value;
        this.staminaRecoveryCyclePending = source.staminaRecoveryCyclePending;
    }
}