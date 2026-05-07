package net.eclipce.somnium.example;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType.TransformationProperties;
import net.eclipce.somnium.core.ability.transformation.TransformationData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Example transformation template. Copy and modify for your addon.
 *
 * <pre>{@code
 * public static final RegistryObject<AbilityType> MY_FORM =
 *     ABILITIES.register("my_form", () -> new ExampleTransformation());
 * }</pre>
 */
public class ExampleTransformation extends TransformationAbilityType {

    public ExampleTransformation() {
        super(createProps());
    }

    private static TransformationProperties createProps() {
        TransformationProperties props = new TransformationProperties();
        props.activationType(ActivationType.TOGGLE);
        props.staminaCost(5f);
        props.visualData(TransformationData.builder()
                .transformedTexture(new ResourceLocation("mymod",
                        "textures/entity/my_form.png"))
                .modelScale(1.0f)
                .build());
        return props;
    }

    @Override
    public void onTransformComplete(AbilityActivationContext context) {
        // Applied when transformation activates
        context.getPlayer().addEffect(
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 0, true, false));
    }

    @Override
    public void onDeTransformStart(AbilityActivationContext context) {
        // Cleanup when transformation deactivates
        context.getPlayer().removeEffect(MobEffects.DAMAGE_RESISTANCE);
    }
}