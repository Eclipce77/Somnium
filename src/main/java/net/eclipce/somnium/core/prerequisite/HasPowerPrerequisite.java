package net.eclipce.somnium.core.prerequisite;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Prerequisite: player must have a specific power granted.
 *
 * <p>Used for cross-power abilities — abilities that only become visible
 * when the player possesses both the power containing the ability AND
 * another specific power.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Ice-Fire Combo only appears if the player also has the Ice power
 * Power.builder()
 *     .ability(() -> ICE_FIRE_COMBO.get(),
 *             new HasPowerPrerequisite(() -> ICE_POWER.get()))
 *     .build();
 * }</pre>
 */
public class HasPowerPrerequisite implements Prerequisite {

    private final Supplier<Power> requiredPower;

    /**
     * @param requiredPower supplier for the power the player must have
     */
    public HasPowerPrerequisite(Supplier<Power> requiredPower) {
        this.requiredPower = requiredPower;
    }

    @Override
    public boolean isMet(SomniumPlayerData data) {
        Power power = requiredPower.get();
        return power != null && data.hasPower(power);
    }

    @Override
    public Component getDescription() {
        Power power = requiredPower.get();
        if (power != null) {
            return Component.literal("Requires power: ")
                    .append(power.getDisplayName());
        }
        return Component.literal("Requires another power");
    }
}