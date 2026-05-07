package net.eclipce.somnium.example;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.minecraft.resources.ResourceLocation;

/**
 * Example INSTANT ability template. Copy and modify for your addon.
 *
 * <pre>{@code
 * public static final RegistryObject<AbilityType> MY_ABILITY =
 *     ABILITIES.register("my_ability", () -> new ExampleAbility());
 * }</pre>
 */
public class ExampleAbility extends AbilityType {

    public ExampleAbility() {
        super(new Properties()
                .activationType(ActivationType.INSTANT)
                .cooldown(20)        // 1 second cooldown
                .staminaCost(10f)    // 10 stamina per use
                // .compositionOptOut() // uncomment to disable composition gain
                .icon(new ResourceLocation("mymod", "textures/ability/my_ability.png"))
        );
    }

    @Override
    public void onActivate(AbilityActivationContext context) {
        // Your ability logic here
    }
}
