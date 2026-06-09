package net.eclipce.somnium.compat.geckolib.player.cast;

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
    private final Set<CastBodyPart> hideBodyPart;
    private final Set<CastLayer>    hideLayer;
    private final boolean showInFirstPerson;
    private final boolean heldItemsShown;

    private CastAnimationOptions(Set<CastBodyPart> suppressVanillaAnimOn,
                                 Set<CastBodyPart> followPlayerBody,
                                 Set<CastBodyPart> hideBodyPart,
                                 Set<CastLayer>    hideLayer,
                                 boolean showInFirstPerson,
                                 boolean heldItemsShown) {
        this.suppressVanillaAnimOn = suppressVanillaAnimOn;
        this.followPlayerBody      = followPlayerBody;
        this.hideBodyPart          = hideBodyPart;
        this.hideLayer             = hideLayer;
        this.showInFirstPerson     = showInFirstPerson;
        this.heldItemsShown        = heldItemsShown;
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
     * The base parts that should also receive the player's head pitch as an
     * additional X-axis rotation on top of the animation. Use for aimable abilities
     * — an arm extending forward will tilt up when the player looks up, etc. See
     * {@link Builder#followPlayerBody} for the rationale and scope.
     */
    public Set<CastBodyPart> followPlayerBody() { return followPlayerBody; }

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

    public static Builder builder() { return new Builder(); }

    // ─── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private final EnumSet<CastBodyPart> suppressVanillaAnimOn = EnumSet.noneOf(CastBodyPart.class);
        private final EnumSet<CastBodyPart> followPlayerBody      = EnumSet.noneOf(CastBodyPart.class);
        private final EnumSet<CastBodyPart> hideBodyPart          = EnumSet.noneOf(CastBodyPart.class);
        private final EnumSet<CastLayer>    hideLayer             = EnumSet.noneOf(CastLayer.class);
        private boolean showInFirstPerson = false;
        private boolean heldItemsShown    = true;

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
         * Adds the player's head pitch as an extra X-axis rotation on the given parts,
         * so the animation aims with where the player is looking. The body's yaw is
         * already inherited automatically (the whole rig rotates with the entity body
         * yaw), so this only handles the pitch axis — which is the bit vanilla doesn't
         * already give you on body-anchored bones.
         *
         * <h3>Scope</h3>
         * <p>Pitch is applied only to base parts that the cast animation actually
         * touches this frame. A right-arm-only animation with
         * {@code followPlayerBody(RIGHT_ARM, LEFT_ARM)} only pitches the right arm —
         * the left arm doesn't get a phantom pitch added to its walk swing. Layer
         * overlays (sleeves, pants) follow the base part automatically via the
         * applicator's post-application {@code copyFrom} pass.</p>
         *
         * <h3>Which parts make sense</h3>
         * <p>{@link CastBodyPart#RIGHT_ARM}, {@link CastBodyPart#LEFT_ARM},
         * {@link CastBodyPart#RIGHT_LEG}, {@link CastBodyPart#LEFT_LEG} are the
         * intended values. Passing {@link CastBodyPart#HEAD} is a no-op (the head
         * pitches with the look in vanilla already, and double-pitching would over-rotate).
         * Passing {@link CastBodyPart#BODY} is also a no-op — the player body never
         * pitches in MC and adding pitch there would break the rig's anchor.</p>
         */
        public Builder followPlayerBody(CastBodyPart... parts) {
            Collections.addAll(followPlayerBody, parts);
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

        public CastAnimationOptions build() {
            // EnumSet copies preserve enum-iteration order and use a compact bitset
            // internally — cheap to construct, cheap to iterate.
            return new CastAnimationOptions(
                    suppressVanillaAnimOn.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(suppressVanillaAnimOn)),
                    followPlayerBody.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(followPlayerBody)),
                    hideBodyPart.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(hideBodyPart)),
                    hideLayer.isEmpty() ? Collections.emptySet()
                            : Collections.unmodifiableSet(EnumSet.copyOf(hideLayer)),
                    showInFirstPerson,
                    heldItemsShown);
        }
    }
}