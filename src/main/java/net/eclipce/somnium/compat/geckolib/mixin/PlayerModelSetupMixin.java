package net.eclipce.somnium.compat.geckolib.mixin;

import net.eclipce.somnium.compat.geckolib.GeckoLibCompat;
import net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions;
import net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastBoneApplicator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link HumanoidModel#setupAnim} at RETURN so that Somnium's
 * GeckoLib cast animation bone transforms are applied immediately after the
 * vanilla model pose has been established.
 *
 * <h3>Why HumanoidModel and not PlayerModel?</h3>
 * <p>{@code setupAnim} is declared in {@code HumanoidModel<T extends LivingEntity>}
 * with erased signature {@code setupAnim(LivingEntity, float, float, float, float, float)}.
 * Targeting the declaring class directly avoids any bridge-method ambiguity that
 * arises from subclass overrides. The injection fires for all HumanoidModel
 * instances; the {@code instanceof Player} guard limits execution to players only.</p>
 *
 * <h3>Why additive?</h3>
 * <p>Vanilla sets each {@code ModelPart}'s rotation fields to match the current
 * locomotion state (arm swing, head look, swimming lean, etc.). By adding our
 * GeckoLib rotation deltas on top with {@code +=}, both animations coexist:
 * a punch animation on {@code right_arm} adds to whatever arm swing vanilla computed,
 * while every other bone remains exactly as vanilla set it.</p>
 */
@Mixin(HumanoidModel.class)
public abstract class PlayerModelSetupMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void somnium$onSetupAnimReturn(
            LivingEntity entity,   // declared erased type in HumanoidModel — matches bytecode exactly
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {

        // Only apply to player entities
        if (!(entity instanceof Player player)) return;
        if (!GeckoLibCompat.isLoaded()) return;

        // Skip entirely if this player has never been involved with the cast system
        // (no animatable instance exists). Otherwise we must call apply() even when no
        // animation is currently active, so it can restore parts that a prior animation
        // hid via CastAnimationOptions#hideBodyPart / #hideLayer.
        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());
        if (animatable == null) {
            // Fast path: also accept a freshly queued animation that hasn't materialised
            // an instance yet (setAnimation() always calls getOrCreate first, so this
            // branch is purely defensive).
            if (!SomniumCastAnimatable.isActive(player.getUUID())) return;
        } else if (animatable.getActiveAnimation() == null
                && animatable.getHiddenBodyParts().isEmpty()
                && animatable.getHiddenLayers().isEmpty()
                && !animatable.needsCleanupPass()
                && !SomniumCastAnimatable.isActive(player.getUUID())) {
            return;
        }

        // Only apply when this HumanoidModel is actually a PlayerModel
        if (!((Object) this instanceof PlayerModel)) return;

        @SuppressWarnings("unchecked")
        PlayerModel<Player> self = (PlayerModel<Player>) (Object) this;

        float partialTick = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        System.out.println("[Somnium-DIAG] PlayerModelSetupMixin: GUARDS PASSED — calling apply for "
                + player.getName().getString() + " uuid=" + player.getUUID()
                + " modelClass=" + self.getClass().getSimpleName());
        SomniumCastBoneApplicator.apply(player, self, partialTick);

        // ── First-person own-player part hiding ──
        // When SomniumFirstPersonRenderer is re-rendering the local player from inside
        // the FP camera, we want only the arms (and their sleeve overlays) plus held
        // items to draw — head/body/legs and their layer overlays would either clip
        // the camera or block view of the animation. This MUST happen here in setupAnim
        // rather than in the FP renderer itself: PlayerRenderer.render's very first call
        // is setModelProperties() which does model.setAllVisible(true), wiping any
        // visibility we set before render. setupAnim runs AFTER setModelProperties, and
        // PlayerModel's post-super copyFrom calls don't touch the visible flag, so a flag
        // set here survives intact through the rest of the render.
        //
        // The user's own hideBodyPart / hideLayer options applied above still take
        // effect — applyHideOptions already ran inside the applicator's apply() call —
        // so any extra parts the addon hid stay hidden. We only force-hide head/body/
        // legs and their overlays; arms/sleeves remain at whatever the user's options
        // (or vanilla setModelProperties) left them.
        if (net.eclipce.somnium.compat.geckolib.player.cast.SomniumFirstPersonRenderer
                .RENDERING_OWN_PLAYER_IN_FIRST_PERSON.get()) {
            self.head.visible       = false;
            self.hat.visible        = false;
            self.body.visible       = false;
            self.jacket.visible     = false;
            self.rightLeg.visible   = false;
            self.leftLeg.visible    = false;
            self.rightPants.visible = false;
            self.leftPants.visible  = false;

            // Show only the arm(s) the animation actually controls, rather than both
            // unconditionally. The suppressVanillaAnimOn set is the "this limb is in use"
            // signal: an animation that owns an arm's pose suppresses vanilla on it. If
            // neither arm is suppressed (e.g. an additive animation that drives arms
            // without suppressing, or DEFAULT options), fall back to showing both so we
            // never hide an arm the animation is in fact moving.
            CastAnimationOptions fpOptions = (animatable != null)
                    ? animatable.getCurrentOptions()
                    : CastAnimationOptions.DEFAULT;
            java.util.Set<CastBodyPart> inUse = fpOptions.suppressVanillaAnimOn();
            boolean showRight = inUse.contains(CastBodyPart.RIGHT_ARM);
            boolean showLeft  = inUse.contains(CastBodyPart.LEFT_ARM);
            if (!showRight && !showLeft) {
                showRight = true;
                showLeft  = true;
            }

            self.rightArm.visible = showRight;
            self.leftArm.visible  = showLeft;

            // Sleeve visibility must respect hideLayer: a sleeve shows only if its arm is
            // shown AND the animation did not request that sleeve hidden. Without the
            // hideLayer check this line was overwriting the visible=false that
            // applyHideOptions set inside apply(), which is exactly why sleeves that hide
            // correctly in third person reappeared in the first-person re-render.
            java.util.Set<net.eclipce.somnium.compat.geckolib.player.cast.CastLayer> hidden =
                    fpOptions.hideLayer();
            boolean hideRightSleeve =
                    hidden.contains(net.eclipce.somnium.compat.geckolib.player.cast.CastLayer.RIGHT_SLEEVE);
            boolean hideLeftSleeve =
                    hidden.contains(net.eclipce.somnium.compat.geckolib.player.cast.CastLayer.LEFT_SLEEVE);
            self.rightSleeve.visible = showRight && !hideRightSleeve;
            self.leftSleeve.visible  = showLeft  && !hideLeftSleeve;
            // Held items render through ItemInHandLayer, which uses its own visibility
            // path (cancellable via heldItemsShown=false in the layer mixin).
        }
    }
}