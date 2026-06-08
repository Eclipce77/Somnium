package net.eclipce.somnium.compat.geckolib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eclipce.somnium.compat.geckolib.GeckoLibCompat;
import net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumBoneScaleMap;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastBoneApplicator;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges the cast animation system into the first-person view via the
 * {@code showInFirstPerson} option on {@link CastAnimationOptions}.
 *
 * <h3>How vanilla first-person hand rendering works</h3>
 * <p>{@link PlayerRenderer#renderRightHand} and {@code renderLeftHand} both delegate to
 * the private {@code renderHand(PoseStack, MultiBufferSource, int, AbstractClientPlayer, ModelPart, ModelPart)}.
 * That method calls {@code playerModel.setupAnim(player, 0, 0, 0, 0, 0)} with all-zero
 * locomotion inputs (the held arm is rendered in screen space, not world space), then
 * explicitly assigns {@code arm.xRot = 0.0F} and {@code sleeve.xRot = 0.0F} before rendering
 * each part. The other rotation channels ({@code yRot}, {@code zRot}), the position fields
 * ({@code x}, {@code y}, {@code z}), and scale (via {@link SomniumBoneScaleMap}) are not
 * touched, so they survive vanilla's reset.</p>
 *
 * <h3>What our setupAnim mixin already did</h3>
 * <p>{@link PlayerModelSetupMixin} fires inside {@code renderHand}'s own
 * {@code setupAnim} call and applies our cast animation to every {@link ModelPart},
 * including {@code arm} and {@code sleeve}. After {@code setupAnim} returns, the arm
 * holds: vanilla locomotion contribution (all zero here) + our animation deltas. Then
 * vanilla immediately wipes {@code arm.xRot} to {@code 0.0F}, throwing away the X-axis
 * component of our animation but leaving the rest intact.</p>
 *
 * <h3>What this mixin does</h3>
 * <p>Inject before each {@code ModelPart#render} call. Depending on the cast options:</p>
 * <ul>
 *   <li><b>{@code showInFirstPerson = true}</b>: re-apply the cast animation's
 *       {@code xRot} so the punch / extend / wind-up motion is visible in first person.
 *       Other channels are already correct from the setupAnim pass.</li>
 *   <li><b>{@code showInFirstPerson = false}</b>: reset the arm and sleeve to their rig
 *       default ({@code ModelPart#resetPose}) and clear any scale we registered, so the
 *       first-person view shows the unmodified vanilla arm even while the third-person
 *       view continues to play the animation. Necessary because our setupAnim mixin
 *       always runs — it doesn't know whether it's being called from the third-person
 *       render path or from {@code renderHand}.</li>
 * </ul>
 *
 * <h3>Limitations (acknowledged by the design)</h3>
 * <p>Only the held arm is visible in first person — there's no equivalent of body,
 * head, legs, or the off-hand arm being shown alongside the camera. The third-person
 * animation may include keyframes for those parts; they are silently dropped in first
 * person. A future "first-person-only animation type" can opt into more nuanced
 * behaviour (e.g. dedicated camera-relative arm choreography).</p>
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererFirstPersonMixin {

    /**
     * Fires twice per {@code renderHand} invocation — once before {@code arm.render(...)},
     * once before {@code sleeve.render(...)}. Both branches below re-apply (or wipe) both
     * parts each time; the operation is idempotent for the first call and corrective for
     * the second (vanilla's intervening {@code sleeve.xRot = 0.0F} would otherwise undo
     * the sleeve re-apply we did the first time).
     */
    @Inject(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"
            )
    )
    private void somnium$handleFirstPerson(PoseStack poseStack,
                                           MultiBufferSource buffer,
                                           int packedLight,
                                           AbstractClientPlayer player,
                                           ModelPart arm,
                                           ModelPart sleeve,
                                           CallbackInfo ci) {
        if (!GeckoLibCompat.isLoaded()) return;

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());
        if (animatable == null) return;
        if (animatable.getActiveAnimation() == null) return;

        CastAnimationOptions options = animatable.getCurrentOptions();
        PlayerRenderer self = (PlayerRenderer) (Object) this;
        PlayerModel<?> model = self.getModel();
        boolean rightHand = (arm == model.rightArm);

        if (options.showInFirstPerson()) {
            // Re-apply animation rotation that vanilla just wiped to 0.
            // yRot, zRot, x, y, z, and scale are already correct from our setupAnim mixin.
            SomniumCastBoneApplicator.reapplyArmRotation(animatable, arm, sleeve, rightHand);
        } else {
            // Hide our animation in first-person. Our setupAnim mixin already wrote our
            // animation transforms onto arm/sleeve; undo all of them by snapping to the
            // rig-default pose, then re-apply vanilla's zero xRot (resetPose may have
            // restored the rig's default xRot, which is fine — but vanilla explicitly
            // wants zero), and drop our scale registration so ModelPartRenderMixin
            // doesn't apply a non-identity scale during render.
            arm.resetPose();
            sleeve.resetPose();
            arm.xRot = 0.0F;
            sleeve.xRot = 0.0F;
            SomniumBoneScaleMap.removeFor(arm);
            SomniumBoneScaleMap.removeFor(sleeve);
        }
    }
}