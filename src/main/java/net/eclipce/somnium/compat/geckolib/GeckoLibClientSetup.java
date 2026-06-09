package net.eclipce.somnium.compat.geckolib;

import net.eclipce.somnium.compat.geckolib.player.TransformationRenderHandler;
import net.eclipce.somnium.compat.geckolib.player.TransformedPlayerAnimatable;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side setup for GeckoLib integration.
 *
 * <p>Registers the transformation render handler on the Forge event bus
 * so it can intercept player rendering during active transformations.</p>
 *
 * <p>This class is only loaded when GeckoLib is present — it's called
 * from {@link GeckoLibCompat#initClient()} which checks isLoaded() first.</p>
 */
public final class GeckoLibClientSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger("Somnium/GeckoLib");

    /**
     * Initializes client-side GeckoLib integration.
     * Called once from {@link GeckoLibCompat#initClient()}.
     */
    public static void init() {
        // Initialize the transformed player renderer
        TransformationRenderHandler.init();

        // Register the render event handler
        MinecraftForge.EVENT_BUS.register(TransformationRenderHandler.class);

        // Register the cast-animation first-person renderer (handles showInFirstPerson
        // and heldItemsShown options on CastAnimationOptions). See
        // SomniumFirstPersonRenderer Javadoc for the full design.
        MinecraftForge.EVENT_BUS.register(
                net.eclipce.somnium.compat.geckolib.player.cast.SomniumFirstPersonRenderer.class);

        LOGGER.info("Registered GeckoLib transformation renderer");
        LOGGER.info("Registered Somnium cast-animation first-person renderer");
    }

    /**
     * Cleans up on disconnect/world change.
     */
    public static void cleanup() {
        TransformedPlayerAnimatable.clearAll();
    }

    private GeckoLibClientSetup() {}
}