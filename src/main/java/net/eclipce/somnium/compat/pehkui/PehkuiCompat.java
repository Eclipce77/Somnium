package net.eclipce.somnium.compat.pehkui;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pehkui compatibility layer. Detects Pehkui at runtime and enables
 * entity scaling support for Somnium abilities and transformations.
 *
 * <p>All Pehkui-dependent code lives in the {@code compat.pehkui}
 * package. Core Somnium classes never import Pehkui directly —
 * they store scale values as floats, and this layer applies them
 * through the Pehkui API when present.</p>
 *
 * <p>If Pehkui is not present, scale values in TransformationData
 * and abilities are ignored (no visual scaling occurs).</p>
 */
public final class PehkuiCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Somnium/Pehkui");
    private static boolean pehkuiLoaded = false;
    private static boolean initialized = false;

    /**
     * Checks if Pehkui is loaded. Safe to call at any time.
     */
    public static boolean isLoaded() {
        if (!initialized) {
            pehkuiLoaded = ModList.get().isLoaded("pehkui");
            initialized = true;
            if (pehkuiLoaded) {
                LOGGER.info("Pehkui detected — Somnium entity scaling enabled");
            } else {
                LOGGER.info("Pehkui not found — running without entity scaling");
            }
        }
        return pehkuiLoaded;
    }

    private PehkuiCompat() {}
}