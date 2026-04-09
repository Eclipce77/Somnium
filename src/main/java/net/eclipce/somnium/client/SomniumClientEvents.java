package net.eclipce.somnium.client;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.client.keybind.SomniumKeybinds;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.network.ActivateAbilityPacket;
import net.eclipce.somnium.network.SomniumNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handler for the Somnium API.
 *
 * <p>This class handles three responsibilities:</p>
 * <ul>
 *     <li><strong>Registration</strong> — registers keybinds and the HUD overlay
 *         during mod loading via mod bus events</li>
 *     <li><strong>Key input</strong> — processes ability bar key presses/releases
 *         each client tick, sending packets to the server</li>
 *     <li><strong>State tracking</strong> — tracks which ability keys are currently
 *         held down, enabling HOLD and CHARGED ability detection</li>
 * </ul>
 *
 * <p>Split into two inner classes because keybind/overlay registration happens
 * on the mod event bus, while tick handling happens on the Forge event bus.</p>
 */
public class SomniumClientEvents {

    // ═══════════════════════════════════════════════════════════════════
    //  Key state tracking for press/release detection
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tracks whether each ability slot key was held down last tick.
     * Used to detect press (false→true) and release (true→false) transitions.
     */
    private static final boolean[] slotKeyHeld = new boolean[SomniumPlayerData.BAR_SIZE];

    // ═══════════════════════════════════════════════════════════════════
    //  Mod bus events (registration)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles mod bus events for client-side registration.
     * {@code @Mod.EventBusSubscriber} with {@code bus = MOD} and
     * {@code value = Dist.CLIENT} ensures these only fire on the
     * physical client during mod loading.
     */
    @Mod.EventBusSubscriber(modid = Somnium.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {

        /**
         * Registers all Somnium keybinds with Forge.
         */
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            for (KeyMapping keybind : SomniumKeybinds.getAllKeybinds()) {
                event.register(keybind);
            }
            Somnium.LOGGER.debug("Somnium keybinds registered");
        }

        /**
         * Registers the ability bar HUD overlay.
         * Renders above the hotbar so it doesn't conflict with vanilla UI.
         */
        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("ability_bar", new AbilityBarOverlay());
            Somnium.LOGGER.debug("Somnium ability bar overlay registered");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Forge bus events (runtime key handling)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles Forge bus events for runtime key input processing.
     * Uses {@code Dist.CLIENT} to ensure this only runs on the physical client.
     */
    @Mod.EventBusSubscriber(modid = Somnium.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusEvents {

        /**
         * Processes keybind input each client tick. Detects press and release
         * transitions for ability slot keys and sends the appropriate packets
         * to the server.
         *
         * <p>Uses {@code KeyMapping#isDown()} for continuous state tracking
         * (needed for HOLD/CHARGED abilities) rather than
         * {@code consumeClick()} (which only catches the initial press).</p>
         */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            // Process ability slot keys — detect press/release transitions
            for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
                boolean isDown = SomniumKeybinds.ABILITY_SLOTS[slot].isDown();
                boolean wasDown = slotKeyHeld[slot];

                if (isDown && !wasDown) {
                    // Key just pressed — send PRESS
                    SomniumNetwork.sendToServer(
                            new ActivateAbilityPacket(slot, ActivateAbilityPacket.Action.PRESS));
                } else if (!isDown && wasDown) {
                    // Key just released — send RELEASE
                    SomniumNetwork.sendToServer(
                            new ActivateAbilityPacket(slot, ActivateAbilityPacket.Action.RELEASE));
                }

                slotKeyHeld[slot] = isDown;
            }

            // Process utility keys
            if (SomniumKeybinds.PAGE_TOGGLE.consumeClick()) {
                SomniumPlayerData localData = ClientAbilityData.getLocalData();
                if (localData != null) {
                    int nextPage = (localData.getActivePage() + 1) % SomniumPlayerData.MAX_PAGES;
                    localData.setActivePage(nextPage);
                    AbilityBarOverlay.cyclePage();
                }
            }

            if (SomniumKeybinds.ABILITY_INVENTORY.consumeClick()) {
                SomniumPlayerData localData = ClientAbilityData.getLocalData();
                if (localData != null) {
                    mc.setScreen(new AbilityInventoryScreen(localData));
                }
            }

            if (SomniumKeybinds.TRANSFORMATION_QUICKBIND.consumeClick()) {
                // TODO: Quick-activate the player's transformation ability
                // This will find the first transformation on the bar and
                // send an activation packet for it
            }
        }
    }
}