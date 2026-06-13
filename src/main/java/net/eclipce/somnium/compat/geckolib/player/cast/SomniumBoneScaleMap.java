package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.client.model.geom.ModelPart;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Frame-local store of per-{@link ModelPart} scale values to be applied during rendering.
 *
 * <p>{@link SomniumCastBoneApplicator} writes scale values here after processing a GeckoLib
 * animation frame. {@link net.eclipce.somnium.compat.geckolib.mixin.ModelPartRenderMixin} reads
 * them back inside {@code ModelPart.render()} and calls {@code PoseStack.scale()} at the
 * correct moment — after the PoseStack is positioned at the bone's pivot but before any
 * geometry is drawn.</p>
 *
 * <h3>Why IdentityHashMap?</h3>
 * <p>Each player entity has its own {@code PlayerModel} with its own {@code ModelPart} object
 * references. Keying by reference ({@code IdentityHashMap}) ensures that scale data written
 * for player A's {@code rightArm} never accidentally matches player B's {@code rightArm},
 * even if those parts were somehow considered {@code equals()} by value.</p>
 *
 * <h3>Why multiplicative?</h3>
 * <p>Vanilla does not apply any scale during {@code setupAnim()} — scale is always 1.0 by
 * default, unlike rotation which vanilla actively sets each frame. Multiplying means a bone
 * with scale {@code [1, 2, 1]} doubles the arm length regardless of locomotion state. It also
 * means two animations both contributing scale to the same bone compose naturally:
 * {@code 2.0 × 0.5 = 1.0} (they cancel), whereas addition would give {@code 2.0 + 0.5 = 2.5}
 * (incorrect).</p>
 *
 * <h3>Lifecycle</h3>
 * <p>{@link #clearAll()} is called at the start of every {@code SomniumCastBoneApplicator.apply()}
 * invocation. Because Minecraft renders each entity's model to completion before starting the next,
 * this guarantees a clean slate for every player without risk of stale data carrying over.</p>
 */
public final class SomniumBoneScaleMap {

    // Per-part multiplicative scale [sx, sy, sz], applied by ModelPartRenderMixin AFTER
    // translateAndRotate (pivot-centred, as vanilla scale works). Composes across animations.
    private static final Map<ModelPart, float[]> SCALE_MAP = new IdentityHashMap<>();

    // Per-part changeDirectionOnLook pitch (radians, X axis). Applied by ModelPartRenderMixin
    // BEFORE translateAndRotate as a rotation about the bone's OWN pivot, so it pre-tilts the
    // frame the whole animation then plays inside — angling the entire authored animation
    // (position, rotation, scale) up/down about the joint while keeping the pivot fixed and the
    // animation visually intact. Stored separately from scale because the two are applied at
    // different points in the render (pitch before the bone transforms, scale after).
    private static final Map<ModelPart, Float> PITCH_MAP = new IdentityHashMap<>();

    /**
     * Registers (or multiplies into) a scale for the given {@link ModelPart} this frame.
     *
     * <p>A scale of exactly {@code [1,1,1]} is a no-op. Existing scale for the part this frame
     * is multiplied into, so multiple animations compose.</p>
     */
    public static void setScale(ModelPart part, float sx, float sy, float sz) {
        if (sx == 1f && sy == 1f && sz == 1f) return;
        float[] existing = SCALE_MAP.get(part);
        if (existing != null) {
            existing[0] *= sx;
            existing[1] *= sy;
            existing[2] *= sz;
        } else {
            SCALE_MAP.put(part, new float[]{sx, sy, sz});
        }
    }

    /**
     * Registers the changeDirectionOnLook pitch (radians) for a part this frame. The mixin
     * rotates the frame by this about the bone's own pivot before the bone draws, tilting the
     * whole animation up/down. A pitch of 0 is a no-op. Last writer wins (one look-tilt per bone).
     */
    public static void setLookPitch(ModelPart part, float pitchRad) {
        if (pitchRad == 0f) return;
        PITCH_MAP.put(part, pitchRad);
    }

    /** Pitch (radians) registered for this part this frame, or 0 if none. */
    public static float getLookPitch(ModelPart part) {
        Float p = PITCH_MAP.get(part);
        return p != null ? p : 0f;
    }

    /**
     * Returns the registered scale for this part, or {@code null} if none is registered
     * (implying identity scale — no scale should be applied).
     *
     * <p>Called every frame by {@link net.eclipce.somnium.compat.geckolib.mixin.ModelPartRenderMixin}
     * for every rendered {@link ModelPart}. The null check lets the mixin return immediately
     * for the vast majority of parts that have no pending scale.</p>
     */
    @Nullable
    public static float[] getScale(ModelPart part) {
        return SCALE_MAP.get(part);
    }

    /**
     * Removes all registered scale entries.
     *
     * <p>Called at the start of each {@code SomniumCastBoneApplicator.apply()} invocation
     * so that scale data from the previous rendered player does not carry forward.</p>
     */
    public static void clearAll() {
        SCALE_MAP.clear();
        PITCH_MAP.clear();
    }

    /**
     * Removes the registered scale (if any) for a single {@link ModelPart}.
     *
     * <p>Used when an option flag (e.g. {@code showInFirstPerson = false}) requires us
     * to skip the scale contribution for a specific part within the same frame the rest of
     * the player keeps its scale. Idempotent — calling on a part with no entry is a no-op.</p>
     */
    public static void removeFor(ModelPart part) {
        SCALE_MAP.remove(part);
        PITCH_MAP.remove(part);
    }

    private SomniumBoneScaleMap() {}
}