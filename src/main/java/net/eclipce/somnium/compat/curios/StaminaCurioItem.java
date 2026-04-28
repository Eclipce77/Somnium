package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.meter.StaminaData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Pre-built curio item that modifies stamina properties while equipped.
 * Supports drain modifier, max value boost, and regen rate changes.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Ring that reduces stamina drain by 30% and boosts max by 50
 * public static final RegistryObject<Item> STAMINA_RING = ITEMS.register("stamina_ring",
 *     () -> new StaminaCurioItem(
 *         new Item.Properties().stacksTo(1),
 *         0.7f,   // 30% less drain
 *         50f,    // +50 max stamina
 *         0.01f   // +0.01 regen/tick bonus
 *     ));
 * }</pre>
 */
public class StaminaCurioItem extends SomniumCurioItem {

    private final float drainModifier;
    private final float maxValueBoost;
    private final float regenRateBoost;

    /**
     * @param properties     item properties
     * @param drainModifier  multiplier for stamina drain (0.5 = 50% less drain).
     *                       Set to 1.0 for no change.
     * @param maxValueBoost  flat amount added to max stamina. Set to 0 for no change.
     * @param regenRateBoost flat amount added to regen rate per tick. Set to 0 for no change.
     */
    public StaminaCurioItem(Properties properties, float drainModifier,
                             float maxValueBoost, float regenRateBoost) {
        super(properties);
        this.drainModifier = drainModifier;
        this.maxValueBoost = maxValueBoost;
        this.regenRateBoost = regenRateBoost;
    }

    @Override
    public void onSomniumEquip(ItemStack stack, ServerPlayer player,
                                SomniumPlayerData data, String slotId, int slotIndex) {
        StaminaData stamina = data.getStaminaData();
        stamina.setDrainModifier(stamina.getDrainModifier() * drainModifier);
        if (maxValueBoost != 0) {
            stamina.setMaxValue(stamina.getMaxValue() + maxValueBoost);
        }
        if (regenRateBoost != 0) {
            stamina.setRegenRate(StaminaData.DEFAULT_REGEN_RATE + regenRateBoost);
        }
    }

    @Override
    public void onSomniumUnequip(ItemStack stack, ServerPlayer player,
                                  SomniumPlayerData data, String slotId, int slotIndex) {
        StaminaData stamina = data.getStaminaData();
        // Reverse the drain modifier
        if (drainModifier != 0) {
            stamina.setDrainModifier(stamina.getDrainModifier() / drainModifier);
        }
        if (maxValueBoost != 0) {
            stamina.setMaxValue(stamina.getMaxValue() - maxValueBoost);
        }
        if (regenRateBoost != 0) {
            stamina.setRegenRate(StaminaData.DEFAULT_REGEN_RATE);
        }
    }
}
