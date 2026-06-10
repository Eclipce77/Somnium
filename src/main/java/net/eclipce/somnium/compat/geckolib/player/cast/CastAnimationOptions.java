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
 * <p>All part-name parameters are now strict {@link CastBodyPart} / {@link CastLayer}
 * enums instead of free-form strings. The IDE's autocomplete handles validation —
 * typos are syntactically impossible. {@code followPlayerLook} was replaced with
 * {@link Builder#followPlayerBody} which takes specific parts; see that method for
 * the rationale.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CastAnimationOptions.builder()
 *     .suppressVanillaAnimOn(CastBodyPart.RIGHT_ARM, CastBodyPart.LEFT_ARM)
 *     .followPlayerBody(CastBodyPart.RIGHT_ARM)
 *     .hideLayer(CastLayer.RIGHT_SLEEVE, CastLayer.LEFT_SLEEVE)
 *     .showInFirstPerson(true)
 *     .build();
 * }</pre>
 */
public final class CastAnimationOptions {

    /** All options at their defaults: no suppress, no follow, no hide, third-person only, items visible. */
    public static final CastAnimationOptions DEFAULT = new Builder().build();

    private final Set<CastBodyPart> suppressVanillaAnimOn;
    private final Set<CastBodyPart> followPlayerBody;
    private final Set<CastBodyPart> followPlayerLook;
    private final Set<CastBodyPart> hideBodyPart;
    private final Set<CastLayer>    hideLayer;
    private final boolean showInFirstPerson;
    private final boolean heldItemsShown;
    private final int     animationLengthTicks;

    private CastAnimationOptions(Set<CastBodyPart> suppressVanillaAnimOn,
                                 Set<CastBodyPart> followPlayerBody,
                                 Set<CastBodyPart> followPlayerLook,
                                 Set<CastBodyPart> hideBodyPart,
                                 Set<CastLayer>    hideLayer,
                                 boolean showInFirstPerson,
                                 boolean heldItemsShown,
                                 int     animationLengthTicks) {
        this.suppressVanillaAnimOn = suppressVanillaAnimOn;
        this.followPlayerBody      = followPlayerBody;
        this.followPlayerLook      = followPlayerLook;
        this.hideBodyPart          = hideBodyPart;
        this.hideLayer             = hideLayer;
        this.showInFirstPerson     = showInFirstPerson;
        this.heldItemsShown        = heldItemsShown;
        this.animationLengthTicks  = animationLengthTicks;
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
     * The presence of any part here flags the ability as body-aiming: on activation the
     * server snaps the player's body yaw to their look direction so the whole rig faces
     * where they're aiming. (The specific parts are not individually meaningful for the
     * yaw snap — a non-empty set is the trigger. The set type is kept as CastBodyPart for
     * API symmetry and forward use.) See {@link Builder#followPlayerBody}.
     */
    public Set<CastBodyPart> followPlayerBody() { return followPlayerBody; }

    /**
     * Base parts that should pitch (X-axis) to match the player's vertical look angle, so
     * the limb aims up/down with the camera. Applied at render time around each part's
     * own pivot (the shoulder, for arms). See {@link Builder#followPlayerLook}.
     */
    public Set<CastBodyPart> followPlayerLook() { return followPlayerLook; }

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
                readEnumSet(buf, CastBodyPart.class),
                readEnumSet(buf, CastBodyPart.class),
                readEnumSet(buf, CastBodyPart.class),
                readEnumSet(buf, CastLayer.class),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt());
    }

    /**
     * Writes this {@code CastAnimationOptions} into the packet buffer. The order of
     * fields matches {@link #read} — change one and you must change the other in
     * lockstep, or every client will deserialise garbage.
     */
    public void write(FriendlyByteBuf buf) {
        writeEnumSet(buf, suppressVanillaAnimOn);
        writeEnumSet(buf, followPlayerBody);
        writeEnumSet(buf, followPlayerLook);
        writeEnumSet(buf, hideBodyPart);
        writeEnumSet(buf, hideLayer);
        buf.writeBoolean(showInFirstPerson);
        buf.writeBoolean(heldItemsShown);
        buf.writeVarInt(animationLengthTicks);
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
        private final EnumSet<CastBodyPart> followPlayerBody      = EnumSet.noneOf(CastBodyPart.class);
        private final EnumSet<CastBodyPart> followPlayerLook      = EnumSet.noneOf(CastBodyPart.class);
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
         * Marks this ability as body-aiming: when triggered, the server snaps the player's
         * body yaw to their current look direction, so the whole rig turns to face where
         * they're aiming and it's obvious to the player where the ability will go. If the
         * body and look are already aligned (within ~1°), it does nothing.
         *
         * <p>Any non-empty set enables this; the specific parts are not individually
         * required for the yaw snap (the parameter is retained for API symmetry with the
         * other part-scoped options and possible future per-part use). Passing the arm(s)
         * the ability animates is the conventional, self-documenting choice.</p>
         *
         * <p>This is a horizontal (yaw) alignment only. For vertical aim — tilting a limb
         * up/down to match the look pitch — use {@link #followPlayerLook} instead; the two
         * compose (yaw snap turns the body, look pitch tilts the limb).</p>
         */
        public Builder followPlayerBody(CastBodyPart... parts) {
            Collections.addAll(followPlayerBody, parts);
            return this;
        }

        /**
         * Pitches the given base parts (X-axis rotation about their own pivot) to match the
         * player's vertical look angle, so the limb aims up or down with the camera. Use for
         * abilities where the player should be able to hit a target above or below them —
         * an extended arm tracks the crosshair's vertical angle while still reading as a
         * natural arm motion.
         *
         * <h3>How it differs from {@link #followPlayerBody}</h3>
         * <p>{@code followPlayerBody} is a one-shot horizontal body turn on activation;
         * {@code followPlayerLook} is a continuous per-frame vertical tilt applied at render
         * time. They're independent and combine naturally: body faces the target's
         * compass direction, limb tilts to its elevation.</p>
         *
         * <h3>Scope and pivots</h3>
         * <p>The pitch is applied around each part's anchor — for arms, the shoulder pivot
         * (y=22 in the player rig), so the whole arm swings like aiming a gun rather than
         * bending. {@link CastBodyPart#RIGHT_ARM} / {@link CastBodyPart#LEFT_ARM} are the
         * intended values. {@link CastBodyPart#HEAD} is a no-op (the head already pitches
         * with the look in vanilla); {@link CastBodyPart#BODY} is a no-op (the torso never
         * pitches in MC). Legs are accepted but rarely useful.</p>
         */
        public Builder followPlayerLook(CastBodyPart... parts) {
            Collections.addAll(followPlayerLook, parts);
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
                    followPlayerBody.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(followPlayerBody)),
                    followPlayerLook.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(followPlayerLook)),
                    hideBodyPart.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(hideBodyPart)),
                    hideLayer.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(hideLayer)),
                    showInFirstPerson,
                    heldItemsShown,
                    animationLengthTicks);
        }
    }
}