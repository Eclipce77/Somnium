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
 * slot or clears a slot. Supports both the ability bar and transformation bar.
 */
public class UpdateBarSlotPacket {

    /** Ability bar (paged). */
    public static final int BAR_ABILITY = 0;
    /** Transformation bar (no pages). */
    public static final int BAR_TRANSFORMATION = 1;

    private final int barType;
    private final int page;
    private final int slot;
    private final String abilityId;

    /**
     * Creates a packet for the ability bar.
     */
    public UpdateBarSlotPacket(int page, int slot, @Nullable ResourceLocation abilityId) {
        this(BAR_ABILITY, page, slot, abilityId);
    }

    /**
     * Creates a packet for a specific bar type.
     */
    public UpdateBarSlotPacket(int barType, int page, int slot,
                               @Nullable ResourceLocation abilityId) {
        this.barType = barType;
        this.page = page;
        this.slot = slot;
        this.abilityId = abilityId != null ? abilityId.toString() : "";
    }

    private UpdateBarSlotPacket(int barType, int page, int slot, String abilityId) {
        this.barType = barType;
        this.page = page;
        this.slot = slot;
        this.abilityId = abilityId;
    }

    /**
     * Creates a packet for the transformation bar (page is ignored).
     */
    public static UpdateBarSlotPacket forTransBar(int slot,
                                                  @Nullable ResourceLocation abilityId) {
        return new UpdateBarSlotPacket(BAR_TRANSFORMATION, 0, slot, abilityId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(barType);
        buf.writeVarInt(page);
        buf.writeVarInt(slot);
        buf.writeUtf(abilityId);
    }

    public static UpdateBarSlotPacket decode(FriendlyByteBuf buf) {
        int barType = buf.readVarInt();
        int page = buf.readVarInt();
        int slot = buf.readVarInt();
        String abilityId = buf.readUtf(256);
        return new UpdateBarSlotPacket(barType, page, slot, abilityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            if (barType == BAR_TRANSFORMATION) {
                handleTransformationBar(player, data);
            } else {
                if (abilityId.isEmpty()) {
                    handleUnequip(player, data);
                } else {
                    handleEquip(player, data);
                }
            }

            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Transformation bar handler (simpler — no events/pages)
    // ═══════════════════════════════════════════════════════════════════

    private void handleTransformationBar(ServerPlayer player, SomniumPlayerData data) {
        if (abilityId.isEmpty()) {
            data.setTransBarSlot(slot, null);
        } else {
            ResourceLocation key = new ResourceLocation(abilityId);
            AbilityType type = SomniumRegistries.getAbilityValue(key);
            if (type == null) return;
            if (!data.isAbilityUnlocked(type)) return;
            // Duplicate prevention on trans bar
            if (data.isAbilityOnTransBar(key)) return;
            data.setTransBarSlot(slot, type);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ability bar handlers (with events, pages, duplicate prevention)
    // ═══════════════════════════════════════════════════════════════════

    private void handleUnequip(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation existingKey = data.getBarSlotKey(page, slot);
        if (existingKey == null) return;

        AbilityType existingType = SomniumRegistries.getAbilityValue(existingKey);
        if (existingType != null) {
            AbilityUnequippedEvent event = new AbilityUnequippedEvent(
                    player, existingType, page, slot);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return;
            }
        }

        data.setBarSlot(page, slot, null);
    }

    private void handleEquip(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation key = new ResourceLocation(abilityId);
        AbilityType type = SomniumRegistries.getAbilityValue(key);
        if (type == null) return;

        if (!data.isAbilityUnlocked(type)) return;
        if (!type.isBarEquippable()) return;
        if (data.isAbilityOnPage(page, key)) return;

        ResourceLocation existingKey = data.getBarSlotKey(page, slot);
        if (existingKey != null) {
            AbilityType existingType = SomniumRegistries.getAbilityValue(existingKey);
            if (existingType != null) {
                AbilityUnequippedEvent unequipEvent = new AbilityUnequippedEvent(
                        player, existingType, page, slot);
                if (MinecraftForge.EVENT_BUS.post(unequipEvent)) {
                    return;
                }
            }
            data.setBarSlot(page, slot, null);
        }

        AbilityEquippedEvent equipEvent = new AbilityEquippedEvent(
                player, type, page, slot);
        if (MinecraftForge.EVENT_BUS.post(equipEvent)) {
            return;
        }

        data.setBarSlot(page, slot, type);
    }
}