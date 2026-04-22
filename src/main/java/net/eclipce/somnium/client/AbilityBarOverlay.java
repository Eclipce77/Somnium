package net.eclipce.somnium.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.client.ClientAbilityData;
import net.eclipce.somnium.client.config.BarPosition;
import net.eclipce.somnium.client.config.SomniumClientConfig;
import net.eclipce.somnium.client.keybind.SomniumKeybinds;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import javax.annotation.Nullable;

/**
 * The HUD overlay that renders the ability bar on screen.
 *
 * <p>The bar displays 6 ability slots with icons, cooldown indicators,
 * and keybind labels. It can be positioned in any of the four screen
 * corners via the client config. When positioned at the top of the screen,
 * the texture is flipped vertically.</p>
 *
 * <h3>Texture layout</h3>
 * <p>Two textures are used depending on whether pages are active:</p>
 * <ul>
 *     <li>{@code ability_bar.png} — single page (no page arrows)</li>
 *     <li>{@code ability_bar_paged.png} — multiple pages (with arrows)</li>
 * </ul>
 * <p>Both are 26x129 canvases. Six 16x16 icon slots begin at x=5 with
 * y offsets of 5, 24, 43, 62, 81, 100.</p>
 */
public class AbilityBarOverlay implements IGuiOverlay {

    // ═══════════════════════════════════════════════════════════════════
    //  Texture constants
    // ═══════════════════════════════════════════════════════════════════

    /** Texture for single-page bar (no page arrows). */
    private static final ResourceLocation BAR_TEXTURE =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/ability_bar.png");

    /** Texture for multi-page bar — only left arrow highlighted (can go back). */
    private static final ResourceLocation BAR_TEXTURE_PAGED_LEFT =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/ability_bar_paged_left.png");

    /** Texture for multi-page bar — only right arrow highlighted (can go forward). */
    private static final ResourceLocation BAR_TEXTURE_PAGED_RIGHT =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/ability_bar_paged_right.png");

    /** Texture for multi-page bar — both arrows highlighted (can go either way). */
    private static final ResourceLocation BAR_TEXTURE_PAGED_BOTH =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/bar/ability_bar_paged_both.png");

    /** Width of both bar textures. */
    private static final int TEX_WIDTH = 26;
    /** Height of both bar textures (canvas size). */
    private static final int TEX_HEIGHT = 129;

    /**
     * Visible height of the non-paged bar texture (y=1 to y=119).
     * Used for screen positioning so the bar sits flush against edges.
     */
    private static final int VISIBLE_HEIGHT_NORMAL = 120;

    /**
     * Visible height of the paged bar texture (y=1 to y=125).
     */
    private static final int VISIBLE_HEIGHT_PAGED = 126;

    /** X offset within the texture where icon slots begin. */
    private static final int SLOT_ICON_X = 5;
    /** Width/height of each ability icon. */
    private static final int ICON_SIZE = 16;

    /**
     * Y offsets within the texture for each of the 6 slot icon areas.
     * Each slot is a 16x16 region.
     */
    private static final int[] SLOT_ICON_Y = {5, 24, 43, 62, 81, 100};

    /** Gap between the bar edge and keybind label text. */
    private static final int KEYBIND_LABEL_GAP = 2;

    // ═══════════════════════════════════════════════════════════════════
    //  Transformation bar toggle
    // ═══════════════════════════════════════════════════════════════════

    /** Whether the transformation bar is currently visible instead of
     * the ability bar. Toggled by the transformation keybind. */
    private static boolean showTransformationBar = false;

    /** The currently shown custom category bar key, or null if not showing. */
    @Nullable
    private static ResourceLocation activeCategoryBar = null;

    /** @return true if the transformation bar is currently displayed */
    public static boolean isShowingTransformationBar() {
        return showTransformationBar;
    }

    /** Toggles transformation bar visibility on/off. Hides any category bar. */
    public static void toggleTransformationBar() {
        activeCategoryBar = null;
        showTransformationBar = !showTransformationBar;
    }

    /** Hides the transformation bar. */
    public static void hideTransformationBar() {
        showTransformationBar = false;
    }

    /** @return true if any custom category bar is currently displayed */
    public static boolean isShowingCategoryBar() {
        return activeCategoryBar != null;
    }

    /** @return the currently shown category key, or null */
    @Nullable
    public static ResourceLocation getActiveCategoryBar() {
        return activeCategoryBar;
    }

    /**
     * Toggles a custom category bar on/off. Hides transformation bar.
     * If a different category bar is showing, switches to this one.
     *
     * @param categoryKey the category to show/toggle
     */
    public static void toggleCategoryBar(ResourceLocation categoryKey) {
        showTransformationBar = false;
        if (categoryKey.equals(activeCategoryBar)) {
            activeCategoryBar = null;
        } else {
            activeCategoryBar = categoryKey;
        }
    }

    /** Hides any custom category bar. */
    public static void hideCategoryBar() {
        activeCategoryBar = null;
    }

    /** @return true if any alternate bar (transformation or category) is showing */
    public static boolean isShowingAlternateBar() {
        return showTransformationBar || activeCategoryBar != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Page tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cycles to the next page in the client data.
     * Called when the page toggle key is pressed.
     */
    public static void cyclePage() {
        SomniumPlayerData data = ClientAbilityData.getLocalData();
        if (data != null) {
            int nextPage = (data.getActivePage() + 1) % SomniumPlayerData.MAX_PAGES;
            data.setActivePage(nextPage);
        }
    }

    /** @return the current active page from client data, or 0 */
    public static int getCurrentPage() {
        SomniumPlayerData data = ClientAbilityData.getLocalData();
        return data != null ? data.getActivePage() : 0;
    }

    /** @return true if the player has abilities on more than one page */
    private static boolean isPaged() {
        SomniumPlayerData data = ClientAbilityData.getLocalData();
        if (data == null) return false;
        // Show paged texture if any slot on page 1+ has content
        for (int page = 1; page < SomniumPlayerData.MAX_PAGES; page++) {
            for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
                if (data.getBarSlotKey(page, slot) != null) return true;
            }
        }
        return false;
    }

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

        // Determine if we should show the paged texture
        // Transformation bar and category bars never show page arrows
        boolean paged = !showTransformationBar && activeCategoryBar == null && isPaged();
        int visibleHeight = paged ? VISIBLE_HEIGHT_PAGED : VISIBLE_HEIGHT_NORMAL;
        ResourceLocation texture;
        if (paged) {
            int currentPage = data.getActivePage();
            boolean canGoBack = currentPage > 0;
            boolean canGoForward = currentPage < SomniumPlayerData.MAX_PAGES - 1;
            if (canGoBack && canGoForward) {
                texture = BAR_TEXTURE_PAGED_BOTH;
            } else if (canGoBack) {
                texture = BAR_TEXTURE_PAGED_LEFT;
            } else {
                texture = BAR_TEXTURE_PAGED_RIGHT;
            }
        } else {
            texture = BAR_TEXTURE;
        }

        // Get config values
        BarPosition position = SomniumClientConfig.getBarPosition();
        int offsetX = SomniumClientConfig.BAR_OFFSET_X.get();
        int offsetY = SomniumClientConfig.BAR_OFFSET_Y.get();

        // Calculate bar position on screen
        int barX = calculateBarX(position, screenWidth, offsetX);
        int barY = calculateBarY(position, screenHeight, visibleHeight, offsetY);

        Font font = mc.font;

        if (position.isTop()) {
            // Render flipped for top positions
            renderFlipped(graphics, font, data, texture, barX, barY,
                    visibleHeight, position, screenWidth, screenHeight);
        } else {
            // Render normal for bottom positions
            renderNormal(graphics, font, data, texture, barX, barY,
                    visibleHeight, position);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Normal rendering (bottom positions)
    // ═══════════════════════════════════════════════════════════════════

    private void renderNormal(GuiGraphics graphics, Font font, SomniumPlayerData data,
                              ResourceLocation texture, int barX, int barY,
                              int visibleHeight, BarPosition position) {
        // Render bar background texture
        graphics.blit(texture, barX, barY, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        // Render ability icons and cooldown overlays in each slot
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            int iconX = barX + SLOT_ICON_X;
            int iconY = barY + SLOT_ICON_Y[slot];

            renderSlotContents(graphics, data, slot, iconX, iconY);
            renderKeybindLabel(graphics, font, slot, barX, iconY, position);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Flipped rendering (top positions)
    // ═══════════════════════════════════════════════════════════════════

    private void renderFlipped(GuiGraphics graphics, Font font, SomniumPlayerData data,
                               ResourceLocation texture, int barX, int barY,
                               int visibleHeight, BarPosition position,
                               int screenWidth, int screenHeight) {
        var poseStack = graphics.pose();

        // Flip the bar texture and icons vertically
        poseStack.pushPose();
        float centerX = barX + TEX_WIDTH / 2f;
        float centerY = barY + visibleHeight / 2f;
        poseStack.translate(centerX, centerY, 0);
        poseStack.scale(1, -1, 1);
        poseStack.translate(-centerX, -centerY, 0);

        // Render bar background (flipped via PoseStack)
        graphics.blit(texture, barX, barY, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        // Render ability icons (flipped via PoseStack)
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            int iconX = barX + SLOT_ICON_X;
            int iconY = barY + SLOT_ICON_Y[slot];
            renderSlotContents(graphics, data, slot, iconX, iconY);
        }

        poseStack.popPose();

        // Render keybind labels OUTSIDE the flip transform (text stays readable)
        // When flipped, slot visual positions are reversed
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            // Calculate where the slot appears after the flip
            int originalIconY = barY + SLOT_ICON_Y[slot];
            int flippedIconY = (int) (2 * centerY - originalIconY - ICON_SIZE);
            renderKeybindLabel(graphics, font, slot, barX, flippedIconY, position);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Slot content rendering
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Renders the icon and cooldown overlay for a single bar slot.
     */
    private void renderSlotContents(GuiGraphics graphics, SomniumPlayerData data,
                                    int slot, int iconX, int iconY) {
        AbilityInstance instance;
        if (activeCategoryBar != null) {
            instance = data.getCategoryBarSlotInstance(activeCategoryBar, slot);
        } else if (showTransformationBar) {
            instance = data.getTransBarSlotInstance(slot);
        } else {
            instance = data.getBarSlotInstance(slot);
        }
        if (instance == null) return;

        AbilityType type = instance.getAbilityType();
        ResourceLocation iconTexture = type.getIcon();

        // Render ability icon
        if (iconTexture != null) {
            RenderSystem.enableBlend();
            graphics.blit(iconTexture, iconX, iconY, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            RenderSystem.disableBlend();
        }

        // Render cooldown overlay
        if (instance.isOnCooldown()) {
            float progress = instance.getCooldownProgress();
            int cooldownHeight = (int) (ICON_SIZE * (1.0f - progress));
            if (cooldownHeight > 0) {
                // Overlay anchored to bottom, shrinks downward as cooldown completes
                graphics.fill(iconX, iconY + ICON_SIZE - cooldownHeight,
                        iconX + ICON_SIZE, iconY + ICON_SIZE, 0x80FFFFFF);
            }
        }

        // Render active indicator (for toggle abilities that are on)
        if (instance.isActive()) {
            // Thin colored border to indicate active state
            int activeColor = 0x8000FF00; // semi-transparent green
            graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + 1, activeColor);
            graphics.fill(iconX, iconY + ICON_SIZE - 1, iconX + ICON_SIZE, iconY + ICON_SIZE, activeColor);
            graphics.fill(iconX, iconY, iconX + 1, iconY + ICON_SIZE, activeColor);
            graphics.fill(iconX + ICON_SIZE - 1, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, activeColor);
        }
    }

    /**
     * Renders the keybind label for a slot on the appropriate side of the bar.
     */
    private void renderKeybindLabel(GuiGraphics graphics, Font font, int slot,
                                    int barX, int iconY, BarPosition position) {
        String keyName = SomniumKeybinds.getSlotKeyName(slot);
        int textWidth = font.width(keyName);

        int textX;
        if (position.isRight()) {
            // Bar is on the right — labels go to the left of the bar
            textX = barX - textWidth - KEYBIND_LABEL_GAP;
        } else {
            // Bar is on the left — labels go to the right of the bar
            textX = barX + TEX_WIDTH + KEYBIND_LABEL_GAP;
        }

        // Center the label vertically within the slot
        int textY = iconY + (ICON_SIZE - font.lineHeight) / 2;

        // Draw with shadow for readability
        graphics.drawString(font, keyName, textX, textY, 0xFFFFFF, true);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Position calculation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the X position of the bar on screen.
     */
    private int calculateBarX(BarPosition position, int screenWidth, int offsetX) {
        if (position.isRight()) {
            return screenWidth - TEX_WIDTH - offsetX;
        } else {
            return offsetX;
        }
    }

    /**
     * Calculates the Y position of the bar on screen.
     * For bottom positions, the bar is anchored to the bottom edge.
     * For top positions, the bar is anchored to the top edge.
     */
    private int calculateBarY(BarPosition position, int screenHeight,
                              int visibleHeight, int offsetY) {
        if (position.isTop()) {
            return offsetY;
        } else {
            return screenHeight - visibleHeight - offsetY;
        }
    }
}