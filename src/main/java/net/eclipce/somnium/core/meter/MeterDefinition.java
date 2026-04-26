package net.eclipce.somnium.core.meter;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Defines a custom meter — a visual resource bar that can be linked to
 * abilities, powers, transformations, or tags.
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public static final MeterDefinition MANA = MeterDefinition.builder(
 *         new ResourceLocation("mymod", "mana"))
 *     .displayName(Component.literal("Mana"))
 *     .maxValue(100)
 *     .regenRate(0.5f)       // +0.5 per tick
 *     .regenDelay(40)        // 2 second delay after drain
 *     .color(0x3366FF)       // blue fill
 *     .visibleWhenPowerActive(new ResourceLocation("mymod", "arcane"))
 *     .onDepleted(player -> player.hurt(...))  // custom depletion effect
 *     .build();
 *
 * // Register in mod constructor:
 * MeterDefinition.register(MANA);
 *
 * // Link to abilities via Properties:
 * new Properties()
 *     .meterCost(MANA.getId(), 25f)       // drains 25 mana on use
 *     .meterRequired(MANA.getId(), 10f)   // requires 10 mana to activate
 * }</pre>
 */
public class MeterDefinition {

    // ═══════════════════════════════════════════════════════════════════
    //  Static registry
    // ═══════════════════════════════════════════════════════════════════

    private static final Map<ResourceLocation, MeterDefinition> REGISTRY =
            new LinkedHashMap<>();

    public static void register(MeterDefinition meter) {
        if (REGISTRY.containsKey(meter.id)) {
            throw new IllegalStateException(
                    "MeterDefinition already registered: " + meter.id);
        }
        REGISTRY.put(meter.id, meter);
    }

    @Nullable
    public static MeterDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<MeterDefinition> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static int count() {
        return REGISTRY.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Visibility modes
    // ═══════════════════════════════════════════════════════════════════

    public enum VisibilityMode {
        /** Always visible when the player has meter data. */
        ALWAYS,
        /** Visible only when a specific power is active. */
        POWER_ACTIVE,
        /** Visible only when any transformation is active. */
        TRANSFORMATION_ACTIVE,
        /** Visible only when the player has a specific tag. */
        TAG_PRESENT
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Instance fields
    // ═══════════════════════════════════════════════════════════════════

    private final ResourceLocation id;
    private final Component displayName;
    private final float defaultMaxValue;
    private final boolean regenEnabled;
    private final float regenRate;
    private final int regenDelay;
    private final int color;
    private final VisibilityMode visibilityMode;
    @Nullable
    private final ResourceLocation visibilityKey;
    @Nullable
    private final ResourceLocation frameTexture;
    @Nullable
    private final ResourceLocation fillTexture;
    @Nullable
    private final int[] screenPosition; // {x, y} or null for auto
    @Nullable
    private final Consumer<net.minecraft.server.level.ServerPlayer> onDepleted;

    private MeterDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.defaultMaxValue = builder.defaultMaxValue;
        this.regenEnabled = builder.regenEnabled;
        this.regenRate = builder.regenRate;
        this.regenDelay = builder.regenDelay;
        this.color = builder.color;
        this.visibilityMode = builder.visibilityMode;
        this.visibilityKey = builder.visibilityKey;
        this.frameTexture = builder.frameTexture;
        this.fillTexture = builder.fillTexture;
        this.screenPosition = builder.screenPosition;
        this.onDepleted = builder.onDepleted;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    public ResourceLocation getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public float getDefaultMaxValue() { return defaultMaxValue; }
    public boolean isRegenEnabled() { return regenEnabled; }
    public float getRegenRate() { return regenRate; }
    public int getRegenDelay() { return regenDelay; }
    public int getColor() { return color; }
    public VisibilityMode getVisibilityMode() { return visibilityMode; }
    @Nullable public ResourceLocation getVisibilityKey() { return visibilityKey; }
    @Nullable public ResourceLocation getFrameTexture() { return frameTexture; }
    @Nullable public ResourceLocation getFillTexture() { return fillTexture; }
    @Nullable public int[] getScreenPosition() { return screenPosition; }
    @Nullable public Consumer<net.minecraft.server.level.ServerPlayer> getOnDepleted() { return onDepleted; }

    // ═══════════════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════════════

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private Component displayName;
        private float defaultMaxValue = 100f;
        private boolean regenEnabled = true;
        private float regenRate = 0.25f;
        private int regenDelay = 40;
        private int color = 0x55FF55; // green
        private VisibilityMode visibilityMode = VisibilityMode.ALWAYS;
        private ResourceLocation visibilityKey = null;
        private ResourceLocation frameTexture = null;
        private ResourceLocation fillTexture = null;
        private int[] screenPosition = null;
        private Consumer<net.minecraft.server.level.ServerPlayer> onDepleted = null;

        private Builder(ResourceLocation id) {
            this.id = id;
            String path = id.getPath();
            this.displayName = Component.literal(
                    Character.toUpperCase(path.charAt(0)) + path.substring(1));
        }

        public Builder displayName(Component name) { this.displayName = name; return this; }

        /** Sets the default maximum value for this meter. Can be changed at runtime per-player. */
        public Builder maxValue(float max) { this.defaultMaxValue = max; return this; }

        /** Enables or disables automatic regeneration. Default: true. */
        public Builder regenEnabled(boolean enabled) { this.regenEnabled = enabled; return this; }

        /** Sets the regen amount per tick. Default: 0.25 per tick (5/second). */
        public Builder regenRate(float rate) { this.regenRate = rate; return this; }

        /** Sets the delay in ticks before regen starts after drain. Default: 40 (2 seconds). */
        public Builder regenDelay(int ticks) { this.regenDelay = ticks; return this; }

        /** Sets the fill color as an RGB hex code (e.g., 0xFF0000 for red). */
        public Builder color(int rgb) { this.color = rgb; return this; }

        /** Meter is always visible when player has data. */
        public Builder alwaysVisible() {
            this.visibilityMode = VisibilityMode.ALWAYS;
            this.visibilityKey = null;
            return this;
        }

        /** Meter is visible only when the player has a specific power. */
        public Builder visibleWhenPowerActive(ResourceLocation powerKey) {
            this.visibilityMode = VisibilityMode.POWER_ACTIVE;
            this.visibilityKey = powerKey;
            return this;
        }

        /** Meter is visible only when any transformation is active. */
        public Builder visibleWhenTransformed() {
            this.visibilityMode = VisibilityMode.TRANSFORMATION_ACTIVE;
            this.visibilityKey = null;
            return this;
        }

        /** Meter is visible only when the player has a specific tag. */
        public Builder visibleWhenTagPresent(ResourceLocation tag) {
            this.visibilityMode = VisibilityMode.TAG_PRESENT;
            this.visibilityKey = tag;
            return this;
        }

        /** Custom frame texture. Defaults to somnium meter_bar.png. */
        public Builder frameTexture(ResourceLocation tex) { this.frameTexture = tex; return this; }

        /** Custom fill texture. Defaults to somnium meter_bar_energy.png. */
        public Builder fillTexture(ResourceLocation tex) { this.fillTexture = tex; return this; }

        /** Fixed screen position {x, y}. Null = auto-layout next to bar. */
        public Builder screenPosition(int x, int y) { this.screenPosition = new int[]{x, y}; return this; }

        /**
         * Callback fired on the server when this meter hits zero.
         * Use for custom depletion effects (debuffs, damage, etc.).
         */
        public Builder onDepleted(Consumer<net.minecraft.server.level.ServerPlayer> callback) {
            this.onDepleted = callback;
            return this;
        }

        public MeterDefinition build() {
            return new MeterDefinition(this);
        }
    }
}