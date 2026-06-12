package net.eclipce.somnium.compat.geckolib.player.cast;

/**
 * Per-bone anchor offsets for scale anchoring, used by {@link SomniumCastBoneApplicator} when
 * it registers a scaled bone and applied by {@code ModelPartRenderMixin} when it scales.
 *
 * <h3>What an anchor is</h3>
 * <p>{@code PoseStack.scale()} expands a bone's geometry about the current origin, which sits
 * at the bone's pivot. Vanilla limb cubes hang off their pivot rather than being centred on
 * it, so a large stretch pushes both ends away from the pivot — e.g. an arm's shoulder end
 * lifts off the shoulder while the arm balloons. The anchor is the offset (in model units,
 * 1/16 block) from the pivot to the point that should stay FIXED during scaling; the mixin
 * scales about that point instead of the pivot.</p>
 *
 * <h3>Why per-bone-type</h3>
 * <p>Each bone's pivot-to-cube geometry differs. The arm pivots at the shoulder with the cube
 * hanging down; the leg pivots at the hip with the cube hanging down; the body is roughly
 * centred. So the "pin the back/top end" anchor differs per bone. Values here pin the
 * shoulder/hip (top) end so a stretched limb grows away from the joint toward its far end,
 * keeping the back of the limb attached to the body.</p>
 *
 * <h3>Tuning</h3>
 * <p>These are expressed in the bone's local render space (after {@code translateAndRotate},
 * i.e. post-pivot, post-rotation). The dominant axis for limb stretch is Y (limb length). If a
 * stretched limb still floats off or sinks into the joint, adjust that bone's Y here: more
 * positive pins lower down the limb, more negative pins higher up. X/Z are 0 for the standard
 * rig (limbs stretch along their length, not sideways) but are available for abilities that
 * scale on those axes.</p>
 *
 * <p>Bone names match the geo/vanilla mapping used elsewhere: {@code head, body, right_arm,
 * left_arm, right_leg, left_leg}.</p>
 */
public final class SomniumBoneAnchors {

    // ── Per-bone anchor offsets {x, y, z} in model units ──
    //
    // Arms: pivot is at the shoulder; the cube extends ~12 units down to the hand. To pin the
    // shoulder (back) end, anchor near the top of the cube. ~ -2 in the part's local Y places
    // the fixed point at the shoulder face so the stretch runs from shoulder toward the hand.
    // (If the arm stretches the wrong way or detaches, this Y is the value to adjust.)
    private static final float[] ARM  = { 0f, -2f, 0f };

    // Legs: pivot is at the hip; cube extends down to the foot. Same idea as the arm — pin the
    // hip (top) end so a stretched leg grows toward the foot.
    private static final float[] LEG  = { 0f, -2f, 0f };

    // Body: roughly centred on its pivot; scaling about the pivot is usually fine. Zero anchor
    // (pivot-centred) unless a specific ability needs otherwise.
    private static final float[] BODY = { 0f, 0f, 0f };

    // Head: pivot at the neck; small enough that pivot-centred scaling is fine.
    private static final float[] HEAD = { 0f, 0f, 0f };

    private static final float[] ZERO = { 0f, 0f, 0f };

    /**
     * Returns the anchor {x, y, z} for the given bone name. Never null; returns a zero anchor
     * (pivot-centred scaling) for unrecognised bones. The returned array must not be mutated by
     * callers (it is shared) — callers only read it.
     */
    public static float[] forBone(String boneName) {
        if (boneName == null) return ZERO;
        return switch (boneName) {
            case "right_arm", "left_arm" -> ARM;
            case "right_leg", "left_leg" -> LEG;
            case "body"                  -> BODY;
            case "head"                  -> HEAD;
            default                      -> ZERO;
        };
    }

    private SomniumBoneAnchors() {}
}