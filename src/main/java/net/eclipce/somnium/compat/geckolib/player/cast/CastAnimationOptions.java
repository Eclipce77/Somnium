package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-animation behaviour flags for a Somnium cast animation.
 *
 * <p>Each option tunes a specific overlap between the cast animation and the rest of
 * the player render pipeline. All options default to {@code off / empty / true} —
 * passing {@link #DEFAULT} produces a vanilla-style overlay where the animation
 * blends additively onto whatever vanilla locomotion is doing.</p>
 *
 * <h3>Migration notes (this revision)</h3>
 * <p>{@code followPlayerBody} (a body-yaw snap keyed off a part set) is now
 * {@link Builder#onExecuteBodyAlign} (a plain boolean), and {@code followPlayerLook}
 * (a live per-frame 1:1 look pitch) is now {@link Builder#changeDirectionOnLook} (a mild,
 * clamped tilt locked to the look pitch at execution). All part-name parameters are strict
 * {@link CastBodyPart} / {@link CastLayer} enums.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CastAnimationOptions.builder()
 *     .suppressVanillaAnimOn(CastBodyPart.RIGHT_ARM, CastBodyPart.LEFT_ARM)
 *     .onExecuteBodyAlign(true)
 *     .changeDirectionOnLook(CastBodyPart.RIGHT_ARM)
 *     .hideLayer(CastLayer.RIGHT_SLEEVE, CastLayer.LEFT_SLEEVE)
 *     .showInFirstPerson(true)
 *     .build();
 * }</pre>
 */
public final class CastAnimationOptions {

    /** All options at their defaults: no suppress, no follow, no hide, third-person only, items visible. */
    public static final CastAnimationOptions DEFAULT = new Builder().build();

    private final Set<CastBodyPart> suppressVanillaAnimOn;
    private final boolean           onExecuteBodyAlign;
    private final Set<CastBodyPart> changeDirectionOnLook;
    private final Set<CastBodyPart> hideBodyPart;
    private final Set<CastLayer>    hideLayer;
    private final boolean showInFirstPerson;
    private final boolean heldItemsShown;
    private final int     animationLengthTicks;
    /**
     * Look pitch (degrees) captured server-side at execution and frozen for the life of
     * this animation. Used by {@code changeDirectionOnLook}: the tilt is locked to where the
     * player was looking when the ability fired, not re-read each frame. {@code 0} when
     * changeDirectionOnLook is unused. Stamped via {@link #withCapturedLookPitch(float)}.
     */
    private final float capturedLookPitch;

    private CastAnimationOptions(Set<CastBodyPart> suppressVanillaAnimOn,
                                 boolean           onExecuteBodyAlign,
                                 Set<CastBodyPart> changeDirectionOnLook,
                                 Set<CastBodyPart> hideBodyPart,
                                 Set<CastLayer>    hideLayer,
                                 boolean showInFirstPerson,
                                 boolean heldItemsShown,
                                 int     animationLengthTicks,
                                 float   capturedLookPitch) {
        this.suppressVanillaAnimOn  = suppressVanillaAnimOn;
        this.onExecuteBodyAlign     = onExecuteBodyAlign;
        this.changeDirectionOnLook  = changeDirectionOnLook;
        this.hideBodyPart           = hideBodyPart;
        this.hideLayer              = hideLayer;
        this.showInFirstPerson      = showInFirstPerson;
        this.heldItemsShown         = heldItemsShown;
        this.animationLengthTicks  = animationLengthTicks;
        this.capturedLookPitch      = capturedLookPitch;
    }

    /**
     * Returns a copy of these options with the look pitch (degrees) frozen in. Called
     * server-side from {@code SomniumAnimHelper.triggerCastAnimation} at the moment the
     * ability fires, so {@code changeDirectionOnLook} locks to the player's look at
     * execution time rather than tracking it live each frame. All other fields are carried
     * over unchanged (the immutable sets are reused, not copied — they're already
     * unmodifiable).
     */
    public CastAnimationOptions withCapturedLookPitch(float pitchDegrees) {
        return new CastAnimationOptions(
                suppressVanillaAnimOn,
                onExecuteBodyAlign,
                changeDirectionOnLook,
                hideBodyPart,
                hideLayer,
                showInFirstPerson,
                heldItemsShown,
                animationLengthTicks,
                pitchDegrees);
    }

    // ─── Accessors ─────────────────────────────────────────────────────────────

    /**
     * The base parts whose vanilla setupAnim pose should be discarded before the cast
     * animation applies its deltas. Use this for any limb the animation actively
     * controls so vanilla walking/swinging doesn't leak through. See
     * {@link Builder#suppressVanillaAnimOn} for usage.
     */
    public Set<CastBodyPart> suppressVanillaAnimOn() { return suppressVanillaAnimOn; }

    /**
     * If {@code true}, the server snaps the player's body yaw to their look direction at the
     * instant the ability executes, so the whole rig faces exactly where they're aiming.
     * See {@link Builder#onExecuteBodyAlign}.
     */
    public boolean onExecuteBodyAlign() { return onExecuteBodyAlign; }

    /**
     * Base parts that get a mild, clamped up/down (X-axis) tilt toward where the player was
     * looking when the ability fired, so the limb aims at the intended target without the
     * over-rotation of a 1:1 look mapping. The pitch is locked at execution (see
     * {@link #capturedLookPitch()}). See {@link Builder#changeDirectionOnLook}.
     */
    public Set<CastBodyPart> changeDirectionOnLook() { return changeDirectionOnLook; }

    /** The execution-time look pitch (degrees) used by {@link #changeDirectionOnLook()}. */
    public float capturedLookPitch() { return capturedLookPitch; }

    /** Base parts to hide for the duration of the animation. */
    public Set<CastBodyPart> hideBodyPart() { return hideBodyPart; }

    /** Skin overlay layers to hide for the duration of the animation. */
    public Set<CastLayer> hideLayer() { return hideLayer; }

    /**
     * If {@code true}, the local player is re-rendered from inside the first-person
     * camera so the animation is visible to its owner. Only arms, sleeves, and held
     * items are drawn in that pass — head/body/legs/their overlays are hidden so the
     * player doesn't see the inside of their own model.
     */
    public boolean showInFirstPerson() { return showInFirstPerson; }

    /**
     * If {@code false}, the held item is hidden in both third- and first-person for
     * the duration of the animation. Defaults to {@code true} — useful for things
     * like {@code Gomu Pistol} where the arm transforms into a weapon and the player's
     * sword would clip through it.
     */
    public boolean heldItemsShown() { return heldItemsShown; }

    /**
     * Authored length of this animation in ticks, used by the applicator for elapsed-time
     * completion detection. {@code <= 0} means "do not auto-finish" — i.e. a looping clip
     * that is ended explicitly by triggering a different animation (the gatling loop uses
     * this). Defaults to {@code 0}.
     *
     * <p>This exists because the cast system drives the GeckoLib processor manually, and in
     * that path the controller's own PLAY_ONCE stop-transition never fires, so the animation
     * would otherwise hold its final frame forever. The caller knows the authored duration
     * (e.g. EXTEND_TICKS), so it passes it here.</p>
     */
    public int animationLengthTicks() { return animationLengthTicks; }

    public static Builder builder() { return new Builder(); }

    // ─── Network codec ─────────────────────────────────────────────────────────
    //
    // Serialised to / from the play packet that pushes a cast trigger out to every
    // tracking client. The wire format is six EnumSets followed by two booleans —
    // all enum values are written as ordinal varInts so the byte count stays small
    // even when several parts are listed.
    //
    // Forward-compatibility tradeoff: ordinals shift if enum entries are reordered
    // or removed. Adding new entries to the end of CastBodyPart / CastLayer is
    // safe; reordering is not. If we ever need a stable wire format across
    // version mismatches, switch to writeUtf(enum.name()) on both sides — slower
    // (string per entry) but immune to ordinal drift.

    /**
     * Reads a {@code CastAnimationOptions} from the packet buffer. Pairs with
     * {@link #write}. Constructed via the private constructor with each enum set
     * wrapped immutable, matching how {@link Builder#build} produces instances.
     */
    public static CastAnimationOptions read(FriendlyByteBuf buf) {
        return new CastAnimationOptions(
                readEnumSet(buf, CastBodyPart.class),
                buf.readBoolean(),
                readEnumSet(buf, CastBodyPart.class),
                readEnumSet(buf, CastBodyPart.class),
                readEnumSet(buf, CastLayer.class),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readFloat());
    }

    /**
     * Writes this {@code CastAnimationOptions} into the packet buffer. The order of
     * fields matches {@link #read} — change one and you must change the other in
     * lockstep, or every client will deserialise garbage.
     */
    public void write(FriendlyByteBuf buf) {
        writeEnumSet(buf, suppressVanillaAnimOn);
        buf.writeBoolean(onExecuteBodyAlign);
        writeEnumSet(buf, changeDirectionOnLook);
        writeEnumSet(buf, hideBodyPart);
        writeEnumSet(buf, hideLayer);
        buf.writeBoolean(showInFirstPerson);
        buf.writeBoolean(heldItemsShown);
        buf.writeVarInt(animationLengthTicks);
        buf.writeFloat(capturedLookPitch);
    }

    /**
     * Reads a varInt-prefixed list of enum ordinals and rebuilds an immutable view
     * over an {@link EnumSet}. Empty sets are normalised to {@link Collections#emptySet()}
     * to match {@link Builder#build}'s output, so equality comparisons against
     * {@link #DEFAULT} stay reflexive across the wire round trip.
     */
    private static <E extends Enum<E>> Set<E> readEnumSet(FriendlyByteBuf buf, Class<E> enumClass) {
        int size = buf.readVarInt();
        if (size == 0) return Collections.emptySet();

        EnumSet<E> set = EnumSet.noneOf(enumClass);
        E[] values = enumClass.getEnumConstants();
        for (int i = 0; i < size; i++) {
            int ordinal = buf.readVarInt();
            // Defensive: skip out-of-range ordinals rather than crash the packet
            // handler. This can happen if the sender is on a newer version with
            // entries we don't recognise.
            if (ordinal >= 0 && ordinal < values.length) {
                set.add(values[ordinal]);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /** Writes an enum set as: varInt size, then {@code size} varInt ordinals. */
    private static <E extends Enum<E>> void writeEnumSet(FriendlyByteBuf buf, Set<E> set) {
        buf.writeVarInt(set.size());
        for (E e : set) {
            buf.writeVarInt(e.ordinal());
        }
    }

    // ─── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private final EnumSet<CastBodyPart> suppressVanillaAnimOn = EnumSet.noneOf(CastBodyPart.class);
        private boolean                     onExecuteBodyAlign    = false;
        private final EnumSet<CastBodyPart> changeDirectionOnLook = EnumSet.noneOf(CastBodyPart.class);
        private final EnumSet<CastBodyPart> hideBodyPart          = EnumSet.noneOf(CastBodyPart.class);
        private final EnumSet<CastLayer>    hideLayer             = EnumSet.noneOf(CastLayer.class);
        private boolean showInFirstPerson = false;
        private boolean heldItemsShown    = true;
        private int     animationLengthTicks = 0;

        private Builder() {}

        /**
         * Discards the vanilla setupAnim pose on the given parts before this frame's
         * cast deltas are applied. Accumulates across calls.
         *
         * <p>Use this for any limb the animation actively positions — a chest-mounted
         * gun pointing forward, a sword raised overhead, a kick extending out — so the
         * vanilla walk/swing/attack motion doesn't leak through underneath the
         * animation's pose. Parts not listed here still play their vanilla motion
         * additively under whatever the animation does.</p>
         */
        public Builder suppressVanillaAnimOn(CastBodyPart... parts) {
            Collections.addAll(suppressVanillaAnimOn, parts);
            return this;
        }

        /**
         * When {@code true}, the player's body yaw is instantly snapped to their current
         * look direction at the moment the ability executes, so the whole rig faces exactly
         * where they're aiming and the ability goes precisely where intended. The change is
         * server-side and persists (other players see it); if the body and look are already
         * aligned it does nothing.
         *
         * <p>This is a horizontal (yaw) alignment. For vertical aim — tilting a limb up/down
         * toward the target — combine with {@link #changeDirectionOnLook}.</p>
         */
        public Builder onExecuteBodyAlign(boolean value) {
            this.onExecuteBodyAlign = value;
            return this;
        }

        /**
         * Gives the listed base parts a mild, clamped up/down tilt (X-axis rotation about
         * their pivot) toward where the player was looking when the ability fired, so an
         * aimable limb points at a target above or below the player.
         *
         * <h3>Why mild + clamped</h3>
         * <p>Applying the full look pitch 1:1 over-rotates the limb at steep angles (a 60°
         * downward look would dump 60° onto the arm, swinging it far past anything natural).
         * Instead the captured pitch is clamped to a maximum tilt, so the limb reads as
         * aiming up or down without breaking the animation's pose.</p>
         *
         * <h3>Locked at execution</h3>
         * <p>The pitch is captured server-side once, when the ability fires, and frozen for
         * the animation — it does not re-aim if the player moves the camera mid-cast. This
         * keeps the motion deterministic and identical on every client.</p>
         *
         * <h3>Scope</h3>
         * <p>{@link CastBodyPart#RIGHT_ARM} / {@link CastBodyPart#LEFT_ARM} are the intended
         * targets. {@link CastBodyPart#HEAD} and {@link CastBodyPart#BODY} are no-ops.</p>
         */
        public Builder changeDirectionOnLook(CastBodyPart... parts) {
            Collections.addAll(changeDirectionOnLook, parts);
            return this;
        }

        /**
         * Hides the given base parts for the duration of the animation. Accumulates
         * across calls. Restored to whatever {@code setModelProperties} sets (typically
         * visible) the frame after the animation ends.
         */
        public Builder hideBodyPart(CastBodyPart... parts) {
            Collections.addAll(hideBodyPart, parts);
            return this;
        }

        /**
         * Hides the given skin overlay layers for the duration of the animation.
         * Accumulates across calls.
         */
        public Builder hideLayer(CastLayer... layers) {
            Collections.addAll(hideLayer, layers);
            return this;
        }

        /**
         * When {@code true}, the local player is re-rendered from inside the FP camera
         * for this animation, with everything except arms/sleeves and held items
         * hidden. The player sees the animation as if a third-person observer were
         * looking at them, but cropped to what's actually attached to the camera.
         * Reverts to vanilla FP arm rendering the frame after the animation ends.
         */
        public Builder showInFirstPerson(boolean value) {
            this.showInFirstPerson = value;
            return this;
        }

        /**
         * When {@code false}, the held item is hidden in both third- and first-person
         * for the duration of the animation. Defaults to {@code true}.
         */
        public Builder heldItemsShown(boolean value) {
            this.heldItemsShown = value;
            return this;
        }

        /**
         * Sets the authored length of this animation in ticks. The applicator finishes the
         * animation (restoring suppress / hide / first-person state) once this many ticks
         * have elapsed since it started. Pass the same tick count you authored the clip at
         * (e.g. a 0.45s clip is 9 ticks).
         *
         * <p>Pass {@code 0} (the default) for a looping clip that should NOT auto-finish —
         * the caller ends it by triggering a different animation. The gatling loop uses
         * {@code 0}; its charge and retract pass their real lengths.</p>
         */
        public Builder animationLengthTicks(int ticks) {
            this.animationLengthTicks = ticks;
            return this;
        }

        public CastAnimationOptions build() {
            // EnumSet copies preserve enum-iteration order and use a compact bitset
            // internally — cheap to construct, cheap to iterate.
            return new CastAnimationOptions(
                    suppressVanillaAnimOn.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(suppressVanillaAnimOn)),
                    onExecuteBodyAlign,
                    changeDirectionOnLook.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(changeDirectionOnLook)),
                    hideBodyPart.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(hideBodyPart)),
                    hideLayer.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(hideLayer)),
                    showInFirstPerson,
                    heldItemsShown,
                    animationLengthTicks,
                    0f); // capturedLookPitch is stamped later, server-side, at execution
        }
    }
}