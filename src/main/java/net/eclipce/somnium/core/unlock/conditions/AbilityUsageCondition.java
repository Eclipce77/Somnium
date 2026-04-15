package net.eclipce.somnium.core.unlock.conditions;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.unlock.UnlockCondition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Unlocks an ability after a specific target ability has been activated
 * a required number of times.
 *
 * <p>This is the most common progression condition — it rewards players
 * for using their existing abilities. The counter tracks all successful
 * activations of the target ability (INSTANT fire, TOGGLE on, HOLD start,
 * CHARGED start).</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Power.builder()
 *     // Fire Blast unlocks after using Fire Punch 50 times
 *     .ability(() -> ModAbilities.FIRE_BLAST.get(),
 *              new AbilityUsageCondition(() -> ModAbilities.FIRE_PUNCH.get(), 50))
 *     .build();
 * }</pre>
 *
 * @see net.eclipce.somnium.core.unlock.ProgressionHandler
 */
public class AbilityUsageCondition extends UnlockCondition {

    private static final String TAG_COUNT = "Count";

    private final Supplier<AbilityType> targetAbility;
    private final int requiredCount;

    /**
     * Creates a usage-based unlock condition.
     *
     * @param targetAbility supplier for the ability that must be used
     *                      (supplier to avoid registry ordering issues)
     * @param requiredCount number of times the target must be activated
     */
    public AbilityUsageCondition(Supplier<AbilityType> targetAbility, int requiredCount) {
        this.targetAbility = targetAbility;
        this.requiredCount = Math.max(1, requiredCount);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UnlockCondition implementation
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean isMet(CompoundTag progress) {
        return progress.getInt(TAG_COUNT) >= requiredCount;
    }

    @Override
    public Component getProgressText(CompoundTag progress) {
        int current = Math.min(progress.getInt(TAG_COUNT), requiredCount);
        AbilityType target = getTargetAbilityType();
        String targetName = target != null
                ? target.getDisplayName().getString()
                : "???";
        return Component.literal("Use " + targetName + ": " + current + "/" + requiredCount);
    }

    @Override
    public float getProgressFraction(CompoundTag progress) {
        int current = progress.getInt(TAG_COUNT);
        return Math.min(1.0f, (float) current / requiredCount);
    }

    @Override
    public CompoundTag createInitialProgress() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_COUNT, 0);
        return tag;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Progress update — called by ProgressionHandler
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Increments the usage counter by one. Called by the progression
     * handler when the target ability is successfully activated.
     *
     * @param progress the progress tag to update
     */
    public void incrementCount(CompoundTag progress) {
        int current = progress.getInt(TAG_COUNT);
        progress.putInt(TAG_COUNT, current + 1);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return the resolved target ability type, or null if the supplier
     *         returns null (shouldn't happen after registry loading)
     */
    @Nullable
    public AbilityType getTargetAbilityType() {
        return targetAbility.get();
    }

    /**
     * @return the registry key of the target ability, or null
     */
    @Nullable
    public ResourceLocation getTargetAbilityKey() {
        AbilityType type = getTargetAbilityType();
        return type != null ? SomniumRegistries.getAbilityKey(type) : null;
    }

    /** @return the number of activations required to unlock */
    public int getRequiredCount() {
        return requiredCount;
    }
}
