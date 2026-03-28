package net.eclipce.somnium.core.ability.transformation;

import net.minecraft.server.level.ServerPlayer;

/**
 * Defines a condition that can involuntarily trigger a transformation.
 *
 * <p>When a {@link TransformationAbilityType} has a trigger, the ability system
 * evaluates it each tick for players who have the transformation but are not
 * currently transformed. If {@link #test(ServerPlayer)} returns {@code true}
 * and no suppression or cooldown prevents it, the transformation activates
 * automatically.</p>
 *
 * <h3>Trigger behavior configuration</h3>
 * <p>Each trigger has three configurable behaviors:</p>
 * <ul>
 *     <li><strong>Auto-revert:</strong> When the condition stops being true,
 *         should the transformation automatically end? A werewolf reverts at
 *         dawn; a rage form stays until manually dismissed.</li>
 *     <li><strong>Suppressibility:</strong> Can the player resist the forced
 *         trigger? If suppressible, {@link #canSuppress(ServerPlayer)} determines
 *         whether suppression is currently working (e.g., holding a special item,
 *         having a specific effect active).</li>
 *     <li><strong>Cooldown:</strong> After a forced de-transform, how many ticks
 *         before the trigger can fire again? Prevents rapid re-triggering when
 *         conditions fluctuate.</li>
 * </ul>
 *
 * <h3>Built-in implementations</h3>
 * <ul>
 *     <li>{@code TimeTrigger} — triggers during specific times of day</li>
 *     <li>{@code HealthThresholdTrigger} — triggers at low health</li>
 *     <li>{@code CompositeTrigger} — AND/OR combinations of other triggers</li>
 * </ul>
 *
 * <h3>Custom triggers</h3>
 * <p>Addon developers can create custom triggers by extending this class:</p>
 * <pre>{@code
 * public class OnFireTrigger extends TransformationTrigger {
 *     public OnFireTrigger() {
 *         super(true, false, 100); // auto-revert, not suppressible, 5s cooldown
 *     }
 *
 *     @Override
 *     public boolean test(ServerPlayer player) {
 *         return player.isOnFire();
 *     }
 * }
 * }</pre>
 *
 * @see TransformationAbilityType
 */
public abstract class TransformationTrigger {

    private final boolean autoRevertOnConditionEnd;
    private final boolean suppressible;
    private final int triggerCooldownTicks;

    /**
     * Creates a new transformation trigger.
     *
     * @param autoRevertOnConditionEnd if {@code true}, the transformation ends
     *                                  automatically when {@link #test} returns false
     * @param suppressible             if {@code true}, the player can resist this
     *                                  trigger via {@link #canSuppress}
     * @param triggerCooldownTicks     minimum ticks between forced activations
     *                                  (prevents rapid re-triggering)
     */
    protected TransformationTrigger(boolean autoRevertOnConditionEnd,
                                    boolean suppressible,
                                    int triggerCooldownTicks) {
        this.autoRevertOnConditionEnd = autoRevertOnConditionEnd;
        this.suppressible = suppressible;
        this.triggerCooldownTicks = Math.max(0, triggerCooldownTicks);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Core condition — subclasses implement this
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Evaluates whether the trigger condition is currently met.
     * Called each tick for players who have this transformation but
     * are not currently transformed.
     *
     * <p>Implementations should be lightweight — this runs every tick.
     * Avoid heavy computation, world queries, or network calls.</p>
     *
     * @param player the player to check
     * @return {@code true} if the condition is met and the transformation
     *         should be forced (subject to suppression and cooldown checks)
     */
    public abstract boolean test(ServerPlayer player);

    // ═══════════════════════════════════════════════════════════════════
    //  Suppression
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks whether the player is currently suppressing/resisting
     * this trigger. Only called if {@link #isSuppressible()} returns
     * {@code true} and {@link #test(ServerPlayer)} returned {@code true}.
     *
     * <p>Override this to define suppression mechanics. The default
     * implementation always returns {@code false} (no suppression).</p>
     *
     * <p>Example suppression conditions: holding a specific item,
     * being in a certain dimension, having a potion effect active,
     * or having a specific ability enabled.</p>
     *
     * @param player the player to check
     * @return {@code true} if the player is successfully suppressing
     *         the trigger (transformation will NOT activate)
     */
    public boolean canSuppress(ServerPlayer player) {
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return {@code true} if the transformation should automatically end
     *         when the trigger condition is no longer met. When {@code false},
     *         the transformation persists until manually ended or forced by
     *         another mechanism (duration, death, etc.).
     */
    public boolean isAutoRevertOnConditionEnd() {
        return autoRevertOnConditionEnd;
    }

    /**
     * @return {@code true} if this trigger can be resisted by the player.
     *         When true, {@link #canSuppress(ServerPlayer)} is called to
     *         determine if suppression is currently active.
     */
    public boolean isSuppressible() {
        return suppressible;
    }

    /**
     * @return the minimum number of ticks between forced activations.
     *         After a triggered transformation ends, the trigger won't
     *         fire again until this many ticks have passed. The cooldown
     *         is tracked on the {@link TransformationInstance}.
     */
    public int getTriggerCooldownTicks() {
        return triggerCooldownTicks;
    }
}