package net.eclipce.somnium.core.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The registered definition of an ability. Analogous to {@link net.minecraft.world.item.Item}.
 *
 * <p>Each {@code AbilityType} is a <strong>singleton</strong> — there is exactly one instance
 * per registered ability, shared across all players. Per-player state (cooldowns, charges,
 * toggle state) lives on {@link AbilityInstance}, not here.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <p>Subclass {@code AbilityType} and override the behavior methods that match your
 * {@link ActivationType}. Configure properties via the {@link Properties} builder:</p>
 * <pre>{@code
 * public class FireBlastAbility extends AbilityType {
 *
 *     public FireBlastAbility() {
 *         super(new Properties()
 *             .activationType(ActivationType.INSTANT)
 *             .cooldown(40)      // 2 seconds
 *             .resourceCost(5f)
 *             .icon(new ResourceLocation("mymod", "textures/ability/fire_blast.png"))
 *             .conflictTag(new ResourceLocation("mymod", "fire"))
 *         );
 *     }
 *
 *     @Override
 *     public void onActivate(AbilityActivationContext context) {
 *         // spawn fireball, deal damage, etc.
 *     }
 * }
 * }</pre>
 *
 * <p>Then register it in your {@code ModAbilities} class using a
 * {@link net.minecraftforge.registries.DeferredRegister}.</p>
 *
 * @see AbilityInstance
 * @see ActivationType
 * @see AbilityActivationContext
 */
public class AbilityType {

    // ═══════════════════════════════════════════════════════════════════
    //  Properties (set at construction, immutable after)
    // ═══════════════════════════════════════════════════════════════════

    private final ActivationType activationType;
    private final int cooldownTicks;
    private final float resourceCost;
    private final boolean persistOnDeath;
    private final boolean defaultEnabled;
    private final ResourceLocation icon;
    private final Set<ResourceLocation> conflictTags;

    /**
     * Creates an AbilityType with the given properties.
     *
     * @param properties configuration built via the {@link Properties} builder
     */
    public AbilityType(Properties properties) {
        this.activationType = properties.activationType;
        this.cooldownTicks = properties.cooldownTicks;
        this.resourceCost = properties.resourceCost;
        this.persistOnDeath = properties.persistOnDeath;
        this.defaultEnabled = properties.defaultEnabled;
        this.icon = properties.icon;
        this.conflictTags = Collections.unmodifiableSet(new HashSet<>(properties.conflictTags));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Behavior methods — override the ones matching your ActivationType
    // ═══════════════════════════════════════════════════════════════════

    // --- INSTANT & TOGGLE ---

    /**
     * Called when the ability is activated.
     * <p>For {@link ActivationType#INSTANT}: this is the entire effect.</p>
     * <p>For {@link ActivationType#TOGGLE}: this fires when toggled ON.</p>
     *
     * @param context the activation context
     */
    public void onActivate(AbilityActivationContext context) {}

    /**
     * Called when a {@link ActivationType#TOGGLE} ability is turned OFF.
     *
     * @param context the activation context
     */
    public void onDeactivate(AbilityActivationContext context) {}

    /**
     * Called every tick while a {@link ActivationType#TOGGLE} ability is active.
     *
     * @param context the activation context
     */
    public void onActiveTick(AbilityActivationContext context) {}

    // --- HOLD ---

    /**
     * Called the first tick the key is pressed for a {@link ActivationType#HOLD} ability.
     *
     * @param context the activation context
     */
    public void onKeyDown(AbilityActivationContext context) {}

    /**
     * Called every tick while the key remains held for a {@link ActivationType#HOLD} ability.
     *
     * @param context the activation context
     */
    public void onKeyHeld(AbilityActivationContext context) {}

    /**
     * Called when the key is released for a {@link ActivationType#HOLD} ability.
     *
     * @param context the activation context
     */
    public void onKeyUp(AbilityActivationContext context) {}

    // --- PASSIVE ---

    /**
     * Called when a {@link ActivationType#PASSIVE} ability is enabled
     * (clicked ON in the Passives tab).
     *
     * @param context the activation context
     */
    public void onEnabled(AbilityActivationContext context) {}

    /**
     * Called when a {@link ActivationType#PASSIVE} ability is disabled
     * (clicked OFF in the Passives tab).
     *
     * @param context the activation context
     */
    public void onDisabled(AbilityActivationContext context) {}

    /**
     * Called every tick while a {@link ActivationType#PASSIVE} ability is enabled.
     *
     * @param context the activation context
     */
    public void onPassiveTick(AbilityActivationContext context) {}

    // --- CHARGED ---

    /**
     * Called the first tick the key is pressed to begin charging a
     * {@link ActivationType#CHARGED} ability.
     *
     * @param context the activation context
     */
    public void onChargeStart(AbilityActivationContext context) {}

    /**
     * Called every tick while the key is held and the ability is charging.
     *
     * @param context      the activation context
     * @param ticksCharged how many ticks the ability has been charging
     */
    public void onChargeTick(AbilityActivationContext context, int ticksCharged) {}

    /**
     * Called when the key is released, executing the charged ability.
     *
     * @param context      the activation context
     * @param ticksCharged how many ticks the ability was charged for
     */
    public void onChargeRelease(AbilityActivationContext context, int ticksCharged) {}

    // ═══════════════════════════════════════════════════════════════════
    //  Validation — override to add custom activation conditions
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called before activation to check if the ability CAN be used right now.
     * The base implementation checks cooldown and resource cost. Override to
     * add custom checks (e.g., "must be on fire", "must be in the Nether"),
     * but call {@code super.canActivate(context)} to preserve built-in checks.
     *
     * @param context the activation context
     * @return {@code true} if the ability can be activated
     */
    public boolean canActivate(AbilityActivationContext context) {
        AbilityInstance instance = context.getAbilityInstance();

        // Cannot activate while on cooldown
        if (instance.isOnCooldown()) {
            return false;
        }

        // Resource cost check — currently a placeholder.
        // When the stamina/resource system is implemented, this will query
        // the player's resource pool and return false if insufficient.
        // For now, abilities with a cost > 0 still activate (no enforcement yet).

        return true;
    }

    /**
     * Called after a successful activation to apply costs (cooldown, resources).
     * The base implementation starts the cooldown. Override to add custom cost
     * application, but call {@code super.applyCosts(context)} to preserve
     * built-in behavior.
     *
     * @param context the activation context
     */
    public void applyCosts(AbilityActivationContext context) {
        if (cooldownTicks > 0) {
            context.getAbilityInstance().startCooldown(cooldownTicks);
        }
        // Resource deduction will be added here when the resource system is implemented
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Factory
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new {@link AbilityInstance} for this ability type.
     * Called when a player is granted an ability. Override to provide
     * a custom AbilityInstance subclass with additional state.
     *
     * @return a fresh AbilityInstance referencing this type
     */
    public AbilityInstance createInstance() {
        return new AbilityInstance(this);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Display — override for custom behavior
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns the display name of this ability shown in the inventory and bar.
     * Default implementation returns a translatable component using the pattern
     * {@code "ability.modid.abilityname"}.
     *
     * <p><strong>Note:</strong> This requires the registry system (Layer 2) to
     * resolve the registry name. Until then, returns a placeholder. Override
     * in your subclass for a hardcoded name during early development.</p>
     *
     * @return the display name component
     */
    public Component getDisplayName() {
        // Will use registry name for translation key once registry is built:
        // "ability." + registryName.getNamespace() + "." + registryName.getPath()
        return Component.literal("Unnamed Ability");
    }

    /**
     * Returns the description of this ability, shown as a tooltip in the
     * ability inventory. Override to provide a custom description.
     *
     * @return the description component, or {@code null} for no description
     */
    public Component getDescription() {
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /** @return the activation type that determines how this ability is triggered */
    public ActivationType getActivationType() {
        return activationType;
    }

    /** @return cooldown duration in ticks after activation, or 0 for no cooldown */
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /**
     * @return the resource cost to activate this ability. Currently a raw float
     *         that will be consumed by the resource/stamina system when implemented.
     *         A value of 0 means no cost.
     */
    public float getResourceCost() {
        return resourceCost;
    }

    /** @return {@code true} if this ability is kept when the player dies (default: true) */
    public boolean isPersistOnDeath() {
        return persistOnDeath;
    }

    /**
     * @return the default enabled state for {@link ActivationType#PASSIVE} abilities.
     *         When a passive is first granted, this determines whether it starts ON or OFF.
     *         Ignored for non-passive abilities.
     */
    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    /**
     * @return the icon texture location for rendering in the ability bar and inventory.
     *         May be {@code null} if no icon has been set (a default placeholder will
     *         be rendered by the UI layer).
     */
    public ResourceLocation getIcon() {
        return icon;
    }

    /**
     * @return an unmodifiable set of conflict tags. Two abilities with overlapping
     *         conflict tags may conflict — the API provides query utilities, but does
     *         not block equipping by default. Addon devs can enforce exclusivity via events.
     */
    public Set<ResourceLocation> getConflictTags() {
        return conflictTags;
    }

    /**
     * @return {@code true} if this ability type can be placed on the ability bar.
     *         Returns {@code false} for {@link ActivationType#PASSIVE} abilities.
     */
    public boolean isBarEquippable() {
        return activationType.isBarEquippable();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Properties builder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Configuration builder for {@link AbilityType}. Follows the same pattern as
     * {@link net.minecraft.world.item.Item.Properties} — create one, chain setters,
     * pass to the constructor.
     *
     * <pre>{@code
     * new AbilityType.Properties()
     *     .activationType(ActivationType.TOGGLE)
     *     .cooldown(60)
     *     .icon(new ResourceLocation("mymod", "textures/ability/flight.png"))
     * }</pre>
     */
    public static class Properties {

        ActivationType activationType = ActivationType.INSTANT;
        int cooldownTicks = 0;
        float resourceCost = 0f;
        boolean persistOnDeath = true;
        boolean defaultEnabled = false;
        ResourceLocation icon = null;
        Set<ResourceLocation> conflictTags = new HashSet<>();

        /**
         * Sets the activation type. Defaults to {@link ActivationType#INSTANT}.
         */
        public Properties activationType(ActivationType type) {
            this.activationType = type;
            return this;
        }

        /**
         * Sets the cooldown duration in ticks (20 ticks = 1 second).
         * A value of 0 (default) means no cooldown.
         */
        public Properties cooldown(int ticks) {
            this.cooldownTicks = Math.max(0, ticks);
            return this;
        }

        /**
         * Sets the resource cost for activation. The resource system will
         * consume this amount when the ability is used. Default is 0 (free).
         */
        public Properties resourceCost(float cost) {
            this.resourceCost = Math.max(0f, cost);
            return this;
        }

        /**
         * Sets whether this ability persists through player death.
         * Default is {@code true}.
         */
        public Properties persistOnDeath(boolean persist) {
            this.persistOnDeath = persist;
            return this;
        }

        /**
         * Sets the default enabled state for passive abilities. When a passive
         * ability is first granted, this determines if it starts ON or OFF.
         * Default is {@code false} (starts OFF). Ignored for non-passive abilities.
         */
        public Properties defaultEnabled(boolean enabled) {
            this.defaultEnabled = enabled;
            return this;
        }

        /**
         * Sets the icon texture used in the ability bar and inventory UI.
         * Should point to a texture file, e.g.:
         * {@code new ResourceLocation("mymod", "textures/ability/fire_blast.png")}
         */
        public Properties icon(ResourceLocation icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Adds a conflict tag. Abilities sharing a conflict tag may conflict
         * when both are equipped or active. The API provides utilities to
         * query conflicts, but enforcement is left to addon devs via events.
         *
         * @param tag a ResourceLocation identifying the conflict category,
         *            e.g., {@code new ResourceLocation("somnium", "movement")}
         */
        public Properties conflictTag(ResourceLocation tag) {
            this.conflictTags.add(tag);
            return this;
        }
    }
}