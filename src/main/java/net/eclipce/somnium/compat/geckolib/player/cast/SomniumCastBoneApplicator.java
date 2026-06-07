package net.eclipce.somnium.compat.geckolib.player.cast;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin;

import java.util.Optional;

/**
 * Core of Somnium's per-bone player animation system.
 *
 * <p>Called once per render frame from {@link PlayerModelSetupMixin} immediately after
 * {@code PlayerModel.setupAnim()} completes. If the player has an active cast animation,
 * this class:</p>
 * <ol>
 *   <li>Consumes any newly queued animation from {@link SomniumCastAnimatable}.</li>
 *   <li>Resolves the registered {@link SomniumCastModel} for that animation.</li>
 *   <li>Runs the GeckoLib animation processor for the current frame (without rendering).</li>
 *   <li>Walks each bone in the baked model and adds its rotation/position deltas onto
 *       the corresponding vanilla {@link ModelPart} in the {@link PlayerModel}.</li>
 * </ol>
 *
 * <p>Because transforms are applied <em>additively</em> (using {@code +=}), vanilla
 * locomotion animations (walking, swimming, sneaking, etc.) continue running on all
 * bones. Only keyframed bones in the cast animation are affected — a punch animation
 * that keys only {@code right_arm} will not disturb the legs, head, or any other part.</p>
 *
 * <p>Both skin layers are preserved because this modifies the already-set-up vanilla
 * model directly — armor layers and the overlay skin (jacket, sleeves, pants, hat) are
 * all rendered through the normal pipeline after this method returns.</p>
 */
public final class SomniumCastBoneApplicator {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Probe state (diagnostic only) ──
    private static String probedAnim = null;
    private static int frameCountForCurrentAnim = 0;

    /**
     * Entry point called by {@link PlayerModelSetupMixin}.
     * No-ops gracefully if the player has no active animation or model.
     *
     * @param player      the player being rendered (may be the local player or a remote player)
     * @param playerModel the vanilla model whose bones will be adjusted
     * @param partialTick render partial tick from the event
     */
    public static void apply(Player player, PlayerModel<?> playerModel, float partialTick) {
        // Clear scale data from the previous rendered player before writing new data.
        // This must happen unconditionally — even if there is no active animation —
        // so that scale data never carries over across players in the same frame.
        SomniumBoneScaleMap.clearAll();

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrCreate(player.getUUID());

        // Consume any packet-queued animation before checking isActive
        animatable.consumeQueue();

        if (animatable.getActiveAnimation() == null) {
            return;
        }

        // Reset frame counter when starting a new animation
        if (!animatable.getActiveAnimation().equals(probedAnim)) {
            frameCountForCurrentAnim = 0;
        }

        // Resolve the model registered for this animation's modelId
        SomniumCastModel model = CastAnimationModelRegistry.get(animatable.getActiveModelId());
        if (model == null) {
            LOGGER.warn("[Somnium] CastAnimation: no model registered for id '{}'.",
                    animatable.getActiveModelId());
            animatable.onAnimationFinished();
            return;
        }

        // ── Load the baked model FIRST ──
        // GeckoLib's GeoModel#getBakedModel populates the AnimationProcessor's bone registry
        // as a side-effect (setActiveModel). If this is skipped, setCustomAnimations silently
        // no-ops because tickAnimation gates on a non-empty bone set — no exception, no log,
        // no animation. Every standard GeckoLib renderer calls getBakedModel before
        // setCustomAnimations for this reason.
        BakedGeoModel bakedModel;
        try {
            net.minecraft.resources.ResourceLocation modelRes = model.getModelResource(animatable);
            bakedModel = model.getBakedModel(modelRes);
        } catch (Exception e) {
            System.out.println("[Somnium-DIAG] apply: getBakedModel THREW " + e);
            LOGGER.error("[Somnium] CastAnimation: getBakedModel threw for resource '{}'.",
                    model.getModelResource(animatable));
            LOGGER.error("[Somnium] CastAnimation: exception detail", e);
            return;
        }

        // ── First-frame announcement (diagnostic — will be removed once stable) ──
        if (!animatable.getActiveAnimation().equals(probedAnim)) {
            probedAnim = animatable.getActiveAnimation();
            System.out.println("[Somnium-DIAG] starting animation '" + animatable.getActiveAnimation()
                    + "' on model '" + animatable.getActiveModelId() + "' for " + player.getName().getString());
        }

        // ── Drive the animation processor manually ──
        //
        // Why not use model.setCustomAnimations(...)?
        //
        // In GeckoLib 4.8, GeoModel#setCustomAnimations contains an early-return path that
        // fires (silently, with no log) for animatables that are neither LivingEntity, Item,
        // nor BlockEntity — i.e. plain GeoAnimatable implementations like ours. With that
        // path taken, lastUpdateTime never advances, the AnimationProcessor's
        // tickAnimation is never called, and the controller predicate is never invoked.
        // (Diagnostic confirmation: a side-by-side test showed setCustomAnimations leaving
        // manager state untouched while a direct tickAnimation call fired the predicate
        // and produced correct first-keyframe bone values on the very next frame.)
        //
        // We replicate the small amount of setup setCustomAnimations would normally do
        // (firstTickTime initialization, updatedAt for tick tracking) and then call
        // tickAnimation directly. This is exactly what GeckoLib does internally for
        // LivingEntity animatables; we're just bypassing the gate.
        AnimationState<SomniumCastAnimatable> state =
                new AnimationState<>(animatable, 0f, 0f, partialTick, false);

        try {
            software.bernie.geckolib.core.animation.AnimatableManager<SomniumCastAnimatable> mgr =
                    animatable.getAnimatableInstanceCache().getManagerForId(animatable.getInstanceId());

            double frameTime = software.bernie.geckolib.util.RenderUtils.getCurrentTick() + partialTick;

            if (mgr.getFirstTickTime() == -1) {
                mgr.startedAt(frameTime);
            }
            mgr.updatedAt(frameTime);

            model.getAnimationProcessor().tickAnimation(
                    animatable, model, mgr, frameTime, state, false);
        } catch (Exception e) {
            System.out.println("[Somnium-DIAG] apply: tickAnimation THREW " + e);
            LOGGER.error("[Somnium] CastAnimation: tickAnimation threw for animation '{}'.",
                    animatable.getActiveAnimation());
            LOGGER.error("[Somnium] CastAnimation: exception detail", e);
            return;
        }

        // Verification log — show bone progression at key frames so we can confirm motion
        if (frameCountForCurrentAnim == 0 || frameCountForCurrentAnim == 5
                || frameCountForCurrentAnim == 15 || frameCountForCurrentAnim == 30
                || frameCountForCurrentAnim == 60) {
            Optional<GeoBone> rightArm = bakedModel.getBone("right_arm");
            if (rightArm.isPresent()) {
                GeoBone b = rightArm.get();
                System.out.println("[Somnium-DIAG] frame=" + frameCountForCurrentAnim
                        + " right_arm rot=(" + b.getRotX() + ", " + b.getRotY() + ", " + b.getRotZ() + ")"
                        + " pos=(" + b.getPosX() + ", " + b.getPosY() + ", " + b.getPosZ() + ")"
                        + " scale=(" + b.getScaleX() + ", " + b.getScaleY() + ", " + b.getScaleZ() + ")");
            }
            Optional<GeoBone> body = bakedModel.getBone("body");
            if (body.isPresent()) {
                GeoBone b = body.get();
                System.out.println("[Somnium-DIAG] frame=" + frameCountForCurrentAnim
                        + " body       rot=(" + b.getRotX() + ", " + b.getRotY() + ", " + b.getRotZ() + ")"
                        + " pos=(" + b.getPosX() + ", " + b.getPosY() + ", " + b.getPosZ() + ")"
                        + " scale=(" + b.getScaleX() + ", " + b.getScaleY() + ", " + b.getScaleZ() + ")");
            }
        }
        frameCountForCurrentAnim++;

        applyAllBones(bakedModel, playerModel);
    }

    // ─── Bone application ────────────────────────────────────────────────────

    /**
     * Applies every animated bone in {@code bakedModel} onto its corresponding vanilla
     * {@link ModelPart}, additively.
     *
     * <p><b>Root bone translation is suppressed.</b> In Blockbench (and GeckoLib's
     * recursive PoseStack renderer), the {@code body} bone is the parent of every
     * other bone — translating body translates the entire skeleton because all the
     * other bones inherit the body's PoseStack frame. Vanilla Minecraft's
     * {@link PlayerModel}, in contrast, exposes every part as a flat, independent
     * {@link ModelPart} with no parent reference. There is no way to faithfully
     * replicate Blockbench's body translation by writing to individual
     * {@link ModelPart#x}/{@code y}/{@code z} fields: writing only to the body
     * detaches the chest from the head and limbs; cascading the offset to every
     * other part detaches the head from the rotated body (because we'd also need to
     * rotate each child around the body's pivot, not its own — proper 4×4 matrix
     * math, not a scalar add). Faithfully matching GeckoLib here would require a
     * separate PoseStack-translation mixin and is deferred.</p>
     *
     * <p>For now we drop the body bone's position channel and keep its rotation and
     * scale channels. Most "lean" and "wind-up" animations can be authored using
     * body rotation (e.g. {@code body.rotX = -0.3} for a backward lean) instead of
     * body translation, and read identically on the player. Per-limb translations
     * (right_arm, head, etc.) continue to work normally because those parts are
     * leaves of the bone tree and their offsets translate cleanly to ModelPart
     * pivot offsets.</p>
     */
    private static void applyAllBones(BakedGeoModel bakedModel, PlayerModel<?> playerModel) {
        applyBoneIfPresent(bakedModel, playerModel, "body");
        applyBoneIfPresent(bakedModel, playerModel, "head");
        applyBoneIfPresent(bakedModel, playerModel, "hat");
        applyBoneIfPresent(bakedModel, playerModel, "jacket");
        applyBoneIfPresent(bakedModel, playerModel, "right_arm");
        applyBoneIfPresent(bakedModel, playerModel, "right_sleeve");
        applyBoneIfPresent(bakedModel, playerModel, "left_arm");
        applyBoneIfPresent(bakedModel, playerModel, "left_sleeve");
        applyBoneIfPresent(bakedModel, playerModel, "right_leg");
        applyBoneIfPresent(bakedModel, playerModel, "right_pants");
        applyBoneIfPresent(bakedModel, playerModel, "left_leg");
        applyBoneIfPresent(bakedModel, playerModel, "left_pants");
    }

    private static void applyBoneIfPresent(BakedGeoModel baked,
                                           PlayerModel<?> playerModel,
                                           String boneName) {
        Optional<GeoBone> opt = baked.getBone(boneName);
        if (opt.isEmpty()) return;

        GeoBone bone = opt.get();
        if (bone.isHidden()) return;

        ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, boneName);
        if (part == null) return;

        // ── Additive rotation (radians) ──
        //
        // Coordinate conversion: Blockbench → vanilla ModelPart.
        // The player model is rendered with PoseStack.scale(-1, -1, 1) (X and Y flipped),
        // which inverts the direction of rotations around all three axes. The X-flip
        // alone reverses Y and Z rotations; the Y-flip alone reverses X and Z rotations;
        // combined, all three axes flip. Empirically: a Blockbench rotX of +π/2 (arm
        // forward) maps to a vanilla xRot of -π/2 (arm forward) — same visual, opposite sign.
        float rx = bone.getRotX();
        float ry = bone.getRotY();
        float rz = bone.getRotZ();

        if (rx != 0f) part.xRot -= rx;
        if (ry != 0f) part.yRot -= ry;
        if (rz != 0f) part.zRot -= rz;

        // ── Additive position (model units) ──
        //
        // Coordinate conversion: Blockbench → vanilla ModelPart.
        // Same scale(-1, -1, 1) flip as above — X and Y are mirrored, Z is unchanged.
        //
        // The body bone's position channel is intentionally skipped — see Javadoc on
        // applyAllBones for the rationale (Blockbench root-bone translation has no
        // clean equivalent on the flat ModelPart hierarchy; rotation and scale on
        // the body still propagate visually because they affect the body's own cube
        // mesh directly).
        if (!"body".equals(boneName)) {
            float px = bone.getPosX();
            float py = bone.getPosY();
            float pz = bone.getPosZ();

            if (px != 0f) part.x -= px;
            if (py != 0f) part.y -= py;
            if (pz != 0f) part.z += pz;
        }

        // ── Multiplicative scale (applied via PoseStack in ModelPartRenderMixin) ──
        // Scale cannot be stored as a field on ModelPart — it must be applied via
        // PoseStack.scale() at render time. Values are written to SomniumBoneScaleMap
        // and read by ModelPartRenderMixin after translateAndRotate() positions the
        // PoseStack at this bone's pivot. Identity scale (1, 1, 1) is a no-op.
        float sx = bone.getScaleX();
        float sy = bone.getScaleY();
        float sz = bone.getScaleZ();
        if (sx != 1f || sy != 1f || sz != 1f) {
            SomniumBoneScaleMap.setScale(part, sx, sy, sz);
        }
    }

    private SomniumCastBoneApplicator() {}
}