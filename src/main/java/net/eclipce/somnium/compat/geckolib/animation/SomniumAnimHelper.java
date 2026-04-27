package net.eclipce.somnium.compat.geckolib.animation;

import net.eclipce.somnium.compat.geckolib.player.TransformedPlayerAnimatable;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

import javax.annotation.Nullable;

/**
 * Static helper methods for triggering GeckoLib animations from
 * Somnium abilities and transformations.
 *
 * <p>These methods are safe to call — they no-op gracefully if the
 * player doesn't have an active GeckoLib model or if GeckoLib isn't
 * loaded (callers should check {@code GeckoLibCompat.isLoaded()} first).</p>
 *
 * <h3>Usage from ability code</h3>
 * <pre>{@code
 * // In your AbilityType.onActivate:
 * if (GeckoLibCompat.isLoaded()) {
 *     SomniumAnimHelper.triggerAnimation(player, "cast",
 *         RawAnimation.begin().thenPlay("animation.mymod.fireball_cast"));
 * }
 *
 * // Or use the string-based version:
 * SomniumAnimHelper.triggerAnimation(player, "cast", "animation.mymod.fireball_cast");
 * }</pre>
 *
 * <h3>Usage from transformation code</h3>
 * <pre>{@code
 * // In TransformationAbilityType.onTransformComplete:
 * if (GeckoLibCompat.isLoaded()) {
 *     SomniumAnimHelper.playTransformationAnim(player,
 *         RawAnimation.begin().thenPlay("animation.mymod.transform_in"));
 * }
 * }</pre>
 */
public final class SomniumAnimHelper {

    /**
     * Triggers a one-shot animation on a player's transformation model.
     * No-ops if the player doesn't have an active GeckoLib model.
     *
     * @param player         the player
     * @param controllerName the animation controller name to target
     * @param animation      the animation to play
     */
    public static void triggerAnimation(Player player, String controllerName,
                                         RawAnimation animation) {
        TransformedPlayerAnimatable animatable =
                TransformedPlayerAnimatable.getOrCreate(player);
        if (!animatable.hasActiveModel()) return;

        AnimationController<?> controller = animatable.getAnimatableInstanceCache()
                .getManagerForId(animatable.hashCode())
                .getAnimationControllers()
                .get(controllerName);

        if (controller != null) {
            controller.forceAnimationReset();
            controller.setAnimation(animation);
        }
    }

    /**
     * Triggers a named animation by string on a player's transformation model.
     *
     * @param player         the player
     * @param controllerName the controller name
     * @param animationName  the animation name (e.g., "animation.mymod.cast")
     */
    public static void triggerAnimation(Player player, String controllerName,
                                         String animationName) {
        triggerAnimation(player, controllerName,
                RawAnimation.begin().thenPlay(animationName));
    }

    /**
     * Plays an animation on the transformation controller specifically.
     * Uses the default "transformation" controller name.
     *
     * @param player    the player
     * @param animation the animation to play
     */
    public static void playTransformationAnim(Player player, RawAnimation animation) {
        triggerAnimation(player, "transformation", animation);
    }

    /**
     * Plays a looping animation on the transformation controller.
     *
     * @param player        the player
     * @param animationName the animation name to loop
     */
    public static void loopAnimation(Player player, String animationName) {
        triggerAnimation(player, "transformation",
                RawAnimation.begin().thenLoop(animationName));
    }

    /**
     * Stops all animations on a player's transformation model.
     *
     * @param player the player
     */
    public static void stopAnimation(Player player) {
        TransformedPlayerAnimatable animatable =
                TransformedPlayerAnimatable.getOrCreate(player);
        if (!animatable.hasActiveModel()) return;

        animatable.getAnimatableInstanceCache()
                .getManagerForId(animatable.hashCode())
                .getAnimationControllers()
                .values()
                .forEach(AnimationController::forceAnimationReset);
    }

    private SomniumAnimHelper() {}
}
