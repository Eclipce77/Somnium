package net.eclipce.somnium.core.data;

import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.ability.transformation.TransformationInstance;
import net.eclipce.somnium.core.ability.transformation.TransformationPhase;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.unlock.ProgressionHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Holds all ability-related data for a single player.
 *
 * <p>This is the central data object for the Somnium API. Every player has
 * exactly one {@code SomniumPlayerData} instance, attached via Forge's
 * capability system. It stores:</p>
 * <ul>
 *     <li><strong>Granted Powers:</strong> which powers the player has been given</li>
 *     <li><strong>Unlocked Abilities:</strong> which abilities the player has access to
 *         (subset of abilities in their granted powers)</li>
 *     <li><strong>Ability Inventory:</strong> per-player {@link AbilityInstance} objects
 *         for each unlocked ability (tracks cooldowns, toggle state, etc.)</li>
 *     <li><strong>Ability Bar:</strong> which abilities are equipped in the 6 bar slots</li>
 *     <li><strong>Passive States:</strong> on/off state for each passive ability</li>
 *     <li><strong>Active Transformation:</strong> which transformation (if any) is
 *         currently active on the player</li>
 * </ul>
 *
 * <p>This class is purely data — it does not perform any game logic (no ticking,
 * no activation, no rendering). Those behaviors are handled by systems in later
 * layers that read and write this data.</p>
 *
 * <h3>For addon developers</h3>
 * <p>You generally don't create or manage these directly. Use the static helper
 * methods on {@link SomniumCapability} to access a player's data:</p>
 * <pre>{@code
 * SomniumPlayerData data = SomniumCapability.get(player);
 * if (data != null) {
 *     data.grantPower(myPower);
 * }
 * }</pre>
 *
 * @see SomniumCapability
 * @see SomniumDataProvider
 */
public class SomniumPlayerData {

    /** Number of slots on each ability bar page. */
    public static final int BAR_SIZE = 6;

    /** Maximum number of ability bar pages. */
    public static final int MAX_PAGES = 3;

    // ═══════════════════════════════════════════════════════════════════
    //  NBT keys
    // ═══════════════════════════════════════════════════════════════════

    private static final String TAG_GRANTED_POWERS = "GrantedPowers";
    private static final String TAG_UNLOCKED_ABILITIES = "UnlockedAbilities";
    private static final String TAG_ABILITY_INVENTORY = "AbilityInventory";
    private static final String TAG_ABILITY_BAR = "AbilityBar";
    private static final String TAG_BAR_SLOT = "Slot";
    private static final String TAG_BAR_ABILITY_ID = "AbilityId";
    private static final String TAG_PASSIVE_STATES = "PassiveStates";
    private static final String TAG_PASSIVE_ID = "Id";
    private static final String TAG_PASSIVE_ENABLED = "Enabled";
    private static final String TAG_ACTIVE_TRANSFORMATION = "ActiveTransformation";
    private static final String TAG_BAR_PAGE = "Page";
    private static final String TAG_ACTIVE_PAGE = "ActivePage";
    private static final String TAG_UNLOCK_PROGRESS = "UnlockProgress";
    private static final String TAG_PROGRESS_KEY = "Key";
    private static final String TAG_PROGRESS_DATA = "Data";

    // ═══════════════════════════════════════════════════════════════════
    //  Fields
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The set of powers this player has been granted.
     * Stored as ResourceLocations internally for reliable serialization,
     * resolved to Power objects on demand via the registry.
     */
    private final Set<ResourceLocation> grantedPowers = new LinkedHashSet<>();

    /**
     * The set of abilities this player has unlocked.
     * An ability must be unlocked before it can appear in the inventory
     * or be equipped to the bar.
     */
    private final Set<ResourceLocation> unlockedAbilities = new LinkedHashSet<>();

    /**
     * Per-player ability instances, keyed by ability registry name.
     * Each instance tracks cooldown, toggle state, charges, and custom data
     * for one ability on this player.
     */
    private final Map<ResourceLocation, AbilityInstance> abilityInventory = new LinkedHashMap<>();

    /**
     * The ability bar — MAX_PAGES pages × BAR_SIZE slots.
     * Indexed as [page * BAR_SIZE + slot].
     */
    private final ResourceLocation[] abilityBar = new ResourceLocation[MAX_PAGES * BAR_SIZE];

    /**
     * The currently active bar page (0 to MAX_PAGES-1).
     */
    private int activePage = 0;

    /**
     * On/off state for passive abilities. Keyed by ability registry name.
     * Only contains entries for passive abilities the player has unlocked.
     */
    private final Map<ResourceLocation, Boolean> passiveStates = new LinkedHashMap<>();

    /**
     * The registry name of the currently active transformation, or null
     * if no transformation is active.
     */
    @Nullable
    private ResourceLocation activeTransformation = null;

    /**
     * Per-ability-per-power progression data. Keys are composite strings
     * in the format "powerKey|abilityKey". Values are CompoundTags
     * containing condition-specific progress (counters, flags, etc.).
     * Only contains entries for locked abilities with active conditions.
     */
    private final Map<String, CompoundTag> unlockProgress = new LinkedHashMap<>();

    /**
     * Dirty flag — set to true whenever data changes and needs to be
     * synced to the client. The sync system (Layer 4) reads and clears this.
     */
    private boolean dirty = false;

    // ═══════════════════════════════════════════════════════════════════
    //  Power management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Grants a power to this player. If the power's progression is disabled,
     * all abilities in the power are also immediately unlocked.
     *
     * @param power the power to grant
     * @return {@code true} if the power was newly granted (not already present)
     */
    public boolean grantPower(Power power) {
        ResourceLocation key = SomniumRegistries.getPowerKey(power);
        if (key == null || !grantedPowers.add(key)) {
            return false;
        }

        // If progression is disabled for this power, unlock all its abilities.
        // If enabled, initialize progress tracking and do retroactive checks.
        if (!power.isProgressionEnabled()) {
            for (AbilityType abilityType : power.getAbilityTypes()) {
                unlockAbility(abilityType, power);
            }
        } else {
            ProgressionHandler.initializeProgressForPower(this, power);
        }

        markDirty();
        return true;
    }

    /**
     * Revokes a power from this player. Also removes any abilities from
     * this power that aren't also in another granted power, and clears
     * them from the bar.
     *
     * @param power the power to revoke
     * @return {@code true} if the power was removed (was present)
     */
    public boolean revokePower(Power power) {
        ResourceLocation key = SomniumRegistries.getPowerKey(power);
        if (key == null || !grantedPowers.remove(key)) {
            return false;
        }

        // Remove abilities that only belonged to this power
        for (AbilityType abilityType : power.getAbilityTypes()) {
            if (!isAbilityInAnyGrantedPower(abilityType)) {
                lockAbility(abilityType);
            }
        }

        // Clean up progression data for this power
        ProgressionHandler.removeProgressForPower(this, power);

        markDirty();
        return true;
    }

    /**
     * @param power the power to check
     * @return {@code true} if this player has been granted this power
     */
    public boolean hasPower(Power power) {
        ResourceLocation key = SomniumRegistries.getPowerKey(power);
        return key != null && grantedPowers.contains(key);
    }

    /**
     * @return an unmodifiable view of the granted power registry names
     */
    public Set<ResourceLocation> getGrantedPowerKeys() {
        return Collections.unmodifiableSet(grantedPowers);
    }

    /**
     * Resolves the granted power keys to Power objects via the registry.
     *
     * @return a list of granted Powers (excludes any with missing registry entries)
     */
    public List<Power> getGrantedPowers() {
        List<Power> powers = new ArrayList<>();
        for (ResourceLocation key : grantedPowers) {
            Power power = SomniumRegistries.getPowerValue(key);
            if (power != null) {
                powers.add(power);
            }
        }
        return powers;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ability unlock / lock
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Unlocks an ability for this player, creating an AbilityInstance for it.
     * For passive abilities, the initial enabled state is determined by the
     * power's configuration (or the ability's default if no override exists).
     *
     * @param abilityType the ability to unlock
     * @param fromPower   the power context (used for passive default overrides),
     *                    or null to use the ability's own defaults
     * @return {@code true} if the ability was newly unlocked
     */
    public boolean unlockAbility(AbilityType abilityType, @Nullable Power fromPower) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        if (key == null || unlockedAbilities.contains(key)) {
            return false;
        }

        unlockedAbilities.add(key);

        // Create the per-player instance
        AbilityInstance instance = abilityType.createInstance();
        abilityInventory.put(key, instance);

        // Set up passive state if this is a passive ability
        if (abilityType.getActivationType() == ActivationType.PASSIVE) {
            boolean defaultEnabled = abilityType.isDefaultEnabled();
            if (fromPower != null) {
                Power.PowerAbilityEntry entry = fromPower.getEntry(abilityType);
                if (entry != null) {
                    defaultEnabled = entry.getEffectiveDefaultEnabled();
                }
            }
            passiveStates.put(key, defaultEnabled);
        }

        markDirty();
        return true;
    }

    /**
     * Convenience overload — unlocks without a power context.
     */
    public boolean unlockAbility(AbilityType abilityType) {
        return unlockAbility(abilityType, null);
    }

    /**
     * Locks (revokes) an ability, removing it from the inventory, bar,
     * and passive states.
     *
     * @param abilityType the ability to lock
     * @return {@code true} if the ability was locked (was unlocked)
     */
    public boolean lockAbility(AbilityType abilityType) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        if (key == null || !unlockedAbilities.remove(key)) {
            return false;
        }

        abilityInventory.remove(key);
        passiveStates.remove(key);

        // Clear from bar if equipped (check all pages)
        for (int i = 0; i < MAX_PAGES * BAR_SIZE; i++) {
            if (key.equals(abilityBar[i])) {
                abilityBar[i] = null;
            }
        }

        // Clear active transformation if it was this ability
        if (key.equals(activeTransformation)) {
            activeTransformation = null;
        }

        markDirty();
        return true;
    }

    /**
     * @param abilityType the ability to check
     * @return {@code true} if this ability is unlocked for this player
     */
    public boolean isAbilityUnlocked(AbilityType abilityType) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        return key != null && unlockedAbilities.contains(key);
    }

    /**
     * @return an unmodifiable view of the unlocked ability registry names
     */
    public Set<ResourceLocation> getUnlockedAbilityKeys() {
        return Collections.unmodifiableSet(unlockedAbilities);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ability inventory access
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the AbilityInstance for a specific ability on this player.
     *
     * @param abilityType the ability type
     * @return the instance, or null if the ability isn't unlocked
     */
    @Nullable
    public AbilityInstance getAbilityInstance(AbilityType abilityType) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        return key != null ? abilityInventory.get(key) : null;
    }

    /**
     * Gets the AbilityInstance for a specific ability by registry name.
     *
     * @param abilityKey the ability's registry name
     * @return the instance, or null if not found
     */
    @Nullable
    public AbilityInstance getAbilityInstance(ResourceLocation abilityKey) {
        return abilityInventory.get(abilityKey);
    }

    /**
     * @return an unmodifiable view of all ability instances this player has
     */
    public Map<ResourceLocation, AbilityInstance> getAbilityInventory() {
        return Collections.unmodifiableMap(abilityInventory);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ability bar management
    // ═══════════════════════════════════════════════════════════════════

    /** @return the currently active bar page (0 to MAX_PAGES-1) */
    public int getActivePage() { return activePage; }

    /**
     * Sets the active bar page.
     * @param page the page index (0 to MAX_PAGES-1)
     */
    public void setActivePage(int page) {
        if (page >= 0 && page < MAX_PAGES) {
            this.activePage = page;
            markDirty();
        }
    }

    /** Converts page + slot to flat array index. */
    private int barIndex(int page, int slot) {
        return page * BAR_SIZE + slot;
    }

    /**
     * Equips an ability to a bar slot on a specific page.
     *
     * @param page        the page index (0 to MAX_PAGES-1)
     * @param slot        the slot index (0 to BAR_SIZE-1)
     * @param abilityType the ability to equip, or null to clear
     * @return {@code true} if the operation succeeded
     */
    public boolean setBarSlot(int page, int slot, @Nullable AbilityType abilityType) {
        if (page < 0 || page >= MAX_PAGES || slot < 0 || slot >= BAR_SIZE) return false;
        int index = barIndex(page, slot);

        if (abilityType == null) {
            abilityBar[index] = null;
            markDirty();
            return true;
        }

        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        if (key == null || !unlockedAbilities.contains(key)) return false;
        if (!abilityType.isBarEquippable()) return false;

        abilityBar[index] = key;
        markDirty();
        return true;
    }

    /**
     * Equips an ability to a slot on the ACTIVE page.
     * Backward-compatible with code that doesn't know about pages.
     */
    public boolean setBarSlot(int slot, @Nullable AbilityType abilityType) {
        return setBarSlot(activePage, slot, abilityType);
    }

    /**
     * Gets the AbilityInstance in a bar slot on a specific page.
     */
    @Nullable
    public AbilityInstance getBarSlotInstance(int page, int slot) {
        if (page < 0 || page >= MAX_PAGES || slot < 0 || slot >= BAR_SIZE) return null;
        ResourceLocation key = abilityBar[barIndex(page, slot)];
        return key != null ? abilityInventory.get(key) : null;
    }

    /** Gets the AbilityInstance in a slot on the ACTIVE page. */
    @Nullable
    public AbilityInstance getBarSlotInstance(int slot) {
        return getBarSlotInstance(activePage, slot);
    }

    /** Gets the ability key in a slot on a specific page. */
    @Nullable
    public ResourceLocation getBarSlotKey(int page, int slot) {
        if (page < 0 || page >= MAX_PAGES || slot < 0 || slot >= BAR_SIZE) return null;
        return abilityBar[barIndex(page, slot)];
    }

    /** Gets the ability key in a slot on the ACTIVE page. */
    @Nullable
    public ResourceLocation getBarSlotKey(int slot) {
        return getBarSlotKey(activePage, slot);
    }

    /** Clears all bar slots on all pages. */
    public void clearBar() {
        Arrays.fill(abilityBar, null);
        markDirty();
    }

    /**
     * Finds which bar slot (on the active page) an ability occupies.
     * @return the slot index (0 to BAR_SIZE-1), or -1 if not on the active page
     */
    public int findBarSlot(AbilityType abilityType) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        if (key == null) return -1;
        for (int i = 0; i < BAR_SIZE; i++) {
            if (key.equals(abilityBar[barIndex(activePage, i)])) return i;
        }
        return -1;
    }

    /**
     * Checks if an ability is on any bar slot across all pages.
     *
     * @param key the registry key of the ability
     * @return {@code true} if the ability is equipped on any page
     */
    public boolean isAbilityOnAnyBar(@Nullable ResourceLocation key) {
        if (key == null) return false;
        for (int i = 0; i < abilityBar.length; i++) {
            if (key.equals(abilityBar[i])) return true;
        }
        return false;
    }

    /**
     * Checks if an ability is on any bar slot on a specific page.
     *
     * @param page the page to check
     * @param key  the registry key of the ability
     * @return {@code true} if the ability is on that page's bar
     */
    public boolean isAbilityOnPage(int page, @Nullable ResourceLocation key) {
        if (key == null || page < 0 || page >= MAX_PAGES) return false;
        for (int slot = 0; slot < BAR_SIZE; slot++) {
            if (key.equals(abilityBar[barIndex(page, slot)])) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Passive ability states
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the enabled state of a passive ability.
     *
     * @param abilityType the passive ability
     * @param enabled     true to enable, false to disable
     * @return {@code true} if the state was changed
     */
    public boolean setPassiveEnabled(AbilityType abilityType, boolean enabled) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        if (key == null || !passiveStates.containsKey(key)) return false;

        Boolean previous = passiveStates.put(key, enabled);
        if (previous != null && previous == enabled) return false;

        markDirty();
        return true;
    }

    /**
     * @param abilityType the passive ability to check
     * @return true if the passive is enabled, false if disabled or not found
     */
    public boolean isPassiveEnabled(AbilityType abilityType) {
        ResourceLocation key = SomniumRegistries.getAbilityKey(abilityType);
        return key != null && passiveStates.getOrDefault(key, false);
    }

    /**
     * @return an unmodifiable view of passive states (ability key → enabled)
     */
    public Map<ResourceLocation, Boolean> getPassiveStates() {
        return Collections.unmodifiableMap(passiveStates);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Transformation tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the currently active transformation.
     *
     * @param transformationKey the registry name of the active transformation,
     *                          or null to clear
     */
    public void setActiveTransformation(@Nullable ResourceLocation transformationKey) {
        this.activeTransformation = transformationKey;
        markDirty();
    }

    /**
     * @return the registry name of the active transformation, or null if none
     */
    @Nullable
    public ResourceLocation getActiveTransformation() {
        return activeTransformation;
    }

    /**
     * @return {@code true} if the player currently has an active transformation
     */
    public boolean hasActiveTransformation() {
        return activeTransformation != null;
    }

    /**
     * Gets the TransformationInstance for the currently active transformation.
     *
     * @return the transformation instance, or null if no transformation is active
     *         or the instance isn't a TransformationInstance
     */
    @Nullable
    public TransformationInstance getActiveTransformationInstance() {
        if (activeTransformation == null) return null;
        AbilityInstance instance = abilityInventory.get(activeTransformation);
        return instance instanceof TransformationInstance ti ? ti : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Unlock progress tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the progress data for a specific ability's unlock condition.
     *
     * @param progressKey the composite key (format: "powerKey|abilityKey"),
     *                    generated by {@link ProgressionHandler#makeProgressKey}
     * @return the progress CompoundTag, or null if no progress is tracked
     */
    @Nullable
    public CompoundTag getUnlockProgress(String progressKey) {
        return unlockProgress.get(progressKey);
    }

    /**
     * Sets the progress data for a specific ability's unlock condition.
     *
     * @param progressKey the composite key
     * @param progress    the progress data tag
     */
    public void setUnlockProgress(String progressKey, CompoundTag progress) {
        unlockProgress.put(progressKey, progress);
        markDirty();
    }

    /**
     * Removes the progress data for a specific ability. Called when an
     * ability is unlocked (no longer needs tracking) or when a power
     * is revoked.
     *
     * @param progressKey the composite key
     */
    public void removeUnlockProgress(String progressKey) {
        if (unlockProgress.remove(progressKey) != null) {
            markDirty();
        }
    }

    /**
     * @return an unmodifiable view of all unlock progress entries
     */
    public Map<String, CompoundTag> getAllUnlockProgress() {
        return Collections.unmodifiableMap(unlockProgress);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Dirty flag (for sync system)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Marks the data as dirty, meaning it needs to be synced to the client.
     * Called automatically by all mutating methods.
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * @return {@code true} if the data has changed since the last sync
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Clears the dirty flag. Called by the sync system after successfully
     * sending updates to the client.
     */
    public void clearDirty() {
        this.dirty = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Death handling
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processes death persistence rules. Called when a player dies.
     * Removes non-persistent powers and abilities, and clears
     * non-persistent abilities from the bar.
     */
    public void handleDeath() {
        // Collect powers to revoke
        List<ResourceLocation> powersToRemove = new ArrayList<>();
        for (ResourceLocation powerKey : grantedPowers) {
            Power power = SomniumRegistries.getPowerValue(powerKey);
            if (power != null && !power.isPersistOnDeath()) {
                powersToRemove.add(powerKey);
            }
        }

        // Revoke non-persistent powers (this also handles their abilities)
        for (ResourceLocation powerKey : powersToRemove) {
            Power power = SomniumRegistries.getPowerValue(powerKey);
            if (power != null) {
                revokePower(power);
            }
        }

        // Handle individual non-persistent abilities (from persistent powers)
        List<ResourceLocation> abilitiesToRemove = new ArrayList<>();
        for (ResourceLocation abilityKey : unlockedAbilities) {
            AbilityType type = SomniumRegistries.getAbilityValue(abilityKey);
            if (type != null && !type.isPersistOnDeath()) {
                abilitiesToRemove.add(abilityKey);
            }
        }

        for (ResourceLocation abilityKey : abilitiesToRemove) {
            AbilityType type = SomniumRegistries.getAbilityValue(abilityKey);
            if (type != null) {
                lockAbility(type);
            }
        }

        // Clear active transformation (player always de-transforms on death)
        activeTransformation = null;

        // Reset all cooldowns and active states on surviving abilities
        for (AbilityInstance instance : abilityInventory.values()) {
            instance.clearCooldown();
            instance.setActive(false);
            instance.resetChargeTicks();
            if (instance instanceof TransformationInstance ti) {
                ti.setPhase(TransformationPhase.IDLE);
                ti.resetPhaseTicks();
                ti.resetTotalTransformedTicks();
            }
        }

        markDirty();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Copy (for PlayerEvent.Clone)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Copies all data from another SomniumPlayerData instance.
     * Used during {@code PlayerEvent.Clone} to transfer data to the
     * new player entity after respawn or returning from the End.
     *
     * @param source the data to copy from
     */
    public void copyFrom(SomniumPlayerData source) {
        this.grantedPowers.clear();
        this.grantedPowers.addAll(source.grantedPowers);

        this.unlockedAbilities.clear();
        this.unlockedAbilities.addAll(source.unlockedAbilities);

        this.abilityInventory.clear();
        this.abilityInventory.putAll(source.abilityInventory);

        System.arraycopy(source.abilityBar, 0, this.abilityBar, 0, MAX_PAGES * BAR_SIZE);
        this.activePage = source.activePage;

        this.passiveStates.clear();
        this.passiveStates.putAll(source.passiveStates);

        this.activeTransformation = source.activeTransformation;

        this.unlockProgress.clear();
        for (Map.Entry<String, CompoundTag> entry : source.unlockProgress.entrySet()) {
            this.unlockProgress.put(entry.getKey(), entry.getValue().copy());
        }

        this.dirty = true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if an ability belongs to any of the player's currently
     * granted powers. Used to determine if an ability should be removed
     * when a power is revoked.
     */
    private boolean isAbilityInAnyGrantedPower(AbilityType abilityType) {
        for (ResourceLocation powerKey : grantedPowers) {
            Power power = SomniumRegistries.getPowerValue(powerKey);
            if (power != null && power.containsAbility(abilityType)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  NBT serialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Serializes all player data to NBT for saving to disk.
     *
     * @return the serialized CompoundTag
     */
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        // Granted powers — list of registry name strings
        ListTag powersTag = new ListTag();
        for (ResourceLocation key : grantedPowers) {
            powersTag.add(StringTag.valueOf(key.toString()));
        }
        root.put(TAG_GRANTED_POWERS, powersTag);

        // Unlocked abilities — list of registry name strings
        ListTag unlockedTag = new ListTag();
        for (ResourceLocation key : unlockedAbilities) {
            unlockedTag.add(StringTag.valueOf(key.toString()));
        }
        root.put(TAG_UNLOCKED_ABILITIES, unlockedTag);

        // Ability inventory — list of serialized AbilityInstances
        ListTag inventoryTag = new ListTag();
        for (Map.Entry<ResourceLocation, AbilityInstance> entry : abilityInventory.entrySet()) {
            inventoryTag.add(entry.getValue().serializeNBT(entry.getKey()));
        }
        root.put(TAG_ABILITY_INVENTORY, inventoryTag);

        // Ability bar — list of {Page, Slot, AbilityId} entries across all pages
        ListTag barTag = new ListTag();
        for (int page = 0; page < MAX_PAGES; page++) {
            for (int slot = 0; slot < BAR_SIZE; slot++) {
                int index = barIndex(page, slot);
                if (abilityBar[index] != null) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.putInt(TAG_BAR_PAGE, page);
                    slotTag.putInt(TAG_BAR_SLOT, slot);
                    slotTag.putString(TAG_BAR_ABILITY_ID, abilityBar[index].toString());
                    barTag.add(slotTag);
                }
            }
        }
        root.put(TAG_ABILITY_BAR, barTag);
        root.putInt(TAG_ACTIVE_PAGE, activePage);

        // Passive states — list of {Id, Enabled} entries
        ListTag passivesTag = new ListTag();
        for (Map.Entry<ResourceLocation, Boolean> entry : passiveStates.entrySet()) {
            CompoundTag passiveTag = new CompoundTag();
            passiveTag.putString(TAG_PASSIVE_ID, entry.getKey().toString());
            passiveTag.putBoolean(TAG_PASSIVE_ENABLED, entry.getValue());
            passivesTag.add(passiveTag);
        }
        root.put(TAG_PASSIVE_STATES, passivesTag);

        // Active transformation
        if (activeTransformation != null) {
            root.putString(TAG_ACTIVE_TRANSFORMATION, activeTransformation.toString());
        }

        // Unlock progress — list of {Key, Data} entries
        ListTag progressTag = new ListTag();
        for (Map.Entry<String, CompoundTag> entry : unlockProgress.entrySet()) {
            CompoundTag progressEntry = new CompoundTag();
            progressEntry.putString(TAG_PROGRESS_KEY, entry.getKey());
            progressEntry.put(TAG_PROGRESS_DATA, entry.getValue().copy());
            progressTag.add(progressEntry);
        }
        root.put(TAG_UNLOCK_PROGRESS, progressTag);

        return root;
    }

    /**
     * Deserializes player data from NBT.
     *
     * @param root the CompoundTag to read from
     */
    public void deserializeNBT(CompoundTag root) {
        AbilityInstance.AbilityTypeLookup lookup = SomniumRegistries.abilityLookup();

        // Granted powers
        grantedPowers.clear();
        ListTag powersTag = root.getList(TAG_GRANTED_POWERS, Tag.TAG_STRING);
        for (int i = 0; i < powersTag.size(); i++) {
            grantedPowers.add(new ResourceLocation(powersTag.getString(i)));
        }

        // Unlocked abilities
        unlockedAbilities.clear();
        ListTag unlockedTag = root.getList(TAG_UNLOCKED_ABILITIES, Tag.TAG_STRING);
        for (int i = 0; i < unlockedTag.size(); i++) {
            unlockedAbilities.add(new ResourceLocation(unlockedTag.getString(i)));
        }

        // Ability inventory
        abilityInventory.clear();
        ListTag inventoryTag = root.getList(TAG_ABILITY_INVENTORY, Tag.TAG_COMPOUND);
        for (int i = 0; i < inventoryTag.size(); i++) {
            CompoundTag instanceTag = inventoryTag.getCompound(i);
            AbilityInstance instance = AbilityInstance.deserializeNBT(instanceTag, lookup);
            if (instance != null) {
                ResourceLocation key = SomniumRegistries.getAbilityKey(instance.getAbilityType());
                if (key != null) {
                    abilityInventory.put(key, instance);
                }
            }
        }

        // Ability bar (paged)
        Arrays.fill(abilityBar, null);
        ListTag barTag = root.getList(TAG_ABILITY_BAR, Tag.TAG_COMPOUND);
        for (int i = 0; i < barTag.size(); i++) {
            CompoundTag slotTag = barTag.getCompound(i);
            int page = slotTag.contains(TAG_BAR_PAGE) ? slotTag.getInt(TAG_BAR_PAGE) : 0;
            int slot = slotTag.getInt(TAG_BAR_SLOT);
            if (page >= 0 && page < MAX_PAGES && slot >= 0 && slot < BAR_SIZE) {
                ResourceLocation abilityKey = new ResourceLocation(
                        slotTag.getString(TAG_BAR_ABILITY_ID));
                if (abilityInventory.containsKey(abilityKey)) {
                    abilityBar[barIndex(page, slot)] = abilityKey;
                }
            }
        }
        activePage = root.contains(TAG_ACTIVE_PAGE)
                ? Math.max(0, Math.min(MAX_PAGES - 1, root.getInt(TAG_ACTIVE_PAGE)))
                : 0;

        // Passive states
        passiveStates.clear();
        ListTag passivesTag = root.getList(TAG_PASSIVE_STATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < passivesTag.size(); i++) {
            CompoundTag passiveTag = passivesTag.getCompound(i);
            ResourceLocation id = new ResourceLocation(passiveTag.getString(TAG_PASSIVE_ID));
            boolean enabled = passiveTag.getBoolean(TAG_PASSIVE_ENABLED);
            // Only restore if the ability still exists
            if (unlockedAbilities.contains(id)) {
                passiveStates.put(id, enabled);
            }
        }

        // Active transformation
        if (root.contains(TAG_ACTIVE_TRANSFORMATION)) {
            activeTransformation = new ResourceLocation(
                    root.getString(TAG_ACTIVE_TRANSFORMATION));
            // Validate that the transformation still exists in inventory
            if (!abilityInventory.containsKey(activeTransformation)) {
                activeTransformation = null;
            }
        } else {
            activeTransformation = null;
        }

        // Unlock progress
        unlockProgress.clear();
        if (root.contains(TAG_UNLOCK_PROGRESS)) {
            ListTag progressTag = root.getList(TAG_UNLOCK_PROGRESS, Tag.TAG_COMPOUND);
            for (int i = 0; i < progressTag.size(); i++) {
                CompoundTag progressEntry = progressTag.getCompound(i);
                String key = progressEntry.getString(TAG_PROGRESS_KEY);
                CompoundTag data = progressEntry.getCompound(TAG_PROGRESS_DATA);
                if (!key.isEmpty()) {
                    unlockProgress.put(key, data.copy());
                }
            }
        }
    }
}