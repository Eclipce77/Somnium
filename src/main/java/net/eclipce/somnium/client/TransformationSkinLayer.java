package net.eclipce.somnium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationData;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * A render layer that draws the transformation texture over the player's skin.
 *
 * <p>Uses the same approach as Palladium's skin_overlay render layer:
 * renders the existing PlayerModel with a different texture on top.
 * The model is already fully animated by the vanilla renderer — this
 * layer just adds the texture pass.</p>
 *
 * <p>The layer renders at a very slight inflation (0.01) to prevent
 * Z-fighting with the base skin, making the transformation texture
 * fully cover the player's original appearance.</p>
 *
 * <p>Registered via {@code EntityRenderersEvent.AddLayers} on both
 * default and slim player renderers.</p>
 */
public class TransformationSkinLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public TransformationSkinLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        // Check for active transformation with a texture
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null || !data.hasActiveTransformation()) return;

        ResourceLocation activeKey = data.getActiveTransformation();
        if (activeKey == null) return;

        AbilityType type = SomniumRegistries.getAbilityValue(activeKey);
        if (!(type instanceof TransformationAbilityType transType)) return;

        TransformationData transData = transType.getVisualData();
        if (transData == null || transData.getTransformedTexture() == null) return;

        // Get the parent model — already has correct animation pose from vanilla render
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        ResourceLocation texture = transData.getTransformedTexture();

        // Render the model with the transformation texture
        // Slight scale to prevent Z-fighting with the base skin
        poseStack.pushPose();
        poseStack.scale(1.001f, 1.001f, 1.001f);

        VertexConsumer vc = bufferSource.getBuffer(
                RenderType.entitySolid(texture));
        model.renderToBuffer(poseStack, vc, packedLight,
                OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();
    }
}