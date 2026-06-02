package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

import javax.annotation.Nullable;

/**
 * Maps GeckoLib bone names from a cast animation model onto the corresponding
 * vanilla {@link ModelPart} fields of a {@link PlayerModel}.
 *
 * <p>Bone names are matched case-insensitively and accept both underscore and
 * camelCase forms (e.g. {@code "right_arm"} and {@code "RightArm"} both map
 * to {@code PlayerModel.rightArm}).</p>
 *
 * <h3>Supported bone names</h3>
 * <table>
 *   <tr><th>Geo bone</th><th>Vanilla field</th><th>Notes</th></tr>
 *   <tr><td>head</td><td>HumanoidModel.head</td><td>Main head part</td></tr>
 *   <tr><td>hat</td><td>HumanoidModel.hat</td><td>Head overlay (outer skin layer)</td></tr>
 *   <tr><td>body</td><td>HumanoidModel.body</td><td>Torso</td></tr>
 *   <tr><td>jacket</td><td>PlayerModel.jacket</td><td>Body overlay layer</td></tr>
 *   <tr><td>right_arm</td><td>HumanoidModel.rightArm</td><td>Inner right arm; wide=4px, slim=3px</td></tr>
 *   <tr><td>right_sleeve</td><td>PlayerModel.rightSleeve</td><td>Right arm overlay layer</td></tr>
 *   <tr><td>left_arm</td><td>HumanoidModel.leftArm</td><td>Inner left arm</td></tr>
 *   <tr><td>left_sleeve</td><td>PlayerModel.leftSleeve</td><td>Left arm overlay layer</td></tr>
 *   <tr><td>right_leg</td><td>HumanoidModel.rightLeg</td><td>Inner right leg</td></tr>
 *   <tr><td>right_pants</td><td>PlayerModel.rightPants</td><td>Right leg overlay layer</td></tr>
 *   <tr><td>left_leg</td><td>HumanoidModel.leftLeg</td><td>Inner left leg</td></tr>
 *   <tr><td>left_pants</td><td>PlayerModel.leftPants</td><td>Left leg overlay layer</td></tr>
 * </table>
 */
public final class SomniumPlayerBoneMap {

    /**
     * Returns the vanilla {@link ModelPart} corresponding to the given GeckoLib bone name,
     * or {@code null} if the name is not recognised.
     *
     * @param model    the player model instance being rendered this frame
     * @param boneName the bone name from the animation's {@code .geo.json}
     */
    @Nullable
    public static ModelPart getPart(PlayerModel<?> model, String boneName) {
        // Normalise to lowercase with underscores so both "RightArm" and "right_arm" match
        String key = boneName.toLowerCase().replace(" ", "_");

        // Check PlayerModel-specific overlay parts first
        ModelPart overlay = getOverlayPart(model, key);
        if (overlay != null) return overlay;

        // Then check base HumanoidModel parts
        return getHumanoidPart(model, key);
    }

    @Nullable
    private static ModelPart getHumanoidPart(HumanoidModel<?> model, String key) {
        return switch (key) {
            case "head"                        -> model.head;
            case "hat"                         -> model.hat;
            case "body"                        -> model.body;
            case "rightarm",  "right_arm"      -> model.rightArm;
            case "leftarm",   "left_arm"       -> model.leftArm;
            case "rightleg",  "right_leg"      -> model.rightLeg;
            case "leftleg",   "left_leg"       -> model.leftLeg;
            default                            -> null;
        };
    }

    @Nullable
    private static ModelPart getOverlayPart(PlayerModel<?> model, String key) {
        return switch (key) {
            case "jacket"                             -> model.jacket;
            case "rightsleeve", "right_sleeve"        -> model.rightSleeve;
            case "leftsleeve",  "left_sleeve"         -> model.leftSleeve;
            case "rightpants",  "right_pants"         -> model.rightPants;
            case "leftpants",   "left_pants"          -> model.leftPants;
            default                                   -> null;
        };
    }

    private SomniumPlayerBoneMap() {}
}