package net.eclipce.somnium.core.meter;

import net.minecraft.nbt.CompoundTag;

/**
 * Stamina system with rolling 10-minute overuse window.
 *
 * <h3>Overuse Cycle</h3>
 * <p>Each time stamina goes negative, a 10-minute window starts (or resets).
 * If the player goes negative again within that window, the stage advances.
 * If 10 minutes pass without going negative, everything resets.</p>
 *
 * <ol>
 *     <li><b>1st negative:</b> Grace period — no Overuse effect, 5-second
 *         regen halt, 10-minute window starts.</li>
 *     <li><b>2nd negative (within 10 min):</b> Overuse I applied, window resets.</li>
 *     <li><b>3rd negative (within 10 min):</b> Overuse II, window resets.</li>
 *     <li>...up to Overuse V.</li>
 *     <li><b>10 minutes pass with no negatives:</b> Full reset — next negative
 *         is a fresh grace period.</li>
 * </ol>
 *
 * <p>Large drains (≥15% of max) skip the grace period.</p>
 *
 * <h3>Drain Modifier</h3>
 * <pre>{@code
 * data.getStaminaData().setDrainModifier(0.5f); // drain 50% slower
 * }</pre>
 */
public class StaminaData {

    // ═══════════════════════════════════════════════════════════════════
    //  Constants
    // ═══════════════════════════════════════════════════════════════════

    public static final float DEFAULT_MAX = 100f;
    public static final float DEFAULT_REGEN_RATE = 0.028f;
    public static final int DEFAULT_REGEN_DELAY = 60;
    public static final int MAX_OVERUSE_STAGE = 3;

    /** Grace period regen halt in ticks (5 seconds). */
    private static final int GRACE_HALT_TICKS = 100;

    /** Ticks per minute. */
    private static final int TICKS_PER_MINUTE = 1200;

    /** The 10-minute overuse window in ticks. */
    private static final int OVERUSE_WINDOW_TICKS = 10 * TICKS_PER_MINUTE; // 12000

    /**
     * Percentage of max stamina that counts as a "large drain".
     * If a single drain exceeds this, the grace period is skipped.
     */
    private static final float LARGE_DRAIN_THRESHOLD = 0.15f;

    // ═══════════════════════════════════════════════════════════════════
    //  State
    // ═══════════════════════════════════════════════════════════════════

    private float currentValue;
    private float maxValue;
    private float regenRate;
    private int regenDelay;
    private int regenCooldown;

    /** Multiplier applied to all stamina drain amounts. Default 1.0. */
    private float drainModifier = 1.0f;

    /**
     * Whether the grace period has been consumed in this window.
     * false = next negative triggers grace (no effect).
     * true = next negative triggers real overuse.
     */
    private boolean graceUsed = false;

    /** True if stamina is currently below 0. */
    private boolean inOveruse = false;

    /** Current overuse stage (0 = none/grace, 1-5 = Overuse I-V). */
    private int overuseStage = 0;

    /**
     * Ticks remaining in the 10-minute overuse window.
     * Resets to OVERUSE_WINDOW_TICKS each time the player goes negative.
     * When this hits 0 with no new negatives, everything resets.
     * -1 = no active window.
     */
    private int windowTimer = -1;

    /** Ticks remaining on regen halt (grace or post-overuse). */
    private int regenHaltTimer = 0;

    /**
     * Ticks remaining on the Overuse effect duration.
     * Set to ((stage + 1) * TICKS_PER_MINUTE) when entering overuse.
     */
    private int effectTimer = 0;

    // ── State transition flags (consumed once by tick handler) ──
    private boolean justEnteredOveruse = false;
    private boolean effectsJustExpired = false;
    private boolean justEnteredGrace = false;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════════

    public StaminaData() {
        this.maxValue = DEFAULT_MAX;
        this.currentValue = DEFAULT_MAX;
        this.regenRate = DEFAULT_REGEN_RATE;
        this.regenDelay = DEFAULT_REGEN_DELAY;
        this.regenCooldown = this.regenDelay;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    public float getValue() { return currentValue; }
    public float getMaxValue() { return maxValue; }
    public boolean isEmpty() { return currentValue <= 0; }
    public boolean isFull() { return currentValue >= maxValue; }
    public boolean isInOveruse() { return inOveruse; }
    public int getOveruseStage() { return overuseStage; }
    public float getDrainModifier() { return drainModifier; }
    public int getEffectTimer() { return effectTimer; }
    public int getRegenHaltTimer() { return regenHaltTimer; }
    public boolean isGraceUsed() { return graceUsed; }
    public int getWindowTimer() { return windowTimer; }

    /** True if overuse effects are currently active. */
    public boolean areEffectsActive() {
        return overuseStage > 0 && (inOveruse || effectTimer > 0);
    }

    /** Fill fraction for rendering (0-1, clamped). */
    public float getFraction() {
        if (maxValue <= 0) return 0;
        return Math.max(0, currentValue / maxValue);
    }

    /** Consumes and returns the justEnteredOveruse flag. */
    public boolean consumeEnteredOveruse() {
        boolean val = justEnteredOveruse;
        justEnteredOveruse = false;
        return val;
    }

    /** Consumes and returns the effectsJustExpired flag. */
    public boolean consumeEffectsExpired() {
        boolean val = effectsJustExpired;
        effectsJustExpired = false;
        return val;
    }

    /** Consumes and returns the justEnteredGrace flag. */
    public boolean consumeEnteredGrace() {
        boolean val = justEnteredGrace;
        justEnteredGrace = false;
        return val;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Value manipulation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Drains stamina, applying the drain modifier. Can push below 0.
     *
     * <p>If stamina ≥ 0 before the drain and goes negative after:</p>
     * <ul>
     *     <li>First time in window (grace not used): grace period — no effect,
     *         short regen halt, 10-min window starts.</li>
     *     <li>Large drain (≥15% max): skips grace, goes to Stage 1.</li>
     *     <li>Subsequent negatives within window: advances overuse stage.</li>
     * </ul>
     */
    public float drain(float amount) {
        if (amount <= 0) return 0;
        float modified = amount * drainModifier;
        float oldValue = currentValue;
        currentValue -= modified;
        regenCooldown = 0;

        // Check if we just crossed from positive/zero into negative
        if (currentValue < 0 && oldValue >= 0) {
            onWentNegative(modified);
        }

        return modified;
    }

    /**
     * Drains stamina WITHOUT triggering the overuse system.
     * The drain still applies (stamina goes negative if needed)
     * and abilities are still blocked while negative, but no grace/overuse
     * processing occurs. Useful for utility abilities, self-heals, etc.
     *
     * @param amount the base amount to drain (before modifier)
     * @return the actual amount drained
     */
    public float drainWithoutOveruse(float amount) {
        if (amount <= 0) return 0;
        float modified = amount * drainModifier;
        currentValue -= modified;
        regenCooldown = 0;
        return modified;
    }

    /**
     * Called when stamina crosses from ≥0 to <0.
     * Handles grace, overuse entry, and window management.
     */
    private void onWentNegative(float drainAmount) {
        if (!graceUsed) {
            // First negative in this cycle
            boolean isLargeDrain = drainAmount >= (maxValue * LARGE_DRAIN_THRESHOLD);
            if (isLargeDrain) {
                // Large drain — skip grace, enter overuse directly
                graceUsed = true;
                windowTimer = OVERUSE_WINDOW_TICKS;
                enterOveruse();
            } else {
                // Normal drain — grace period
                graceUsed = true;
                windowTimer = OVERUSE_WINDOW_TICKS;
                regenHaltTimer = GRACE_HALT_TICKS;
                justEnteredGrace = true;
                // No overuse effect — just blocked until regen recovers
            }
        } else {
            // Grace already used — enter/advance overuse
            windowTimer = OVERUSE_WINDOW_TICKS; // reset window
            if (!inOveruse) {
                enterOveruse();
            }
        }
    }

    /** Adds to stamina. Clamps at max. */
    public float add(float amount) {
        if (amount <= 0) return 0;
        float added = Math.min(maxValue - currentValue, amount);
        currentValue += added;
        if (currentValue >= 0 && inOveruse) {
            leaveOveruse();
        }
        return added;
    }

    /** Sets stamina directly. */
    public void setValue(float value) {
        currentValue = Math.min(maxValue, value);
        if (currentValue >= 0 && inOveruse) leaveOveruse();
    }

    public void setMaxValue(float max) {
        this.maxValue = Math.max(1, max);
        if (currentValue > maxValue) currentValue = maxValue;
    }

    public void setRegenRate(float rate) { this.regenRate = rate; }
    public void setRegenDelay(int ticks) { this.regenDelay = ticks; }
    public void setDrainModifier(float modifier) {
        this.drainModifier = Math.max(0f, modifier);
    }

    /**
     * Can the player use a stamina-costing ability?
     * Only blocked if stamina is already negative.
     */
    public boolean canAfford(float requiredMin) {
        return currentValue >= 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Overuse management
    // ═══════════════════════════════════════════════════════════════════

    private void enterOveruse() {
        inOveruse = true;
        overuseStage = Math.min(overuseStage + 1, MAX_OVERUSE_STAGE);
        effectTimer = (overuseStage + 1) * TICKS_PER_MINUTE;
        justEnteredOveruse = true;
    }

    private void leaveOveruse() {
        inOveruse = false;
        regenHaltTimer = overuseStage * TICKS_PER_MINUTE;
    }

    /** Resets all overuse state. */
    private void fullReset() {
        overuseStage = 0;
        graceUsed = false;
        inOveruse = false;
        windowTimer = -1;
        regenHaltTimer = 0;
        effectTimer = 0;
        justEnteredOveruse = false;
        effectsJustExpired = false;
        justEnteredGrace = false;
    }

    /** Public reset for commands. */
    public void resetOveruse() {
        fullReset();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tick
    // ═══════════════════════════════════════════════════════════════════

    /** Server-side tick. */
    public void tick() {
        // Tick the 10-minute window
        if (windowTimer > 0) {
            windowTimer--;
            if (windowTimer <= 0 && !inOveruse && currentValue >= 0) {
                // Window expired with no new negatives — full reset
                boolean hadEffects = effectTimer > 0;
                fullReset();
                if (hadEffects) {
                    effectsJustExpired = true;
                }
            }
        }

        // Tick effect timer (separate from window — effects can outlast window)
        if (effectTimer > 0) {
            effectTimer--;
            if (effectTimer <= 0 && !inOveruse) {
                effectsJustExpired = true;
            }
        }

        // Tick regen halt timer
        if (regenHaltTimer > 0) {
            regenHaltTimer--;
        }

        // Regeneration — blocked while overuse effects are active
        if (currentValue < maxValue && regenHaltTimer <= 0 && !areEffectsActive()) {
            if (regenCooldown >= regenDelay) {
                currentValue += regenRate;
                if (currentValue > maxValue) currentValue = maxValue;
                if (currentValue >= 0 && inOveruse) {
                    leaveOveruse();
                }
            } else {
                regenCooldown++;
            }
        }
    }

    /** Client-side tick for smooth rendering. */
    public void clientTick() {
        if (windowTimer > 0) windowTimer--;
        if (effectTimer > 0) effectTimer--;
        if (regenHaltTimer > 0) regenHaltTimer--;

        if (currentValue < maxValue && regenHaltTimer <= 0 && !areEffectsActive()) {
            if (regenCooldown >= regenDelay) {
                currentValue += regenRate;
                if (currentValue > maxValue) currentValue = maxValue;
            } else {
                regenCooldown++;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Serialization
    // ═══════════════════════════════════════════════════════════════════

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Value", currentValue);
        tag.putFloat("Max", maxValue);
        tag.putInt("RegenCooldown", regenCooldown);
        tag.putFloat("DrainModifier", drainModifier);
        tag.putBoolean("GraceUsed", graceUsed);
        tag.putBoolean("InOveruse", inOveruse);
        tag.putInt("OveruseStage", overuseStage);
        tag.putInt("WindowTimer", windowTimer);
        tag.putInt("RegenHaltTimer", regenHaltTimer);
        tag.putInt("EffectTimer", effectTimer);
        return tag;
    }

    public static StaminaData deserialize(CompoundTag tag) {
        StaminaData data = new StaminaData();
        data.currentValue = tag.getFloat("Value");
        data.maxValue = tag.getFloat("Max");
        data.regenCooldown = tag.getInt("RegenCooldown");
        data.drainModifier = tag.contains("DrainModifier")
                ? tag.getFloat("DrainModifier") : 1.0f;
        data.graceUsed = tag.getBoolean("GraceUsed");
        data.inOveruse = tag.getBoolean("InOveruse");
        data.overuseStage = tag.getInt("OveruseStage");
        data.windowTimer = tag.getInt("WindowTimer");
        data.regenHaltTimer = tag.getInt("RegenHaltTimer");
        data.effectTimer = tag.getInt("EffectTimer");
        return data;
    }

    public void copyFrom(StaminaData source) {
        this.currentValue = source.currentValue;
        this.maxValue = source.maxValue;
        this.regenCooldown = source.regenCooldown;
        this.drainModifier = source.drainModifier;
        this.graceUsed = source.graceUsed;
        this.inOveruse = source.inOveruse;
        this.overuseStage = source.overuseStage;
        this.windowTimer = source.windowTimer;
        this.regenHaltTimer = source.regenHaltTimer;
        this.effectTimer = source.effectTimer;
    }
}