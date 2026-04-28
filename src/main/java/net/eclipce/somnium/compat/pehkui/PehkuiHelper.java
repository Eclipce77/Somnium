package net.eclipce.somnium.compat.pehkui;

import net.minecraft.world.entity.Entity;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

/**
 * Static helper methods for entity scaling through the Pehkui API.
 *
 * <p>All methods are safe to call when Pehkui is loaded. Callers should
 * check {@code PehkuiCompat.isLoaded()} before calling these methods.</p>
 *
 * <h3>Usage from ability/transformation code</h3>
 * <pre>{@code
 * // In your transformation's onTransformComplete:
 * if (PehkuiCompat.isLoaded()) {
 *     PehkuiHelper.setScale(player, 2.0f);           // double size
 *     PehkuiHelper.setScale(player, 2.0f, 20);       // double size over 1 second
 *     PehkuiHelper.setModelScale(player, 1.5f, 0.8f); // wide and short
 * }
 *
 * // In onDeTransformStart:
 * if (PehkuiCompat.isLoaded()) {
 *     PehkuiHelper.resetScale(player);                // back to normal
 *     PehkuiHelper.resetScale(player, 20);            // smooth transition back
 * }
 *
 * // Granular control:
 * PehkuiHelper.setMotionScale(player, 1.5f);          // faster movement
 * PehkuiHelper.setReachScale(player, 2.0f);            // longer reach
 * PehkuiHelper.setAttackScale(player, 1.5f);           // stronger attacks
 * PehkuiHelper.setDefenseScale(player, 2.0f);          // more defense
 * PehkuiHelper.setHealthScale(player, 3.0f);           // more health
 * PehkuiHelper.setStepHeightScale(player, 2.0f);       // higher step
 * }</pre>
 *
 * <h3>Scale types available</h3>
 * <ul>
 *     <li><b>BASE</b> — overall entity scale (affects all properties)</li>
 *     <li><b>MODEL_WIDTH / MODEL_HEIGHT</b> — visual model dimensions</li>
 *     <li><b>MOTION</b> — movement speed scaling</li>
 *     <li><b>REACH</b> — block/entity interaction reach</li>
 *     <li><b>ATTACK</b> — attack damage scaling</li>
 *     <li><b>DEFENSE</b> — defense/armor scaling</li>
 *     <li><b>HEALTH</b> — max health scaling</li>
 *     <li><b>STEP_HEIGHT</b> — step height scaling</li>
 *     <li><b>DROPS</b> — dropped item scale</li>
 *     <li><b>PROJECTILES</b> — projectile scale</li>
 *     <li><b>EXPLOSIONS</b> — explosion size scaling</li>
 * </ul>
 */
public final class PehkuiHelper {

    // ═══════════════════════════════════════════════════════════════════
    //  Base scale (affects everything)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the overall base scale of an entity. Affects all properties.
     *
     * @param entity the entity to scale
     * @param scale  the target scale (1.0 = normal, 2.0 = double, 0.5 = half)
     */
    public static void setScale(Entity entity, float scale) {
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setScale(scale);
    }

    /**
     * Sets the overall base scale with a smooth transition.
     *
     * @param entity    the entity to scale
     * @param scale     the target scale
     * @param tickDelay ticks for the transition (20 = 1 second)
     */
    public static void setScale(Entity entity, float scale, int tickDelay) {
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(scale);
    }

    /**
     * Resets the entity to normal size (1.0) instantly.
     */
    public static void resetScale(Entity entity) {
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setScale(1.0f);
    }

    /**
     * Resets the entity to normal size with a smooth transition.
     *
     * @param entity    the entity to reset
     * @param tickDelay ticks for the transition
     */
    public static void resetScale(Entity entity, int tickDelay) {
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(1.0f);
    }

    /**
     * Gets the current base scale of an entity.
     */
    public static float getScale(Entity entity) {
        return ScaleTypes.BASE.getScaleData(entity).getScale();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Model dimensions
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the model width and height scales independently.
     * Useful for making entities wider/taller without uniform scaling.
     *
     * @param entity the entity
     * @param width  width scale multiplier
     * @param height height scale multiplier
     */
    public static void setModelScale(Entity entity, float width, float height) {
        ScaleTypes.MODEL_WIDTH.getScaleData(entity).setScale(width);
        ScaleTypes.MODEL_HEIGHT.getScaleData(entity).setScale(height);
    }

    /**
     * Sets model dimensions with smooth transition.
     */
    public static void setModelScale(Entity entity, float width, float height, int tickDelay) {
        ScaleData widthData = ScaleTypes.MODEL_WIDTH.getScaleData(entity);
        widthData.setScaleTickDelay(tickDelay);
        widthData.setTargetScale(width);

        ScaleData heightData = ScaleTypes.MODEL_HEIGHT.getScaleData(entity);
        heightData.setScaleTickDelay(tickDelay);
        heightData.setTargetScale(height);
    }

    /**
     * Resets model dimensions to normal.
     */
    public static void resetModelScale(Entity entity) {
        ScaleTypes.MODEL_WIDTH.getScaleData(entity).setScale(1.0f);
        ScaleTypes.MODEL_HEIGHT.getScaleData(entity).setScale(1.0f);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Individual property scales
    // ═══════════════════════════════════════════════════════════════════

    /** Sets motion/movement speed scale. */
    public static void setMotionScale(Entity entity, float scale) {
        ScaleTypes.MOTION.getScaleData(entity).setScale(scale);
    }

    public static void setMotionScale(Entity entity, float scale, int tickDelay) {
        ScaleData data = ScaleTypes.MOTION.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(scale);
    }

    /** Sets block/entity reach distance scale. */
    public static void setReachScale(Entity entity, float scale) {
        ScaleTypes.REACH.getScaleData(entity).setScale(scale);
    }

    public static void setReachScale(Entity entity, float scale, int tickDelay) {
        ScaleData data = ScaleTypes.REACH.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(scale);
    }

    /** Sets attack damage scale. */
    public static void setAttackScale(Entity entity, float scale) {
        ScaleTypes.ATTACK.getScaleData(entity).setScale(scale);
    }

    public static void setAttackScale(Entity entity, float scale, int tickDelay) {
        ScaleData data = ScaleTypes.ATTACK.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(scale);
    }

    /** Sets defense/armor scale. */
    public static void setDefenseScale(Entity entity, float scale) {
        ScaleTypes.DEFENSE.getScaleData(entity).setScale(scale);
    }

    public static void setDefenseScale(Entity entity, float scale, int tickDelay) {
        ScaleData data = ScaleTypes.DEFENSE.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(scale);
    }

    /** Sets max health scale. */
    public static void setHealthScale(Entity entity, float scale) {
        ScaleTypes.HEALTH.getScaleData(entity).setScale(scale);
    }

    public static void setHealthScale(Entity entity, float scale, int tickDelay) {
        ScaleData data = ScaleTypes.HEALTH.getScaleData(entity);
        data.setScaleTickDelay(tickDelay);
        data.setTargetScale(scale);
    }

    /** Sets step height scale. */
    public static void setStepHeightScale(Entity entity, float scale) {
        ScaleTypes.STEP_HEIGHT.getScaleData(entity).setScale(scale);
    }

    /** Sets dropped item scale. */
    public static void setDropsScale(Entity entity, float scale) {
        ScaleTypes.DROPS.getScaleData(entity).setScale(scale);
    }

    /** Sets projectile scale. */
    public static void setProjectilesScale(Entity entity, float scale) {
        ScaleTypes.PROJECTILES.getScaleData(entity).setScale(scale);
    }

    /** Sets explosion size scale. */
    public static void setExplosionsScale(Entity entity, float scale) {
        ScaleTypes.EXPLOSIONS.getScaleData(entity).setScale(scale);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters for individual scale types
    // ═══════════════════════════════════════════════════════════════════

    public static float getMotionScale(Entity entity) {
        return ScaleTypes.MOTION.getScaleData(entity).getScale();
    }

    public static float getReachScale(Entity entity) {
        return ScaleTypes.REACH.getScaleData(entity).getScale();
    }

    public static float getAttackScale(Entity entity) {
        return ScaleTypes.ATTACK.getScaleData(entity).getScale();
    }

    public static float getDefenseScale(Entity entity) {
        return ScaleTypes.DEFENSE.getScaleData(entity).getScale();
    }

    public static float getHealthScale(Entity entity) {
        return ScaleTypes.HEALTH.getScaleData(entity).getScale();
    }

    public static float getModelWidthScale(Entity entity) {
        return ScaleTypes.MODEL_WIDTH.getScaleData(entity).getScale();
    }

    public static float getModelHeightScale(Entity entity) {
        return ScaleTypes.MODEL_HEIGHT.getScaleData(entity).getScale();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Bulk operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resets ALL scale types on an entity to 1.0 instantly.
     * Use with caution — this overrides any other mod's scaling.
     */
    public static void resetAllScales(Entity entity) {
        resetScale(entity);
        resetModelScale(entity);
        ScaleTypes.MOTION.getScaleData(entity).setScale(1.0f);
        ScaleTypes.REACH.getScaleData(entity).setScale(1.0f);
        ScaleTypes.ATTACK.getScaleData(entity).setScale(1.0f);
        ScaleTypes.DEFENSE.getScaleData(entity).setScale(1.0f);
        ScaleTypes.HEALTH.getScaleData(entity).setScale(1.0f);
        ScaleTypes.STEP_HEIGHT.getScaleData(entity).setScale(1.0f);
        ScaleTypes.DROPS.getScaleData(entity).setScale(1.0f);
        ScaleTypes.PROJECTILES.getScaleData(entity).setScale(1.0f);
        ScaleTypes.EXPLOSIONS.getScaleData(entity).setScale(1.0f);
    }

    /**
     * Resets ALL scale types with a smooth transition.
     */
    public static void resetAllScales(Entity entity, int tickDelay) {
        resetScale(entity, tickDelay);
        setModelScale(entity, 1.0f, 1.0f, tickDelay);
        setMotionScale(entity, 1.0f, tickDelay);
        setReachScale(entity, 1.0f, tickDelay);
        setAttackScale(entity, 1.0f, tickDelay);
        setDefenseScale(entity, 1.0f, tickDelay);
        setHealthScale(entity, 1.0f, tickDelay);
    }

    private PehkuiHelper() {}
}