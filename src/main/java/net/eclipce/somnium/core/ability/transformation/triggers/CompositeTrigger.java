package net.eclipce.somnium.core.ability.transformation.triggers;

import net.eclipce.somnium.core.ability.transformation.TransformationTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/**
 * A transformation trigger that combines multiple child triggers using
 * AND or OR logic.
 *
 * <h3>Example: Transform only at night AND below 50% health</h3>
 * <pre>{@code
 * new CompositeTrigger(
 *     CompositeTrigger.Mode.AND,
 *     true, false, 0,
 *     new TimeTrigger(13000, 23000),
 *     new HealthThresholdTrigger(0.5f)
 * )
 * }</pre>
 *
 * <h3>Example: Transform at night OR below 25% health</h3>
 * <pre>{@code
 * new CompositeTrigger(
 *     CompositeTrigger.Mode.OR,
 *     false, false, 200,
 *     new TimeTrigger(13000, 23000),
 *     new HealthThresholdTrigger(0.25f)
 * )
 * }</pre>
 *
 * <p>Composites can be nested: a composite can contain other composites,
 * allowing complex condition trees.</p>
 */
public class CompositeTrigger extends TransformationTrigger {

    /**
     * How child trigger conditions are combined.
     */
    public enum Mode {
        /** All child triggers must return true. */
        AND,
        /** At least one child trigger must return true. */
        OR
    }

    private final Mode mode;
    private final List<TransformationTrigger> children;

    /**
     * Creates a composite trigger with explicit behavior configuration.
     *
     * @param mode                     how to combine child conditions
     * @param autoRevertOnConditionEnd if true, reverts when the composite
     *                                  condition is no longer met
     * @param suppressible             if true, the player can resist
     * @param triggerCooldownTicks     minimum ticks between forced activations
     * @param children                 the child triggers to combine (at least 2)
     * @throws IllegalArgumentException if fewer than 2 children are provided
     */
    public CompositeTrigger(Mode mode,
                            boolean autoRevertOnConditionEnd,
                            boolean suppressible,
                            int triggerCooldownTicks,
                            TransformationTrigger... children) {
        super(autoRevertOnConditionEnd, suppressible, triggerCooldownTicks);
        if (children.length < 2) {
            throw new IllegalArgumentException(
                    "CompositeTrigger requires at least 2 child triggers, got " + children.length);
        }
        this.mode = mode;
        this.children = Arrays.asList(children);
    }

    @Override
    public boolean test(ServerPlayer player) {
        return switch (mode) {
            case AND -> children.stream().allMatch(t -> t.test(player));
            case OR -> children.stream().anyMatch(t -> t.test(player));
        };
    }

    /**
     * For composite triggers, suppression succeeds if ANY child's suppression
     * succeeds (for AND mode) or if ALL children's suppressions succeed (for OR mode).
     * This provides intuitive behavior: in AND mode, suppressing one condition
     * is enough to prevent the composite from triggering.
     */
    @Override
    public boolean canSuppress(ServerPlayer player) {
        return switch (mode) {
            case AND -> children.stream()
                    .filter(TransformationTrigger::isSuppressible)
                    .anyMatch(t -> t.canSuppress(player));
            case OR -> children.stream()
                    .filter(TransformationTrigger::isSuppressible)
                    .allMatch(t -> t.canSuppress(player));
        };
    }

    /** @return the combination mode (AND/OR) */
    public Mode getMode() {
        return mode;
    }

    /** @return an unmodifiable view of the child triggers */
    public List<TransformationTrigger> getChildren() {
        return children;
    }
}