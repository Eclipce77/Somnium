package net.eclipce.somnium.core.prerequisite;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Prerequisite: player must have a minimum number of abilities unlocked
 * in a specific power.
 *
 * <p>Used for cross-power abilities that require a certain proficiency
 * level before they become visible.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Requires 3 abilities unlocked in the Ice power before this ability appears
 * Power.builder()
 *     .ability(() -> ADVANCED_COMBO.get(),
 *             new PowerProficiencyPrerequisite(() -> ICE_POWER.get(), 3))
 *     .build();
 * }</pre>
 */
public class PowerProficiencyPrerequisite implements Prerequisite {

    private final Supplier<Power> targetPower;
    private final int requiredCount;

    /**
     * @param targetPower   supplier for the power to check proficiency in
     * @param requiredCount minimum number of abilities the player must have
     *                      unlocked in that power
     */
    public PowerProficiencyPrerequisite(Supplier<Power> targetPower, int requiredCount) {
        this.targetPower = targetPower;
        this.requiredCount = requiredCount;
    }

    @Override
    public boolean isMet(SomniumPlayerData data) {
        Power power = targetPower.get();
        if (power == null || !data.hasPower(power)) return false;

        int unlocked = 0;
        for (AbilityType type : power.getAbilityTypes()) {
            if (data.isAbilityUnlocked(type)) {
                unlocked++;
            }
        }
        return unlocked >= requiredCount;
    }

    @Override
    public Component getDescription() {
        Power power = targetPower.get();
        String powerName = power != null ? power.getDisplayName().getString() : "Unknown";
        return Component.literal("Unlock " + requiredCount + " abilities in " + powerName);
    }
}