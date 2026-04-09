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
import net.eclipce.somnium.network.SomniumNetwork;
import net.eclipce.somnium.network.UpdateBarSlotPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The ability inventory screen for managing abilities and bar layout.
 *
 * <h3>Layout</h3>
 * <ul>
 *     <li><b>Left tabs</b> — one per granted power, dynamically generated, scrollable</li>
 *     <li><b>Right tabs</b> — ability type filter: Transformations (T), Standard (A), Passives (P)</li>
 *     <li><b>Ability grid</b> — 5×8 grid showing abilities filtered by power + type</li>
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

    // Background texture canvas
    private static final int TEX_W = 427, TEX_H = 240;
    // Visible area within texture
    private static final int VIS_X = 125, VIS_Y = 37, VIS_W = 197, VIS_H = 166;

    // --- Ability grid (slot overlay) ---
    // Matches ability_slots.png layout: 5 cols × 8 rows, 18px pitch
    private static final int GRID_REL_X = 10, GRID_REL_Y = 11;
    private static final int GRID_COLS = 5, GRID_ROWS = 8;
    private static final int CELL_PITCH = 18;
    private static final int CELL_CONTENT = 16;
    // Icon draw offset within each cell (1px border on left/top)
    private static final int CELL_ICON_OFFSET = 1;

    // --- Ability bar slots (built into background texture) ---
    private static final int BAR_REL_X = 131;
    private static final int[] BAR_SLOT_REL_Y = {28, 47, 66, 85, 104, 123};
    private static final int ICON_SIZE = 16;

    // --- Left power tabs ---
    private static final int LEFT_TAB_REL_X = -25;
    private static final int LEFT_TAB_START_REL_Y = 20;
    private static final int LEFT_TAB_SPACING = 29;
    private static final int LEFT_TAB_W = 32, LEFT_TAB_H = 28;
    private static final int MAX_VISIBLE_TABS = 4;

    // --- Left tab scroll arrows ---
    private static final int SCROLL_ARROW_REL_X = LEFT_TAB_REL_X + 3;
    private static final int SCROLL_UP_REL_Y = LEFT_TAB_START_REL_Y - 14;
    private static final int SCROLL_DOWN_REL_Y = LEFT_TAB_START_REL_Y + MAX_VISIBLE_TABS * LEFT_TAB_SPACING + 2;
    private static final int SCROLL_ARROW_W = 32, SCROLL_ARROW_H = 32;

    // --- Right type tabs (T, A, P) --- positions match texture built-in tabs
    private static final int RIGHT_TAB_REL_X = 170;
    private static final int[] RIGHT_TAB_REL_Y = {43, 70, 97};
    private static final int RIGHT_TAB_W = 26, RIGHT_TAB_H = 25;
    private static final int RIGHT_TAB_TEX_W = 32, RIGHT_TAB_TEX_H = 28;
    private static final String[] TYPE_TAB_LABELS = {"T", "A", "P"};

    // --- Page arrows (below bar, overlay on built-in arrows) ---
    private static final int PAGE_ARROW_W = 8, PAGE_ARROW_H = 10;
    private static final int PAGE_BACK_REL_X = 128, PAGE_BACK_REL_Y = 145;
    private static final int PAGE_FWD_REL_X = 142, PAGE_FWD_REL_Y = 145;

    // ═══════════════════════════════════════════════════════════════════
    //  Ability type tab indices
    // ═══════════════════════════════════════════════════════════════════

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
        // Each row is CELL_PITCH px; +1 for the initial top border
        int cropHeight = Math.min(144, 1 + rowsNeeded * CELL_PITCH);

        // Blit from the same UV space as the slots texture
        // Texture origin for slots: (135, 48) in the 427×240 canvas
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

            // Power icon centered in tab
            Power power = powers.get(powerIndex);
            if (power.getIcon() != null) {
                graphics.blit(power.getIcon(), tabX + 8, tabY + 6,
                        0, 0, 16, 16, 16, 16);
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

            // Label centered in tab
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

                if (type.getIcon() != null) {
                    RenderSystem.enableBlend();
                    graphics.blit(type.getIcon(), iconX, iconY,
                            0, 0, CELL_CONTENT, CELL_CONTENT, CELL_CONTENT, CELL_CONTENT);
                    RenderSystem.disableBlend();
                } else {
                    graphics.fill(iconX + 2, iconY + 2,
                            iconX + CELL_CONTENT - 2, iconY + CELL_CONTENT - 2,
                            0xFF555555);
                }

                if (isInBounds(mouseX, mouseY, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) {
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
            }

            if (isInBounds(mouseX, mouseY, slotX, slotY, ICON_SIZE, ICON_SIZE)) {
                graphics.fill(slotX, slotY,
                        slotX + ICON_SIZE, slotY + ICON_SIZE,
                        0x40FFFFFF);
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

    /**
     * Renders the page arrows below the ability bar.
     * The background texture has built-in "disabled" arrows. We overlay
     * the highlighted arrow texture when that direction IS clickable.
     */
    private void renderPageArrows(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean canGoBack = selectedPage > 0;
        boolean canGoFwd = selectedPage < SomniumPlayerData.MAX_PAGES - 1;

        // Only overlay the highlighted arrow when the direction is available
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

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
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
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(type.getDisplayName());
                    Component desc = type.getDescription();
                    if (desc != null) tooltip.add(desc);
                    graphics.renderTooltip(font, tooltip, java.util.Optional.empty(),
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
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(instance.getAbilityType().getDisplayName());
                    tooltip.add(Component.translatable("tooltip." + Somnium.MOD_ID + ".click_to_remove")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    graphics.renderTooltip(font, tooltip, java.util.Optional.empty(),
                            mouseX, mouseY);
                }
                return;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Click handling
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        if (handlePowerTabClick(mx, my)) return true;
        if (handleTypeTabClick(mx, my)) return true;
        if (handleTabScrollClick(mx, my)) return true;
        if (handlePageArrowClick(mx, my)) return true;
        if (handleGridClick(mx, my)) return true;
        if (handleBarSlotClick(mx, my)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
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
        // Back arrow
        if (selectedPage > 0) {
            int bx = leftPos + PAGE_BACK_REL_X;
            int by = topPos + PAGE_BACK_REL_Y;
            if (isInBounds(mx, my, bx, by, PAGE_ARROW_W, PAGE_ARROW_H)) {
                selectedPage--;
                return true;
            }
        }
        // Forward arrow
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

    private boolean handleGridClick(int mx, int my) {
        // Don't allow equipping from the Passives tab (future feature)
        if (selectedTypeTab == TAB_PASSIVES) return false;

        int startIndex = gridScrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                if (index >= currentAbilities.size()) return false;

                int iconX = leftPos + GRID_REL_X + CELL_ICON_OFFSET + col * CELL_PITCH;
                int iconY = topPos + GRID_REL_Y + CELL_ICON_OFFSET + row * CELL_PITCH;

                if (isInBounds(mx, my, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) {
                    AbilityType type = currentAbilities.get(index);
                    ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(type);
                    if (abilityKey == null) return false;

                    // Find first empty bar slot on selected page
                    for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
                        if (data.getBarSlotKey(selectedPage, slot) == null) {
                            SomniumNetwork.sendToServer(
                                    new UpdateBarSlotPacket(selectedPage, slot, abilityKey));
                            data.setBarSlot(selectedPage, slot, type);
                            return true;
                        }
                    }
                    return true; // Bar full — click consumed
                }
            }
        }
        return false;
    }

    private boolean handleBarSlotClick(int mx, int my) {
        for (int slot = 0; slot < SomniumPlayerData.BAR_SIZE; slot++) {
            int slotX = leftPos + BAR_REL_X;
            int slotY = topPos + BAR_SLOT_REL_Y[slot];

            if (isInBounds(mx, my, slotX, slotY, ICON_SIZE, ICON_SIZE)) {
                if (data.getBarSlotKey(selectedPage, slot) != null) {
                    SomniumNetwork.sendToServer(
                            new UpdateBarSlotPacket(selectedPage, slot, null));
                    data.setBarSlot(selectedPage, slot, null);
                }
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
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}