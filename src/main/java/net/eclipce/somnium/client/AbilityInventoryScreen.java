package net.eclipce.somnium.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.category.AbilityCategory;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.prerequisite.Prerequisite;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.network.RequestSyncPacket;
import net.eclipce.somnium.network.SomniumNetwork;
import net.eclipce.somnium.network.TogglePassivePacket;
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
 * The ability inventory screen — three seamless modes sharing one Screen.
 *
 * <p>Modes are selected via right-side tabs:</p>
 * <ul>
 *     <li><b>Transformations (T)</b> — 5 placeholder bar slots, no pages</li>
 *     <li><b>Abilities (A)</b> — 6 bar slots with page arrows</li>
 *     <li><b>Passives (P)</b> — no bar slots, toggle on/off in grid</li>
 * </ul>
 */
public class AbilityInventoryScreen extends Screen {

    // ═══════════════════════════════════════════════════════════════════
    //  Texture resources — per-mode backgrounds
    // ═══════════════════════════════════════════════════════════════════

    private static final ResourceLocation BG_ABILITIES =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/ability_inventory.png");
    private static final ResourceLocation BG_TRANSFORMATIONS =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/transformation_inventory.png");
    private static final ResourceLocation BG_PASSIVES =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/passive_inventory.png");

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

    // Right type tabs (rendered separately from background)
    private static final ResourceLocation TAB_RIGHT =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_right_middle.png");
    private static final ResourceLocation TAB_RIGHT_SEL =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/assets/tab_right_middle_selected.png");

    // Type tab icons (16x16)
    private static final ResourceLocation ICON_TRANSFORMATIONS =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/icons/ability_inventory_transformations.png");
    private static final ResourceLocation ICON_ABILITIES =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/icons/ability_inventory_abilities.png");
    private static final ResourceLocation ICON_PASSIVES =
            new ResourceLocation(Somnium.MOD_ID, "textures/gui/inventory/icons/ability_inventory_passives.png");
    private static final ResourceLocation[] TYPE_TAB_ICONS = {
            ICON_TRANSFORMATIONS, ICON_ABILITIES, ICON_PASSIVES
    };
    private static final String[] TYPE_TAB_NAMES = {"Transformations", "Abilities", "Passives"};

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
    //  Layout constants
    // ═══════════════════════════════════════════════════════════════════

    private static final int TEX_W = 427, TEX_H = 240;
    private static final int VIS_X = 125, VIS_Y = 37, VIS_W = 197, VIS_H = 166;

    // Ability grid: 5 cols × 8 rows, 18px pitch
    private static final int GRID_REL_X = 10, GRID_REL_Y = 11;
    private static final int GRID_COLS = 5, GRID_ROWS = 8;
    private static final int CELL_PITCH = 18;
    private static final int CELL_CONTENT = 16;
    private static final int CELL_ICON_OFFSET = 1;

    // Ability bar slots (6 for all bar types)
    private static final int BAR_REL_X = 131;
    private static final int[] ABILITY_BAR_Y  = {27, 46, 65, 84, 103, 122};
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

    // Right type tabs (T, A, P) — rendered separately
    private static final int RIGHT_TAB_REL_X = 170;
    private static final int[] RIGHT_TAB_REL_Y = {43, 70, 97};
    private static final int RIGHT_TAB_W = 32, RIGHT_TAB_H = 28;

    // Page arrows (abilities mode only)
    private static final int PAGE_ARROW_W = 8, PAGE_ARROW_H = 10;
    private static final int PAGE_BACK_REL_X = 128, PAGE_BACK_REL_Y = 145;
    private static final int PAGE_FWD_REL_X = 142, PAGE_FWD_REL_Y = 145;

    // Mode indices — built-in modes
    private static final int MODE_TRANSFORMATIONS = 0;
    private static final int MODE_ABILITIES = 1;
    private static final int MODE_PASSIVES = 2;
    /** Custom category modes start at this offset. Mode = CATEGORY_START + index in AbilityCategory.getAll(). */
    private static final int MODE_CATEGORY_START = 3;

    // ═══════════════════════════════════════════════════════════════════
    //  State
    // ═══════════════════════════════════════════════════════════════════

    private final SomniumPlayerData data;
    private int leftPos, topPos;

    private int selectedPowerTab = 0;
    private int tabScrollOffset = 0;
    private int selectedMode = MODE_ABILITIES;
    private int selectedPage = 0;
    private int gridScrollRow = 0;

    private List<Power> powers = new ArrayList<>();
    private List<AbilityType> currentAbilities = new ArrayList<>();
    private List<AbilityCategory> registeredCategories = new ArrayList<>();

    @Nullable
    private AbilityType heldAbility = null;
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
        registeredCategories = new ArrayList<>(AbilityCategory.getAll());
        refreshPowers();
        SomniumNetwork.sendToServer(new RequestSyncPacket());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ═══════════════════════════════════════════════════════════════════
    //  Mode helpers
    // ═══════════════════════════════════════════════════════════════════

    private ResourceLocation getBackgroundTexture() {
        return switch (selectedMode) {
            case MODE_TRANSFORMATIONS -> BG_TRANSFORMATIONS;
            case MODE_PASSIVES -> BG_PASSIVES;
            case MODE_ABILITIES -> BG_ABILITIES;
            default -> BG_TRANSFORMATIONS; // Category modes use transformation layout (bar, no pages)
        };
    }

    private boolean hasBarSlots() {
        return selectedMode != MODE_PASSIVES;
    }

    private boolean hasPages() {
        return selectedMode == MODE_ABILITIES;
    }

    private int getBarSlotCount() {
        return selectedMode == MODE_PASSIVES ? 0 : 6;
    }

    private int[] getBarSlotYPositions() {
        return ABILITY_BAR_Y; // All bars use same Y positions (6 slots)
    }

    /** Returns the total number of right-side tabs (3 built-in + categories). */
    private int getTotalTabCount() {
        return 3 + registeredCategories.size();
    }

    /** Returns the category for the current mode, or null if built-in mode. */
    @Nullable
    private AbilityCategory getSelectedCategory() {
        if (selectedMode < MODE_CATEGORY_START) return null;
        int catIndex = selectedMode - MODE_CATEGORY_START;
        if (catIndex >= 0 && catIndex < registeredCategories.size()) {
            return registeredCategories.get(catIndex);
        }
        return null;
    }

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
            AbilityCategory selectedCat = getSelectedCategory();

            for (Power.PowerAbilityEntry entry : power.getEntries()) {
                AbilityType type = entry.getAbilityType();
                if (type == null) continue;

                // Check prerequisite — if unmet, ability is completely hidden
                Prerequisite prereq = entry.getPrerequisite();
                if (prereq != null && !prereq.isMet(data)) continue;

                if (selectedCat != null) {
                    // Custom category mode: show only abilities with matching category
                    if (type.getCategory() == selectedCat) {
                        currentAbilities.add(type);
                    }
                } else {
                    // Built-in modes: exclude categorized abilities
                    switch (selectedMode) {
                        case MODE_TRANSFORMATIONS -> {
                            if (type instanceof TransformationAbilityType
                                    && type.getCategory() == null) {
                                currentAbilities.add(type);
                            }
                        }
                        case MODE_ABILITIES -> {
                            if (!(type instanceof TransformationAbilityType)
                                    && type.getActivationType() != ActivationType.PASSIVE
                                    && type.getCategory() == null) {
                                currentAbilities.add(type);
                            }
                        }
                        case MODE_PASSIVES -> {
                            if (type.getActivationType() == ActivationType.PASSIVE
                                    && type.getCategory() == null) {
                                currentAbilities.add(type);
                            }
                        }
                    }
                }
            }
        }
        gridScrollRow = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  State helpers
    // ═══════════════════════════════════════════════════════════════════

    private boolean isAbilityGrayed(AbilityType type) {
        if (type instanceof TransformationAbilityType && data.hasActiveTransformation()) {
            ResourceLocation activeKey = data.getActiveTransformation();
            ResourceLocation thisKey = SomniumRegistries.getAbilityKey(type);
            return !java.util.Objects.equals(activeKey, thisKey);
        }
        return false;
    }

    private boolean isAbilityLocked(AbilityType type) {
        return !data.isAbilityUnlocked(type);
    }

    /**
     * For passives mode: returns true if the passive is currently disabled.
     */
    private boolean isPassiveDisabled(AbilityType type) {
        return type.getActivationType() == ActivationType.PASSIVE
                && !data.isPassiveEnabled(type);
    }

    @Nullable
    private Component getLockedProgressText(AbilityType type) {
        if (selectedPowerTab < 0 || selectedPowerTab >= powers.size()) return null;
        Power power = powers.get(selectedPowerTab);
        Power.PowerAbilityEntry entry = power.getEntry(type);
        if (entry == null || entry.getUnlockCondition() == null) return null;
        ResourceLocation powerKey = SomniumRegistries.getPowerKey(power);
        if (powerKey == null) return null;
        String progressKey = net.eclipce.somnium.core.unlock.ProgressionHandler
                .makeProgressKey(powerKey, type);
        if (progressKey == null) return null;
        net.minecraft.nbt.CompoundTag progress = data.getUnlockProgress(progressKey);
        if (progress == null) return null;
        return entry.getUnlockCondition().getProgressText(progress);
    }

    private float getLockedProgressFraction(AbilityType type) {
        if (selectedPowerTab < 0 || selectedPowerTab >= powers.size()) return 0f;
        Power power = powers.get(selectedPowerTab);
        Power.PowerAbilityEntry entry = power.getEntry(type);
        if (entry == null || entry.getUnlockCondition() == null) return 0f;
        ResourceLocation powerKey = SomniumRegistries.getPowerKey(power);
        if (powerKey == null) return 0f;
        String progressKey = net.eclipce.somnium.core.unlock.ProgressionHandler
                .makeProgressKey(powerKey, type);
        if (progressKey == null) return 0f;
        net.minecraft.nbt.CompoundTag progress = data.getUnlockProgress(progressKey);
        if (progress == null) return 0f;
        return entry.getUnlockCondition().getProgressFraction(progress);
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
        if (hasBarSlots()) renderBarSlots(graphics, mouseX, mouseY);
        renderTabScrollArrows(graphics, mouseX, mouseY);
        if (hasPages()) {
            renderPageNumber(graphics);
            renderPageArrows(graphics, mouseX, mouseY);
        }
        renderTooltips(graphics, mouseX, mouseY);
        renderHeldAbility(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainBackground(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        graphics.blit(getBackgroundTexture(), leftPos, topPos,
                VIS_X, VIS_Y, VIS_W, VIS_H, TEX_W, TEX_H);
        RenderSystem.disableBlend();
    }

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

            Power power = powers.get(powerIndex);
            if (power.getIcon() != null) {
                graphics.blit(power.getIcon(), tabX + 8, tabY + 6,
                        0, 0, 16, 16, 16, 16);
            } else {
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

    /**
     * Renders the right-side type tabs (T, A, P, + custom categories).
     */
    private void renderTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int totalTabs = getTotalTabCount();
        for (int i = 0; i < totalTabs; i++) {
            boolean isSelected = (i == selectedMode);
            int tabX = leftPos + RIGHT_TAB_REL_X;
            int tabY = topPos + RIGHT_TAB_REL_Y[0] + i * (RIGHT_TAB_H + 2);

            ResourceLocation tabTex = isSelected ? TAB_RIGHT_SEL : TAB_RIGHT;
            graphics.blit(tabTex, tabX, tabY, 0, 0,
                    RIGHT_TAB_W, RIGHT_TAB_H, RIGHT_TAB_W, RIGHT_TAB_H);

            // Render icon or category label
            if (i < 3) {
                // Built-in type tabs: render 16x16 icon centered
                int iconX = tabX + (RIGHT_TAB_W - 16) / 2;
                int iconY = tabY + (RIGHT_TAB_H - 16) / 2;
                graphics.blit(TYPE_TAB_ICONS[i], iconX, iconY, 0, 0,
                        16, 16, 16, 16);
            } else {
                int catIndex = i - MODE_CATEGORY_START;
                String label = "?";
                if (catIndex >= 0 && catIndex < registeredCategories.size()) {
                    label = registeredCategories.get(catIndex).getTabLabel();
                }
                int textX = tabX + (RIGHT_TAB_W - font.width(label)) / 2;
                int textY = tabY + (RIGHT_TAB_H - font.lineHeight) / 2;
                graphics.drawString(font, label, textX, textY,
                        isSelected ? 0xFFFFFF : 0xAAAAAA, false);
            }
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
                boolean locked = isAbilityLocked(type);
                boolean passiveOff = (selectedMode == MODE_PASSIVES
                        && !locked && isPassiveDisabled(type));
                boolean dimmed = grayed || locked || passiveOff;

                // Render ability icon
                if (type.getIcon() != null) {
                    RenderSystem.enableBlend();
                    if (dimmed) {
                        RenderSystem.setShaderColor(0.4f, 0.4f, 0.4f, 0.7f);
                    }
                    graphics.blit(type.getIcon(), iconX, iconY,
                            0, 0, CELL_CONTENT, CELL_CONTENT, CELL_CONTENT, CELL_CONTENT);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.disableBlend();
                } else {
                    int color = dimmed ? 0xFF333333 : 0xFF555555;
                    graphics.fill(iconX + 2, iconY + 2,
                            iconX + CELL_CONTENT - 2, iconY + CELL_CONTENT - 2,
                            color);
                }

                // Locked ability: progress bar at bottom
                if (locked) {
                    float fraction = getLockedProgressFraction(type);
                    int barWidth = Math.round((CELL_CONTENT - 2) * fraction);
                    graphics.fill(iconX + 1, iconY + CELL_CONTENT - 3,
                            iconX + CELL_CONTENT - 1, iconY + CELL_CONTENT - 1,
                            0xAA000000);
                    if (barWidth > 0) {
                        graphics.fill(iconX + 1, iconY + CELL_CONTENT - 3,
                                iconX + 1 + barWidth, iconY + CELL_CONTENT - 1,
                                0xAA00CC00);
                    }
                }

                // Cooldown sweep (unlocked, non-passive modes)
                if (!locked && selectedMode != MODE_PASSIVES) {
                    AbilityInstance instance = data.getAbilityInstance(type);
                    if (instance != null && instance.isOnCooldown()) {
                        float progress = instance.getCooldownProgress();
                        int overlayHeight = Math.round(CELL_CONTENT * (1.0f - progress));
                        if (overlayHeight > 0) {
                            graphics.fill(iconX, iconY,
                                    iconX + CELL_CONTENT, iconY + overlayHeight,
                                    0x80000000);
                        }
                    }
                }

                // Duplicate indicator (abilities/transformations/categories only)
                if (!locked && selectedMode != MODE_PASSIVES) {
                    ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(type);
                    if (abilityKey != null) {
                        boolean onBar;
                        if (selectedMode == MODE_TRANSFORMATIONS) {
                            onBar = data.isAbilityOnTransBar(abilityKey);
                        } else if (selectedMode >= MODE_CATEGORY_START) {
                            AbilityCategory cat = getSelectedCategory();
                            onBar = cat != null && data.isAbilityOnCategoryBar(cat.getId(), abilityKey);
                        } else {
                            onBar = data.isAbilityOnPage(selectedPage, abilityKey);
                        }
                        if (onBar) {
                            graphics.fill(iconX, iconY,
                                    iconX + CELL_CONTENT, iconY + CELL_CONTENT,
                                    0x40FF0000);
                        }
                    }
                }

                // Hover highlight
                if (heldAbility == null && !dimmed
                        && isInBounds(mouseX, mouseY, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) {
                    graphics.fill(iconX, iconY,
                            iconX + CELL_CONTENT, iconY + CELL_CONTENT,
                            0x40FFFFFF);
                }
            }
        }
    }

    private void renderBarSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        int slotCount = getBarSlotCount();
        int[] slotY = getBarSlotYPositions();

        for (int slot = 0; slot < slotCount; slot++) {
            int slotX = leftPos + BAR_REL_X;
            int sy = topPos + slotY[slot];

            // Read from the correct bar
            AbilityInstance instance = null;
            if (selectedMode == MODE_ABILITIES) {
                instance = data.getBarSlotInstance(selectedPage, slot);
            } else if (selectedMode == MODE_TRANSFORMATIONS) {
                instance = data.getTransBarSlotInstance(slot);
            } else if (selectedMode >= MODE_CATEGORY_START) {
                AbilityCategory cat = getSelectedCategory();
                if (cat != null) {
                    instance = data.getCategoryBarSlotInstance(cat.getId(), slot);
                }
            }

            if (instance != null) {
                AbilityType type = instance.getAbilityType();
                if (type.getIcon() != null) {
                    RenderSystem.enableBlend();
                    graphics.blit(type.getIcon(), slotX, sy,
                            0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                    RenderSystem.disableBlend();
                } else {
                    graphics.fill(slotX + 2, sy + 2,
                            slotX + ICON_SIZE - 2, sy + ICON_SIZE - 2,
                            0xFF555555);
                }

                // Active toggle indicator
                if (instance.isActive()) {
                    graphics.fill(slotX, sy, slotX + ICON_SIZE, sy + 1, 0xFF00FF00);
                    graphics.fill(slotX, sy + ICON_SIZE - 1, slotX + ICON_SIZE, sy + ICON_SIZE, 0xFF00FF00);
                    graphics.fill(slotX, sy, slotX + 1, sy + ICON_SIZE, 0xFF00FF00);
                    graphics.fill(slotX + ICON_SIZE - 1, sy, slotX + ICON_SIZE, sy + ICON_SIZE, 0xFF00FF00);
                }

                // Cooldown overlay
                if (instance.isOnCooldown()) {
                    float progress = instance.getCooldownProgress();
                    int overlayHeight = Math.round(ICON_SIZE * (1.0f - progress));
                    if (overlayHeight > 0) {
                        graphics.fill(slotX, sy,
                                slotX + ICON_SIZE, sy + overlayHeight,
                                0x80000000);
                    }
                }

                // Transformation badge
                if (type instanceof TransformationAbilityType) {
                    graphics.drawString(font, "T", slotX + 1, sy + 1, 0xFFFF00, false);
                }
            }

            // Hover highlight
            if (isInBounds(mouseX, mouseY, slotX, sy, ICON_SIZE, ICON_SIZE)) {
                int highlightColor = heldAbility != null ? 0x4000FF00 : 0x40FFFFFF;
                graphics.fill(slotX, sy, slotX + ICON_SIZE, sy + ICON_SIZE,
                        highlightColor);
            }
        }
    }

    /**
     * Renders just the current page number, centered between the GUI top
     * and the first ability bar slot. Only displayed in abilities mode.
     */
    private void renderPageNumber(GuiGraphics graphics) {
        String pageStr = String.valueOf(selectedPage + 1);
        int barCenterX = leftPos + BAR_REL_X + ICON_SIZE / 2;
        int labelX = barCenterX - font.width(pageStr) / 2;
        // Center vertically between GUI top (0) and first bar slot Y (27)
        int labelY = topPos + (ABILITY_BAR_Y[0] - font.lineHeight) / 2;
        graphics.drawString(font, pageStr, labelX, labelY, 0xFFFFFF, true);
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
        if (selectedPage > 0) {
            int bx = leftPos + PAGE_BACK_REL_X;
            int by = topPos + PAGE_BACK_REL_Y;
            RenderSystem.enableBlend();
            graphics.blit(PAGE_ARROW_BACK, bx, by,
                    0, 0, PAGE_ARROW_W, PAGE_ARROW_H,
                    PAGE_ARROW_W, PAGE_ARROW_H);
            RenderSystem.disableBlend();
        }
        if (selectedPage < SomniumPlayerData.MAX_PAGES - 1) {
            int fx = leftPos + PAGE_FWD_REL_X;
            int fy = topPos + PAGE_FWD_REL_Y;
            RenderSystem.enableBlend();
            graphics.blit(PAGE_ARROW_FWD, fx, fy,
                    0, 0, PAGE_ARROW_W, PAGE_ARROW_H,
                    PAGE_ARROW_W, PAGE_ARROW_H);
            RenderSystem.disableBlend();
        }
    }

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
        if (heldAbility != null) return;

        // Type tab tooltips (right side)
        int totalTabs = getTotalTabCount();
        for (int i = 0; i < totalTabs; i++) {
            int tabX = leftPos + RIGHT_TAB_REL_X;
            int tabY = topPos + RIGHT_TAB_REL_Y[0] + i * (RIGHT_TAB_H + 2);
            if (isInBounds(mouseX, mouseY, tabX, tabY, RIGHT_TAB_W, RIGHT_TAB_H)) {
                Component name;
                if (i < 3) {
                    name = Component.literal(TYPE_TAB_NAMES[i]);
                } else {
                    int catIndex = i - MODE_CATEGORY_START;
                    name = (catIndex >= 0 && catIndex < registeredCategories.size())
                            ? registeredCategories.get(catIndex).getDisplayName()
                            : Component.literal("Unknown");
                }
                graphics.renderTooltip(font,
                        List.of(name), Optional.empty(),
                        mouseX, mouseY);
                return;
            }
        }

        // Power tab tooltips (left side)
        if (data != null) {
            java.util.List<net.eclipce.somnium.core.power.Power> powers =
                    new java.util.ArrayList<>(data.getGrantedPowers());
            int visibleCount = Math.min(powers.size() - tabScrollOffset, MAX_VISIBLE_TABS);
            for (int v = 0; v < visibleCount; v++) {
                int tabIndex = tabScrollOffset + v;
                int tabX = leftPos + LEFT_TAB_REL_X;
                int tabY = topPos + LEFT_TAB_START_REL_Y + v * LEFT_TAB_SPACING;
                if (isInBounds(mouseX, mouseY, tabX, tabY, LEFT_TAB_W, LEFT_TAB_H)) {
                    net.eclipce.somnium.core.power.Power power = powers.get(tabIndex);
                    ResourceLocation key = net.eclipce.somnium.core.registry.SomniumRegistries
                            .getPowerKey(power);
                    String powerName = key != null ? key.getPath() : "Unknown";
                    // Capitalize first letter
                    powerName = powerName.substring(0, 1).toUpperCase()
                            + powerName.substring(1).replace('_', ' ');
                    graphics.renderTooltip(font,
                            List.of(Component.literal(powerName)), Optional.empty(),
                            mouseX, mouseY);
                    return;
                }
            }
        }

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
        if (hasBarSlots()) {
            int slotCount = getBarSlotCount();
            int[] slotY = getBarSlotYPositions();
            for (int slot = 0; slot < slotCount; slot++) {
                int slotX = leftPos + BAR_REL_X;
                int sy = topPos + slotY[slot];
                if (isInBounds(mouseX, mouseY, slotX, sy, ICON_SIZE, ICON_SIZE)) {
                    if (selectedMode == MODE_ABILITIES) {
                        AbilityInstance instance = data.getBarSlotInstance(selectedPage, slot);
                        if (instance != null) {
                            List<Component> tooltip = buildAbilityTooltip(instance.getAbilityType());
                            tooltip.add(Component.literal("Click to remove from bar")
                                    .withStyle(ChatFormatting.GRAY));
                            graphics.renderTooltip(font, tooltip, Optional.empty(),
                                    mouseX, mouseY);
                        }
                    } else if (selectedMode == MODE_TRANSFORMATIONS) {
                        AbilityInstance instance = data.getTransBarSlotInstance(slot);
                        if (instance != null) {
                            List<Component> tooltip = buildAbilityTooltip(instance.getAbilityType());
                            tooltip.add(Component.literal("Click to remove from bar")
                                    .withStyle(ChatFormatting.GRAY));
                            graphics.renderTooltip(font, tooltip, Optional.empty(),
                                    mouseX, mouseY);
                        }
                    } else if (selectedMode >= MODE_CATEGORY_START) {
                        AbilityCategory cat = getSelectedCategory();
                        if (cat != null) {
                            AbilityInstance instance = data.getCategoryBarSlotInstance(cat.getId(), slot);
                            if (instance != null) {
                                List<Component> tooltip = buildAbilityTooltip(instance.getAbilityType());
                                tooltip.add(Component.literal("Click to remove from bar")
                                        .withStyle(ChatFormatting.GRAY));
                                graphics.renderTooltip(font, tooltip, Optional.empty(),
                                        mouseX, mouseY);
                            }
                        }
                    }
                    return;
                }
            }
        }

        // Power tab tooltips
        if (!powers.isEmpty()) {
            int visibleCount = Math.min(powers.size() - tabScrollOffset, MAX_VISIBLE_TABS);
            for (int i = 0; i < visibleCount; i++) {
                int tabX = leftPos + LEFT_TAB_REL_X;
                int tabY = topPos + LEFT_TAB_START_REL_Y + i * LEFT_TAB_SPACING;
                if (isInBounds(mouseX, mouseY, tabX, tabY, LEFT_TAB_W, LEFT_TAB_H)) {
                    Power power = powers.get(i + tabScrollOffset);
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(power.getDisplayName());
                    graphics.renderTooltip(font, tooltip, Optional.empty(),
                            mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private List<Component> buildAbilityTooltip(AbilityType type) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(type.getDisplayName());

        Component desc = type.getDescription();
        if (desc != null) {
            tooltip.add(desc.copy().withStyle(ChatFormatting.GRAY));
        }

        // Locked
        if (isAbilityLocked(type)) {
            tooltip.add(Component.literal("Locked")
                    .withStyle(ChatFormatting.RED));
            Component progressText = getLockedProgressText(type);
            if (progressText != null) {
                tooltip.add(progressText.copy().withStyle(ChatFormatting.GRAY));
            }
            return tooltip;
        }

        // Passive mode: show enabled/disabled state
        if (selectedMode == MODE_PASSIVES) {
            boolean enabled = data.isPassiveEnabled(type);
            if (enabled) {
                tooltip.add(Component.literal("Enabled")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("Disabled")
                        .withStyle(ChatFormatting.RED));
            }
            tooltip.add(Component.literal("Click to toggle")
                    .withStyle(ChatFormatting.GRAY));
            return tooltip;
        }

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
        } else if (type.getCategory() != null) {
            AbilityCategory cat = type.getCategory();
            tooltip.add(cat.getDisplayName().copy()
                    .withStyle(cat.getTooltipColor()));
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
    //  Click handling
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (button == 1) {
            if (heldAbility != null) {
                heldAbility = null;
                heldFromBar = false;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (handlePowerTabClick(mx, my)) return true;
        if (handleTypeTabClick(mx, my)) return true;
        if (handleTabScrollClick(mx, my)) return true;
        if (hasPages() && handlePageArrowClick(mx, my)) return true;
        if (hasBarSlots() && handleBarSlotClick(mx, my)) return true;
        if (handleGridClick(mx, my)) return true;

        if (heldAbility != null) {
            heldAbility = null;
            heldFromBar = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleBarSlotClick(int mx, int my) {
        if (selectedMode == MODE_PASSIVES) return false;

        int slotCount = getBarSlotCount();
        int[] slotY = getBarSlotYPositions();

        for (int slot = 0; slot < slotCount; slot++) {
            int slotX = leftPos + BAR_REL_X;
            int sy = topPos + slotY[slot];
            if (!isInBounds(mx, my, slotX, sy, ICON_SIZE, ICON_SIZE)) continue;

            if (selectedMode == MODE_ABILITIES) {
                return handleAbilityBarSlotClick(slot);
            } else if (selectedMode == MODE_TRANSFORMATIONS) {
                return handleTransBarSlotClick(slot);
            } else if (selectedMode >= MODE_CATEGORY_START) {
                AbilityCategory cat = getSelectedCategory();
                if (cat != null) return handleCategoryBarSlotClick(slot, cat);
            }
        }
        return false;
    }

    private boolean handleAbilityBarSlotClick(int slot) {
        if (heldAbility != null) {
            ResourceLocation heldKey = SomniumRegistries.getAbilityKey(heldAbility);
            if (heldKey == null) return true;
            if (data.isAbilityOnPage(selectedPage, heldKey)) return true;
            if (!heldAbility.isBarEquippable()) return true;

            SomniumNetwork.sendToServer(
                    new UpdateBarSlotPacket(selectedPage, slot, heldKey));
            data.setBarSlot(selectedPage, slot, heldAbility);
            heldAbility = null;
            heldFromBar = false;
            return true;
        } else {
            AbilityInstance instance = data.getBarSlotInstance(selectedPage, slot);
            if (instance == null) return true;
            heldAbility = instance.getAbilityType();
            heldFromBar = true;
            SomniumNetwork.sendToServer(
                    new UpdateBarSlotPacket(selectedPage, slot, null));
            data.setBarSlot(selectedPage, slot, null);
            return true;
        }
    }

    private boolean handleTransBarSlotClick(int slot) {
        if (heldAbility != null) {
            ResourceLocation heldKey = SomniumRegistries.getAbilityKey(heldAbility);
            if (heldKey == null) return true;
            if (data.isAbilityOnTransBar(heldKey)) return true;
            if (!(heldAbility instanceof TransformationAbilityType)) return true;

            SomniumNetwork.sendToServer(
                    UpdateBarSlotPacket.forTransBar(slot, heldKey));
            data.setTransBarSlot(slot, heldAbility);
            heldAbility = null;
            heldFromBar = false;
            return true;
        } else {
            AbilityInstance instance = data.getTransBarSlotInstance(slot);
            if (instance == null) return true;
            heldAbility = instance.getAbilityType();
            heldFromBar = true;
            SomniumNetwork.sendToServer(
                    UpdateBarSlotPacket.forTransBar(slot, null));
            data.setTransBarSlot(slot, null);
            return true;
        }
    }

    private boolean handleCategoryBarSlotClick(int slot, AbilityCategory category) {
        ResourceLocation catKey = category.getId();
        if (heldAbility != null) {
            ResourceLocation heldKey = SomniumRegistries.getAbilityKey(heldAbility);
            if (heldKey == null) return true;
            if (data.isAbilityOnCategoryBar(catKey, heldKey)) return true;
            // Verify the held ability belongs to this category
            if (heldAbility.getCategory() != category) return true;

            SomniumNetwork.sendToServer(
                    UpdateBarSlotPacket.forCategoryBar(catKey, slot, heldKey));
            data.setCategoryBarSlot(catKey, slot, heldAbility);
            heldAbility = null;
            heldFromBar = false;
            return true;
        } else {
            AbilityInstance instance = data.getCategoryBarSlotInstance(catKey, slot);
            if (instance == null) return true;
            heldAbility = instance.getAbilityType();
            heldFromBar = true;
            SomniumNetwork.sendToServer(
                    UpdateBarSlotPacket.forCategoryBar(catKey, slot, null));
            data.setCategoryBarSlot(catKey, slot, null);
            return true;
        }
    }

    private boolean handleGridClick(int mx, int my) {
        int startIndex = gridScrollRow * GRID_COLS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = startIndex + row * GRID_COLS + col;
                if (index >= currentAbilities.size()) return false;
                int iconX = leftPos + GRID_REL_X + CELL_ICON_OFFSET + col * CELL_PITCH;
                int iconY = topPos + GRID_REL_Y + CELL_ICON_OFFSET + row * CELL_PITCH;
                if (!isInBounds(mx, my, iconX, iconY, CELL_CONTENT, CELL_CONTENT)) continue;

                AbilityType type = currentAbilities.get(index);

                // Passives mode: toggle on/off
                if (selectedMode == MODE_PASSIVES) {
                    if (isAbilityLocked(type)) return true;
                    ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(type);
                    if (abilityKey != null) {
                        SomniumNetwork.sendToServer(new TogglePassivePacket(abilityKey));
                    }
                    return true;
                }

                // Drag-and-drop modes (abilities/transformations)
                if (heldAbility != null) {
                    heldAbility = null;
                    heldFromBar = false;
                    return true;
                }

                if (isAbilityLocked(type)) return true;
                if (isAbilityGrayed(type)) return true;
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
                heldAbility = null;
                heldFromBar = false;
                return true;
            }
        }
        return false;
    }

    private boolean handleTypeTabClick(int mx, int my) {
        int totalTabs = getTotalTabCount();
        for (int i = 0; i < totalTabs; i++) {
            int tabX = leftPos + RIGHT_TAB_REL_X;
            int tabY = topPos + RIGHT_TAB_REL_Y[0] + i * (RIGHT_TAB_H + 2);
            if (isInBounds(mx, my, tabX, tabY, RIGHT_TAB_W, RIGHT_TAB_H)) {
                selectedMode = i;
                refreshAbilities();
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
                data.setActivePage(selectedPage);
                SomniumNetwork.sendToServer(
                        new net.eclipce.somnium.network.SetActivePagePacket(selectedPage));
                return true;
            }
        }
        if (selectedPage < SomniumPlayerData.MAX_PAGES - 1) {
            int fx = leftPos + PAGE_FWD_REL_X;
            int fy = topPos + PAGE_FWD_REL_Y;
            if (isInBounds(mx, my, fx, fy, PAGE_ARROW_W, PAGE_ARROW_H)) {
                selectedPage++;
                data.setActivePage(selectedPage);
                SomniumNetwork.sendToServer(
                        new net.eclipce.somnium.network.SetActivePagePacket(selectedPage));
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

    @Override
    public void onClose() {
        heldAbility = null;
        heldFromBar = false;
        super.onClose();
    }

    private static boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}