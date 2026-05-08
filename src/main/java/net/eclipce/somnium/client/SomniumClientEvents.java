package net.eclipce.somnium.client;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.client.keybind.SomniumKeybinds;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.meter.MeterInstance;
import net.eclipce.somnium.network.ActivateAbilityPacket;
import net.eclipce.somnium.network.QuickCategoryPacket;
import net.eclipce.somnium.network.QuickTransformPacket;
import net.eclipce.somnium.network.SomniumNetwork;
import net.eclipce.somnium.test.TestContent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
            event.registerAboveAll("meter_bar", new MeterOverlay());
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
            if (mc.player == null) return;

            // Tick client-side cooldowns for smooth display (runs even with screen open)
            SomniumPlayerData localData = ClientAbilityData.getLocalData();
            if (localData != null) {
                for (AbilityInstance instance : localData.getAbilityInventory().values()) {
                    if (instance.isOnCooldown()) {
                        instance.tickCooldown();
                    }
                }
                // Client-side meter regen for smooth display
                localData.getStaminaData().clientTick();
                for (net.eclipce.somnium.core.meter.MeterInstance meter
                        : localData.getAllMeters().values()) {
                    meter.tick();
                }
            }

            // Don't process keybinds when a screen is open
            if (mc.screen != null) return;

            // Auto-hide transformation bar when a transformation activates
            if (AbilityBarOverlay.isShowingTransformationBar()
                    && localData != null && localData.hasActiveTransformation()) {
                AbilityBarOverlay.hideTransformationBar();
            }

            // Determine which bar slot keys target
            boolean transBarActive = AbilityBarOverlay.isShowingTransformationBar();
            ResourceLocation activeCatBar = AbilityBarOverlay.getActiveCategoryBar();
            boolean catBarActive = activeCatBar != null;

            // Process ability slot keys — detect press/release transitions
            for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
                boolean isDown = SomniumKeybinds.ABILITY_SLOTS[slot].isDown();
                boolean wasDown = slotKeyHeld[slot];

                if (isDown && !wasDown) {
                    if (catBarActive) {
                        SomniumNetwork.sendToServer(new ActivateAbilityPacket(
                                slot, ActivateAbilityPacket.Action.PRESS, activeCatBar));
                        AbilityBarOverlay.hideCategoryBar();
                    } else if (transBarActive) {
                        SomniumNetwork.sendToServer(new ActivateAbilityPacket(
                                slot, ActivateAbilityPacket.Action.PRESS, 1));
                    } else {
                        SomniumNetwork.sendToServer(new ActivateAbilityPacket(
                                slot, ActivateAbilityPacket.Action.PRESS, 0));
                    }
                } else if (!isDown && wasDown) {
                    // Release always goes to whichever bar was active on press
                    if (transBarActive) {
                        SomniumNetwork.sendToServer(new ActivateAbilityPacket(
                                slot, ActivateAbilityPacket.Action.RELEASE, 1));
                    } else {
                        SomniumNetwork.sendToServer(new ActivateAbilityPacket(
                                slot, ActivateAbilityPacket.Action.RELEASE, 0));
                    }
                }

                slotKeyHeld[slot] = isDown;
            }

            // Process utility keys
            if (SomniumKeybinds.PAGE_TOGGLE.consumeClick()) {
                // Page toggle only applies to ability bar
                if (!transBarActive && !catBarActive && localData != null) {
                    // Find next populated page (wrapping around)
                    int current = localData.getActivePage();
                    int nextPage = current;
                    for (int i = 1; i <= SomniumPlayerData.MAX_PAGES; i++) {
                        int candidate = (current + i) % SomniumPlayerData.MAX_PAGES;
                        // Check if this page has any abilities
                        boolean hasContent = false;
                        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
                            if (localData.getBarSlotKey(candidate, slot) != null) {
                                hasContent = true;
                                break;
                            }
                        }
                        if (hasContent) {
                            nextPage = candidate;
                            break;
                        }
                    }
                    if (nextPage != current) {
                        localData.setActivePage(nextPage);
                        SomniumNetwork.sendToServer(
                                new net.eclipce.somnium.network.SetActivePagePacket(nextPage));
                    }
                }
            }

            if (SomniumKeybinds.ABILITY_INVENTORY.consumeClick()) {
                if (localData != null) {
                    mc.setScreen(new AbilityInventoryScreen(localData));
                }
            }

            // Transformation keybind
            if (SomniumKeybinds.TRANSFORMATION_QUICKBIND.consumeClick()) {
                if (mc.player.isCrouching()) {
                    SomniumNetwork.sendToServer(new QuickTransformPacket());
                } else {
                    AbilityBarOverlay.toggleTransformationBar();
                }
            }

            // Utility bar keybind (test content — demonstrates addon pattern)
            if (SomniumKeybinds.UTILITY_BAR.consumeClick()) {
                if (mc.player.isCrouching()) {
                    SomniumNetwork.sendToServer(
                            new QuickCategoryPacket(TestContent.UTILITY.getId()));
                } else {
                    AbilityBarOverlay.toggleCategoryBar(TestContent.UTILITY.getId());
                }
            }
        }
    }
}