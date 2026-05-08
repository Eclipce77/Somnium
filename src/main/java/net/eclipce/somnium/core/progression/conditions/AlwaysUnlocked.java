package net.eclipce.somnium.core.progression.conditions;

import net.eclipce.somnium.core.progression.UnlockCondition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * An unlock condition that is always satisfied. Abilities with this
 * condition are available immediately when the power is granted.
 *
 * <p>This is a singleton — use {@link #INSTANCE} rather than creating
 * new instances.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Power.builder()
 *     .ability(() -> ModAbilities.FIRE_PUNCH.get(), AlwaysUnlocked.INSTANCE)
 *     .build();
 * }</pre>
 */
public final class AlwaysUnlocked extends UnlockCondition {

    /** The singleton instance. */
    public static final AlwaysUnlocked INSTANCE = new AlwaysUnlocked();

    private AlwaysUnlocked() {}

    @Override
    public boolean isMet(CompoundTag progress) {
        return true;
    }

    @Override
    public Component getProgressText(CompoundTag progress) {
        return Component.translatable("somnium.unlock.always");
    }

    @Override
    public float getProgressFraction(CompoundTag progress) {
        return 1.0f;
    }

    @Override
    public CompoundTag createInitialProgress() {
        return new CompoundTag();
    }
}
