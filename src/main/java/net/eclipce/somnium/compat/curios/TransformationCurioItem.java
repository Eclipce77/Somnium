package net.eclipce.somnium.compat.curios;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Pre-built curio item that activates a transformation when equipped
 * and deactivates it when unequipped. The player must have the
 * transformation ability unlocked for this to work.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public static final RegistryObject<Item> WOLF_PELT = ITEMS.register("wolf_pelt",
 *     () -> new TransformationCurioItem(
 *         new Item.Properties().stacksTo(1),
 *         () -> MyAbilities.WEREWOLF_FORM.get()));
 * }</pre>
 */
public class TransformationCurioItem extends SomniumCurioItem {

    private final Supplier<AbilityType> transformationSupplier;

    /**
     * @param properties              item properties
     * @param transformationSupplier  supplier for the transformation ability type
     */
    public TransformationCurioItem(Properties properties,
                                    Supplier<AbilityType> transformationSupplier) {
        super(properties);
        this.transformationSupplier = transformationSupplier;
    }

    @Override
    public void onSomniumEquip(ItemStack stack, ServerPlayer player,
                                SomniumPlayerData data, String slotId, int slotIndex) {
        AbilityType type = transformationSupplier.get();
        if (!(type instanceof TransformationAbilityType)) return;
        if (!data.isAbilityUnlocked(type)) return;

        ResourceLocation key = SomniumRegistries.getAbilityKey(type);
        AbilityInstance instance = data.getAbilityInstance(key);
        if (instance == null || instance.isActive()) return;

        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), instance);

        if (type.canActivate(ctx)) {
            type.onActivate(ctx);
            instance.setActive(true);
            data.setMostRecentTransformation(key);
        }
    }

    @Override
    public void onSomniumUnequip(ItemStack stack, ServerPlayer player,
                                  SomniumPlayerData data, String slotId, int slotIndex) {
        AbilityType type = transformationSupplier.get();
        if (!(type instanceof TransformationAbilityType)) return;

        ResourceLocation key = SomniumRegistries.getAbilityKey(type);
        AbilityInstance instance = data.getAbilityInstance(key);
        if (instance == null || !instance.isActive()) return;

        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), instance);

        type.onDeactivate(ctx);
        instance.setActive(false);
    }
}
