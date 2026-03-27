package net.eclipce.somnium.core.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Context provided to all {@link AbilityType} behavior methods.
 *
 * <p>Contains references to the player, world, the specific ability instance
 * being used, and the bar slot it occupies (if applicable). This is the single
 * object every ability callback receives, keeping method signatures clean and
 * extensible — new context can be added here without changing every ability's
 * method signature.</p>
 *
 * <p>All ability behavior runs server-side. The {@link #player} field is a
 * {@link ServerPlayer} to enforce this — client-side effects (particles,
 * sounds, screen shakes) will be triggered via packets sent from within
 * ability code, not by running ability logic on the client.</p>
 */
public class AbilityActivationContext {

    private final ServerPlayer player;
    private final Level level;
    private final AbilityInstance abilityInstance;
    private final int barSlot;

    /**
     * Creates a new activation context.
     *
     * @param player          the player activating the ability
     * @param level           the world the player is in
     * @param abilityInstance the specific instance being activated (carries per-player state)
     * @param barSlot         the bar slot index (0-5), or {@code -1} for passive abilities
     */
    public AbilityActivationContext(ServerPlayer player, Level level,
                                    AbilityInstance abilityInstance, int barSlot) {
        this.player = player;
        this.level = level;
        this.abilityInstance = abilityInstance;
        this.barSlot = barSlot;
    }

    /**
     * Convenience constructor for passive abilities (no bar slot).
     */
    public AbilityActivationContext(ServerPlayer player, Level level,
                                    AbilityInstance abilityInstance) {
        this(player, level, abilityInstance, -1);
    }

    /**
     * @return the player using this ability. Always a {@link ServerPlayer}
     *         because ability logic is server-authoritative.
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * @return the world/level the player is currently in
     */
    public Level getLevel() {
        return level;
    }

    /**
     * @return the specific {@link AbilityInstance} being used. Use this to
     *         read or write per-player state like cooldown, charges, or
     *         custom data via {@link AbilityInstance#getCustomData()}.
     */
    public AbilityInstance getAbilityInstance() {
        return abilityInstance;
    }

    /**
     * @return the ability bar slot (0-5) this ability occupies, or {@code -1}
     *         if this is a passive ability (passives don't live on the bar)
     */
    public int getBarSlot() {
        return barSlot;
    }

    /**
     * @return {@code true} if this context is for a passive ability
     *         (i.e., not triggered from a bar slot)
     */
    public boolean isPassive() {
        return barSlot == -1;
    }

    /**
     * @return the {@link AbilityType} being activated (shortcut for
     *         {@code getAbilityInstance().getAbilityType()})
     */
    public AbilityType getAbilityType() {
        return abilityInstance.getAbilityType();
    }
}