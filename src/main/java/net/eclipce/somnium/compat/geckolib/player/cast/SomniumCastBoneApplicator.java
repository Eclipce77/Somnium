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

        // ── Restore previously-hidden parts FIRST ──
        // Runs every frame regardless of whether an animation is active. If the previous
        // animation hid some parts and has since ended (or was replaced with one whose
        // hide-set differs), this re-shows everything we had hidden. The current frame's
        // hide-set is reapplied below after applyAllBones.
        restorePreviouslyHidden(playerModel, animatable);

        // Consume any packet-queued animation before checking isActive
        animatable.consumeQueue();

        if (animatable.getActiveAnimation() == null) {
            return;
        }

        CastAnimationOptions options = animatable.getCurrentOptions();

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

        // ── suppressVanillaAnimOn ──
        // For each configured part, reset to its initial (rest) pose BEFORE we apply
        // animation deltas. Vanilla setupAnim already ran by the time we get here, so the
        // part holds the vanilla walk-swing / attack / look pose; resetPose() wipes that
        // to the part's rig-default. Our additive deltas then sit on rest, not on vanilla.
        for (String partName : options.suppressVanillaAnimOn()) {
            ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, partName);
            if (part != null) part.resetPose();
        }

        // ── Capture vanilla head pose BEFORE applyAllBones mutates the head ModelPart ──
        // Used by followPlayerLook below. We need pitch (head.xRot) and yaw delta
        // (head.yRot, which vanilla setupAnim has already set to netHeadYaw in radians)
        // as the source values to add onto animated arm/leg pairs.
        float vanillaHeadPitch = 0f;
        float vanillaHeadYaw   = 0f;
        if (options.followPlayerLook()) {
            ModelPart head = SomniumPlayerBoneMap.getPart(playerModel, "head");
            if (head != null) {
                vanillaHeadPitch = head.xRot;
                vanillaHeadYaw   = head.yRot;
            }
        }

        // Apply bones — track which received a non-zero delta so followPlayerLook below
        // can scope itself to only animated parts (per the API contract).
        java.util.Set<String> animatedBones = new java.util.HashSet<>();
        applyAllBones(bakedModel, playerModel, animatedBones);

        // ── followPlayerLook ──
        // Add the head's pitch and yaw delta to animated arms and legs, so the punch /
        // kick / projectile motion angles with the player's view direction.
        if (options.followPlayerLook()) {
            applyFollowLook(playerModel, animatedBones, vanillaHeadPitch, vanillaHeadYaw);
        }

        // ── hideBodyPart / hideLayer ──
        // Set the requested ModelParts invisible and record them in hiddenByUs so that
        // restorePreviouslyHidden (at the top of the next frame's apply()) can re-show
        // them when the option set changes or the animation ends.
        applyHideOptions(playerModel, options, animatable);
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
    private static void applyAllBones(BakedGeoModel bakedModel,
                                      PlayerModel<?> playerModel,
                                      java.util.Set<String> animatedBones) {
        applyBoneIfPresent(bakedModel, playerModel, "body",         animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "head",         animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "hat",          animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "jacket",       animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "right_arm",    animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "right_sleeve", animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "left_arm",     animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "left_sleeve",  animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "right_leg",    animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "right_pants",  animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "left_leg",     animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "left_pants",   animatedBones);
    }

    private static void applyBoneIfPresent(BakedGeoModel baked,
                                           PlayerModel<?> playerModel,
                                           String boneName,
                                           java.util.Set<String> animatedBones) {
        Optional<GeoBone> opt = baked.getBone(boneName);
        if (opt.isEmpty()) return;

        GeoBone bone = opt.get();
        if (bone.isHidden()) return;

        ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, boneName);
        if (part == null) return;

        boolean anyTransform = false;

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

        if (rx != 0f) { part.xRot -= rx; anyTransform = true; }
        if (ry != 0f) { part.yRot -= ry; anyTransform = true; }
        if (rz != 0f) { part.zRot -= rz; anyTransform = true; }

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

            if (px != 0f) { part.x -= px; anyTransform = true; }
            if (py != 0f) { part.y -= py; anyTransform = true; }
            if (pz != 0f) { part.z += pz; anyTransform = true; }
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
            anyTransform = true;
        }

        if (anyTransform) animatedBones.add(boneName);
    }

    // ─── Option-driven helpers ───────────────────────────────────────────────

    /**
     * Restores every part previously hidden by us to {@code visible = true}.
     * Called unconditionally at the top of every {@link #apply} so visibility
     * self-heals as soon as an animation ends or its hide-set changes.
     */
    private static void restorePreviouslyHidden(PlayerModel<?> playerModel, SomniumCastAnimatable animatable) {
        java.util.Set<String> hidden = animatable.getHiddenByUs();
        if (hidden.isEmpty()) return;
        for (String name : hidden) {
            ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, name);
            if (part != null) part.visible = true;
        }
        hidden.clear();
    }

    /**
     * Applies the current frame's hide-set. The two hide channels (body and layer) are
     * functionally identical — both set {@link ModelPart#visible} false — and are kept
     * separate purely to give the API surface clarity about what kind of part is targeted.
     */
    private static void applyHideOptions(PlayerModel<?> playerModel,
                                         CastAnimationOptions options,
                                         SomniumCastAnimatable animatable) {
        java.util.Set<String> hidden = animatable.getHiddenByUs();
        for (String name : options.hideBodyPart()) {
            ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, name);
            if (part != null) {
                part.visible = false;
                hidden.add(name);
            }
        }
        for (String name : options.hideLayer()) {
            ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, name);
            if (part != null) {
                part.visible = false;
                hidden.add(name);
            }
        }
    }

    /**
     * Arm and leg layer pairs for {@link #applyFollowLook}. When any member of a group
     * has a non-zero animated transform, all members of the group receive the look
     * influence so the skin layer stays glued to its base part (otherwise a follow-look
     * rotation applied only to {@code right_arm} would leave {@code right_sleeve}
     * trailing behind at the vanilla pose).
     */
    private static final java.util.List<java.util.List<String>> FOLLOW_LOOK_GROUPS = java.util.List.of(
            java.util.List.of("right_arm",  "right_sleeve"),
            java.util.List.of("left_arm",   "left_sleeve"),
            java.util.List.of("right_leg",  "right_pants"),
            java.util.List.of("left_leg",   "left_pants")
    );

    /**
     * Adds the vanilla head pitch and yaw delta onto every animated arm/leg group.
     * Head and body parts are deliberately excluded — the head already follows look in
     * vanilla, and the body rotating to follow the head would be visually jarring.
     */
    private static void applyFollowLook(PlayerModel<?> playerModel,
                                        java.util.Set<String> animatedBones,
                                        float pitch,
                                        float yaw) {
        for (java.util.List<String> group : FOLLOW_LOOK_GROUPS) {
            boolean groupAnimated = false;
            for (String name : group) {
                if (animatedBones.contains(name)) { groupAnimated = true; break; }
            }
            if (!groupAnimated) continue;
            for (String name : group) {
                ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, name);
                if (part != null) {
                    part.xRot += pitch;
                    part.yRot += yaw;
                }
            }
        }
    }

    // ─── First-person re-apply ───────────────────────────────────────────────

    /**
     * Re-applies the cast animation's arm/sleeve rotation after vanilla
     * {@code PlayerRenderer#renderHand} has wiped {@code arm.xRot} and {@code sleeve.xRot}
     * to {@code 0.0F}.
     *
     * <p>Called from {@code PlayerRendererFirstPersonMixin} before the
     * {@code arm.render(...)} / {@code sleeve.render(...)} calls inside {@code renderHand}.
     * Our setupAnim mixin already ran during {@code renderHand}'s own {@code setupAnim}
     * call, so all other channels ({@code yRot}, {@code zRot}, {@code x}, {@code y},
     * {@code z}, scale) survive vanilla's reset — only the explicit {@code xRot = 0.0F}
     * assignments need to be undone here.</p>
     *
     * <p>No coord-conversion sign juggling is needed at the call site because the
     * sign flip is identical to the third-person application: a Blockbench
     * {@code rotX = +π/2} maps to a vanilla {@code xRot = -π/2}, so we write
     * {@code -bone.getRotX()} directly.</p>
     *
     * @param animatable the active animatable for the local player
     * @param arm        the {@link ModelPart} vanilla {@code renderHand} is about to render
     * @param sleeve     the {@link ModelPart} vanilla {@code renderHand} is about to render
     * @param rightHand  {@code true} for the right arm/sleeve, {@code false} for left
     */
    public static void reapplyArmRotation(SomniumCastAnimatable animatable,
                                          ModelPart arm,
                                          ModelPart sleeve,
                                          boolean rightHand) {
        if (animatable == null || animatable.getActiveAnimation() == null) return;

        SomniumCastModel model = CastAnimationModelRegistry.get(animatable.getActiveModelId());
        if (model == null) return;

        BakedGeoModel baked;
        try {
            baked = model.getBakedModel(model.getModelResource(animatable));
        } catch (Exception e) {
            return;
        }
        if (baked == null) return;

        String armBone    = rightHand ? "right_arm"    : "left_arm";
        String sleeveBone = rightHand ? "right_sleeve" : "left_sleeve";

        Optional<GeoBone> armOpt = baked.getBone(armBone);
        if (armOpt.isPresent() && !armOpt.get().isHidden()) {
            // SET (not add) — vanilla just wrote 0.0F so the field has no useful prior value
            arm.xRot = -armOpt.get().getRotX();
        }
        Optional<GeoBone> sleeveOpt = baked.getBone(sleeveBone);
        if (sleeveOpt.isPresent() && !sleeveOpt.get().isHidden()) {
            sleeve.xRot = -sleeveOpt.get().getRotX();
        }
    }

    private SomniumCastBoneApplicator() {}
}