package net.eclipce.somnium.network;

import net.eclipce.somnium.client.ClientAbilityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet that synchronizes a player's full ability data.
 *
 * <p>Sent when:</p>
 * <ul>
 *     <li>A player logs in (initial sync)</li>
 *     <li>A player respawns after death</li>
 *     <li>Any ability data changes server-side (power granted, ability
 *         unlocked, bar slot changed, cooldown state, etc.)</li>
 * </ul>
 *
 * <p>The packet carries the full serialized {@link net.eclipce.somnium.core.data.SomniumPlayerData}
 * as a {@link CompoundTag}. On the client, this is deserialized into the
 * {@link ClientAbilityData} cache for rendering use.</p>
 *
 * <p>This is a full sync — the entire data blob is sent. For a first
 * implementation this is simple and reliable. If performance becomes a
 * concern with large data sets, a delta-sync packet can be added later
 * without changing this packet.</p>
 */
public class SyncPlayerDataPacket {

    private final CompoundTag data;

    /**
     * Creates a sync packet with the given serialized player data.
     *
     * @param data the serialized SomniumPlayerData
     */
    public SyncPlayerDataPacket(CompoundTag data) {
        this.data = data;
    }

    /**
     * Encodes this packet to a network buffer for transmission.
     *
     * @param buf the buffer to write to
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    /**
     * Decodes a sync packet from a network buffer.
     *
     * @param buf the buffer to read from
     * @return the decoded packet
     */
    public static SyncPlayerDataPacket decode(FriendlyByteBuf buf) {
        return new SyncPlayerDataPacket(buf.readNbt());
    }

    /**
     * Handles this packet on the client side. Updates the client-side
     * ability data cache on the main client thread.
     *
     * @param ctx the network context supplier
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // Update the client-side cache with the received data
            ClientAbilityData.updateFromSync(data);
        });
        context.setPacketHandled(true);
    }
}