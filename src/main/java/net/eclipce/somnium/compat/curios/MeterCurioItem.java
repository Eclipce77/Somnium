package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.meter.MeterInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Pre-built curio item that continuously regenerates a custom meter
 * while equipped. Useful for accessories that boost mana regen,
 * energy recovery, etc.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Ring that regens 0.1 mana per tick while equipped
 * public static final RegistryObject<Item> MANA_RING = ITEMS.register("mana_ring",
 *     () -> new MeterCurioItem(
 *         new Item.Properties().stacksTo(1),
 *         new ResourceLocation("mymod", "mana"),
 *         0.1f  // regen per tick
 *     ));
 * }</pre>
 */
public class MeterCurioItem extends SomniumCurioItem {

    private final ResourceLocation meterId;
    private final float regenPerTick;

    /**
     * @param properties   item properties
     * @param meterId      the custom meter definition ID to modify
     * @param regenPerTick amount to add to the meter each server tick
     */
    public MeterCurioItem(Properties properties, ResourceLocation meterId,
                           float regenPerTick) {
        super(properties);
        this.meterId = meterId;
        this.regenPerTick = regenPerTick;
    }

    @Override
    public void onSomniumTick(ItemStack stack, ServerPlayer player,
                               SomniumPlayerData data, String slotId, int slotIndex) {
        MeterInstance meter = data.getMeter(meterId);
        if (meter != null && !meter.isFull()) {
            meter.add(regenPerTick);
        }
    }
}
