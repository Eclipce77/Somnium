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
 * Client → Server packet for bar slot assignment changes.
 * Supports ability bar, transformation bar, and custom category bars.
 */
public class UpdateBarSlotPacket {

    public static final int BAR_ABILITY = 0;
    public static final int BAR_TRANSFORMATION = 1;
    public static final int BAR_CATEGORY = 2;

    private final int barType;
    private final int page;
    private final int slot;
    private final String abilityId;
    private final String categoryId; // only used when barType == BAR_CATEGORY

    /** Creates a packet for the ability bar. */
    public UpdateBarSlotPacket(int page, int slot, @Nullable ResourceLocation abilityId) {
        this(BAR_ABILITY, page, slot, abilityId != null ? abilityId.toString() : "", "");
    }

    private UpdateBarSlotPacket(int barType, int page, int slot,
                                String abilityId, String categoryId) {
        this.barType = barType;
        this.page = page;
        this.slot = slot;
        this.abilityId = abilityId;
        this.categoryId = categoryId;
    }

    /** Creates a packet for the transformation bar. */
    public static UpdateBarSlotPacket forTransBar(int slot,
                                                  @Nullable ResourceLocation abilityId) {
        return new UpdateBarSlotPacket(BAR_TRANSFORMATION, 0, slot,
                abilityId != null ? abilityId.toString() : "", "");
    }

    /** Creates a packet for a custom category bar. */
    public static UpdateBarSlotPacket forCategoryBar(ResourceLocation categoryKey, int slot,
                                                     @Nullable ResourceLocation abilityId) {
        return new UpdateBarSlotPacket(BAR_CATEGORY, 0, slot,
                abilityId != null ? abilityId.toString() : "",
                categoryKey.toString());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(barType);
        buf.writeVarInt(page);
        buf.writeVarInt(slot);
        buf.writeUtf(abilityId);
        buf.writeUtf(categoryId);
    }

    public static UpdateBarSlotPacket decode(FriendlyByteBuf buf) {
        int barType = buf.readVarInt();
        int page = buf.readVarInt();
        int slot = buf.readVarInt();
        String abilityId = buf.readUtf(256);
        String categoryId = buf.readUtf(256);
        return new UpdateBarSlotPacket(barType, page, slot, abilityId, categoryId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            switch (barType) {
                case BAR_TRANSFORMATION -> handleTransformationBar(player, data);
                case BAR_CATEGORY -> handleCategoryBar(player, data);
                default -> {
                    if (abilityId.isEmpty()) handleUnequip(player, data);
                    else handleEquip(player, data);
                }
            }

            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Transformation bar
    // ═══════════════════════════════════════════════════════════════════

    private void handleTransformationBar(ServerPlayer player, SomniumPlayerData data) {
        if (abilityId.isEmpty()) {
            data.setTransBarSlot(slot, null);
        } else {
            ResourceLocation key = new ResourceLocation(abilityId);
            AbilityType type = SomniumRegistries.getAbilityValue(key);
            if (type == null) return;
            if (!data.isAbilityUnlocked(type)) return;
            if (data.isAbilityOnTransBar(key)) return;
            data.setTransBarSlot(slot, type);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Category bar
    // ═══════════════════════════════════════════════════════════════════

    private void handleCategoryBar(ServerPlayer player, SomniumPlayerData data) {
        if (categoryId.isEmpty()) return;
        ResourceLocation catKey = new ResourceLocation(categoryId);

        if (abilityId.isEmpty()) {
            data.setCategoryBarSlot(catKey, slot, null);
        } else {
            ResourceLocation key = new ResourceLocation(abilityId);
            AbilityType type = SomniumRegistries.getAbilityValue(key);
            if (type == null) return;
            if (!data.isAbilityUnlocked(type)) return;
            if (data.isAbilityOnCategoryBar(catKey, key)) return;
            data.setCategoryBarSlot(catKey, slot, type);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ability bar (with events, pages, duplicate prevention)
    // ═══════════════════════════════════════════════════════════════════

    private void handleUnequip(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation existingKey = data.getBarSlotKey(page, slot);
        if (existingKey == null) return;

        AbilityType existingType = SomniumRegistries.getAbilityValue(existingKey);
        if (existingType != null) {
            AbilityUnequippedEvent event = new AbilityUnequippedEvent(
                    player, existingType, page, slot);
            if (MinecraftForge.EVENT_BUS.post(event)) return;
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
                if (MinecraftForge.EVENT_BUS.post(unequipEvent)) return;
            }
            data.setBarSlot(page, slot, null);
        }

        AbilityEquippedEvent equipEvent = new AbilityEquippedEvent(
                player, type, page, slot);
        if (MinecraftForge.EVENT_BUS.post(equipEvent)) return;

        data.setBarSlot(page, slot, type);
    }
}