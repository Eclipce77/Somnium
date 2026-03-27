package net.eclipce.somnium.core.power;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
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
 * <h3>Passive default overrides</h3>
 * <p>When an ability is {@link ActivationType#PASSIVE}, each power can override
 * whether it starts enabled or disabled via
 * {@link Builder#ability(Supplier, Boolean)}. This allows the same passive
 * ability to default to ON in one power but OFF in another.</p>
 *
 * <h3>Progression (Layer 8)</h3>
 * <p>The {@link PowerAbilityEntry} class is designed to be extended with unlock
 * conditions when the progression system is implemented. For now, all abilities
 * in a power are available immediately when the power is granted.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public static final RegistryObject<Power> PYROMANCY = POWERS.register("pyromancy",
 *     () -> Power.builder()
 *         .displayName(Component.translatable("power.mymod.pyromancy"))
 *         .icon(new ResourceLocation("mymod", "textures/power/pyromancy.png"))
 *         .ability(() -> ModAbilities.FIRE_PUNCH.get())
 *         .ability(() -> ModAbilities.FIRE_BLAST.get())
 *         .ability(() -> ModAbilities.FIRE_RESISTANCE.get(), true)  // passive, starts ON
 *         .build()
 * );
 * }</pre>
 *
 * @see AbilityType
 * @see PowerAbilityEntry
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

    // ═══════════════════════════════════════════════════════════════════
    //  Construction (use Builder)
    // ═══════════════════════════════════════════════════════════════════

    private Power(Builder builder) {
        this.displayName = builder.displayName;
        this.icon = builder.icon;
        this.persistOnDeath = builder.persistOnDeath;
        this.progressionEnabled = builder.progressionEnabled;
        this.entries = Collections.unmodifiableList(new ArrayList<>(builder.entries));
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

        private Builder() {}

        /**
         * Sets the display name shown on the power's inventory tab.
         */
        public Builder displayName(Component name) {
            this.displayName = name;
            return this;
        }

        /**
         * Sets the icon texture for the power's inventory tab.
         */
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
         * Adds an ability to this power with no passive default override.
         * The ability's own {@link AbilityType#isDefaultEnabled()} will be used
         * for passive abilities.
         *
         * @param abilitySupplier a supplier for the ability type (use a supplier to
         *                        avoid registry ordering issues during mod loading)
         */
        public Builder ability(Supplier<AbilityType> abilitySupplier) {
            entries.add(new PowerAbilityEntry(abilitySupplier, null));
            return this;
        }

        /**
         * Adds an ability to this power with a passive default override.
         * This allows a power to control whether a passive ability starts
         * enabled or disabled, overriding the ability's own default.
         *
         * <p>For non-passive abilities, the override is stored but ignored.</p>
         *
         * @param abilitySupplier       a supplier for the ability type
         * @param defaultEnabledOverride {@code true} to start enabled, {@code false}
         *                               to start disabled, overriding the ability's default
         */
        public Builder ability(Supplier<AbilityType> abilitySupplier,
                               @Nullable Boolean defaultEnabledOverride) {
            entries.add(new PowerAbilityEntry(abilitySupplier, defaultEnabledOverride));
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
     * per-power metadata for that ability.
     *
     * <p>Currently holds the passive default override. When the progression
     * system is implemented (Layer 8), this class will be extended to include
     * the {@code UnlockCondition} for this ability within this power.</p>
     *
     * <p>The ability is stored as a {@link Supplier} to avoid registry
     * ordering issues during mod loading — the supplier is resolved lazily
     * when the ability is actually needed.</p>
     */
    public static class PowerAbilityEntry {

        private final Supplier<AbilityType> abilitySupplier;
        private final Boolean defaultEnabledOverride;

        // Future (Layer 8): private UnlockCondition unlockCondition;

        /**
         * Creates a new entry.
         *
         * @param abilitySupplier       supplier for the ability type
         * @param defaultEnabledOverride passive default override, or {@code null}
         *                               to use the ability's own default
         */
        public PowerAbilityEntry(Supplier<AbilityType> abilitySupplier,
                                 @Nullable Boolean defaultEnabledOverride) {
            this.abilitySupplier = abilitySupplier;
            this.defaultEnabledOverride = defaultEnabledOverride;
        }

        /**
         * @return the ability type, resolved from the supplier. May return
         *         {@code null} if the supplier fails (shouldn't happen after
         *         registry loading is complete).
         */
        @Nullable
        public AbilityType getAbilityType() {
            return abilitySupplier.get();
        }

        /**
         * Returns the effective default enabled state for a passive ability
         * within this power. If an override was specified, it takes priority
         * over the ability's own default.
         *
         * @return the effective default enabled state
         */
        public boolean getEffectiveDefaultEnabled() {
            AbilityType type = getAbilityType();
            if (defaultEnabledOverride != null) {
                return defaultEnabledOverride;
            }
            return type != null ? type.isDefaultEnabled() : false;
        }

        /**
         * @return the passive default override, or {@code null} if the ability's
         *         own default should be used
         */
        @Nullable
        public Boolean getDefaultEnabledOverride() {
            return defaultEnabledOverride;
        }
    }
}