package net.eclipce.somnium.core.ability;

import net.eclipce.somnium.core.category.AbilityCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
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
    @Nullable
    private final AbilityCategory category;
    @Nullable
    private final net.eclipce.somnium.core.meter.MeterCost staminaCost;
    private final java.util.Map<ResourceLocation, net.eclipce.somnium.core.meter.MeterCost> meterCosts;
    @Nullable
    private final String castAnimation;
    @Nullable
    private final net.minecraft.resources.ResourceLocation castAnimationModel;
    private final boolean compositionOptOut;
    private final boolean enabledOnGrant;
    private final boolean forced;
    private final int powerXP;
    private final int levelRequirement;

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
        this.conflictTags = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(properties.conflictTags));
        this.category = properties.category;
        this.staminaCost = properties.staminaCost;
        this.meterCosts = java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(properties.meterCosts));
        this.castAnimation = properties.castAnimation;
        this.castAnimationModel = properties.castAnimationModel;
        this.compositionOptOut = properties.compositionOptOut;
        this.enabledOnGrant = properties.enabledOnGrant;
        this.forced = properties.forced;
        this.powerXP = properties.powerXP;
        this.levelRequirement = properties.levelRequirement;
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
        // Use registry-based translation key: "ability.modid.abilityname"
        ResourceLocation key = net.eclipce.somnium.core.registry.SomniumRegistries.getAbilityKey(this);
        if (key != null) {
            String translationKey = "ability." + key.getNamespace() + "." + key.getPath();
            Component translated = Component.translatable(translationKey);
            // If translation resolves to itself (key not in lang file), use a formatted fallback
            if (translated.getString().equals(translationKey)) {
                // Convert "fire_blast" → "Fire Blast"
                String path = key.getPath();
                String[] words = path.split("_");
                StringBuilder name = new StringBuilder();
                for (String word : words) {
                    if (!name.isEmpty()) name.append(" ");
                    if (!word.isEmpty()) {
                        name.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) name.append(word.substring(1));
                    }
                }
                return Component.literal(name.toString());
            }
            return translated;
        }
        return Component.literal("Unnamed Ability");
    }

    /**
     * Returns the description of this ability, shown as a tooltip in the
     * ability inventory. Default implementation uses the translation key
     * {@code "ability.modid.abilityname.desc"}. If the key is not defined
     * in the lang file, returns {@code null} (no description shown).
     *
     * <p>Override to provide a custom description programmatically.</p>
     *
     * @return the description component, or {@code null} for no description
     */
    public Component getDescription() {
        ResourceLocation key = net.eclipce.somnium.core.registry.SomniumRegistries.getAbilityKey(this);
        if (key != null) {
            String descKey = "ability." + key.getNamespace() + "." + key.getPath() + ".desc";
            Component translated = Component.translatable(descKey);
            // If the translation resolves to the raw key, no description is defined
            if (!translated.getString().equals(descKey)) {
                return translated;
            }
        }
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
     * Returns the intrinsic conflict tags declared at construction time via
     * {@link Properties#conflictTag}. These are automatically converted to
     * data-driven Forge tags by {@code SomniumAbilityTagsProvider} during datagen.
     *
     * <p><strong>For runtime conflict checking, use
     * {@link net.eclipce.somnium.core.registry.SomniumRegistries#isAbilityInTag}
     * or {@link net.eclipce.somnium.core.registry.SomniumRegistries#doAbilitiesConflict}
     * </strong>, which query the full tag system (including datapack additions).
     * This method only returns the code-declared tags, not tags added via JSON.</p>
     *
     * @return an unmodifiable set of intrinsic conflict tag IDs
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

    /**
     * @return the custom category this ability belongs to, or {@code null}
     *         if it's a standard ability (shown in the Abilities tab).
     *         Abilities with a category appear in that category's dedicated tab.
     */
    @Nullable
    public AbilityCategory getCategory() {
        return category;
    }

    /** @return the stamina cost for this ability, or null if none */
    @Nullable
    public net.eclipce.somnium.core.meter.MeterCost getStaminaCost() {
        return staminaCost;
    }

    /** @return all custom meter costs for this ability (unmodifiable) */
    public java.util.Map<ResourceLocation, net.eclipce.somnium.core.meter.MeterCost> getMeterCosts() {
        return meterCosts;
    }

    /**
     * @return the GeckoLib animation name to play on cast, or {@code null}
     *         if no cast animation is set. Requires GeckoLib to be loaded.
     */
    @Nullable
    public String getCastAnimation() {
        return castAnimation;
    }

    /**
     * @return the {@link net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationModelRegistry}
     *         key of the model that drives this ability's cast animation, or {@code null} if none.
     */
    @Nullable
    public net.minecraft.resources.ResourceLocation getCastAnimationModel() {
        return castAnimationModel;
    }

    /** @return true if this ability should NOT grant composition on use */
    public boolean isCompositionOptOut() {
        return compositionOptOut;
    }

    /** @return true if this passive should be auto-enabled when granted */
    public boolean isEnabledOnGrant() {
        return enabledOnGrant;
    }

    /** @return true if this passive is forced on and cannot be toggled off */
    public boolean isForced() {
        return forced;
    }

    /** @return XP granted to the parent power when this ability is activated */
    public int getPowerXP() {
        return powerXP;
    }

    /** @return the power level required to unlock this ability (0 = no requirement) */
    public int getLevelRequirement() {
        return levelRequirement;
    }

    /**
     * Checks if the player can afford all meter costs for this ability.
     * Legacy overload without player context — the
     * {@link net.eclipce.somnium.event.AbilityMeterCostEvent} still fires,
     * with a null player. Prefer the player-aware overload.
     *
     * @param data the player's data
     * @return true if all meter requirements are met
     */
    public boolean canAffordMeterCosts(net.eclipce.somnium.core.data.SomniumPlayerData data) {
        return canAffordMeterCosts(data, null);
    }

    /**
     * Checks if the player can afford all meter costs for this ability.
     *
     * <p>Fires {@link net.eclipce.somnium.event.AbilityMeterCostEvent} for each
     * DRAIN cost so addons can scale costs per player (e.g. proficiency-based
     * efficiency). The multiplier scales both the drain amount and the
     * required-minimum gate, so this check always agrees with
     * {@link #applyMeterCosts(net.eclipce.somnium.core.data.SomniumPlayerData, net.minecraft.server.level.ServerPlayer)}.</p>
     *
     * @param data   the player's data
     * @param player the activating player; nullable in legacy call paths
     * @return true if all meter requirements are met
     */
    public boolean canAffordMeterCosts(net.eclipce.somnium.core.data.SomniumPlayerData data,
                                       @Nullable net.minecraft.server.level.ServerPlayer player) {
        if (staminaCost != null) {
            float mult = fireCostEvent(player, data, null, staminaCost);
            if (!data.getStaminaData().canAfford(staminaCost.scaledRequiredMinimum(mult))) {
                return false;
            }
        }
        for (java.util.Map.Entry<ResourceLocation, net.eclipce.somnium.core.meter.MeterCost> entry
                : meterCosts.entrySet()) {
            net.eclipce.somnium.core.meter.MeterInstance meter = data.getMeter(entry.getKey());
            if (meter == null) {
                return false;
            }
            float mult = fireCostEvent(player, data, entry.getKey(), entry.getValue());
            if (!entry.getValue().canActivate(meter, mult)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies all meter costs. Legacy overload without player context.
     * Prefer the player-aware overload.
     *
     * @param data the player's data
     */
    public void applyMeterCosts(net.eclipce.somnium.core.data.SomniumPlayerData data) {
        applyMeterCosts(data, null);
    }

    /**
     * Applies all meter costs. Call after successful activation. Fires
     * {@link net.eclipce.somnium.event.AbilityMeterCostEvent} per DRAIN cost;
     * see the afford-check overload for the contract.
     *
     * @param data   the player's data
     * @param player the activating player; nullable in legacy call paths
     */
    public void applyMeterCosts(net.eclipce.somnium.core.data.SomniumPlayerData data,
                                @Nullable net.minecraft.server.level.ServerPlayer player) {
        if (staminaCost != null) {
            float mult = fireCostEvent(player, data, null, staminaCost);
            if (staminaCost.isOveruseExempt()) {
                data.getStaminaData().drainWithoutOveruse(staminaCost.scaledAmount(mult));
            } else {
                data.getStaminaData().drain(staminaCost.scaledAmount(mult));
            }
        }
        for (java.util.Map.Entry<ResourceLocation, net.eclipce.somnium.core.meter.MeterCost> entry
                : meterCosts.entrySet()) {
            net.eclipce.somnium.core.meter.MeterInstance meter = data.getMeter(entry.getKey());
            if (meter != null) {
                float mult = fireCostEvent(player, data, entry.getKey(), entry.getValue());
                entry.getValue().apply(meter, mult);
            }
        }
    }

    /**
     * Posts an {@link net.eclipce.somnium.event.AbilityMeterCostEvent} for a
     * single cost and returns the resulting multiplier. Non-DRAIN operations
     * never fire (they can't be scaled) and always return 1.0.
     */
    private float fireCostEvent(@Nullable net.minecraft.server.level.ServerPlayer player,
                                net.eclipce.somnium.core.data.SomniumPlayerData data,
                                @Nullable ResourceLocation meterId,
                                net.eclipce.somnium.core.meter.MeterCost cost) {
        if (cost.getOperation() != net.eclipce.somnium.core.meter.MeterCost.Operation.DRAIN) {
            return 1.0f;
        }
        net.eclipce.somnium.event.AbilityMeterCostEvent event =
                new net.eclipce.somnium.event.AbilityMeterCostEvent(player, data, this, meterId, cost);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        return event.getMultiplier();
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
        @Nullable AbilityCategory category = null;
        @Nullable net.eclipce.somnium.core.meter.MeterCost staminaCost = null;
        final java.util.Map<ResourceLocation, net.eclipce.somnium.core.meter.MeterCost> meterCosts =
                new java.util.LinkedHashMap<>();
        @Nullable String castAnimation = null;
        @Nullable net.minecraft.resources.ResourceLocation castAnimationModel = null;
        boolean compositionOptOut = false;
        boolean enabledOnGrant = false;
        boolean forced = false;
        int powerXP = 1;
        int levelRequirement = 0; // 0 = no level requirement

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

        /**
         * Assigns this ability to a custom category. Abilities with a category
         * appear in that category's dedicated inventory tab and can only be
         * equipped to that category's bar.
         *
         * @param category the custom category to assign to
         * @see AbilityCategory
         */
        public Properties category(AbilityCategory category) {
            this.category = category;
            return this;
        }

        /**
         * Sets a stamina cost for this ability.
         *
         * @param cost the stamina meter cost
         * @see net.eclipce.somnium.core.meter.MeterCost
         */
        public Properties staminaCost(net.eclipce.somnium.core.meter.MeterCost cost) {
            this.staminaCost = cost;
            return this;
        }

        /**
         * Simple stamina drain. Drains the amount on activation, requires
         * at least that amount to activate.
         *
         * @param amount stamina to drain (also the minimum required)
         */
        public Properties staminaCost(float amount) {
            this.staminaCost = net.eclipce.somnium.core.meter.MeterCost.drain(amount, amount);
            return this;
        }

        /**
         * Stamina drain that does NOT trigger overuse processing.
         * Use for utility, defensive, or healing abilities.
         *
         * @param amount stamina to drain
         */
        public Properties staminaCostExempt(float amount) {
            this.staminaCost = net.eclipce.somnium.core.meter.MeterCost.drainExempt(amount);
            return this;
        }

        /**
         * Adds a custom meter cost for this ability.
         *
         * @param meterId the meter definition ID
         * @param cost    the meter cost
         */
        public Properties meterCost(ResourceLocation meterId,
                                    net.eclipce.somnium.core.meter.MeterCost cost) {
            this.meterCosts.put(meterId, cost);
            return this;
        }

        /**
         * Simple custom meter drain. Drains amount, requires at least that amount.
         *
         * @param meterId the meter definition ID
         * @param amount  the amount to drain (also the minimum required)
         */
        public Properties meterCost(ResourceLocation meterId, float amount) {
            this.meterCosts.put(meterId,
                    net.eclipce.somnium.core.meter.MeterCost.drain(amount, amount));
            return this;
        }

        /**
         * Sets a GeckoLib animation to play on the player when this ability
         * is activated. Requires GeckoLib to be loaded — silently ignored
         * if GeckoLib is not present.
         *
         * @param animationName the animation name (e.g., "animation.mymod.cast_fire")
         */
        public Properties castAnimation(String animationName) {
            this.castAnimation = animationName;
            return this;
        }

        /**
         * Sets the GeckoLib cast animation and the model registry key that drives it.
         *
         * @param animationName the animation name declared in your {@code .animation.json}
         *                      (e.g. {@code "animation.mymod.fire_cast"})
         * @param modelId       key matching a model registered via
         *                      {@link net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationModelRegistry}
         *                      (e.g. {@code new ResourceLocation("mymod", "fire_cast")})
         */
        public Properties castAnimation(String animationName,
                                        net.minecraft.resources.ResourceLocation modelId) {
            this.castAnimation = animationName;
            this.castAnimationModel = modelId;
            return this;
        }

        /**
         * Opts this ability out of granting composition on use.
         * Default is opt-in (abilities grant composition).
         */
        public Properties compositionOptOut() {
            this.compositionOptOut = true;
            return this;
        }

        /**
         * For PASSIVE abilities: automatically enables the passive when
         * the player first receives it, giving them the effect immediately.
         */
        public Properties enabledOnGrant() {
            this.enabledOnGrant = true;
            return this;
        }

        /**
         * For PASSIVE abilities: permanently forces the passive on while
         * the player has the power. Cannot be toggled off by the player.
         */
        public Properties forced() {
            this.forced = true;
            this.enabledOnGrant = true; // forced implies enabled on grant
            return this;
        }

        /**
         * Sets the power XP granted when this ability is activated.
         * Default is 1. Set to 0 to grant no power XP.
         */
        public Properties powerXP(int amount) {
            this.powerXP = amount;
            return this;
        }

        /**
         * Sets the power level required to unlock this ability.
         * Only effective when the parent power has {@code powerLevelEnabled()}.
         * Default is 0 (no requirement).
         */
        public Properties levelRequirement(int level) {
            this.levelRequirement = level;
            return this;
        }
    }
}