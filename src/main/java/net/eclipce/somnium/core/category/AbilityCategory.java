package net.eclipce.somnium.core.category;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Defines a custom ability category — a separate grouping with its own
 * inventory tab, HUD bar, and keybind support.
 *
 * <p>Custom categories work like the built-in Transformation system but are
 * fully addon-defined. Each category gets its own right-side tab in the
 * ability inventory, its own 6-slot bar, and support for crouch quick-bind.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * // 1. Define and register the category (during mod construction)
 * public static final AbilityCategory MAGIC = AbilityCategory.builder(
 *         new ResourceLocation("mymod", "magic"))
 *     .displayName(Component.literal("Magic"))
 *     .tabLabel("M")
 *     .tooltipColor(ChatFormatting.BLUE)
 *     .build();
 *
 * // Call in your mod constructor:
 * AbilityCategory.register(MAGIC);
 *
 * // 2. Assign abilities to the category
 * public class IceBlastAbility extends AbilityType {
 *     public IceBlastAbility() {
 *         super(new Properties()
 *             .activationType(ActivationType.INSTANT)
 *             .category(MAGIC)  // <-- assigns to the Magic category
 *             .cooldown(40)
 *         );
 *     }
 * }
 *
 * // 3. Create your own keybind and handle it:
 * // In your client tick handler:
 * if (MY_MAGIC_KEY.consumeClick()) {
 *     if (mc.player.isCrouching()) {
 *         SomniumNetwork.sendToServer(new QuickCategoryPacket(MAGIC.getId()));
 *     } else {
 *         AbilityBarOverlay.toggleCategoryBar(MAGIC.getId());
 *     }
 * }
 * }</pre>
 *
 * @see net.eclipce.somnium.core.ability.AbilityType.Properties#category(AbilityCategory)
 */
public class AbilityCategory {

    // ═══════════════════════════════════════════════════════════════════
    //  Static registry
    // ═══════════════════════════════════════════════════════════════════

    private static final Map<ResourceLocation, AbilityCategory> REGISTRY =
            new LinkedHashMap<>();

    /**
     * Registers a custom ability category. Call during mod construction
     * (before registries freeze).
     *
     * @param category the category to register
     * @throws IllegalStateException if a category with the same ID is
     *         already registered
     */
    public static void register(AbilityCategory category) {
        if (REGISTRY.containsKey(category.id)) {
            throw new IllegalStateException(
                    "AbilityCategory already registered: " + category.id);
        }
        REGISTRY.put(category.id, category);
    }

    /**
     * Gets a registered category by ID.
     *
     * @param id the category's ResourceLocation
     * @return the category, or null if not registered
     */
    @Nullable
    public static AbilityCategory get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    /**
     * @return all registered categories in registration order
     */
    public static Collection<AbilityCategory> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * @return all registered category IDs
     */
    public static Set<ResourceLocation> getKeys() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * @return the number of registered categories
     */
    public static int count() {
        return REGISTRY.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Instance fields
    // ═══════════════════════════════════════════════════════════════════

    private final ResourceLocation id;
    private final Component displayName;
    private final String tabLabel;
    private final ChatFormatting tooltipColor;

    private AbilityCategory(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.tabLabel = builder.tabLabel;
        this.tooltipColor = builder.tooltipColor;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /** @return the unique identifier for this category */
    public ResourceLocation getId() { return id; }

    /** @return the display name (shown in tooltips) */
    public Component getDisplayName() { return displayName; }

    /** @return 1-2 character label shown on the inventory tab */
    public String getTabLabel() { return tabLabel; }

    /** @return the color for the category name in ability tooltips */
    public ChatFormatting getTooltipColor() { return tooltipColor; }

    // ═══════════════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new builder for an AbilityCategory.
     *
     * @param id unique identifier (e.g., {@code new ResourceLocation("mymod", "magic")})
     */
    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private Component displayName;
        private String tabLabel;
        private ChatFormatting tooltipColor = ChatFormatting.WHITE;

        private Builder(ResourceLocation id) {
            this.id = id;
            // Default display name and label from the path
            String path = id.getPath();
            this.displayName = Component.literal(
                    Character.toUpperCase(path.charAt(0)) + path.substring(1));
            this.tabLabel = path.substring(0, 1).toUpperCase();
        }

        /** Sets the display name shown in tooltips and the inventory. */
        public Builder displayName(Component name) {
            this.displayName = name;
            return this;
        }

        /** Sets the 1-2 character label on the right-side inventory tab. */
        public Builder tabLabel(String label) {
            this.tabLabel = label;
            return this;
        }

        /** Sets the color for the category name in ability tooltips. */
        public Builder tooltipColor(ChatFormatting color) {
            this.tooltipColor = color;
            return this;
        }

        public AbilityCategory build() {
            return new AbilityCategory(this);
        }
    }
}