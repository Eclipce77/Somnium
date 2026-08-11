package net.eclipce.somnium.compat.geckolib.player.cast;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed bone parent-relationship map for cast-animation compounding — see
 * {@link BoneHierarchyRegistry} for where these come from and the on-disk JSON format.
 *
 * <p>This is intentionally NOT the same thing as a {@code .geo.json}'s bone tree.
 * {@code default_player.geo.json} (and every vanilla-matching cast model) keeps every base
 * part as a flat, independent sibling — see {@link SomniumCastBoneApplicator}'s class
 * javadoc for why. A {@code BoneHierarchy} is a separate, purely-logical parent map used
 * ONLY to decide how animated bone deltas should compound onto each other before being
 * written onto the (still flat) vanilla {@code ModelPart}s. It never touches rendering
 * geometry and has no effect unless a triggered animation's
 * {@link CastAnimationOptions#boneHierarchy()} points at one.</p>
 */
public final class BoneHierarchy {

    /** No parent relationships at all — every bone independent. This is the default when
     *  {@link CastAnimationOptions#boneHierarchy()} is unset, and matches Somnium's original,
     *  pre-hierarchy behavior exactly: nothing compounds, every bone reacts only to its own
     *  keyframes. Existing cast abilities that never opt into a hierarchy are entirely
     *  unaffected by this class's existence. */
    public static final BoneHierarchy FLAT = new BoneHierarchy(Map.of());

    private final Map<String, String> parentOf;

    private BoneHierarchy(Map<String, String> parentOf) {
        this.parentOf = parentOf;
    }

    public static BoneHierarchy of(Map<String, String> parentOf) {
        return parentOf.isEmpty() ? FLAT : new BoneHierarchy(Map.copyOf(parentOf));
    }

    /** @return the immediate parent bone name of {@code boneName}, or {@code null} if it's a root. */
    @Nullable
    public String parentOf(String boneName) {
        return parentOf.get(boneName);
    }

    /**
     * Resolves {@code boneName}'s full ancestry, leaf first: {@code chainToRoot("right_sleeve")}
     * for the hierarchy in this class's example returns {@code [right_sleeve, right_arm, body]}.
     * The last entry is always a root (a bone with no parent — {@link #parentOf} returns null
     * for it).
     *
     * <p>Cycle-safe: a malformed JSON file that parents a bone to itself (directly or via a
     * loop) stops the walk at the point it would repeat rather than looping forever, so a bad
     * hierarchy file degrades to "the ancestry above the cycle is ignored" instead of hanging
     * the client.</p>
     */
    public List<String> chainToRoot(String boneName) {
        List<String> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = boneName;
        while (current != null && seen.add(current)) {
            chain.add(current);
            current = parentOf(current);
        }
        return chain;
    }

    public boolean isFlat() {
        return parentOf.isEmpty();
    }
}
