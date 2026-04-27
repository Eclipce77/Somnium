package net.eclipce.somnium.compat.geckolib;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GeckoLib compatibility layer. Detects GeckoLib at runtime and
 * conditionally initializes rendering support.
 *
 * <p>All GeckoLib-dependent code lives in the {@code compat.geckolib}
 * package. Core Somnium classes never import GeckoLib directly —
 * they store data as ResourceLocations, and this layer resolves
 * them to GeckoLib models/animations at render time.</p>
 *
 * <p>If GeckoLib is not present, this class gracefully no-ops.
 * The mod functions fully without it — transformations just won't
 * have custom model rendering.</p>
 */
public final class GeckoLibCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Somnium/GeckoLib");
    private static boolean geckoLibLoaded = false;
    private static boolean initialized = false;

    /**
     * Checks if GeckoLib is loaded. Safe to call at any time.
     */
    public static boolean isLoaded() {
        if (!initialized) {
            geckoLibLoaded = ModList.get().isLoaded("geckolib");
            initialized = true;
            if (geckoLibLoaded) {
                LOGGER.info("GeckoLib detected — Somnium animation support enabled");
            } else {
                LOGGER.info("GeckoLib not found — running without animation support");
            }
        }
        return geckoLibLoaded;
    }

    /**
     * Initializes GeckoLib integration on the client side.
     * Called conditionally from client setup events.
     * Only executes if GeckoLib is loaded.
     */
    public static void initClient() {
        if (!isLoaded()) return;
        try {
            // Client-side registration is handled by GeckoLibClientSetup
            // which registers render layers and renderers
            GeckoLibClientSetup.init();
            LOGGER.info("GeckoLib client integration initialized");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize GeckoLib client integration", e);
            geckoLibLoaded = false;
        }
    }

    private GeckoLibCompat() {}
}
