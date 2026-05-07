package net.eclipce.somnium.example;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.prerequisite.HasTagPrerequisite;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Example power registration template. Shows how to register abilities,
 * transformations, and passives under a power with prerequisites.
 *
 * <pre>{@code
 * public class MyModPowers {
 *
 *     public static final DeferredRegister<AbilityType> ABILITIES =
 *         DeferredRegister.create(SomniumRegistries.ABILITY_TYPE_KEY, "mymod");
 *
 *     public static final DeferredRegister<Power> POWERS =
 *         DeferredRegister.create(SomniumRegistries.POWER_KEY, "mymod");
 *
 *     // Abilities
 *     public static final RegistryObject<AbilityType> FIREBALL =
 *         ABILITIES.register("fireball", ExampleAbility::new);
 *
 *     public static final RegistryObject<AbilityType> FIRE_FORM =
 *         ABILITIES.register("fire_form", ExampleTransformation::new);
 *
 *     public static final RegistryObject<AbilityType> HEAT_AURA =
 *         ABILITIES.register("heat_aura", ExamplePassive::new);
 *
 *     // Power — groups abilities together
 *     public static final RegistryObject<Power> FIRE_POWER = POWERS.register("fire",
 *         () -> Power.builder()
 *             .ability(() -> FIREBALL.get())          // always available
 *             .ability(() -> FIRE_FORM.get())         // always available
 *             .ability(() -> HEAT_AURA.get(),         // hidden until tag present
 *                 new HasTagPrerequisite(new ResourceLocation("mymod", "fire_mastery")))
 *             .build());
 *
 *     public static void init(IEventBus bus) {
 *         ABILITIES.register(bus);
 *         POWERS.register(bus);
 *     }
 * }
 * }</pre>
 *
 * <h3>Granting powers to players</h3>
 * <pre>{@code
 * // In a command, event handler, or item interaction:
 * SomniumPlayerData data = SomniumCapability.get(player);
 * data.grantPower(MyModPowers.FIRE_POWER.get());
 * SomniumNetwork.syncToClient(player);
 * }</pre>
 */
public final class ExamplePower {
    // This class is documentation-only. See the Javadoc above for usage.
    private ExamplePower() {}
}
