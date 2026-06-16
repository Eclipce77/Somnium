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

    // Per-part changeDirectionOnLook look-tilt. Stored as [pitchRad, pivotX, pivotY, pivotZ]
    // where pivot is the bone's REST pivot (the joint) in BLOCK units — NOT the animated pivot.
    // The animation mutates ModelPart.x/y/z to bake in its position keyframes, so by render time
    // those fields are restPivot + animationOffset; rotating about them pivots the limb around a
    // displaced point in front of the body (the bug where the arm detached from the shoulder).
    // We rotate about the supplied rest pivot instead, so the joint stays fixed and the whole
    // animation tilts about the true shoulder/hip.
    private static final Map<ModelPart, float[]> PITCH_MAP = new IdentityHashMap<>();

    /**
     * Registers (or multiplies into) a scale for the given {@link ModelPart} this frame.
     *
     * <p>A scale of exactly {@code [1,1,1]} is a no-op. Existing scale for the part this frame
     * is multiplied into, so multiple animations compose.</p>
     */
    public static void setScale(ModelPart part, float sx, float sy, float sz) {
        setScale(part, sx, sy, sz, 0f, 0f, 0f);
    }

    /**
     * As {@link #setScale(ModelPart, float, float, float)} but scales about an explicit anchor
     * point (in the part's local model units) instead of the part origin/pivot.
     *
     * <p>Why this matters: vanilla limb cubes are not centred on their pivot. The arm cube spans
     * local Y from -2 (a 2-unit shoulder cap ABOVE the pivot) to +10 (down the arm). Scaling Y
     * about the pivot (y=0) magnifies that 2-unit overhang — at 32x it juts 64 units up past the
     * shoulder, so the back of a stretched arm lifts off the body. Anchoring the scale at the
     * cube's top face (y=-2 → anchor (0,-2,0)) keeps the shoulder end planted and stretches the
     * limb only away from the joint, matching how the authored clips sit flush.</p>
     */
    public static void setScale(ModelPart part, float sx, float sy, float sz,
                                float ax, float ay, float az) {
        if (sx == 1f && sy == 1f && sz == 1f) return;
        float[] existing = SCALE_MAP.get(part);
        if (existing != null) {
            existing[0] *= sx;
            existing[1] *= sy;
            existing[2] *= sz;
            // Anchor is a property of the stretch, not multiplicative — last writer sets it.
            existing[3] = ax;
            existing[4] = ay;
            existing[5] = az;
        } else {
            SCALE_MAP.put(part, new float[]{sx, sy, sz, ax, ay, az});
        }
    }

    /**
     * Registers the changeDirectionOnLook pitch (radians) for a part this frame, to be applied
     * by the mixin as a rotation about {@code (restPivotX, restPivotY, restPivotZ)} — the bone's
     * REST joint position in BLOCK units (i.e. vanilla pivot / 16), captured before the animation
     * displaced it. A pitch of 0 is a no-op. Last writer wins (one look-tilt per bone).
     */
    public static void setLookPitch(ModelPart part, float pitchRad,
                                    float restPivotX, float restPivotY, float restPivotZ) {
        if (pitchRad == 0f) return;
        PITCH_MAP.put(part, new float[]{pitchRad, restPivotX, restPivotY, restPivotZ});
    }

    /** Look-tilt data [pitchRad, pivotX, pivotY, pivotZ] for this part, or null if none. */
    @Nullable
    public static float[] getLookPitch(ModelPart part) {
        return PITCH_MAP.get(part);
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