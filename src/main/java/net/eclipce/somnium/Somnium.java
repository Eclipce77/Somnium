package net.eclipce.somnium;

import com.mojang.logging.LogUtils;
import net.eclipce.somnium.client.config.SomniumClientConfig;
import net.eclipce.somnium.command.SomniumCommand;
import net.eclipce.somnium.config.SomniumCommonConfig;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.effects.SomniumEffects;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.network.SomniumNetwork;
import net.eclipce.somnium.test.CuriosTestSetup;
import net.eclipce.somnium.test.GeckoLibTestSetup;
import net.eclipce.somnium.test.TestContent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Somnium.MOD_ID)
public class Somnium {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "somnium";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public Somnium(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Layer 2: Register DeferredRegisters so addon mods can register content,
        // and listen for NewRegistryEvent to create the actual IForgeRegistry instances.
        SomniumRegistries.ABILITY_TYPES.register(modEventBus);
        SomniumRegistries.POWERS.register(modEventBus);
        modEventBus.addListener(SomniumRegistries::onNewRegistry);

        // Layer 3: Register the capability type so Forge knows about it.
        modEventBus.addListener(this::registerCapabilities);

        // Layer 4: Initialize the network channel and register all packet types.
        SomniumNetwork.init();

        // Register custom effects (Overuse)
        SomniumEffects.init(modEventBus);

        // Register gamerules
        SomniumGameRules.init();

        // Register config
        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, SomniumCommonConfig.SPEC, "somnium-common.toml"
        );

        // Layer 6: Register client config for ability bar position settings.
        // Keybind and overlay registration is handled by SomniumClientEvents
        // via @Mod.EventBusSubscriber (client-side only, auto-discovered).
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SomniumClientConfig.SPEC);

        // Layer 9: Register commands on the Forge event bus.
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent event) ->
                        SomniumCommand.register(event.getDispatcher()));

        TestContent.init(modEventBus);

        // Integration test content — remove before release
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            CuriosTestSetup.init(modEventBus);
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("geckolib")) {
            GeckoLibTestSetup.init(modEventBus);
        }

        modEventBus.addListener(this::onClientSetup);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::onCommonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Somnium API initialized");

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SomniumPlayerData.class);
    }

    /**
     * Client-side setup — conditionally initializes GeckoLib integration.
     */
    private void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Optional GeckoLib integration
            net.eclipce.somnium.compat.geckolib.GeckoLibCompat.initClient();
        });
    }


    /**
     * Common setup — conditionally initializes Curios integration.
     */
    private void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.eclipce.somnium.compat.curios.CuriosCompat.init();
        });
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

}