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

        float sx = scale[0], sy = scale[1], sz = scale[2];
        float ax = scale[3], ay = scale[4], az = scale[5];
        float pitchRad = scale[6];

        boolean hasScale  = (sx != 1f || sy != 1f || sz != 1f);
        boolean hasPitch  = (pitchRad != 0f);
        boolean hasAnchor = (ax != 0f || ay != 0f || az != 0f);

        // Fast path: plain pivot-centred scale, no anchor, no look-pitch.
        if (!hasPitch && !hasAnchor) {
            if (hasScale) poseStack.scale(sx, sy, sz);
            return;
        }

        // ── Anchored transform: rotate (look-pitch) and scale ABOUT THE ANCHOR ──
        // After translateAndRotate the origin sits at the bone pivot with the bone's own
        // animated position+rotation applied. We move to the anchor (shoulder/hip), apply the
        // changeDirectionOnLook pitch as an OUTER X rotation that wraps the whole posed bone,
        // then the scale, then move back — so the entire animated + scaled limb tilts up/down
        // about the joint while the anchored end stays pinned to the body. Doing the pitch
        // here (not as a bone xRot) is what stops it from fighting the anchor and throwing the
        // arm away from the player.
        poseStack.translate(ax, ay, az);
        if (hasPitch) {
            poseStack.mulPose(Axis.XP.rotation(pitchRad));
        }
        if (hasScale) {
            poseStack.scale(sx, sy, sz);
        }
        poseStack.translate(-ax, -ay, -az);
    }
}