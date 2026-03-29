package net.eclipce.somnium.core.data;

import net.eclipce.somnium.Somnium;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge event handler that manages the Somnium capability lifecycle.
 *
 * <p>Handles two critical events:</p>
 * <ul>
 *     <li>{@link AttachCapabilitiesEvent} — attaches a {@link SomniumDataProvider}
 *         to every {@link Player} entity when it's created</li>
 *     <li>{@link PlayerEvent.Clone} — copies capability data from the old
 *         player entity to the new one on respawn or returning from the End</li>
 * </ul>
 *
 * <p>This class is registered on the Forge event bus (not the mod event bus)
 * via {@link Mod.EventBusSubscriber}. It's automatically discovered and
 * instantiated by Forge — no manual registration needed.</p>
 */
@Mod.EventBusSubscriber(modid = Somnium.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SomniumCapabilityHandler {

    /** The ResourceLocation used to identify this capability on entities. */
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(Somnium.MOD_ID, "player_data");

    /**
     * Attaches the Somnium capability to every Player entity.
     *
     * <p>This fires for ALL entities, so we check for Player specifically.
     * The generic type must be {@code Entity}, not {@code Player}, because
     * Forge doesn't fire the event for subclasses.</p>
     *
     * @param event the capability attach event
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            SomniumDataProvider provider = new SomniumDataProvider();
            event.addCapability(CAPABILITY_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    /**
     * Copies Somnium data from the old player entity to the new one.
     *
     * <p>This fires in two scenarios:</p>
     * <ul>
     *     <li><strong>Death:</strong> {@code event.isWasDeath() == true}.
     *         We copy the data, then apply death persistence rules
     *         (removing non-persistent powers/abilities).</li>
     *     <li><strong>Returning from the End:</strong> {@code event.isWasDeath() == false}.
     *         We copy the data as-is, since the player didn't die.</li>
     * </ul>
     *
     * <p>Without this handler, all Somnium data would be lost on respawn
     * because Forge creates a completely new Player entity.</p>
     *
     * @param event the player clone event
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();

        // Forge 1.20.1 requires this call to access capabilities on the old player
        original.reviveCaps();

        SomniumPlayerData oldData = SomniumCapability.get(original);
        SomniumPlayerData newData = SomniumCapability.get(newPlayer);

        if (oldData != null && newData != null) {
            // Copy all data to the new entity
            newData.copyFrom(oldData);

            // If this was a death (not returning from End), apply death rules
            if (event.isWasDeath()) {
                newData.handleDeath();
            }
        }

        // Re-invalidate after we're done reading
        original.invalidateCaps();
    }
}