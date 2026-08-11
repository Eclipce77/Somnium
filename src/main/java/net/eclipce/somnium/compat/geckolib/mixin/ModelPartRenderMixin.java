package net.eclipce.somnium.compat.geckolib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumBoneScaleMap;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link ModelPart#render} to apply per-bone scale from
 * {@link SomniumBoneScaleMap} at precisely the right point in the render pipeline.
 *
 * <h3>Why not a field?</h3>
 * <p>{@code ModelPart} has no scale field — vanilla always applies scale by calling
 * {@code PoseStack.scale()} around specific render calls, never by storing a value.
 * Adding scale support therefore requires intercepting the PoseStack mid-render.</p>
 *
 * <h3>Injection point</h3>
 * <p>The injection fires immediately after {@code translateAndRotate(PoseStack)} returns.
 * At that moment the PoseStack has been pushed and is positioned at the bone's pivot with
 * its rotation applied — exactly the point at which {@code poseStack.scale()} will expand
 * or contract the bone's geometry outward from its pivot. Injecting here ensures scale
 * applies to this bone's cubes and all its children before they are compiled to the buffer.</p>
 *
 * <h3>Cost</h3>
 * <p>This injection runs for every {@code ModelPart.render()} call in the game — armor,
 * items, block entities, mobs, everything. The hot path is a single {@code IdentityHashMap}
 * lookup followed by a null check, which returns immediately for the overwhelming majority
 * of parts that have no registered scale. The {@code PoseStack.scale()} call only executes
 * for parts that Somnium's bone applicator has explicitly written data for.</p>
 */
@Mixin(ModelPart.class)
public class ModelPartRenderMixin {

    // ── Body-rotation compounding: wrap the frame in body's own translation + rotation,
    // BEFORE the bone's own translateAndRotate runs ──
    //
    // Opted into per-animation via CastAnimationOptions#propagateBodyTransform (see that
    // field's javadoc). This is what makes a part behave as a real child of body instead of
    // an independent sibling: body's rest pivot is (0,0,0) (SomniumBoneAnchors), so this
    // reduces to "translate by body's own position delta, then rotate about the origin by
    // body's own rotation" — every subsequent transform this part applies (its own authored
    // position/rotation keyframes, written directly onto its ModelPart fields exactly as
    // before) plays out INSIDE this already-shifted-and-rotated frame, so the part's pivot
    // gets carried through space by body's rotation the same way a true parent-child bone
    // hierarchy would, instead of only reacting to its own independently-authored keyframes.
    //
    // Declared before somnium$applyLookPitch so it acts as the outermost wrap if a part ever
    // needed both (it doesn't today — transformation animations don't use
    // changeDirectionOnLook, see CastAnimationOptions#propagateBodyTransform's javadoc — but
    // this ordering is the more correct one if that ever changes: the "which parent frame
    // am I in" wrap should sit outside a "which way am I personally aiming" tilt).
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void somnium$applyBodyRotationCompound(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red, float green, float blue, float alpha,
            CallbackInfo ci) {

        float[] compound = SomniumBoneScaleMap.getBodyRotationCompound((ModelPart) (Object) this);
        if (compound == null) return;

        float rotXRad = compound[0];
        float dx = compound[1], dy = compound[2], dz = compound[3];

        // Body's rest pivot is (0,0,0) (SomniumBoneAnchors), so there's no separate
        // translate-to-pivot / translate-back pair needed here — unlike the look-pitch wrap
        // below, which pivots about a nonzero joint. Order matters: translate first (an outer
        // shift by body's own position delta), then rotate about the origin, so the rotation
        // still happens about body's true rest position rather than the already-shifted point.
        if (dx != 0f || dy != 0f || dz != 0f) {
            poseStack.translate(dx, dy, dz);
        }
        if (rotXRad != 0f) {
            poseStack.mulPose(Axis.XP.rotation(rotXRad));
        }
    }

    // ── changeDirectionOnLook: pre-tilt the frame about the bone's pivot, BEFORE the bone's
    // own translateAndRotate runs ──
    //
    // This is the whole-animation aim. At BEFORE the translateAndRotate invoke the PoseStack is
    // still in the parent frame. We rotate the frame by the look pitch about the bone's OWN
    // pivot (its x/y/z), then let vanilla's translateAndRotate + the bone's position/rotation +
    // our scale all run normally inside that tilted frame. Because we rotate about the pivot,
    // the pivot stays fixed (the limb stays attached at the shoulder/hip), and because the tilt
    // is applied to the frame rather than mixed into any bone channel, the entire authored
    // animation — position, rotation, scale — plays exactly as intended, just angled up/down.
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void somnium$applyLookPitch(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red, float green, float blue, float alpha,
            CallbackInfo ci) {

        float[] tilt = SomniumBoneScaleMap.getLookPitch((ModelPart) (Object) this);
        if (tilt == null) return;

        float pitchRad = tilt[0];
        // Rest pivot (the joint), already in BLOCK units. We must NOT use self.x/y/z here —
        // the animation has mutated those to bake in its position keyframes, so they point in
        // FRONT of the body, and rotating about them detaches the limb from the shoulder.
        float px = tilt[1];
        float py = tilt[2];
        float pz = tilt[3];

        // Rotate the frame about the true joint: translate to rest pivot, rotate X, translate back.
        poseStack.translate(px, py, pz);
        poseStack.mulPose(Axis.XP.rotation(pitchRad));
        poseStack.translate(-px, -py, -pz);
    }

    // ── Scale: applied AFTER translateAndRotate, pivot-centred, exactly as before ──
    @Inject(
            // Must specify the full descriptor to disambiguate from the 4-parameter overload
            // render(PoseStack, VertexConsumer, int, int) — which only delegates and never
            // calls translateAndRotate directly. The 8-parameter version is the one that
            // contains the actual rendering logic including the translateAndRotate call.
            // Note: CallbackInfo is NOT included in the descriptor — it is Mixin's own addition.
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void somnium$applyBoneScale(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red, float green, float blue, float alpha,
            CallbackInfo ci) {

        // getScale() returns null for the vast majority of parts — cheap IdentityHashMap lookup.
        // No GeckoLibCompat.isLoaded() check needed: if GeckoLib is absent, nothing ever
        // writes to SomniumBoneScaleMap, so this always returns null and exits immediately.
        float[] scale = SomniumBoneScaleMap.getScale((ModelPart) (Object) this);
        if (scale == null) return;

        // Scale about the anchor (scale[3..5], local model units) rather than the part origin.
        // For an unset anchor this is (0,0,0) and reduces to a plain poseStack.scale(). Anchoring
        // at e.g. the arm's shoulder-cap face keeps a stretched limb planted at the joint instead
        // of jutting back past it (vanilla limb cubes aren't centred on their pivot).
        float ax = scale[3], ay = scale[4], az = scale[5];
        if (ax != 0f || ay != 0f || az != 0f) {
            poseStack.translate(ax, ay, az);
            poseStack.scale(scale[0], scale[1], scale[2]);
            poseStack.translate(-ax, -ay, -az);
        } else {
            poseStack.scale(scale[0], scale[1], scale[2]);
        }
    }
}