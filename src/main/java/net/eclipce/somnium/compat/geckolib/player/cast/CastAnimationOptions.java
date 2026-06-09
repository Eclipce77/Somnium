package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, per-cast-animation behaviour options.
 *
 * <p>Passed to
 * {@link net.eclipce.somnium.compat.geckolib.animation.SomniumAnimHelper#triggerCastAnimation(
 * net.minecraft.server.level.ServerPlayer, String, net.minecraft.resources.ResourceLocation, CastAnimationOptions)}
 * to fine-tune how a single cast animation interacts with vanilla locomotion, the player's
 * view direction, the visible skin layers, and the first-person view.</p>
 *
 * <h3>Defaults</h3>
 * <p>{@link #DEFAULT} reproduces the original
 * {@code triggerCastAnimation(player, anim, modelId)} behaviour exactly: vanilla animations
 * blend additively on every body part, the animation does not follow the player's look
 * direction, no skin layers are hidden, and the animation is not shown in first-person view.
 * Any option you don't explicitly set on the builder keeps its default value.</p>
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * CastAnimationOptions options = CastAnimationOptions.builder()
 *     .suppressVanillaAnimOn("right_arm")          // arm shows only our animation, not walk swing
 *     .followPlayerLook(true)                       // arm tracks where the player is looking
 *     .hideLayer("right_sleeve")                    // skin sleeve hides while arm stretches 10×
 *     .showInFirstPerson(true)                      // animation also plays in first-person
 *     .build();
 *
 * SomniumAnimHelper.triggerCastAnimation(player, "pistol_extend", GOMU_CAST_MODEL, options);
 * }</pre>
 *
 * <h3>Bone-name conventions</h3>
 * <p>All part-name arguments use the vanilla {@link net.minecraft.client.model.PlayerModel}
 * naming: {@code head}, {@code body}, {@code right_arm}, {@code left_arm}, {@code right_leg},
 * {@code left_leg} (base parts); {@code hat}, {@code jacket}, {@code right_sleeve},
 * {@code left_sleeve}, {@code right_pants}, {@code left_pants} (skin overlay parts).
 * Names that don't match any vanilla part are silently ignored.</p>
 *
 * <h3>Wire format</h3>
 * <p>{@link #write} and {@link #read} are stable across calls and survive a server round-trip
 * unchanged — the same options applied server-side will reach the client identically.</p>
 */
public final class CastAnimationOptions {

    /** No-op options — every channel disabled. Use this when calling the no-options overload. */
    public static final CastAnimationOptions DEFAULT = builder().build();

    private final Set<String> suppressVanillaAnimOn;
    private final boolean     followPlayerLook;
    private final Set<String> hideBodyPart;
    private final Set<String> hideLayer;
    private final boolean     showInFirstPerson;
    private final boolean     heldItemsShown;

    private CastAnimationOptions(Builder b) {
        this.suppressVanillaAnimOn = Set.copyOf(b.suppressVanillaAnimOn);
        this.followPlayerLook      = b.followPlayerLook;
        this.hideBodyPart          = Set.copyOf(b.hideBodyPart);
        this.hideLayer             = Set.copyOf(b.hideLayer);
        this.showInFirstPerson     = b.showInFirstPerson;
        this.heldItemsShown        = b.heldItemsShown;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /**
     * Body parts whose vanilla animation (walk swing, attack swing, look pitch, etc.) is
     * suppressed for the duration of this cast — only this animation's deltas affect them.
     * Other parts continue to receive vanilla locomotion as a base layer.
     */
    public Set<String> suppressVanillaAnimOn() { return suppressVanillaAnimOn; }

    /**
     * If {@code true}, animated arms and legs additionally inherit the player's view
     * direction (head pitch + head yaw delta), so e.g. a forward-punch animation also angles
     * up/down/sideways as the player looks around. Head and body parts are exempt (head
     * already follows look in vanilla; body rotating would be jarring).
     */
    public boolean followPlayerLook() { return followPlayerLook; }

    /** Base body parts ({@code head}, {@code body}, {@code right_arm}, ...) to render invisible. */
    public Set<String> hideBodyPart() { return hideBodyPart; }

    /** Skin overlay parts ({@code hat}, {@code jacket}, {@code right_sleeve}, ...) to render invisible. */
    public Set<String> hideLayer() { return hideLayer; }

    /**
     * If {@code true}, the cast animation's arm rotation/position/scale also plays on the
     * first-person view. When enabled, Somnium renders the full player model from the
     * player's own eye position so any animated part (arm, leg, head, body) that enters
     * the player's line of sight is visible to them — matching what third-person observers
     * see, just from a first-person camera. If {@code false}, the first-person arm renders
     * as vanilla would.
     */
    public boolean showInFirstPerson() { return showInFirstPerson; }

    /**
     * If {@code true} (default), the player's held items continue to render in their hand
     * during the cast animation, in both first- and third-person views. If {@code false},
     * the held items are hidden for the duration of the animation — useful for animations
     * where the held item would clip into the animated geometry or otherwise distract from
     * the visual.
     */
    public boolean heldItemsShown() { return heldItemsShown; }

    // ── Wire format ──────────────────────────────────────────────────────────

    public void write(FriendlyByteBuf buf) {
        writeStringSet(buf, suppressVanillaAnimOn);
        buf.writeBoolean(followPlayerLook);
        writeStringSet(buf, hideBodyPart);
        writeStringSet(buf, hideLayer);
        buf.writeBoolean(showInFirstPerson);
        buf.writeBoolean(heldItemsShown);
    }

    public static CastAnimationOptions read(FriendlyByteBuf buf) {
        Builder b = builder();
        readStringSet(buf, b.suppressVanillaAnimOn);
        b.followPlayerLook = buf.readBoolean();
        readStringSet(buf, b.hideBodyPart);
        readStringSet(buf, b.hideLayer);
        b.showInFirstPerson = buf.readBoolean();
        b.heldItemsShown = buf.readBoolean();
        return b.build();
    }

    private static void writeStringSet(FriendlyByteBuf buf, Set<String> set) {
        buf.writeVarInt(set.size());
        for (String s : set) buf.writeUtf(s);
    }

    private static void readStringSet(FriendlyByteBuf buf, Set<String> into) {
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) into.add(buf.readUtf());
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        final Set<String> suppressVanillaAnimOn = new HashSet<>();
        boolean           followPlayerLook      = false;
        final Set<String> hideBodyPart          = new HashSet<>();
        final Set<String> hideLayer             = new HashSet<>();
        boolean           showInFirstPerson     = false;
        boolean           heldItemsShown        = true;   // default ON per design

        private Builder() {}

        /**
         * Adds one or more parts whose vanilla animation should be suppressed for the
         * duration of this cast. Calling this multiple times accumulates parts (does not
         * replace).
         */
        public Builder suppressVanillaAnimOn(String... parts) {
            Collections.addAll(this.suppressVanillaAnimOn, parts);
            return this;
        }

        public Builder followPlayerLook(boolean value) {
            this.followPlayerLook = value;
            return this;
        }

        /** Adds one or more base body parts to hide. Accumulates across calls. */
        public Builder hideBodyPart(String... parts) {
            Collections.addAll(this.hideBodyPart, parts);
            return this;
        }

        /** Adds one or more skin overlay parts to hide. Accumulates across calls. */
        public Builder hideLayer(String... parts) {
            Collections.addAll(this.hideLayer, parts);
            return this;
        }

        public Builder showInFirstPerson(boolean value) {
            this.showInFirstPerson = value;
            return this;
        }

        /**
         * Whether the player's held items render in their hand during the cast animation.
         * Default is {@code true}. Set to {@code false} for animations where the held
         * item would clip into animated geometry or otherwise distract from the visual.
         */
        public Builder heldItemsShown(boolean value) {
            this.heldItemsShown = value;
            return this;
        }

        public CastAnimationOptions build() {
            return new CastAnimationOptions(this);
        }
    }
}