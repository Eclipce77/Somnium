package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for {@link SomniumCastModel} instances.
 *
 * <p>Addon developers register their models during client setup. When a
 * {@link net.eclipce.somnium.network.PlayPlayerAnimationPacket} arrives with a matching
 * {@code modelId}, Somnium uses the registered model to drive the animation.</p>
 *
 * <h3>Registration</h3>
 * <pre>{@code
 * // In your mod's client-setup event (FMLClientSetupEvent or ClientSetupEvent):
 * CastAnimationModelRegistry.register(
 *     new ResourceLocation("mymod", "fire_cast"),
 *     new FireCastModel());
 * }</pre>
 *
 * <h3>Usage in an ability</h3>
 * <pre>{@code
 * public class FireballAbility extends AbilityType {
 *     public FireballAbility() {
 *         super(new Properties()
 *             .activationType(ActivationType.INSTANT)
 *             .staminaCost(15f)
 *             // animation name (in your .animation.json) + model registry key:
 *             .castAnimation("animation.mymod.fire_cast",
 *                            new ResourceLocation("mymod", "fire_cast")));
 *     }
 * }
 * }</pre>
 *
 * <p>Register all models on the <strong>client side only</strong> —
 * this class must never be accessed on a dedicated server.</p>
 */
public final class CastAnimationModelRegistry {

    private static final Map<ResourceLocation, SomniumCastModel> REGISTRY = new HashMap<>();

    /**
     * Registers a model under the given key.
     *
     * @param id    unique key matching the one used in
     *              {@link net.eclipce.somnium.core.ability.AbilityType.Properties#castAnimation(String, ResourceLocation)}
     * @param model the model instance to use for this key
     * @throws IllegalStateException if a model is already registered under {@code id}
     */
    public static void register(ResourceLocation id, SomniumCastModel model) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException(
                    "Somnium CastAnimationModelRegistry: duplicate model id '" + id + "'");
        }
        REGISTRY.put(id, model);
    }

    /**
     * Retrieves the model for the given key, or {@code null} if none is registered.
     *
     * @param id the registry key, matching the {@code modelId} in the animation packet
     */
    @Nullable
    public static SomniumCastModel get(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return REGISTRY.get(new ResourceLocation(id));
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns {@code true} if any model is registered under {@code id}. */
    public static boolean has(ResourceLocation id) {
        return REGISTRY.containsKey(id);
    }

    private CastAnimationModelRegistry() {}
}