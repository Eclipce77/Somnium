package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.prerequisite.Prerequisite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Prerequisite that requires a specific Curios item to be equipped.
 * The ability is hidden until the player equips the required curio.
 *
 * <p>Only evaluates when Curios is loaded — always returns false otherwise.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Power.builder()
 *     .ability(() -> ENHANCED_FIREBALL.get(),
 *             new CurioEquippedPrerequisite(
 *                 () -> MyItems.FIRE_FOCUS.get(),
 *                 "Fire Focus"))
 *     .build();
 * }</pre>
 */
public class CurioEquippedPrerequisite implements Prerequisite {

    private final Supplier<Item> requiredItem;
    private final String itemName;

    /**
     * @param requiredItem supplier for the curio item that must be equipped
     * @param itemName     display name for the prerequisite description
     */
    public CurioEquippedPrerequisite(Supplier<Item> requiredItem, String itemName) {
        this.requiredItem = requiredItem;
        this.itemName = itemName;
    }

    @Override
    public boolean isMet(SomniumPlayerData data) {
        if (!CuriosCompat.isLoaded()) return false;

        // SomniumPlayerData doesn't hold a player reference directly,
        // but it's attached as a capability. We need to find the player.
        // This is evaluated during GUI refresh which has player context.
        // For server-side, the player is available via the capability owner.
        // For client-side, we check the local player.
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            return CuriosHelper.hasItem(mc.player, requiredItem.get());
        }
        return false;
    }

    @Override
    public Component getDescription() {
        return Component.literal("Requires curio: " + itemName);
    }
}
