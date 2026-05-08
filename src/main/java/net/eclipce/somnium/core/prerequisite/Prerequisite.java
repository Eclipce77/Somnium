package net.eclipce.somnium.core.prerequisite;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.network.chat.Component;

/**
 * A prerequisite is a visibility gate on an ability within a power.
 * When the prerequisite is not met, the ability is completely hidden
 * from the ability inventory — not grayed out, not locked, just invisible.
 *
 * <p>Unlike {@link net.eclipce.somnium.core.progression.UnlockCondition} which
 * tracks progress toward unlocking, prerequisites are binary: either the
 * player meets the requirement or they don't. There is no progress bar.</p>
 *
 * <p>Prerequisites evaluate against the full player data (not just a progress
 * tag), so they can check cross-power state, tags, or any other data.</p>
 *
 * <h3>Built-in prerequisites</h3>
 * <ul>
 *     <li>{@link HasPowerPrerequisite} — player must have a specific power</li>
 *     <li>{@link PowerProficiencyPrerequisite} — player must have N abilities
 *         unlocked in a specific power</li>
 *     <li>{@link HasTagPrerequisite} — player must have a specific tag</li>
 * </ul>
 *
 * <h3>Usage in power registration</h3>
 * <pre>{@code
 * Power.builder()
 *     .ability(() -> FIRE_ICE_COMBO.get(),
 *             new HasPowerPrerequisite(() -> ICE_POWER.get()),  // prerequisite
 *             new AbilityUsageCondition(() -> FIRE_BLAST.get(), 25))  // unlock condition
 *     .build();
 * }</pre>
 *
 * @see net.eclipce.somnium.core.power.Power.PowerAbilityEntry
 */
public interface Prerequisite {

    /**
     * Checks if this prerequisite is currently met for the player.
     *
     * @param data the player's Somnium data
     * @return {@code true} if the prerequisite is satisfied and the ability
     *         should be visible (may still be locked by an UnlockCondition)
     */
    boolean isMet(SomniumPlayerData data);

    /**
     * Returns a human-readable description of the prerequisite.
     * Shown in tooltips when the ability first becomes visible but is
     * still locked. Not shown when the ability is hidden.
     *
     * @return a Component describing the requirement
     */
    Component getDescription();
}