package net.eclipce.somnium.core.registry;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.power.Power;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.tags.ITag;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Central registry hub for the Somnium API. Creates and exposes the custom
 * Forge registries for {@link AbilityType} and {@link Power}.
 *
 * <h3>How Somnium's registries work</h3>
 * <p>Somnium creates two custom Forge registries, one for ability types and
 * one for powers. These work exactly like Minecraft's built-in registries
 * (items, blocks, etc.) — each entry gets a unique {@link ResourceLocation}
 * name, and the registry maps names to singleton objects.</p>
 *
 * <p>The registries are created via {@link NewRegistryEvent} during mod loading.
 * Addon mods create their own {@code DeferredRegister} instances pointing to
 * the same registry names to register their own abilities and powers.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <p>In your mod's init class:</p>
 * <pre>{@code
 * public class ModAbilities {
 *     // Create a DeferredRegister referencing Somnium's ability registry
 *     public static final DeferredRegister<AbilityType> ABILITIES =
 *         DeferredRegister.create(SomniumRegistries.ABILITY_TYPES_NAME, "your_mod_id");
 *
 *     // Register your abilities
 *     public static final RegistryObject<AbilityType> FIRE_BLAST =
 *         ABILITIES.register("fire_blast", () -> new FireBlastAbility());
 *
 *     // Don't forget to call ABILITIES.register(modEventBus) in your mod constructor!
 * }
 * }</pre>
 *
 * <p>For powers:</p>
 * <pre>{@code
 * public class ModPowers {
 *     public static final DeferredRegister<Power> POWERS =
 *         DeferredRegister.create(SomniumRegistries.POWERS_NAME, "your_mod_id");
 *
 *     public static final RegistryObject<Power> PYROMANCY =
 *         POWERS.register("pyromancy", () -> Power.builder()
 *             .displayName(Component.translatable("power.your_mod_id.pyromancy"))
 *             .ability(() -> ModAbilities.FIRE_BLAST.get())
 *             .build()
 *         );
 *
 *     // Don't forget to call POWERS.register(modEventBus) in your mod constructor!
 * }
 * }</pre>
 *
 * @see AbilityType
 * @see Power
 */
public final class SomniumRegistries {

    // ═══════════════════════════════════════════════════════════════════
    //  Registry names — addon mods reference these to create their
    //  own DeferredRegister instances for the same registries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The registry name for ability types: {@code somnium:ability_types}.
     * <p>Addon mods use this when creating their {@link DeferredRegister}:</p>
     * <pre>{@code
     * DeferredRegister.create(SomniumRegistries.ABILITY_TYPES_NAME, "your_mod_id");
     * }</pre>
     */
    public static final ResourceLocation ABILITY_TYPES_NAME =
            new ResourceLocation(Somnium.MOD_ID, "ability_types");

    /**
     * The registry name for powers: {@code somnium:powers}.
     * <p>Addon mods use this when creating their {@link DeferredRegister}:</p>
     * <pre>{@code
     * DeferredRegister.create(SomniumRegistries.POWERS_NAME, "your_mod_id");
     * }</pre>
     */
    public static final ResourceLocation POWERS_NAME =
            new ResourceLocation(Somnium.MOD_ID, "powers");

    // ═══════════════════════════════════════════════════════════════════
    //  DeferredRegisters — addon mods use these registry names to
    //  create their own DeferredRegisters. Somnium's own DeferredRegisters
    //  are used to register Somnium's built-in content (if any).
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Somnium's internal DeferredRegister for AbilityType.
     * <p><strong>Addon mods should NOT reference this.</strong> Create your own
     * {@code DeferredRegister} using {@link #ABILITY_TYPES_NAME}.</p>
     */
    public static final DeferredRegister<AbilityType> ABILITY_TYPES =
            DeferredRegister.create(ABILITY_TYPES_NAME, Somnium.MOD_ID);

    /**
     * Somnium's internal DeferredRegister for Power.
     * <p><strong>Addon mods should NOT reference this.</strong> Create your own
     * {@code DeferredRegister} using {@link #POWERS_NAME}.</p>
     */
    public static final DeferredRegister<Power> POWERS =
            DeferredRegister.create(POWERS_NAME, Somnium.MOD_ID);

    // ═══════════════════════════════════════════════════════════════════
    //  Registry suppliers — populated during NewRegistryEvent
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Supplier for the ability type registry. Populated during
     * {@link NewRegistryEvent}. Returns {@code null} before the event fires.
     *
     * <p>Tags are enabled on this registry, allowing abilities to be grouped
     * into data-driven tags (e.g., conflict categories like
     * {@code somnium:transformation}).</p>
     */
    private static Supplier<IForgeRegistry<AbilityType>> abilityTypeRegistrySupplier;

    /**
     * Supplier for the power registry. Populated during
     * {@link NewRegistryEvent}. Returns {@code null} before the event fires.
     */
    private static Supplier<IForgeRegistry<Power>> powerRegistrySupplier;

    // ═══════════════════════════════════════════════════════════════════
    //  Registry creation — called from Somnium main class
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates the custom registries during {@link NewRegistryEvent}.
     * Called from the Somnium main class event handler.
     *
     * <p>This uses {@code NewRegistryEvent#create} with a {@link RegistryBuilder}
     * to create the actual {@link IForgeRegistry} instances. The returned
     * suppliers become valid after the event finishes processing.</p>
     *
     * @param event the new registry event
     */
    public static void onNewRegistry(NewRegistryEvent event) {
        // Create the ability type registry with tag support
        abilityTypeRegistrySupplier = event.create(
                new RegistryBuilder<AbilityType>()
                        .setName(ABILITY_TYPES_NAME)
                        .hasTags()
        );

        // Create the power registry
        powerRegistrySupplier = event.create(
                new RegistryBuilder<Power>()
                        .setName(POWERS_NAME)
        );

        Somnium.LOGGER.debug("Somnium registries created");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Registry access
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the ability type registry.
     *
     * @return the registry, or {@code null} if called before
     *         {@link NewRegistryEvent} has finished
     */
    @Nullable
    public static IForgeRegistry<AbilityType> getAbilityTypeRegistry() {
        return abilityTypeRegistrySupplier != null ? abilityTypeRegistrySupplier.get() : null;
    }

    /**
     * Gets the power registry.
     *
     * @return the registry, or {@code null} if called before
     *         {@link NewRegistryEvent} has finished
     */
    @Nullable
    public static IForgeRegistry<Power> getPowerRegistry() {
        return powerRegistrySupplier != null ? powerRegistrySupplier.get() : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility methods — name/value lookups
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the registry name (ResourceLocation) of an AbilityType.
     * This is how you find out "what is this ability called" after registration.
     *
     * @param abilityType the ability type to look up
     * @return the registry name, or {@code null} if not registered
     */
    @Nullable
    public static ResourceLocation getAbilityKey(AbilityType abilityType) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        if (registry == null) return null;
        return registry.getKey(abilityType);
    }

    /**
     * Gets an AbilityType by its registry name.
     *
     * @param key the registry name to look up (e.g., {@code "mymod:fire_blast"})
     * @return the ability type, or {@code null} if not found
     */
    @Nullable
    public static AbilityType getAbilityValue(ResourceLocation key) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        if (registry == null) return null;
        return registry.getValue(key);
    }

    /**
     * Gets the registry name of a Power.
     *
     * @param power the power to look up
     * @return the registry name, or {@code null} if not registered
     */
    @Nullable
    public static ResourceLocation getPowerKey(Power power) {
        IForgeRegistry<Power> registry = getPowerRegistry();
        if (registry == null) return null;
        return registry.getKey(power);
    }

    /**
     * Gets a Power by its registry name.
     *
     * @param key the registry name to look up (e.g., {@code "mymod:pyromancy"})
     * @return the power, or {@code null} if not found
     */
    @Nullable
    public static Power getPowerValue(ResourceLocation key) {
        IForgeRegistry<Power> registry = getPowerRegistry();
        if (registry == null) return null;
        return registry.getValue(key);
    }

    /**
     * Checks whether an AbilityType is registered in the registry.
     *
     * @param key the registry name to check
     * @return {@code true} if an ability with this name exists
     */
    public static boolean isAbilityRegistered(ResourceLocation key) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        return registry != null && registry.containsKey(key);
    }

    /**
     * Checks whether a Power is registered in the registry.
     *
     * @param key the registry name to check
     * @return {@code true} if a power with this name exists
     */
    public static boolean isPowerRegistered(ResourceLocation key) {
        IForgeRegistry<Power> registry = getPowerRegistry();
        return registry != null && registry.containsKey(key);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Translation key helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates the standard translation key for an ability type, following
     * the Minecraft convention: {@code "ability.namespace.path"}.
     *
     * <p>Example: an ability registered as {@code mymod:fire_blast} gets
     * the translation key {@code "ability.mymod.fire_blast"}.</p>
     *
     * @param abilityType the ability type
     * @return the translation key, or {@code "ability.somnium.unknown"} if
     *         the ability is not registered
     */
    public static String getAbilityTranslationKey(AbilityType abilityType) {
        ResourceLocation key = getAbilityKey(abilityType);
        if (key == null) return "ability." + Somnium.MOD_ID + ".unknown";
        return "ability." + key.getNamespace() + "." + key.getPath();
    }

    /**
     * Generates the standard translation key for a power:
     * {@code "power.namespace.path"}.
     *
     * @param power the power
     * @return the translation key, or {@code "power.somnium.unknown"} if
     *         the power is not registered
     */
    public static String getPowerTranslationKey(Power power) {
        ResourceLocation key = getPowerKey(power);
        if (key == null) return "power." + Somnium.MOD_ID + ".unknown";
        return "power." + key.getNamespace() + "." + key.getPath();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AbilityTypeLookup implementation — bridges registry to
    //  AbilityInstance deserialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns an {@link AbilityInstance.AbilityTypeLookup} backed by the
     * ability type registry. Used when deserializing AbilityInstances from NBT.
     *
     * <p>This is the bridge between Layer 1 (data model) and Layer 2 (registry).
     * AbilityInstance's {@code deserializeNBT} method needs a way to resolve
     * a ResourceLocation back to an AbilityType, but it doesn't know about
     * the registry directly. This method provides the lookup function.</p>
     *
     * <pre>{@code
     * // Deserializing an AbilityInstance from NBT:
     * AbilityInstance instance = AbilityInstance.deserializeNBT(
     *     tag, SomniumRegistries.abilityLookup()
     * );
     * }</pre>
     *
     * @return a lookup function that resolves ability IDs to types
     */
    public static AbilityInstance.AbilityTypeLookup abilityLookup() {
        return SomniumRegistries::getAbilityValue;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tag query utilities
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if an ability type belongs to a given tag.
     *
     * <p>Tags are loaded from datapack JSON files at:
     * {@code data/<namespace>/tags/somnium/ability_types/<tagpath>.json}</p>
     *
     * @param abilityType the ability to check
     * @param tagKey      the tag to check against
     * @return {@code true} if the ability is in the tag, {@code false} if not
     *         or if the tag system is not yet initialized
     */
    public static boolean isAbilityInTag(AbilityType abilityType, TagKey<AbilityType> tagKey) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        if (registry == null || registry.tags() == null) return false;
        ITag<AbilityType> tag = registry.tags().getTag(tagKey);
        return tag.contains(abilityType);
    }

    /**
     * Gets all abilities that belong to a given tag.
     *
     * @param tagKey the tag to query
     * @return a collection of ability types in the tag, or empty if the tag
     *         doesn't exist or the system isn't initialized
     */
    public static Collection<AbilityType> getAbilitiesInTag(TagKey<AbilityType> tagKey) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        if (registry == null || registry.tags() == null) return Collections.emptyList();
        ITag<AbilityType> tag = registry.tags().getTag(tagKey);
        return tag.stream().toList();
    }

    /**
     * Gets all tags that an ability type belongs to.
     *
     * @param abilityType the ability to query
     * @return a stream of tag keys, or empty if not found
     */
    public static java.util.stream.Stream<TagKey<AbilityType>> getAbilityTags(AbilityType abilityType) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        if (registry == null || registry.tags() == null) {
            return java.util.stream.Stream.empty();
        }
        return registry.tags().getReverseTag(abilityType)
                .map(reverseTag -> reverseTag.getTagKeys())
                .orElse(java.util.stream.Stream.empty());
    }

    /**
     * Checks if two abilities share any tags, indicating a potential conflict.
     *
     * @param a the first ability
     * @param b the second ability
     * @return {@code true} if the abilities share at least one tag
     */
    public static boolean doAbilitiesConflict(AbilityType a, AbilityType b) {
        IForgeRegistry<AbilityType> registry = getAbilityTypeRegistry();
        if (registry == null || registry.tags() == null) return false;

        var reverseTags = registry.tags().getReverseTag(a);
        if (reverseTags.isEmpty()) return false;

        return reverseTags.get().getTagKeys()
                .anyMatch(tagKey -> isAbilityInTag(b, tagKey));
    }

    // Private constructor — this is a utility class
    private SomniumRegistries() {}
}