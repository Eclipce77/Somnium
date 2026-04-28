package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.tag.TagHandler;
import net.eclipce.somnium.network.SomniumNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Forge event handler for Curios API integration.
 *
 * <p>Listens for {@link CurioChangeEvent} which fires whenever a curio
 * slot's contents change (equip or unequip). When a {@link SomniumCurioItem}
 * is equipped or unequipped, this handler delegates to the item's
 * Somnium-specific methods.</p>
 *
 * <p>Only registered when Curios is present — called from
 * {@link CuriosCompat#init()}.</p>
 */
public class CuriosEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Somnium/Curios");

    /**
     * Registers this handler on the Forge event bus.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(CuriosEventHandler.class);
        LOGGER.info("Registered Curios event handler");
    }

    /**
     * Fires when a curio slot's contents change. Detects Somnium curio
     * items and calls their equip/unequip methods.
     */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack oldStack = event.getFrom();
        ItemStack newStack = event.getTo();
        String slotId = event.getIdentifier();
        int slotIndex = event.getSlotIndex();

        // Handle unequip of old item
        if (!oldStack.isEmpty() && oldStack.getItem() instanceof SomniumCurioItem oldCurio) {
            SomniumPlayerData data = SomniumCapability.get(player);
            if (data != null) {
                oldCurio.onSomniumUnequip(oldStack, player, data, slotId, slotIndex);
                SomniumNetwork.syncToClient(player);
            }
        }

        // Handle equip of new item
        if (!newStack.isEmpty() && newStack.getItem() instanceof SomniumCurioItem newCurio) {
            SomniumPlayerData data = SomniumCapability.get(player);
            if (data != null) {
                newCurio.onSomniumEquip(newStack, player, data, slotId, slotIndex);
                SomniumNetwork.syncToClient(player);
            }
        }
    }
}
