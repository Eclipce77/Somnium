package net.eclipce.somnium.core.progression.conditions;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.progression.UnlockCondition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Unlocks an ability after the player kills a required number of entities.
 *
 * <p>Optionally requires a specific ability to be active (toggled on or
 * held) during the kill. This supports scenarios like "kill 20 enemies
 * while using Fire Aura."</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Kill 20 entities (no ability requirement)
 * new KillCondition(20)
 *
 * // Kill 15 entities while Fire Aura is active
 * new KillCondition(15, () -> ModAbilities.FIRE_AURA.get())
 * }</pre>
 *
 * @see net.eclipce.somnium.core.progression.ProgressionHandler
 */
public class KillCondition extends UnlockCondition {

    private static final String TAG_KILLS = "Kills";

    private final int requiredKills;
    @Nullable
    private final Supplier<AbilityType> whileAbilityActive;

    /**
     * Creates a kill condition with no active-ability requirement.
     *
     * @param requiredKills number of kills needed
     */
    public KillCondition(int requiredKills) {
        this(requiredKills, null);
    }

    /**
     * Creates a kill condition that only counts kills while a specific
     * ability is active (toggled on or held).
     *
     * @param requiredKills      number of kills needed
     * @param whileAbilityActive supplier for the ability that must be active
     *                           during the kill, or null for any kill
     */
    public KillCondition(int requiredKills, @Nullable Supplier<AbilityType> whileAbilityActive) {
        this.requiredKills = Math.max(1, requiredKills);
        this.whileAbilityActive = whileAbilityActive;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UnlockCondition implementation
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean isMet(CompoundTag progress) {
        return progress.getInt(TAG_KILLS) >= requiredKills;
    }

    @Override
    public Component getProgressText(CompoundTag progress) {
        int current = Math.min(progress.getInt(TAG_KILLS), requiredKills);
        String text = "Kills: " + current + "/" + requiredKills;
        if (whileAbilityActive != null) {
            AbilityType required = whileAbilityActive.get();
            String name = required != null
                    ? required.getDisplayName().getString()
                    : "???";
            text += " (while " + name + " active)";
        }
        return Component.literal(text);
    }

    @Override
    public float getProgressFraction(CompoundTag progress) {
        int current = progress.getInt(TAG_KILLS);
        return Math.min(1.0f, (float) current / requiredKills);
    }

    @Override
    public CompoundTag createInitialProgress() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_KILLS, 0);
        return tag;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Progress update — called by ProgressionHandler
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Increments the kill counter by one. Called by the progression
     * handler when a valid kill is detected.
     *
     * @param progress the progress tag to update
     */
    public void incrementKills(CompoundTag progress) {
        int current = progress.getInt(TAG_KILLS);
        progress.putInt(TAG_KILLS, current + 1);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /** @return the number of kills required to unlock */
    public int getRequiredKills() {
        return requiredKills;
    }

    /**
     * @return the ability that must be active during kills, or null if
     *         any kill counts
     */
    @Nullable
    public AbilityType getRequiredActiveAbility() {
        return whileAbilityActive != null ? whileAbilityActive.get() : null;
    }

    /**
     * @return the registry key of the required active ability, or null
     */
    @Nullable
    public ResourceLocation getRequiredActiveAbilityKey() {
        AbilityType type = getRequiredActiveAbility();
        return type != null ? SomniumRegistries.getAbilityKey(type) : null;
    }

    /** @return true if kills must occur while a specific ability is active */
    public boolean hasAbilityRequirement() {
        return whileAbilityActive != null;
    }
}
