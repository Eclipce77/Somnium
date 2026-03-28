package net.eclipce.somnium.core.ability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A per-player instance of an ability, holding runtime state. Analogous to
 * {@link net.minecraft.world.item.ItemStack}.
 *
 * <p>While {@link AbilityType} is a shared singleton defining behavior,
 * {@code AbilityInstance} holds the state that varies per player: cooldown
 * progress, toggle on/off, charge ticks, passive enabled state, and any
 * custom data the addon developer needs.</p>
 *
 * <p>Every player who has an ability gets their own {@code AbilityInstance}
 * for it. Two players with Fire Blast share the same {@link AbilityType}
 * but have separate {@code AbilityInstance} objects tracking their
 * individual cooldowns.</p>
 *
 * <h3>Custom data</h3>
 * <p>For abilities that need additional per-player state beyond what's built
 * in (e.g., upgrade tier, accumulated damage, combo counter), use
 * {@link #getCustomData()} to get a {@link CompoundTag} you can freely
 * read/write. It serializes automatically with the rest of the instance.</p>
 *
 * @see AbilityType
 * @see AbilityType#createInstance()
 */
public class AbilityInstance {

    // ═══════════════════════════════════════════════════════════════════
    //  Constants — NBT keys for serialization
    // ═══════════════════════════════════════════════════════════════════

    private static final String TAG_ABILITY_ID = "AbilityId";
    private static final String TAG_COOLDOWN = "Cooldown";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_CHARGE_TICKS = "ChargeTicks";
    private static final String TAG_ENABLED = "Enabled";
    private static final String TAG_CUSTOM_DATA = "CustomData";

    // ═══════════════════════════════════════════════════════════════════
    //  Fields
    // ═══════════════════════════════════════════════════════════════════

    private final AbilityType abilityType;

    /** Remaining cooldown ticks. 0 = ready. */
    private int cooldownRemaining;

    /** Maximum cooldown ticks for the current cooldown cycle (for UI progress bar). */
    private int cooldownMax;

    /** Whether this ability is currently active (used by TOGGLE abilities). */
    private boolean active;

    /** How many ticks this ability has been charging (used by HOLD and CHARGED abilities). */
    private int chargeTicks;

    /** Whether this passive ability is enabled (used by PASSIVE abilities). */
    private boolean enabled;

    /** Free-form data for addon developers to store custom per-player state. */
    private CompoundTag customData;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new AbilityInstance for the given type with default state.
     * Typically called by {@link AbilityType#createInstance()}.
     *
     * @param abilityType the ability type this instance represents
     */
    public AbilityInstance(AbilityType abilityType) {
        this.abilityType = abilityType;
        this.cooldownRemaining = 0;
        this.cooldownMax = 0;
        this.active = false;
        this.chargeTicks = 0;
        this.enabled = abilityType.isDefaultEnabled();
        this.customData = new CompoundTag();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ability Type
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return the {@link AbilityType} this instance is bound to
     */
    public AbilityType getAbilityType() {
        return abilityType;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Cooldown
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Starts the cooldown timer. Called by {@link AbilityType#applyCosts}
     * after a successful activation.
     *
     * @param ticks the number of ticks to cool down for
     */
    public void startCooldown(int ticks) {
        this.cooldownMax = ticks;
        this.cooldownRemaining = ticks;
    }

    /**
     * Decrements the cooldown by one tick. Called every tick by the ability
     * system for instances that are on cooldown.
     */
    public void tickCooldown() {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }
    }

    /**
     * Immediately clears the cooldown (e.g., for a "cooldown reset" effect).
     */
    public void clearCooldown() {
        this.cooldownRemaining = 0;
        this.cooldownMax = 0;
    }

    /** @return {@code true} if this ability is currently on cooldown */
    public boolean isOnCooldown() {
        return cooldownRemaining > 0;
    }

    /** @return the remaining cooldown in ticks, or 0 if ready */
    public int getCooldownRemaining() {
        return cooldownRemaining;
    }

    /** @return the maximum cooldown for the current cycle (for UI progress display) */
    public int getCooldownMax() {
        return cooldownMax;
    }

    /**
     * @return cooldown progress as a float from 0.0 (just started) to 1.0 (ready).
     *         Returns 1.0 if not on cooldown. Used by the HUD to render a cooldown overlay.
     */
    public float getCooldownProgress() {
        if (cooldownMax <= 0) return 1.0f;
        return 1.0f - ((float) cooldownRemaining / (float) cooldownMax);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Active state (TOGGLE)
    // ═══════════════════════════════════════════════════════════════════

    /** @return {@code true} if this toggle ability is currently active */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the active state. Used by the ability activation system for
     * TOGGLE abilities — not typically called directly by addon devs.
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Charge state (HOLD / CHARGED)
    // ═══════════════════════════════════════════════════════════════════

    /** @return how many ticks this ability has been charging */
    public int getChargeTicks() {
        return chargeTicks;
    }

    /** Increments the charge counter by one tick. */
    public void incrementChargeTicks() {
        chargeTicks++;
    }

    /** Resets the charge counter to zero. */
    public void resetChargeTicks() {
        chargeTicks = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Enabled state (PASSIVE)
    // ═══════════════════════════════════════════════════════════════════

    /** @return {@code true} if this passive ability is currently enabled */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled state for this passive ability. Called when the
     * player clicks the ability in the Passives tab of the inventory.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom data
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns the custom data tag for this instance. Addon developers can
     * store any additional per-player ability state here (upgrade levels,
     * combo counters, accumulated values, etc.). This tag is automatically
     * saved and loaded with the instance.
     *
     * <pre>{@code
     * // In your ability's onActivate:
     * CompoundTag data = context.getAbilityInstance().getCustomData();
     * int combo = data.getInt("ComboCount");
     * data.putInt("ComboCount", combo + 1);
     * }</pre>
     *
     * @return the mutable custom data tag
     */
    public CompoundTag getCustomData() {
        return customData;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  NBT serialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Serializes this instance to an NBT tag for saving to disk.
     *
     * <p>The ability type is stored by its registry name (a {@link ResourceLocation}).
     * This requires the ability registry to be set up (Layer 2). The
     * {@code abilityId} parameter is provided externally by the serialization
     * system that has access to the registry.</p>
     *
     * @param abilityId the registry name of this instance's AbilityType,
     *                  resolved via the ability registry
     * @return the serialized CompoundTag
     */
    public CompoundTag serializeNBT(ResourceLocation abilityId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ABILITY_ID, abilityId.toString());
        tag.putInt(TAG_COOLDOWN, cooldownRemaining);
        tag.putBoolean(TAG_ACTIVE, active);
        tag.putInt(TAG_CHARGE_TICKS, chargeTicks);
        tag.putBoolean(TAG_ENABLED, enabled);

        if (!customData.isEmpty()) {
            tag.put(TAG_CUSTOM_DATA, customData.copy());
        }

        // Allow subclasses (e.g., TransformationInstance) to write their own data
        writeAdditionalData(tag);

        return tag;
    }

    /**
     * Deserializes an AbilityInstance from NBT.
     *
     * <p>Requires a lookup function to resolve the stored ability ID back to an
     * {@link AbilityType}. This function will be provided by the registry system
     * (Layer 2). Returns {@code null} if the ability type cannot be found
     * (e.g., the mod that registered it was removed).</p>
     *
     * @param tag    the NBT tag to read from
     * @param lookup a function that resolves a ResourceLocation to an AbilityType,
     *               typically provided by the ability registry
     * @return the deserialized instance, or {@code null} if the ability type is unknown
     */
    @Nullable
    public static AbilityInstance deserializeNBT(CompoundTag tag, AbilityTypeLookup lookup) {
        ResourceLocation abilityId = new ResourceLocation(tag.getString(TAG_ABILITY_ID));
        AbilityType type = lookup.resolve(abilityId);

        if (type == null) {
            return null; // Ability type no longer exists (mod removed?)
        }

        AbilityInstance instance = type.createInstance();
        instance.cooldownRemaining = tag.getInt(TAG_COOLDOWN);
        instance.active = tag.getBoolean(TAG_ACTIVE);
        instance.chargeTicks = tag.getInt(TAG_CHARGE_TICKS);
        instance.enabled = tag.getBoolean(TAG_ENABLED);

        if (tag.contains(TAG_CUSTOM_DATA)) {
            instance.customData = tag.getCompound(TAG_CUSTOM_DATA).copy();
        }

        // Recalculate cooldownMax from the type, since we don't save it
        // (it's a property of the type, not the instance)
        if (instance.cooldownRemaining > 0) {
            instance.cooldownMax = type.getCooldownTicks();
        }

        // Allow subclasses (e.g., TransformationInstance) to read their own data
        instance.readAdditionalData(tag);

        return instance;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Subclass serialization hooks
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called at the end of {@link #serializeNBT} to allow subclasses to write
     * additional data to the NBT tag. Override this in subclasses that have
     * extra per-instance state (e.g., {@code TransformationInstance} stores
     * the current transformation phase).
     *
     * <p>The base implementation does nothing. Subclasses should call
     * {@code super.writeAdditionalData(tag)} for future-proofing.</p>
     *
     * @param tag the NBT tag being written to (already contains base fields)
     */
    protected void writeAdditionalData(CompoundTag tag) {}

    /**
     * Called at the end of {@link #deserializeNBT} to allow subclasses to read
     * additional data from the NBT tag. Override this in subclasses that have
     * extra per-instance state.
     *
     * <p>The base implementation does nothing. Subclasses should call
     * {@code super.readAdditionalData(tag)} for future-proofing.</p>
     *
     * @param tag the NBT tag being read from (base fields already loaded)
     */
    protected void readAdditionalData(CompoundTag tag) {}

    /**
     * Functional interface for resolving ability IDs to types during deserialization.
     * Implemented by the ability registry system (Layer 2).
     */
    @FunctionalInterface
    public interface AbilityTypeLookup {
        /**
         * Resolves a registry name to its AbilityType.
         *
         * @param id the registry name of the ability
         * @return the AbilityType, or {@code null} if not found
         */
        @Nullable
        AbilityType resolve(ResourceLocation id);
    }
}