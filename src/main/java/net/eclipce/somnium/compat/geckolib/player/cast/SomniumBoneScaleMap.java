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

    // Stored value per part: [sx, sy, sz, ax, ay, az]
    //   s* = scale factors (multiplicative, compose across animations in a frame)
    //   a* = anchor offset (model units) from the bone pivot to the point that should stay
    //        FIXED while scaling. The mixin scales about this point instead of the pivot, so
    //        e.g. the shoulder end of an arm stays put while the arm stretches toward the hand
    //        rather than ballooning off the shoulder. Anchor does NOT compose multiplicatively
    //        — the last writer's anchor wins (anchors are a property of the bone geometry, not
    //        the animation, so all writers for the same bone should pass the same one anyway).
    private static final Map<ModelPart, float[]> SCALE_MAP = new IdentityHashMap<>();

    /**
     * Registers (or multiplies into) a scale for the given {@link ModelPart} this frame, with
     * no anchor (scales about the pivot). Equivalent to {@link #setScale(ModelPart, float,
     * float, float, float, float, float)} with a zero anchor.
     */
    public static void setScale(ModelPart part, float sx, float sy, float sz) {
        setScale(part, sx, sy, sz, 0f, 0f, 0f);
    }

    /**
     * Registers a scale plus an anchor for the given {@link ModelPart} this frame.
     *
     * <p>Scale factors multiply into any existing entry (so multiple animations compose);
     * the anchor is stored as-is (last writer wins — see the field comment). A scale of
     * exactly {@code [1,1,1]} with a zero anchor is a no-op and is not stored.</p>
     *
     * @param part the ModelPart instance — must be the actual object from this frame's PlayerModel
     * @param sx X scale (1 = none) @param sy Y scale @param sz Z scale
     * @param ax anchor X offset from pivot (model units) @param ay anchor Y @param az anchor Z
     */
    public static void setScale(ModelPart part, float sx, float sy, float sz,
                                float ax, float ay, float az) {
        if (sx == 1f && sy == 1f && sz == 1f && ax == 0f && ay == 0f && az == 0f) return;
        float[] existing = SCALE_MAP.get(part);
        if (existing != null) {
            existing[0] *= sx;
            existing[1] *= sy;
            existing[2] *= sz;
            // Anchor: keep a non-zero anchor if one is supplied; don't let a later zero-anchor
            // writer wipe a real anchor set earlier this frame.
            if (ax != 0f || ay != 0f || az != 0f) {
                existing[3] = ax;
                existing[4] = ay;
                existing[5] = az;
            }
        } else {
            SCALE_MAP.put(part, new float[]{sx, sy, sz, ax, ay, az});
        }
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
    }

    private SomniumBoneScaleMap() {}
}