package net.eclipce.somnium.compat.geckolib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eclipce.somnium.compat.geckolib.GeckoLibCompat;
import net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumBoneScaleMap;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two responsibilities, both honouring options on {@link CastAnimationOptions}:
 *
 * <ol>
 *   <li><b>{@code heldItemsShown=false}:</b> cancels the entire held-item render so the
 *       item disappears for the duration of the cast animation, in both third-person
 *       and the {@code SomniumFirstPersonRenderer} re-render path (which calls
 *       {@code PlayerRenderer.render} and therefore invokes this layer).</li>
 *   <li><b>Scaled arms:</b> when a cast animation scales the arm bone (e.g. Gomu Pistol's
 *       1.64× Y-scale), the held item drifts because vanilla's hand offset is hardcoded
 *       to the unscaled arm length. We add an extra translate in the arm-local Y axis
 *       so the item tracks the visual tip of the scaled arm — without scaling the item
 *       itself.</li>
 * </ol>
 *
 * <h3>Why these two live together</h3>
 * <p>Both checks need the same upstream guards (entity is a Player, cast animation is
 * active, etc.) and both target {@code ItemInHandLayer}'s render path. Splitting them
 * across two mixin classes would require those guards twice. Keeping them together is
 * the simpler shape.</p>
 *
 * <h3>What this hides for held items</h3>
 * <p>{@link ItemInHandLayer} draws whatever the entity is holding attached to its hand
 * bone in the <em>third-person world render</em>. The vanilla first-person path uses a
 * separate {@code ItemInHandRenderer.renderHandsWithItems} call, which is suppressed
 * elsewhere by {@code SomniumFirstPersonRenderer} via {@code RenderHandEvent}.</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    /**
     * Cancel the layer entirely when {@code heldItemsShown=false}. Runs at HEAD so we
     * skip all of vanilla's work (translateToHand, rotations, item lookup, draw).
     */
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void somnium$skipItemWhenHidden(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {

        if (!GeckoLibCompat.isLoaded()) return;
        if (!(entity instanceof Player player)) return;

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());
        if (animatable == null) return;
        if (animatable.getActiveAnimation() == null) return;

        if (!animatable.getCurrentOptions().heldItemsShown()) {
            ci.cancel();
        }
    }

    /**
     * After vanilla's {@code translateToHand} positions the PoseStack at the arm's
     * pivot+rotation but <em>before</em> the subsequent rotations and hand-offset
     * translate, slide the PoseStack along the arm's local Y axis by the additional
     * distance to the scaled-arm tip.
     *
     * <h3>The geometry</h3>
     * <p>Vanilla's {@code translate(±0.0625, 0.125, -0.625)} after the {@code -90° X /
     * 180° Y} rotations is putting the item ten voxels down the arm from the pivot — the
     * unscaled hand position. Eleven-ish voxels = 0.625 blocks ÷ 16 × 16 = 10 voxels.
     * When we scale the arm Y by {@code s}, the visual hand is at {@code 10 * s} voxels
     * instead. The simplest delta-correct is a translate of {@code (s - 1) * 0.625} in
     * the arm-local +Y direction — applied <em>before</em> the X/Y rotations, while the
     * PoseStack's +Y axis still aligns with the arm's extension direction. After this,
     * vanilla's rotations and hand-offset translate land the item at the right spot
     * automatically, without scaling the item itself.</p>
     *
     * <p>For unscaled arms ({@code s == 1}) the delta is zero and this is a no-op.</p>
     *
     * <h3>Why the {@code ArmedModel} target descriptor</h3>
     * <p>{@code ItemInHandLayer<T, M extends EntityModel<T> & ArmedModel>} calls
     * {@code this.getParentModel().translateToHand(...)}, which the compiler emits as
     * an {@code INVOKEINTERFACE} on {@code ArmedModel} (the bound that declares the
     * method), not on {@code HumanoidModel}. Mixin matches the descriptor literally
     * against the bytecode, so this owner must be {@code ArmedModel} or the
     * {@code @At} target won't resolve.</p>
     */
    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void somnium$adjustForArmScale(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            CallbackInfo ci) {

        if (!GeckoLibCompat.isLoaded()) return;
        if (!(entity instanceof Player player)) return;

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());
        if (animatable == null) return;
        if (animatable.getActiveAnimation() == null) return;

        ItemInHandLayer<?, ?> self = (ItemInHandLayer<?, ?>) (Object) this;
        if (!(self.getParentModel() instanceof HumanoidModel<?> humanModel)) return;

        ModelPart armPart = (arm == HumanoidArm.LEFT) ? humanModel.leftArm : humanModel.rightArm;
        float[] scale = SomniumBoneScaleMap.getScale(armPart);
        if (scale == null) return;

        float scaleY = scale[1];
        if (scaleY == 1f) return;

        // Magic number 0.625 = 10/16 = vanilla's hand offset distance from arm pivot
        // (matches the -0.625 in the immediately-following translate call). Anything
        // less than 1 contracts toward the pivot; anything greater extends past the
        // unscaled hand position to where the visually-scaled arm tip is.
        float additionalY = (scaleY - 1f) * 0.625f;
        poseStack.translate(0f, additionalY, 0f);
    }
}