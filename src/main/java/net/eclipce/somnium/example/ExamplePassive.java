package net.eclipce.somnium.example;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Example passive ability template. Copy and modify for your addon.
 *
 * <p>Three patterns:</p>
 * <ul>
 *     <li><b>Normal:</b> player toggles on/off manually (default)</li>
 *     <li><b>enabledOnGrant:</b> auto-enabled when power is granted,
 *         player can still toggle off</li>
 *     <li><b>forced:</b> always on while power is held, cannot be
 *         toggled off by the player</li>
 * </ul>
 *
 * <pre>{@code
 * // Normal passive (player toggles)
 * new Properties().activationType(ActivationType.PASSIVE)
 *
 * // Auto-enabled on grant, player can toggle off
 * new Properties().activationType(ActivationType.PASSIVE).enabledOnGrant()
 *
 * // Forced — always on, no toggle
 * new Properties().activationType(ActivationType.PASSIVE).forced()
 * }</pre>
 */
public class ExamplePassive extends AbilityType {

    public ExamplePassive() {
        super(new Properties()
                .activationType(ActivationType.PASSIVE)
                .enabledOnGrant()    // auto-enabled when granted
                // .forced()         // uncomment for permanently active passive
                .icon(new ResourceLocation("mymod", "textures/ability/my_passive.png"))
        );
    }

    @Override
    public void onPassiveTick(AbilityActivationContext context) {
        // Runs every server tick while enabled
        context.getPlayer().addEffect(
                new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0,
                        true, false, true));
    }

    @Override
    public void onEnabled(AbilityActivationContext context) {
        // Called once when the passive is toggled on
    }

    @Override
    public void onDisabled(AbilityActivationContext context) {
        // Called once when the passive is toggled off
        context.getPlayer().removeEffect(MobEffects.NIGHT_VISION);
    }
}
