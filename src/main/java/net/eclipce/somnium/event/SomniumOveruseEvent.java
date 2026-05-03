package net.eclipce.somnium.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge events fired when overuse state changes. Subscribe on
 * {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}.
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onOveruseEnter(SomniumOveruseEvent.Enter event) {
 *     ServerPlayer player = event.getPlayer();
 *     int stage = event.getStage();
 *     // Play a warning sound, show custom UI, etc.
 * }
 *
 * @SubscribeEvent
 * public static void onOveruseLeave(SomniumOveruseEvent.Leave event) {
 *     // Player recovered from overuse
 * }
 *
 * @SubscribeEvent
 * public static void onGrace(SomniumOveruseEvent.Grace event) {
 *     // Player hit grace period (first depletion)
 * }
 * }</pre>
 */
public abstract class SomniumOveruseEvent extends Event {

    private final ServerPlayer player;

    protected SomniumOveruseEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }

    /**
     * Fired when the player enters a new overuse stage.
     * Stage advances each time: 1 → 2 → 3 → 4 → 5.
     */
    public static class Enter extends SomniumOveruseEvent {
        private final int stage;
        private final int effectDurationTicks;

        public Enter(ServerPlayer player, int stage, int effectDurationTicks) {
            super(player);
            this.stage = stage;
            this.effectDurationTicks = effectDurationTicks;
        }

        /** @return the new overuse stage (1-5) */
        public int getStage() { return stage; }

        /** @return the duration of the Overuse effect in ticks */
        public int getEffectDurationTicks() { return effectDurationTicks; }
    }

    /**
     * Fired when overuse effects expire (timer ran out or window expired).
     * The player is no longer under overuse penalties.
     */
    public static class Leave extends SomniumOveruseEvent {
        private final int previousStage;

        public Leave(ServerPlayer player, int previousStage) {
            super(player);
            this.previousStage = previousStage;
        }

        /** @return the stage the player was at before recovery */
        public int getPreviousStage() { return previousStage; }
    }

    /**
     * Fired when the player enters the grace period (first depletion).
     * No overuse effect is applied, but stamina is negative.
     */
    public static class Grace extends SomniumOveruseEvent {
        public Grace(ServerPlayer player) {
            super(player);
        }
    }
}