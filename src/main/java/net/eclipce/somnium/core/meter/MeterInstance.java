package net.eclipce.somnium.core.meter;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-player runtime data for a single meter instance. Stores the current
 * value, max value, regeneration state, and regen cooldown timer.
 *
 * <p>Each player has one MeterInstance per registered MeterDefinition
 * (created on demand) plus a special stamina instance.</p>
 */
public class MeterInstance {

    private float currentValue;
    private float maxValue;
    private float regenRate;
    private int regenDelay;
    private boolean regenEnabled;
    private int regenCooldown; // ticks since last drain, counts up to regenDelay
    private boolean depleted; // true when value hit 0 (fires callback once)

    /**
     * Creates a new meter instance from a definition's defaults.
     */
    public MeterInstance(MeterDefinition definition) {
        this.maxValue = definition.getDefaultMaxValue();
        this.currentValue = this.maxValue;
        this.regenRate = definition.getRegenRate();
        this.regenDelay = definition.getRegenDelay();
        this.regenEnabled = definition.isRegenEnabled();
        this.regenCooldown = this.regenDelay;
        this.depleted = false;
    }

    /**
     * Creates a meter instance with explicit values (for stamina or deserialization).
     */
    public MeterInstance(float maxValue, float currentValue, float regenRate,
                         int regenDelay, boolean regenEnabled) {
        this.maxValue = maxValue;
        this.currentValue = currentValue;
        this.regenRate = regenRate;
        this.regenDelay = regenDelay;
        this.regenEnabled = regenEnabled;
        this.regenCooldown = this.regenDelay;
        this.depleted = currentValue <= 0f;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Value manipulation
    // ═══════════════════════════════════════════════════════════════════

    /** @return the current meter value */
    public float getValue() { return currentValue; }

    /** @return the maximum meter value */
    public float getMaxValue() { return maxValue; }

    /** @return the current value as a fraction 0.0–1.0 */
    public float getFraction() { return maxValue > 0 ? currentValue / maxValue : 0f; }

    /** @return true if the meter is at or below zero */
    public boolean isEmpty() { return currentValue <= 0f; }

    /** @return true if the meter is at max capacity */
    public boolean isFull() { return currentValue >= maxValue; }

    /**
     * Drains (subtracts) from the meter. Clamps at zero.
     * Resets the regen cooldown timer.
     *
     * @param amount the amount to drain (positive)
     * @return the actual amount drained
     */
    public float drain(float amount) {
        if (amount <= 0) return 0;
        float drained = Math.min(currentValue, amount);
        currentValue -= drained;
        if (currentValue < 0) currentValue = 0;
        regenCooldown = 0; // reset regen timer
        if (currentValue <= 0 && !depleted) {
            depleted = true; // will trigger callback on next tick
        }
        return drained;
    }

    /**
     * Adds to the meter. Clamps at max.
     *
     * @param amount the amount to add (positive)
     * @return the actual amount added
     */
    public float add(float amount) {
        if (amount <= 0) return 0;
        float added = Math.min(maxValue - currentValue, amount);
        currentValue += added;
        if (currentValue > 0) depleted = false;
        return added;
    }

    /**
     * Multiplies the current value.
     *
     * @param factor the multiplier
     */
    public void multiply(float factor) {
        currentValue = Math.max(0, Math.min(maxValue, currentValue * factor));
        if (factor < 1.0f) regenCooldown = 0;
        if (currentValue <= 0 && !depleted) depleted = true;
        if (currentValue > 0) depleted = false;
    }

    /**
     * Sets the current value directly. Clamps between 0 and max.
     */
    public void setValue(float value) {
        float old = currentValue;
        currentValue = Math.max(0, Math.min(maxValue, value));
        if (currentValue < old) regenCooldown = 0;
        if (currentValue <= 0 && !depleted) depleted = true;
        if (currentValue > 0) depleted = false;
    }

    /**
     * Sets a new maximum value. Current value is clamped if needed.
     * Does NOT change the current value otherwise (useful for dynamic scaling).
     */
    public void setMaxValue(float max) {
        this.maxValue = Math.max(1, max);
        if (currentValue > maxValue) currentValue = maxValue;
    }

    public void setRegenRate(float rate) { this.regenRate = rate; }
    public void setRegenDelay(int ticks) { this.regenDelay = ticks; }
    public void setRegenEnabled(boolean enabled) { this.regenEnabled = enabled; }
    public float getRegenRate() { return regenRate; }
    public int getRegenDelay() { return regenDelay; }
    public boolean isRegenEnabled() { return regenEnabled; }

    // ═══════════════════════════════════════════════════════════════════
    //  Tick
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ticks regeneration. Call once per server tick.
     *
     * @return true if the meter was depleted this tick (for callback firing)
     */
    public boolean tick() {
        boolean justDepleted = false;

        if (depleted && currentValue <= 0) {
            justDepleted = true;
        }

        if (regenEnabled && currentValue < maxValue) {
            if (regenCooldown >= regenDelay) {
                currentValue = Math.min(maxValue, currentValue + regenRate);
                if (currentValue > 0) depleted = false;
            } else {
                regenCooldown++;
            }
        }

        return justDepleted;
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
        tag.putBoolean("RegenEnabled", regenEnabled);
        tag.putInt("RegenCooldown", regenCooldown);
        return tag;
    }

    public static MeterInstance deserialize(CompoundTag tag) {
        float max = tag.getFloat("Max");
        float value = tag.getFloat("Value");
        float rate = tag.getFloat("RegenRate");
        int delay = tag.getInt("RegenDelay");
        boolean enabled = tag.getBoolean("RegenEnabled");
        MeterInstance instance = new MeterInstance(max, value, rate, delay, enabled);
        instance.regenCooldown = tag.getInt("RegenCooldown");
        return instance;
    }
}