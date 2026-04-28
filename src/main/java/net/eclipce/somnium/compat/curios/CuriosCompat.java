package net.eclipce.somnium.compat.curios;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Curios API compatibility layer. Detects Curios at runtime and
 * conditionally initializes event handlers and item support.
 *
 * <p>All Curios-dependent code lives in the {@code compat.curios}
 * package. Core Somnium classes never import Curios directly —
 * this layer bridges the two APIs.</p>
 *
 * <p>If Curios is not present, this class gracefully no-ops.
 * The mod functions fully without it.</p>
 */
public final class CuriosCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Somnium/Curios");
    private static boolean curiosLoaded = false;
    private static boolean initialized = false;

    /**
     * Checks if Curios is loaded. Safe to call at any time.
     */
    public static boolean isLoaded() {
        if (!initialized) {
            curiosLoaded = ModList.get().isLoaded("curios");
            initialized = true;
            if (curiosLoaded) {
                LOGGER.info("Curios API detected — Somnium curio support enabled");
            } else {
                LOGGER.info("Curios API not found — running without curio support");
            }
        }
        return curiosLoaded;
    }

    /**
     * Initializes Curios integration on the common side.
     * Registers event handlers for curio equip/unequip events.
     * Only executes if Curios is loaded.
     */
    public static void init() {
        if (!isLoaded()) return;
        try {
            CuriosEventHandler.register();
            LOGGER.info("Curios integration initialized");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Curios integration", e);
            curiosLoaded = false;
        }
    }

    private CuriosCompat() {}
}
