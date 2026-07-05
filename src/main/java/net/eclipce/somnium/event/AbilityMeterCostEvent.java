package net.eclipce.somnium.event;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.meter.MeterCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * Forge event fired whenever an ability's {@link MeterCost} is about to be
 * evaluated for a specific player — both in the afford-check
 * ({@code AbilityType.canAffordMeterCosts}) and in the drain
 * ({@code AbilityType.applyMeterCosts}). Subscribe on
 * {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}.
 *
 * <p>Listeners can scale the cost per player by adjusting {@link #setMultiplier}.
 * The multiplier applies to BOTH the drain amount and the
 * {@code requiredMinimum} activation gate, so the "can I afford it" check and
 * the actual drain always agree. Only {@link MeterCost.Operation#DRAIN} costs
 * fire this event — ADD/MULTIPLY/DIVIDE costs are never scaled.</p>
 *
 * <p>Because the event fires once in the afford-check and once in the apply
 * path of the same activation, listeners MUST be deterministic for a given
 * (player, ability, meter) — derive the multiplier from player data, not from
 * randomness or mutable transient state, or the gate and the drain can
 * disagree.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onMeterCost(AbilityMeterCostEvent event) {
 *     if (!MY_METER_ID.equals(event.getMeterId())) return;
 *     double proficiency = event.getData().getPowerProgress().getXP(trackFor(event.getAbility()));
 *     event.setMultiplier(event.getMultiplier() * efficiencyCurve(proficiency));
 * }
 * }</pre>
 */
public class AbilityMeterCostEvent extends Event {

    @Nullable
    private final ServerPlayer player;
    private final SomniumPlayerData data;
    private final AbilityType ability;
    @Nullable
    private final ResourceLocation meterId;
    private final MeterCost baseCost;

    private float multiplier = 1.0f;

    /**
     * @param player   the player activating the ability; may be {@code null} when the
     *                 cost is evaluated without a player in scope (legacy call paths)
     * @param data     the player's Somnium data (never null)
     * @param ability  the ability whose cost is being evaluated
     * @param meterId  the meter this cost applies to, or {@code null} for the
     *                 built-in stamina meter
     * @param baseCost the immutable cost as declared in the ability's Properties
     */
    public AbilityMeterCostEvent(@Nullable ServerPlayer player, SomniumPlayerData data,
                                 AbilityType ability, @Nullable ResourceLocation meterId,
                                 MeterCost baseCost) {
        this.player = player;
        this.data = data;
        this.ability = ability;
        this.meterId = meterId;
        this.baseCost = baseCost;
    }

    /** @return the activating player, or null if unavailable in this call path */
    @Nullable
    public ServerPlayer getPlayer() { return player; }

    /** @return the player's Somnium data */
    public SomniumPlayerData getData() { return data; }

    /** @return the ability whose cost is being evaluated */
    public AbilityType getAbility() { return ability; }

    /** @return the meter id, or {@code null} for the built-in stamina meter */
    @Nullable
    public ResourceLocation getMeterId() { return meterId; }

    /** @return the declared, unmodified cost */
    public MeterCost getBaseCost() { return baseCost; }

    /** @return the current multiplier (starts at 1.0) */
    public float getMultiplier() { return multiplier; }

    /**
     * Sets the cost multiplier. Clamped to be non-negative. Applies to both the
     * drain amount and the required-minimum gate.
     */
    public void setMultiplier(float multiplier) {
        this.multiplier = Math.max(0f, multiplier);
    }

    /** @return the drain amount after the current multiplier is applied */
    public float getScaledAmount() {
        return baseCost.scaledAmount(multiplier);
    }
}