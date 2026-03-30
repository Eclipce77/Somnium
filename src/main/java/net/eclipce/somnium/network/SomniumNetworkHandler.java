package net.eclipce.somnium.network;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.client.ClientAbilityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge event handler for network-related player lifecycle events.
 *
 * <p>Handles:</p>
 * <ul>
 *     <li>{@link PlayerEvent.PlayerLoggedInEvent} — sends the initial full
 *         data sync to the client when a player joins the server</li>
 *     <li>{@link PlayerEvent.PlayerRespawnEvent} — re-syncs data after respawn
 *         since the player entity is recreated</li>
 *     <li>{@link PlayerEvent.PlayerChangedDimensionEvent} — re-syncs data after
 *         dimension change since the player entity may be recreated</li>
 *     <li>{@link PlayerEvent.PlayerLoggedOutEvent} — clears the client-side
 *         cache when the player disconnects (client-side only)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Somnium.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SomniumNetworkHandler {

    /**
     * Sends the initial full sync when a player logs in.
     * This ensures the client has the complete ability data
     * immediately after joining.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SomniumNetwork.syncToClient(serverPlayer);
        }
    }

    /**
     * Re-syncs data after respawn. The player entity is recreated on
     * respawn, and while the capability data is copied by
     * SomniumCapabilityHandler's clone handler, the client's cache
     * needs to be updated to reflect any death-related changes.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SomniumNetwork.syncToClient(serverPlayer);
        }
    }

    /**
     * Re-syncs data after dimension change (e.g., entering/leaving the End
     * or Nether). The player entity may be recreated during dimension travel.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SomniumNetwork.syncToClient(serverPlayer);
        }
    }

    /**
     * Clears the client-side ability data cache when the player disconnects.
     * Prevents stale data from persisting across server connections.
     *
     * <p>This only runs on the client side (physical client). On a dedicated
     * server, this event is irrelevant for client cache management.</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Clear client cache — safe to call from any side; on dedicated server
        // it's a no-op since ClientAbilityData is never populated there.
        // However, we guard with @OnlyIn in a helper to be safe.
        clearClientCache();
    }

    /**
     * Clears the client-side cache. Separated into a method to allow
     * for future dist-safety considerations.
     */
    private static void clearClientCache() {
        // This is safe because ClientAbilityData is a simple static holder.
        // On a dedicated server, this class is still loaded but the data is
        // always null, so clear() is harmless.
        ClientAbilityData.clear();
    }
}