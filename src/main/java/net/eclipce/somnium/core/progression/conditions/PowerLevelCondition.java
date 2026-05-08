package net.eclipce.somnium.core.progression.conditions;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.prerequisite.Prerequisite;
import net.eclipce.somnium.core.progression.PowerProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Prerequisite that requires the parent power to be at a minimum level.
 * The ability is hidden until the player's power level reaches the threshold.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Power.builder()
 *     .ability(() -> BASIC_FIRE.get())                              // always visible
 *     .ability(() -> METEOR.get(), new PowerLevelCondition(10))     // requires power level 10
 *     .build();
 * }</pre>
 */
public class PowerLevelCondition implements Prerequisite {

    private final int requiredLevel;
    private final ResourceLocation powerKey;

    /**
     * @param requiredLevel the minimum power level needed
     * @param powerKey      the power's registry key to check level for
     */
    public PowerLevelCondition(int requiredLevel, ResourceLocation powerKey) {
        this.requiredLevel = requiredLevel;
        this.powerKey = powerKey;
    }

    /**
     * Simplified constructor — power key resolved at check time from context.
     * Use when the prerequisite is attached to an ability within the same power.
     */
    public PowerLevelCondition(int requiredLevel) {
        this(requiredLevel, null);
    }

    @Override
    public boolean isMet(SomniumPlayerData data) {
        if (powerKey != null) {
            return data.getPowerProgress().getLevel(powerKey) >= requiredLevel;
        }
        // If no power key specified, check all powers the player has
        // This is a fallback — prefer specifying the power key
        for (ResourceLocation key : data.getGrantedPowerKeys()) {
            if (data.getPowerProgress().getLevel(key) >= requiredLevel) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getDescription() {
        return Component.literal("Requires Power Level " + requiredLevel);
    }
}