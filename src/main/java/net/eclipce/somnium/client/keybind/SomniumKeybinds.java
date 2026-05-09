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

    /** Keybind to open the ability inventory screen. Default: I */
    public static final KeyMapping ABILITY_INVENTORY = new KeyMapping(
            "key." + Somnium.MOD_ID + ".ability_inventory",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);

    /** Keybind for transformation quick activation (WIP). Default: Y */
    public static final KeyMapping TRANSFORMATION_QUICKBIND = new KeyMapping(
            "key." + Somnium.MOD_ID + ".transformation",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

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
                PAGE_TOGGLE, ABILITY_INVENTORY, TRANSFORMATION_QUICKBIND
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
    /**
     * Gets an abbreviated display name for the key bound to a slot.
     * Common keys use standard abbreviations. Returns empty string
     * for mouse buttons (use {@link #isSlotMouseBound} instead).
     */
    public static String getSlotKeyName(int slot) {
        if (slot < 0 || slot >= SomniumPlayerData.BAR_SIZE) return "?";
        String raw = ABILITY_SLOTS[slot].getTranslatedKeyMessage().getString();
        return abbreviateKeyName(raw);
    }

    /**
     * Checks if a slot's keybind is a mouse button.
     */
    public static boolean isSlotMouseBound(int slot) {
        if (slot < 0 || slot >= SomniumPlayerData.BAR_SIZE) return false;
        return ABILITY_SLOTS[slot].getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE;
    }

    /**
     * Gets the mouse button index for a slot (0=left, 1=right, 2=middle, 3=button4, 4=button5).
     * Returns -1 if not a mouse button.
     */
    public static int getSlotMouseButton(int slot) {
        if (!isSlotMouseBound(slot)) return -1;
        return ABILITY_SLOTS[slot].getKey().getValue();
    }

    /**
     * Abbreviates common key names for compact display on the ability bar.
     */
    private static String abbreviateKeyName(String name) {
        if (name == null || name.isEmpty()) return "?";
        return switch (name.toLowerCase()) {
            case "left control" -> "Ctrl";
            case "right control" -> "RCtrl";
            case "left shift" -> "Shft";
            case "right shift" -> "RShft";
            case "left alt" -> "Alt";
            case "right alt" -> "RAlt";
            case "caps lock" -> "Caps";
            case "tab" -> "Tab";
            case "space", "spacebar" -> "Space";
            case "escape" -> "Esc";
            case "enter", "return" -> "Ent";
            case "backspace" -> "Bksp";
            case "delete" -> "Del";
            case "insert" -> "Ins";
            case "home" -> "Hm";
            case "end" -> "End";
            case "page up" -> "PgU";
            case "page down" -> "PgD";
            case "print screen" -> "PrtS";
            case "scroll lock" -> "ScrL";
            case "pause" -> "Pse";
            case "num lock" -> "NmLk";
            case "numpad 0" -> "Nm0";
            case "numpad 1" -> "Nm1";
            case "numpad 2" -> "Nm2";
            case "numpad 3" -> "Nm3";
            case "numpad 4" -> "Nm4";
            case "numpad 5" -> "Nm5";
            case "numpad 6" -> "Nm6";
            case "numpad 7" -> "Nm7";
            case "numpad 8" -> "Nm8";
            case "numpad 9" -> "Nm9";
            case "numpad add" -> "Nm+";
            case "numpad subtract" -> "Nm-";
            case "numpad multiply" -> "Nm*";
            case "numpad divide" -> "Nm/";
            case "numpad decimal" -> "Nm.";
            case "numpad enter" -> "NmEnt";
            case "left button", "button 1" -> "M1";
            case "right button", "button 2" -> "M2";
            case "middle button", "button 3" -> "M3";
            case "button 4" -> "M4";
            case "button 5" -> "M5";
            default -> {
                // Single character keys stay as-is (A, B, 1, 2, etc.)
                // Shorten "Keypad X" style names
                if (name.length() <= 3) yield name;
                // Function keys stay as-is (F1, F2, etc.)
                if (name.matches("F\\d+")) yield name;
                yield name;
            }
        };
    }

    // Private constructor — utility class
    private SomniumKeybinds() {}
}