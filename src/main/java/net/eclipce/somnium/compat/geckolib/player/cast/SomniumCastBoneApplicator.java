package net.eclipce.somnium.compat.geckolib.player.cast;

import com.mojang.logging.LogUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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

    /**
     * Block-unit Y anchor for stretching an arm. The vanilla arm cube spans local Y from -2 (a
     * 2-unit shoulder cap above the pivot) to +10 in MODEL units; scaling about y=-2 (the cap's
     * top face) keeps the shoulder end planted so a stretched arm extends only away from the
     * joint and its back stays flush with the body.
     *
     * <p><b>Units:</b> the render PoseStack at the scale-injection point is in BLOCK units (the
     * same space the look-pitch rest pivot uses, e.g. -5/16). So the model-unit value -2 must be
     * divided by 16. Using the raw -2 translated the arm a full 2 blocks away — which flung it
     * out of frame on long stretches and detached it on short ones.</p>
     */
    private static final float ARM_SCALE_ANCHOR_Y = -2f / 16f;

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

        // ── Procedural (code-driven) stretch pass ──
        // Runs every frame, independent of whether a clip animation is active, because some
        // abilities (e.g. Gomu Rocket's grab) stretch a limb purely from gameplay code toward
        // a world point with no authored clip. Writes into the same SomniumBoneScaleMap channel
        // the clip pass uses, so the two compose multiplicatively if both are present.
        applyProceduralStretch(player, playerModel);

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

        // Apply bones — track which received a non-zero delta. (animatedBones is retained
        // for potential future per-bone option scoping; it is not currently consumed —
        // onExecuteBodyAlign is a server-side yaw snap and changeDirectionOnLook applies to
        // its explicitly-listed parts regardless of per-frame delta.)
        java.util.Set<String> animatedBones = new java.util.HashSet<>();
        applyAllBones(bakedModel, playerModel, animatedBones);

        // Opt-in only — no-ops unless this animation's options explicitly requested it.
        // See applyBodyRotationCompound's javadoc and CastAnimationOptions#propagateBodyTransform.
        applyBodyRotationCompound(bakedModel, playerModel, options);

        // ── changeDirectionOnLook ──
        // Register the execution-time look pitch (frozen in the options) as a render-time
        // OUTER rotation about each part's anchor, NOT as a bone xRot. The mixin applies it
        // wrapping the bone's pose + scale, so the whole posed/scaled limb tilts up/down about
        // the shoulder/hip while staying anchored. Doing it as a field write (the old way)
        // fought the anchor and flung the arm off the body.
        //
        // Must run AFTER applyAllBones (which registers the animation's scale + anchor for the
        // base parts) so the pitch composes into the same scale-map entry. The sleeve/pant
        // overlay won't inherit this via syncLayersFromBaseParts (copyFrom only moves fields,
        // not render-time scale-map data), so we register the pitch for the overlay too.
        if (!options.changeDirectionOnLook().isEmpty()) {
            applyDirectionOnLook(playerModel, options.changeDirectionOnLook(),
                    options.capturedLookPitch());
        }

        // ── Sync skin-layer overlays to their base parts ──
        // PlayerModel.setupAnim ends with a series of copyFrom calls that align each
        // layer (sleeves, pants, hat, jacket) with its base part (arms, legs, head, body).
        // We've since mutated the base parts (suppress, applyAllBones, changeDirectionOnLook)
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
     * <p><b>Root bone translation/rotation is independent by default.</b> In Blockbench
     * (and GeckoLib's recursive PoseStack renderer), the {@code body} bone is the parent of
     * every other bone — translating or rotating body carries the entire skeleton because
     * all the other bones inherit body's PoseStack frame. Vanilla Minecraft's
     * {@link PlayerModel}, in contrast, exposes every part as a flat, independent
     * {@link ModelPart} with no parent reference, so by default each part here only ever
     * reflects its own keyframes.</p>
     *
     * <p>Most "lean" and "wind-up" animations don't need body's motion to reach the limbs —
     * Pistol/Gatling/Bazooka/Rocket were authored and screenshot-tuned around exactly that.
     * Large whole-body transformation animations do need it. See
     * {@link #applyBodyRotationCompound}, called right after this method, for the proper
     * (quaternion-composed, not scalar-add) fix — gated behind
     * {@link CastAnimationOptions#propagateBodyTransform()} so it never touches an
     * animation that doesn't explicitly ask for it.</p>
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
     * Compounds body's rotation and position onto every other base part — head, both arms,
     * both legs — the way a real parent bone would carry its children. No-ops entirely
     * unless {@code options.propagateBodyTransform()} is set.
     *
     * <h3>Why quaternion composition, not a scalar rotation add</h3>
     * An earlier version of this fix tried registering body's rotation as a render-time
     * PoseStack wrap and, before that, adding body's position as a flat scalar onto each
     * child's own position. Neither produced a visible change worth the complexity, and
     * for good reason: at body's ~45° peak rotation in {@code gear_second}, the dominant
     * visual effect isn't a small offset, it's the child's REST PIVOT sweeping through a
     * large arc as it orbits body's pivot — exactly the effect confirmed against the
     * reference Blockbench render (a deep crouch, torso folding down near the legs), which
     * a flat position add can't produce at all. Getting this right needs the same math
     * GeckoLib's own renderer uses to walk a real parent-child hierarchy: {@code
     * RenderUtils.rotateMatrixAroundBone} composes a bone's Z, then Y, then X rotation in
     * sequence (matching JOML's {@code rotationZYX} convention exactly) — a parent's full
     * rotation composes with a child's own local rotation via quaternion multiplication
     * before the child's pivot offset is ever applied, so the pivot orbits the parent's
     * rotation instead of just adding to it.
     *
     * <h3>Coordinate conversion</h3>
     * Reuses {@link #applyBoneIfPresent}'s already-proven sign conversion (negate all three
     * rotation axes; negate X/Y position, keep Z) for both body and each child, since that
     * conversion is empirically validated against every existing Gomu clip and isn't
     * re-derived here. {@link SomniumBoneAnchors#restPivot} gives each child's rest pivot
     * in vanilla block units (body's own rest pivot is the origin), used to compute how far
     * that pivot moves when swept through body's rotation.
     *
     * <h3>What this deliberately does NOT do</h3>
     * Body's own SCALE isn't compounded (no current clip uses it on body). Rotation is only
     * composed for the six base parts named below — overlay layers (hat, sleeves, pants)
     * pick up the result automatically through {@link #syncLayersFromBaseParts}'s copyFrom
     * pass, which runs after this method.
     *
     * <h3>A latent assumption worth knowing about</h3>
     * This overwrites (not adds to) each carried part's {@code xRot/yRot/zRot} with the
     * fully-composed value. That's correct as long as {@code suppressVanillaAnimOn} covers
     * every part this method touches — true for Gear Second today — because
     * {@code resetPose()} already zeroed the vanilla locomotion contribution before
     * {@link #applyAllBones} ran. If a future transformation stops suppressing one of these
     * parts (to let vanilla walk-swing show through, as flagged as a possible tweak for
     * Gear Second's legs), this method would need to preserve that vanilla rotation instead
     * of overwriting it.
     */
    private static void applyBodyRotationCompound(BakedGeoModel bakedModel,
                                                  PlayerModel<?> playerModel,
                                                  CastAnimationOptions options) {
        if (!options.propagateBodyTransform()) return;

        Optional<GeoBone> bodyBone = bakedModel.getBone("body");
        if (bodyBone.isEmpty() || bodyBone.get().isHidden()) return;
        GeoBone bb = bodyBone.get();

        // Body's own vanilla-equivalent rotation, built exactly like applyBoneIfPresent's
        // per-axis conversion but composed into a quaternion via rotationZYX so it can be
        // multiplied with each child's own rotation below.
        float bodyVXRot = -bb.getRotX();
        float bodyVYRot = -bb.getRotY();
        float bodyVZRot = -bb.getRotZ();
        Quaternionf qBody = new Quaternionf().rotationZYX(bodyVZRot, bodyVYRot, bodyVXRot);

        // Body's own position delta, pixel units (matches ModelPart.x/y/z directly — same
        // conversion applyBoneIfPresent uses for every non-body bone's position).
        float bodyPosDX = -bb.getPosX();
        float bodyPosDY = -bb.getPosY();
        float bodyPosDZ =  bb.getPosZ();

        if (bodyVXRot == 0f && bodyVYRot == 0f && bodyVZRot == 0f
                && bodyPosDX == 0f && bodyPosDY == 0f && bodyPosDZ == 0f) {
            return;
        }

        compoundOntoChild(bakedModel, playerModel.head,     "head",      qBody, bodyPosDX, bodyPosDY, bodyPosDZ);
        compoundOntoChild(bakedModel, playerModel.rightArm, "right_arm", qBody, bodyPosDX, bodyPosDY, bodyPosDZ);
        compoundOntoChild(bakedModel, playerModel.leftArm,  "left_arm",  qBody, bodyPosDX, bodyPosDY, bodyPosDZ);
        compoundOntoChild(bakedModel, playerModel.rightLeg, "right_leg", qBody, bodyPosDX, bodyPosDY, bodyPosDZ);
        compoundOntoChild(bakedModel, playerModel.leftLeg,  "left_leg",  qBody, bodyPosDX, bodyPosDY, bodyPosDZ);
    }

    /**
     * Composes body's rotation (outer/parent) with one child bone's own rotation
     * (inner/local), writes the combined result directly onto the child's {@link ModelPart},
     * and adds the position delta caused by the child's rest pivot orbiting body's rotation
     * on top of body's own translation. See {@link #applyBodyRotationCompound}'s javadoc for
     * the full reasoning.
     */
    private static void compoundOntoChild(BakedGeoModel bakedModel, ModelPart part, String boneName,
                                          Quaternionf qBody, float bodyPosDX, float bodyPosDY, float bodyPosDZ) {
        Optional<GeoBone> opt = bakedModel.getBone(boneName);
        if (opt.isEmpty() || opt.get().isHidden()) return;
        GeoBone cb = opt.get();

        float childVXRot = -cb.getRotX();
        float childVYRot = -cb.getRotY();
        float childVZRot = -cb.getRotZ();
        Quaternionf qChild = new Quaternionf().rotationZYX(childVZRot, childVYRot, childVXRot);

        // qBody * qChild: transforming a point applies qChild first (the child's own local
        // rotation), then qBody (body's rotation wrapping around it) — exactly how GeckoLib's
        // renderRecursively nests a child's PoseStack transform inside its already-rotated
        // parent frame.
        Quaternionf qCombined = new Quaternionf(qBody).mul(qChild);

        Vector3f euler = new Vector3f();
        qCombined.getEulerAnglesZYX(euler);
        part.xRot = euler.x;
        part.yRot = euler.y;
        part.zRot = euler.z;

        // Rest pivot (block units; body's own rest pivot is the origin, so this offset is
        // already relative to it) swept through body's rotation — the arc a real child bone's
        // pivot would travel as its parent rotates. Delta only (rotatedOffset - offset), not
        // the absolute rotated position, then converted to pixel units to match
        // ModelPart.x/y/z and added on top of whatever applyBoneIfPresent already wrote for
        // this child's own local position delta moments ago.
        float[] restPivot = SomniumBoneAnchors.restPivot(boneName);
        Vector3f offset = new Vector3f(restPivot[0], restPivot[1], restPivot[2]);
        Vector3f rotatedOffset = qBody.transform(new Vector3f(offset));
        Vector3f pivotShiftPixels = new Vector3f(rotatedOffset).sub(offset).mul(16f);

        part.x += bodyPosDX + pivotShiftPixels.x();
        part.y += bodyPosDY + pivotShiftPixels.y();
        part.z += bodyPosDZ + pivotShiftPixels.z();
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
     * Applies any active {@link SomniumProceduralStretch} for this player by writing a scale
     * into {@link SomniumBoneScaleMap} for the stretched part. The arm's length axis in the
     * vanilla {@code PlayerModel} runs along local Y (the limb hangs downward from its pivot),
     * so a reach is expressed as a Y-scale; X/Z stay at 1 so the limb gets longer, not fatter.
     *
     * <p>This composes with clip-driven scale because {@code SomniumBoneScaleMap.setScale} is
     * multiplicative. It runs before the clip pass's early-return, so an ability that stretches
     * purely from code (no clip) still renders.</p>
     */
    private static void applyProceduralStretch(Player player, PlayerModel<?> playerModel) {
        SomniumProceduralStretch.Lean lean =
                SomniumProceduralStretch.get(player.getUUID());
        if (lean == null) return;

        // ── Body lean: pitch each listed part about its rest pivot (composes with clips) ──
        if (lean.pitchRad != 0f && !lean.parts.isEmpty()) {
            for (CastBodyPart part : lean.parts) {
                float[] rp = SomniumBoneAnchors.restPivot(part.partName());
                SomniumBoneScaleMap.setLookPitch(part.get(playerModel), lean.pitchRad,
                        rp[0], rp[1], rp[2]);
                ModelPart overlay = overlayFor(playerModel, part);
                if (overlay != null) {
                    SomniumBoneScaleMap.setLookPitch(overlay, lean.pitchRad, rp[0], rp[1], rp[2]);
                }
            }
        }

        // ── Held-arm stretch: keep the grabbing arm extended + aimed during flight/hold, when
        // the single clip slot is busy (or no clip drives the arm). ──
        if (lean.armPart != null) {
            ModelPart arm = lean.armPart.get(playerModel);
            ModelPart armOverlay = overlayFor(playerModel, lean.armPart);

            // CRITICAL: reset the arm to its REST pose first. Vanilla PlayerModel.setupAnim has
            // already written this frame's locomotion pose (idle sway / walk swing / look) onto
            // the arm; without wiping it, our aim rotation and Y-scale would compose on top of an
            // uncontrolled, every-frame-changing orientation — which is exactly why the arm went
            // wild after the clip ended. resetPose() gives a known base (arm hanging straight
            // down, all rotations 0), so the aim below is absolute and stable. Same call the clip
            // path uses via suppressVanillaAnimOn. Arm-agnostic: works for RIGHT_ARM or LEFT_ARM.
            arm.resetPose();
            if (armOverlay != null) armOverlay.resetPose();

            if (lean.armPitchRad != 0f) {
                float[] rp = SomniumBoneAnchors.restPivot(lean.armPart.partName());
                SomniumBoneScaleMap.setLookPitch(arm, lean.armPitchRad, rp[0], rp[1], rp[2]);
                if (armOverlay != null) {
                    SomniumBoneScaleMap.setLookPitch(armOverlay, lean.armPitchRad, rp[0], rp[1], rp[2]);
                }
            }
            if (lean.armScaleY != 1f) {
                // Anchor the stretch at the arm cube's shoulder-cap face (local y = -2), not the
                // pivot, so the back of the arm stays planted at the shoulder instead of jutting
                // up past it as it lengthens. See SomniumBoneScaleMap.setScale anchor docs.
                SomniumBoneScaleMap.setScale(arm, 1f, lean.armScaleY, 1f, 0f, ARM_SCALE_ANCHOR_Y, 0f);
                if (armOverlay != null) {
                    SomniumBoneScaleMap.setScale(armOverlay, 1f, lean.armScaleY, 1f,
                            0f, ARM_SCALE_ANCHOR_Y, 0f);
                }
            }
        }
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

    // onExecuteBodyAlign is handled entirely server-side at execution (see
    // SomniumAnimHelper.triggerCastAnimation) — it re-faces the player and needs no render
    // code. It handles the HORIZONTAL (yaw) component of aiming; applyDirectionOnLook below
    // handles the VERTICAL (pitch) component, and the two together make the limb point
    // where the player is looking.

    /**
     * Registers the {@code changeDirectionOnLook} pitch for the requested parts. The pitch is
     * applied by {@code ModelPartRenderMixin} as a rotation about each bone's own pivot, BEFORE
     * the bone's transforms run — pre-tilting the frame so the entire authored animation
     * (position, rotation, scale) plays inside it, angled up/down toward the look. Because the
     * rotation is about the pivot, the limb stays attached at the shoulder/hip automatically;
     * no anchor table is needed.
     *
     * <h3>Sign</h3>
     * <p>{@code capturedLookPitch} is degrees, positive looking down. Registered as radians. If
     * up/down comes out inverted on your rig, negate {@code tiltRad} here — single sign knob.</p>
     *
     * <h3>Base + overlay</h3>
     * <p>Registered for both the base part and its overlay (sleeve/pants), since the overlay
     * won't inherit a render-time rotation via {@code copyFrom}. HEAD and BODY are skipped.</p>
     */
    private static void applyDirectionOnLook(PlayerModel<?> playerModel,
                                             java.util.Set<CastBodyPart> requestedParts,
                                             float capturedLookPitchDegrees) {
        float tiltRad = (float) Math.toRadians(capturedLookPitchDegrees);
        if (tiltRad == 0f) return;

        for (CastBodyPart part : requestedParts) {
            if (part == CastBodyPart.HEAD || part == CastBodyPart.BODY) continue;

            // Rest pivot (the joint) for this bone — the rotation point. The base part and its
            // overlay sleeve/pants share the same joint.
            float[] rp = SomniumBoneAnchors.restPivot(part.partName());

            SomniumBoneScaleMap.setLookPitch(part.get(playerModel), tiltRad, rp[0], rp[1], rp[2]);

            ModelPart overlay = overlayFor(playerModel, part);
            if (overlay != null) {
                SomniumBoneScaleMap.setLookPitch(overlay, tiltRad, rp[0], rp[1], rp[2]);
            }
        }
    }

    /** Maps a base {@link CastBodyPart} to its overlay-layer ModelPart, or null if none. */
    private static ModelPart overlayFor(PlayerModel<?> m, CastBodyPart part) {
        return switch (part) {
            case RIGHT_ARM -> m.rightSleeve;
            case LEFT_ARM  -> m.leftSleeve;
            case RIGHT_LEG -> m.rightPants;
            case LEFT_LEG  -> m.leftPants;
            default        -> null;
        };
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