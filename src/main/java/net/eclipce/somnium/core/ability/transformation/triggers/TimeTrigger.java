package net.eclipce.somnium.core.ability.transformation.triggers;

import net.eclipce.somnium.core.ability.transformation.TransformationTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * A transformation trigger based on the in-game time of day.
 *
 * <p>Activates when the world's day time falls within a specified tick range.
 * Minecraft's day cycle runs from 0 to 24000 ticks:</p>
 * <ul>
 *     <li>0 = sunrise (06:00)</li>
 *     <li>6000 = noon (12:00)</li>
 *     <li>12000 = sunset (18:00)</li>
 *     <li>13000 = night begins (19:00)</li>
 *     <li>18000 = midnight (00:00)</li>
 *     <li>23000 = night ends (05:00)</li>
 * </ul>
 *
 * <h3>Example: Werewolf transformation at night</h3>
 * <pre>{@code
 * new TimeTrigger(13000, 23000, true, false, 0)
 * // Triggers at nightfall, auto-reverts at dawn, not suppressible
 * }</pre>
 *
 * <p>Supports wrap-around ranges (e.g., 22000 to 2000 spans midnight).
 * The trigger correctly handles the day cycle wrapping from 24000 back to 0.</p>
 */
public class TimeTrigger extends TransformationTrigger {

    private final long startTick;
    private final long endTick;

    /**
     * Creates a time-based transformation trigger.
     *
     * @param startTick                the day time tick when the trigger becomes active
     * @param endTick                  the day time tick when the trigger deactivates.
     *                                  If endTick &lt; startTick, the range wraps around
     *                                  midnight (e.g., 22000 to 2000).
     * @param autoRevertOnConditionEnd if true, the transformation ends when the time
     *                                  leaves the specified range
     * @param suppressible             if true, the player can resist this trigger
     * @param triggerCooldownTicks     minimum ticks between forced activations
     */
    public TimeTrigger(long startTick, long endTick,
                       boolean autoRevertOnConditionEnd,
                       boolean suppressible,
                       int triggerCooldownTicks) {
        super(autoRevertOnConditionEnd, suppressible, triggerCooldownTicks);
        this.startTick = startTick % 24000;
        this.endTick = endTick % 24000;
    }

    /**
     * Convenience constructor with common defaults: auto-revert enabled,
     * not suppressible, no cooldown.
     *
     * @param startTick the day time tick when the trigger becomes active
     * @param endTick   the day time tick when the trigger deactivates
     */
    public TimeTrigger(long startTick, long endTick) {
        this(startTick, endTick, true, false, 0);
    }

    @Override
    public boolean test(ServerPlayer player) {
        long dayTime = player.level().getDayTime() % 24000;

        if (startTick <= endTick) {
            // Normal range (e.g., 13000 to 23000)
            return dayTime >= startTick && dayTime < endTick;
        } else {
            // Wrap-around range (e.g., 22000 to 2000 spans midnight)
            return dayTime >= startTick || dayTime < endTick;
        }
    }

    /** @return the start tick of the active time range */
    public long getStartTick() {
        return startTick;
    }

    /** @return the end tick of the active time range */
    public long getEndTick() {
        return endTick;
    }
}