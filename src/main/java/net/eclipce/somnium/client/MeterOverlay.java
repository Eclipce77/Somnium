package net.eclipce.somnium.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.client.config.BarPosition;
import net.eclipce.somnium.client.config.SomniumClientConfig;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.meter.MeterDefinition;
import net.eclipce.somnium.core.meter.MeterInstance;
import net.eclipce.somnium.core.meter.StaminaData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Renders meter bars on the HUD. Stamina renders flush right of the ability
 * bar. Custom meters render in sequence after stamina (or at fixed positions
 * if specified by the definition).
 *
 * <p>The fill texture is tinted by the meter's color. The frame texture
 * renders first, fill overlays on top. Fill direction is bottom-to-top.</p>
 */
public class MeterOverlay implements IGuiOverlay {

    // Default textures
    private static final ResourceLocation DEFAULT_FRAME =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/meter_bar.png");
    private static final ResourceLocation DEFAULT_FILL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/energy/meter_bar_energy.png");

    // Meter texture dimensions
    private static final int TEX_WIDTH = 26;
    private static final int TEX_HEIGHT = 129;

    /** Visible height matching the ability bar for Y alignment. */
    private static final int ABILITY_BAR_VISIBLE_HEIGHT = 120;

    /** Width of the ability bar texture. */
    private static final int ABILITY_BAR_WIDTH = 26;

    /** Gap between meters when stacked. */
    private static final int METER_GAP = 2;

    // ═══════════════════════════════════════════════════════════════════
    //  Meter position — paged bar variant
    //  Adjust these to reposition the meter when the paged bar is showing
    // ═══════════════════════════════════════════════════════════════════

    /** X offset relative to abilityBarX when paged bar is showing. */
    private static final int PAGED_X = 21;

    /** Y offset relative to barY when paged bar is showing. */
    private static final int PAGED_Y = -5;

    // ═══════════════════════════════════════════════════════════════════
    //  Meter position — standard (non-paged) bar variant
    //  Adjust these to reposition the meter when the standard bar is showing
    // ═══════════════════════════════════════════════════════════════════

    /** X offset relative to abilityBarX when standard bar is showing. */
    private static final int STANDARD_X = 20;

    /** Y offset relative to barY when standard bar is showing. */
    private static final int STANDARD_Y = -2;

    // ═══════════════════════════════════════════════════════════════════
    //  Rendering
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        SomniumPlayerData data = ClientAbilityData.getLocalData();
        if (data == null) return;

        BarPosition position = SomniumClientConfig.getBarPosition();
        int offsetX = AbilityBarOverlay.SCREEN_OFFSET_X;
        int offsetY = AbilityBarOverlay.SCREEN_OFFSET_Y;

        // Calculate ability bar position (same logic as AbilityBarOverlay)
        int abilityBarX;
        if (position.isRight()) {
            abilityBarX = screenWidth - ABILITY_BAR_WIDTH - offsetX;
        } else {
            abilityBarX = offsetX;
        }

        int barY;
        if (position.isTop()) {
            barY = offsetY;
        } else {
            barY = screenHeight - ABILITY_BAR_VISIBLE_HEIGHT - offsetY;
        }

        // Choose position set based on paged vs standard bar
        boolean paged = AbilityBarOverlay.isPaged()
                && !AbilityBarOverlay.isShowingAlternateBar();

        int staminaX, staminaY;
        if (paged) {
            staminaX = abilityBarX + PAGED_X;
            staminaY = barY + PAGED_Y;
        } else {
            staminaX = abilityBarX + STANDARD_X;
            staminaY = barY + STANDARD_Y;
        }

        // Render stamina
        net.eclipce.somnium.core.meter.StaminaData stamina = data.getStaminaData();
        renderStamina(graphics, staminaX, staminaY, stamina);

        // Custom meters stack after stamina
        int nextMeterX = staminaX + TEX_WIDTH + METER_GAP;

        // Render custom meters
        for (Map.Entry<ResourceLocation, MeterInstance> entry : data.getAllMeters().entrySet()) {
            MeterDefinition def = MeterDefinition.get(entry.getKey());
            if (def == null) continue;

            if (!isMeterVisible(def, data)) continue;

            MeterInstance meter = entry.getValue();

            int meterX, meterY;
            int[] fixedPos = def.getScreenPosition();
            if (fixedPos != null) {
                meterX = fixedPos[0];
                meterY = fixedPos[1];
            } else {
                meterX = nextMeterX;
                meterY = staminaY;
                nextMeterX += TEX_WIDTH + METER_GAP;
            }

            ResourceLocation frame = def.getFrameTexture() != null
                    ? def.getFrameTexture() : DEFAULT_FRAME;
            ResourceLocation fill = def.getFillTexture() != null
                    ? def.getFillTexture() : DEFAULT_FILL;

            renderMeter(graphics, meterX, meterY, meter, def, frame, fill,
                    def.getColor());
        }
    }

    /**
     * Renders a single meter bar (frame + fill overlay).
     * Fill direction: bottom-to-top (full at bottom, empties from top).
     *
     * @param color RGB color for the fill texture, or -1 for no tint (white)
     */
    private void renderMeter(GuiGraphics graphics, int x, int y,
                             MeterInstance meter,
                             @Nullable MeterDefinition def,
                             ResourceLocation frameTexture,
                             ResourceLocation fillTexture,
                             int color) {
        // Render frame first (background)
        RenderSystem.enableBlend();
        graphics.blit(frameTexture, x, y,
                0, 0, TEX_WIDTH, TEX_HEIGHT,
                TEX_WIDTH, TEX_HEIGHT);
        RenderSystem.disableBlend();

        // Render fill on top, clipped by fill fraction (bottom-to-top)
        float fraction = meter.getFraction();
        int fillPixels = Math.round(TEX_HEIGHT * fraction);
        if (fillPixels > 0) {
            RenderSystem.enableBlend();
            if (color >= 0) {
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                RenderSystem.setShaderColor(r, g, b, 1.0f);
            }

            // Fill anchored at bottom, growing upward
            int fillStartY = y + TEX_HEIGHT - fillPixels;
            int texV = TEX_HEIGHT - fillPixels;
            graphics.blit(fillTexture, x, fillStartY,
                    0, texV,
                    TEX_WIDTH, fillPixels,
                    TEX_WIDTH, TEX_HEIGHT);

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
    }

    /**
     * Renders the stamina meter. Uses StaminaData for fraction (handles negative overuse values).
     * No color tint for stamina.
     */
    private void renderStamina(GuiGraphics graphics, int x, int y, StaminaData stamina) {
        // Render frame
        RenderSystem.enableBlend();
        graphics.blit(DEFAULT_FRAME, x, y,
                0, 0, TEX_WIDTH, TEX_HEIGHT,
                TEX_WIDTH, TEX_HEIGHT);
        RenderSystem.disableBlend();

        // Render fill (bottom-to-top, no color tint)
        float fraction = stamina.getFraction(); // clamped 0-1, negative = 0
        int fillPixels = Math.round(TEX_HEIGHT * fraction);
        if (fillPixels > 0) {
            RenderSystem.enableBlend();
            int fillStartY = y + TEX_HEIGHT - fillPixels;
            int texV = TEX_HEIGHT - fillPixels;
            graphics.blit(DEFAULT_FILL, x, fillStartY,
                    0, texV,
                    TEX_WIDTH, fillPixels,
                    TEX_WIDTH, TEX_HEIGHT);
            RenderSystem.disableBlend();
        }
    }

    /**
     * Checks if a custom meter should be visible based on its visibility mode.
     */
    private boolean isMeterVisible(MeterDefinition def, SomniumPlayerData data) {
        return switch (def.getVisibilityMode()) {
            case ALWAYS -> true;
            case POWER_ACTIVE -> {
                ResourceLocation key = def.getVisibilityKey();
                yield key != null && data.getGrantedPowerKeys().contains(key);
            }
            case TRANSFORMATION_ACTIVE -> data.hasActiveTransformation();
            case TAG_PRESENT -> {
                ResourceLocation key = def.getVisibilityKey();
                yield key != null && data.hasTag(key);
            }
        };
    }
}