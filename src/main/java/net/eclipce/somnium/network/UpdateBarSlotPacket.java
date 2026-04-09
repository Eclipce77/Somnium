package net.eclipce.somnium.network;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Client → Server packet sent when the player assigns an ability to a bar
 * slot on a specific page, or clears a slot.
 */
public class UpdateBarSlotPacket {

    private final int page;
    private final int slot;
    private final String abilityId;

    public UpdateBarSlotPacket(int page, int slot, @Nullable ResourceLocation abilityId) {
        this.page = page;
        this.slot = slot;
        this.abilityId = abilityId != null ? abilityId.toString() : "";
    }

    private UpdateBarSlotPacket(int page, int slot, String abilityId) {
        this.page = page;
        this.slot = slot;
        this.abilityId = abilityId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeVarInt(slot);
        buf.writeUtf(abilityId);
    }

    public static UpdateBarSlotPacket decode(FriendlyByteBuf buf) {
        int page = buf.readVarInt();
        int slot = buf.readVarInt();
        String abilityId = buf.readUtf(256);
        return new UpdateBarSlotPacket(page, slot, abilityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            if (abilityId.isEmpty()) {
                data.setBarSlot(page, slot, null);
            } else {
                ResourceLocation key = new ResourceLocation(abilityId);
                AbilityType type = SomniumRegistries.getAbilityValue(key);
                if (type != null) {
                    data.setBarSlot(page, slot, type);
                }
            }

            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }
}