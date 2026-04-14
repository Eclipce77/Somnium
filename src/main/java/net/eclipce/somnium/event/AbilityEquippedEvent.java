package net.eclipce.somnium.event;

import net.eclipce.somnium.core.ability.AbilityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus (server-side) when an ability is about to be
 * equipped to a bar slot. Canceling this event prevents the equip action.
 *
 * <p>This event fires from the server-side packet handler for
 * {@link net.eclipce.somnium.network.UpdateBarSlotPacket}, before the
 * bar slot is actually modified. If canceled, the bar slot remains unchanged
 * and the client receives a re-sync with the unmodified state.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onAbilityEquipped(AbilityEquippedEvent event) {
 *     if (someCondition(event.getAbilityType())) {
 *         event.setCanceled(true); // prevent equipping
 *     }
 * }
 * }</pre>
 */
@Cancelable
public class AbilityEquippedEvent extends Event {

    private final ServerPlayer player;
    private final AbilityType abilityType;
    private final int page;
    private final int slot;

    public AbilityEquippedEvent(ServerPlayer player, AbilityType abilityType, int page, int slot) {
        this.player = player;
        this.abilityType = abilityType;
        this.page = page;
        this.slot = slot;
    }

    /** @return the player equipping the ability */
    public ServerPlayer getPlayer() { return player; }

    /** @return the ability type being equipped */
    public AbilityType getAbilityType() { return abilityType; }

    /** @return the bar page the ability is being equipped to */
    public int getPage() { return page; }

    /** @return the bar slot the ability is being equipped to */
    public int getSlot() { return slot; }
}