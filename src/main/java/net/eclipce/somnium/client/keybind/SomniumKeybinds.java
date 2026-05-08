package net.eclipce.somnium.client.keybind;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Defines all keybindings for the Somnium API.
 *
 * <p>All keybinds are in the {@code "Somnium"} category in the Controls menu.
 * Players can rebind all keys through the standard Minecraft controls screen.</p>
 *
 * <h3>Default keybinds</h3>
 * <ul>
 *     <li>Ability Slots 1–6: Z, X, C, V, B, G</li>
 *     <li>Page Toggle: Left Alt</li>
 *     <li>Ability Inventory: P</li>
 *     <li>Transformation Quick Bind: T (WIP)</li>
 * </ul>
 */
public final class SomniumKeybinds {

    /** The keybind category name shown in the Controls menu. */
    public static final String CATEGORY = "key.categories." + Somnium.MOD_ID;

    // ═══════════════════════════════════════════════════════════════════
    //  Ability bar slot keybinds (6 slots)
    // ═══════════════════════════════════════════════════════════════════

    /** Keybind for ability bar slot 1. Default: Z */
    public static final KeyMapping ABILITY_SLOT_1 = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_slot_1",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    /** Keybind for ability bar slot 2. Default: X */
    public static final KeyMapping ABILITY_SLOT_2 = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_slot_2",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    /** Keybind for ability bar slot 3. Default: C */
    public static final KeyMapping ABILITY_SLOT_3 = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_slot_3",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    /** Keybind for ability bar slot 4. Default: V */
    public static final KeyMapping ABILITY_SLOT_4 = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_slot_4",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** Keybind for ability bar slot 5. Default: B */
    public static final KeyMapping ABILITY_SLOT_5 = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_slot_5",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    /** Keybind for ability bar slot 6. Default: G */
    public static final KeyMapping ABILITY_SLOT_6 = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_slot_6",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /**
     * Array of all slot keybinds for indexed access.
     * Index matches bar slot index (0–5).
     */
    public static final KeyMapping[] ABILITY_SLOTS = {
            ABILITY_SLOT_1, ABILITY_SLOT_2, ABILITY_SLOT_3,
            ABILITY_SLOT_4, ABILITY_SLOT_5, ABILITY_SLOT_6
    };

    // ═══════════════════════════════════════════════════════════════════
    //  Utility keybinds
    // ═══════════════════════════════════════════════════════════════════

    /** Keybind to toggle between ability bar pages. Default: Left Alt */
    public static final KeyMapping PAGE_TOGGLE = new KeyMapping(
            "key." + Somnium.MOD_ID + ".page_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    /** Keybind to open the ability inventory screen. Default: P */
    public static final KeyMapping ABILITY_INVENTORY = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_inventory",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);

    /** Keybind for transformation quick activation (WIP). Default: T */
    public static final KeyMapping TRANSFORMATION_QUICKBIND = new KeyMapping(
            "key." + Somnium.MOD_ID + ".transformation",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    /** Keybind for Utility category bar toggle (test content). Default: ~ (grave accent) */
    public static final KeyMapping UTILITY_BAR = new KeyMapping(
            "key." + Somnium.MOD_ID + ".utility_bar",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);

    // ═══════════════════════════════════════════════════════════════════
    //  Utility methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns all keybinds that need to be registered.
     * Called by the registration event handler.
     *
     * @return array of all Somnium key mappings
     */
    public static KeyMapping[] getAllKeybinds() {
        return new KeyMapping[]{
                ABILITY_SLOT_1, ABILITY_SLOT_2, ABILITY_SLOT_3,
                ABILITY_SLOT_4, ABILITY_SLOT_5, ABILITY_SLOT_6,
                PAGE_TOGGLE, ABILITY_INVENTORY, TRANSFORMATION_QUICKBIND,
                UTILITY_BAR
        };
    }

    /**
     * Gets the display name of a keybind for rendering on the HUD.
     * Returns the key name (e.g., "Z", "X", "LAlt") suitable for
     * display next to the ability bar slot.
     *
     * @param slot the bar slot index (0–5)
     * @return the display string for the bound key
     */
    public static String getSlotKeyName(int slot) {
        if (slot < 0 || slot >= SomniumPlayerData.BAR_SIZE) return "?";
        return ABILITY_SLOTS[slot].getTranslatedKeyMessage().getString();
    }

    // Private constructor — utility class
    private SomniumKeybinds() {}
}