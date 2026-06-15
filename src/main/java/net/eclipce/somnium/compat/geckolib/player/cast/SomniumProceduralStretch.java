package net.eclipce.somnium.compat.geckolib.player.cast;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of a <em>code-driven body lean</em>, keyed by player UUID.
 *
 * <p>Some abilities need to pitch a set of the player's body parts from gameplay code, toward a
 * direction that isn't known at animation-authoring time — e.g. the Gomu Rocket, where, while
 * the player is propelled toward a grabbed block, every part EXCEPT the grabbing arm should
 * lean (pitch) toward the block so the body "dives" the way it's being pulled. The amount and
 * the set of affected parts are computed server-side and pushed to the client via
 * {@code ProceduralStretchPacket}.</p>
 *
 * <h3>How it feeds the render pipeline</h3>
 * <p>{@link SomniumCastBoneApplicator#apply} reads the active lean for the player each frame and,
 * for each listed part, registers a render-time pitch about the bone's rest pivot via
 * {@link SomniumBoneScaleMap#setLookPitch} — the same proven mechanism the clip-driven
 * {@code changeDirectionOnLook} uses. It composes with any active clip (the rocket leg clips,
 * the arm extend clip) because the pitch is applied as a frame rotation at render time.</p>
 *
 * <h3>What is stored</h3>
 * <p>A single active lean per player: the pitch in radians, and the set of {@link CastBodyPart}s
 * to apply it to (typically everything except the grabbing arm). Smoothing/easing of the angle
 * is done server-side before it's sent, so the client just renders whatever angle it last got.</p>
 */
public final class SomniumProceduralStretch {

    /** Immutable snapshot of one player's active body lean. */
    /**
     * Immutable snapshot of one player's active procedural pose. Carries a body <em>lean</em>
     * (a pitch applied to a set of parts) and, optionally, a held <em>arm stretch</em> (a scale
     * + aim on one arm) — both at once, because during the Gomu Rocket flight the legs play a
     * clip while the grabbing arm must stay stretched toward the block (the single clip slot
     * can't do both, so the arm rides this procedural channel).
     */
    public static final class Lean {
        public final float pitchRad;            // lean pitch applied to each listed part (radians)
        public final Set<CastBodyPart> parts;   // parts to lean (everything but the grabbing arm)

        // Optional held-arm stretch (null armPart = no arm stretch this frame).
        @Nullable public final CastBodyPart armPart;
        public final float armScaleY;           // multiplicative Y length for the arm bone
        public final float armPitchRad;         // aim pitch for the arm (about its rest pivot)

        public Lean(float pitchRad, Set<CastBodyPart> parts,
                    @Nullable CastBodyPart armPart, float armScaleY, float armPitchRad) {
            this.pitchRad = pitchRad;
            this.parts = parts;
            this.armPart = armPart;
            this.armScaleY = armScaleY;
            this.armPitchRad = armPitchRad;
        }
    }

    private static final Map<UUID, Lean> ACTIVE = new ConcurrentHashMap<>();

    private SomniumProceduralStretch() {}

    /** Sets (or replaces) the active body lean for a player. */
    public static void set(UUID player, Lean lean) {
        ACTIVE.put(player, lean);
    }

    /** Clears any active body lean for a player. */
    public static void clear(UUID player) {
        ACTIVE.remove(player);
    }

    /** The active lean for this player, or {@code null} if none. */
    @Nullable
    public static Lean get(UUID player) {
        return ACTIVE.get(player);
    }

    /** Decodes a bitmask of CastBodyPart ordinals into a part set. */
    public static Set<CastBodyPart> partsFromMask(int mask) {
        EnumSet<CastBodyPart> set = EnumSet.noneOf(CastBodyPart.class);
        CastBodyPart[] all = CastBodyPart.values();
        for (int i = 0; i < all.length; i++) {
            if ((mask & (1 << i)) != 0) set.add(all[i]);
        }
        return set;
    }

    /** Encodes a part set into a bitmask of CastBodyPart ordinals. */
    public static int maskFromParts(Set<CastBodyPart> parts) {
        int mask = 0;
        for (CastBodyPart p : parts) mask |= (1 << p.ordinal());
        return mask;
    }
}