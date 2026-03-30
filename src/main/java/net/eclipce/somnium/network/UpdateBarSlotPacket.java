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
 * Client → Server packet sent when the player drags an ability to a bar
 * slot (or clears a slot) in the ability inventory screen.
 *
 * <p>The packet carries:</p>
 * <ul>
 *     <li><strong>slot</strong> — which bar slot to modify (0 to 5)</li>
 *     <li><strong>abilityId</strong> — the registry name of the ability to
 *         place in the slot, or empty string to clear the slot</li>
 * </ul>
 *
 * <p>The server validates the request: is the slot valid? Is the ability
 * unlocked? Is it bar-equippable (not a passive)? If all checks pass,
 * the bar is updated and synced back to the client.</p>
 */
public class UpdateBarSlotPacket {

    private final int slot;
    private final String abilityId;

    /**
     * Creates a bar slot update packet.
     *
     * @param slot      the bar slot index (0-5)
     * @param abilityId the ability registry name, or empty string to clear
     */
    public UpdateBarSlotPacket(int slot, @Nullable ResourceLocation abilityId) {
        this.slot = slot;
        this.abilityId = abilityId != null ? abilityId.toString() : "";
    }

    private UpdateBarSlotPacket(int slot, String abilityId) {
        this.slot = slot;
        this.abilityId = abilityId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeUtf(abilityId);
    }

    public static UpdateBarSlotPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        String abilityId = buf.readUtf(256);
        return new UpdateBarSlotPacket(slot, abilityId);
    }

    /**
     * Handles this packet on the server side. Validates and applies the
     * bar slot change.
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            if (abilityId.isEmpty()) {
                // Clear the slot
                data.setBarSlot(slot, null);
            } else {
                // Set the slot to the specified ability
                ResourceLocation key = new ResourceLocation(abilityId);
                AbilityType type = SomniumRegistries.getAbilityValue(key);
                if (type != null) {
                    data.setBarSlot(slot, type);
                }
            }

            // Sync the updated data back to the client
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }
}