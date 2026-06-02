package net.eclipce.somnium.compat.geckolib.player.cast;

import software.bernie.geckolib.model.GeoModel;

/**
 * Abstract base class for GeckoLib models used with Somnium's cast animation system.
 *
 * <p>Addon developers extend this class and provide paths to their Blockbench-exported
 * {@code .geo.json} and {@code .animation.json} files. The model geometry is never
 * rendered directly — only its animation data is used. Bone transforms are extracted
 * each frame and applied additively to the vanilla {@code PlayerModel}, so vanilla
 * animations (walking, swimming, etc.), armor layers, and held items all continue
 * to render normally.</p>
 *
 * <h3>Creating an animation</h3>
 * <ol>
 *   <li>In Blockbench, open the Somnium default player template
 *       ({@code assets/somnium/geo/default_player.geo.json}) or use your own player rig.</li>
 *   <li>Create your animation on the existing bones. Only bones you keyframe will affect
 *       the vanilla model — untouched bones contribute zero rotation delta.</li>
 *   <li>Export the animation as a GeckoLib {@code .animation.json}.</li>
 *   <li>Extend this class and point the three resource methods at your files.</li>
 *   <li>Register with {@link CastAnimationModelRegistry}.</li>
 * </ol>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public class FireCastModel extends SomniumCastModel {
 *     private static final ResourceLocation MODEL =
 *         new ResourceLocation("mymod", "geo/player_cast.geo.json");
 *     private static final ResourceLocation ANIMATION =
 *         new ResourceLocation("mymod", "animations/fire_cast.animation.json");
 *     // Texture is not rendered but GeoModel requires a non-null value
 *     private static final ResourceLocation TEXTURE =
 *         new ResourceLocation("mymod", "textures/entity/placeholder.png");
 *
 *     @Override
 *     public ResourceLocation getModelResource(SomniumCastAnimatable a)     { return MODEL; }
 *     @Override
 *     public ResourceLocation getAnimationResource(SomniumCastAnimatable a) { return ANIMATION; }
 *     @Override
 *     public ResourceLocation getTextureResource(SomniumCastAnimatable a)   { return TEXTURE; }
 * }
 *
 * // In your mod's client setup:
 * CastAnimationModelRegistry.register(new ResourceLocation("mymod", "fire_cast"), new FireCastModel());
 * }</pre>
 *
 * <h3>Default player rig</h3>
 * <p>If you want to animate the standard player skeleton without providing your own
 * {@code .geo.json}, extend {@link DefaultSomniumCastModel} instead and only override
 * {@link #getAnimationResource}.</p>
 *
 * <h3>Bone names</h3>
 * <p>The bone names in your {@code .geo.json} must match the following for the transforms
 * to be applied to the correct vanilla model parts:</p>
 * <pre>
 *   head, hat, body, jacket,
 *   right_arm, right_sleeve, left_arm, left_sleeve,
 *   right_leg, right_pants, left_leg, left_pants
 * </pre>
 * <p>Any bone whose name is not in this list is silently ignored.</p>
 */
public abstract class SomniumCastModel extends GeoModel<SomniumCastAnimatable> {
    // All three resource methods are abstract — addon devs must implement them.
}