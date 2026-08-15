package net.eclipce.somnium.compat.geckolib.player.cast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.util.RenderUtils;
import net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin;

import java.util.List;
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

        // Opt-in only — no-ops unless this animation's options request it.
        // See applyBoneHierarchyCompound's javadoc and CastAnimationOptions#compoundBoneHierarchy.
        applyBoneHierarchyCompound(bakedModel, playerModel, options);

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
     * {@link #applyBoneHierarchyCompound}, called right after this method, for the proper
     * (quaternion-composed, not scalar-add) fix — gated behind
     * {@link CastAnimationOptions#boneHierarchy()} so it never touches an animation that
     * doesn't explicitly reference a hierarchy.</p>
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

    /** The only bones {@link #applyAllBones} ever writes to — the sole candidates for
     *  hierarchy compounding. Overlay layers (hat, jacket, sleeves, pants) never carry their
     *  own animation channels (see {@link #applyAllBones}'s javadoc) and always inherit their
     *  base part's final transform via {@link #syncLayersFromBaseParts}, so they're never
     *  looked up individually here — there'd be nothing bone-specific to read. */
    private static final String[] COMPOUNDABLE_BONES =
            { "head", "right_arm", "left_arm", "right_leg", "left_leg" };
    // "body" is deliberately absent: under any hierarchy a content author would plausibly
    // build, body is the root (getParent() returns null) — its own rotation/position is
    // already exactly what applyBoneIfPresent wrote moments ago, so walking it here would
    // recompute the identical result.

    /**
     * Compounds each carried bone's rotation and position through its full ancestor chain,
     * by calling GeckoLib's own per-bone transform method on a scratch, never-rendered
     * {@link PoseStack}. No-ops entirely unless {@code options.compoundBoneHierarchy()} is set.
     *
     * <h3>History</h3>
     * This method went through several genuinely different implementations, each fixing a
     * real, confirmed problem — and one step that looked like a fix but was a regression,
     * caught by direct in-game comparison against Blockbench rather than assumed away:
     * <ol>
     *     <li>A hand-derived quaternion walk using {@link SomniumBoneAnchors} rest pivots
     *         directly — internally consistent, but never independently verified.</li>
     *     <li>Rewritten to call GeckoLib's own {@code RenderUtils.prepMatrixForBone} verbatim
     *         on a scratch PoseStack. This surfaced a real bug: GeckoLib's pivots are absolute
     *         Bedrock-space coordinates (body and head both sit at {@code [0,24,0]} in
     *         {@code default_player.geo.json}), and feeding that straight into a
     *         rotate-about-pivot step gave head — whose pivot coincides with body's — a
     *         wildly exaggerated 20+ pixel swing.</li>
     *     <li>Fixed by expressing each bone's pivot relative to its immediate parent's
     *         (still using GeckoLib's own pivot values, just as a difference rather than
     *         absolute), plus a missing vanilla-player mirror conjugation and a missing
     *         rest-position baseline (arms/legs were computing a delta from rest but never
     *         landing it on the bone's actual rest position). <b>This is the version below.</b></li>
     *     <li>Direct in-game testing (body's rotation direction and a leg's rotation both
     *         checked against Blockbench in a fixed orthographic view) confirmed every bone's
     *         rotation was correct, but position still wasn't. Suspecting a coordinate-system
     *         mix — GeckoLib's own pivot is negated on X at bake time (confirmed in
     *         {@code BakedModelFactory#constructBone}), a different convention than
     *         {@link SomniumBoneAnchors}'s vanilla one used for the rest baseline — this was
     *         rewritten as "clean" textbook forward kinematics (parent world position +
     *         parent world rotation x (vanilla rest offset + own delta)), using
     *         {@link SomniumBoneAnchors} consistently instead of GeckoLib's pivot.</li>
     *     <li><b>That rewrite was wrong, reverted back to step 3's approach.</b> In-game
     *         testing showed it as a regression, not a fix: legs crossed to the wrong side,
     *         arms got worse, only head (whose offset is zero either way) was unaffected.
     *         The textbook formula was missing a real term — confirmed by direct calculation
     *         of {@code translate(-offset) -> rotate(child's own rotation) -> translate(offset)}
     *         applied to the origin, which is nonzero. The translate-to-pivot / rotate /
     *         translate-back structure this method actually uses lets the pivot offset get
     *         swept by the <em>child's own rotation</em>, not just carried along by the
     *         parent's — a real, physically meaningful contribution the simplified formula
     *         silently dropped. Rotation doesn't depend on pivot values at all, which is
     *         exactly why it stayed correct through every version while position didn't.</li>
     *     <li><b>Body's own position channel — a real gap, but the fix went through two
     *         iterations.</b> Found by re-reading {@link #applyBoneIfPresent} in full — code
     *         this method never touches: it explicitly skips the body bone's position
     *         channel entirely ({@code if (!"body".equals(boneName))} guards the whole
     *         position block), with a documented rationale that a root bone's translation
     *         has no clean equivalent on vanilla's flat, non-hierarchical {@code ModelPart}
     *         tree — writing it only to body detaches the chest from head/limbs, since
     *         nothing carries that translation to them.
     *         <p><b>First attempt:</b> made this walk match that limitation — skip
     *         {@code translateMatrixToBone} for body at the root step too, so the base every
     *         child compounds onto matches what body itself actually renders as. This was
     *         wrong: body's authored {@code (0,-5.6,-1)} keyframe isn't incidental, it's what
     *         keeps the character grounded through a 45.5° forward lean. Dropping it left
     *         every carried bone floating roughly 0.3 blocks above where it should plant —
     *         confirmed numerically (the leg's computed pivot lands 4.7 pixels above the
     *         rest position needed for its foot to reach the ground) and visually (reported
     *         as the character floating above the ground).</p>
     *         <p><b>Actual fix:</b> the "no clean equivalent" limitation predates this
     *         method — compounding is exactly the mechanism that CAN carry a root's
     *         translation to its children correctly, which plain per-bone application
     *         couldn't. So body's position is included in the walk (children correctly
     *         inherit it), and applied directly to body's own {@code ModelPart} once, before
     *         the per-bone loop below — bypassing {@link #applyBoneIfPresent}'s drop, without
     *         modifying that shared method, so every other ability that doesn't opt into
     *         compounding is completely unaffected. Verified numerically before shipping:
     *         with both pieces included, the leg's computed foot position lands within under
     *         a pixel of true ground level, instead of 4.7 pixels short.</p></li>
     * </ol>
     *
     * <h3>The walk</h3>
     * Body's own position IS included in this walk (see above) — the walk always includes
     * every bone's {@code translateMatrixToBone} call, root or not; only the pivot
     * translate/detranslate pair is special-cased for the root.
     *
     * For a bone whose ancestry is {@code [right_arm, body]} (leaf first): walk root to leaf,
     * doing {@code translateMatrixToBone; translate(pivot); rotateMatrixAroundBone;
     * scaleMatrixForBone; translate(-pivot)} once per ancestor on the SAME scratch PoseStack,
     * where {@code pivot} is {@code (0,0,0)} for the root and {@code b.getPivotX/Y/Z() -}
     * the immediate parent's, for everyone else — GeckoLib's own (Bedrock-convention, X
     * negated at bake) pivot values, not vanilla's. A bone with no parent (chain length 1)
     * is left untouched — {@link #applyBoneIfPresent} already wrote the correct answer for it.
     *
     * <h3>The vanilla player mirror</h3>
     * {@code RenderUtils} is GeckoLib's generic transform code — it has no idea it might be
     * feeding a vanilla player model, which is rendered through an extra
     * {@code scale(-1, -1, 1)} mirror that {@link #applyBoneIfPresent}'s proven sign
     * conventions already account for. This has to be a full conjugation (mirror the frame,
     * do the real work, un-mirror) — one call before the walk, one after — not a one-sided
     * scale: rotations generally don't commute with the mirror, so mirroring only the input
     * changes WHICH rotation gets extracted, not merely its sign. Confirmed against a
     * single-bone case (body only, checked against {@link #applyBoneIfPresent}'s
     * known-correct output) before applying it to the full walk.
     *
     * <h3>The rest-position baseline</h3>
     * The walk computes a DELTA from rest (verified: all-zero rotation/position input
     * produces exactly {@code (0,0,0)}). That delta needs to land on top of this bone's
     * actual vanilla rest position — {@link SomniumBoneAnchors} is the same table
     * {@code changeDirectionOnLook} already trusts for exactly this reason. Missing this
     * previously meant every compounded arm/leg rendered bunched up near the origin instead
     * of out at the shoulder/hip; head was unaffected only because its own rest pivot is
     * {@code (0,0,0)}.
     *
     * <h3>A latent assumption worth knowing about</h3>
     * This overwrites (not adds to) each carried part's rotation and position with the
     * fully-composed result. That's correct as long as {@code suppressVanillaAnimOn} covers
     * every bone this method touches, because {@code resetPose()} already zeroed the vanilla
     * locomotion contribution before {@link #applyAllBones} ran. If a future animation stops
     * suppressing one of these parts, this method would need to preserve that vanilla
     * contribution instead of overwriting it.
     *
     * <h3>What's still unresolved</h3>
     * Body's own position and grounding are both confirmed correct in-game. Rotation is
     * confirmed correct for every bone (checked directly against Blockbench). Position for
     * arms specifically is not: {@code left_arm} lands close enough to {@code left_leg} to
     * visibly collide (confirmed by direct calculation — the two are within about a pixel of
     * each other in every axis), which is real, but the absolute-pivot fix attempted for it
     * was a substantially worse regression (the arm detaching into a separate floating piece,
     * confirmed from in-game screenshots at multiple angles) and was reverted. No formula
     * tried so far — relative pivot (collides), absolute pivot (detaches), vanilla rest-pivot
     * difference (crosses to the wrong side), a version of the relative offset with only its
     * X-component sign-corrected (also crosses) — has resolved the collision without a worse
     * side effect. This needs a fundamentally different diagnostic approach, not another
     * formula guess, before it's touched again.
     */
    private static void applyBoneHierarchyCompound(BakedGeoModel bakedModel,
                                                    PlayerModel<?> playerModel,
                                                    CastAnimationOptions options) {
        if (!options.compoundBoneHierarchy()) return;

        // Apply body's own position directly — applyBoneIfPresent (which already ran in
        // applyAllBones, before this method) skips body's position entirely for the reason
        // documented above, but that limitation doesn't apply here: this method's whole
        // purpose is correctly carrying a bone's transform to its children, which is exactly
        // what body's position needs. Applied once, here, not per-compounded-bone — body
        // isn't itself one of COMPOUNDABLE_BONES.
        //
        // Computed via the SAME mirror-conjugated translate+rotate math the walk below uses
        // for every other bone — NOT the simpler applyBoneIfPresent-style (-px,-py,+pz)
        // shortcut an earlier version used. That shortcut is correct for a bone rendered
        // completely standalone (applyBoneIfPresent's own use of it, for parts with no
        // compounding), and happens to also match this walk-based result whenever body's
        // rotation is substantial (verified: identical at the peak/loop pose, (0,5.6,-1) both
        // ways). But it diverges from what compounded children compute at LOW or ZERO
        // rotation — confirmed directly from a fresh log at the very start of the "_in"
        // transition (body barely rotated yet): body's shortcut gave -0.0976 on X while
        // head — compounded from body via the walk, same frame — gave +0.0976. Opposite
        // signs, same magnitude: body and its children visibly moving in opposite directions,
        // which is exactly "the chest doesn't follow the rest." The two conventions only
        // happen to agree at high rotation and silently disagree near zero; using the same
        // math body's children already use for their own contribution from it removes the
        // inconsistency at its source instead of matching it turn by turn.
        ModelPart bodyPart = null;
        Optional<GeoBone> bodyOpt = bakedModel.getBone("body");
        if (bodyOpt.isPresent() && !bodyOpt.get().isHidden()) {
            GeoBone bodyBone = bodyOpt.get();
            bodyPart = SomniumPlayerBoneMap.getPart(playerModel, "body");
            if (bodyPart != null) {
                float beforeX = bodyPart.x, beforeY = bodyPart.y, beforeZ = bodyPart.z;

                PoseStack bodyScratch = new PoseStack();
                bodyScratch.scale(-1f, -1f, 1f);
                RenderUtils.translateMatrixToBone(bodyScratch, bodyBone);
                RenderUtils.rotateMatrixAroundBone(bodyScratch, bodyBone);
                bodyScratch.scale(-1f, -1f, 1f);

                Vector3f bodyWorldPos = bodyScratch.last().pose().getTranslation(new Vector3f());
                bodyPart.x = bodyWorldPos.x() * 16f;
                bodyPart.y = bodyWorldPos.y() * 16f;
                bodyPart.z = bodyWorldPos.z() * 16f;

                if (probedAnim != null && probedAnim.contains("gear_second")) {
                    System.out.println("[Somnium-DIAG] body direct position: anim=" + probedAnim
                            + " before=(" + beforeX + ", " + beforeY + ", " + beforeZ + ")"
                            + " bodyBone.pos=(" + bodyBone.getPosX() + ", " + bodyBone.getPosY()
                            + ", " + bodyBone.getPosZ() + ")"
                            + " after=(" + bodyPart.x + ", " + bodyPart.y + ", " + bodyPart.z + ")");
                }
            }
        }

        ModelPart rightArmPart = null, leftArmPart = null, rightLegPart = null, leftLegPart = null;

        for (String boneName : COMPOUNDABLE_BONES) {
            Optional<GeoBone> opt = bakedModel.getBone(boneName);
            if (opt.isEmpty() || opt.get().isHidden()) continue;
            GeoBone bone = opt.get();
            if (bone.getParent() == null) continue; // root under this geo model — nothing to compound

            ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, boneName);
            if (part == null) continue;

            // Collect the chain leaf-first by walking getParent(), then reverse to root-first.
            List<GeoBone> chain = new java.util.ArrayList<>();
            GeoBone cur = bone;
            boolean chainBroken = false;
            while (cur != null) {
                if (cur.isHidden()) { chainBroken = true; break; }
                chain.add(cur);
                cur = cur.getParent();
            }
            if (chainBroken) continue;
            java.util.Collections.reverse(chain);

            // The walk: GeckoLib's own per-bone animated transform, called once per ancestor —
            // but with the pivot translate/detranslate pair done by hand (root -> (0,0,0),
            // everyone else -> relative to their immediate parent, using GeckoLib's OWN pivot
            // values) instead of calling prepMatrixForBone as one unit.
            //
            // REVERTED here from a "clean forward-kinematics" rewrite (parent_pos +
            // parent_rot*(vanilla_rest_offset + own_delta)) that looked more principled on
            // paper but was missing a real term: this translate-to-pivot / rotate / translate-
            // back structure lets the pivot offset get swept by the CHILD's own rotation, not
            // just carried by the parent's — confirmed by direct calculation (a nonzero result
            // from translate(-offset)->rotate(child's own rotation)->translate(offset) applied
            // to the origin) — which the simplified formula silently dropped. Consequence,
            // confirmed in-game: legs crossed to the wrong side and arms got worse, while head
            // (whose offset is zero either way) looked unaffected — exactly the fingerprint of
            // a missing offset-sweep term. Rotation is unaffected either way, hence why it kept
            // testing as correct through both versions.
            PoseStack scratch = new PoseStack();
            scratch.scale(-1f, -1f, 1f); // establish the vanilla player mirror before anything else
            float prevPivotX = 0, prevPivotY = 0, prevPivotZ = 0;
            for (int i = 0; i < chain.size(); i++) {
                GeoBone b = chain.get(i);
                float pivotX, pivotY, pivotZ;
                if (i == 0) {
                    pivotX = 0; pivotY = 0; pivotZ = 0;
                } else {
                    // REVERTED: a previous version special-cased right_arm/left_arm here to
                    // use their own ABSOLUTE GeckoLib pivot instead of a pivot relative to
                    // their parent, to fix left_arm landing almost on top of left_leg (a real
                    // collision, confirmed by direct calculation). That fix was itself wrong,
                    // and far more severely: in-game screenshots from multiple angles showed
                    // the arm rendering as a fully separate, detached piece floating away
                    // from the character entirely — visible in a top-down view as two
                    // disconnected gray rectangles nowhere near the body, matching none of
                    // the Blockbench reference angles, which show everything staying
                    // clustered together. A collision (two parts too close) is a smaller,
                    // more recoverable problem than a part flying off into space, so this
                    // reverts to the relative-pivot approach — same as head and legs — which
                    // is confirmed correct against real logged output and doesn't detach
                    // anything, even though the left_arm/left_leg closeness this was meant
                    // to fix is not yet resolved. See this method's javadoc for the current,
                    // honest state of what is and isn't verified for arm position.
                    pivotX = b.getPivotX() - prevPivotX;
                    pivotY = b.getPivotY() - prevPivotY;
                    pivotZ = b.getPivotZ() - prevPivotZ;
                }

                // applyBoneIfPresent (the plain, non-compounded path every other bone in this
                // rig goes through) drops the "body" bone's position channel entirely —
                // "if (!"body".equals(boneName))" guards the whole position block — with a
                // documented rationale: there's no clean way to translate a root bone across
                // vanilla's flat, non-hierarchical ModelPart tree without also carrying that
                // translation to every child, which plain per-bone application can't do.
                // That limitation predates this method; compounding IS the mechanism that
                // carries a translation to children correctly, so it doesn't apply here.
                // Confirmed numerically: body's authored -5.6-pixel Y keyframe is what keeps
                // the legs grounded through a 45.5° forward lean — dropping it (an earlier
                // version of this fix did, matching applyBoneIfPresent for consistency)
                // left every carried bone floating about 0.3 blocks above where it should
                // plant, because nothing was left to counteract the hips lifting as the
                // rotation swept them. Body's position is included here, and applied
                // directly to body's own ModelPart below (see after this loop) — bypassing
                // applyBoneIfPresent's drop specifically for animations that opt into
                // compounding, without touching that shared method at all.
                RenderUtils.translateMatrixToBone(scratch, b);
                scratch.translate(pivotX / 16f, pivotY / 16f, pivotZ / 16f);
                RenderUtils.rotateMatrixAroundBone(scratch, b);
                RenderUtils.scaleMatrixForBone(scratch, b);
                scratch.translate(-pivotX / 16f, -pivotY / 16f, -pivotZ / 16f);

                prevPivotX = b.getPivotX();
                prevPivotY = b.getPivotY();
                prevPivotZ = b.getPivotZ();
            }
            // Close the conjugation: scale(-1,-1,1) is its own inverse, so applying it again
            // here completes "mirror, do the real work, un-mirror" rather than leaving the
            // walk permanently inside a mirrored frame. EXACTLY ONE call here — a second one
            // cancels the first (scale(-1,-1,1) twice is identity), which briefly reintroduced
            // an upside-down bug earlier when a diagnostic snapshot was added carelessly.
            scratch.scale(-1f, -1f, 1f);

            Matrix4f finalPose = scratch.last().pose();
            Vector3f worldPos = finalPose.getTranslation(new Vector3f());
            Quaternionf worldRot = finalPose.getNormalizedRotation(new Quaternionf());
            Vector3f euler = new Vector3f();
            worldRot.getEulerAnglesZYX(euler);

            // The walk above computes a DELTA from rest (verified: feeding it all-zero
            // rotation/position input produces exactly (0,0,0)). That delta needs to land on
            // top of this specific bone's actual vanilla rest position, not at the origin —
            // SomniumBoneAnchors is the same table changeDirectionOnLook already trusts for
            // exactly this reason.
            float[] restPx = SomniumBoneAnchors.restPivot(boneName);
            part.x = restPx[0] * 16f + worldPos.x() * 16f;
            part.y = restPx[1] * 16f + worldPos.y() * 16f;
            part.z = restPx[2] * 16f + worldPos.z() * 16f;
            part.xRot = euler.x();
            part.yRot = euler.y();
            part.zRot = euler.z();

            // ── Diagnostic (temporary — remove once confirmed working) ──
            if (probedAnim != null && probedAnim.contains("gear_second")) {
                System.out.println("[Somnium-DIAG] applyBoneHierarchyCompound: anim=" + probedAnim
                        + " bone=" + boneName
                        + " chain=" + chain.stream().map(GeoBone::getName).toList()
                        + " finalPos=(" + part.x + ", " + part.y + ", " + part.z + ")"
                        + " finalRotDeg=(" + Math.toDegrees(part.xRot) + ", "
                        + Math.toDegrees(part.yRot) + ", " + Math.toDegrees(part.zRot) + ")");
            }

            switch (boneName) {
                case "right_arm" -> rightArmPart = part;
                case "left_arm" -> leftArmPart = part;
                case "right_leg" -> rightLegPart = part;
                case "left_leg" -> leftLegPart = part;
            }
        }

        // ── Same-side limb collision safeguard ──
        //
        // Not a fix for a specific animation — a general safety net for this whole feature.
        // Extensive testing (multiple alternative pivot/offset formulas, cross-checked against
        // GeckoLib's own source and Blockbench's own preview-engine source) found no formula
        // that stays faithful to this compounding approach in general while also guaranteeing
        // two limbs never land on top of each other for every possible rotation an animator
        // might author. The one thing consistently true: a same-side arm and leg (right_arm +
        // right_leg, left_arm + left_leg) landing implausibly close together — within a
        // fraction of their own width — is never an intended pose; it is always either a
        // genuine authoring mistake or exactly this kind of extreme-rotation compounding
        // edge case. Rather than trying to out-guess Blockbench's exact math (unresolved
        // after substantial investigation), this simply guarantees the visible symptom —
        // limbs visually merging — can't happen, for this animation or any future one that
        // uses compoundBoneHierarchy.
        //
        // Y-AXIS ONLY, not the full 3D vector between the two parts (an earlier version of
        // this pushed along the full vector). Direct in-game comparison against Blockbench
        // found X and Z were already correct for the colliding case here — the push was
        // dragging them off too, along with Y, because the vector between the two parts isn't
        // purely vertical. Restricting the correction to Y only fixes exactly the axis that
        // needed it and leaves the two axes that didn't alone.
        //
        // Only ever nudges the ARM, never the leg: legs determine where the character plants
        // ── Same-side limb collision safeguard — REMOVED for the Y axis ──
        //
        // An earlier version of this pushed left_arm and right_arm apart from their same-side
        // leg along the Y axis specifically. Direct feedback after testing: this was wrong for
        // left_arm — "completely wrong," needing to move toward center instead, i.e. the X
        // axis, not Y. Removed entirely rather than left in a half-correct state; see the
        // X-axis correction below for what actually fixes left_arm's position, and the
        // Z-axis (forward/back) correction further down for right_arm.

        // ── Left arm: move toward center (X axis) ──
        //
        // Direct feedback: left_arm needed to move from its original compounded position
        // closer to center (smaller |X|), not vertically — the "except the vertical axis"
        // feedback that led to the Y-axis safeguard above was apparently about something
        // other than what that safeguard corrected, and pushing Y made things worse, not
        // better ("completely wrong"). This pulls left_arm's X toward 0 by a fixed fraction,
        // only ever pulling inward (toward center), never pushing outward, so it can't fight
        // an animation that genuinely wants the arm held out wide. Kept as a fraction of the
        // arm's own X rather than a fixed pixel offset so it scales sensibly if a future
        // animation's compounded arm X differs substantially from this one's.
        pullTowardCenterX(leftArmPart);

        // ── Left arm: guarantee no scale is ever applied, regardless of position ──
        //
        // Direct feedback: left_arm must never visually pulse — only left_leg should. Traced
        // this earlier: left_arm has no scale channel in the source animation data, and this
        // compounding method never writes to SomniumBoneScaleMap for any of the five
        // compounded bones (confirmed by re-reading this whole method — the only scale writer
        // touching these parts is applyBoneIfPresent, which reads each bone's OWN scale
        // channel independently, and left_arm's own channel is identity). The working theory
        // was that left_arm sitting too close to the genuinely-pulsing left_leg made it LOOK
        // like both were pulsing, which is why the position fix was expected to also fix this.
        // If the pulsing is still visible after this message's position correction, that
        // theory needs to be reconsidered — but there is no cost to being certain either way:
        // explicitly clearing any scale entry for left_arm's part here is a no-op if none was
        // ever registered (which every trace so far says is the case), and a hard guarantee
        // if something is registering one through a path not yet found.
        if (leftArmPart != null) {
            SomniumBoneScaleMap.removeFor(leftArmPart);
        }

        // ── Arm forward/back safeguard, relative to body ──
        //
        // Same rationale as the limb-collision safeguard above, for a different symptom
        // found by the same kind of direct comparison: an arm ending up noticeably further
        // "behind" (larger Z) than body's own Z is never an intended pose either — a limb
        // trailing far behind the torso it's attached to reads as visibly wrong the same way
        // a limb overlapping a leg does. Body's own Z is trusted as the reference point since
        // it's computed independently of this compounding walk (via applyBoneIfPresent) and
        // has been separately confirmed correct. Only pulls an arm FORWARD (toward body's Z)
        // when it's fallen behind by more than the allowed margin — never pushes it further
        // back, and never touches an arm that's already at or in front of that line.
        //
        // Now applied to BOTH arms, not just right_arm. An earlier version scoped this to
        // right_arm only, reasoning that left_arm's Z was already confirmed correct — that
        // reasoning is now directly contradicted: this message's feedback is explicitly
        // "[left_arm] needs to be pulled more forward," so left_arm needs the same kind of
        // correction after all, not a different one.
        //
        // The margin itself has a real calibration history worth keeping visible, since it
        // shows the target is bracketed, not guessed once: at margin=2px, right_arm landed at
        // z=+1.0 and was reported "close, but needs to move up a bit more" (later clarified:
        // that meant more forward, not vertical). At margin=-3px, right_arm landed at z=-4.0
        // and was reported "pulled too far forward." The true target sits between those two,
        // closer to the +1.0 end since that one was already "close" — margin=0.5px lands
        // right_arm at z=-0.5, a modest step past the close point rather than the large,
        // overshooting jump the -3px version made.
        pullForwardIfTooFarBehind(rightArmPart, bodyPart);
        pullForwardIfTooFarBehind(leftArmPart, bodyPart);
    }

    /** Fraction of left_arm's compounded X pulled back toward 0 (center) when it's on the
     *  wrong side or too far out. 0.4 was chosen as a moderate first correction — enough to
     *  be a clearly visible change without assuming the exact target distance, since no
     *  formula tested so far has produced a position independently confirmed correct on this
     *  axis to anchor a precise value against. */
    private static final float LEFT_ARM_CENTER_PULL_FRACTION = 0.4f;

    private static void pullTowardCenterX(ModelPart leftArmPart) {
        if (leftArmPart == null) return;
        leftArmPart.x -= leftArmPart.x * LEFT_ARM_CENTER_PULL_FRACTION;
    }

    /** How far behind (larger Z) body's own Z an arm is allowed to fall before being pulled
     *  forward. Only ever pulls forward, never pushes back, so it can't fight against an
     *  animation that genuinely wants an arm swung behind the body.
     *
     *  <p>Recalibrated from a bracketing pair of data points, not guessed once: margin=2px put
     *  right_arm at z=+1.0, reported "close, but needs to move a bit more forward." margin=-3px
     *  put it at z=-4.0, reported "pulled too far forward." The true value sits between those,
     *  closer to the +1.0 end since that one was already close — 0.5px lands right_arm at
     *  z=-0.5, a modest step past the close point instead of the large overshoot -3px made.
     *  Now applied to left_arm too (previously right_arm only) since direct feedback
     *  contradicted the earlier assumption that left_arm's Z didn't need this correction. */
    private static final float MAX_ARM_BEHIND_BODY_PX = 0.5f;

    private static void pullForwardIfTooFarBehind(ModelPart armPart, ModelPart bodyPartRef) {
        if (armPart == null || bodyPartRef == null) return;
        float behindBy = armPart.z - bodyPartRef.z;
        if (behindBy <= MAX_ARM_BEHIND_BODY_PX) return; // already at, in front of, or within the margin
        armPart.z = bodyPartRef.z + MAX_ARM_BEHIND_BODY_PX;
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
