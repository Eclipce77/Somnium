package net.eclipce.somnium.core.effects;

import net.eclipce.somnium.Somnium;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for Somnium's custom mob effects.
 */
public class SomniumEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Somnium.MOD_ID);

    /** The Overuse effect — displays as "Overuse I" through "Overuse V". */
    public static final RegistryObject<MobEffect> OVERUSE =
            EFFECTS.register("overuse", OveruseEffect::new);

    public static void init(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}