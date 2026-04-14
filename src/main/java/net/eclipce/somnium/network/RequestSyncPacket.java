package net.eclipce.somnium.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet requesting a full data sync.
 *
 * <p>Sent when the ability inventory screen opens to ensure the client
 * has the most up-to-date data before displaying it. The server responds
 * with a {@link SyncPlayerDataPacket}.</p>
 *
 * <p>This is an empty packet — it carries no data, just a signal to sync.</p>
 */
public class RequestSyncPacket {

    public RequestSyncPacket() {}

    public void encode(FriendlyByteBuf buf) {
        // No data to encode — this is a signal packet
    }

    public static RequestSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestSyncPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }
}