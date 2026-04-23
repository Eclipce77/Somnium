package net.eclipce.somnium.core.power;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.prerequisite.Prerequisite;
import net.eclipce.somnium.core.unlock.UnlockCondition;
import net.eclipce.somnium.core.unlock.conditions.AlwaysUnlocked;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A registered grouping of abilities. Analogous to {@link net.minecraft.world.item.CreativeModeTab}.
 *
 * <p>A {@code Power} defines a themed collection of abilities that a player can be
 * granted. It appears as a tab in the ability inventory, and players can have
 * multiple powers simultaneously. An {@link AbilityType} can belong to multiple
 * powers without conflict.</p>
 *
 * <h3>Progression</h3>
 * <p>Each ability within a power can have an {@link UnlockCondition} that defines
 * when it becomes available. Abilities without a condition (or with
 * {@link AlwaysUnlocked}) are available immediately when the power is granted.
 * Progression can be disabled per-power via {@link Builder#progressionEnabled(boolean)},
 * in which case all abilities unlock immediately regardless of conditions.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public static final RegistryObject<Power> PYROMANCY = POWERS.register("pyromancy",
 *     () -> Power.builder()
 *         .displayName(Component.translatable("power.mymod.pyromancy"))
 *         .icon(new ResourceLocation("mymod", "textures/power/pyromancy.png"))
 *         .ability(() -> ModAbilities.FIRE_PUNCH.get())  // AlwaysUnlocked
 *         .ability(() -> ModAbilities.FIRE_BLAST.get(),
 *                  new AbilityUsageCondition(() -> ModAbilities.FIRE_PUNCH.get(), 50))
 *         .ability(() -> ModAbilities.FIRE_RESISTANCE.get(), true)  // passive, starts ON
 *         .build()
 * );
 * }</pre>
 *
 * @see AbilityType
 * @see PowerAbilityEntry
 * @see UnlockCondition
 */
public class Power {

    // ═══════════════════════════════════════════════════════════════════
    //  Fields
    // ═══════════════════════════════════════════════════════════════════

    private final Component displayName;
    private final ResourceLocation icon;
    private final boolean persistOnDeath;
    private final boolean progressionEnabled;
    private final List<PowerAbilityEntry> entries;
    @Nullable
    private final ResourceLocation autoGrantTag;
    @Nullable
    private final ResourceLocation requiredTag;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction (use Builder)
    // ═══════════════════════════════════════════════════════════════════

    private Power(Builder builder) {
        this.displayName = builder.displayName;
        this.icon = builder.icon;
        this.persistOnDeath = builder.persistOnDeath;
        this.progressionEnabled = builder.progressionEnabled;
        this.entries = Collections.unmodifiableList(new ArrayList<>(builder.entries));
        this.autoGrantTag = builder.autoGrantTag;
        this.requiredTag = builder.requiredTag;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return the display name shown on the power's tab in the ability inventory
     */
    public Component getDisplayName() {
        return displayName;
    }

    /**
     * @return the icon texture for the power's tab, or {@code null} for a default icon
     */
    @Nullable
    public ResourceLocation getIcon() {
        return icon;
    }

    /**
     * @return {@code true} if this power (and all its abilities) persists through death.
     *         When {@code false}, the entire power is revoked on death, including all
     *         its abilities and their progress.
     */
    public boolean isPersistOnDeath() {
        return persistOnDeath;
    }

    /**
     * @return {@code true} if abilities in this power use the progression/unlock system.
     *         When {@code false}, all abilities are immediately available when the power
     *         is granted, regardless of any unlock conditions defined on them.
     */
    public boolean isProgressionEnabled() {
        return progressionEnabled;
    }

    /**
     * @return the tag that auto-grants this power when added to a player,
     *         or {@code null} if no auto-grant tag is set
     */
    @Nullable
    public ResourceLocation getAutoGrantTag() {
        return autoGrantTag;
    }

    /**
     * @return the tag that must be present before this power can be granted,
     *         or {@code null} if no requirement
     */
    @Nullable
    public ResourceLocation getRequiredTag() {
        return requiredTag;
    }

    /**
     * @return an unmodifiable list of all ability entries in this power
     */
    public List<PowerAbilityEntry> getEntries() {
        return entries;
    }

    /**
     * Returns all ability types in this power, resolving the suppliers.
     * Convenience method for getting just the types without entry metadata.
     *
     * @return a list of resolved AbilityTypes
     */
    public List<AbilityType> getAbilityTypes() {
        List<AbilityType> types = new ArrayList<>();
        for (PowerAbilityEntry entry : entries) {
            AbilityType type = entry.getAbilityType();
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    /**
     * Checks whether this power contains the given ability type.
     *
     * @param abilityType the ability type to check for
     * @return {@code true} if this power includes the given ability
     */
    public boolean containsAbility(AbilityType abilityType) {
        for (PowerAbilityEntry entry : entries) {
            if (entry.getAbilityType() == abilityType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the entry for a specific ability type, if it exists in this power.
     *
     * @param abilityType the ability type to look up
     * @return the entry, or {@code null} if the ability isn't in this power
     */
    @Nullable
    public PowerAbilityEntry getEntry(AbilityType abilityType) {
        for (PowerAbilityEntry entry : entries) {
            if (entry.getAbilityType() == abilityType) {
                return entry;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new builder for constructing a Power.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link Power} instances. Provides a fluent API
     * for adding abilities and configuring power properties.
     */
    public static class Builder {

        private Component displayName = Component.literal("Unnamed Power");
        private ResourceLocation icon = null;
        private boolean persistOnDeath = true;
        private boolean progressionEnabled = true;
        private final List<PowerAbilityEntry> entries = new ArrayList<>();
        private ResourceLocation autoGrantTag = null;
        private ResourceLocation requiredTag = null;

        private Builder() {}

        public Builder displayName(Component name) {
            this.displayName = name;
            return this;
        }

        public Builder icon(ResourceLocation icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Sets whether this power persists through player death. Default is {@code true}.
         * When {@code false}, the power and ALL its abilities are revoked on death.
         */
        public Builder persistOnDeath(boolean persist) {
            this.persistOnDeath = persist;
            return this;
        }

        /**
         * Sets whether the progression/unlock system is active for this power.
         * Default is {@code true}. When {@code false}, all abilities are immediately
         * unlocked when the power is granted, ignoring any unlock conditions.
         */
        public Builder progressionEnabled(boolean enabled) {
            this.progressionEnabled = enabled;
            return this;
        }

        /**
         * Sets a tag that automatically grants this power when added to a player.
         * When the tag is removed, the power is automatically revoked.
         *
         * @param tag the auto-grant tag
         */
        public Builder autoGrantTag(ResourceLocation tag) {
            this.autoGrantTag = tag;
            return this;
        }

        /**
         * Sets a tag that must be present before this power can be granted.
         * If the player doesn't have this tag, grant attempts are rejected.
         *
         * @param tag the required tag
         */
        public Builder requiredTag(ResourceLocation tag) {
            this.requiredTag = tag;
            return this;
        }

        /** Adds an ability with no conditions (immediate unlock). */
        public Builder ability(Supplier<AbilityType> abilitySupplier) {
            entries.add(new PowerAbilityEntry(abilitySupplier, null, null, null));
            return this;
        }

        /** Adds an ability with a passive default override. */
        public Builder ability(Supplier<AbilityType> abilitySupplier,
                               @Nullable Boolean defaultEnabledOverride) {
            entries.add(new PowerAbilityEntry(abilitySupplier, defaultEnabledOverride, null, null));
            return this;
        }

        /** Adds an ability with an unlock condition. */
        public Builder ability(Supplier<AbilityType> abilitySupplier,
                               UnlockCondition condition) {
            entries.add(new PowerAbilityEntry(abilitySupplier, null, condition, null));
            return this;
        }

        /** Adds an ability with an unlock condition and passive default override. */
        public Builder ability(Supplier<AbilityType> abilitySupplier,
                               UnlockCondition condition,
                               @Nullable Boolean defaultEnabledOverride) {
            entries.add(new PowerAbilityEntry(abilitySupplier, defaultEnabledOverride, condition, null));
            return this;
        }

        /**
         * Adds an ability with a prerequisite (visibility gate).
         * The ability is completely hidden until the prerequisite is met.
         *
         * @param abilitySupplier a supplier for the ability type
         * @param prerequisite    the visibility gate
         */
        public Builder ability(Supplier<AbilityType> abilitySupplier,
                               Prerequisite prerequisite) {
            entries.add(new PowerAbilityEntry(abilitySupplier, null, null, prerequisite));
            return this;
        }

        /**
         * Adds an ability with both a prerequisite and an unlock condition.
         * The ability is hidden until the prerequisite is met, then locked
         * until the unlock condition is met.
         *
         * @param abilitySupplier a supplier for the ability type
         * @param prerequisite    the visibility gate
         * @param condition       the unlock condition (applied after prerequisite is met)
         */
        public Builder ability(Supplier<AbilityType> abilitySupplier,
                               Prerequisite prerequisite,
                               UnlockCondition condition) {
            entries.add(new PowerAbilityEntry(abilitySupplier, null, condition, prerequisite));
            return this;
        }

        /**
         * Builds the Power instance.
         *
         * @return the constructed Power
         * @throws IllegalStateException if no abilities have been added
         */
        public Power build() {
            if (entries.isEmpty()) {
                throw new IllegalStateException("A Power must contain at least one ability.");
            }
            return new Power(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PowerAbilityEntry — per-ability metadata within a power
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents a single ability's entry within a {@link Power}, holding
     * per-power metadata for that ability: unlock condition and passive
     * default override.
     *
     * <p>The ability is stored as a {@link Supplier} to avoid registry
     * ordering issues during mod loading — the supplier is resolved lazily
     * when the ability is actually needed.</p>
     */
    public static class PowerAbilityEntry {

        private final Supplier<AbilityType> abilitySupplier;
        private final Boolean defaultEnabledOverride;
        @Nullable
        private final UnlockCondition unlockCondition;
        @Nullable
        private final Prerequisite prerequisite;

        /**
         * Creates a new entry.
         *
         * @param abilitySupplier       supplier for the ability type
         * @param defaultEnabledOverride passive default override, or {@code null}
         * @param unlockCondition        the condition to unlock, or {@code null} for immediate
         * @param prerequisite           visibility gate, or {@code null} for always visible
         */
        public PowerAbilityEntry(Supplier<AbilityType> abilitySupplier,
                                 @Nullable Boolean defaultEnabledOverride,
                                 @Nullable UnlockCondition unlockCondition,
                                 @Nullable Prerequisite prerequisite) {
            this.abilitySupplier = abilitySupplier;
            this.defaultEnabledOverride = defaultEnabledOverride;
            this.unlockCondition = unlockCondition;
            this.prerequisite = prerequisite;
        }

        @Nullable
        public AbilityType getAbilityType() {
            return abilitySupplier.get();
        }

        public boolean getEffectiveDefaultEnabled() {
            AbilityType type = getAbilityType();
            if (defaultEnabledOverride != null) {
                return defaultEnabledOverride;
            }
            return type != null ? type.isDefaultEnabled() : false;
        }

        @Nullable
        public Boolean getDefaultEnabledOverride() {
            return defaultEnabledOverride;
        }

        @Nullable
        public UnlockCondition getUnlockCondition() {
            return unlockCondition;
        }

        /**
         * @return the prerequisite visibility gate, or {@code null} if
         *         the ability is always visible (may still be locked)
         */
        @Nullable
        public Prerequisite getPrerequisite() {
            return prerequisite;
        }

        public boolean hasProgressionCondition() {
            return unlockCondition != null
                    && !(unlockCondition instanceof AlwaysUnlocked);
        }
    }
}