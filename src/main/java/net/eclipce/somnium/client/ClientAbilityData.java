package net.eclipce.somnium.client;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Client-side cache of the local player's ability data.
 *
 * <p>The server owns all ability state via the {@link SomniumPlayerData} capability.
 * This class holds a <strong>client-side copy</strong> that is updated whenever the
 * server sends a {@code SyncPlayerDataPacket}. The HUD overlay and ability
 * inventory screen read from this cache.</p>
 *
 * <p>This is <strong>not</strong> a capability — it's a simple static holder.
 * Only the local player's data is cached here. Other players' data is not
 * available on the client (their transformations and bar state will be
 * synced via entity data or other mechanisms if needed for rendering).</p>
 *
 * <h3>Thread safety</h3>
 * <p>Updates arrive from the network thread but must be applied on the main
 * client thread. The sync packet handler uses
 * {@code context.enqueueWork()} to ensure this.</p>
 */
public final class ClientAbilityData {

    /**
     * The client-side copy of the local player's ability data.
     * Updated by sync packets from the server.
     * {@code null} before the first sync packet arrives or after disconnect.
     */
    @Nullable
    private static SomniumPlayerData localData = null;

    /**
     * Gets the local player's cached ability data.
     *
     * @return the cached data, or {@code null} if not yet synced
     */
    @Nullable
    public static SomniumPlayerData getLocalData() {
        return localData;
    }

    /**
     * Updates the client-side cache from a server sync packet.
     * Called on the main client thread by the sync packet handler.
     *
     * @param nbt the serialized SomniumPlayerData from the server
     */
    public static void updateFromSync(CompoundTag nbt) {
        if (localData == null) {
            localData = new SomniumPlayerData();
        }
        localData.deserializeNBT(nbt);
    }

    /**
     * Clears the client-side cache. Called on disconnect to prevent
     * stale data from persisting across server connections.
     */
    public static void clear() {
        localData = null;
    }

    // Private constructor — utility class
    private ClientAbilityData() {}
}