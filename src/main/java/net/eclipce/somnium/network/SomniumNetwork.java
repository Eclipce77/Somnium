package net.eclipce.somnium.network;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Central networking hub for the Somnium API. Creates the
 * {@link SimpleChannel}, registers all packet types, and provides
 * convenience methods for sending packets.
 *
 * <h3>Channel</h3>
 * <p>Somnium uses a single {@link SimpleChannel} named {@code somnium:main}
 * for all communication. The protocol version is checked on both sides —
 * client and server must match, ensuring packet format compatibility.</p>
 *
 * <h3>Packet types</h3>
 * <ul>
 *     <li><strong>Server → Client:</strong>
 *         <ul>
 *             <li>{@link SyncPlayerDataPacket} — full player data sync</li>
 *         </ul>
 *     </li>
 *     <li><strong>Client → Server:</strong>
 *         <ul>
 *             <li>{@link ActivateAbilityPacket} — ability bar key press/release</li>
 *             <li>{@link UpdateBarSlotPacket} — bar slot assignment change</li>
 *             <li>{@link TogglePassivePacket} — passive ability on/off toggle</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <p>Call {@link #init()} from the mod constructor to set up the channel.
 * Use the convenience methods to send packets:</p>
 * <pre>{@code
 * // Server-side: sync player data to their client
 * SomniumNetwork.syncToClient(serverPlayer);
 *
 * // Client-side: send ability activation to server
 * SomniumNetwork.sendToServer(new ActivateAbilityPacket(0, Action.PRESS));
 * }</pre>
 */
public final class SomniumNetwork {

    /**
     * Protocol version string. Increment this when packet formats change
     * to prevent desync between mismatched client/server versions.
     */
    private static final String PROTOCOL_VERSION = "1";

    /**
     * The SimpleChannel used for all Somnium network communication.
     */
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Somnium.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /** Packet ID counter. Each packet type gets a unique ID. */
    private static int packetId = 0;

    /**
     * Initializes the network channel and registers all packet types.
     * Must be called from the Somnium mod constructor.
     */
    public static void init() {
        // Server → Client packets
        CHANNEL.messageBuilder(SyncPlayerDataPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPlayerDataPacket::encode)
                .decoder(SyncPlayerDataPacket::decode)
                .consumerMainThread(SyncPlayerDataPacket::handle)
                .add();

        // Client → Server packets
        CHANNEL.messageBuilder(ActivateAbilityPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ActivateAbilityPacket::encode)
                .decoder(ActivateAbilityPacket::decode)
                .consumerMainThread(ActivateAbilityPacket::handle)
                .add();

        CHANNEL.messageBuilder(UpdateBarSlotPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateBarSlotPacket::encode)
                .decoder(UpdateBarSlotPacket::decode)
                .consumerMainThread(UpdateBarSlotPacket::handle)
                .add();

        CHANNEL.messageBuilder(TogglePassivePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TogglePassivePacket::encode)
                .decoder(TogglePassivePacket::decode)
                .consumerMainThread(TogglePassivePacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestSyncPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestSyncPacket::encode)
                .decoder(RequestSyncPacket::decode)
                .consumerMainThread(RequestSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetActivePagePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetActivePagePacket::encode)
                .decoder(SetActivePagePacket::decode)
                .consumerMainThread(SetActivePagePacket::handle)
                .add();

        Somnium.LOGGER.debug("Somnium network channel initialized with {} packet types", packetId);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Convenience send methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a packet from the client to the server.
     *
     * @param packet the packet to send
     */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /**
     * Sends a packet from the server to a specific client.
     *
     * @param packet the packet to send
     * @param player the target player
     */
    public static void sendToClient(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Sends a packet from the server to all connected clients.
     *
     * @param packet the packet to send
     */
    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /**
     * Sends a packet from the server to all players tracking a specific player.
     * Useful for syncing visual state (like transformations) to nearby players.
     *
     * @param packet the packet to send
     * @param trackedPlayer the player being tracked
     */
    public static void sendToTracking(Object packet, ServerPlayer trackedPlayer) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> trackedPlayer), packet);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  High-level sync methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Synchronizes a player's full ability data to their client.
     * This is the primary sync method — call it whenever server-side
     * ability data changes for a player.
     *
     * <p>Also clears the dirty flag on the player's data after syncing.</p>
     *
     * @param player the player to sync
     */
    public static void syncToClient(ServerPlayer player) {
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) return;

        sendToClient(new SyncPlayerDataPacket(data.serializeNBT()), player);
        data.clearDirty();
    }

    /**
     * Synchronizes a player's data if it has been marked dirty.
     * Intended for periodic sync checks (e.g., from a tick handler).
     *
     * @param player the player to check and sync
     */
    public static void syncIfDirty(ServerPlayer player) {
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data != null && data.isDirty()) {
            syncToClient(player);
        }
    }

    // Private constructor — utility class
    private SomniumNetwork() {}
}