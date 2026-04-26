package net.eclipce.somnium.core.meter;

import net.minecraft.nbt.CompoundTag;

/**
 * Stamina-specific data with the overuse system.
 *
 * <h3>Overuse System</h3>
 * <p>Stamina can go below 0 (overuse). While in overuse, all stamina-costing
 * abilities are blocked and regen is 10x slower. Each time the player enters
 * overuse, the stage advances (max 5). Stages persist until a full reset.</p>
 *
 * <h3>Stage Effects</h3>
 * <ul>
 *     <li>Stage 1: Slowness</li>
 *     <li>Stage 2: Slowness + Hunger</li>
 *     <li>Stage 3: Slowness II + Hunger + Weakness</li>
 *     <li>Stage 4: Slowness II + Hunger II + Weakness II</li>
 *     <li>Stage 5: Slowness II + Hunger II + Weakness II + Wither</li>
 * </ul>
 *
 * <h3>Regen Halt</h3>
 * <p>After leaving overuse, regen is halted for (stage) minutes.
 * Stage 1 = 1 minute halt, Stage 2 = 2 minutes, etc.</p>
 *
 * <h3>Effect Duration</h3>
 * <p>Stage effects last for (stage + 1) minutes after leaving overuse.
 * Re-entering overuse advances the stage, removes old effects, and
 * applies new stage effects.</p>
 *
 * <h3>Drain Modifier</h3>
 * <pre>{@code
 * data.getStaminaData().setDrainModifier(0.5f); // drain 50% slower
 * data.getStaminaData().setDrainModifier(1.0f); // reset to normal
 * }</pre>
 */
public class StaminaData {

    // ═══════════════════════════════════════════════════════════════════
    //  Constants
    // ═══════════════════════════════════════════════════════════════════

    public static final float DEFAULT_MAX = 100f;
    public static final float DEFAULT_REGEN_RATE = 0.05f; // 1/sec, ~100s full regen
    public static final int DEFAULT_REGEN_DELAY = 60;     // 3 seconds
    public static final float OVERUSE_REGEN_MULTIPLIER = 0.1f; // 10x slower
    public static final int MAX_OVERUSE_STAGE = 5;

    /** Ticks per minute. */
    private static final int TICKS_PER_MINUTE = 1200;

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

    /** True if stamina is currently below 0. */
    private boolean inOveruse = false;

    /** Current overuse stage (0 = none, 1-5 = penalty stages). */
    private int overuseStage = 0;

    /**
     * Ticks remaining on the regen halt after leaving overuse.
     * Set to (stage * TICKS_PER_MINUTE) when leaving overuse.
     * Regen is completely blocked while this is > 0.
     */
    private int regenHaltTimer = 0;

    /**
     * Ticks remaining on the overuse effect duration.
     * Set to ((stage + 1) * TICKS_PER_MINUTE) when entering overuse.
     * While > 0, stage effects should be active. When it hits 0, effects expire.
     */
    private int effectTimer = 0;

    // ── State transition flags (consumed by tick handler) ──

    /** Set to true when the player just entered a NEW overuse stage. */
    private boolean justEnteredOveruse = false;

    /** Set to true when overuse effects just expired (effectTimer hit 0). */
    private boolean effectsJustExpired = false;

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

    /** @return true if stage effects are currently active (in overuse or effect timer > 0) */
    public boolean areEffectsActive() {
        return inOveruse || effectTimer > 0;
    }

    /**
     * Returns the fill fraction for rendering. Clamps to 0-1 range.
     */
    public float getFraction() {
        if (maxValue <= 0) return 0;
        return Math.max(0, currentValue / maxValue);
    }

    // ── Transition flag consumers (call once, then flag resets) ──

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

    // ═══════════════════════════════════════════════════════════════════
    //  Value manipulation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Drains stamina, applying the drain modifier. Can push below 0 (overuse).
     *
     * @param amount the base amount to drain (before modifier)
     * @return the actual amount drained (after modifier)
     */
    public float drain(float amount) {
        if (amount <= 0) return 0;
        float modified = amount * drainModifier;
        currentValue -= modified;
        regenCooldown = 0;

        if (currentValue < 0 && !inOveruse) {
            enterOveruse();
        }
        return modified;
    }

    /**
     * Adds to stamina. Clamps at max.
     */
    public float add(float amount) {
        if (amount <= 0) return 0;
        float added = Math.min(maxValue - currentValue, amount);
        currentValue += added;
        if (currentValue >= 0 && inOveruse) {
            leaveOveruse();
        }
        return added;
    }

    public void setValue(float value) {
        currentValue = Math.min(maxValue, value);
        if (currentValue < 0 && !inOveruse) enterOveruse();
        if (currentValue >= 0 && inOveruse) leaveOveruse();
    }

    public void setMaxValue(float max) {
        this.maxValue = Math.max(1, max);
        if (currentValue > maxValue) currentValue = maxValue;
    }

    public void setRegenRate(float rate) { this.regenRate = rate; }
    public void setRegenDelay(int ticks) { this.regenDelay = ticks; }
    public void setDrainModifier(float modifier) { this.drainModifier = Math.max(0f, modifier); }

    /**
     * Checks if the player can use a stamina-costing ability.
     * Blocked while in overuse (below 0).
     */
    public boolean canAfford(float requiredMin) {
        if (inOveruse) return false;
        return currentValue >= requiredMin;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Overuse management
    // ═══════════════════════════════════════════════════════════════════

    private void enterOveruse() {
        inOveruse = true;

        // Advance stage (capped at MAX)
        overuseStage = Math.min(overuseStage + 1, MAX_OVERUSE_STAGE);

        // Set effect timer: (stage + 1) minutes
        effectTimer = (overuseStage + 1) * TICKS_PER_MINUTE;

        // Signal the tick handler to apply new effects
        justEnteredOveruse = true;
    }

    private void leaveOveruse() {
        inOveruse = false;

        // Start regen halt: (stage) minutes
        regenHaltTimer = overuseStage * TICKS_PER_MINUTE;

        // Effect timer continues counting down from its current value
        // (it was set on enter, now counts down through recovery)
    }

    /**
     * Resets all overuse state. Called by commands or special abilities.
     */
    public void resetOveruse() {
        overuseStage = 0;
        inOveruse = false;
        regenHaltTimer = 0;
        effectTimer = 0;
        justEnteredOveruse = false;
        effectsJustExpired = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tick
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ticks stamina regeneration, regen halt, and effect timers.
     * Call once per server tick.
     */
    public void tick() {
        // Tick effect timer
        if (effectTimer > 0) {
            effectTimer--;
            if (effectTimer <= 0) {
                effectsJustExpired = true;
                // If we're not in overuse and effects just expired, reset stage
                if (!inOveruse) {
                    overuseStage = 0;
                }
            }
        }

        // Tick regen halt timer
        if (regenHaltTimer > 0) {
            regenHaltTimer--;
        }

        // Regeneration
        if (currentValue < maxValue && regenHaltTimer <= 0) {
            if (regenCooldown >= regenDelay) {
                float rate = regenRate;
                if (inOveruse) {
                    rate *= OVERUSE_REGEN_MULTIPLIER;
                }
                currentValue += rate;
                if (currentValue > maxValue) currentValue = maxValue;

                if (currentValue >= 0 && inOveruse) {
                    leaveOveruse();
                }
            } else {
                regenCooldown++;
            }
        }
    }

    /**
     * Client-side tick for smooth rendering. Only handles regen animation.
     */
    public void clientTick() {
        if (effectTimer > 0) effectTimer--;
        if (regenHaltTimer > 0) regenHaltTimer--;

        if (currentValue < maxValue && regenHaltTimer <= 0) {
            if (regenCooldown >= regenDelay) {
                float rate = regenRate;
                if (inOveruse) rate *= OVERUSE_REGEN_MULTIPLIER;
                currentValue += rate;
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
        tag.putFloat("RegenRate", regenRate);
        tag.putInt("RegenDelay", regenDelay);
        tag.putInt("RegenCooldown", regenCooldown);
        tag.putFloat("DrainModifier", drainModifier);
        tag.putBoolean("InOveruse", inOveruse);
        tag.putInt("OveruseStage", overuseStage);
        tag.putInt("RegenHaltTimer", regenHaltTimer);
        tag.putInt("EffectTimer", effectTimer);
        return tag;
    }

    public static StaminaData deserialize(CompoundTag tag) {
        StaminaData data = new StaminaData();
        data.currentValue = tag.getFloat("Value");
        data.maxValue = tag.getFloat("Max");
        data.regenRate = tag.getFloat("RegenRate");
        data.regenDelay = tag.getInt("RegenDelay");
        data.regenCooldown = tag.getInt("RegenCooldown");
        data.drainModifier = tag.contains("DrainModifier")
                ? tag.getFloat("DrainModifier") : 1.0f;
        data.inOveruse = tag.getBoolean("InOveruse");
        data.overuseStage = tag.getInt("OveruseStage");
        data.regenHaltTimer = tag.getInt("RegenHaltTimer");
        data.effectTimer = tag.getInt("EffectTimer");
        return data;
    }

    public void copyFrom(StaminaData source) {
        this.currentValue = source.currentValue;
        this.maxValue = source.maxValue;
        this.regenRate = source.regenRate;
        this.regenDelay = source.regenDelay;
        this.regenCooldown = source.regenCooldown;
        this.drainModifier = source.drainModifier;
        this.inOveruse = source.inOveruse;
        this.overuseStage = source.overuseStage;
        this.regenHaltTimer = source.regenHaltTimer;
        this.effectTimer = source.effectTimer;
    }
}