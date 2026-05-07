package net.eclipce.somnium;

import net.minecraft.world.level.GameRules;

/**
 * Forge gamerules for Somnium. Registered during class load.
 */
public class SomniumGameRules {

    /**
     * Whether players lose composition on death.
     * Default: true.
     */
    public static final GameRules.Key<GameRules.BooleanValue> COMPOSITION_LOSS_ON_DEATH =
            GameRules.register("somniumCompositionLossOnDeath",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true));

    /** Call during mod init to trigger class loading and gamerule registration. */
    public static void init() {
        // Class load triggers the static initializer above
    }
}