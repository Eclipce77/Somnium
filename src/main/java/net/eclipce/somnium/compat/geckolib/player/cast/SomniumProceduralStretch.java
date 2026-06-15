package net.eclipce.somnium.compat.geckolib.player.cast;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of <em>code-driven</em> (procedural) bone stretches, keyed by player UUID.
 *
 * <p>Unlike {@link CastAnimationOptions}-driven stretches, which come from authored animation
 * clips, a procedural stretch is set imperatively from gameplay code (server-side, pushed to
 * the client via {@code ProceduralStretchPacket}) to make a limb reach toward an arbitrary
 * world point that isn't known at animation-authoring time — e.g. the Gomu Rocket grab, where
 * the arm must stretch to whatever block the player is aiming at.</p>
 *
 * <h3>How it feeds the render pipeline</h3>
 * <p>{@link SomniumCastBoneApplicator#apply} reads the active stretch for the player each frame
 * (after the clip pass) and writes the resulting scale into {@link SomniumBoneScaleMap}, which
 * {@code ModelPartRenderMixin} already consumes. No new mixin is needed — this rides the exact
 * same per-bone scale channel as clip-driven stretches, and composes multiplicatively with
 * them.</p>
 *
 * <h3>What is stored</h3>
 * <p>A single active stretch per player: which {@link CastBodyPart} to stretch, and the
 * <em>reach</em> in blocks (the measured distance from the shoulder to the target point). The
 * applicator converts reach → bone scale using {@link #BLOCKS_PER_SCALE_UNIT}. The world target
 * itself is sent for potential future use (e.g. orienting the hand), but length is what drives
 * the visible stretch.</p>
 *
 * <p>Server code never touches this class directly; it calls the helper on
 * {@code SomniumAnimHelper}, which sends the packet that the client handler applies here.</p>
 */
public final class SomniumProceduralStretch {

    /**
     * Calibration: how many blocks of reach correspond to one unit of added bone scale on the
     * stretch axis. The arm bone is ~12 px ≈ 0.75 blocks long at scale 1; a value here means
     * "stretching the arm to N blocks of reach multiplies its length axis by 1 + reach/N".
     * Tunable — larger = the arm has to reach further to look fully stretched. Chosen so a
     * 25-block max reach reads as a long but not absurd rubber arm.
     */
    public static final float BLOCKS_PER_SCALE_UNIT = 1.5f;

    /** Immutable snapshot of one player's active procedural stretch. */
    public static final class Stretch {
        public final CastBodyPart part;
        public final float reachBlocks;     // shoulder→target distance, clamped to the ability's max
        public final float aimPitchRad;     // local bone pitch (xRot) so the limb points along look
        public final double targetX, targetY, targetZ;

        public Stretch(CastBodyPart part, float reachBlocks, float aimPitchRad,
                       double targetX, double targetY, double targetZ) {
            this.part = part;
            this.reachBlocks = reachBlocks;
            this.aimPitchRad = aimPitchRad;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }
    }

    private static final Map<UUID, Stretch> ACTIVE = new ConcurrentHashMap<>();

    private SomniumProceduralStretch() {}

    /** Sets (or replaces) the active procedural stretch for a player. */
    public static void set(UUID player, Stretch stretch) {
        ACTIVE.put(player, stretch);
    }

    /** Clears any active procedural stretch for a player (e.g. on release / retract). */
    public static void clear(UUID player) {
        ACTIVE.remove(player);
    }

    /** The active stretch for this player, or {@code null} if none. */
    @Nullable
    public static Stretch get(UUID player) {
        return ACTIVE.get(player);
    }

    /**
     * Converts a reach (in blocks) into a multiplicative scale factor for the stretch axis.
     * Reach 0 → scale 1 (no stretch); larger reach → proportionally longer limb.
     */
    public static float reachToScale(float reachBlocks) {
        if (reachBlocks <= 0f) return 1f;
        return 1f + (reachBlocks / BLOCKS_PER_SCALE_UNIT);
    }
}