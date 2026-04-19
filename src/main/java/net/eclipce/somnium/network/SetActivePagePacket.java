package net.eclipce.somnium.network;

import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet sent when the player changes their active bar page.
 *
 * <p>Sent when the player presses the page cycle keybind or clicks the
 * page arrows in the ability inventory. The server updates the player's
 * active page so that ability activation targets the correct page.</p>
 */
public class SetActivePagePacket {

    private final int page;

    public SetActivePagePacket(int page) {
        this.page = page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
    }

    public static SetActivePagePacket decode(FriendlyByteBuf buf) {
        return new SetActivePagePacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            if (page >= 0 && page < SomniumPlayerData.MAX_PAGES) {
                data.setActivePage(page);
            }
        });
        context.setPacketHandled(true);
    }
}