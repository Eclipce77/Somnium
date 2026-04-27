package net.eclipce.somnium.compat.geckolib.player;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.transformation.TransformationData;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Dynamic GeoModel for transformed players. Resolves model, texture,
 * and animation file paths from the active {@link TransformationData}.
 *
 * <p>When the transformation changes, the resource paths update
 * automatically on the next render frame.</p>
 */
public class TransformedPlayerModel extends GeoModel<TransformedPlayerAnimatable> {

    private static final ResourceLocation FALLBACK_MODEL =
            new ResourceLocation(Somnium.MOD_ID, "geo/transformed_player.geo.json");
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation(Somnium.MOD_ID, "textures/entity/transformed_player.png");
    private static final ResourceLocation FALLBACK_ANIMATION =
            new ResourceLocation(Somnium.MOD_ID, "animations/transformed_player.animation.json");

    @Override
    public ResourceLocation getModelResource(TransformedPlayerAnimatable animatable) {
        TransformationData data = animatable.getCurrentData();
        if (data != null && data.getTransformedModel() != null) {
            return data.getTransformedModel();
        }
        return FALLBACK_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TransformedPlayerAnimatable animatable) {
        TransformationData data = animatable.getCurrentData();
        if (data != null && data.getTransformedTexture() != null) {
            return data.getTransformedTexture();
        }
        return FALLBACK_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TransformedPlayerAnimatable animatable) {
        TransformationData data = animatable.getCurrentData();
        // Use the idle animation file as the base animation resource
        if (data != null && data.getIdleAnimation() != null) {
            return data.getIdleAnimation();
        }
        return FALLBACK_ANIMATION;
    }
}
