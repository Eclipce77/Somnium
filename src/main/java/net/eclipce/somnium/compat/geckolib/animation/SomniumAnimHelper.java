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

    /**
     * Triggers a GeckoLib cast animation on a player's vanilla model, with default behaviour
     * (vanilla animations blend additively on every body part, no look tracking, no hidden
     * layers, no first-person render). Equivalent to calling
     * {@link #triggerCastAnimation(net.minecraft.server.level.ServerPlayer, String, net.minecraft.resources.ResourceLocation, net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions)}
     * with {@link net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions#DEFAULT}.
     *
     * <p>Sends a {@link net.eclipce.somnium.network.PlayPlayerAnimationPacket} to all
     * clients tracking {@code player} (including the player themselves). Client-side, the
     * {@link net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin} picks up the
     * animation and applies bone-level transforms additively on top of the vanilla model
     * pose, so locomotion animations and armor layers are unaffected.</p>
     *
     * <p>Only bones explicitly keyframed in the animation are altered. Bones not present
     * in the animation contribute a zero delta and leave vanilla behaviour unchanged.</p>
     *
     * <p>Requires the target model to be registered via
     * {@link net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationModelRegistry}
     * on the client before the packet arrives.</p>
     *
     * @param player        the server-side player performing the ability
     * @param animationName animation name matching a key in your {@code .animation.json}
     *                      (e.g. {@code "animation.mymod.fire_cast"})
     * @param modelId       registry key matching a registered
     *                      {@link net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastModel}
     *                      (e.g. {@code new ResourceLocation("mymod", "fire_cast")})
     */
    public static void triggerCastAnimation(net.minecraft.server.level.ServerPlayer player,
                                            String animationName,
                                            net.minecraft.resources.ResourceLocation modelId) {
        triggerCastAnimation(player, animationName, modelId,
                net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions.DEFAULT);
    }

    /**
     * Triggers a GeckoLib cast animation on a player's vanilla model with the given
     * per-cast {@link net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions}.
     *
     * <p>The options travel with the packet to every tracking client and live for the
     * duration of this single animation; the next call to {@code triggerCastAnimation}
     * replaces them. Use the builder on {@code CastAnimationOptions} to toggle vanilla
     * suppression on specific parts, look-direction tracking, skin/layer hiding, and
     * first-person visibility — see that class's Javadoc for the full menu.</p>
     *
     * <p>Sample call:</p>
     * <pre>{@code
     * SomniumAnimHelper.triggerCastAnimation(player, "pistol_extend", GOMU_CAST_MODEL,
     *     CastAnimationOptions.builder()
     *         .suppressVanillaAnimOn(CastBodyPart.RIGHT_ARM)
     *         .onExecuteBodyAlign(true)
     *         .hideLayer(CastLayer.RIGHT_SLEEVE)
     *         .showInFirstPerson(true)
     *         .build());
     * }</pre>
     *
     * @param options never {@code null}; pass
     *                {@link net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions#DEFAULT}
     *                if you only need vanilla blending
     */
    public static void triggerCastAnimation(net.minecraft.server.level.ServerPlayer player,
                                            String animationName,
                                            net.minecraft.resources.ResourceLocation modelId,
                                            net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions options) {
        System.out.println("[Somnium-DIAG] triggerCastAnimation: player=" + player.getName().getString()
                + " uuid=" + player.getUUID() + " anim=" + animationName + " model=" + modelId);
        if (animationName == null || modelId == null) {
            System.out.println("[Somnium-DIAG] triggerCastAnimation: ABORTED — null arg");
            return;
        }
        if (options == null) {
            options = net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions.DEFAULT;
        }

        // ── onExecuteBodyAlign → re-face the player to their look direction ──
        // Tell the CASTING CLIENT to set its own body/head yaw to the look yaw (like a
        // left-click face-align). The client owns its rotation, so a server-side write here
        // would just get overwritten, and teleport() stutters movement. A rotation-only
        // packet to the owner is the path that both sticks and doesn't stutter. The raycast
        // already aims by look, so this is the cosmetic body catch-up — fired at execution.
        // No-op if already aligned (~1°).
        if (options.onExecuteBodyAlign()) {
            float lookYaw = player.getYRot();
            float delta = net.minecraft.util.Mth.degreesDifference(player.yBodyRot, lookYaw);
            if (Math.abs(delta) > 1.0f) {
                // Also set it server-side so other tracking clients see the new facing; the
                // packet handles the caster's own authoritative view.
                player.setYBodyRot(lookYaw);
                player.setYHeadRot(lookYaw);
                net.eclipce.somnium.network.SomniumNetwork.sendToClient(
                        new net.eclipce.somnium.network.BodyAlignPacket(lookYaw), player);
                System.out.println("[Somnium-DIAG] triggerCastAnimation: body-align -> " + lookYaw
                        + " (server set + packet to caster)");
            }
        }

        // ── changeDirectionOnLook → freeze the execution-time look pitch into the options ──
        // The vertical tilt is locked to where the player looked when the ability fired, so
        // it doesn't re-aim mid-animation. Capture the pitch here (server-side) and bake it
        // into the options that travel in the packet; the client applicator clamps + applies
        // it. Only bother when the option is actually in use.
        if (!options.changeDirectionOnLook().isEmpty()) {
            options = options.withCapturedLookPitch(player.getXRot());
            System.out.println("[Somnium-DIAG] triggerCastAnimation: captured look pitch "
                    + player.getXRot() + " for changeDirectionOnLook");
        }

        System.out.println("[Somnium-DIAG] triggerCastAnimation: sending PlayPlayerAnimationPacket via sendToTracking");
        net.eclipce.somnium.network.SomniumNetwork.sendToTracking(
                new net.eclipce.somnium.network.PlayPlayerAnimationPacket(
                        player.getUUID(), animationName, modelId.toString(), options),
                player);
        System.out.println("[Somnium-DIAG] triggerCastAnimation: sendToTracking returned");
    }

    /**
     * Drives a code-driven <em>body lean</em>: pitches a set of body parts by {@code pitchRad}
     * toward a direction known only at runtime — e.g. the Gomu Rocket leaning every part except
     * the grabbing arm toward the block it's being pulled to.
     *
     * <p>Call each tick while the lean angle changes (server-side); ease the angle yourself
     * before calling so the lean is smooth. Sends to the player and all trackers so it renders
     * for every viewer. Call {@link #clearBodyLean} when the lean ends.</p>
     *
     * @param player   the leaning player (server-side)
     * @param pitchRad pitch (radians) to apply to each listed part; 0 means no lean
     * @param parts    the parts to lean (typically everything except the active/grabbing arm)
     */
    public static void setBodyLean(net.minecraft.server.level.ServerPlayer player,
                                   float pitchRad,
                                   java.util.Set<net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart> parts) {
        setBodyLean(player, pitchRad, parts, null, 1f, 0f);
    }

    /**
     * As {@link #setBodyLean(net.minecraft.server.level.ServerPlayer, float, java.util.Set)} but
     * also carries a held-arm stretch (length scale + aim pitch) on {@code armPart}, applied at
     * the same time. Used by the Gomu Rocket flight: the body leans while the grabbing arm stays
     * extended toward the block (the clip slot is busy with the legs propel clip).
     *
     * @param armPart     the arm to keep stretched, or {@code null} for none
     * @param armScaleY   multiplicative Y length for the arm bone (1 = no stretch)
     * @param armPitchRad aim pitch for the arm about its rest pivot (0 = rest)
     */
    public static void setBodyLean(net.minecraft.server.level.ServerPlayer player,
                                   float pitchRad,
                                   java.util.Set<net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart> parts,
                                   @javax.annotation.Nullable net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart armPart,
                                   float armScaleY, float armPitchRad) {
        int mask = net.eclipce.somnium.compat.geckolib.player.cast.SomniumProceduralStretch
                .maskFromParts(parts);
        int armOrdinal = (armPart == null) ? -1 : armPart.ordinal();
        net.eclipce.somnium.network.SomniumNetwork.sendToTracking(
                new net.eclipce.somnium.network.ProceduralStretchPacket(
                        player.getUUID(), true, pitchRad, mask, armOrdinal, armScaleY, armPitchRad),
                player);
    }

    /** Clears any active body lean for the player on the client and all trackers. */
    public static void clearBodyLean(net.minecraft.server.level.ServerPlayer player) {
        net.eclipce.somnium.network.SomniumNetwork.sendToTracking(
                net.eclipce.somnium.network.ProceduralStretchPacket.clear(player.getUUID()),
                player);
    }

    /**
     * Snaps the player's body + head yaw to their current look yaw, the way a left-click
     * face-align does. Use from code-driven abilities (e.g. the Gomu Rocket grab) that want
     * the body — and therefore a procedurally-aimed arm — to face the aim direction, but
     * which don't go through a clip carrying {@code onExecuteBodyAlign}.
     *
     * <p>Sends a rotation-only packet to the casting client (which owns its rotation) and also
     * writes it server-side so other trackers see the new facing. No-op if already aligned.</p>
     */
    public static void faceLookDirection(net.minecraft.server.level.ServerPlayer player) {
        float lookYaw = player.getYRot();
        float delta = net.minecraft.util.Mth.degreesDifference(player.yBodyRot, lookYaw);
        if (Math.abs(delta) <= 1.0f) return;
        player.setYBodyRot(lookYaw);
        player.setYHeadRot(lookYaw);
        net.eclipce.somnium.network.SomniumNetwork.sendToClient(
                new net.eclipce.somnium.network.BodyAlignPacket(lookYaw), player);
    }
}