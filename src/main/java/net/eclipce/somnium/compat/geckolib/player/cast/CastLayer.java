package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Locale;

/**
 * Type-safe identifier for one of a player's skin overlay layers.
 *
 * <p>Used wherever the {@link CastAnimationOptions} API takes a layer name — currently
 * only {@code hideLayer}. As with {@link CastBodyPart}, the IDE's autocomplete makes
 * typos impossible at the call site.</p>
 *
 * <h3>Why a separate enum from {@link CastBodyPart}</h3>
 * <p>Layers are the second skin texture rendered slightly offset from the base parts —
 * the hat brim, jacket, sleeves, and pant overlays. They follow their base part's pose
 * via {@code copyFrom} every frame, so they don't get animated independently; their
 * only meaningful API operation is "hide / don't hide". Keeping them off
 * {@link CastBodyPart} prevents nonsense like {@code suppressVanillaAnimOn(HAT)} from
 * being syntactically valid.</p>
 *
 * <h3>Mapping to the vanilla model</h3>
 * <ul>
 *   <li>{@link #HAT}          → {@code HumanoidModel.hat}</li>
 *   <li>{@link #JACKET}       → {@code PlayerModel.jacket}</li>
 *   <li>{@link #RIGHT_SLEEVE} → {@code PlayerModel.rightSleeve}</li>
 *   <li>{@link #LEFT_SLEEVE}  → {@code PlayerModel.leftSleeve}</li>
 *   <li>{@link #RIGHT_PANTS}  → {@code PlayerModel.rightPants}</li>
 *   <li>{@link #LEFT_PANTS}   → {@code PlayerModel.leftPants}</li>
 * </ul>
 */
public enum CastLayer {
    HAT          { @Override public ModelPart get(PlayerModel<?> m) { return m.hat; } },
    JACKET       { @Override public ModelPart get(PlayerModel<?> m) { return m.jacket; } },
    RIGHT_SLEEVE { @Override public ModelPart get(PlayerModel<?> m) { return m.rightSleeve; } },
    LEFT_SLEEVE  { @Override public ModelPart get(PlayerModel<?> m) { return m.leftSleeve; } },
    RIGHT_PANTS  { @Override public ModelPart get(PlayerModel<?> m) { return m.rightPants; } },
    LEFT_PANTS   { @Override public ModelPart get(PlayerModel<?> m) { return m.leftPants; } };

    /** Resolves this enum to the concrete {@link ModelPart} on the given player model. */
    public abstract ModelPart get(PlayerModel<?> model);

    /** The lowercase layer name used in {@code .geo.json} files. */
    public String partName() {
        return name().toLowerCase(Locale.ROOT);
    }
}