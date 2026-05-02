package net.eclipce.somnium.tick;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationInstance;
import net.eclipce.somnium.core.ability.transformation.TransformationPhase;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.effects.OveruseEffect;
import net.eclipce.somnium.core.effects.SomniumEffects;
import net.eclipce.somnium.network.SomniumNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * The server-side tick handler that drives all ability behavior each tick.
 *
 * <p>Registered on the Forge event bus, this handler listens to
 * {@link TickEvent.PlayerTickEvent} and processes ability logic for each
 * server-side player once per tick. It also listens to {@link LivingHurtEvent}
 * to handle damage-based transformation cancellation.</p>
 *
 * <h3>What runs each tick (per player)</h3>
 * <ol>
 *     <li><strong>Cooldown ticking:</strong> All ability instances with active
 *         cooldowns have their cooldown decremented by one tick.</li>
 *     <li><strong>Active ability ticking:</strong> TOGGLE abilities that are
 *         active call {@code onActiveTick()}. HOLD abilities that are active
 *         call {@code onKeyHeld()} and increment charge ticks. CHARGED abilities
 *         that are active call {@code onChargeTick()} and increment charge ticks.</li>
 *     <li><strong>Passive ticking:</strong> All enabled passive abilities call
 *         {@code onPassiveTick()}.</li>
 *     <li><strong>Transformation ticking:</strong> If a transformation is active,
 *         its phase state machine is advanced. If not, triggers on transformation
 *         abilities are evaluated for involuntary activation.</li>
 *     <li><strong>Sync:</strong> If any data changed this tick, a sync packet
 *         is sent to the client.</li>
 * </ol>
 *
 * <p>All tick logic runs on the server only. The client receives the results
 * via sync packets.</p>
 */
@Mod.EventBusSubscriber(modid = Somnium.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SomniumTickHandler {

    /**
     * Main tick entry point. Fires twice per tick (START and END phase).
     * We only process on END to run once per tick, after vanilla player
     * logic has already executed.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only process server-side, at the END phase
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;

        SomniumPlayerData data = SomniumCapability.get(serverPlayer);
        if (data == null) return;

        // Process all ability ticking
        tickCooldowns(data);
        tickActiveAbilities(serverPlayer, data);
        tickPassiveAbilities(serverPlayer, data);
        tickTransformations(serverPlayer, data);
        tickMeters(serverPlayer, data);

        // Sync to client if anything changed
        SomniumNetwork.syncIfDirty(serverPlayer);
    }

    /**
     * Handles damage-based transformation cancellation.
     * If a player takes damage during the TRANSFORMING_IN phase and the
     * transformation is marked as cancelable by damage, the transformation
     * is canceled.
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null || !data.hasActiveTransformation()) return;

        TransformationInstance ti = data.getActiveTransformationInstance();
        if (ti == null || ti.getPhase() != TransformationPhase.TRANSFORMING_IN) return;

        AbilityType type = ti.getAbilityType();
        if (!(type instanceof TransformationAbilityType transType)) return;

        if (transType.isCancelableByDamage()) {
            TransformationTickHandler.cancelTransformation(player, data, transType, ti);
            data.markDirty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tick subsystems
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Decrements cooldowns on all ability instances that are on cooldown.
     * Only marks data dirty when a cooldown finishes (transitions to 0),
     * not every tick. The client predicts intermediate cooldown values
     * locally for smooth display.
     */
    private static void tickCooldowns(SomniumPlayerData data) {
        for (AbilityInstance instance : data.getAbilityInventory().values()) {
            if (instance.isOnCooldown()) {
                instance.tickCooldown();
                // Only sync when cooldown finishes — client predicts the rest
                if (!instance.isOnCooldown()) {
                    data.markDirty();
                }
            }
        }
    }

    /**
     * Ticks active TOGGLE, HOLD, and CHARGED abilities on the bar.
     * Only abilities that are currently in an active state get ticked.
     */
    private static void tickActiveAbilities(ServerPlayer player, SomniumPlayerData data) {
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            AbilityInstance instance = data.getBarSlotInstance(slot);
            if (instance == null || !instance.isActive()) continue;

            AbilityType type = instance.getAbilityType();
            ActivationType activation = type.getActivationType();

            // Skip transformations — they're handled separately
            if (type instanceof TransformationAbilityType) continue;

            AbilityActivationContext ctx = new AbilityActivationContext(
                    player, player.level(), instance, slot);

            switch (activation) {
                case TOGGLE -> {
                    type.onActiveTick(ctx);
                }
                case HOLD -> {
                    instance.incrementChargeTicks();
                    type.onKeyHeld(ctx);
                }
                case CHARGED -> {
                    instance.incrementChargeTicks();
                    type.onChargeTick(ctx, instance.getChargeTicks());
                }
                // INSTANT and PASSIVE don't tick from bar slots
                default -> {}
            }
        }
    }

    /**
     * Ticks all enabled passive abilities. Passives tick regardless of
     * bar state since they don't live on the bar.
     */
    private static void tickPassiveAbilities(ServerPlayer player, SomniumPlayerData data) {
        for (Map.Entry<ResourceLocation, Boolean> entry : data.getPassiveStates().entrySet()) {
            if (!entry.getValue()) continue; // Skip disabled passives

            AbilityInstance instance = data.getAbilityInstance(entry.getKey());
            if (instance == null) continue;

            AbilityType type = instance.getAbilityType();
            if (type.getActivationType() != ActivationType.PASSIVE) continue;

            AbilityActivationContext ctx = new AbilityActivationContext(
                    player, player.level(), instance);
            type.onPassiveTick(ctx);
        }
    }

    /**
     * Handles all transformation-related ticking:
     * - If a transformation is active, ticks its phase state machine
     * - If no transformation is active, evaluates triggers on all
     *   transformation abilities the player has
     */
    private static void tickTransformations(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation activeTransId = data.getActiveTransformation();

        if (activeTransId != null) {
            // Tick the active transformation's phase state machine
            TransformationTickHandler.tickActiveTransformation(player, data, activeTransId);
            data.markDirty();
        } else {
            // No active transformation — evaluate triggers
            evaluateTransformationTriggers(player, data);
        }
    }

    /**
     * Evaluates transformation triggers for all transformation abilities
     * the player has. Only runs when no transformation is currently active.
     */
    private static void evaluateTransformationTriggers(ServerPlayer player,
                                                       SomniumPlayerData data) {
        for (Map.Entry<ResourceLocation, AbilityInstance> entry :
                data.getAbilityInventory().entrySet()) {

            AbilityInstance instance = entry.getValue();
            if (!(instance instanceof TransformationInstance ti)) continue;

            AbilityType type = instance.getAbilityType();
            if (!(type instanceof TransformationAbilityType transType)) continue;

            if (!transType.hasTrigger()) continue;

            // Check if the ability is unlocked
            if (!data.isAbilityUnlocked(type)) continue;

            TransformationTickHandler.evaluateTrigger(player, data, transType, ti);

            // If a trigger fired and activated a transformation, stop
            // evaluating other triggers this tick
            if (data.hasActiveTransformation()) break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Meter ticking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ticks all meters (stamina + custom). Handles regeneration and
     * depletion effects.
     */
    private static void tickMeters(ServerPlayer player, SomniumPlayerData data) {
        // Tick stamina
        net.eclipce.somnium.core.meter.StaminaData stamina = data.getStaminaData();
        stamina.tick();

        // Handle overuse state transitions
        if (stamina.consumeEnteredOveruse()) {
            // Entered a new overuse stage — remove old effect, apply new Overuse effect
            player.removeEffect(SomniumEffects.OVERUSE.get());
            player.addEffect(OveruseEffect.createInstance(
                    SomniumEffects.OVERUSE.get(),
                    stamina.getOveruseStage(),
                    stamina.getEffectTimer()));
        }

        if (stamina.consumeEffectsExpired()) {
            // Effect timer ran out — remove Overuse effect
            player.removeEffect(SomniumEffects.OVERUSE.get());
        }

        // Tick custom meters
        for (java.util.Map.Entry<net.minecraft.resources.ResourceLocation,
                net.eclipce.somnium.core.meter.MeterInstance> entry
                : data.getAllMeters().entrySet()) {
            net.eclipce.somnium.core.meter.MeterInstance meter = entry.getValue();
            boolean meterDepleted = meter.tick();

            if (meterDepleted) {
                net.eclipce.somnium.core.meter.MeterDefinition def =
                        net.eclipce.somnium.core.meter.MeterDefinition.get(entry.getKey());
                if (def != null && def.getOnDepleted() != null) {
                    def.getOnDepleted().accept(player);
                }
            }
        }
    }
}