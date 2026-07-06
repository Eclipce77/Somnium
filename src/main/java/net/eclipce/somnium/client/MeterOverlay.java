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
 * Renders meter bars on the HUD.
 *
 * <h3>Layout model</h3>
 * <p>The meter stack (stamina, then any auto-positioned custom meters) renders
 * <strong>flush against the true screen edge</strong> — the same
 * {@link AbilityBarOverlay#SCREEN_OFFSET_X}/{@code Y} margin the ability bar
 * itself uses. The ability bar is then pushed inward (toward screen centre) by
 * exactly the width the meter stack consumes, via
 * {@link #getReservedStackWidth}, which {@code AbilityBarOverlay.calculateBarX}
 * calls every frame. Because the ability bar's keybind labels are always
 * positioned relative to the bar's own X, this single adjustment is what makes
 * the labels move out of the way automatically whenever a meter is present —
 * there is no separate "keybind avoidance" calculation to keep in sync.</p>
 *
 * <p>Custom meters with no fixed {@link MeterDefinition#getScreenPosition()}
 * stack inward from stamina in the same direction the ability bar was pushed.
 * Each meter's footprint for stacking purposes is its texture width scaled by
 * {@link MeterDefinition#getScale()}, so differently-sized custom meters stack
 * without overlapping. Meters with a fixed screen position render independently
 * and don't contribute to the stack width or the ability bar's shift.</p>
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

    /** Gap between meters when stacked, and between the stack and the ability bar. */
    private static final int METER_GAP = 2;

    // ═══════════════════════════════════════════════════════════════════
    //  Meter vertical fine-tuning
    //  X positioning is now edge-flush (see getReservedStackWidth /
    //  render()) and no longer needs a paged-vs-standard distinction — only
    //  Y needs a small nudge, since the paged ability bar texture has a
    //  taller visible area (page-arrow graphic) that shifts its icon rows.
    // ═══════════════════════════════════════════════════════════════════

    /** Y nudge relative to the ability bar's Y when the paged bar is showing. */
    private static final int PAGED_Y = -5;

    /** Y nudge relative to the ability bar's Y when the standard bar is showing. */
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

        // Ability bar Y only — used purely to vertically line the meter stack
        // up with the ability bar's icon rows. X is no longer derived from the
        // ability bar at all: the meter stack is flush against the true screen
        // edge (same margin the ability bar itself uses), and it's the ability
        // bar that shifts inward to make room (see calculateBarX in
        // AbilityBarOverlay, which calls getReservedStackWidth below).
        int barY = position.isTop() ? offsetY : screenHeight - ABILITY_BAR_VISIBLE_HEIGHT - offsetY;

        boolean paged = AbilityBarOverlay.isPaged() && !AbilityBarOverlay.isShowingAlternateBar();
        int yNudge = paged ? PAGED_Y : STANDARD_Y;
        int meterY = barY + yNudge;

        // The stack's outer edge — the side facing the true screen edge.
        // For a right-anchored bar this is the RIGHT edge of each meter;
        // for a left-anchored bar this is the LEFT edge. `cumulative` tracks
        // how much width (previous meters + their gaps) has already been
        // consumed moving inward from that edge.
        int outerEdge = position.isRight() ? screenWidth - offsetX : offsetX;
        int cumulative = 0;

        // ── Stamina — always first in the stack ──
        int staminaX = position.isRight() ? outerEdge - cumulative - TEX_WIDTH : outerEdge + cumulative;
        renderStamina(graphics, staminaX, meterY, data.getStaminaData());
        cumulative += TEX_WIDTH + METER_GAP;

        // ── Custom meters — stack inward from stamina, or render at a
        //    definition's fixed screenPosition if it has one ──
        for (MeterDefinition def : MeterDefinition.getAll()) {
            MeterInstance meter = data.getMeterIfPresent(def.getId());
            if (meter == null) continue;               // player never touched this meter
            if (!isVisible(def, data)) continue;

            int[] fixed = def.getScreenPosition();
            if (fixed != null && fixed.length == 2) {
                // Fixed-position meters render independently — they don't
                // participate in the stack and don't reserve any space.
                renderCustomMeter(graphics, fixed[0] + def.getOffsetX(),
                        fixed[1] + def.getOffsetY(), def, meter);
                continue;
            }

            int texW = def.getTextureWidth() != null ? def.getTextureWidth() : TEX_WIDTH;
            int renderW = Math.round(texW * def.getScale());

            int meterX = position.isRight() ? outerEdge - cumulative - renderW : outerEdge + cumulative;
            renderCustomMeter(graphics, meterX + def.getOffsetX(),
                    meterY + def.getOffsetY(), def, meter);
            cumulative += renderW + METER_GAP;
        }
    }

    /**
     * Total horizontal space (in GUI-scaled pixels) consumed by the auto-
     * stacked meter sequence — stamina plus any custom meters using automatic
     * layout — including the trailing gap before whatever renders next (the
     * ability bar). Meters with a fixed {@link MeterDefinition#getScreenPosition()}
     * render independently and are excluded, matching how {@link #render}
     * treats them.
     *
     * <p>Called once per frame by {@code AbilityBarOverlay.calculateBarX} so
     * the ability bar — and by extension its keybind labels, which are always
     * positioned relative to the bar's own X — shifts inward exactly enough
     * to avoid the flush-edge meter stack.</p>
     */
    public static int getReservedStackWidth(@Nullable SomniumPlayerData data) {
        if (data == null) return 0;

        int total = TEX_WIDTH + METER_GAP; // stamina always renders whenever data exists
        for (MeterDefinition def : MeterDefinition.getAll()) {
            if (def.getScreenPosition() != null) continue;
            MeterInstance meter = data.getMeterIfPresent(def.getId());
            if (meter == null) continue;
            if (!isVisible(def, data)) continue;

            int texW = def.getTextureWidth() != null ? def.getTextureWidth() : TEX_WIDTH;
            total += Math.round(texW * def.getScale()) + METER_GAP;
        }
        return total;
    }

    /**
     * Evaluates a definition's {@link MeterDefinition.VisibilityMode} against the
     * local player's synced data.
     */
    private static boolean isVisible(MeterDefinition def, SomniumPlayerData data) {
        return switch (def.getVisibilityMode()) {
            case ALWAYS -> true;
            case POWER_ACTIVE -> def.getVisibilityKey() != null
                    && data.getGrantedPowerKeys().contains(def.getVisibilityKey());
            case TRANSFORMATION_ACTIVE -> data.hasActiveTransformation();
            case TAG_PRESENT -> def.getVisibilityKey() != null
                    && data.hasTag(def.getVisibilityKey());
        };
    }

    /**
     * Renders one custom meter: frame, then bottom-to-top fill tinted by the
     * definition's color. Uses the definition's frame/fill textures when set,
     * falling back to the defaults, and honours per-definition texture size
     * ({@link MeterDefinition#getTextureWidth()}/{@code getTextureHeight()})
     * and scale ({@link MeterDefinition#getScale()}).
     *
     * <h3>Why scale doesn't need to read the GUI Scale option</h3>
     * <p>{@code IGuiOverlay.render} is already called with a PoseStack that
     * has Minecraft's GUI-scale matrix applied — {@code screenWidth}/
     * {@code screenHeight} and every {@code GuiGraphics} call here operate in
     * that same logical-pixel space, the same way the vanilla hotbar or XP
     * bar do. Wrapping the blit calls in an additional
     * {@code poseStack.scale(...)} for {@link MeterDefinition#getScale()}
     * therefore composes correctly with whatever GUI Scale the player has
     * chosen automatically; reading
     * {@code Minecraft.getInstance().getWindow().getGuiScale()} here and
     * multiplying manually would double-apply it.</p>
     */
    private void renderCustomMeter(GuiGraphics graphics, int x, int y,
                                   MeterDefinition def, MeterInstance meter) {
        ResourceLocation frame = def.getFrameTexture() != null ? def.getFrameTexture() : DEFAULT_FRAME;
        ResourceLocation fill = def.getFillTexture() != null ? def.getFillTexture() : DEFAULT_FILL;

        int texW = def.getTextureWidth() != null ? def.getTextureWidth() : TEX_WIDTH;
        int texH = def.getTextureHeight() != null ? def.getTextureHeight() : TEX_HEIGHT;
        float scale = def.getScale();
        boolean scaled = scale != 1.0f;

        var poseStack = graphics.pose();
        if (scaled) {
            // Scale around this meter's own top-left anchor so offsetX/offsetY
            // (already baked into x, y by the caller) stay the visual anchor
            // point regardless of scale.
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scale, scale, 1f);
            poseStack.translate(-x, -y, 0);
        }

        // Layer 1: Frame
        RenderSystem.enableBlend();
        graphics.blit(frame, x, y,
                0, 0, texW, texH,
                texW, texH);

        // Layer 2: Fill (tinted, bottom-to-top)
        float fraction = Math.max(0f, Math.min(1f, meter.getFraction()));
        int fillPixels = Math.round(texH * fraction);
        if (fillPixels > 0) {
            int color = def.getColor();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            graphics.setColor(r, g, b, 1f);

            int fillStartY = y + texH - fillPixels;
            int texV = texH - fillPixels;
            graphics.blit(fill, x, fillStartY,
                    0, texV,
                    texW, fillPixels,
                    texW, texH);

            graphics.setColor(1f, 1f, 1f, 1f);
        }
        RenderSystem.disableBlend();

        if (scaled) poseStack.popPose();
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