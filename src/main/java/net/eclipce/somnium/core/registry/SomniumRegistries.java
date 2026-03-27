package net.eclipce.somnium.core.registry;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.power.Power;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import javax.annotation.Nullable;
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
 * <p>Somnium itself creates the registries via {@link DeferredRegister} and
 * calls {@link DeferredRegister#makeRegistry(Supplier)} to build them during the
 * {@code NewRegistryEvent}. Addon mods then create their own
 * {@code DeferredRegister} instances pointing to the same registry names
 * to register their own abilities and powers.</p>
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
    //  DeferredRegisters — these create the actual IForgeRegistry
    //  instances during NewRegistryEvent. Only Somnium calls makeRegistry;
    //  addon mods create their own DeferredRegisters referencing the
    //  same registry names.
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Somnium's internal DeferredRegister for AbilityType. Creates the registry.
     * <p><strong>Addon mods should NOT reference this.</strong> Create your own
     * {@code DeferredRegister} using {@link #ABILITY_TYPES_NAME}.</p>
     */
    public static final DeferredRegister<AbilityType> ABILITY_TYPES =
            DeferredRegister.create(ABILITY_TYPES_NAME, Somnium.MOD_ID);

    /**
     * Somnium's internal DeferredRegister for Power. Creates the registry.
     * <p><strong>Addon mods should NOT reference this.</strong> Create your own
     * {@code DeferredRegister} using {@link #POWERS_NAME}.</p>
     */
    public static final DeferredRegister<Power> POWERS =
            DeferredRegister.create(POWERS_NAME, Somnium.MOD_ID);

    // ═══════════════════════════════════════════════════════════════════
    //  Registry suppliers — these become non-null after NewRegistryEvent
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The Forge registry for ability types. Access via {@code .get()} after
     * registries are initialized. Will return {@code null} if called too early
     * (before {@code NewRegistryEvent}).
     */
    public static final Supplier<IForgeRegistry<AbilityType>> ABILITY_TYPE_REGISTRY =
            ABILITY_TYPES.makeRegistry(() -> new RegistryBuilder<AbilityType>()
                    // Sync registry IDs to clients for networking
                    // This ensures ability IDs are consistent across client/server
            );

    /**
     * The Forge registry for powers. Access via {@code .get()} after
     * registries are initialized.
     */
    public static final Supplier<IForgeRegistry<Power>> POWER_REGISTRY =
            POWERS.makeRegistry(() -> new RegistryBuilder<Power>()
                    // Sync registry IDs to clients for networking
            );

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
        IForgeRegistry<AbilityType> registry = ABILITY_TYPE_REGISTRY.get();
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
        IForgeRegistry<AbilityType> registry = ABILITY_TYPE_REGISTRY.get();
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
        IForgeRegistry<Power> registry = POWER_REGISTRY.get();
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
        IForgeRegistry<Power> registry = POWER_REGISTRY.get();
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
        IForgeRegistry<AbilityType> registry = ABILITY_TYPE_REGISTRY.get();
        return registry != null && registry.containsKey(key);
    }

    /**
     * Checks whether a Power is registered in the registry.
     *
     * @param key the registry name to check
     * @return {@code true} if a power with this name exists
     */
    public static boolean isPowerRegistered(ResourceLocation key) {
        IForgeRegistry<Power> registry = POWER_REGISTRY.get();
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

    // Private constructor — this is a utility class
    private SomniumRegistries() {}
}