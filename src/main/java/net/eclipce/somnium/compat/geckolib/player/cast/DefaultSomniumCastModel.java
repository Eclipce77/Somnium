package net.eclipce.somnium.compat.geckolib.player.cast;

import net.eclipce.somnium.Somnium;
import net.minecraft.resources.ResourceLocation;

/**
 * A {@link SomniumCastModel} that uses Somnium's built-in default player rig
 * ({@code assets/somnium/geo/default_player.geo.json}).
 *
 * <p>Extend this class when you want to animate the standard player skeleton.
 * You only need to provide your animation file — the geo model and placeholder
 * texture are handled automatically.</p>
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>Download or copy {@code assets/somnium/geo/default_player.geo.json} and
 *       open it in Blockbench as an existing model.</li>
 *   <li>Create animations on the bones. Export as
 *       {@code assets/yourmod/animations/your_cast.animation.json}.</li>
 *   <li>Extend this class, override {@link #getAnimationResource}, and point it at
 *       your animation file.</li>
 *   <li>Register with {@link CastAnimationModelRegistry}.</li>
 * </ol>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public class PunchCastModel extends DefaultSomniumCastModel {
 *     private static final ResourceLocation ANIM =
 *         new ResourceLocation("mymod", "animations/punch_cast.animation.json");
 *
 *     @Override
 *     public ResourceLocation getAnimationResource(SomniumCastAnimatable animatable) {
 *         return ANIM;
 *     }
 * }
 * }</pre>
 */
public abstract class DefaultSomniumCastModel extends SomniumCastModel {

    private static final ResourceLocation DEFAULT_MODEL =
            new ResourceLocation(Somnium.MOD_ID, "geo/default_player.geo.json");

    /**
     * Placeholder texture — not rendered, but required by GeoModel.
     * Addon devs may override to return a real texture if they also render the model.
     */
    private static final ResourceLocation PLACEHOLDER_TEXTURE =
            new ResourceLocation(Somnium.MOD_ID, "textures/entity/default_player.png");

    @Override
    public ResourceLocation getModelResource(SomniumCastAnimatable animatable) {
        return DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SomniumCastAnimatable animatable) {
        return PLACEHOLDER_TEXTURE;
    }

    // getAnimationResource must be overridden by the addon dev
}