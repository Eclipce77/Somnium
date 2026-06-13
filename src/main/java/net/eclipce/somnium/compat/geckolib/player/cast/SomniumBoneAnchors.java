package net.eclipce.somnium.compat.geckolib.player.cast;

/**
 * Vanilla REST pivot positions for player body parts, in BLOCK units (vanilla pivot / 16),
 * used by {@code changeDirectionOnLook} as the point to rotate the limb about.
 *
 * <h3>Why rest pivots, and why a fixed table</h3>
 * <p>changeDirectionOnLook tilts the whole animation about the joint. The rotation must happen
 * about the bone's REST pivot (the shoulder/hip), but by render time the animation has mutated
 * {@code ModelPart.x/y/z} to bake in its position keyframes — so those fields point in front of
 * the body, not at the joint. Rotating about them detaches the limb. The rest pivot is a fixed
 * property of the vanilla rig, so we read it from this table instead of the live (displaced)
 * fields.</p>
 *
 * <h3>Values</h3>
 * <p>From HumanoidModel's part definitions (1.20.1), divided by 16 to block units:</p>
 * <ul>
 *   <li>right arm pivot (-5, 2, 0)</li>
 *   <li>left arm pivot ( 5, 2, 0)</li>
 *   <li>right leg pivot (-1.9, 12, 0)</li>
 *   <li>left leg pivot ( 1.9, 12, 0)</li>
 *   <li>head/body pivot (0, 0, 0)</li>
 * </ul>
 *
 * <p>If a future rig uses different pivots, this is the one place to adjust. Returned arrays
 * must not be mutated by callers (they are shared).</p>
 */
public final class SomniumBoneAnchors {

    private static final float[] RIGHT_ARM = { -5f / 16f, 2f / 16f, 0f };
    private static final float[] LEFT_ARM  = {  5f / 16f, 2f / 16f, 0f };
    private static final float[] RIGHT_LEG = { -1.9f / 16f, 12f / 16f, 0f };
    private static final float[] LEFT_LEG  = {  1.9f / 16f, 12f / 16f, 0f };
    private static final float[] ZERO      = { 0f, 0f, 0f };

    /**
     * Returns the rest pivot {x, y, z} (block units) for the given bone name. Never null;
     * returns {0,0,0} for head/body/unknown (rotating about origin is fine for those).
     */
    public static float[] restPivot(String boneName) {
        if (boneName == null) return ZERO;
        return switch (boneName) {
            case "right_arm" -> RIGHT_ARM;
            case "left_arm"  -> LEFT_ARM;
            case "right_leg" -> RIGHT_LEG;
            case "left_leg"  -> LEFT_LEG;
            default          -> ZERO;
        };
    }

    private SomniumBoneAnchors() {}
}