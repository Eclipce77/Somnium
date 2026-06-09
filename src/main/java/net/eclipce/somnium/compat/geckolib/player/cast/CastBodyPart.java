package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Locale;

/**
 * Type-safe identifier for one of a player's base-body {@link ModelPart}s.
 *
 * <p>Used wherever the public {@link CastAnimationOptions} API previously took a raw
 * {@code String} part name — {@code suppressVanillaAnimOn}, {@code hideBodyPart},
 * {@code followPlayerBody}. The IDE's autocomplete is the typo-defence: addon authors
 * can only pass a value from this enum, so {@code "right_srm"} or {@code "RIGHT_ARM"}
 * as one comma-joined varargs string can't happen.</p>
 *
 * <h3>Mapping to the vanilla model</h3>
 * <ul>
 *   <li>{@link #HEAD} → {@code HumanoidModel.head}</li>
 *   <li>{@link #BODY} → {@code HumanoidModel.body}</li>
 *   <li>{@link #RIGHT_ARM} / {@link #LEFT_ARM} → {@code HumanoidModel.rightArm} / {@code .leftArm}</li>
 *   <li>{@link #RIGHT_LEG} / {@link #LEFT_LEG} → {@code HumanoidModel.rightLeg} / {@code .leftLeg}</li>
 * </ul>
 *
 * <p>The overlay layers ({@code hat}, {@code jacket}, {@code sleeves}, {@code pants})
 * live on a separate enum, {@link CastLayer}. They're kept separate because the API
 * intent differs — base parts are what an animation actually moves, layers are
 * cosmetic overlays that follow their base part automatically.</p>
 */
public enum CastBodyPart {
    HEAD      { @Override public ModelPart get(PlayerModel<?> m) { return m.head; } },
    BODY      { @Override public ModelPart get(PlayerModel<?> m) { return m.body; } },
    RIGHT_ARM { @Override public ModelPart get(PlayerModel<?> m) { return m.rightArm; } },
    LEFT_ARM  { @Override public ModelPart get(PlayerModel<?> m) { return m.leftArm; } },
    RIGHT_LEG { @Override public ModelPart get(PlayerModel<?> m) { return m.rightLeg; } },
    LEFT_LEG  { @Override public ModelPart get(PlayerModel<?> m) { return m.leftLeg; } };

    /** Resolves this enum to the concrete {@link ModelPart} on the given player model. */
    public abstract ModelPart get(PlayerModel<?> model);

    /**
     * The lowercase bone name used in {@code .geo.json} files and in
     * {@code SomniumPlayerBoneMap.getPart(model, String)}. Matches the GeckoLib bone
     * identifier convention: {@code "right_arm"}, {@code "head"}, etc.
     */
    public String partName() {
        return name().toLowerCase(Locale.ROOT);
    }
}