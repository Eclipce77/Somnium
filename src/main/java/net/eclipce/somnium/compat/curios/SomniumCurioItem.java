package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Base class for items that function as both Curios accessories and
 * Somnium system hooks. Implements {@link ICurioItem} for Curios
 * compatibility and adds Somnium-specific equip/unequip/tick callbacks.
 *
 * <p>Addon developers extend this class and override the Somnium
 * callbacks to interact with powers, abilities, tags, meters, etc.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public class FireAmulet extends SomniumCurioItem {
 *     public FireAmulet() {
 *         super(new Item.Properties().stacksTo(1));
 *     }
 *
 *     @Override
 *     public void onSomniumEquip(ItemStack stack, ServerPlayer player,
 *                                SomniumPlayerData data, String slotId, int slotIndex) {
 *         // Grant a tag that unlocks fire abilities
 *         TagHandler.addTag(player, new ResourceLocation("mymod", "fire_attuned"));
 *     }
 *
 *     @Override
 *     public void onSomniumUnequip(ItemStack stack, ServerPlayer player,
 *                                  SomniumPlayerData data, String slotId, int slotIndex) {
 *         TagHandler.removeTag(player, new ResourceLocation("mymod", "fire_attuned"));
 *     }
 *
 *     @Override
 *     public void onSomniumTick(ItemStack stack, ServerPlayer player,
 *                               SomniumPlayerData data, String slotId, int slotIndex) {
 *         // Slowly regenerate a custom mana meter while equipped
 *         MeterInstance mana = data.getMeter(new ResourceLocation("mymod", "mana"));
 *         if (mana != null) mana.add(0.1f);
 *     }
 * }
 * }</pre>
 *
 * <p>Don't forget to assign the item to a Curios slot type using a
 * datapack tag file (e.g., {@code data/curios/tags/items/necklace.json}).</p>
 */
public abstract class SomniumCurioItem extends Item implements ICurioItem {

    public SomniumCurioItem(Properties properties) {
        super(properties);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Somnium callbacks — override these in your subclass
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called on the server when this curio is equipped into a slot.
     * Use to grant powers, tags, modify meters, etc.
     *
     * @param stack     the item stack being equipped
     * @param player    the server-side player
     * @param data      the player's Somnium data
     * @param slotId    the Curios slot identifier (e.g., "necklace", "ring")
     * @param slotIndex the index within that slot type
     */
    public void onSomniumEquip(ItemStack stack, ServerPlayer player,
                                SomniumPlayerData data, String slotId, int slotIndex) {
        // Override in subclass
    }

    /**
     * Called on the server when this curio is unequipped from a slot.
     * Use to revoke powers, tags, reset meters, etc.
     *
     * @param stack     the item stack being unequipped
     * @param player    the server-side player
     * @param data      the player's Somnium data
     * @param slotId    the Curios slot identifier
     * @param slotIndex the index within that slot type
     */
    public void onSomniumUnequip(ItemStack stack, ServerPlayer player,
                                  SomniumPlayerData data, String slotId, int slotIndex) {
        // Override in subclass
    }

    /**
     * Called every server tick while this curio is equipped.
     * Use for ongoing effects: meter regen, stamina modifiers, etc.
     *
     * <p>Called from {@link ICurioItem#curioTick(SlotContext, ItemStack)}
     * which fires every tick on both sides. This method only fires
     * server-side.</p>
     *
     * @param stack     the item stack in the curio slot
     * @param player    the server-side player
     * @param data      the player's Somnium data
     * @param slotId    the Curios slot identifier
     * @param slotIndex the index within that slot type
     */
    public void onSomniumTick(ItemStack stack, ServerPlayer player,
                               SomniumPlayerData data, String slotId, int slotIndex) {
        // Override in subclass
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ICurioItem implementation — delegates to Somnium callbacks
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player) {
            SomniumPlayerData data =
                    net.eclipce.somnium.core.data.SomniumCapability.get(player);
            if (data != null) {
                onSomniumTick(stack, player, data,
                        slotContext.identifier(), slotContext.index());
            }
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        // Equip is handled by CuriosEventHandler via CurioChangeEvent
        // to ensure proper ordering with other Somnium systems
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // Unequip is handled by CuriosEventHandler via CurioChangeEvent
    }
}
