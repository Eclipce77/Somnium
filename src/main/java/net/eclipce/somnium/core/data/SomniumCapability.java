package net.eclipce.somnium.core.data;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.NonNullConsumer;

import javax.annotation.Nullable;

/**
 * Holds the Forge {@link Capability} token for {@link SomniumPlayerData} and
 * provides static helper methods for accessing a player's ability data.
 *
 * <h3>Usage for addon developers</h3>
 * <p>This is the primary entry point for interacting with a player's ability state:</p>
 * <pre>{@code
 * // Get player data (returns null if capability not present)
 * SomniumPlayerData data = SomniumCapability.get(player);
 * if (data != null) {
 *     data.grantPower(ModPowers.PYROMANCY.get());
 *     data.unlockAbility(ModAbilities.FIRE_BLAST.get());
 *     data.setBarSlot(0, ModAbilities.FIRE_BLAST.get());
 * }
 * }</pre>
 *
 * <p>Or with optional handling:</p>
 * <pre>{@code
 * SomniumCapability.ifPresent(player, data -> {
 *     data.grantPower(ModPowers.PYROMANCY.get());
 * });
 * }</pre>
 *
 * @see SomniumPlayerData
 * @see SomniumDataProvider
 */
public final class SomniumCapability {

    /**
     * The Forge Capability token for SomniumPlayerData.
     *
     * <p>This is populated by Forge's capability injection system at startup.
     * Use the static helper methods ({@link #get}, {@link #ifPresent}) rather
     * than accessing this directly, unless you need the raw LazyOptional.</p>
     */
    public static final Capability<SomniumPlayerData> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    /**
     * Gets the SomniumPlayerData for a player, or null if not available.
     *
     * <p>This is the simplest and most common way to access player data.
     * It will return null if the capability hasn't been attached yet
     * (shouldn't happen for valid Player entities) or if the capability
     * system isn't initialized.</p>
     *
     * @param player the player to get data for
     * @return the player's ability data, or null
     */
    @Nullable
    public static SomniumPlayerData get(Player player) {
        return player.getCapability(CAPABILITY).orElse(null);
    }

    /**
     * Executes an action on the player's SomniumPlayerData if present.
     * A convenience method for when you want to do something with the
     * data without null-checking.
     *
     * @param player the player
     * @param action the action to perform with the data
     */
    public static void ifPresent(Player player, NonNullConsumer<SomniumPlayerData> action) {
        player.getCapability(CAPABILITY).ifPresent(action);
    }

    // Private constructor — utility class
    private SomniumCapability() {}
}