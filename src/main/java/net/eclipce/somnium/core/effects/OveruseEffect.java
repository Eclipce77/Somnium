package net.eclipce.somnium.core.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Custom "Overuse" potion effect. Displays as Overuse I through III.
 *
 * <p>Three stages, all harsh to discourage overuse:</p>
 * <ul>
 *     <li>Stage 1 (amp 0): -25% speed, -20% attack, exhaustion 0.05/tick</li>
 *     <li>Stage 2 (amp 1): -40% speed, -40% attack, exhaustion 0.1/tick</li>
 *     <li>Stage 3 (amp 2): -50% speed, -50% attack, exhaustion 0.15/tick, wither 1dmg/2sec</li>
 * </ul>
 *
 * <p><strong>Not removable by milk.</strong> Only {@code /effect clear}.</p>
 */
public class OveruseEffect extends MobEffect {

    private static final UUID SPEED_UUID =
            UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID ATTACK_UUID =
            UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e");

    public OveruseEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // All stages: exhaustion
        float exhaustionRate = switch (amplifier) {
            case 0 -> 0.05f;
            case 1 -> 0.10f;
            default -> 0.15f;
        };
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            player.causeFoodExhaustion(exhaustionRate);
        }

        // Stage 3: wither damage every 40 ticks
        if (amplifier >= 2 && entity.tickCount % 40 == 0) {
            entity.hurt(entity.damageSources().wither(), 1.0f);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // All stages tick
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes,
                                      int amplifier) {
        double speedPenalty = switch (amplifier) {
            case 0 -> -0.25;
            case 1 -> -0.40;
            default -> -0.50;
        };
        double attackPenalty = switch (amplifier) {
            case 0 -> -0.20;
            case 1 -> -0.40;
            default -> -0.50;
        };

        if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_UUID);
            entity.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(
                    new AttributeModifier(SPEED_UUID, "Overuse speed penalty",
                            speedPenalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(ATTACK_UUID);
            entity.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
                    new AttributeModifier(ATTACK_UUID, "Overuse attack penalty",
                            attackPenalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        super.addAttributeModifiers(entity, attributes, amplifier);
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes,
                                         int amplifier) {
        if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null)
            entity.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_UUID);
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null)
            entity.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(ATTACK_UUID);
        super.removeAttributeModifiers(entity, attributes, amplifier);
    }

    /**
     * Creates a non-curable Overuse effect instance.
     *
     * @param stage    the overuse stage (1-5)
     * @param duration the duration in ticks
     * @return an effect instance that cannot be removed by milk
     */
    public static MobEffectInstance createInstance(MobEffect overuseEffect,
                                                   int stage, int duration) {
        MobEffectInstance instance = new MobEffectInstance(
                overuseEffect,
                duration,
                stage - 1, // amplifier is 0-indexed (stage 1 = amp 0)
                false,     // ambient
                true,      // visible
                true       // show icon
        );
        // Make it not removable by milk or other curative items
        instance.setCurativeItems(new ArrayList<>());
        return instance;
    }
}