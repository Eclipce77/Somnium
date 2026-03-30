package net.eclipce.somnium.tick;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.transformation.*;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Map;

/**
 * Handles the transformation phase state machine each tick.
 *
 * <p>This is called by {@link SomniumTickHandler} for players who have
 * transformation abilities. It manages:</p>
 * <ul>
 *     <li>Phase advancement (TRANSFORMING_IN → TRANSFORMED → TRANSFORMING_OUT → IDLE)</li>
 *     <li>Phase-specific tick callbacks on {@link TransformationAbilityType}</li>
 *     <li>Attribute modifier application/removal at phase transitions</li>
 *     <li>Max duration enforcement</li>
 *     <li>Trigger evaluation for involuntary activations</li>
 *     <li>Trigger auto-revert when conditions are no longer met</li>
 *     <li>Trigger cooldown countdown</li>
 * </ul>
 *
 * <p>All methods are static and called from the main tick handler.
 * This class contains no state — all state lives on
 * {@link TransformationInstance} and {@link SomniumPlayerData}.</p>
 */
public final class TransformationTickHandler {

    /**
     * Ticks the currently active transformation for a player.
     * Called once per server tick for players who have an active transformation.
     *
     * @param player  the server player
     * @param data    the player's ability data
     * @param transId the registry name of the active transformation
     */
    public static void tickActiveTransformation(ServerPlayer player,
                                                SomniumPlayerData data,
                                                ResourceLocation transId) {
        AbilityInstance instance = data.getAbilityInstance(transId);
        if (!(instance instanceof TransformationInstance ti)) {
            // Instance isn't a TransformationInstance — clear invalid state
            data.setActiveTransformation(null);
            return;
        }

        AbilityType type = instance.getAbilityType();
        if (!(type instanceof TransformationAbilityType transType)) {
            data.setActiveTransformation(null);
            return;
        }

        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), instance,
                data.findBarSlot(type));

        switch (ti.getPhase()) {
            case TRANSFORMING_IN -> tickTransformingIn(player, data, transType, ti, ctx);
            case TRANSFORMED -> tickTransformed(player, data, transType, ti, ctx);
            case TRANSFORMING_OUT -> tickTransformingOut(player, data, transType, ti, ctx);
            case IDLE -> {
                // Shouldn't have an active transformation while IDLE — clean up
                data.setActiveTransformation(null);
            }
        }

        ti.incrementPhaseTicks();
    }

    /**
     * Evaluates transformation triggers for a player who is NOT currently
     * transformed. Called once per server tick for each transformation
     * ability the player has that has a trigger.
     *
     * @param player   the server player
     * @param data     the player's ability data
     * @param transType the transformation ability type with a trigger
     * @param ti       the transformation instance
     */
    public static void evaluateTrigger(ServerPlayer player,
                                       SomniumPlayerData data,
                                       TransformationAbilityType transType,
                                       TransformationInstance ti) {
        TransformationTrigger trigger = transType.getTrigger();
        if (trigger == null) return;

        // Tick trigger cooldown while not transformed
        ti.tickTriggerCooldown();

        // Don't trigger if on cooldown
        if (ti.isTriggerOnCooldown()) return;

        // Don't trigger if another transformation is already active
        if (data.hasActiveTransformation()) return;

        // Evaluate the trigger condition
        if (!trigger.test(player)) return;

        // Check suppression
        if (trigger.isSuppressible() && trigger.canSuppress(player)) return;

        // All checks passed — force-activate the transformation
        ResourceLocation transId = SomniumRegistries.getAbilityKey(transType);
        if (transId == null) return;

        ti.setPhase(TransformationPhase.TRANSFORMING_IN);
        ti.resetPhaseTicks();
        ti.resetTotalTransformedTicks();
        ti.setTriggeredByCondition(true);
        ti.setActive(true);
        data.setActiveTransformation(transId);

        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), ti, data.findBarSlot(transType));
        transType.onTransformStart(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Phase tick methods
    // ═══════════════════════════════════════════════════════════════════

    private static void tickTransformingIn(ServerPlayer player, SomniumPlayerData data,
                                           TransformationAbilityType transType,
                                           TransformationInstance ti,
                                           AbilityActivationContext ctx) {
        int duration = transType.getTransformInDuration();
        transType.onTransformingInTick(ctx, ti.getPhaseTicks());

        // Check if transform-in phase is complete
        if (ti.getPhaseTicks() >= duration) {
            // Transition to TRANSFORMED
            ti.setPhase(TransformationPhase.TRANSFORMED);
            ti.resetPhaseTicks();
            ti.resetTotalTransformedTicks();

            // Apply attribute modifiers
            applyAttributeModifiers(player, transType);

            transType.onTransformComplete(ctx);
        }
    }

    private static void tickTransformed(ServerPlayer player, SomniumPlayerData data,
                                        TransformationAbilityType transType,
                                        TransformationInstance ti,
                                        AbilityActivationContext ctx) {
        ti.incrementTotalTransformedTicks();
        transType.onTransformedTick(ctx);

        // Check max duration
        if (transType.shouldForceDeTransform(ctx, ti.getTotalTransformedTicks())) {
            beginDeTransform(player, data, transType, ti, ctx);
            return;
        }

        // Check trigger auto-revert
        if (ti.isTriggeredByCondition() && transType.hasTrigger()) {
            TransformationTrigger trigger = transType.getTrigger();
            if (trigger.isAutoRevertOnConditionEnd() && !trigger.test(player)) {
                beginDeTransform(player, data, transType, ti, ctx);
            }
        }
    }

    private static void tickTransformingOut(ServerPlayer player, SomniumPlayerData data,
                                            TransformationAbilityType transType,
                                            TransformationInstance ti,
                                            AbilityActivationContext ctx) {
        int duration = transType.getTransformOutDuration();
        transType.onTransformingOutTick(ctx, ti.getPhaseTicks());

        // Check if transform-out phase is complete
        if (ti.getPhaseTicks() >= duration) {
            completeDeTransform(player, data, transType, ti, ctx);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Phase transition helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Begins the de-transform sequence (TRANSFORMED → TRANSFORMING_OUT).
     */
    private static void beginDeTransform(ServerPlayer player, SomniumPlayerData data,
                                         TransformationAbilityType transType,
                                         TransformationInstance ti,
                                         AbilityActivationContext ctx) {
        ti.setPhase(TransformationPhase.TRANSFORMING_OUT);
        ti.resetPhaseTicks();

        // Remove attribute modifiers at the start of de-transform
        removeAttributeModifiers(player, transType);

        transType.onDeTransformStart(ctx);
    }

    /**
     * Completes the de-transform sequence (TRANSFORMING_OUT → IDLE).
     */
    private static void completeDeTransform(ServerPlayer player, SomniumPlayerData data,
                                            TransformationAbilityType transType,
                                            TransformationInstance ti,
                                            AbilityActivationContext ctx) {
        ti.setPhase(TransformationPhase.IDLE);
        ti.resetPhaseTicks();
        ti.setActive(false);
        data.setActiveTransformation(null);

        // Start trigger cooldown if this was a triggered transformation
        if (ti.isTriggeredByCondition() && transType.hasTrigger()) {
            ti.startTriggerCooldown(transType.getTrigger().getTriggerCooldownTicks());
        }
        ti.setTriggeredByCondition(false);

        transType.onDeTransformComplete(ctx);
    }

    /**
     * Cancels a transformation during the TRANSFORMING_IN phase.
     * Called when the player takes damage and the transformation is cancelable.
     */
    public static void cancelTransformation(ServerPlayer player, SomniumPlayerData data,
                                            TransformationAbilityType transType,
                                            TransformationInstance ti) {
        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), ti, data.findBarSlot(transType));

        ti.setPhase(TransformationPhase.IDLE);
        ti.resetPhaseTicks();
        ti.setActive(false);
        data.setActiveTransformation(null);

        if (ti.isTriggeredByCondition() && transType.hasTrigger()) {
            ti.startTriggerCooldown(transType.getTrigger().getTriggerCooldownTicks());
        }
        ti.setTriggeredByCondition(false);

        transType.onTransformCanceled(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Attribute modifier helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies all attribute modifiers defined by the transformation.
     */
    private static void applyAttributeModifiers(ServerPlayer player,
                                                TransformationAbilityType transType) {
        for (Map.Entry<net.minecraft.world.entity.ai.attributes.Attribute,
                TransformationAbilityType.AttributeModifierEntry> entry :
                transType.getAttributeModifiers().entrySet()) {

            AttributeInstance attrInstance = player.getAttribute(entry.getKey());
            if (attrInstance != null) {
                AttributeModifier modifier = entry.getValue().createModifier();
                // Remove existing modifier with same UUID first (safety)
                attrInstance.removeModifier(modifier.getId());
                attrInstance.addTransientModifier(modifier);
            }
        }
    }

    /**
     * Removes all attribute modifiers defined by the transformation.
     */
    private static void removeAttributeModifiers(ServerPlayer player,
                                                 TransformationAbilityType transType) {
        for (Map.Entry<net.minecraft.world.entity.ai.attributes.Attribute,
                TransformationAbilityType.AttributeModifierEntry> entry :
                transType.getAttributeModifiers().entrySet()) {

            AttributeInstance attrInstance = player.getAttribute(entry.getKey());
            if (attrInstance != null) {
                attrInstance.removeModifier(entry.getValue().getUuid());
            }
        }
    }

    // Private constructor — utility class
    private TransformationTickHandler() {}
}