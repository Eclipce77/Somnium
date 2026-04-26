package net.eclipce.somnium.client.config;

import net.eclipce.somnium.client.AbilityBarOverlay;
import net.eclipce.somnium.client.MeterOverlay;
import net.eclipce.somnium.client.config.BarPosition;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side configuration for the Somnium API.
 *
 * <p>This config is per-installation (not per-server) and controls
 * visual preferences like ability bar position. It is registered
 * as a {@code CLIENT} config type.</p>
 *
 * <p>Settings are accessible via the static fields after config loading.
 * The config file is located at {@code config/somnium-client.toml}.</p>
 *
 * <p>Pixel-level offset values for the bar and meter positions are
 * defined as constants in {@link AbilityBarOverlay} and {@link MeterOverlay}
 * for easy adjustment during development.</p>
 */
public class SomniumClientConfig {

    public static final ForgeConfigSpec SPEC;

    /**
     * Which corner of the screen the ability bar is rendered in.
     * Options: BOTTOM_RIGHT (default), BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT.
     */
    public static final ForgeConfigSpec.EnumValue<BarPosition> BAR_POSITION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Ability Bar Settings")
                .push("ability_bar");

        BAR_POSITION = builder
                .comment("Which corner of the screen the ability bar is rendered in.",
                        "BOTTOM_RIGHT (default), BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT.",
                        "When on top, the bar texture is flipped vertically.")
                .defineEnum("position", BarPosition.BOTTOM_RIGHT);

        builder.pop();

        SPEC = builder.build();
    }

    /**
     * @return the configured bar position
     */
    public static BarPosition getBarPosition() {
        return BAR_POSITION.get();
    }

    // Private constructor — utility class
    private SomniumClientConfig() {}
}