package net.eclipce.somnium.core.ability;

/**
 * Defines how an ability is activated by the player.
 *
 * <p>Each activation type determines which behavior methods on {@link AbilityType}
 * will be called by the ability system. Addon developers set this on their ability
 * via {@link AbilityType.Properties#activationType(ActivationType)}.</p>
 *
 * <p>Note: {@link #PASSIVE} abilities cannot be placed on the ability bar.
 * They appear in the dedicated Passives tab of the ability inventory and
 * function as toggleable on/off switches.</p>
 */
public enum ActivationType {

    /**
     * Press the key once, the effect fires once.
     * <p>Uses: {@link AbilityType#onActivate(AbilityActivationContext)}</p>
     * <p>Example: firing a projectile, dealing AoE damage.</p>
     */
    INSTANT,

    /**
     * Press once to turn on, press again to turn off.
     * <p>Uses: {@link AbilityType#onActivate(AbilityActivationContext)},
     * {@link AbilityType#onDeactivate(AbilityActivationContext)},
     * {@link AbilityType#onActiveTick(AbilityActivationContext)}</p>
     * <p>Example: flight mode, damage boost aura.</p>
     */
    TOGGLE,

    /**
     * Effect is active while the key is held down, stops on release.
     * <p>Uses: {@link AbilityType#onKeyDown(AbilityActivationContext)},
     * {@link AbilityType#onKeyHeld(AbilityActivationContext)},
     * {@link AbilityType#onKeyUp(AbilityActivationContext)}</p>
     * <p>Example: channeled beam, blocking shield.</p>
     */
    HOLD,

    /**
     * Always active when enabled. Not equippable on the ability bar.
     * Lives in the Passives tab and is toggled on/off by clicking.
     * <p>Uses: {@link AbilityType#onEnabled(AbilityActivationContext)},
     * {@link AbilityType#onDisabled(AbilityActivationContext)},
     * {@link AbilityType#onPassiveTick(AbilityActivationContext)}</p>
     * <p>Example: fire resistance, health regeneration.</p>
     */
    PASSIVE,

    /**
     * Hold the key to charge up, release to execute.
     * <p>Uses: {@link AbilityType#onChargeStart(AbilityActivationContext)},
     * {@link AbilityType#onChargeTick(AbilityActivationContext, int)},
     * {@link AbilityType#onChargeRelease(AbilityActivationContext, int)}</p>
     * <p>Example: charged energy blast, ground slam with variable range.</p>
     */
    CHARGED;

    /**
     * @return {@code true} if this activation type can be placed on the ability bar.
     *         Returns {@code false} only for {@link #PASSIVE}.
     */
    public boolean isBarEquippable() {
        return this != PASSIVE;
    }

    /**
     * @return {@code true} if this activation type requires per-tick updates
     *         while active (TOGGLE, HOLD, PASSIVE, CHARGED).
     */
    public boolean requiresTicking() {
        return this == TOGGLE || this == HOLD || this == PASSIVE || this == CHARGED;
    }
}