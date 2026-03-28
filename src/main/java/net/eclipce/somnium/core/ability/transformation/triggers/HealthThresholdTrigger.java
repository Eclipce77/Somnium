package net.eclipce.somnium.core.ability.transformation.triggers;

import net.eclipce.somnium.core.ability.transformation.TransformationTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * A transformation trigger based on the player's health percentage.
 *
 * <p>Activates when the player's current health falls below a specified
 * percentage of their maximum health. Useful for rage-style or last-resort
 * transformations.</p>
 *
 * <h3>Example: Berserker rage at low health</h3>
 * <pre>{@code
 * new HealthThresholdTrigger(0.25f, false, true, 600)
 * // Triggers below 25% health, doesn't auto-revert when healed,
 * // suppressible, 30 second cooldown between forced triggers
 * }</pre>
 */
public class HealthThresholdTrigger extends TransformationTrigger {

    private final float healthPercentage;

    /**
     * Creates a health-based transformation trigger.
     *
     * @param healthPercentage         the health threshold as a fraction (0.0 to 1.0).
     *                                  Trigger activates when player health is at or
     *                                  below this percentage of max health.
     *                                  Example: 0.25f = 25% health.
     * @param autoRevertOnConditionEnd if true, the transformation ends when health
     *                                  rises back above the threshold
     * @param suppressible             if true, the player can resist this trigger
     * @param triggerCooldownTicks     minimum ticks between forced activations
     */
    public HealthThresholdTrigger(float healthPercentage,
                                  boolean autoRevertOnConditionEnd,
                                  boolean suppressible,
                                  int triggerCooldownTicks) {
        super(autoRevertOnConditionEnd, suppressible, triggerCooldownTicks);
        this.healthPercentage = Math.max(0f, Math.min(1f, healthPercentage));
    }

    /**
     * Convenience constructor with common defaults: no auto-revert,
     * not suppressible, 30 second cooldown.
     *
     * @param healthPercentage the health threshold as a fraction (0.0 to 1.0)
     */
    public HealthThresholdTrigger(float healthPercentage) {
        this(healthPercentage, false, false, 600);
    }

    @Override
    public boolean test(ServerPlayer player) {
        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0) return false;
        float currentRatio = player.getHealth() / maxHealth;
        return currentRatio <= healthPercentage;
    }

    /** @return the health threshold as a fraction (0.0 to 1.0) */
    public float getHealthPercentage() {
        return healthPercentage;
    }
}