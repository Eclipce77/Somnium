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
 * Custom "Overuse" potion effect that replaces individual debuff effects.
 * Displays as a single effect (Overuse I through V) in the HUD.
 *
 * <p>The amplifier (0-4) maps to the overuse stage (1-5). Each stage
 * applies progressively harsher attribute modifiers:</p>
 * <ul>
 *     <li>Stage 1 (amplifier 0): -15% movement speed</li>
 *     <li>Stage 2 (amplifier 1): -15% speed, +exhaustion</li>
 *     <li>Stage 3 (amplifier 2): -30% speed, +exhaustion, -20% attack damage</li>
 *     <li>Stage 4 (amplifier 3): -30% speed, +heavy exhaustion, -40% attack damage</li>
 *     <li>Stage 5 (amplifier 4): -30% speed, +heavy exhaustion, -40% attack, +wither damage</li>
 * </ul>
 *
 * <p><strong>Not removable by milk or other curative items.</strong>
 * Only removable via {@code /effect clear} command or when the
 * overuse timer expires naturally.</p>
 */
public class OveruseEffect extends MobEffect {

    private static final UUID SPEED_UUID =
            UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID ATTACK_UUID =
            UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e");

    public OveruseEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000); // dark red color
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Stage 2+ (amplifier 1+): apply exhaustion (hunger drain)
        if (amplifier >= 1) {
            float exhaustionRate = amplifier >= 3 ? 0.1f : 0.05f;
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                player.causeFoodExhaustion(exhaustionRate);
            }
        }

        // Stage 5 (amplifier 4): apply wither damage every 40 ticks
        if (amplifier >= 4) {
            if (entity.tickCount % 40 == 0) {
                entity.hurt(entity.damageSources().wither(), 1.0f);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Tick every tick for exhaustion, wither checks internally
        return amplifier >= 1;
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes,
                                      int amplifier) {
        // Speed reduction: -15% at stage 1-2, -30% at stage 3+
        double speedPenalty = amplifier >= 2 ? -0.30 : -0.15;
        if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_UUID);
            entity.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(
                    new AttributeModifier(SPEED_UUID, "Overuse speed penalty",
                            speedPenalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        // Attack reduction: -20% at stage 3, -40% at stage 4+
        if (amplifier >= 2 && entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double attackPenalty = amplifier >= 3 ? -0.40 : -0.20;
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
        if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_UUID);
        }
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(ATTACK_UUID);
        }

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