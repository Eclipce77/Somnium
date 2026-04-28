package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Pre-built curio item that grants a Somnium power when equipped
 * and revokes it when unequipped.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In your item registration:
 * public static final RegistryObject<Item> FIRE_RING = ITEMS.register("fire_ring",
 *     () -> new PowerCurioItem(
 *         new Item.Properties().stacksTo(1),
 *         () -> MyPowers.FIRE_POWER.get()));
 * }</pre>
 *
 * <p>Don't forget to create a Curios slot tag for the item
 * (e.g., {@code data/curios/tags/items/ring.json}).</p>
 */
public class PowerCurioItem extends SomniumCurioItem {

    private final Supplier<Power> powerSupplier;

    /**
     * @param properties    item properties
     * @param powerSupplier supplier for the power to grant/revoke
     */
    public PowerCurioItem(Properties properties, Supplier<Power> powerSupplier) {
        super(properties);
        this.powerSupplier = powerSupplier;
    }

    @Override
    public void onSomniumEquip(ItemStack stack, ServerPlayer player,
                                SomniumPlayerData data, String slotId, int slotIndex) {
        Power power = powerSupplier.get();
        if (power != null && !data.hasPower(power)) {
            data.grantPower(power);
        }
    }

    @Override
    public void onSomniumUnequip(ItemStack stack, ServerPlayer player,
                                  SomniumPlayerData data, String slotId, int slotIndex) {
        Power power = powerSupplier.get();
        if (power != null && data.hasPower(power)) {
            data.revokePower(power);
        }
    }
}
