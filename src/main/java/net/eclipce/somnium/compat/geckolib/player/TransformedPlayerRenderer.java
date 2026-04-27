package net.eclipce.somnium.compat.geckolib.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.somnium.core.ability.transformation.TransformationData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

import javax.annotation.Nullable;

/**
 * Renders a transformed player using a GeckoLib model.
 *
 * <p>Extends {@link GeoObjectRenderer} (for non-entity objects) to render
 * the transformation model at the player's position. Called from the
 * {@link TransformationRenderHandler} when a player has an active
 * transformation with a GeckoLib model.</p>
 *
 * <p>The model, texture, and animations are resolved dynamically from
 * the active {@link TransformationData} via {@link TransformedPlayerModel}.</p>
 */
public class TransformedPlayerRenderer extends GeoObjectRenderer<TransformedPlayerAnimatable> {

    public TransformedPlayerRenderer() {
        super(new TransformedPlayerModel());
    }

    /**
     * Renders the transformed player model.
     *
     * @param poseStack    the pose stack for positioning
     * @param animatable   the player's animatable wrapper
     * @param bufferSource the buffer source for rendering
     * @param packedLight  the packed light level
     * @param partialTick  partial tick for interpolation
     */
    public void renderTransformed(PoseStack poseStack,
                                   TransformedPlayerAnimatable animatable,
                                   MultiBufferSource bufferSource,
                                   int packedLight,
                                   float partialTick) {
        TransformationData data = animatable.getCurrentData();
        if (data == null || !data.hasModel()) return;

        float scale = data.getModelScale();

        poseStack.pushPose();

        // Apply transformation scale
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale);
        }

        // Render the GeckoLib model
        this.defaultRender(poseStack, animatable, bufferSource, null,
                null, 0, partialTick, packedLight);

        poseStack.popPose();
    }

    @Override
    public RenderType getRenderType(TransformedPlayerAnimatable animatable,
                                     ResourceLocation texture,
                                     @Nullable MultiBufferSource bufferSource,
                                     float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
