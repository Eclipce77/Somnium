package net.eclipce.somnium.network;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.event.AbilityEquippedEvent;
import net.eclipce.somnium.event.AbilityUnequippedEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Client → Server packet sent when the player assigns an ability to a bar
 * slot on a specific page, or clears a slot.
 *
 * <p>The server-side handler validates the request (ability must be unlocked,
 * bar-equippable, and not already on the same page), fires the appropriate
 * {@link AbilityEquippedEvent} or {@link AbilityUnequippedEvent}, and applies
 * the change if not canceled.</p>
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
                handleUnequip(player, data);
            } else {
                handleEquip(player, data);
            }

            // Always re-sync to ensure client matches server state
            // (important if an event was canceled)
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }

    /**
     * Handles clearing a bar slot. Fires {@link AbilityUnequippedEvent}
     * if there was an ability in the slot.
     */
    private void handleUnequip(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation existingKey = data.getBarSlotKey(page, slot);
        if (existingKey == null) return;

        AbilityType existingType = SomniumRegistries.getAbilityValue(existingKey);
        if (existingType != null) {
            AbilityUnequippedEvent event = new AbilityUnequippedEvent(
                    player, existingType, page, slot);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return; // Canceled
            }
        }

        data.setBarSlot(page, slot, null);
    }

    /**
     * Handles equipping an ability to a bar slot. Validates the ability,
     * checks for duplicates on the same page, fires events, and applies.
     */
    private void handleEquip(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation key = new ResourceLocation(abilityId);
        AbilityType type = SomniumRegistries.getAbilityValue(key);
        if (type == null) return;

        if (!data.isAbilityUnlocked(type)) return;
        if (!type.isBarEquippable()) return;

        // Duplicate prevention: same ability can't appear twice on the same page
        if (data.isAbilityOnPage(page, key)) return;

        // If target slot is occupied, fire unequip event first
        ResourceLocation existingKey = data.getBarSlotKey(page, slot);
        if (existingKey != null) {
            AbilityType existingType = SomniumRegistries.getAbilityValue(existingKey);
            if (existingType != null) {
                AbilityUnequippedEvent unequipEvent = new AbilityUnequippedEvent(
                        player, existingType, page, slot);
                if (MinecraftForge.EVENT_BUS.post(unequipEvent)) {
                    return; // Can't remove existing, so can't equip new
                }
            }
            // Clear the old ability first
            data.setBarSlot(page, slot, null);
        }

        // Fire equip event
        AbilityEquippedEvent equipEvent = new AbilityEquippedEvent(
                player, type, page, slot);
        if (MinecraftForge.EVENT_BUS.post(equipEvent)) {
            return; // Canceled
        }

        data.setBarSlot(page, slot, type);
    }
}