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
 * <p><strong>The ability bar and the stamina meter never move</strong> — both
 * sit at exactly the positions they always have (ability bar flush at its
 * configured screen corner; stamina offset from it via {@link #STANDARD_X}/
 * {@link #PAGED_X}). Nothing in this class shifts either of them.</p>
 *
 * <p>Every custom meter picks one of four positioning modes:</p>
 * <ul>
 *   <li><b>Auto (default)</b> — no {@link MeterDefinition#getScreenPosition()},
 *       {@link MeterDefinition#getLockedCorner()}, or
 *       {@link MeterDefinition#isMirrorOppositeSide()} set. Placed the exact
 *       same way stamina is, mirrored onto the ability bar's other side — the
 *       same side the keybind labels occupy. Concretely: stamina sits at
 *       {@code abilityBarX + xOffset}; a custom meter's mirror image sits
 *       {@code xOffset} pixels in from the ability bar's *other* edge,
 *       extending outward from there — a true reflection across the bar's
 *       width. Multiple auto meters stack further inward, one after another.
 *       Texture never affects this — a meter with a custom frame/fill is
 *       placed identically to one without.</li>
 *   <li><b>Fixed pixels</b> ({@link MeterDefinition#getScreenPosition()}) —
 *       an explicit {@code (x, y)}, independent of everything else.</li>
 *   <li><b>Locked corner</b> ({@link MeterDefinition#getLockedCorner()}) —
 *       flush at a specific {@link MeterDefinition.ScreenCorner}, independent
 *       of the ability bar's configured {@code BarPosition}. Never moves even
 *       if the player reconfigures the bar.</li>
 *   <li><b>Mirror opposite screen side</b>
 *       ({@link MeterDefinition#isMirrorOppositeSide()}) — flush at the
 *       corner directly opposite the ability bar's live {@code BarPosition}
 *       (same vertical half, flipped horizontal side). Tracks the config: if
 *       the player moves their ability bar, this meter follows to the new
 *       opposite corner.</li>
 * </ul>
 *
 * <p>Only the Auto mode affects keybind labels — since it renders where the
 * labels normally would, {@link #getKeybindLabelExtraOffset} tells
 * {@code AbilityBarOverlay} exactly how much further out to push them, only
 * when at least one Auto meter is actually visible this frame. The other
 * three modes render nowhere near the ability bar by design and never affect
 * label placement.</p>
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

    /**
     * Left-mirrored variants of the default textures — pre-flipped art (not
     * just a mirrored position) so the bevelled/decorative edge and the flat
     * "attaches to the ability bar" edge land on the correct sides once this
     * texture is rendered in AUTO mode, where a default-look meter's own
     * right edge always overlaps the ability bar (see the mirrorBase math
     * below) regardless of whether the ability bar itself is left- or
     * right-anchored on screen.
     *
     * <p><b>Asset path assumption:</b> these mirror {@link #DEFAULT_FRAME}/
     * {@link #DEFAULT_FILL}'s own folders exactly (frame directly under
     * {@code textures/gui/bar/}, fill under {@code textures/gui/bar/energy/}).
     * Move these two constants' paths if the actual files end up somewhere
     * else in the resource pack.</p>
     */
    private static final ResourceLocation DEFAULT_FRAME_LEFT =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/meter_bar_left.png");
    private static final ResourceLocation DEFAULT_FILL_LEFT =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/energy/meter_bar_energy_left.png");

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

    /** Gap between meters when stacked, and between a stack and the ability bar. */
    private static final int METER_GAP = 2;

    // ═══════════════════════════════════════════════════════════════════
    //  Stamina position — original, untouched values.
    //  Stamina always renders at abilityBarX + (STANDARD_X or PAGED_X),
    //  exactly as before. Nothing in this class moves the ability bar or
    //  stamina; only custom meters (see class javadoc for the four
    //  positioning modes) and the keybind label offset are new.
    // ═══════════════════════════════════════════════════════════════════

    /** X offset relative to abilityBarX when the paged bar is showing. */
    private static final int PAGED_X = 21;

    /** Y offset relative to barY when the paged bar is showing. */
    private static final int PAGED_Y = -5;

    /** X offset relative to abilityBarX when the standard bar is showing. */
    private static final int STANDARD_X = 20;

    /** Y offset relative to barY when the standard bar is showing. */
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

        // Ability bar position — identical to AbilityBarOverlay's own
        // (unshifted) calculation. Nothing here ever moves this.
        int abilityBarX;
        if (position.isRight()) {
            abilityBarX = screenWidth - ABILITY_BAR_WIDTH - offsetX;
        } else {
            abilityBarX = offsetX;
        }

        int barY = position.isTop() ? offsetY : screenHeight - ABILITY_BAR_VISIBLE_HEIGHT - offsetY;

        boolean paged = AbilityBarOverlay.isPaged() && !AbilityBarOverlay.isShowingAlternateBar();
        int xOffset = paged ? PAGED_X : STANDARD_X;
        int yOffset = paged ? PAGED_Y : STANDARD_Y;

        // Stamina — original position, untouched.
        int staminaX = abilityBarX + xOffset;
        int staminaY = barY + yOffset;
        renderStamina(graphics, staminaX, staminaY, data.getStaminaData());

        // Mirror base: reflects stamina's (abilityBarX + xOffset) placement
        // across the ability bar's own width, so a same-width meter on the
        // opposite flank sits the same visual distance from the bar, on the
        // other side — the same side the keybind labels are on.
        int mirrorBase = abilityBarX + (ABILITY_BAR_WIDTH - xOffset);

        int innerCumulative = 0; // stacks inward from mirrorBase, one meter after another

        for (MeterDefinition def : MeterDefinition.getAll()) {
            MeterInstance meter = data.getMeterIfPresent(def.getId());
            if (meter == null) continue;               // player never touched this meter
            if (!isVisible(def, data)) continue;

            int[] fixed = def.getScreenPosition();
            if (fixed != null && fixed.length == 2) {
                // Fixed-position meters render independently — no stacking,
                // no effect on stamina, the ability bar, or the labels. Not
                // inherently "attached" to anything, so the regular (right-
                // facing) default texture applies when no custom one is set.
                renderCustomMeter(graphics, fixed[0] + def.getOffsetX(), fixed[1] + def.getOffsetY(),
                        resolveFrame(def, DEFAULT_FRAME), resolveFill(def, DEFAULT_FILL), def, meter);
                continue;
            }

            MeterDefinition.ScreenCorner corner = def.getLockedCorner();
            if (corner != null) {
                // Pinned to an explicit corner — independent of the ability
                // bar's configured position, and doesn't affect it or the
                // labels either. Not attached to the ability bar, so the
                // regular default texture applies.
                int[] pos = edgeFlushPosition(corner.isRight(), corner.isTop(),
                        screenWidth, screenHeight, def);
                renderCustomMeter(graphics, pos[0] + def.getOffsetX(), pos[1] + def.getOffsetY(),
                        resolveFrame(def, DEFAULT_FRAME), resolveFill(def, DEFAULT_FILL), def, meter);
                continue;
            }

            if (def.isMirrorOppositeSide()) {
                // Opposite side of the SCREEN from the ability bar's own
                // configured side (same vertical half, flipped horizontal) —
                // distinct from the local mirror-stack below, which flanks
                // the ability bar directly. Not attached to the ability bar,
                // so the regular default texture applies.
                int[] pos = edgeFlushPosition(!position.isRight(), position.isTop(),
                        screenWidth, screenHeight, def);
                renderCustomMeter(graphics, pos[0] + def.getOffsetX(), pos[1] + def.getOffsetY(),
                        resolveFrame(def, DEFAULT_FRAME), resolveFill(def, DEFAULT_FILL), def, meter);
                continue;
            }

            int texW = def.getTextureWidth() != null ? def.getTextureWidth() : TEX_WIDTH;
            int renderW = Math.round(texW * def.getScale());

            // Mirrored onto the ability bar's other side — the labels' side —
            // stacking further inward for each additional custom meter.
            // Texture doesn't factor into the POSITION: every custom meter
            // without a fixed position is placed the exact same way, texture
            // or not. It does affect which DEFAULT texture applies, though:
            // by construction of mirrorBase, an AUTO meter's own right edge
            // always overlaps the ability bar's left edge (true regardless of
            // whether the ability bar itself is left- or right-anchored on
            // screen) — the same relationship stamina has, just flipped. So
            // when no custom texture is set, the pre-flipped LEFT variant is
            // the correct default, not the regular one stamina itself uses.
            int meterX = mirrorBase - renderW - innerCumulative;
            int meterY = staminaY;
            innerCumulative += renderW + METER_GAP;

            renderCustomMeter(graphics, meterX + def.getOffsetX(), meterY + def.getOffsetY(),
                    resolveFrame(def, DEFAULT_FRAME_LEFT), resolveFill(def, DEFAULT_FILL_LEFT), def, meter);
        }
    }

    /**
     * Computes a flush position against a given screen edge combination
     * (right/left, top/bottom), using the same {@code SCREEN_OFFSET_X}/{@code Y}
     * margin the ability bar itself uses. Shared by both
     * {@link MeterDefinition#getLockedCorner()} (an explicit, fixed corner)
     * and {@link MeterDefinition#isMirrorOppositeSide()} (a corner derived
     * live from the ability bar's own configured side, flipped horizontally)
     * — the flush-edge math is identical either way, only which corner is
     * being targeted differs.
     */
    private static int[] edgeFlushPosition(boolean isRight, boolean isTop,
                                           int screenWidth, int screenHeight,
                                           MeterDefinition def) {
        int offsetX = AbilityBarOverlay.SCREEN_OFFSET_X;
        int offsetY = AbilityBarOverlay.SCREEN_OFFSET_Y;

        int texW = def.getTextureWidth() != null ? def.getTextureWidth() : TEX_WIDTH;
        int texH = def.getTextureHeight() != null ? def.getTextureHeight() : TEX_HEIGHT;
        int renderW = Math.round(texW * def.getScale());
        int renderH = Math.round(texH * def.getScale());

        int x = isRight ? screenWidth - renderW - offsetX : offsetX;
        int y = isTop ? offsetY : screenHeight - renderH - offsetY;
        return new int[]{x, y};
    }

    /**
     * How many extra pixels {@code AbilityBarOverlay} should push its keybind
     * labels beyond its normal {@code KEYBIND_LABEL_GAP}, to clear the custom
     * meters mirrored onto the label's side of the ability bar (see this
     * class's javadoc). Returns 0 if there are none — fixed-position,
     * locked-corner, and mirror-opposite-screen meters never affect this,
     * since none of them render on the label's side.
     *
     * <p>Deliberately a flat sum of each visible AUTO meter's own rendered
     * footprint ({@code width + gap}) — not a geometric recomputation of
     * where the mirror-stack's outer edge actually lands. The two happen to
     * differ slightly because the mirror placement lets a meter overlap a few
     * pixels into the ability bar's own footprint (mirroring stamina's own
     * overlap on its side); the label offset intentionally ignores that and
     * just adds the plain footprint on top of the label's existing,
     * unchanged {@code KEYBIND_LABEL_GAP} — so with no meter active, labels
     * sit at exactly their original position, and with one active, they sit
     * at that same original position plus exactly the meter's own width.</p>
     */
    public static int getKeybindLabelExtraOffset(@Nullable SomniumPlayerData data) {
        if (data == null) return 0;

        int total = 0;
        for (MeterDefinition def : MeterDefinition.getAll()) {
            if (def.getScreenPosition() != null) continue;
            if (def.getLockedCorner() != null) continue;
            if (def.isMirrorOppositeSide()) continue;
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
     * definition's color. {@code frame}/{@code fill} are fully resolved by
     * the caller — see {@link #resolveFrame}/{@link #resolveFill} — since
     * which fallback texture applies (regular vs. left-mirrored) depends on
     * the positioning mode, not just whether the definition set a custom one.
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
                                   ResourceLocation frame, ResourceLocation fill,
                                   MeterDefinition def, MeterInstance meter) {
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

    /** Resolves a meter's custom frame texture, or {@code fallback} if none is set. */
    private static ResourceLocation resolveFrame(MeterDefinition def, ResourceLocation fallback) {
        return def.getFrameTexture() != null ? def.getFrameTexture() : fallback;
    }

    /** Resolves a meter's custom fill texture, or {@code fallback} if none is set. */
    private static ResourceLocation resolveFill(MeterDefinition def, ResourceLocation fallback) {
        return def.getFillTexture() != null ? def.getFillTexture() : fallback;
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