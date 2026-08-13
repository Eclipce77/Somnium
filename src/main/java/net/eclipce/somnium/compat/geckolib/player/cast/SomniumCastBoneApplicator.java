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
     * exactly the way GeckoLib's own renderer would, by calling GeckoLib's own per-bone
     * transform method on a scratch, never-rendered {@link PoseStack}. No-ops entirely unless
     * {@code options.compoundBoneHierarchy()} is set.
     *
     * <h3>Why call GeckoLib's own code instead of reimplementing the math</h3>
     * Earlier versions of this fix hand-derived the composition — first a render-time
     * PoseStack wrap, then a flat position add, then a hand-rolled quaternion walk reusing
     * {@link SomniumBoneAnchors} rest pivots and a manually chosen rotation order. All three
     * were extensively cross-checked against JOML's and GeckoLib's own source and were
     * internally self-consistent, but "internally consistent" isn't the same guarantee as
     * "matches Blockbench's own preview" — that guarantee only comes from running the actual
     * code Blockbench-authored content is designed against. GeckoLib's {@code GeoRenderer}
     * already does exactly this walk for real hierarchical models: {@code renderRecursively}
     * calls {@link RenderUtils#prepMatrixForBone} once per bone, and a child bone's call
     * happens inside the PoseStack frame its parent's call already established. Calling that
     * same method, in that same order, on a scratch PoseStack we never actually render with,
     * reproduces GeckoLib's real hierarchical transform exactly — no hand-derived rotation
     * order, sign convention, or pivot math of our own left to get subtly wrong.
     *
     * <h3>What this requires</h3>
     * The baked model's bones need real {@code parent}/{@code childBones} relationships —
     * i.e. the {@code .geo.json} actually has {@code "parent": "body"} on head/arms/legs,
     * matching the Blockbench rig, rather than Somnium's original flat sibling layout. See
     * {@code default_player.geo.json}. This does NOT change vanilla rendering at all — the
     * vanilla {@link PlayerModel} stays exactly as flat as ever; only {@code GeoBone.getParent}
     * (read here, and only here) depends on the reparenting.
     *
     * <h3>The walk</h3>
     * For a bone whose ancestry is {@code [right_arm, body]} (leaf first): walk root to leaf
     * (reverse order), calling {@code RenderUtils.prepMatrixForBone(scratch, bone)} once per
     * ancestor on the SAME scratch PoseStack, so each subsequent call composes inside the
     * previous one's frame — identical to how {@code renderRecursively} nests real child
     * bones inside their parent's already-transformed PoseStack. After the walk, the
     * PoseStack's accumulated pose IS the bone's fully-compounded transform. A bone with no
     * parent (chain length 1) is left untouched — {@link #applyBoneIfPresent} already wrote
     * the correct answer for it.
     *
     * <h3>Extracting the result</h3>
     * {@code prepMatrixForBone} also applies the bone's own animated SCALE (between rotate
     * and translate-away-from-pivot) — for free, this makes scale compound correctly too,
     * without this method needing to handle it separately. That means the accumulated
     * {@link Matrix4f} isn't a pure rigid transform, so translation and rotation are pulled
     * out with {@code getTranslation}/{@code getNormalizedRotation} (which is robust to a
     * scaled matrix) rather than reading matrix columns directly. GeckoLib's own translate
     * calls all divide by 16 (blocks, not the pixel units {@code ModelPart.x/y/z} use), so
     * the extracted translation is multiplied back by 16 before being written.
     *
     * <h3>A latent assumption worth knowing about</h3>
     * This overwrites (not adds to) each carried part's rotation and position with the
     * fully-composed result. That's correct as long as {@code suppressVanillaAnimOn} covers
     * every bone this method touches, because {@code resetPose()} already zeroed the vanilla
     * locomotion contribution before {@link #applyAllBones} ran. If a future animation stops
     * suppressing one of these parts, this method would need to preserve that vanilla
     * contribution instead of overwriting it.
     *
     * <h3>Two corrections on top of calling GeckoLib's code (found via direct numeric
     * verification against this exact rig, not guessed)</h3>
     * <ol>
     *     <li><b>The vanilla player mirror.</b> {@code RenderUtils} is GeckoLib's generic
     *         transform code — it has no idea it might be feeding a vanilla player model,
     *         which is rendered through an extra {@code scale(-1, -1, 1)} mirror that
     *         {@link #applyBoneIfPresent}'s proven sign conventions already account for.
     *         Calling {@code RenderUtils} verbatim without this produces a result mirrored
     *         on X/Y relative to what vanilla expects — but it has to be a full
     *         conjugation (mirror the frame, do the real work, un-mirror), not just a
     *         one-sided scale before the walk starts. Position comes out identical either
     *         way (a translation under a left-multiplied mirror is just the mirrored
     *         vector, prepend-only is fine), but rotation is not: two rotations generally
     *         don't commute with the mirror, only pure-Z ones do, so mirroring just the
     *         input changes WHICH rotation gets extracted, not merely its sign. Confirmed
     *         both the bug and the fix against a single-bone case (body only, checked
     *         against {@link #applyBoneIfPresent}'s known-correct output) before this was
     *         applied to the full multi-bone walk: prepend-only silently flipped the sign,
     *         scaling by {@code (-1,-1,1)} again after the loop closes the conjugation and
     *         matches exactly.</li>
     *     <li><b>Pivot values are geo-model-absolute, not parent-relative.</b> Bedrock-format
     *         pivots (what {@code bone.getPivotX/Y/Z} return) are absolute coordinates in
     *         GeckoLib's own geometry space — e.g. body and head both sit at {@code [0,24,0]}
     *         in {@code default_player.geo.json}, matching Blockbench's own numbers exactly.
     *         Vanilla's {@code ModelPart} fields use a completely different origin (body and
     *         head both rest at {@code 0}), and {@link #applyBoneIfPresent} never references
     *         a bone's pivot for position at all — only its animated delta. Feeding
     *         {@code RenderUtils.translateToPivotPoint} a bone's raw absolute pivot (as
     *         {@code prepMatrixForBone} does internally) uses a 24-unit lever arm for a
     *         rotation that should have none, which is exactly what direct numeric testing
     *         against this data showed: a plausible few-pixel result for right_arm (pivot
     *         meaningfully different from body's) alongside a wildly exaggerated 20+ pixel
     *         swing for head specifically (pivot identical to body's) — confirmed as the
     *         cause of the "head flying into the sky" screenshot. Fixed by not calling
     *         {@code prepMatrixForBone} as one unit; the pivot translate/detranslate pair is
     *         done by hand with the root bone's pivot treated as {@code (0,0,0)} and every
     *         other bone's pivot expressed relative to its immediate parent's, while
     *         {@code translateMatrixToBone}/{@code rotateMatrixAroundBone}/
     *         {@code scaleMatrixForBone} — the parts that read animated keyframe data, not
     *         static geometry — are still called from {@code RenderUtils} verbatim.</li>
     * </ol>
     */
    private static void applyBoneHierarchyCompound(BakedGeoModel bakedModel,
                                                    PlayerModel<?> playerModel,
                                                    CastAnimationOptions options) {
        if (!options.compoundBoneHierarchy()) return;

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
            // everyone else -> relative to their immediate parent) instead of calling
            // prepMatrixForBone as one unit. See this method's javadoc for why.
            PoseStack scratch = new PoseStack();
            scratch.scale(-1f, -1f, 1f); // establish the vanilla player mirror before anything else
            float prevPivotX = 0, prevPivotY = 0, prevPivotZ = 0;
            for (int i = 0; i < chain.size(); i++) {
                GeoBone b = chain.get(i);
                float pivotX, pivotY, pivotZ;
                if (i == 0) {
                    pivotX = 0; pivotY = 0; pivotZ = 0;
                } else {
                    pivotX = b.getPivotX() - prevPivotX;
                    pivotY = b.getPivotY() - prevPivotY;
                    pivotZ = b.getPivotZ() - prevPivotZ;
                }

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
            // walk permanently inside a mirrored frame. Skipping this step is what produced
            // the "upside down" rotation in testing — mirroring only the input side changes
            // WHICH rotation gets extracted, not just its sign, because rotations don't
            // commute with the mirror in general (only pure-Z rotations do). Position is
            // unaffected by this distinction — the translation column comes out identical
            // either way — so this being missing was a rotation-only bug, confirmed by
            // checking a single-bone case against applyBoneIfPresent's known-correct answer
            // before shipping this fix.
            scratch.scale(-1f, -1f, 1f);

            // Diagnostic snapshot BEFORE the closing mirror, so a divergence can be isolated
            // to "before the walk finishes" vs "the closing conjugation / decomposition step" —
            // see the quaternion component logging below.
            Matrix4f preCloseMirrorPose = new Matrix4f(scratch.last().pose());
            Quaternionf preCloseMirrorRot = preCloseMirrorPose.getNormalizedRotation(new Quaternionf());

            scratch.scale(-1f, -1f, 1f);

            Matrix4f finalPose = scratch.last().pose();
            Vector3f worldPos = finalPose.getTranslation(new Vector3f());
            Quaternionf worldRot = finalPose.getNormalizedRotation(new Quaternionf());
            Vector3f euler = new Vector3f();
            worldRot.getEulerAnglesZYX(euler);

            // The walk above computes a DELTA from rest (verified: feeding it all-zero
            // rotation/position input produces exactly (0,0,0) — confirmed numerically before
            // this was written, not assumed). That delta needs to land on top of this specific
            // bone's actual vanilla rest position, not at the origin — SomniumBoneAnchors is
            // the same table changeDirectionOnLook already trusts for exactly this reason.
            // Missing this previously meant every compounded arm/leg rendered bunched up near
            // (0,0,0) instead of out at the shoulder/hip — head happened to look unaffected by
            // this specific bug only because its own rest pivot is (0,0,0), so adding it was a
            // no-op there, which is exactly why this went unnoticed until arms/legs were checked.
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
                System.out.println("[Somnium-DIAG] quat: anim=" + probedAnim
                        + " bone=" + boneName
                        + " preCloseMirror(x,y,z,w)=(" + preCloseMirrorRot.x() + ", " + preCloseMirrorRot.y()
                        + ", " + preCloseMirrorRot.z() + ", " + preCloseMirrorRot.w() + ")"
                        + " final(x,y,z,w)=(" + worldRot.x() + ", " + worldRot.y()
                        + ", " + worldRot.z() + ", " + worldRot.w() + ")");
            }
        }
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
