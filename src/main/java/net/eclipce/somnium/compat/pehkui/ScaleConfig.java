package net.eclipce.somnium.compat.pehkui;

import net.minecraft.world.entity.Entity;

/**
 * Configuration data for Pehkui scale effects. Can be attached to
 * abilities and transformations to define how they change entity size.
 *
 * <p>When the ability activates or transformation completes, the
 * configured scale values are applied. When deactivated, they're
 * reverted (either instantly or over a transition period).</p>
 *
 * <h3>Usage in ability/transformation code</h3>
 * <pre>{@code
 * // In TransformationAbilityType properties:
 * private static final ScaleConfig GIANT_SCALE = ScaleConfig.builder()
 *     .baseScale(3.0f)           // 3x overall size
 *     .motionScale(0.8f)         // slightly slower movement
 *     .attackScale(2.0f)         // double attack damage
 *     .reachScale(2.0f)          // double reach
 *     .transitionTicks(40)       // 2 second transition
 *     .build();
 *
 * // In onTransformComplete:
 * if (PehkuiCompat.isLoaded()) {
 *     GIANT_SCALE.apply(player);
 * }
 *
 * // In onDeTransformStart:
 * if (PehkuiCompat.isLoaded()) {
 *     GIANT_SCALE.revert(player);
 * }
 * }</pre>
 */
public class ScaleConfig {

    private final float baseScale;
    private final float modelWidth;
    private final float modelHeight;
    private final float motionScale;
    private final float reachScale;
    private final float attackScale;
    private final float defenseScale;
    private final float healthScale;
    private final float stepHeightScale;
    private final float projectilesScale;
    private final float explosionsScale;
    private final int transitionTicks;

    private ScaleConfig(Builder builder) {
        this.baseScale = builder.baseScale;
        this.modelWidth = builder.modelWidth;
        this.modelHeight = builder.modelHeight;
        this.motionScale = builder.motionScale;
        this.reachScale = builder.reachScale;
        this.attackScale = builder.attackScale;
        this.defenseScale = builder.defenseScale;
        this.healthScale = builder.healthScale;
        this.stepHeightScale = builder.stepHeightScale;
        this.projectilesScale = builder.projectilesScale;
        this.explosionsScale = builder.explosionsScale;
        this.transitionTicks = builder.transitionTicks;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Application
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies all configured scale values to the entity.
     * Uses transition ticks if set > 0, otherwise instant.
     *
     * @param entity the entity to scale
     */
    public void apply(Entity entity) {
        if (transitionTicks > 0) {
            if (baseScale != 1.0f) PehkuiHelper.setScale(entity, baseScale, transitionTicks);
            if (modelWidth != 1.0f || modelHeight != 1.0f)
                PehkuiHelper.setModelScale(entity, modelWidth, modelHeight, transitionTicks);
            if (motionScale != 1.0f) PehkuiHelper.setMotionScale(entity, motionScale, transitionTicks);
            if (reachScale != 1.0f) PehkuiHelper.setReachScale(entity, reachScale, transitionTicks);
            if (attackScale != 1.0f) PehkuiHelper.setAttackScale(entity, attackScale, transitionTicks);
            if (defenseScale != 1.0f) PehkuiHelper.setDefenseScale(entity, defenseScale, transitionTicks);
            if (healthScale != 1.0f) PehkuiHelper.setHealthScale(entity, healthScale, transitionTicks);
        } else {
            if (baseScale != 1.0f) PehkuiHelper.setScale(entity, baseScale);
            if (modelWidth != 1.0f || modelHeight != 1.0f)
                PehkuiHelper.setModelScale(entity, modelWidth, modelHeight);
            if (motionScale != 1.0f) PehkuiHelper.setMotionScale(entity, motionScale);
            if (reachScale != 1.0f) PehkuiHelper.setReachScale(entity, reachScale);
            if (attackScale != 1.0f) PehkuiHelper.setAttackScale(entity, attackScale);
            if (defenseScale != 1.0f) PehkuiHelper.setDefenseScale(entity, defenseScale);
            if (healthScale != 1.0f) PehkuiHelper.setHealthScale(entity, healthScale);
        }
        if (stepHeightScale != 1.0f) PehkuiHelper.setStepHeightScale(entity, stepHeightScale);
        if (projectilesScale != 1.0f) PehkuiHelper.setProjectilesScale(entity, projectilesScale);
        if (explosionsScale != 1.0f) PehkuiHelper.setExplosionsScale(entity, explosionsScale);
    }

    /**
     * Reverts all configured scale values back to 1.0.
     * Uses transition ticks if set > 0, otherwise instant.
     *
     * @param entity the entity to revert
     */
    public void revert(Entity entity) {
        if (transitionTicks > 0) {
            PehkuiHelper.resetAllScales(entity, transitionTicks);
        } else {
            PehkuiHelper.resetAllScales(entity);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    public float getBaseScale() { return baseScale; }
    public float getModelWidth() { return modelWidth; }
    public float getModelHeight() { return modelHeight; }
    public float getMotionScale() { return motionScale; }
    public float getReachScale() { return reachScale; }
    public float getAttackScale() { return attackScale; }
    public float getDefenseScale() { return defenseScale; }
    public float getHealthScale() { return healthScale; }
    public float getStepHeightScale() { return stepHeightScale; }
    public float getProjectilesScale() { return projectilesScale; }
    public float getExplosionsScale() { return explosionsScale; }
    public int getTransitionTicks() { return transitionTicks; }

    // ═══════════════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════════════

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Shortcut for a uniform scale config.
     *
     * @param scale          the uniform scale (affects base)
     * @param transitionTicks ticks for smooth transition (0 = instant)
     */
    public static ScaleConfig uniform(float scale, int transitionTicks) {
        return builder().baseScale(scale).transitionTicks(transitionTicks).build();
    }

    /**
     * Shortcut for instant uniform scale.
     */
    public static ScaleConfig uniform(float scale) {
        return uniform(scale, 0);
    }

    public static class Builder {
        private float baseScale = 1.0f;
        private float modelWidth = 1.0f;
        private float modelHeight = 1.0f;
        private float motionScale = 1.0f;
        private float reachScale = 1.0f;
        private float attackScale = 1.0f;
        private float defenseScale = 1.0f;
        private float healthScale = 1.0f;
        private float stepHeightScale = 1.0f;
        private float projectilesScale = 1.0f;
        private float explosionsScale = 1.0f;
        private int transitionTicks = 0;

        /** Sets the base/overall scale. Affects all properties. */
        public Builder baseScale(float scale) { this.baseScale = scale; return this; }

        /** Sets model width scale independently. */
        public Builder modelWidth(float scale) { this.modelWidth = scale; return this; }

        /** Sets model height scale independently. */
        public Builder modelHeight(float scale) { this.modelHeight = scale; return this; }

        /** Sets movement speed scale. */
        public Builder motionScale(float scale) { this.motionScale = scale; return this; }

        /** Sets interaction reach scale. */
        public Builder reachScale(float scale) { this.reachScale = scale; return this; }

        /** Sets attack damage scale. */
        public Builder attackScale(float scale) { this.attackScale = scale; return this; }

        /** Sets defense/armor scale. */
        public Builder defenseScale(float scale) { this.defenseScale = scale; return this; }

        /** Sets max health scale. */
        public Builder healthScale(float scale) { this.healthScale = scale; return this; }

        /** Sets step height scale. */
        public Builder stepHeightScale(float scale) { this.stepHeightScale = scale; return this; }

        /** Sets projectile size scale. */
        public Builder projectilesScale(float scale) { this.projectilesScale = scale; return this; }

        /** Sets explosion size scale. */
        public Builder explosionsScale(float scale) { this.explosionsScale = scale; return this; }

        /** Sets the transition duration in ticks (0 = instant). */
        public Builder transitionTicks(int ticks) { this.transitionTicks = ticks; return this; }

        public ScaleConfig build() {
            return new ScaleConfig(this);
        }
    }
}