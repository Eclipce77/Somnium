package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.tag.TagHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Pre-built curio item that grants a Somnium tag when equipped
 * and revokes it when unequipped. Since tags can trigger auto-grant
 * powers, this curio can indirectly grant entire power trees.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // A necklace that grants the "fire_attuned" tag (and any powers linked to it)
 * public static final RegistryObject<Item> FIRE_AMULET = ITEMS.register("fire_amulet",
 *     () -> new TagCurioItem(
 *         new Item.Properties().stacksTo(1),
 *         new ResourceLocation("mymod", "fire_attuned")));
 * }</pre>
 */
public class TagCurioItem extends SomniumCurioItem {

    private final ResourceLocation tag;

    /**
     * @param properties item properties
     * @param tag        the Somnium tag to grant/revoke
     */
    public TagCurioItem(Properties properties, ResourceLocation tag) {
        super(properties);
        this.tag = tag;
    }

    @Override
    public void onSomniumEquip(ItemStack stack, ServerPlayer player,
                                SomniumPlayerData data, String slotId, int slotIndex) {
        TagHandler.addTag(player, tag);
    }

    @Override
    public void onSomniumUnequip(ItemStack stack, ServerPlayer player,
                                  SomniumPlayerData data, String slotId, int slotIndex) {
        TagHandler.removeTag(player, tag);
    }
}
