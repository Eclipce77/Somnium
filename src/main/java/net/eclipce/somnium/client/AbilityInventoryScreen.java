package net.eclipce.somnium.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.network.RequestSyncPacket;
import net.eclipce.somnium.network.SomniumNetwork;
import net.eclipce.somnium.network.UpdateBarSlotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ability inventory screen for managing abilities and bar layout.
 *
 * <p>Supports drag-and-drop interaction similar to a standard Minecraft
 * container. Abilities can be picked up from the grid and placed onto
 * bar slots, or picked up from bar slots and returned to the pool.</p>
 *
 * <h3>Layout</h3>
 * <ul>
 *     <li><b>Left tabs</b> — one per granted power, scrollable</li>
 *     <li><b>Right tabs</b> — type filter: Transformations (T), Standard (A), Passives (P)</li>
 *     <li><b>Ability grid</b> — 5×8 grid showing filtered abilities</li>
 *     <li><b>Ability bar</b> — 6 slots for the selected page</li>
 *     <li><b>Page arrows</b> — below the bar, cycle through bar pages</li>
 * </ul>
 */
public class AbilityInventoryScreen extends Screen {

    // ═══════════════════════════════════════════════════════════════════
    //  Texture resources
    // ═══════════════════════════════════════════════════════════════════

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/ability_inventory.png");
    private static final ResourceLocation SLOTS_OVERLAY =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/ability_slots.png");
    private static final ResourceLocation PAGE_ARROW_BACK =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/bar_page_back_arrow.png");
    private static final ResourceLocation PAGE_ARROW_FWD =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/bar_page_forward_arrow.png");

    // Left power tabs
    private static final ResourceLocation TAB_LEFT_TOP =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_left_top.png");
    private static final ResourceLocation TAB_LEFT_TOP_SEL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_left_top_selected.png");
    private static final ResourceLocation TAB_LEFT_MID =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_left_middle.png");
    private static final ResourceLocation TAB_LEFT_MID_SEL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_left_middle_selected.png");
    private static final ResourceLocation TAB_LEFT_BOT =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_left_bottom.png");
    private static final ResourceLocation TAB_LEFT_BOT_SEL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_left_bottom_selected.png");

    // Right type tabs
    private static final ResourceLocation TAB_RIGHT_SEL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_right_bottom_selected.png");

    // Scroll arrows for left power tabs
    private static final ResourceLocation SCROLL_UP =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/server_list_move_up.png");
    private static final ResourceLocation SCROLL_UP_HL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/server_list_move_up_highlighted.png");
    private static final ResourceLocation SCROLL_DOWN =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/server_list_move_down.png");
    private static final ResourceLocation SCROLL_DOWN_HL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/server_list_move_down_highlighted.png");

    // ═══════════════════════════════════════════════════════════════════
    //  Layout constants (all relative to the visible area top-left)
    // ═══════════════════════════════════════════════════════════════════

    private static final int TEX_W = 427, TEX_H = 240;
    private static final int VIS_X = 125, VIS_Y = 37, VIS_W = 197, VIS_H = 166;

    // Ability grid (slot overlay): 5 cols × 8 rows, 18px pitch
    private static final int GRID_REL_X = 10, GRID_REL_Y = 11;
    private static final int GRID_COLS = 5, GRID_ROWS = 8;
    private static final int CELL_PITCH = 18;
    private static final int CELL_CONTENT = 16;
    private static final int CELL_ICON_OFFSET = 1;

    // Ability bar slots (built into background texture)
    private static final int BAR_REL_X = 131;
    private static final int[] BAR_SLOT_REL_Y = {28, 47, 66, 85, 104, 123};
    private static final int ICON_SIZE = 16;

    // Left power tabs
    private static final int LEFT_TAB_REL_X = -25;
    private static final int LEFT_TAB_START_REL_Y = 20;
    private static final int LEFT_TAB_SPACING = 29;
    private static final int LEFT_TAB_W = 32, LEFT_TAB_H = 28;
    private static final int MAX_VISIBLE_TABS = 4;

    // Left tab scroll arrows
    private static final int SCROLL_ARROW_REL_X = LEFT_TAB_REL_X + 3;
    private static final int SCROLL_UP_REL_Y = LEFT_TAB_START_REL_Y - 14;
    private static final int SCROLL_DOWN_REL_Y = LEFT_TAB_START_REL_Y + MAX_VISIBLE_TABS * LEFT_TAB_SPACING + 2;
    private static final int SCROLL_ARROW_W = 32, SCROLL_ARROW_H = 32;

    // Right type tabs (T, A, P) — positions match texture built-in tabs
    private static final int RIGHT_TAB_REL_X = 170;
    private static final int[] RIGHT_TAB_REL_Y = {43, 70, 97};
    private static final int RIGHT_TAB_W = 26, RIGHT_TAB_H = 25;
    private static final int RIGHT_TAB_TEX_W = 32, RIGHT_TAB_TEX_H = 28;
    private static final String[] TYPE_TAB_LABELS = {"T", "A", "P"};

    // Page arrows (below bar, overlay on built-in arrows)
    private static final int PAGE_ARROW_W = 8, PAGE_ARROW_H = 10;
    private static final int PAGE_BACK_REL_X = 128, PAGE_BACK_REL_Y = 145;
    private static final int PAGE_FWD_REL_X = 142, PAGE_FWD_REL_Y = 145;

    // Tab type indices
    private static final int TAB_TRANSFORMATIONS = 0;
    private static final int TAB_STANDARD = 1;
    private static final int TAB_PASSIVES = 2;

    // ═══════════════════════════════════════════════════════════════════
    //  State
    // ═══════════════════════════════════════════════════════════════════

    private final SomniumPlayerData data;
    private int leftPos, topPos;

    private int selectedPowerTab = 0;
    private int tabScrollOffset = 0;
    private int selectedTypeTab = TAB_STANDARD;
    private int selectedPage = 0;
    private int gridScrollRow = 0;

    private List<Power> powers = new ArrayList<>();
    private List<AbilityType> currentAbilities = new ArrayList<>();

    /** The ability currently being carried on the cursor, or null. */
    @Nullable
    private AbilityType heldAbility = null;

    /**
     * Whether the held ability came from a bar slot (true) or the grid (false).
     * When picked from bar, the slot was already cleared via packet.
     */
    private boolean heldFromBar = false;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════════

    public AbilityInventoryScreen(SomniumPlayerData data) {
        super(Component.translatable("screen." + Somnium.MOD_ID + ".ability_inventory"));
        this.data = data;
        this.selectedPage = data.getActivePage();
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - VIS_W) / 2;
        topPos = (height - VIS_H) / 2;
        refreshPowers();

        // Request fresh data from server when screen opens
        SomniumNetwork.sendToServer(new RequestSyncPacket());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ═══════════════════════════════════════════════════════════════════
    //  Data refresh
    // ═══════════════════════════════════════════════════════════════════

    private void refreshPowers() {
        powers = data.getGrantedPowers();
        if (selectedPowerTab >= powers.size()) {
            selectedPowerTab = Math.max(0, powers.size() - 1);
        }
        refreshAbilities();
    }

    private void refreshAbilities() {
        currentAbilities.clear();
        if (selectedPowerTab >= 0 && selectedPowerTab < powers.size()) {
            Power power = powers.get(selectedPowerTab);
            for (AbilityType type : power.getAbilityTypes()) {
                if (!data.isAbilityUnlocked(type)) continue;

                switch (selectedTypeTab) {
                    case TAB_TRANSFORMATIONS -> {
                        if (type instanceof TransformationAbilityType) {
                            currentAbilities.add(type);
                        }
                    }
                    case TAB_STANDARD -> {
                        if (!(type instanceof TransformationAbilityType)
                                && type.getActivationType() != ActivationType.PASSIVE) {
                            currentAbilities.add(type);
                        }
                    }
                    case TAB_PASSIVES -> {
                        if (type.getActivationType() == ActivationType.PASSIVE) {
                            currentAbilities.add(type);
                        }
                    }
                }
            }
        }
        gridScrollRow = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helper: transformation graying
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns true if the given ability should be grayed out and non-interactive.
     * Transformations are grayed when another transformation is active.
     */
    private boolean isAbilityGrayed(AbilityType type) {
        if (type instanceof TransformationAbilityType && data.hasActiveTransformation()) {
            // Allow interacting with the currently active transformation
            ResourceLocation activeKey = data.getActiveTransformation();
            ResourceLocation thisKey = SomniumRegistries.getAbilityKey(type);
            return !java.util.Objects.equals(activeKey, thisKey);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Rendering
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderMainBackground(graphics);
        renderSlotsOverlay(graphics);
        renderPowerTabs(graphics, mouseX, mouseY);
        renderTypeTabs(graphics, mouseX, mouseY);
        renderAbilityGrid(graphics, mouseX, mouseY);
        renderBarSlots(graphics, mouseX, mouseY);
        renderTabScrollArrows(graphics, mouseX, mouseY);
        renderPageArrows(graphics, mouseX, mouseY);
        renderTooltips(graphics, mouseX, mouseY);
        renderHeldAbility(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainBackground(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        graphics.blit(BACKGROUND, leftPos, topPos,
                VIS_X, VIS_Y, VIS_W, VIS_H, TEX_W, TEX_H);
        RenderSystem.disableBlend();
    }

    /**
     * Renders the slot grid overlay, cropped to show only enough rows
     * for the current number of abilities.
     */
    private void renderSlotsOverlay(GuiGraphics graphics) {
        if (currentAbilities.isEmpty()) return;

        int rowsNeeded = Math.min(GRID_ROWS,
                (currentAbilities.size() + GRID_COLS - 1) / GRID_COLS);
        int cropHeight = Math.min(144, 1 + rowsNeeded * CELL_PITCH);

        int slotsTexX = 135, slotsTexY = 48;
        RenderSystem.enableBlend();
        graphics.blit(SLOTS_OVERLAY,
                leftPos + GRID_REL_X, topPos + GRID_REL_Y,
                slotsTexX, slotsTexY,
                GRID_COLS * CELL_PITCH + 1, cropHeight,
                TEX_W, TEX_H);
        RenderSystem.disableBlend();
    }

    private void renderPowerTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        if (powers.isEmpty()) return;

        int visibleCount = Math.min(powers.size() - tabScrollOffset, MAX_VISIBLE_TABS);
        for (int i = 0; i < visibleCount; i++) {
            int powerIndex = i + tabScrollOffset;
            boolean isSelected = (powerIndex == selectedPowerTab);
            boolean isTop = (i == 0 && tabScrollOffset == 0);
            boolean isBottom = (i == visibleCount - 1
                    && powerIndex == powers.size() - 1);

            ResourceLocation tabTex;
            if (isTop) tabTex = isSelected ? TAB_LEFT_TOP_SEL : TAB_LEFT_TOP;
            else if (isBottom) tabTex = isSelected ? TAB_LEFT_BOT_SEL : TAB_LEFT_BOT;
            else tabTex = isSelected ? TAB_LEFT_MID_SEL : TAB_LEFT_MID;

            int tabX = leftPos + LEFT_TAB_REL_X;
            int tabY = topPos + LEFT_TAB_START_REL_Y + i * LEFT_TAB_SPACING;

            graphics.blit(tabTex, tabX, tabY, 0, 0,
                    LEFT_TAB_W, LEFT_TAB_H, LEFT_TAB_W, LEFT_TAB_H);

            // Power icon or first letter fallback
            Power power = powers.get(powerIndex);
            if (power.getIcon() != null) {
                graphics.blit(power.getIcon(), tabX + 8, tabY + 6,
                        0, 0, 16, 16, 16, 16);
            } else {
                // Fallback: render first letter of power name
                String initial = power.getDisplayName().getString();
                if (!initial.isEmpty()) {
                    initial = initial.substring(0, 1).toUpperCase();
                    int tx = tabX + (LEFT_TAB_W - font.width(initial)) / 2;
                    int ty = tabY + (LEFT_TAB_H - font.lineHeight) / 2;
                    graphics.drawString(font, initial, tx, ty,
                            isSelected ? 0xFFFFFF : 0xAAAAAA, false);
                }
            }
        }
    }

    private void renderTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < 3; i++) {
            boolean isSelected = (i == selectedTypeTab);
            int tabX = leftPos + RIGHT_TAB_REL_X;
            int tabY = topPos + RIGHT_TAB_REL_Y[i];

            if (isSelected) {
                graphics.blit(TAB_RIGHT_SEL, tabX, tabY,
                        RIGHT_TAB_W, RIGHT_TAB_H,
                        0, 0,
                        RIGHT_TAB_TEX_W, RIGHT_TAB_TEX_H,
                        RIGHT_TAB_TEX_W, RIGHT_TAB_TEX_H);
            }

            String label = TYPE_TAB_LABELS[i];
            int textX = tabX + (RIGHT_TAB_W - font.width(label)) / 2;
            int textY = tabY + (RIGHT_TAB_H - font.lineHeight) / 2;
            graphics.drawString(font, label, textX, textY,
                    isSelected ? 0xFFFFFF : 0xAAAAAA, false);
        }
    }

    private void renderAbilityGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int startIndex = gridScrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                if (index >= currentAbilities.size()) return;

                AbilityType type = currentAbilities.get(index);
                int iconX = leftPos + GRID_REL_X + CELL_ICON_OFFSET + col * CELL_PITCH;
                int iconY = topPos + GRID_REL_Y + CELL_ICON_OFFSET + row * CELL_PITCH;
                boolean grayed = isAbilityGrayed(type);

                // Render ability icon
                if (type.getIcon() != null) {
                    RenderSystem.enableBlend();
                    if (grayed) {
                        RenderSystem.setShaderColor(0.4f, 0.4f, 0.4f, 0.7f);
                    }
                    graphics.blit(type.getIcon(), iconX, iconY,
                            0, 0, CELL_CONTENT, CELL_CONTENT, CELL_CONTENT, CELL_CONTENT);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.disableBlend();
                } else {
                    int color = grayed ? 0xFF333333 : 0xFF555555;
                    graphics.fill(iconX + 2, iconY + 2,
                            iconX + CELL_CONTENT - 2, iconY + CELL_CONTENT - 2,
                            color);
                }

                // Cooldown sweep overlay
                AbilityInstance instance = data.getAbilityInstance(type);
                if (instance != null && instance.isOnCooldown()) {
                    float progress = instance.getCooldownProgress();
                    // Render a partial overlay from top, shrinking as cooldown progresses
                    int overlayHeight = Math.round(CELL_CONTENT * (1.0f - progress));
                    if (overlayHeight > 0) {
                        graphics.fill(iconX, iconY,
                                iconX + CELL_CONTENT, iconY + overlayHeight,
                                0x80000000);
                    }
                }

                // Duplicate indicator: dim if already on current page's bar
                ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(type);
                if (abilityKey != null && data.isAbilityOnPage(selectedPage, abilityKey)) {
                    graphics.fill(iconX, iconY,
                            iconX + CELL_CONTENT, iconY + CELL_CONTENT,
                            0x40FF0000);
                }

                // Hover highlight (only if not carrying an ability)
                if (heldAbility == null && !grayed
                        && isInBounds(mouseX, mouseY, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) {
                    graphics.fill(iconX, iconY,
                            iconX + CELL_CONTENT, iconY + CELL_CONTENT,
                            0x40FFFFFF);
                }
            }
        }
    }

    private void renderBarSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        // Page number centered above the bar column (bar spans rel x 127-150)
        String pageLabel = (selectedPage + 1) + "/" + SomniumPlayerData.MAX_PAGES;
        int barCenterX = leftPos + 127 + 12;
        int labelX = barCenterX - font.width(pageLabel) / 2;
        int labelY = topPos + 3;
        graphics.drawString(font, pageLabel, labelX, labelY, 0xFFFFFF, true);

        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            int slotX = leftPos + BAR_REL_X;
            int slotY = topPos + BAR_SLOT_REL_Y[slot];

            AbilityInstance instance = data.getBarSlotInstance(selectedPage, slot);
            if (instance != null) {
                AbilityType type = instance.getAbilityType();

                // Render icon
                if (type.getIcon() != null) {
                    RenderSystem.enableBlend();
                    graphics.blit(type.getIcon(), slotX, slotY,
                            0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                    RenderSystem.disableBlend();
                } else {
                    graphics.fill(slotX + 2, slotY + 2,
                            slotX + ICON_SIZE - 2, slotY + ICON_SIZE - 2,
                            0xFF555555);
                }

                // Active/toggle indicator — green border if currently active
                if (instance.isActive()) {
                    // Top
                    graphics.fill(slotX, slotY, slotX + ICON_SIZE, slotY + 1, 0xFF00FF00);
                    // Bottom
                    graphics.fill(slotX, slotY + ICON_SIZE - 1, slotX + ICON_SIZE, slotY + ICON_SIZE, 0xFF00FF00);
                    // Left
                    graphics.fill(slotX, slotY, slotX + 1, slotY + ICON_SIZE, 0xFF00FF00);
                    // Right
                    graphics.fill(slotX + ICON_SIZE - 1, slotY, slotX + ICON_SIZE, slotY + ICON_SIZE, 0xFF00FF00);
                }

                // Cooldown overlay on bar slot too
                if (instance.isOnCooldown()) {
                    float progress = instance.getCooldownProgress();
                    int overlayHeight = Math.round(ICON_SIZE * (1.0f - progress));
                    if (overlayHeight > 0) {
                        graphics.fill(slotX, slotY,
                                slotX + ICON_SIZE, slotY + overlayHeight,
                                0x80000000);
                    }
                }

                // Transformation indicator — "T" badge for transformation abilities
                if (type instanceof TransformationAbilityType) {
                    graphics.drawString(font, "T", slotX + 1, slotY + 1, 0xFFFF00, false);
                }
            }

            // Hover highlight
            if (isInBounds(mouseX, mouseY, slotX, slotY, ICON_SIZE, ICON_SIZE)) {
                // Green highlight when holding an ability (can drop here)
                // White highlight when empty cursor (can pick up)
                int highlightColor = heldAbility != null ? 0x4000FF00 : 0x40FFFFFF;
                graphics.fill(slotX, slotY,
                        slotX + ICON_SIZE, slotY + ICON_SIZE,
                        highlightColor);
            }
        }
    }

    private void renderTabScrollArrows(GuiGraphics graphics, int mouseX, int mouseY) {
        if (powers.size() <= MAX_VISIBLE_TABS) return;

        if (tabScrollOffset > 0) {
            int ax = leftPos + SCROLL_ARROW_REL_X;
            int ay = topPos + SCROLL_UP_REL_Y;
            boolean hovered = isInBounds(mouseX, mouseY, ax, ay, SCROLL_ARROW_W, SCROLL_ARROW_H);
            graphics.blit(hovered ? SCROLL_UP_HL : SCROLL_UP,
                    ax, ay, 0, 0, SCROLL_ARROW_W, SCROLL_ARROW_H,
                    SCROLL_ARROW_W, SCROLL_ARROW_H);
        }

        if (tabScrollOffset + MAX_VISIBLE_TABS < powers.size()) {
            int ax = leftPos + SCROLL_ARROW_REL_X;
            int ay = topPos + SCROLL_DOWN_REL_Y;
            boolean hovered = isInBounds(mouseX, mouseY, ax, ay, SCROLL_ARROW_W, SCROLL_ARROW_H);
            graphics.blit(hovered ? SCROLL_DOWN_HL : SCROLL_DOWN,
                    ax, ay, 0, 0, SCROLL_ARROW_W, SCROLL_ARROW_H,
                    SCROLL_ARROW_W, SCROLL_ARROW_H);
        }
    }

    private void renderPageArrows(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean canGoBack = selectedPage > 0;
        boolean canGoFwd = selectedPage < SomniumPlayerData.MAX_PAGES - 1;

        if (canGoBack) {
            int bx = leftPos + PAGE_BACK_REL_X;
            int by = topPos + PAGE_BACK_REL_Y;
            RenderSystem.enableBlend();
            graphics.blit(PAGE_ARROW_BACK, bx, by,
                    0, 0, PAGE_ARROW_W, PAGE_ARROW_H,
                    PAGE_ARROW_W, PAGE_ARROW_H);
            RenderSystem.disableBlend();
        }

        if (canGoFwd) {
            int fx = leftPos + PAGE_FWD_REL_X;
            int fy = topPos + PAGE_FWD_REL_Y;
            RenderSystem.enableBlend();
            graphics.blit(PAGE_ARROW_FWD, fx, fy,
                    0, 0, PAGE_ARROW_W, PAGE_ARROW_H,
                    PAGE_ARROW_W, PAGE_ARROW_H);
            RenderSystem.disableBlend();
        }
    }

    /**
     * Renders the ability being carried on the cursor, semi-transparent
     * and following the mouse position.
     */
    private void renderHeldAbility(GuiGraphics graphics, int mouseX, int mouseY) {
        if (heldAbility == null) return;

        int drawX = mouseX - ICON_SIZE / 2;
        int drawY = mouseY - ICON_SIZE / 2;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f);

        if (heldAbility.getIcon() != null) {
            graphics.blit(heldAbility.getIcon(), drawX, drawY,
                    0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        } else {
            graphics.fill(drawX + 2, drawY + 2,
                    drawX + ICON_SIZE - 2, drawY + ICON_SIZE - 2,
                    0xBB555555);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Don't show tooltips while carrying an ability
        if (heldAbility != null) return;

        // Grid tooltips
        int startIndex = gridScrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                if (index >= currentAbilities.size()) break;

                int iconX = leftPos + GRID_REL_X + CELL_ICON_OFFSET + col * CELL_PITCH;
                int iconY = topPos + GRID_REL_Y + CELL_ICON_OFFSET + row * CELL_PITCH;

                if (isInBounds(mouseX, mouseY, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) {
                    AbilityType type = currentAbilities.get(index);
                    List<Component> tooltip = buildAbilityTooltip(type);
                    graphics.renderTooltip(font, tooltip, Optional.empty(),
                            mouseX, mouseY);
                    return;
                }
            }
        }

        // Bar slot tooltips
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            int slotX = leftPos + BAR_REL_X;
            int slotY = topPos + BAR_SLOT_REL_Y[slot];
            if (isInBounds(mouseX, mouseY, slotX, slotY, ICON_SIZE, ICON_SIZE)) {
                AbilityInstance instance = data.getBarSlotInstance(selectedPage, slot);
                if (instance != null) {
                    List<Component> tooltip = buildAbilityTooltip(instance.getAbilityType());
                    tooltip.add(Component.translatable("tooltip." + Somnium.MOD_ID + ".click_to_remove")
                            .withStyle(ChatFormatting.GRAY));
                    graphics.renderTooltip(font, tooltip, Optional.empty(),
                            mouseX, mouseY);
                }
                return;
            }
        }
    }

    /**
     * Builds a tooltip for an ability, including name, description,
     * cooldown state, and type info.
     */
    private List<Component> buildAbilityTooltip(AbilityType type) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(type.getDisplayName());

        Component desc = type.getDescription();
        if (desc != null) tooltip.add(desc);

        // Cooldown info
        AbilityInstance instance = data.getAbilityInstance(type);
        if (instance != null && instance.isOnCooldown()) {
            float seconds = instance.getCooldownRemaining() / 20f;
            tooltip.add(Component.literal(String.format("Cooldown: %.1fs", seconds))
                    .withStyle(ChatFormatting.RED));
        }

        // Type indicator
        if (type instanceof TransformationAbilityType) {
            tooltip.add(Component.literal("Transformation")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        // Grayed indicator
        if (isAbilityGrayed(type)) {
            tooltip.add(Component.literal("Another transformation is active")
                    .withStyle(ChatFormatting.DARK_RED));
        }

        // Duplicate indicator
        ResourceLocation key = SomniumRegistries.getAbilityKey(type);
        if (key != null && data.isAbilityOnPage(selectedPage, key)) {
            tooltip.add(Component.literal("Already on this page's bar")
                    .withStyle(ChatFormatting.YELLOW));
        }

        return tooltip;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Click handling — drag and drop
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // Right-click: cancel held ability
        if (button == 1) {
            if (heldAbility != null) {
                heldAbility = null;
                heldFromBar = false;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // UI controls first (these work regardless of held ability)
        if (handlePowerTabClick(mx, my)) return true;
        if (handleTypeTabClick(mx, my)) return true;
        if (handleTabScrollClick(mx, my)) return true;
        if (handlePageArrowClick(mx, my)) return true;

        // Drag-and-drop interactions
        if (handleBarSlotClick(mx, my)) return true;
        if (handleGridClick(mx, my)) return true;

        // Clicked outside all interactive areas — drop held ability
        if (heldAbility != null) {
            heldAbility = null;
            heldFromBar = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handles clicks on bar slots.
     * - Empty cursor + occupied slot → pick up from bar
     * - Held ability + empty slot → place ability
     * - Held ability + occupied slot → replace ability
     */
    private boolean handleBarSlotClick(int mx, int my) {
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            int slotX = leftPos + BAR_REL_X;
            int slotY = topPos + BAR_SLOT_REL_Y[slot];

            if (!isInBounds(mx, my, slotX, slotY, ICON_SIZE, ICON_SIZE)) continue;

            if (heldAbility != null) {
                // Placing held ability into this bar slot
                ResourceLocation heldKey = SomniumRegistries.getAbilityKey(heldAbility);
                if (heldKey == null) return true;

                // Duplicate check: can't place if already on this page
                if (data.isAbilityOnPage(selectedPage, heldKey)) {
                    return true; // Consumed click, but do nothing
                }

                // Don't allow passives onto the bar
                if (!heldAbility.isBarEquippable()) return true;

                // Send equip packet (server handles replacement events)
                SomniumNetwork.sendToServer(
                        new UpdateBarSlotPacket(selectedPage, slot, heldKey));

                // Optimistic client update
                data.setBarSlot(selectedPage, slot, heldAbility);

                heldAbility = null;
                heldFromBar = false;
                return true;

            } else {
                // Picking up from bar slot
                AbilityInstance instance = data.getBarSlotInstance(selectedPage, slot);
                if (instance == null) return true; // Empty slot

                heldAbility = instance.getAbilityType();
                heldFromBar = true;

                // Send clear packet
                SomniumNetwork.sendToServer(
                        new UpdateBarSlotPacket(selectedPage, slot, null));

                // Optimistic client update
                data.setBarSlot(selectedPage, slot, null);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles clicks on the ability grid.
     * - Empty cursor → pick up ability from grid
     * - Held ability → drop back (cancel, since grid is creative-mode)
     */
    private boolean handleGridClick(int mx, int my) {
        // Passives tab: no drag-and-drop (future: toggle on/off)
        if (selectedTypeTab == TAB_PASSIVES) return false;

        int startIndex = gridScrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                if (index >= currentAbilities.size()) return false;

                int iconX = leftPos + GRID_REL_X + CELL_ICON_OFFSET + col * CELL_PITCH;
                int iconY = topPos + GRID_REL_Y + CELL_ICON_OFFSET + row * CELL_PITCH;

                if (!isInBounds(mx, my, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) continue;

                if (heldAbility != null) {
                    // Clicking grid with held ability → just drop it (returns to pool)
                    heldAbility = null;
                    heldFromBar = false;
                    return true;
                }

                // Pick up ability from grid
                AbilityType type = currentAbilities.get(index);

                // Can't pick up grayed transformations
                if (isAbilityGrayed(type)) return true;

                // Can't pick up non-bar-equippable abilities
                if (!type.isBarEquippable()) return true;

                heldAbility = type;
                heldFromBar = false;
                return true;
            }
        }

        return false;
    }

    private boolean handlePowerTabClick(int mx, int my) {
        if (powers.isEmpty()) return false;
        int visibleCount = Math.min(powers.size() - tabScrollOffset, MAX_VISIBLE_TABS);
        for (int i = 0; i < visibleCount; i++) {
            int tabX = leftPos + LEFT_TAB_REL_X;
            int tabY = topPos + LEFT_TAB_START_REL_Y + i * LEFT_TAB_SPACING;
            if (isInBounds(mx, my, tabX, tabY, LEFT_TAB_W, LEFT_TAB_H)) {
                selectedPowerTab = i + tabScrollOffset;
                refreshAbilities();
                // Drop held ability when switching tabs
                heldAbility = null;
                heldFromBar = false;
                return true;
            }
        }
        return false;
    }

    private boolean handleTypeTabClick(int mx, int my) {
        for (int i = 0; i < 3; i++) {
            int tabX = leftPos + RIGHT_TAB_REL_X;
            int tabY = topPos + RIGHT_TAB_REL_Y[i];
            if (isInBounds(mx, my, tabX, tabY, RIGHT_TAB_W, RIGHT_TAB_H)) {
                selectedTypeTab = i;
                refreshAbilities();
                // Drop held ability when switching type tabs
                heldAbility = null;
                heldFromBar = false;
                return true;
            }
        }
        return false;
    }

    private boolean handleTabScrollClick(int mx, int my) {
        if (powers.size() <= MAX_VISIBLE_TABS) return false;

        if (tabScrollOffset > 0) {
            int ax = leftPos + SCROLL_ARROW_REL_X;
            int ay = topPos + SCROLL_UP_REL_Y;
            if (isInBounds(mx, my, ax, ay, SCROLL_ARROW_W, SCROLL_ARROW_H)) {
                tabScrollOffset--;
                return true;
            }
        }
        if (tabScrollOffset + MAX_VISIBLE_TABS < powers.size()) {
            int ax = leftPos + SCROLL_ARROW_REL_X;
            int ay = topPos + SCROLL_DOWN_REL_Y;
            if (isInBounds(mx, my, ax, ay, SCROLL_ARROW_W, SCROLL_ARROW_H)) {
                tabScrollOffset++;
                return true;
            }
        }
        return false;
    }

    private boolean handlePageArrowClick(int mx, int my) {
        if (selectedPage > 0) {
            int bx = leftPos + PAGE_BACK_REL_X;
            int by = topPos + PAGE_BACK_REL_Y;
            if (isInBounds(mx, my, bx, by, PAGE_ARROW_W, PAGE_ARROW_H)) {
                selectedPage--;
                return true;
            }
        }
        if (selectedPage < SomniumPlayerData.MAX_PAGES - 1) {
            int fx = leftPos + PAGE_FWD_REL_X;
            int fy = topPos + PAGE_FWD_REL_Y;
            if (isInBounds(mx, my, fx, fy, PAGE_ARROW_W, PAGE_ARROW_H)) {
                selectedPage++;
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Scroll handling
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX, my = (int) mouseY;

        int gridLeft = leftPos + GRID_REL_X;
        int gridTop = topPos + GRID_REL_Y;
        int gridRight = gridLeft + GRID_COLS * CELL_PITCH;
        int gridBottom = gridTop + GRID_ROWS * CELL_PITCH;

        if (mx >= gridLeft && mx <= gridRight && my >= gridTop && my <= gridBottom) {
            int totalRows = (currentAbilities.size() + GRID_COLS - 1) / GRID_COLS;
            int maxScroll = Math.max(0, totalRows - GRID_ROWS);
            gridScrollRow = Math.max(0, Math.min(maxScroll,
                    gridScrollRow - (int) Math.signum(delta)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Screen close — drop held ability
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onClose() {
        heldAbility = null;
        heldFromBar = false;
        super.onClose();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}