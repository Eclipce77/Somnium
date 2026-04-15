package net.eclipce.somnium.core.unlock.conditions;

import net.eclipce.somnium.core.unlock.UnlockCondition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Combines multiple child conditions with AND or OR logic.
 *
 * <p>Each child condition maintains its own progress data as a sub-tag
 * within the composite's progress tag (keyed by index: "0", "1", etc.).
 * The composite is met when all (AND) or any (OR) children are met.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Unlock Fire Shield when: used Fire Punch 100 times AND killed 10 enemies
 * new CompositeCondition(CompositeCondition.Mode.AND,
 *     new AbilityUsageCondition(() -> ModAbilities.FIRE_PUNCH.get(), 100),
 *     new KillCondition(10)
 * )
 *
 * // Unlock Hover when: used Jump Boost 30 times OR used Levitate 20 times
 * new CompositeCondition(CompositeCondition.Mode.OR,
 *     new AbilityUsageCondition(() -> ModAbilities.JUMP_BOOST.get(), 30),
 *     new AbilityUsageCondition(() -> ModAbilities.LEVITATE.get(), 20)
 * )
 * }</pre>
 */
public class CompositeCondition extends UnlockCondition {

    /**
     * How child conditions are combined.
     */
    public enum Mode {
        /** All children must be met. */
        AND,
        /** At least one child must be met. */
        OR
    }

    private final Mode mode;
    private final List<UnlockCondition> children;

    /**
     * Creates a composite condition.
     *
     * @param mode     AND or OR
     * @param children the child conditions (at least 2)
     */
    public CompositeCondition(Mode mode, UnlockCondition... children) {
        if (children.length < 2) {
            throw new IllegalArgumentException(
                    "CompositeCondition requires at least 2 children, got " + children.length);
        }
        this.mode = mode;
        this.children = Collections.unmodifiableList(Arrays.asList(children));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UnlockCondition implementation
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean isMet(CompoundTag progress) {
        for (int i = 0; i < children.size(); i++) {
            CompoundTag childProgress = progress.getCompound(String.valueOf(i));
            boolean childMet = children.get(i).isMet(childProgress);

            if (mode == Mode.OR && childMet) return true;
            if (mode == Mode.AND && !childMet) return false;
        }
        // AND: all passed → true. OR: none passed → false.
        return mode == Mode.AND;
    }

    @Override
    public Component getProgressText(CompoundTag progress) {
        String separator = mode == Mode.AND ? " AND " : " OR ";
        MutableComponent text = Component.empty();

        for (int i = 0; i < children.size(); i++) {
            if (i > 0) text.append(separator);
            CompoundTag childProgress = progress.getCompound(String.valueOf(i));
            text.append(children.get(i).getProgressText(childProgress));
        }

        return text;
    }

    @Override
    public float getProgressFraction(CompoundTag progress) {
        if (mode == Mode.AND) {
            // Average of all children
            float total = 0f;
            for (int i = 0; i < children.size(); i++) {
                CompoundTag childProgress = progress.getCompound(String.valueOf(i));
                total += children.get(i).getProgressFraction(childProgress);
            }
            return total / children.size();
        } else {
            // Max of all children (OR succeeds with any one)
            float max = 0f;
            for (int i = 0; i < children.size(); i++) {
                CompoundTag childProgress = progress.getCompound(String.valueOf(i));
                max = Math.max(max, children.get(i).getProgressFraction(childProgress));
            }
            return max;
        }
    }

    @Override
    public CompoundTag createInitialProgress() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < children.size(); i++) {
            tag.put(String.valueOf(i), children.get(i).createInitialProgress());
        }
        return tag;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Child access — used by ProgressionHandler to update children
    // ═══════════════════════════════════════════════════════════════════

    /** @return the combination mode (AND/OR) */
    public Mode getMode() {
        return mode;
    }

    /** @return unmodifiable list of child conditions */
    public List<UnlockCondition> getChildren() {
        return children;
    }

    /**
     * Gets the progress sub-tag for a specific child condition.
     * Used by the ProgressionHandler when it needs to update a
     * specific child's progress within a composite.
     *
     * @param compositeProgress the composite's progress tag
     * @param childIndex        the child index
     * @return the child's progress sub-tag
     */
    public CompoundTag getChildProgress(CompoundTag compositeProgress, int childIndex) {
        return compositeProgress.getCompound(String.valueOf(childIndex));
    }
}
