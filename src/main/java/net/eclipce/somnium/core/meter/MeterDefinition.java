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
    //  Screen corners
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A screen corner, used by {@link Builder#lockToCorner} to pin a meter in
     * place independent of the ability bar's configured position.
     *
     * <p>Deliberately a separate, common-package enum rather than reusing
     * {@code net.eclipce.somnium.client.config.BarPosition} — that class
     * lives in the {@code client} package and is client-only, while
     * {@code MeterDefinition} is common code loaded on both sides (server-side
     * code reads {@code maxValue}/{@code regenRate} etc. off it, e.g. via
     * {@code HakiUpkeepHandler}). A common class referencing a client-only
     * type in a field/method signature is a standing Forge foot-gun — even if
     * the field is never touched server-side, it risks class-loading errors
     * on a dedicated server. {@code MeterOverlay} (client-only) is free to
     * translate between this and {@code BarPosition} as needed.</p>
     */
    public enum ScreenCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean isRight() { return this == TOP_RIGHT || this == BOTTOM_RIGHT; }
        public boolean isTop()   { return this == TOP_LEFT  || this == TOP_RIGHT; }
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
    /**
     * When {@code true}, this meter renders flush at the screen corner
     * directly opposite the ability bar's currently-configured
     * {@code BarPosition} — same vertical half (top/bottom), opposite
     * horizontal side — using the same edge margin the ability bar itself
     * uses. Because it's derived from the live config rather than fixed at
     * registration, it tracks the player's ability bar placement: if they
     * move the bar, this meter moves to the new opposite side automatically.
     * Mutually exclusive with {@link #screenPosition} and {@link #lockedCorner}.
     */
    private final boolean mirrorOppositeSide;
    /**
     * When set, pins this meter flush at a specific screen corner,
     * independent of the ability bar's configured position — unlike
     * {@link #mirrorOppositeSide}, it never moves even if the player changes
     * {@code BarPosition} in config. Mutually exclusive with
     * {@link #screenPosition} and {@link #mirrorOppositeSide}.
     */
    @Nullable
    private final ScreenCorner lockedCorner;
    /**
     * Custom texture pixel dimensions, or {@code null} to use the renderer's
     * built-in default (26x129, matching Somnium's stock bar textures). Only
     * meaningful if {@link #frameTexture} and/or {@link #fillTexture} point at
     * art that isn't the stock size — a custom fill/frame pair authored at,
     * say, 32x160 must set this or it will be sampled as if it were 26x129.
     */
    @Nullable
    private final Integer textureWidth;
    @Nullable
    private final Integer textureHeight;
    /**
     * Pixel nudge applied to this meter's rendered position, on top of
     * whichever positioning mode is active (auto-stacked next to the ability
     * bar, or a fixed {@link #screenPosition}). Positive X moves right,
     * positive Y moves down. Defaults to (0, 0) — no nudge.
     */
    private final int offsetX;
    private final int offsetY;
    /**
     * Uniform scale factor applied to this meter's rendered size, anchored at
     * its own top-left corner. Defaults to {@code 1.0f} (100%, no scaling).
     * Applied via a PoseStack transform at render time, so it composes
     * correctly with the player's GUI Scale option automatically — Somnium
     * never reads the raw GUI scale itself for this, since {@code IGuiOverlay}
     * already renders inside that matrix.
     */
    private final float scale;

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
        this.mirrorOppositeSide = builder.mirrorOppositeSide;
        this.lockedCorner = builder.lockedCorner;
        this.textureWidth = builder.textureWidth;
        this.textureHeight = builder.textureHeight;
        this.offsetX = builder.offsetX;
        this.offsetY = builder.offsetY;
        this.scale = builder.scale;
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
    public boolean isMirrorOppositeSide() { return mirrorOppositeSide; }
    @Nullable public ScreenCorner getLockedCorner() { return lockedCorner; }
    @Nullable public Integer getTextureWidth() { return textureWidth; }
    @Nullable public Integer getTextureHeight() { return textureHeight; }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }
    public float getScale() { return scale; }

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
        private Integer textureWidth = null;
        private Integer textureHeight = null;
        private int offsetX = 0;
        private int offsetY = 0;
        private float scale = 1.0f;
        private boolean mirrorOppositeSide = false;
        private ScreenCorner lockedCorner = null;

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

        /**
         * Fixed screen position {@code {x, y}}, in raw pixels from the
         * top-left of the screen. Null (default) = auto-layout mirrored next
         * to the ability bar. Mutually exclusive with
         * {@link #mirrorOppositeScreenSide} and {@link #lockToCorner} —
         * whichever positioning method is called last wins.
         */
        public Builder screenPosition(int x, int y) {
            this.screenPosition = new int[]{x, y};
            this.mirrorOppositeSide = false;
            this.lockedCorner = null;
            return this;
        }

        /**
         * Renders this meter flush at the screen corner directly opposite the
         * ability bar's currently-configured {@code BarPosition} — same
         * vertical half (top/bottom), opposite horizontal side — using the
         * same edge margin the ability bar itself uses. Unlike
         * {@link #lockToCorner}, this tracks the live config: if the player
         * moves their ability bar, this meter follows to the new opposite
         * side. Mutually exclusive with {@link #screenPosition} and
         * {@link #lockToCorner} — whichever positioning method is called last
         * wins.
         */
        public Builder mirrorOppositeScreenSide() {
            this.mirrorOppositeSide = true;
            this.screenPosition = null;
            this.lockedCorner = null;
            return this;
        }

        /**
         * Pins this meter flush at a specific screen corner, independent of
         * the ability bar's configured position — unlike
         * {@link #mirrorOppositeScreenSide}, it never moves even if the
         * player changes {@code BarPosition} in config. Mutually exclusive
         * with {@link #screenPosition} and {@link #mirrorOppositeScreenSide}
         * — whichever positioning method is called last wins.
         */
        public Builder lockToCorner(ScreenCorner corner) {
            this.lockedCorner = corner;
            this.screenPosition = null;
            this.mirrorOppositeSide = false;
            return this;
        }

        /**
         * Sets custom pixel dimensions for this meter's textures, for use
         * with {@link #frameTexture} / {@link #fillTexture} art that isn't
         * the stock 26x129 size. Leave unset to use the renderer's default.
         */
        public Builder textureSize(int width, int height) {
            this.textureWidth = width;
            this.textureHeight = height;
            return this;
        }

        /**
         * Nudges this meter's rendered position by {@code (x, y)} pixels,
         * applied on top of whichever positioning mode is active — the
         * automatic stacked layout next to the ability bar, or a fixed
         * {@link #screenPosition}. Default (0, 0).
         */
        public Builder textureOffset(int x, int y) {
            this.offsetX = x;
            this.offsetY = y;
            return this;
        }

        /**
         * Sets a uniform scale factor for this meter's rendered size (e.g.
         * {@code 1.5f} for 150%), anchored at its own top-left corner.
         * Default {@code 1.0f}. Applied via a PoseStack transform at render
         * time, so it respects the player's GUI Scale setting automatically.
         */
        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

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