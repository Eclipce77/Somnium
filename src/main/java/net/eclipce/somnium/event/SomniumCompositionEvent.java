package net.eclipce.somnium.event;

import net.eclipce.somnium.core.composition.CompositionSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge event fired when a player's composition value changes.
 * Subscribe on {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}.
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onCompositionGrowth(SomniumCompositionEvent event) {
 *     if (event.getSource() == CompositionSource.OVERUSE_SURVIVAL) {
 *         // Play a power-up sound when growing through overuse
 *         event.getPlayer().playNotifySound(SoundEvents.PLAYER_LEVELUP, ...);
 *     }
 *
 *     // Scale a custom meter with composition
 *     double comp = event.getNewValue();
 *     myMeter.setMaxValue(50 + (float)(comp * 0.3));
 * }
 * }</pre>
 */
public class SomniumCompositionEvent extends Event {

    private final ServerPlayer player;
    private final double oldValue;
    private final double newValue;
    private final double amountGained;
    private final CompositionSource source;

    public SomniumCompositionEvent(ServerPlayer player, double oldValue,
                                   double newValue, double amountGained,
                                   CompositionSource source) {
        this.player = player;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.amountGained = amountGained;
        this.source = source;
    }

    public ServerPlayer getPlayer() { return player; }

    /** @return composition value before the change */
    public double getOldValue() { return oldValue; }

    /** @return composition value after the change */
    public double getNewValue() { return newValue; }

    /** @return the actual amount added (after diminishing returns) */
    public double getAmountGained() { return amountGained; }

    /** @return what caused the growth */
    public CompositionSource getSource() { return source; }

    /** @return true if the integer part changed (stamina max changed) */
    public boolean didWholeNumberChange() {
        return (int) oldValue != (int) newValue;
    }
}