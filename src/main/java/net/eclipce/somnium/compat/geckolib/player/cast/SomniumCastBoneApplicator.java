package net.eclipce.somnium.compat.geckolib.player.cast;

import com.mojang.logging.LogUtils;
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
            // restorePreviouslyHidden (above) already re-showed any hidden parts and
            // cleared the hidden sets. If a cleanup pass was pending — i.e. an animation
            // just ended — THIS frame is that pass, so consume the flag. Nothing else to
            // undo: vanilla setupAnim already wrote fresh locomotion poses to every part
            // this frame, so parts that were under suppressVanillaAnimOn resume their
            // vanilla motion automatically simply because we no longer call resetPose().
            if (animatable.needsCleanupPass()) {
                animatable.clearCleanupPass();
            }
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

            // ── Completion detection ──
            //
            // PRIMARY: elapsed-time check. Because we drive tickAnimation manually, the
            // controller's own PLAY_ONCE stop-transition never fires — our predicate
            // re-asserts the same RawAnimation every frame (setAndContinue), so from the
            // controller's view the animation is perpetually "current". It interpolates to
            // the final keyframe and holds there forever, and getCurrentAnimation() never
            // goes null / hasAnimationFinished() never returns true. Diagnosed from a frame
            // log: bone values reached the zeroed end pose at frame ~30 and apply() kept
            // being called indefinitely with no "animation ended" ever printing.
            //
            // So we time it ourselves. We know frameTime each frame and we stamp the start
            // tick the frame the animation was consumed; when elapsed >= the baked
            // animation length (in ticks), the PLAY_ONCE / HOLD animation is done.
            //
            // LOOP animations have loopType().shouldPlayAgain() == true; we never auto-finish
            // those (matches prior behaviour — loops end only when replaced).
            if (animatable.wasJustConsumed()) {
                animatable.setAnimationStartTick(frameTime);
                animatable.clearJustConsumed();
            }

            boolean ended = false;
            String reason = null;

            // Elapsed-time completion against the caller-supplied animation length (in
            // ticks). We use a supplied length rather than reading it off the GeckoLib
            // Animation object because the manual tick-driving path means the controller's
            // own completion state never flips (see note above), and the length you author
            // in the ability (EXTEND_TICKS etc.) is the authoritative duration anyway.
            // A length of <= 0 means "loop / never auto-finish" (used for the gatling loop
            // clip, which is ended explicitly by triggering the retract).
            double startTick = animatable.getAnimationStartTick();
            int lengthTicks = options.animationLengthTicks();
            if (lengthTicks > 0 && startTick >= 0) {
                double elapsed = frameTime - startTick;
                if (elapsed >= lengthTicks) {
                    ended = true;
                    reason = "elapsed " + elapsed + " >= length " + lengthTicks + " ticks";
                }
            }

            // FALLBACK: keep the controller-state checks too, in case a future change makes
            // the controller transition on its own (then we finish promptly rather than
            // waiting out the full length).
            software.bernie.geckolib.core.animation.AnimationController<SomniumCastAnimatable> controller =
                    mgr.getAnimationControllers().get("somnium_cast");
            if (!ended && controller != null) {
                if (controller.getCurrentAnimation() == null) {
                    ended = true;
                    reason = "currentAnimation became null (PLAY_ONCE finished)";
                } else if (controller.hasAnimationFinished()) {
                    ended = true;
                    reason = "hasAnimationFinished() (tick past length)";
                }
            }

            if (ended) {
                System.out.println("[Somnium-DIAG] apply: animation ended — " + reason
                        + " — clearing state");
                animatable.onAnimationFinished();
                // Skip the remaining apply steps; next frame's restorePreviouslyHidden
                // will re-show any layers/parts we hid, and the early-return at the top
                // of apply() will catch the now-null activeAnimation.
                return;
            }
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
        //
        // Layer overlays (sleeves, pants, jacket, hat) are NOT individually reset here —
        // the post-application syncLayersFromBaseParts() pass below copies the base
        // part's final pose to its layer, which inherently resets the layer to match.
        for (CastBodyPart part : options.suppressVanillaAnimOn()) {
            part.get(playerModel).resetPose();
        }

        // ── Capture vanilla head pose BEFORE applyAllBones mutates the head ModelPart ──
        // Used by followPlayerBody below. We need the head pitch (head.xRot) at the value
        // vanilla setupAnim wrote, before any animation delta moves the head.
        float vanillaHeadPitch = 0f;
        if (!options.followPlayerBody().isEmpty()) {
            vanillaHeadPitch = playerModel.head.xRot;
        }

        // Apply bones — track which received a non-zero delta so followPlayerBody below
        // can scope itself to only animated parts (per the API contract).
        java.util.Set<String> animatedBones = new java.util.HashSet<>();
        applyAllBones(bakedModel, playerModel, animatedBones);

        // ── followPlayerBody ──
        // Add the head pitch as additional X-axis rotation to each specified part
        // that the animation actually touched. Yaw is intentionally not applied —
        // the entity body yaw already rotates the whole rig, and adding a head-yaw
        // delta on top would swing bones off the body, breaking the "anchored to the
        // body" intent. See applyFollowBody for the per-part rules and the layer-pair
        // expansion (arm pitches → sleeve also pitches via copyFrom downstream).
        if (!options.followPlayerBody().isEmpty()) {
            applyFollowBody(playerModel, options.followPlayerBody(), animatedBones,
                    options.suppressVanillaAnimOn(), vanillaHeadPitch);
        }

        // ── Sync skin-layer overlays to their base parts ──
        // PlayerModel.setupAnim ends with a series of copyFrom calls that align each
        // layer (sleeves, pants, hat, jacket) with its base part (arms, legs, head, body).
        // We've since mutated the base parts (suppress, applyAllBones, followPlayerBody)
        // without touching the layers, so they now hold the stale vanilla pose. A second
        // copyFrom pass here re-anchors each layer to whatever its base part finally
        // settled on — without this, e.g. the right sleeve would visibly trail the
        // animated right arm, leaving a "ghost arm" at the rest position.
        //
        // copyFrom transfers transform fields only (x/y/z, rotations, scale) — visible
        // is untouched, so the next applyHideOptions call still operates on the right
        // flags. Scale registered via SomniumBoneScaleMap also propagates: each layer
        // shares the same ModelPart::render path that ModelPartRenderMixin hooks, and
        // the layer's ModelPart identity has its own scale entry if one was set for it.
        syncLayersFromBaseParts(playerModel);

        // ── hideBodyPart / hideLayer ──
        // Set the requested ModelParts invisible and record them in the animatable's
        // hiddenBodyParts / hiddenLayers sets so that restorePreviouslyHidden (at the
        // top of the next frame's apply()) can re-show them when the option set changes
        // or the animation ends.
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
        // Only base parts — layers (sleeves, pants, hat, jacket) sync to their base
        // part via syncLayersFromBaseParts() once all base-part mutations are done.
        // Layer-specific animation channels in .geo.json files are intentionally
        // ignored — animators virtually always want sleeve to match arm exactly, and
        // bypassing that with an independent sleeve channel would be a rare-enough
        // case to handle as a future opt-in rather than baseline behaviour.
        applyBoneIfPresent(bakedModel, playerModel, "body",      animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "head",      animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "right_arm", animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "left_arm",  animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "right_leg", animatedBones);
        applyBoneIfPresent(bakedModel, playerModel, "left_leg",  animatedBones);
    }

    /**
     * Mirrors the {@code copyFrom} sequence at the end of {@code PlayerModel.setupAnim},
     * which we missed because our mixin runs <em>after</em> setupAnim returned. Each
     * layer takes its base part's final transform — including any deltas we just wrote.
     */
    private static void syncLayersFromBaseParts(PlayerModel<?> m) {
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
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
    /**
     * Re-shows every base part and layer we hid on a previous frame. Called
     * unconditionally at the top of every {@link #apply} so visibility self-heals as
     * soon as an animation ends or its hide-set changes.
     *
     * <p>The two enum-typed sets ({@code hiddenBodyParts} and {@code hiddenLayers})
     * live on the animatable and persist across frames. Each iteration calls
     * {@code enumValue.get(model)} which is a constant-time field access — no
     * string-to-part lookup. Both sets are cleared after the restore so the next
     * frame's {@code applyHideOptions} starts from empty.</p>
     */
    private static void restorePreviouslyHidden(PlayerModel<?> playerModel, SomniumCastAnimatable animatable) {
        java.util.Set<CastBodyPart> hiddenParts  = animatable.getHiddenBodyParts();
        java.util.Set<CastLayer>    hiddenLayers = animatable.getHiddenLayers();
        if (hiddenParts.isEmpty() && hiddenLayers.isEmpty()) return;

        for (CastBodyPart p : hiddenParts) p.get(playerModel).visible = true;
        for (CastLayer    l : hiddenLayers) l.get(playerModel).visible = true;
        hiddenParts.clear();
        hiddenLayers.clear();
    }

    /**
     * Applies the current frame's hide-set. The two channels (body part / overlay
     * layer) write to two separate enum-typed sets so {@link #restorePreviouslyHidden}
     * can iterate each type with its own resolved {@code ModelPart} lookup.
     */
    private static void applyHideOptions(PlayerModel<?> playerModel,
                                         CastAnimationOptions options,
                                         SomniumCastAnimatable animatable) {
        java.util.Set<CastBodyPart> hiddenParts  = animatable.getHiddenBodyParts();
        java.util.Set<CastLayer>    hiddenLayers = animatable.getHiddenLayers();

        for (CastBodyPart p : options.hideBodyPart()) {
            p.get(playerModel).visible = false;
            hiddenParts.add(p);
        }
        for (CastLayer l : options.hideLayer()) {
            l.get(playerModel).visible = false;
            hiddenLayers.add(l);
        }
    }

    /**
     * Adds the player's head pitch (X-axis rotation, signed by camera up/down) onto
     * each requested {@link CastBodyPart} that the cast animation actually moved this
     * frame. The body's yaw is intentionally not applied — it's already inherited by
     * every body-anchored bone via the entity's body yaw being a PoseStack rotation
     * one level up. Adding the head's yaw delta on top would swing limbs off the
     * shoulder/hip, breaking the "anchored to the body" intent.
     *
     * <h3>Per-part scope</h3>
     * <p>The caller passes which parts they want pitched. We only apply pitch to a
     * given part if (a) the user requested it and (b) the cast animation produced a
     * non-zero delta on that bone this frame — looking up shouldn't pitch a
     * stationary arm doing nothing. {@link CastBodyPart#HEAD} and
     * {@link CastBodyPart#BODY} are silently ignored: the head already pitches with
     * the look in vanilla, and the body never pitches in MC.</p>
     *
     * <h3>Layer overlays</h3>
     * <p>Sleeves and pants are NOT touched here directly. The post-application
     * {@code syncLayersFromBaseParts} pass copies each base part's final transform
     * (including the pitch added by this method) onto its overlay layer, so the
     * sleeve / pant tracks the limb without any per-pair bookkeeping in this method.</p>
     *
     * <h3>Why full strength (no halving)</h3>
     * <p>The previous {@code followPlayerLook} halved its influence because it
     * applied both pitch and yaw simultaneously, which moved limbs noticeably off
     * the body when the head turned. Pitch-only with the body yaw factored out
     * keeps every joint at its rig pivot — there's no "detached" failure mode to
     * mitigate against, so an aim-like 1.0× pitch is what addons actually want.</p>
     */
    private static void applyFollowBody(PlayerModel<?> playerModel,
                                        java.util.Set<CastBodyPart> requestedParts,
                                        java.util.Set<String> animatedBones,
                                        java.util.Set<CastBodyPart> suppressedParts,
                                        float pitch) {
        for (CastBodyPart part : requestedParts) {
            // Head/body are no-ops by design (see Javadoc). Skip silently so addon
            // authors can pass a "all four limbs" preset without it doing weird
            // things if they ever accidentally include head/body in the list.
            if (part == CastBodyPart.HEAD || part == CastBodyPart.BODY) continue;

            // A part is eligible for pitch if the animation moved it this frame OR it is
            // explicitly suppressed (the animation owns that limb's pose for the whole
            // cast, even on frames where the keyframed delta momentarily rounds to zero).
            //
            // The suppressed check fixes the "up/down jerk at the end" reported on Pistol:
            // as an animation winds down, a bone's delta can dip to exactly 0f for a frame,
            // dropping it out of animatedBones; without this it would lose its pitch for
            // that one frame and snap back the next, reading as a jerk. Suppressed limbs
            // stay pitched smoothly across the whole extend+retract.
            boolean eligible = animatedBones.contains(part.partName())
                    || suppressedParts.contains(part);
            if (!eligible) continue;

            // Sign: vanilla head.xRot is NEGATIVE when looking up. The cast arm is
            // keyframed extending forward, where a POSITIVE xRot delta swings the hand
            // downward (toward the legs). So to make a forward arm track the look, we
            // SUBTRACT the head pitch: looking up (negative head pitch) must raise the arm
            // (negative arm-xRot contribution → hand goes up). Adding it instead — the
            // previous behaviour — dropped the arm below the look line when aiming up/down,
            // which is the "animation pulled downward" artifact.
            part.get(playerModel).xRot -= pitch;
        }
    }

    // ─── First-person rendering ──────────────────────────────────────────────
    //
    // First-person support was migrated from a per-arm renderHand mixin to a
    // full-body re-render approach. See SomniumFirstPersonRenderer for the
    // new implementation. Setting showInFirstPerson=true on the cast options now
    // causes the local player to be rendered from inside the eye position during
    // first-person view, so any animated part that enters the player's line of
    // sight is visible to them with no special per-part bookkeeping here.

    private SomniumCastBoneApplicator() {}
}