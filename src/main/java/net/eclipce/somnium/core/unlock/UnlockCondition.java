package net.eclipce.somnium.core.unlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Abstract base for condition-based ability unlocking within a {@link
 * net.eclipce.somnium.core.power.Power}.
 *
 * <p>Each ability in a power can have an {@code UnlockCondition} that determines
 * when it becomes available to the player. The condition evaluates against
 * per-player progress data (stored as a {@link CompoundTag}) and reports
 * whether it has been met, the current progress fraction, and a human-readable
 * progress description for the UI.</p>
 *
 * <p>Progress data is stored per-player per-power-per-ability in
 * {@link net.eclipce.somnium.core.data.SomniumPlayerData}. Each condition type
 * defines what data it stores (counters, flags, etc.) via
 * {@link #createInitialProgress()}.</p>
 *
 * <h3>Built-in conditions</h3>
 * <ul>
 *     <li>{@link net.eclipce.somnium.core.unlock.conditions.AlwaysUnlocked} —
 *         immediately met (no progress tracking)</li>
 *     <li>{@link net.eclipce.somnium.core.unlock.conditions.AbilityUsageCondition} —
 *         "use ability X, N times"</li>
 *     <li>{@link net.eclipce.somnium.core.unlock.conditions.KillCondition} —
 *         "kill N entities"</li>
 *     <li>{@link net.eclipce.somnium.core.unlock.conditions.CompositeCondition} —
 *         AND/OR combinations of other conditions</li>
 * </ul>
 *
 * <h3>Custom conditions (addon developers)</h3>
 * <p>Subclass {@code UnlockCondition} and override all abstract methods.
 * Register a {@link ProgressionHandler} event listener to update your
 * condition's progress when relevant game events occur.</p>
 *
 * @see net.eclipce.somnium.core.power.Power.PowerAbilityEntry
 * @see ProgressionHandler
 */
public abstract class UnlockCondition {

    /**
     * Checks whether this condition is currently met, based on the
     * player's progress data.
     *
     * @param progress the per-player progress tag for this condition.
     *                 Never null — initialized by {@link #createInitialProgress()}.
     * @return {@code true} if the condition is satisfied and the ability
     *         should be unlocked
     */
    public abstract boolean isMet(CompoundTag progress);

    /**
     * Returns a human-readable description of the current progress,
     * for display in the ability inventory tooltip.
     *
     * <p>Examples: "Use Fire Punch: 37/50", "Kills: 12/20",
     * "Complete all requirements"</p>
     *
     * @param progress the per-player progress tag
     * @return a Component describing the current state
     */
    public abstract Component getProgressText(CompoundTag progress);

    /**
     * Returns a progress fraction from 0.0 (no progress) to 1.0 (complete).
     * Used by the inventory UI to render a progress bar or fill indicator
     * on locked ability icons.
     *
     * @param progress the per-player progress tag
     * @return progress as a float between 0.0 and 1.0
     */
    public abstract float getProgressFraction(CompoundTag progress);

    /**
     * Creates the initial progress data for a newly granted power's ability.
     * Called when a power is first granted to a player and the progression
     * system initializes tracking for each ability with a condition.
     *
     * <p>The returned tag should contain all the fields the condition needs,
     * initialized to their starting values (e.g., counter at 0).</p>
     *
     * @return a new CompoundTag with default progress values
     */
    public abstract CompoundTag createInitialProgress();
}
