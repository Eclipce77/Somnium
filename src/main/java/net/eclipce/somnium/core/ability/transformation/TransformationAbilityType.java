package net.eclipce.somnium.core.ability.transformation;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A specialized {@link AbilityType} for transformations — abilities that change
 * the player's model, animations, and attributes when activated.
 *
 * <p>Transformations function as TOGGLE abilities on the ability bar but add
 * a multi-phase lifecycle (transform in → transformed → transform out), model
 * replacement, attribute modifications, and optional condition-based triggers
 * for involuntary activation.</p>
 *
 * <h3>Phase lifecycle</h3>
 * <p>When a transformation activates, it doesn't instantly apply — it moves
 * through {@link TransformationPhase} states:</p>
 * <ol>
 *     <li>{@link TransformationPhase#TRANSFORMING_IN} — transition animation plays
 *         for {@link #getTransformInDuration()} ticks</li>
 *     <li>{@link TransformationPhase#TRANSFORMED} — fully transformed, attributes
 *         applied, new model active</li>
 *     <li>{@link TransformationPhase#TRANSFORMING_OUT} — revert animation plays
 *         for {@link #getTransformOutDuration()} ticks</li>
 *     <li>Back to {@link TransformationPhase#IDLE}</li>
 * </ol>
 *
 * <h3>Mutual exclusivity</h3>
 * <p>All transformations automatically receive the {@code somnium:transformation}
 * conflict tag. Only one transformation can be active at a time — while any
 * transformation is active (including transition phases), all other transformation
 * abilities on the bar are disabled and grayed out.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public class WerewolfTransformation extends TransformationAbilityType {
 *
 *     public WerewolfTransformation() {
 *         super(new TransformationProperties()
 *             .transformInDuration(40)
 *             .transformOutDuration(30)
 *             .cancelableByDamage(false)
 *             .allowVoluntaryActivation(false)
 *             .trigger(new TimeTrigger(13000, 23000))
 *             .visualData(TransformationData.builder()
 *                 .transformedModel(new ResourceLocation("mymod", "geo/werewolf"))
 *                 .transformSound(new ResourceLocation("mymod", "werewolf_howl"))
 *                 .modelScale(1.2f)
 *                 .build())
 *             .attributeModifier(Attributes.MOVEMENT_SPEED,
 *                 new ResourceLocation("mymod", "werewolf_speed"),
 *                 0.05, AttributeModifier.Operation.ADDITION)
 *             .attributeModifier(Attributes.ATTACK_DAMAGE,
 *                 new ResourceLocation("mymod", "werewolf_damage"),
 *                 4.0, AttributeModifier.Operation.ADDITION)
 *         );
 *     }
 *
 *     @Override
 *     public void onTransformComplete(AbilityActivationContext context) {
 *         // Custom logic when fully transformed
 *     }
 *
 *     @Override
 *     public void onTransformedTick(AbilityActivationContext context) {
 *         // Custom per-tick logic while transformed
 *     }
 * }
 * }</pre>
 *
 * @see TransformationPhase
 * @see TransformationData
 * @see TransformationTrigger
 * @see TransformationInstance
 */
public class TransformationAbilityType extends AbilityType {

    /**
     * Conflict tag automatically applied to all transformations.
     * Ensures mutual exclusivity — only one transformation can be active at a time.
     */
    public static final ResourceLocation TRANSFORMATION_CONFLICT_TAG =
            new ResourceLocation(Somnium.MOD_ID, "transformation");

    // ═══════════════════════════════════════════════════════════════════
    //  Transformation-specific properties
    // ═══════════════════════════════════════════════════════════════════

    private final int transformInDuration;
    private final int transformOutDuration;
    private final int maxTransformDuration;
    private final boolean cancelableByDamage;
    private final boolean allowVoluntaryActivation;
    private final TransformationData visualData;
    private final TransformationTrigger trigger;
    private final Map<Attribute, AttributeModifierEntry> attributeModifiers;

    /**
     * Creates a TransformationAbilityType with the given properties.
     *
     * @param properties configuration built via {@link TransformationProperties}
     */
    public TransformationAbilityType(TransformationProperties properties) {
        super(properties);
        this.transformInDuration = properties.transformInDuration;
        this.transformOutDuration = properties.transformOutDuration;
        this.maxTransformDuration = properties.maxTransformDuration;
        this.cancelableByDamage = properties.cancelableByDamage;
        this.allowVoluntaryActivation = properties.allowVoluntaryActivation;
        this.visualData = properties.visualData;
        this.trigger = properties.trigger;
        this.attributeModifiers = Collections.unmodifiableMap(
                new LinkedHashMap<>(properties.attributeModifiers));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Transformation lifecycle hooks — override these in your subclass
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when the TRANSFORMING_IN phase begins. The transformation
     * animation is about to play. Use this for visual setup, sound effects,
     * or particle spawning at the start of the transformation.
     *
     * @param context the activation context
     */
    public void onTransformStart(AbilityActivationContext context) {}

    /**
     * Called each tick during the TRANSFORMING_IN phase.
     * Use for gradual effects during the transition (progressive particle
     * effects, screen overlays, etc.).
     *
     * @param context the activation context
     * @param ticksInPhase how many ticks have elapsed in this phase
     */
    public void onTransformingInTick(AbilityActivationContext context, int ticksInPhase) {}

    /**
     * Called when the TRANSFORMING_IN phase completes and the player enters
     * the TRANSFORMED state. Attribute modifiers are applied at this point.
     * Use for effects that should happen when the transformation is fully active.
     *
     * @param context the activation context
     */
    public void onTransformComplete(AbilityActivationContext context) {}

    /**
     * Called each tick while the player is in the TRANSFORMED state.
     * Use for ongoing transformation effects (particle trails, aura effects,
     * periodic damage to nearby entities, etc.).
     *
     * @param context the activation context
     */
    public void onTransformedTick(AbilityActivationContext context) {}

    /**
     * Called when the TRANSFORMING_OUT phase begins. The de-transformation
     * animation is about to play. Attribute modifiers are removed at this point.
     *
     * @param context the activation context
     */
    public void onDeTransformStart(AbilityActivationContext context) {}

    /**
     * Called each tick during the TRANSFORMING_OUT phase.
     *
     * @param context the activation context
     * @param ticksInPhase how many ticks have elapsed in this phase
     */
    public void onTransformingOutTick(AbilityActivationContext context, int ticksInPhase) {}

    /**
     * Called when the TRANSFORMING_OUT phase completes and the player
     * returns to IDLE. The transformation is fully over. Use for cleanup.
     *
     * @param context the activation context
     */
    public void onDeTransformComplete(AbilityActivationContext context) {}

    /**
     * Called when a transformation is canceled during the TRANSFORMING_IN phase
     * (e.g., by taking damage when {@link #isCancelableByDamage()} is true).
     * The player reverts to IDLE without going through TRANSFORMING_OUT.
     *
     * @param context the activation context
     */
    public void onTransformCanceled(AbilityActivationContext context) {}

    // ═══════════════════════════════════════════════════════════════════
    //  Duration and forced de-transform
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called each tick while transformed to check if the transformation
     * should be forcibly ended. Override to add custom de-transform conditions
     * beyond the max duration timer.
     *
     * <p>The base implementation checks the max duration timer. Call
     * {@code super.shouldForceDeTransform(context, ticksTransformed)}
     * to preserve duration checking, or return your own conditions.</p>
     *
     * @param context           the activation context
     * @param ticksTransformed  how many ticks the player has been in the
     *                           TRANSFORMED state
     * @return {@code true} if the transformation should begin de-transforming
     */
    public boolean shouldForceDeTransform(AbilityActivationContext context, int ticksTransformed) {
        return maxTransformDuration > 0 && ticksTransformed >= maxTransformDuration;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AbilityType overrides
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Overrides the base activation to route through the transformation
     * phase system. When activated, starts the TRANSFORMING_IN phase
     * rather than immediately applying effects.
     *
     * <p>Addon developers should NOT override this — override
     * {@link #onTransformStart} and {@link #onTransformComplete} instead.</p>
     */
    @Override
    public final void onActivate(AbilityActivationContext context) {
        // The actual phase transition logic will be handled by the
        // ability tick system (Layer 5), which reads the TransformationInstance
        // and drives the state machine. This method is the entry point
        // that tells the system "start transforming."
        TransformationInstance instance = getTransformationInstance(context);
        if (instance != null && instance.getPhase() == TransformationPhase.IDLE) {
            instance.setPhase(TransformationPhase.TRANSFORMING_IN);
            instance.resetPhaseTicks();
            instance.setTriggeredByCondition(false);
            onTransformStart(context);
        }
    }

    /**
     * Overrides the base deactivation to route through the de-transform
     * phase. When deactivated, starts TRANSFORMING_OUT rather than
     * immediately removing effects.
     *
     * <p>Addon developers should NOT override this — override
     * {@link #onDeTransformStart} and {@link #onDeTransformComplete} instead.</p>
     */
    @Override
    public final void onDeactivate(AbilityActivationContext context) {
        TransformationInstance instance = getTransformationInstance(context);
        if (instance != null && instance.getPhase() == TransformationPhase.TRANSFORMED) {
            instance.setPhase(TransformationPhase.TRANSFORMING_OUT);
            instance.resetPhaseTicks();
            onDeTransformStart(context);
        }
    }

    /**
     * The main tick method is final — transformation ticking is routed through
     * the phase-specific hooks instead.
     *
     * <p>The ability tick system (Layer 5) will call the appropriate phase
     * tick method based on the current phase.</p>
     */
    @Override
    public final void onActiveTick(AbilityActivationContext context) {
        // Phase-based ticking is driven by the ability system (Layer 5),
        // which calls the appropriate onTransformingInTick / onTransformedTick /
        // onTransformingOutTick based on the current phase.
        // This method is left as a no-op to prevent addon devs from
        // accidentally bypassing the phase system.
    }

    /**
     * Validates activation. For transformations, also checks that no
     * other transformation is currently active and that voluntary
     * activation is allowed (if this is a manual activation).
     */
    @Override
    public boolean canActivate(AbilityActivationContext context) {
        if (!super.canActivate(context)) return false;

        // Check if voluntary activation is allowed
        // (triggered activations bypass this — handled by the trigger system)
        if (!allowVoluntaryActivation) {
            return false;
        }

        // Additional transformation-specific checks will be added by the
        // tick system (Layer 5) — e.g., checking if another transformation
        // is already active on this player.

        return true;
    }

    /**
     * Creates a {@link TransformationInstance} instead of a plain AbilityInstance.
     * This ensures transformation-specific state (phase, phase ticks, trigger
     * cooldown) is tracked per-player.
     */
    @Override
    public AbilityInstance createInstance() {
        return new TransformationInstance(this);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @return duration of the transform-in phase in ticks
     *         (20 ticks = 1 second). 0 means instant transformation.
     */
    public int getTransformInDuration() {
        return transformInDuration;
    }

    /**
     * @return duration of the transform-out phase in ticks.
     *         0 means instant de-transformation.
     */
    public int getTransformOutDuration() {
        return transformOutDuration;
    }

    /**
     * @return maximum number of ticks the TRANSFORMED state can last.
     *         0 means unlimited duration (until manually or forcibly ended).
     */
    public int getMaxTransformDuration() {
        return maxTransformDuration;
    }

    /**
     * @return {@code true} if taking damage during the TRANSFORMING_IN phase
     *         can cancel the transformation before it completes
     */
    public boolean isCancelableByDamage() {
        return cancelableByDamage;
    }

    /**
     * @return {@code true} if the player can manually activate this
     *         transformation from the ability bar. When {@code false},
     *         the transformation can only be activated by its
     *         {@link TransformationTrigger}.
     */
    public boolean isAllowVoluntaryActivation() {
        return allowVoluntaryActivation;
    }

    /**
     * @return the visual specification for this transformation (model,
     *         animations, sounds, scale)
     */
    public TransformationData getVisualData() {
        return visualData;
    }

    /**
     * @return the condition-based trigger for involuntary activation,
     *         or {@code null} if this transformation is purely voluntary
     */
    @Nullable
    public TransformationTrigger getTrigger() {
        return trigger;
    }

    /**
     * @return {@code true} if this transformation has a condition-based trigger
     */
    public boolean hasTrigger() {
        return trigger != null;
    }

    /**
     * @return an unmodifiable map of attribute modifiers applied while
     *         transformed. Keys are the attributes, values contain the
     *         modifier details.
     */
    public Map<Attribute, AttributeModifierEntry> getAttributeModifiers() {
        return attributeModifiers;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helper
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Safely casts the context's AbilityInstance to TransformationInstance.
     *
     * @param context the activation context
     * @return the TransformationInstance, or {@code null} if the instance
     *         is not a TransformationInstance (shouldn't happen in practice)
     */
    @Nullable
    protected TransformationInstance getTransformationInstance(AbilityActivationContext context) {
        AbilityInstance instance = context.getAbilityInstance();
        if (instance instanceof TransformationInstance ti) {
            return ti;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Attribute modifier entry
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Holds the data for a single attribute modifier applied during transformation.
     * Stored as a data entry rather than a raw {@link AttributeModifier} because
     * each modifier needs a unique UUID (generated per-transformation-type) and
     * a name for identification.
     */
    public static class AttributeModifierEntry {

        private final UUID uuid;
        private final String name;
        private final double amount;
        private final AttributeModifier.Operation operation;

        public AttributeModifierEntry(UUID uuid, String name, double amount,
                                      AttributeModifier.Operation operation) {
            this.uuid = uuid;
            this.name = name;
            this.amount = amount;
            this.operation = operation;
        }

        /**
         * Creates the actual {@link AttributeModifier} to apply to the player.
         * Called when entering the TRANSFORMED phase.
         */
        public AttributeModifier createModifier() {
            return new AttributeModifier(uuid, name, amount, operation);
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public double getAmount() { return amount; }
        public AttributeModifier.Operation getOperation() { return operation; }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Properties builder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Configuration builder for {@link TransformationAbilityType}. Extends
     * {@link AbilityType.Properties} with transformation-specific settings.
     *
     * <p>Automatically sets the activation type to {@link ActivationType#TOGGLE}
     * and adds the {@code somnium:transformation} conflict tag. Addon developers
     * should not change these defaults.</p>
     */
    public static class TransformationProperties extends AbilityType.Properties {

        int transformInDuration = 20;
        int transformOutDuration = 20;
        int maxTransformDuration = 0;
        boolean cancelableByDamage = false;
        boolean allowVoluntaryActivation = true;
        TransformationData visualData = TransformationData.builder().build();
        TransformationTrigger trigger = null;
        Map<Attribute, AttributeModifierEntry> attributeModifiers = new LinkedHashMap<>();

        /**
         * Creates new TransformationProperties with TOGGLE activation type
         * and the transformation conflict tag pre-configured.
         */
        public TransformationProperties() {
            // Force TOGGLE activation — transformations are always toggles
            activationType(ActivationType.TOGGLE);
            // Auto-add the transformation mutual exclusivity tag
            conflictTag(TRANSFORMATION_CONFLICT_TAG);
        }

        // ── Transformation-specific setters ─────────────────────────

        /**
         * Sets the duration of the transform-in phase in ticks.
         * Default is 20 ticks (1 second). Set to 0 for instant transformation.
         */
        public TransformationProperties transformInDuration(int ticks) {
            this.transformInDuration = Math.max(0, ticks);
            return this;
        }

        /**
         * Sets the duration of the transform-out phase in ticks.
         * Default is 20 ticks (1 second). Set to 0 for instant de-transformation.
         */
        public TransformationProperties transformOutDuration(int ticks) {
            this.transformOutDuration = Math.max(0, ticks);
            return this;
        }

        /**
         * Sets the maximum duration of the TRANSFORMED state in ticks.
         * Default is 0 (unlimited). After this duration, the transformation
         * automatically begins de-transforming.
         */
        public TransformationProperties maxDuration(int ticks) {
            this.maxTransformDuration = Math.max(0, ticks);
            return this;
        }

        /**
         * Sets whether taking damage during TRANSFORMING_IN can cancel
         * the transformation. Default is {@code false}.
         */
        public TransformationProperties cancelableByDamage(boolean cancelable) {
            this.cancelableByDamage = cancelable;
            return this;
        }

        /**
         * Sets whether the player can manually activate this transformation.
         * When {@code false}, it can only be activated by its trigger.
         * Default is {@code true}.
         */
        public TransformationProperties allowVoluntaryActivation(boolean allow) {
            this.allowVoluntaryActivation = allow;
            return this;
        }

        /**
         * Sets the visual specification (model, animations, sounds).
         */
        public TransformationProperties visualData(TransformationData data) {
            this.visualData = data;
            return this;
        }

        /**
         * Sets the condition-based trigger for involuntary activation.
         * {@code null} (default) means the transformation is purely voluntary.
         */
        public TransformationProperties trigger(@Nullable TransformationTrigger trigger) {
            this.trigger = trigger;
            return this;
        }

        /**
         * Adds an attribute modifier applied while in the TRANSFORMED state.
         * The modifier is automatically applied when transformation completes
         * and removed when de-transformation starts.
         *
         * @param attribute the attribute to modify (e.g., {@code Attributes.ATTACK_DAMAGE})
         * @param id        a unique identifier for this modifier (used to generate a
         *                   stable UUID so modifiers can be cleanly removed)
         * @param amount    the modifier value
         * @param operation the modifier operation (ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL)
         */
        public TransformationProperties attributeModifier(Attribute attribute,
                                                          ResourceLocation id,
                                                          double amount,
                                                          AttributeModifier.Operation operation) {
            UUID uuid = UUID.nameUUIDFromBytes(id.toString().getBytes());
            String name = id.getNamespace() + "." + id.getPath();
            this.attributeModifiers.put(attribute,
                    new AttributeModifierEntry(uuid, name, amount, operation));
            return this;
        }

        // ── Overridden base setters for fluent chaining ─────────────
        // These override the parent methods to return TransformationProperties
        // so that addon devs can chain base and transformation methods freely.

        @Override
        public TransformationProperties cooldown(int ticks) {
            super.cooldown(ticks);
            return this;
        }

        @Override
        public TransformationProperties resourceCost(float cost) {
            super.resourceCost(cost);
            return this;
        }

        @Override
        public TransformationProperties persistOnDeath(boolean persist) {
            super.persistOnDeath(persist);
            return this;
        }

        @Override
        public TransformationProperties icon(ResourceLocation icon) {
            super.icon(icon);
            return this;
        }

        @Override
        public TransformationProperties conflictTag(ResourceLocation tag) {
            super.conflictTag(tag);
            return this;
        }

        @Override
        public TransformationProperties category(net.eclipce.somnium.core.category.AbilityCategory category) {
            super.category(category);
            return this;
        }

        // Note: activationType is NOT overridden — transformations are always TOGGLE.
        // Note: defaultEnabled is NOT overridden — not relevant for transformations.
    }
}