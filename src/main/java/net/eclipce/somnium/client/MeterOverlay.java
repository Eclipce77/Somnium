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

    // Stamina-specific textures
    private static final ResourceLocation STAMINA_NEGATIVE =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/energy/meter_bar_stamina_negative.png");
    private static final ResourceLocation STAMINA_OVERUSE =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/energy/meter_bar_stamina_overuse.png");

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
    }

    /** Ticks per minute — used to calculate overuse timer fraction. */
    private static final int TICKS_PER_MINUTE = 1200;

    /**
     * Renders the stamina meter with negative fill and overuse timer.
     *
     * <p>Three visual layers on top of the frame:</p>
     * <ol>
     *     <li><b>Normal fill (white):</b> bottom-to-top when stamina ≥ 0</li>
     *     <li><b>Negative fill (red):</b> bottom-to-top when stamina &lt; 0,
     *         amount proportional to how negative the stamina is</li>
     *     <li><b>Overuse timer:</b> overlays on top, starts full when overuse
     *         begins, counts down until overuse ends</li>
     * </ol>
     */
    private void renderStamina(GuiGraphics graphics, int x, int y, StaminaData stamina) {
        // Layer 1: Frame
        RenderSystem.enableBlend();
        graphics.blit(DEFAULT_FRAME, x, y,
                0, 0, TEX_WIDTH, TEX_HEIGHT,
                TEX_WIDTH, TEX_HEIGHT);
        RenderSystem.disableBlend();

        float currentValue = stamina.getValue();
        float maxValue = stamina.getMaxValue();

        if (currentValue >= 0) {
            // Layer 2a: Normal fill (white) — bottom-to-top
            float fraction = stamina.getFraction();
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
        } else {
            // Layer 2b: Negative fill (red) — bottom-to-top
            // Amount is proportional to how negative: |-currentValue| / maxValue
            float negativeFraction = Math.min(1.0f, Math.abs(currentValue) / maxValue);
            int negPixels = Math.round(TEX_HEIGHT * negativeFraction);
            if (negPixels > 0) {
                RenderSystem.enableBlend();
                int fillStartY = y + TEX_HEIGHT - negPixels;
                int texV = TEX_HEIGHT - negPixels;
                graphics.blit(STAMINA_NEGATIVE, x, fillStartY,
                        0, texV,
                        TEX_WIDTH, negPixels,
                        TEX_WIDTH, TEX_HEIGHT);
                RenderSystem.disableBlend();
            }
        }

        // Layer 3: Overuse timer overlay — shows while overuse effects are active
        if (stamina.areEffectsActive() && stamina.getOveruseStage() > 0) {
            int effectTimer = stamina.getEffectTimer();
            int initialDuration = (stamina.getOveruseStage() + 1) * TICKS_PER_MINUTE;
            float overuseFraction = (float) effectTimer / (float) initialDuration;
            overuseFraction = Math.max(0, Math.min(1.0f, overuseFraction));

            int overusePixels = Math.round(TEX_HEIGHT * overuseFraction);
            if (overusePixels > 0) {
                RenderSystem.enableBlend();
                // Starts full, drains from top — remaining portion at bottom
                int renderY = y + TEX_HEIGHT - overusePixels;
                int texV = TEX_HEIGHT - overusePixels;
                graphics.blit(STAMINA_OVERUSE, x, renderY,
                        0, texV,
                        TEX_WIDTH, overusePixels,
                        TEX_WIDTH, TEX_HEIGHT);
                RenderSystem.disableBlend();
            }
        }
    }
}