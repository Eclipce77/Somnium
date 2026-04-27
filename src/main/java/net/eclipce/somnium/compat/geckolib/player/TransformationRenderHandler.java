package net.eclipce.somnium.compat.geckolib.player;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eclipce.somnium.compat.geckolib.GeckoLibClientSetup;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationData;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Forge event handler that intercepts player rendering during active
 * transformations with GeckoLib models.
 *
 * <p>When a player has an active transformation with a
 * {@link TransformationData#hasModel() model set}, this handler:</p>
 * <ol>
 *     <li>Cancels the vanilla player renderer ({@code RenderPlayerEvent.Pre})</li>
 *     <li>Renders the GeckoLib model at the player's position instead</li>
 *     <li>Applies scale, animations, and texture from TransformationData</li>
 * </ol>
 *
 * <p>Only registered when GeckoLib is present (conditional registration
 * via {@link GeckoLibClientSetup}).</p>
 */
public class TransformationRenderHandler {

    private static TransformedPlayerRenderer renderer;

    /**
     * Initializes the renderer. Called once from client setup.
     */
    public static void init() {
        renderer = new TransformedPlayerRenderer();
    }

    /**
     * Cancels vanilla player rendering when a GeckoLib transformation model
     * is active, and renders the GeckoLib model instead.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        TransformationData data = getActiveTransformationData(player);

        if (data == null || !data.hasModel()) return;
        if (renderer == null) return;

        // Cancel vanilla rendering
        event.setCanceled(true);

        // Update the animatable with current transformation data
        TransformedPlayerAnimatable animatable =
                TransformedPlayerAnimatable.getOrCreate(player);
        animatable.setCurrentData(data);

        // Render the GeckoLib model at the player's position
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        int packedLight = event.getPackedLight();
        float partialTick = event.getPartialTick();

        renderer.renderTransformed(poseStack, animatable, bufferSource,
                packedLight, partialTick);
    }

    /**
     * Cleans up animatables when players are no longer rendered.
     */
    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        // No-op for now — cleanup happens on disconnect
    }

    /**
     * Resolves the active transformation's visual data for a player.
     *
     * @param player the player to check
     * @return the TransformationData with a model, or null if none active
     */
    @Nullable
    private static TransformationData getActiveTransformationData(Player player) {
        SomniumPlayerData somniumData = SomniumCapability.get(player);
        if (somniumData == null || !somniumData.hasActiveTransformation()) return null;

        ResourceLocation activeKey = somniumData.getActiveTransformation();
        if (activeKey == null) return null;

        AbilityType type = SomniumRegistries.getAbilityValue(activeKey);
        if (!(type instanceof TransformationAbilityType transType)) return null;

        TransformationData data = transType.getVisualData();
        if (data == null || !data.hasModel()) return null;

        return data;
    }
}
